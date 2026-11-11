package com.clipvault.android.domain.repository

import android.os.Build
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.domain.classifier.ClipClassifier
import com.clipvault.android.security.SecurityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
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
    suspend fun updateClipTags(clipId: String, tags: List<String>)
    suspend fun toggleClipTag(clipId: String, tag: String)
    suspend fun batchDeleteClips(clipIds: List<String>)
    suspend fun batchMoveToVault(clipIds: List<String>)
    suspend fun batchAssignTag(clipIds: List<String>, tag: String)
    suspend fun exportBackupJson(): String
    suspend fun importBackupJson(jsonPayload: String): Int
    suspend fun deleteClip(clipId: String)
    suspend fun clearHistory()
    suspend fun clearAllHistory()
    suspend fun pruneRetention(olderThanMillis: Long): Int
}

@Singleton
class ClipboardRepositoryImpl @Inject constructor(
    private val clipDao: ClipDao
) : ClipboardRepository {

    // 1. Mutex & Single-Thread Write Dispatcher:
    // Guarantees all rapid consecutive clipboard insertions are serialized strictly in FIFO order,
    // eliminating race conditions between background listeners, foreground sync, and Room transactions.
    private val insertMutex = Mutex()
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sequentialWriteDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Monotonic timestamp tracker so rapid copies within the same millisecond still preserve exact copy order
    private val lastInsertedTimestamp = AtomicLong(0L)

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

    override suspend fun saveNewClip(content: String, sourceApp: String?, autoVault: Boolean): ClipEntity? =
        withContext(sequentialWriteDispatcher + NonCancellable) {
            insertMutex.withLock {
                val trimmed = content.trim()
                if (trimmed.isEmpty()) return@withLock null

                // 0. Ghost/Orphan Clip Guard after Clear All History:
                // If this clip matches the stale OS clipboard text that existed when history was wiped,
                // ignore background/focus re-syncs until the user copies new text or adds manually.
                if (trimmed == suppressedGhostClipContent && sourceApp != "Manual Entry") {
                    return@withLock null
                }
                if (trimmed != suppressedGhostClipContent) {
                    suppressedGhostClipContent = null
                }

                // 1. Position Lock & Duplicate Detection:
                // If the exact clip already exists in Room, keep it locked in its original position
                // without mutating createdAt or causing erratic list reordering when re-copying.
                val existing = clipDao.findClipByContent(trimmed) ?: clipDao.findClipByContent(content)
                if (existing != null) {
                    return@withLock existing
                }

                // 2. Default Dashboard Behavior + Monotonic Timestamp Ordering:
                // Save all newly copied items (English, Bengali, URLs, Emails, etc.) strictly to the
                // main Home Dashboard by default (isVaulted = false, encryptedIv = null) with a strictly
                // increasing timestamp so rapid copies never collide or overwrite each other.
                val classification = ClipClassifier.classify(content)
                val now = System.currentTimeMillis()
                val monotonicCreatedAt = maxOf(now, lastInsertedTimestamp.get() + 1L)
                lastInsertedTimestamp.set(monotonicCreatedAt)

                val entity = ClipEntity(
                    content = content,
                    encryptedIv = null,
                    category = classification.category,
                    createdAt = monotonicCreatedAt,
                    tags = classification.tags,
                    sourceApp = sourceApp,
                    isVaulted = false,
                    charCount = content.length
                )

                clipDao.insertClip(entity)
                entity
            }
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

    override suspend fun updateClipTags(clipId: String, tags: List<String>) =
        withContext(sequentialWriteDispatcher) {
            clipDao.updateClipTags(clipId, tags.distinct())
        }

    override suspend fun toggleClipTag(clipId: String, tag: String) =
        withContext(sequentialWriteDispatcher) {
            val cleanTag = tag.trim()
            if (cleanTag.isEmpty()) return@withContext
            val clip = clipDao.getClipById(clipId) ?: return@withContext
            val currentTags = clip.tags.toMutableList()
            val existingIndex = currentTags.indexOfFirst { it.equals(cleanTag, ignoreCase = true) }
            if (existingIndex >= 0) {
                currentTags.removeAt(existingIndex)
            } else {
                currentTags.add(cleanTag)
            }
            clipDao.updateClipTags(clipId, currentTags)
        }

    override suspend fun batchDeleteClips(clipIds: List<String>): Unit =
        withContext(sequentialWriteDispatcher + NonCancellable) {
            insertMutex.withLock {
                if (clipIds.isEmpty()) return@withLock
                clipDao.deleteClipsByIds(clipIds)
                runCatching { clipDao.rebuildFtsIndex() }
            }
        }

    override suspend fun batchMoveToVault(clipIds: List<String>): Unit =
        withContext(sequentialWriteDispatcher + NonCancellable) {
            insertMutex.withLock {
                for (id in clipIds) {
                    val clip = clipDao.getClipById(id) ?: continue
                    if (!clip.isVaulted) {
                        val encrypted = SecurityHelper.encrypt(clip.content)
                        clipDao.updateVaultStatus(id, true, encrypted.ciphertextBase64, encrypted.ivBase64)
                    }
                }
            }
        }

    override suspend fun batchAssignTag(clipIds: List<String>, tag: String): Unit =
        withContext(sequentialWriteDispatcher + NonCancellable) {
            insertMutex.withLock {
                val cleanTag = tag.trim()
                if (cleanTag.isEmpty()) return@withLock
                for (id in clipIds) {
                    val clip = clipDao.getClipById(id) ?: continue
                    if (clip.tags.none { it.equals(cleanTag, ignoreCase = true) }) {
                        clipDao.updateClipTags(id, clip.tags + cleanTag)
                    }
                }
            }
        }

    /**
     * Cloud Backup Export (Google Drive SAF / Local JSON Backup):
     * Exports all Room database clips (including custom tags and Vault items) as a structured JSON file.
     * For vaulted clips, decrypts plaintext via Keystore so backups can be seamlessly restored on a new device.
     */
    override suspend fun exportBackupJson(): String =
        withContext(Dispatchers.IO) {
            val allClips = clipDao.getAllClipsIncludingVaultSync()
            val root = JSONObject()
            root.put("app", "ClipVault")
            root.put("schemaVersion", 2)
            root.put("exportedAt", System.currentTimeMillis())
            root.put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            root.put("clipCount", allClips.size)

            val clipsArray = JSONArray()
            for (clip in allClips) {
                val itemObj = JSONObject()
                val plainContent = if (clip.isVaulted && clip.encryptedIv != null) {
                    runCatching { SecurityHelper.decrypt(clip.content, clip.encryptedIv) }.getOrDefault(clip.content)
                } else {
                    clip.content
                }
                itemObj.put("id", clip.id)
                itemObj.put("content", plainContent)
                itemObj.put("category", clip.category.name)
                itemObj.put("createdAt", clip.createdAt)
                itemObj.put("isPinned", clip.isPinned)
                itemObj.put("isVaulted", clip.isVaulted)
                itemObj.put("charCount", plainContent.length)
                itemObj.put("sourceApp", clip.sourceApp ?: "Cloud Backup")
                val tagsJson = JSONArray()
                clip.tags.forEach { tagsJson.put(it) }
                itemObj.put("tags", tagsJson)
                clipsArray.put(itemObj)
            }
            root.put("clips", clipsArray)
            root.toString(2)
        }

    /**
     * Cloud Backup Restore (Google Drive SAF / Local JSON Restore):
     * Parses a ClipVault JSON backup file and safely merges/restores all clips into Room SQLite,
     * automatically re-encrypting Vault clips with the current device's hardware-backed Keystore key.
     */
    override suspend fun importBackupJson(jsonPayload: String): Int =
        withContext(sequentialWriteDispatcher + NonCancellable) {
            insertMutex.withLock {
                val root = JSONObject(jsonPayload)
                val clipsArray = root.optJSONArray("clips") ?: return@withLock 0
                val entitiesToInsert = mutableListOf<ClipEntity>()

                for (i in 0 until clipsArray.length()) {
                    val obj = clipsArray.optJSONObject(i) ?: continue
                    val rawContent = obj.optString("content", "").trim()
                    if (rawContent.isEmpty()) continue

                    val isVaulted = obj.optBoolean("isVaulted", false)
                    val categoryName = obj.optString("category", "TEXT")
                    val category = runCatching { ClipCategory.valueOf(categoryName) }
                        .getOrDefault(ClipClassifier.classify(rawContent).category)

                    val tagsList = mutableListOf<String>()
                    val tagsArray = obj.optJSONArray("tags")
                    if (tagsArray != null) {
                        for (t in 0 until tagsArray.length()) {
                            val tagStr = tagsArray.optString(t, "").trim()
                            if (tagStr.isNotEmpty()) tagsList.add(tagStr)
                        }
                    }

                    val (storedContent, storedIv) = if (isVaulted) {
                        val enc = SecurityHelper.encrypt(rawContent)
                        enc.ciphertextBase64 to enc.ivBase64
                    } else {
                        rawContent to null
                    }

                    val clipId = obj.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                    val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                    entitiesToInsert.add(
                        ClipEntity(
                            id = clipId,
                            content = storedContent,
                            encryptedIv = storedIv,
                            category = category,
                            createdAt = createdAt,
                            isPinned = obj.optBoolean("isPinned", false),
                            isVaulted = isVaulted,
                            charCount = rawContent.length,
                            tags = tagsList.distinct(),
                            sourceApp = obj.optString("sourceApp", "Restored Backup")
                        )
                    )
                }

                if (entitiesToInsert.isNotEmpty()) {
                    clipDao.insertClips(entitiesToInsert)
                    runCatching { clipDao.rebuildFtsIndex() }
                }
                entitiesToInsert.size
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
