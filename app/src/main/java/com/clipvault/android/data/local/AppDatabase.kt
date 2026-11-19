package com.clipvault.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.data.local.entity.ClipFtsEntity
import com.clipvault.android.data.local.entity.ClipboardContentNormalizer
import org.json.JSONArray

typealias ClipDatabase = AppDatabase

class Converters {
    @TypeConverter
    fun fromCategory(category: ClipCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): ClipCategory = try {
        ClipCategory.valueOf(value)
    } catch (e: Exception) {
        ClipCategory.TEXT
    }

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return JSONArray(list).toString()
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }
}

/**
 * Metadata-Safe Clipboard Migration Reconciliation Helper (v1.7.4 Requirement 2).
 *
 * Policies:
 * 1. Vaulted encrypted records (isVaulted == true) are NEVER merged as plaintext duplicates and
 *    their contentHash is always set to "" so encrypted ciphertext is never hashed as visible text.
 * 2. Non-vaulted duplicate records (equivalent under [ClipboardContentNormalizer.normalize]) are
 *    merged onto a single keeper row before deleting redundant duplicate rows:
 *    - isPinned: true if ANY duplicate in the group was pinned.
 *    - tags: Case-preserving distinct union of tags from ALL duplicate records in the group.
 *    - sourceApp: Preserves the most specific/informative source app (preferring specific app names
 *      such as "Chrome", "WhatsApp", "Android Studio" over generic capture labels like "Auto-Capture").
 *    - createdAt: Preserves the oldest original createdAt timestamp (minOf { it.createdAt })
 *      to maintain deterministic historical creation order.
 *    - contentHash: Populated with ClipboardContentNormalizer.computeContentHash(keeper.content).
 * 3. Unique non-vaulted clips are never deleted; their contentHash is backfilled.
 */
object ClipboardMigrationHelper {

    private val GENERIC_SOURCE_LABELS = setOf(
        "",
        "auto-capture",
        "instant auto-capture",
        "foreground sync",
        "system clipboard",
        "manual",
        "imported clip",
        "clipboard",
        "clipvault"
    )

    data class LegacyClipRow(
        val id: String,
        val content: String,
        val isPinned: Boolean,
        val tags: List<String>,
        val sourceApp: String?,
        val createdAt: Long,
        val isVaulted: Boolean
    )

    data class ReconciledKeeperUpdate(
        val id: String,
        val content: String,
        val isPinned: Boolean,
        val mergedTags: List<String>,
        val mergedSourceApp: String?,
        val preservedCreatedAt: Long,
        val contentHash: String,
        val isVaulted: Boolean
    ) {
        val keeperId: String get() = id
        val mergedIsPinned: Boolean get() = isPinned
    }

    data class MigrationReconciliationPlan(
        val updates: List<ReconciledKeeperUpdate>,
        val duplicateIdsToDelete: List<String>
    )

    fun mergeDistinctTags(tagLists: Iterable<List<String>>): List<String> {
        val merged = mutableListOf<String>()
        val seenLower = HashSet<String>()
        for (list in tagLists) {
            for (rawTag in list) {
                val clean = rawTag.trim()
                if (clean.isNotEmpty() && seenLower.add(clean.lowercase())) {
                    merged.add(clean)
                }
            }
        }
        return merged
    }

    fun selectMostUsefulSourceApp(candidates: Iterable<String?>): String? {
        val nonBlank = candidates.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (nonBlank.isEmpty()) return null
        val specific = nonBlank.firstOrNull { !GENERIC_SOURCE_LABELS.contains(it.lowercase()) }
        return specific ?: nonBlank.first()
    }

