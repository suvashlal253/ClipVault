package com.clipvault.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.clipvault.android.data.local.dao.ClipDao
import com.clipvault.android.data.local.entity.ClipCategory
import com.clipvault.android.data.local.entity.ClipEntity
import com.clipvault.android.data.local.entity.ClipFtsEntity
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
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    companion object {
        const val DATABASE_NAME = "clip_vault_db.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addCallback(DB_CALLBACK).build().also { INSTANCE = it }
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
