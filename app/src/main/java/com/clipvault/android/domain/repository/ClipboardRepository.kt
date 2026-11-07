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
    suspend fun saveNewClip(content: String, sourceApp: String? = null, autoVault: Boolean = false): ClipEntity
    suspend fun togglePin(clipId: String, currentPinState: Boolean)
    suspend fun toggleVault(clipId: String, currentVaultState: Boolean, rawContent: String)
    suspend fun deleteClip(clipId: String)
    suspend fun clearHistory()
    suspend fun pruneRetention(olderThanMillis: Long): Int
}

@Singleton
class ClipboardRepositoryImpl @Inject constructor(
    private val clipDao: ClipDao
) : ClipboardRepository {

    override fun getAllClips(): Flow<List<ClipEntity>> = clipDao.getAllClipsFlow()

    override fun getVaultClips(): Flow<List<ClipEntity>> = clipDao.getVaultClipsFlow()

    override fun getPinnedClips(): Flow<List<ClipEntity>> = clipDao.getPinnedClipsFlow()

    override fun getClipsByCategory(category: ClipCategory): Flow<List<ClipEntity>> =
        clipDao.getClipsByCategoryFlow(category)

    override fun searchClips(query: String): Flow<List<ClipEntity>> {
        val sanitizedQuery = query.trim().replace("'", "''") + "*"
        return clipDao.searchClips(sanitizedQuery)
    }

    override suspend fun saveNewClip(content: String, sourceApp: String?, autoVault: Boolean): ClipEntity {
        // Avoid duplicate identical entry if recent
        val existing = clipDao.findClipByContent(content)
        if (existing != null) {
            // Bump timestamp to top
            val updated = existing.copy(createdAt = System.currentTimeMillis())
            clipDao.updateClip(updated)
            return updated
        }

        val classification = ClipClassifier.classify(content)
        val shouldVault = autoVault || classification.isAutoVaultCandidate

        val (finalContent, iv) = if (shouldVault) {
            val encrypted = SecurityHelper.encrypt(content)
            Pair(encrypted.ciphertextBase64, encrypted.ivBase64)
        } else {
            Pair(content, null)
        }

        val entity = ClipEntity(
            content = finalContent,
            encryptedIv = iv,
            category = classification.category,
            tags = classification.tags,
            sourceApp = sourceApp,
            isVaulted = shouldVault,
            charCount = content.length
        )

        clipDao.insertClip(entity)
        return entity
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
        clipDao.clearAllUnpinned()
    }

    override suspend fun pruneRetention(olderThanMillis: Long): Int {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        return clipDao.pruneOldClips(cutoff)
    }
}
