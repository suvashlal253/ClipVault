package com.clipvault.android.service

import android.content.Context
import com.clipvault.android.data.local.ClipDatabase
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.classifier.ClipClassifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var lastProcessedText: String? = null

    init {
        startConsumer()
    }

    /**
     * Synchronously submits a captured clip into the unbounded Channel using trySend
     * so rapid A -> B -> C events never race each other before entering the queue.
     */
    fun enqueueClip(rawText: String, sourceApp: String = "Auto-Capture"): Boolean {
        val cleanText = rawText.trim()
        if (cleanText.isEmpty()) return false
        return clipQueue.trySend(QueuedClipItem(cleanText, sourceApp)).isSuccess
    }

    fun onTextCopied(
        rawText: String,
        sourceApp: String = "Instant Auto-Capture",
        onSaved: ((ClipEntity) -> Unit)? = null
    ) {
        enqueueClip(rawText, sourceApp)
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
        val trimmed = cleanText.trim()
        if (trimmed.isEmpty()) return null

        return mutex.withLock {
            withContext(NonCancellable) {
                // Skip only if identical to the immediately preceding processed clip
                if (trimmed == lastProcessedText) {
                    return@withContext null
                }

                try {
                    val latestInDb = clipDao.getLatestClipContent()?.trim()
                    if (latestInDb != null && latestInDb == trimmed) {
                        lastProcessedText = trimmed
                        return@withContext null
                    }

                    val clipType = detectType(trimmed)
                    val classification = ClipClassifier.classify(trimmed)
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
                        content = trimmed,
                        encryptedIv = null,
                        category = resolvedCategory,
                        createdAt = monotonicTimestamp,
                        tags = classification.tags,
                        sourceApp = sourceApp,
                        isPinned = false,
                        isVaulted = false,
                        charCount = trimmed.length
                    )
                    clipDao.insertClip(entity)

                    // Update ONLY after successful database insertion
                    lastProcessedText = trimmed
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
        @Volatile
        private var INSTANCE: ClipboardServiceHelper? = null

        fun getInstance(context: Context): ClipboardServiceHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipboardServiceHelper(context).also { INSTANCE = it }
            }
        }
    }
}
