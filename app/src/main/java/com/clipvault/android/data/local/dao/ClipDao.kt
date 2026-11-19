package com.clipvault.android.data.local.dao

import androidx.room.*
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.data.local.entity.ClipboardContentNormalizer
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: ClipEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Update
    suspend fun updateClip(clip: ClipEntity)

    @Delete
    suspend fun deleteClip(clip: ClipEntity)

    @Query("DELETE FROM clips WHERE id = :clipId")
    suspend fun deleteClipById(clipId: String)

    @Query("DELETE FROM clips WHERE id IN (:clipIds)")
    suspend fun deleteClipsByIds(clipIds: List<String>)

    @Query("SELECT * FROM clips WHERE is_vaulted = 0 ORDER BY is_pinned DESC, created_at DESC, id ASC")
    fun getAllClipsFlow(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips ORDER BY created_at DESC, id ASC")
    suspend fun getAllClipsIncludingVaultSync(): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE is_vaulted = 0 ORDER BY is_pinned DESC, created_at ASC, id ASC")
    suspend fun getAllNonVaultedClipsSync(): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE is_vaulted = 1 ORDER BY is_pinned DESC, created_at DESC, id ASC")
    fun getVaultClipsFlow(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE is_pinned = 1 AND is_vaulted = 0 ORDER BY created_at DESC, id ASC")
    fun getPinnedClipsFlow(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE category = :category AND is_vaulted = 0 ORDER BY is_pinned DESC, created_at DESC, id ASC")
    fun getClipsByCategoryFlow(category: ClipCategory): Flow<List<ClipEntity>>

    /**
     * Fast Full-Text Search combining Room FTS4 virtual table with primary table
     */
    @Query("""
        SELECT clips.* FROM clips
        JOIN clips_fts ON clips.rowid = clips_fts.docid
        WHERE clips_fts MATCH :query AND clips.is_vaulted = 0
        ORDER BY clips.is_pinned DESC, clips.created_at DESC, clips.id ASC
    """)
    fun searchClips(query: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE content = :content LIMIT 1")
    suspend fun findClipByContent(content: String): ClipEntity?

    @Query("SELECT * FROM clips WHERE content_hash = :contentHash AND is_vaulted = 0")
    suspend fun findClipsByContentHash(contentHash: String): List<ClipEntity>

    /**
     * Targeted indexed lookup for any legacy non-vaulted rows whose content_hash has not yet been populated.
     * Avoids scanning the entire clipboard table when checking for legacy duplicates (v1.7.4 Requirement 4).
     */
    @Query("SELECT * FROM clips WHERE content_hash = '' AND is_vaulted = 0")
    suspend fun findUnindexedLegacyClips(): List<ClipEntity>

    @Query("UPDATE clips SET content_hash = :contentHash WHERE id = :clipId")
    suspend fun updateClipContentHash(clipId: String, contentHash: String)

    @Query("UPDATE clips SET is_pinned = :isPinned WHERE id = :clipId")
    suspend fun updatePinStatus(clipId: String, isPinned: Boolean)

    @Query("UPDATE clips SET tags = :tags WHERE id = :clipId")
    suspend fun updateClipTags(clipId: String, tags: List<String>)

    @Query("SELECT * FROM clips WHERE id = :clipId LIMIT 1")
    suspend fun getClipById(clipId: String): ClipEntity?

    // Position Lock + Content Hash Consistency (v1.7.4 Requirement 3):
    // Preserves original created_at timestamp while updating content, char_count, category, and normalized content_hash
    @Query("UPDATE clips SET content = :newContent, char_count = :charCount, category = :category, content_hash = :contentHash WHERE id = :clipId")
    suspend fun updateClipContent(
        clipId: String,
        newContent: String,
        charCount: Int,
        category: ClipCategory,
        contentHash: String = ClipboardContentNormalizer.computeContentHash(newContent)
    )

    // Vault Edit (v1.7.4 Requirement 3):
    // Re-encrypts modified plaintext with a fresh AES-256-GCM IV while clearing content_hash so ciphertext is never hashed as visible text
    @Query("UPDATE clips SET content = :encryptedContent, encrypted_iv = :iv, char_count = :charCount, category = :category, content_hash = '' WHERE id = :clipId")
    suspend fun updateEncryptedClipContent(
        clipId: String,
        encryptedContent: String,
        iv: String,
        charCount: Int,
        category: ClipCategory
    )

    // Vault Transition (v1.7.4 Requirement 3):
    // Clears content_hash when a clip enters Vault (isVaulted = true) and restores normalized plaintext content_hash when unvaulted
    @Query("UPDATE clips SET is_vaulted = :isVaulted, content = :newContent, encrypted_iv = :iv, content_hash = :contentHash WHERE id = :clipId")
    suspend fun updateVaultStatus(
        clipId: String,
        isVaulted: Boolean,
        newContent: String,
        iv: String?,
        contentHash: String = if (isVaulted) "" else ClipboardContentNormalizer.computeContentHash(newContent)
    )

    @Query("SELECT content FROM clips WHERE is_vaulted = 0 ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestClipContent(): String?

    @Query("DELETE FROM clips WHERE is_vaulted = 0 AND is_pinned = 0")
    suspend fun clearAllUnpinned()

    @Query("DELETE FROM clips WHERE is_vaulted = 0")
    suspend fun clearAllHistory()

    @Query("INSERT INTO clips_fts(clips_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()

    /**
     * Retention policy: Delete unpinned, unvaulted clips older than cutoff timestamp
     */
    @Query("DELETE FROM clips WHERE is_pinned = 0 AND is_vaulted = 0 AND created_at < :cutoffTimestamp")
    suspend fun pruneOldClips(cutoffTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM clips WHERE is_vaulted = 0")
    fun getClipCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clips WHERE is_vaulted = 1")
    fun getVaultCountFlow(): Flow<Int>
}
