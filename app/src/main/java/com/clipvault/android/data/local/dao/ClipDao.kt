package com.clipvault.android.data.local.dao

import androidx.room.*
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
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

    @Query("SELECT * FROM clips WHERE is_vaulted = 0 ORDER BY is_pinned DESC, created_at DESC")
    fun getAllClipsFlow(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE is_vaulted = 1 ORDER BY is_pinned DESC, created_at DESC")
    fun getVaultClipsFlow(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE is_pinned = 1 AND is_vaulted = 0 ORDER BY created_at DESC")
    fun getPinnedClipsFlow(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE category = :category AND is_vaulted = 0 ORDER BY is_pinned DESC, created_at DESC")
    fun getClipsByCategoryFlow(category: ClipCategory): Flow<List<ClipEntity>>

    /**
     * Fast Full-Text Search combining Room FTS4 virtual table with primary table
     */
    @Query("""
        SELECT clips.* FROM clips
        JOIN clips_fts ON clips.rowid = clips_fts.docid
        WHERE clips_fts MATCH :query AND clips.is_vaulted = 0
        ORDER BY clips.is_pinned DESC, clips.created_at DESC
    """)
    fun searchClips(query: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE content = :content LIMIT 1")
    suspend fun findClipByContent(content: String): ClipEntity?

    @Query("UPDATE clips SET is_pinned = :isPinned WHERE id = :clipId")
    suspend fun updatePinStatus(clipId: String, isPinned: Boolean)

    @Query("UPDATE clips SET is_vaulted = :isVaulted, content = :newContent, encrypted_iv = :iv WHERE id = :clipId")
    suspend fun updateVaultStatus(clipId: String, isVaulted: Boolean, newContent: String, iv: String?)

    @Query("DELETE FROM clips WHERE is_vaulted = 0 AND is_pinned = 0")
    suspend fun clearAllUnpinned()

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
