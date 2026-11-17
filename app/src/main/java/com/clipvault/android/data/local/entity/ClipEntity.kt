package com.clipvault.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class ClipCategory {
    TEXT,
    URL,
    CODE,
    EMAIL,
    PHONE,
    OTP,
    SENSITIVE,
    COLOR,
    MARKDOWN
}

@Entity(
    tableName = "clips",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["is_pinned"]),
        Index(value = ["is_vaulted"]),
        Index(value = ["category"])
    ]
)
data class ClipEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "content")
    val content: String,

    // When isVaulted == true, content is AES-256-GCM encrypted ciphertext in Base64
    @ColumnInfo(name = "encrypted_iv")
    val encryptedIv: String? = null,

    @ColumnInfo(name = "category")
    val category: ClipCategory,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "is_vaulted")
    val isVaulted: Boolean = false,

    @ColumnInfo(name = "char_count")
    val charCount: Int = content.length,

    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),

    @ColumnInfo(name = "source_app")
    val sourceApp: String? = null
)

/**
 * FTS4 Full-Text Search virtual table for instant search across millions of clips.
 * contentEntity points to clips table, keeping the FTS index synchronized.
 */
@Entity(tableName = "clips_fts")
@Fts4(contentEntity = ClipEntity::class)
data class ClipFtsEntity(
    @ColumnInfo(name = "content")
    val content: String
)