    fun parseTagsJsonSafe(rawJson: String?): List<String> {
        val trimmed = rawJson?.trim() ?: return emptyList()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
        return try {
            val arr = JSONArray(trimmed)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "").trim()
                if (item.isNotEmpty()) list.add(item)
            }
            list
        } catch (_: Exception) {
            val inner = trimmed.removePrefix("[").removeSuffix("]").trim()
            if (inner.isEmpty()) {
                emptyList()
            } else {
                inner.split(",")
                    .map { token ->
                        token.trim()
                            .removeSurrounding("\"")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .trim()
                    }
                    .filter { it.isNotEmpty() }
            }
        }
    }

    fun serializeTagsJson(tags: List<String>): String {
        if (tags.isEmpty()) return "[]"
        return try {
            JSONArray(tags).toString()
        } catch (_: Exception) {
            tags.joinToString(prefix = "[", postfix = "]", separator = ",") { tag ->
                val escaped = tag.replace("\\", "\\\\").replace("\"", "\\\"")
                "\"$escaped\""
            }
        }
    }

    fun reconcileLegacyRows(rows: List<LegacyClipRow>): MigrationReconciliationPlan {
        val updates = mutableListOf<ReconciledKeeperUpdate>()
        val duplicateIdsToDelete = mutableListOf<String>()
        val nonVaultedGroups = LinkedHashMap<String, MutableList<LegacyClipRow>>()

        for (row in rows) {
            if (row.isVaulted) {
                // Never merge vaulted encrypted records or hash their ciphertext as visible text
                updates.add(
                    ReconciledKeeperUpdate(
                        id = row.id,
                        content = row.content,
                        isPinned = row.isPinned,
                        mergedTags = mergeDistinctTags(listOf(row.tags)),
                        mergedSourceApp = row.sourceApp,
                        preservedCreatedAt = row.createdAt,
                        contentHash = "",
                        isVaulted = true
                    )
                )
            } else {
                val normalized = ClipboardContentNormalizer.normalize(row.content)
                if (normalized.isEmpty()) {
                    updates.add(
                        ReconciledKeeperUpdate(
                            id = row.id,
                            content = row.content,
                            isPinned = row.isPinned,
                            mergedTags = mergeDistinctTags(listOf(row.tags)),
                            mergedSourceApp = row.sourceApp,
                            preservedCreatedAt = row.createdAt,
                            contentHash = "",
                            isVaulted = false
                        )
                    )
                } else {
                    nonVaultedGroups.getOrPut(normalized) { mutableListOf() }.add(row)
                }
            }
        }

        for ((normalizedContent, group) in nonVaultedGroups) {
            val keeper = group.sortedWith(
                compareByDescending<LegacyClipRow> { it.isPinned }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            ).first()

            val mergedPinned = group.any { it.isPinned }
            val mergedTags = mergeDistinctTags(group.map { it.tags })
            val mergedSourceApp = selectMostUsefulSourceApp(group.map { it.sourceApp })
            val oldestCreatedAt = group.minOf { it.createdAt }
            val computedHash = ClipboardContentNormalizer.computeContentHash(normalizedContent)

            updates.add(
                ReconciledKeeperUpdate(
                    id = keeper.id,
                    content = keeper.content,
                    isPinned = mergedPinned,
                    mergedTags = mergedTags,
                    mergedSourceApp = mergedSourceApp,
                    preservedCreatedAt = oldestCreatedAt,
                    contentHash = computedHash,
                    isVaulted = false
                )
            )

            for (row in group) {
                if (row.id != keeper.id) {
                    duplicateIdsToDelete.add(row.id)
                }
            }
        }

        return MigrationReconciliationPlan(
            updates = updates,
            duplicateIdsToDelete = duplicateIdsToDelete
        )
    }
}

