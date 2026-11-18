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

        /**
         * Safe migration from v1 to v2 (v1.7.2 Requirement 1):
         * 1. Adds the content_hash column to clips.
         * 2. Backfills content_hash for all existing records using [ClipboardContentNormalizer].
         * 3. Inspects existing non-vaulted records and safely removes redundant duplicates while
         *    preserving pinned status, tags/favorites, and the original record metadata.
         * 4. Creates the index_clips_content_hash index for fast O(1) duplicate lookup.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clips ADD COLUMN content_hash TEXT NOT NULL DEFAULT ''")

                data class LegacyRow(
                    val id: String,
                    val content: String,
                    val isPinned: Int,
                    val tags: String,
                    val createdAt: Long
                )

                val rowsByNormalized = LinkedHashMap<String, MutableList<LegacyRow>>()
                val cursor = db.query("SELECT id, content, is_pinned, tags, created_at, is_vaulted FROM clips")
                try {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        val content = cursor.getString(1) ?: ""
                        val isPinned = cursor.getInt(2)
                        val tags = cursor.getString(3) ?: ""
                        val createdAt = cursor.getLong(4)
                        val isVaulted = cursor.getInt(5)

                        val hash = ClipboardContentNormalizer.computeContentHash(content)
                        db.execSQL("UPDATE clips SET content_hash = ? WHERE id = ?", arrayOf<Any>(hash, id))

                        if (isVaulted == 0) {
                            val norm = ClipboardContentNormalizer.normalize(content)
                            if (norm.isNotEmpty()) {
                                rowsByNormalized.getOrPut(norm) { mutableListOf() }
                                    .add(LegacyRow(id, content, isPinned, tags, createdAt))
                            }
                        }
                    }
                } finally {
                    cursor.close()
                }

                for ((_, group) in rowsByNormalized) {
                    if (group.size > 1) {
                        val keeper = group.sortedWith(
                            compareByDescending<LegacyRow> { it.isPinned }
                                .thenByDescending { if (it.tags.isNotBlank() && it.tags != "[]") 1 else 0 }
                                .thenBy { it.createdAt }
                        ).first()
                        for (duplicate in group) {
                            if (duplicate.id != keeper.id) {
                                db.execSQL("DELETE FROM clips WHERE id = ?", arrayOf<Any>(duplicate.id))
                            }
                        }
                    }
                }

                db.execSQL("CREATE INDEX IF NOT EXISTS index_clips_content_hash ON clips(content_hash)")
                try {
                    db.execSQL("INSERT INTO clips_fts(clips_fts) VALUES('rebuild')")
                } catch (_: Throwable) {
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
        }
    }
}
