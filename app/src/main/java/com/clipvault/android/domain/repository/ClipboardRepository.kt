package com.clipvault.android.domain.repository

import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.classifier.ClipClassifier
import com.clipvault.android.security.SecurityHelper
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface ClipboardRepository {
    fun getAllClips(): Flow<List<ClipEntity>>
    fun getVaultClips(): Flow<List<ClipEntity>>
    fun getPinnedClips(): Flow<List<ClipEntity>>
    fun getClipsByCategory(category: ClipCategory): Flow<List<ClipEntity>>
    fun searchClips(query: String): Flow<List<ClipEntity>>
    suspend fun saveNewClip(content: String, sourceApp: String? = null, autoVault: Boolean = false): ClipEntity?
    suspend fun editClip(clipId: String, newContent: String)
    suspend fun togglePin(clipId: String, currentPinState: Boolean)
    suspend fun toggleVault(clipId: String, currentVaultState: Boolean, rawContent: String)
    suspend fun deleteClip(clipId: String)
    suspend fun clearHistory()
    suspend fun clearAllHistory()
    suspend fun pruneRetention(olderThanMillis: Long): Int
}

@Singleton
class ClipboardRepositoryImpl @Inject constructor(
    private val clipDao: ClipDao
) : ClipboardRepository {

    // Prevents the last copied item in the OS ClipboardManager from re-inserting as a ghost clip after Clear All History
    @Volatile
    private var suppressedGhostClipContent: String? = null

    override fun getAllClips(): Flow<List<ClipEntity>> = clipDao.getAllClipsFlow()

    override fun getVaultClips(): Flow<List<ClipEntity>> = clipDao.getVaultClipsFlow()

    override fun getPinnedClips(): Flow<List<ClipEntity>> = clipDao.getPinnedClipsFlow()

    override fun getClipsByCategory(category: ClipCategory): Flow<List<ClipEntity>> =
        clipDao.getClipsByCategoryFlow(category)

    override fun searchClips(query: String): Flow<List<ClipEntity>> {
        // Extract alphanumeric prefix tokens so FTS4 MATCH never throws SQLiteException on URLs, quotes, or code symbols
        val tokens = query.trim()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(" ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return getAllClips()
        val sanitizedQuery = tokens.joinToString(" ") { "$it*" }
        return clipDao.searchClips(sanitizedQuery)
    }

    override suspend fun saveNewClip(content: String, sourceApp: String?, autoVault: Boolean): ClipEntity? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null

        // 0. Ghost/Orphan Clip Guard after Clear All History:
        // If this clip matches the stale OS clipboard text that existed when history was wiped,
        // ignore background/focus re-syncs until the user copies new text or adds manually.
        if (trimmed == suppressedGhostClipContent && sourceApp != "Manual Entry") {
            return null
        }
        if (trimmed != suppressedGhostClipContent) {
            suppressedGhostClipContent = null
        }

        // 1. Position Lock & Duplicate Detection:
        // If the exact clip already exists in Room, keep it locked in its original position
        // without mutating createdAt or causing erratic list reordering when re-copying.
        val existing = clipDao.findClipByContent(trimmed) ?: clipDao.findClipByContent(content)
        if (existing != null) {
            return existing
        }

        // 2. Default Dashboard Behavior:
        // Save all newly copied items (English, Bengali, URLs, Emails, etc.) strictly to the
        // main Home Dashboard by default (isVaulted = false, encryptedIv = null).
        // No copied text automatically bypasses the dashboard; items only move to the Secure Vault
        // when the user explicitly clicks the Lock/Vault icon on a clip card (via toggleVault).
        val classification = ClipClassifier.classify(content)

        val entity = ClipEntity(
            content = content,
            encryptedIv = null,
            category = classification.category,
            tags = classification.tags,
            sourceApp = sourceApp,
            isVaulted = false,
            charCount = content.length
        )

        clipDao.insertClip(entity)
        return entity
    }

    override suspend fun editClip(clipId: String, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return
        val classification = ClipClassifier.classify(trimmed)
        val existing = clipDao.getClipById(clipId)

        if (existing != null && existing.isVaulted) {
            // Re-encrypt modified Vault clip with a fresh AES-256-GCM IV via Android Keystore
            val encrypted = SecurityHelper.encrypt(trimmed)
            clipDao.updateEncryptedClipContent(
                clipId = clipId,
                encryptedContent = encrypted.ciphertextBase64,
                iv = encrypted.ivBase64,
                charCount = trimmed.length,
                category = classification.category
            )
        } else {
            // Preserve original created_at position when editing standard clip content
            clipDao.updateClipContent(
                clipId = clipId,
                newContent = trimmed,
                charCount = trimmed.length,
                category = classification.category
            )
        }
    }

    override suspend fun togglePin(clipId: String, currentPinState: Boolean) {
        clipDao.updatePinStatus(clipId, !currentPinState)
    }

    override suspend fun toggleVault(clipId: String, currentVaultState: Boolean, rawContent: String) {
        if (!currentVaultState) {
            // Move into vault -> encrypt
            val encrypted = SecurityHelper.encrypt(rawContent)
            clipDao.updateVaultStatus(clipId, true, encrypted.ciphertextBase64, encrypted.ivBase64)
        } else {
            // Move out of vault -> decrypt
            clipDao.updateVaultStatus(clipId, false, rawContent, null)
        }
    }

    override suspend fun deleteClip(clipId: String) {
        clipDao.deleteClipById(clipId)
    }

    override suspend fun clearHistory() {
        suppressedGhostClipContent = clipDao.getLatestClipContent()?.trim()
        clipDao.clearAllUnpinned()
        try {
            clipDao.rebuildFtsIndex()
        } catch (_: Exception) {}
    }

    override suspend fun clearAllHistory() {
        // Record the latest clip before wiping so background services don't re-insert it from OS Clipboard cache
        suppressedGhostClipContent = clipDao.getLatestClipContent()?.trim()
        clipDao.clearAllHistory()
        try {
            clipDao.rebuildFtsIndex()
        } catch (_: Exception) {}
    }

    override suspend fun pruneRetention(olderThanMillis: Long): Int {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        return clipDao.pruneOldClips(cutoff)
    }
}
