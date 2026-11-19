package com.clipvault.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest
import java.text.Normalizer
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

/**
 * Deterministic clipboard text normalizer and SHA-256 content-hash generator (v1.7.2 Requirement 1).
 *
 * Policy:
 * - Converts CRLF ("\r\n") and CR ("\r") line endings to canonical LF ("\n").
 * - Trims leading and trailing outer whitespace without altering internal spacing or meaningful text.
 * - Applies Unicode Canonical Decomposition followed by Canonical Composition (NFC) so visually
 *   and canonically equivalent Unicode strings produce the exact same normalized representation and hash.
 */
object ClipboardContentNormalizer {

    fun normalize(rawText: String): String {
        if (rawText.isEmpty()) return ""
        val unifiedLineEndings = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (unifiedLineEndings.isEmpty()) return ""
        return try {
            Normalizer.normalize(unifiedLineEndings, Normalizer.Form.NFC)
        } catch (_: Throwable) {
            unifiedLineEndings
        }
    }

    fun computeContentHash(rawOrNormalizedText: String): String {
        val normalized = normalize(rawOrNormalizedText)
        if (normalized.isEmpty()) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
            val hex = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                hex.append(String.format("%02x", b))
            }
            hex.toString()
        } catch (_: Throwable) {
            normalized.hashCode().toUInt().toString(16)
        }
    }

    fun normalizeForComparison(rawText: String): String = normalize(rawText)

    fun areEquivalent(first: String, second: String): Boolean {
        val normA = normalize(first)
        val normB = normalize(second)
        return normA.isNotEmpty() && normA == normB
    }
}

@Entity(
    tableName = "clips",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["is_pinned"]),
        Index(value = ["is_vaulted"]),
        Index(value = ["category"]),
        Index(value = ["content_hash"])
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
    val sourceApp: String? = null,

    // Vaulted items store AES-256-GCM ciphertext in content; never hash ciphertext as visible text
    @ColumnInfo(name = "content_hash", defaultValue = "")
    val contentHash: String = if (isVaulted) "" else ClipboardContentNormalizer.computeContentHash(content)
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
