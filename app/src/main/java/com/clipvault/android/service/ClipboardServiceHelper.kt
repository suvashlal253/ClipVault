package com.clipvault.android.service

import android.content.Context
import com.clipvault.android.data.local.ClipDatabase
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.data.local.entity.ClipboardContentNormalizer
import com.clipvault.android.domain.classifier.ClipClassifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ClipboardServiceHelper internal constructor(
    private val clipDao: ClipDao,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private constructor(context: Context) : this(
        clipDao = ClipDatabase.getDatabase(context.applicationContext).clipDao(),
        dispatcher = Dispatchers.IO
    )

    data class QueuedClipItem(
        val text: String,
        val sourceApp: String
    )

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val clipQueue = Channel<QueuedClipItem>(Channel.UNLIMITED)
    private val mutex = Mutex()
    private val lastTimestamp = AtomicLong(0L)

    @Volatile
    private var lastProcessedNormalizedHash: String? = null

    @Volatile
    private var lastProcessedText: String? = null

    private val instanceAppCopyFingerprints = ConcurrentHashMap<String, Long>()

    init {
        startConsumer()
    }

    /**
     * Synchronously submits a captured clip into the unbounded Channel using trySend
     * so rapid A -> B -> C events never race each other before entering the queue.
     */
    fun enqueueClip(rawText: String, sourceApp: String = "Auto-Capture"): Boolean {
        val normalized = ClipboardContentNormalizer.normalize(rawText)
        if (normalized.isEmpty()) return false
        return clipQueue.trySend(QueuedClipItem(rawText, sourceApp)).isSuccess
    }

    fun onTextCopied(
        rawText: String,
        sourceApp: String = "Instant Auto-Capture",
        onSaved: ((ClipEntity) -> Unit)? = null
    ) {
        enqueueClip(rawText, sourceApp)
    }

    /**
     * Records a short-lived single-use echo token for a clipboard copy action initiated inside ClipVault
     * so the immediate OS clipboard listener echo is ignored without falsely suppressing subsequent external copies.
     */
    fun markAppOriginatedCopy(rawText: String, nowMs: Long = System.currentTimeMillis()) {
        val hash = ClipboardContentNormalizer.computeContentHash(rawText)
        if (hash.isNotEmpty()) {
            instanceAppCopyFingerprints[hash] = nowMs
            recordAppOriginatedCopy(rawText, nowMs)
        }
    }

    /**
     * Determines whether an incoming clipboard event is the immediate OS echo of a copy action
     * initiated inside ClipVault, consuming the echo token on match so a subsequent external copy
     * of identical text is never falsely suppressed.
     *
     * Context rules:
     * 1. If [sourceApp] explicitly identifies an external application (e.g., "Chrome", "WhatsApp",
     *    "Firefox", "Notes" — any non-blank source not in [SELF_OR_GENERIC_CAPTURE_SOURCES]), the event
     *    originated from an external app context. Any stale self-copy token for [contentHash] is cleared
     *    and the event is NOT suppressed here (normal database content_hash deduplication still runs next).
     * 2. If [sourceApp] is a generic listener callback ("Auto-Capture", "System Clipboard", etc.) or
     *    internal ClipVault source, a matching token within [APP_COPY_ECHO_WINDOW_MS] is atomically
     *    consumed (removed) on first match and returns true to suppress the self-copy echo.
     */
    private fun shouldSuppressAndConsumeAppOriginatedEcho(
        contentHash: String,
        sourceApp: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (contentHash.isEmpty()) return false
        if (isExplicitExternalAppSource(sourceApp)) {
            instanceAppCopyFingerprints.remove(contentHash)
            clearAppOriginatedCopyForHash(contentHash)
            return false
        }
        val instanceTs = instanceAppCopyFingerprints.remove(contentHash)
        val matchedInstance = instanceTs != null && (nowMs - instanceTs) in 0..APP_COPY_ECHO_WINDOW_MS
        val matchedGlobal = consumeGlobalAppOriginatedCopy(contentHash, sourceApp, nowMs)
        return matchedInstance || matchedGlobal
    }

    private fun startConsumer() {
        scope.launch {
            for (item in clipQueue) {
                processQueuedItem(item.text, item.sourceApp)
            }
        }
    }

    internal suspend fun processQueuedItem(
        cleanText: String,
        sourceApp: String? = "Auto-Capture"
    ): ClipEntity? {
        val normalized = ClipboardContentNormalizer.normalize(cleanText)
        if (normalized.isEmpty()) return null
        val contentHash = ClipboardContentNormalizer.computeContentHash(normalized)
        val preservedDisplayText = cleanText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

        return mutex.withLock {
            withContext(NonCancellable) {
                try {
                    // 1. Check whether this clipboard event is the immediate OS echo of ClipVault's own Copy action.
                    //    Consumes the token on match and respects explicit external sourceApp context so subsequent
                    //    external copies of identical text are never falsely suppressed.
                    if (shouldSuppressAndConsumeAppOriginatedEcho(contentHash, sourceApp)) {
                        lastProcessedNormalizedHash = contentHash
                        lastProcessedText = preservedDisplayText
                        return@withContext null
                    }

                    // 2. Check database-wide duplicate existence via indexed content_hash (O(log N) B-tree index lookup)
                    //    followed by exact normalized content verification to guard against hash collisions.
                    val hashCandidates = clipDao.findClipsByContentHash(contentHash)
                    val matchedByHash = hashCandidates.firstOrNull { existing ->
                        ClipboardContentNormalizer.areEquivalent(existing.content, normalized)
                    }
                    if (matchedByHash != null) {
                        // Equivalent content already exists in DB: do NOT insert a duplicate and do NOT alter metadata
                        lastProcessedNormalizedHash = contentHash
                        lastProcessedText = preservedDisplayText
                        return@withContext null
                    }

                    // 3. Restricted Legacy Fallback (v1.7.4 Requirement 4):
                    //    Only inspect non-vaulted records whose content_hash is empty (content_hash = '' AND is_vaulted = 0).
                    //    Lazily backfills content_hash on any unindexed legacy row so it is never scanned again,
                    //    and NEVER loads the entire clipboard history (getAllNonVaultedClipsSync) on a hash miss.
                    val unindexedLegacyClips = clipDao.findUnindexedLegacyClips()
                    if (unindexedLegacyClips.isNotEmpty()) {
                        var matchedLegacy: ClipEntity? = null
                        for (legacy in unindexedLegacyClips) {
                            val computedLegacyHash = ClipboardContentNormalizer.computeContentHash(legacy.content)
                            if (computedLegacyHash.isNotEmpty()) {
                                clipDao.updateClipContentHash(legacy.id, computedLegacyHash)
                            }
                            if (matchedLegacy == null && ClipboardContentNormalizer.areEquivalent(legacy.content, normalized)) {
                                matchedLegacy = legacy
                            }
                        }
                        if (matchedLegacy != null) {
                            lastProcessedNormalizedHash = contentHash
                            lastProcessedText = preservedDisplayText
                            return@withContext null
                        }
                    }

                    val clipType = detectType(preservedDisplayText)
                    val classification = ClipClassifier.classify(preservedDisplayText)
                    val resolvedCategory = when (clipType) {
                        "EMAIL" -> ClipCategory.EMAIL
                        "URL" -> ClipCategory.URL
                        "CODE" -> if (classification.category == ClipCategory.OTP) ClipCategory.OTP else ClipCategory.CODE
                        else -> classification.category
                    }

                    val now = System.currentTimeMillis()
                    val monotonicTimestamp = maxOf(now, lastTimestamp.get() + 1L)
                    lastTimestamp.set(monotonicTimestamp)

                    val entity = ClipEntity(
                        content = preservedDisplayText,
                        encryptedIv = null,
                        category = resolvedCategory,
                        createdAt = monotonicTimestamp,
                        tags = classification.tags,
                        sourceApp = sourceApp,
                        isPinned = false,
                        isVaulted = false,
                        charCount = preservedDisplayText.length,
                        contentHash = contentHash
                    )
                    val rowId = clipDao.insertClip(entity)
                    if (rowId <= 0L) {
                        throw IllegalStateException("Room insertClip returned non-positive rowId: $rowId")
                    }

                    // Update ONLY after successful database insertion
                    lastProcessedNormalizedHash = contentHash
                    lastProcessedText = preservedDisplayText
                    entity
                } catch (_: Exception) {
                    // Do NOT update lastProcessedText on failure so subsequent retry is never skipped
                    null
                }
            }
        }
    }

    suspend fun insertSequentially(
        rawText: String,
        sourceApp: String? = "Foreground Sync"
    ): ClipEntity? {
        return processQueuedItem(rawText, sourceApp)
    }

    fun resetLastCopiedText(suppressedText: String? = null) {
        lastProcessedText = suppressedText
        lastProcessedNormalizedHash = suppressedText?.let { ClipboardContentNormalizer.computeContentHash(it) }
    }

    fun getLastProcessedText(): String? = lastProcessedText

    private fun detectType(text: String): String {
        return when {
            text.contains("@") && text.contains(".") && !text.contains(" ") -> "EMAIL"
            text.startsWith("http://") || text.startsWith("https://") -> "URL"
            text.matches(Regex(".*\\d{4,6}.*")) && text.length < 15 -> "CODE"
            else -> "TEXT"
        }
    }

    companion object {
        /**
         * Tight echo window (2,500ms) for consuming the single immediate OS clipboard callback
         * triggered when ClipVault itself calls ClipboardManager.setPrimaryClip.
         */
        const val APP_COPY_ECHO_WINDOW_MS = 2_500L
        const val APP_COPY_FINGERPRINT_TTL_MS = APP_COPY_ECHO_WINDOW_MS

        private val SELF_OR_GENERIC_CAPTURE_SOURCES = setOf(
            "",
            "clipvault",
            "com.clipvault.android",
            "auto-capture",
            "instant auto-capture",
            "system clipboard",
            "foreground sync",
            "clipmonitorservice",
            "clipaccessibilityservice",
            "clipvault ime",
            "clipvault keyboard"
        )

        private val globalAppCopyFingerprints = ConcurrentHashMap<String, Long>()

        @Volatile
        private var INSTANCE: ClipboardServiceHelper? = null

        fun isExplicitExternalAppSource(sourceApp: String?): Boolean {
            if (sourceApp.isNullOrBlank()) return false
            val normalizedSource = sourceApp.trim().lowercase()
            return normalizedSource !in SELF_OR_GENERIC_CAPTURE_SOURCES
        }

        /**
         * Records that ClipVault itself just wrote [rawText] to the system clipboard,
         * preventing the immediate self-capture OS callback while keeping external clipboard monitoring active.
         */
        fun recordAppOriginatedCopy(rawText: String, nowMs: Long = System.currentTimeMillis()) {
            val hash = ClipboardContentNormalizer.computeContentHash(rawText)
            if (hash.isEmpty()) return
            globalAppCopyFingerprints[hash] = nowMs
            val iter = globalAppCopyFingerprints.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (nowMs - entry.value > APP_COPY_ECHO_WINDOW_MS) {
                    iter.remove()
                }
            }
        }

        /**
         * Atomically consumes (removes) a pending app-originated copy token if the event comes from
         * a generic listener or internal ClipVault callback within [APP_COPY_ECHO_WINDOW_MS].
         * If [sourceApp] explicitly identifies an external app, clears any pending token and returns false.
         */
        fun consumeGlobalAppOriginatedCopy(
            contentHash: String,
            sourceApp: String? = null,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (contentHash.isEmpty()) return false
            if (isExplicitExternalAppSource(sourceApp)) {
                globalAppCopyFingerprints.remove(contentHash)
                return false
            }
            val ts = globalAppCopyFingerprints.remove(contentHash) ?: return false
            return (nowMs - ts) in 0..APP_COPY_ECHO_WINDOW_MS
        }

        fun isGlobalAppOriginatedCopy(contentHash: String, nowMs: Long = System.currentTimeMillis()): Boolean {
            if (contentHash.isEmpty()) return false
            val ts = globalAppCopyFingerprints[contentHash] ?: return false
            if (nowMs - ts in 0..APP_COPY_ECHO_WINDOW_MS) {
                return true
            }
            globalAppCopyFingerprints.remove(contentHash, ts)
            return false
        }

        internal fun clearAppOriginatedCopyForHash(contentHash: String) {
            if (contentHash.isNotEmpty()) {
                globalAppCopyFingerprints.remove(contentHash)
            }
        }

        fun clearGlobalAppOriginatedCopies() {
            globalAppCopyFingerprints.clear()
        }

        fun getInstance(context: Context): ClipboardServiceHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipboardServiceHelper(context).also { INSTANCE = it }
            }
        }
    }
}