@Database(
    entities = [ClipEntity::class, ClipFtsEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    companion object {
        const val DATABASE_NAME = "clip_vault_db.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        class FtsMigrationException(
            message: String,
            cause: Throwable? = null
        ) : IllegalStateException(message, cause)

        internal fun installFtsSyncTriggers(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS clips_bu BEFORE UPDATE ON clips BEGIN
                    DELETE FROM clips_fts WHERE docid = old.rowid;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS clips_bd BEFORE DELETE ON clips BEGIN
                    DELETE FROM clips_fts WHERE docid = old.rowid;
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS clips_ai AFTER INSERT ON clips BEGIN
                    INSERT INTO clips_fts(docid, content) VALUES (new.rowid, new.content);
                END;
            """.trimIndent())

            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS clips_au AFTER UPDATE ON clips BEGIN
                    INSERT INTO clips_fts(docid, content) VALUES (new.rowid, new.content);
                END;
            """.trimIndent())
        }

        private fun querySingleLong(db: SupportSQLiteDatabase, sql: String): Long {
            val cursor = db.query(sql)
            try {
                return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } finally {
                cursor.close()
            }
        }

        /**
         * Rebuilds and verifies the FTS4 virtual table (clips_fts) without catching fatal JVM Errors/Throwables.
         *
         * 1. Replaces broad catch (_: Throwable) with specific android.database.sqlite.SQLiteException and
         *    java.sql.SQLException handling.
         * 2. If the initial rebuild command fails because clips_fts was missing or corrupted in a pre-migration
         *    database, performs non-destructive recovery by recreating clips_fts and its sync triggers (never
         *    modifying or deleting rows from clips) and re-running rebuild.
         * 3. Explicitly verifies FTS index consistency (SELECT COUNT(*) FROM clips == SELECT COUNT(*) FROM clips_fts)
         *    and throws [FtsMigrationException] if verification fails so the migration transaction rolls back cleanly.
         */
        internal fun rebuildAndVerifyFtsIndex(db: SupportSQLiteDatabase) {
            installFtsSyncTriggers(db)
            try {
                db.execSQL("INSERT INTO clips_fts(clips_fts) VALUES('rebuild')")
            } catch (sqliteEx: android.database.sqlite.SQLiteException) {
                recoverAndRebuildFtsTable(db, sqliteEx)
            } catch (sqlEx: java.sql.SQLException) {
                recoverAndRebuildFtsTable(db, sqlEx)
            } catch (jdbcEx: IllegalStateException) {
                recoverAndRebuildFtsTable(db, jdbcEx)
            }

            val clipsRowCount = try {
                querySingleLong(db, "SELECT COUNT(*) FROM clips")
            } catch (e: android.database.sqlite.SQLiteException) {
                throw FtsMigrationException("Failed to count clips after FTS rebuild", e)
            } catch (e: java.sql.SQLException) {
                throw FtsMigrationException("Failed to count clips after FTS rebuild", e)
            } catch (e: IllegalStateException) {
                throw FtsMigrationException("Failed to count clips after FTS rebuild", e)
            }

            val ftsRowCount = try {
                querySingleLong(db, "SELECT COUNT(*) FROM clips_fts")
            } catch (e: android.database.sqlite.SQLiteException) {
                throw FtsMigrationException("Failed to query clips_fts during consistency verification", e)
            } catch (e: java.sql.SQLException) {
                throw FtsMigrationException("Failed to query clips_fts during consistency verification", e)
            } catch (e: IllegalStateException) {
                throw FtsMigrationException("Failed to query clips_fts during consistency verification", e)
            }

            if (clipsRowCount != ftsRowCount) {
                throw FtsMigrationException(
                    "FTS index consistency verification failed: clips has $clipsRowCount rows, " +
                        "but clips_fts has $ftsRowCount indexed documents."
                )
            }
        }

        private fun recoverAndRebuildFtsTable(db: SupportSQLiteDatabase, initialCause: Exception) {
            try {
                db.execSQL("DROP TRIGGER IF EXISTS clips_bu")
                db.execSQL("DROP TRIGGER IF EXISTS clips_bd")
                db.execSQL("DROP TRIGGER IF EXISTS clips_ai")
                db.execSQL("DROP TRIGGER IF EXISTS clips_au")
                db.execSQL("DROP TABLE IF EXISTS clips_fts")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS clips_fts USING FTS4(content, content='clips')")
                installFtsSyncTriggers(db)
                db.execSQL("INSERT INTO clips_fts(clips_fts) VALUES('rebuild')")
            } catch (recoverySqliteEx: android.database.sqlite.SQLiteException) {
                throw FtsMigrationException(
                    "FTS index rebuild and non-destructive recovery failed during migration",
                    recoverySqliteEx
                )
            } catch (recoverySqlEx: java.sql.SQLException) {
                throw FtsMigrationException(
                    "FTS index rebuild and non-destructive recovery failed during migration",
                    recoverySqlEx
                )
            } catch (e: FtsMigrationException) {
                throw e
            } catch (e: RuntimeException) {
                throw FtsMigrationException(
                    "Unexpected runtime error during FTS recovery: ${initialCause.message}",
                    e
                )
            }
        }

        /**
         * Metadata-safe atomic migration from v1 to v2 (v1.7.4 Requirements 2 & 3):
         * 1. Executes inside an explicit SQLite transaction if not already in a transaction so any failure
         *    rolls back completely without leaving partially modified data.
         * 2. Adds the content_hash column to clips.
         * 3. Reads all existing rows and reconciles them via [ClipboardMigrationHelper.reconcileLegacyRows]:
         *    - Vaulted records keep content_hash = '' and are never merged as plaintext duplicates.
         *    - Non-vaulted duplicate groups merge tags across all duplicates, preserve is_pinned if any
         *      duplicate was pinned, preserve the most useful source_app, and retain the oldest original
         *      created_at timestamp on the keeper record BEFORE deleting redundant duplicate rows.
         * 4. Creates the index_clips_content_hash index and rebuilds + verifies clips_fts for FTS consistency.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val manageOwnTransaction = !db.inTransaction()
                if (manageOwnTransaction) {
                    db.beginTransaction()
                }
                try {
                    db.execSQL("ALTER TABLE clips ADD COLUMN content_hash TEXT NOT NULL DEFAULT ''")

                    val legacyRows = mutableListOf<ClipboardMigrationHelper.LegacyClipRow>()
                    val cursor = db.query(
                        "SELECT id, content, is_pinned, tags, source_app, created_at, is_vaulted FROM clips"
                    )
                    try {
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(0) ?: continue
                            val content = cursor.getString(1) ?: ""
                            val isPinned = cursor.getInt(2) != 0
                            val rawTags = if (cursor.isNull(3)) "" else cursor.getString(3)
                            val sourceApp = if (cursor.isNull(4)) null else cursor.getString(4)
                            val createdAt = cursor.getLong(5)
                            val isVaulted = cursor.getInt(6) != 0

                            legacyRows.add(
                                ClipboardMigrationHelper.LegacyClipRow(
                                    id = id,
                                    content = content,
                                    isPinned = isPinned,
                                    tags = ClipboardMigrationHelper.parseTagsJsonSafe(rawTags),
                                    sourceApp = sourceApp,
                                    createdAt = createdAt,
                                    isVaulted = isVaulted
                                )
                            )
                        }
                    } finally {
                        cursor.close()
                    }

                    val plan = ClipboardMigrationHelper.reconcileLegacyRows(legacyRows)

                    // 1. Update keeper & unique rows with merged metadata and content_hash BEFORE any deletion
                    for (update in plan.updates) {
                        db.execSQL(
                            """
                            UPDATE clips
                            SET is_pinned = ?,
                                tags = ?,
                                source_app = ?,
                                created_at = ?,
                                content_hash = ?
                            WHERE id = ?
                            """.trimIndent(),
                            arrayOf<Any?>(
                                if (update.isPinned) 1 else 0,
                                ClipboardMigrationHelper.serializeTagsJson(update.mergedTags),
                                update.mergedSourceApp,
                                update.preservedCreatedAt,
                                update.contentHash,
                                update.id
                            )
                        )
                    }

                    // 2. Delete redundant duplicate rows after metadata has been safely persisted on the keeper row
                    for (dupId in plan.duplicateIdsToDelete) {
                        db.execSQL("DELETE FROM clips WHERE id = ?", arrayOf<Any>(dupId))
                    }

                    // 3. Create content_hash index and rebuild + verify FTS4 virtual table index
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_clips_content_hash ON clips(content_hash)")
                    rebuildAndVerifyFtsIndex(db)

                    if (manageOwnTransaction) {
                        db.setTransactionSuccessful()
                    }
                } finally {
                    if (manageOwnTransaction) {
                        db.endTransaction()
                    }
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(DB_CALLBACK)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        val DB_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Initialize SQLite Triggers to keep FTS4 synced with the main table automatically
                installFtsSyncTriggers(db)
            }
        }
    }
}
