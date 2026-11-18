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
     * Records a short-lived fingerprint for a clipboard copy action initiated inside ClipVault
     * so the clipboard listener ignores the self-originated copy event.
     */
    fun markAppOriginatedCopy(rawText: String, nowMs: Long = System.currentTimeMillis()) {
        val hash = ClipboardContentNormalizer.computeContentHash(rawText)
        if (hash.isNotEmpty()) {
            instanceAppCopyFingerprints[hash] = nowMs
            recordAppOriginatedCopy(rawText, nowMs)
        }
    }

    private fun isRecentAppOriginatedCopy(contentHash: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (contentHash.isEmpty()) return false
        val instanceTs = instanceAppCopyFingerprints[contentHash]
        if (instanceTs != null && (nowMs - instanceTs) in 0..APP_COPY_FINGERPRINT_TTL_MS) {
            return true
        }
        return isGlobalAppOriginatedCopy(contentHash, nowMs)
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
                    // 1. Check whether this clipboard event originated from ClipVault's own Copy action
                    if (isRecentAppOriginatedCopy(contentHash)) {
                        lastProcessedNormalizedHash = contentHash
                        lastProcessedText = preservedDisplayText
                        return@withContext null
                    }

                    // 2. Check database-wide duplicate existence via indexed content_hash + normalized content verification
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

                    // 3. Fallback check for exact or normalized match across non-vaulted clips (handles any legacy rows)
                    val exactMatch = clipDao.findClipByContent(preservedDisplayText)
                        ?: clipDao.getAllNonVaultedClipsSync().firstOrNull { existing ->
                            ClipboardContentNormalizer.areEquivalent(existing.content, normalized)
                        }
                    if (exactMatch != null) {
                        lastProcessedNormalizedHash = contentHash
                        lastProcessedText = preservedDisplayText
                        return@withContext null
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
        const val APP_COPY_FINGERPRINT_TTL_MS = 10_000L

        private val globalAppCopyFingerprints = ConcurrentHashMap<String, Long>()

        @Volatile
        private var INSTANCE: ClipboardServiceHelper? = null

        /**
         * Records that ClipVault itself just wrote [rawText] to the system clipboard,
         * preventing self-capture loops while keeping external clipboard monitoring active.
         */
        fun recordAppOriginatedCopy(rawText: String, nowMs: Long = System.currentTimeMillis()) {
            val hash = ClipboardContentNormalizer.computeContentHash(rawText)
            if (hash.isEmpty()) return
            globalAppCopyFingerprints[hash] = nowMs
            val iter = globalAppCopyFingerprints.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                if (nowMs - entry.value > APP_COPY_FINGERPRINT_TTL_MS) {
                    iter.remove()
                }
            }
        }

        fun isGlobalAppOriginatedCopy(contentHash: String, nowMs: Long = System.currentTimeMillis()): Boolean {
            if (contentHash.isEmpty()) return false
            val ts = globalAppCopyFingerprints[contentHash] ?: return false
            if (nowMs - ts in 0..APP_COPY_FINGERPRINT_TTL_MS) {
                return true
            }
            globalAppCopyFingerprints.remove(contentHash, ts)
            return false
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
