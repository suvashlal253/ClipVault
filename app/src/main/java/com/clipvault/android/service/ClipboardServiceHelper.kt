package com.clipvault.android.service

import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.classifier.ClipClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardServiceHelper @Inject constructor(
    private val clipDao: ClipDao
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val lastTimestamp = AtomicLong(0L)

    @Volatile
    private var lastCopiedText: String? = null

    fun onTextCopied(
        rawText: String,
        sourceApp: String = "Instant Auto-Capture",
        onSaved: ((ClipEntity) -> Unit)? = null
    ) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return

        // Prevent duplicate consecutive immediate copies
        if (trimmed == lastCopiedText) return
        lastCopiedText = trimmed

        // Launch into background scope and lock with Mutex to preserve order & prevent race conditions
        serviceScope.launch {
            mutex.withLock {
                withContext(NonCancellable) {
                    val entity = insertLocked(trimmed, sourceApp)
                    if (entity != null) {
                        onSaved?.invoke(entity)
                    }
                }
            }
        }
    }

    suspend fun insertSequentially(
        rawText: String,
        sourceApp: String? = "Instant Auto-Capture"
    ): ClipEntity? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        return mutex.withLock {
            withContext(NonCancellable) {
                lastCopiedText = trimmed
                insertLocked(trimmed, sourceApp)
            }
        }
    }

    fun resetLastCopiedText(suppressedText: String? = null) {
        lastCopiedText = suppressedText
    }

    private suspend fun insertLocked(
        trimmed: String,
        sourceApp: String?
    ): ClipEntity? {
        // Skip only if the very latest clip at the top of Room already matches this exact text
        val latestInDb = clipDao.getLatestClipContent()?.trim()
        if (latestInDb != null && latestInDb == trimmed) {
            return clipDao.findClipByContent(trimmed)
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
            isVaulted = false, // Always default to Home Dashboard, never auto-vault
            charCount = trimmed.length
        )
        clipDao.insertClip(entity)
        return entity
    }

    private fun detectType(text: String): String {
        return when {
            text.contains("@") && text.contains(".") && !text.contains(" ") -> "EMAIL"
            text.startsWith("http://") || text.startsWith("https://") -> "URL"
            text.matches(Regex(".*\\d{4,6}.*")) && text.length < 15 -> "CODE"
            else -> "TEXT"
        }
    }
}
