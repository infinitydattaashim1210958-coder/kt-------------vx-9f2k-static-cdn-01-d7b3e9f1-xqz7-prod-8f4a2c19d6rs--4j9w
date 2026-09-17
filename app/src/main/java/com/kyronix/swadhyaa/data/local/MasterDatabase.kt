package com.kyronix.swadhyaa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kyronix.swadhyaa.data.local.dao.MasterDao
import com.kyronix.swadhyaa.data.local.entity.InstalledPackageEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookChapterEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookParagraphEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookRefEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookSelectionEntity
import com.kyronix.swadhyaa.data.local.entity.MahabharataAdhyayaEntity
import com.kyronix.swadhyaa.data.local.entity.MahabharataUpakhyanaEntity
import com.kyronix.swadhyaa.data.local.entity.RamayanaBhashyaContentEntity
import com.kyronix.swadhyaa.data.local.entity.VedaBhashyaContentEntity

/**
 * v3 adds: LibraryBookSelectionEntity (user highlights/bookmarks).
 * All prior schema comments preserved. See MIGRATION_2_3 below.
 */
@Database(
    entities = [
        InstalledPackageEntity::class,
        VedaBhashyaContentEntity::class,
        RamayanaBhashyaContentEntity::class,
        MahabharataAdhyayaEntity::class,
        MahabharataUpakhyanaEntity::class,
        LibraryBookChapterEntity::class,
        LibraryBookParagraphEntity::class,
        LibraryBookRefEntity::class,
        LibraryBookSelectionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MasterDatabase : RoomDatabase() {

    abstract fun masterDao(): MasterDao

    companion object {
        private const val DB_NAME = "swadhyay_master"

        @Volatile
        private var INSTANCE: MasterDatabase? = null

        fun getInstance(context: Context): MasterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): MasterDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                MasterDatabase::class.java,
                DB_NAME
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        try { db.execSQL(FTS_TABLE_SQL) } catch (_: Exception) {}
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA synchronous=NORMAL;")
                        db.execSQL("PRAGMA foreign_keys=ON;")
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `library_book_chapters` (
                        `book_id` TEXT NOT NULL, `chapter_id` TEXT NOT NULL,
                        `seq` INTEGER, `heading` TEXT, `is_cover` INTEGER NOT NULL,
                        `heading_bold` INTEGER NOT NULL, `heading_center` INTEGER NOT NULL,
                        `heading_underline` INTEGER NOT NULL, `heading_size` REAL NOT NULL,
                        PRIMARY KEY(`book_id`, `chapter_id`)
                    )""".trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `library_book_paragraphs` (
                        `id` INTEGER NOT NULL, `book_id` TEXT NOT NULL,
                        `chapter_id` TEXT NOT NULL, `seq` INTEGER, `content` TEXT,
                        `is_bold` INTEGER NOT NULL, `is_center` INTEGER NOT NULL,
                        `is_right` INTEGER NOT NULL, `is_underline` INTEGER NOT NULL,
                        `font_size` REAL NOT NULL, PRIMARY KEY(`id`)
                    )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_lib_para_chapter` ON `library_book_paragraphs` (`book_id`, `chapter_id`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `library_book_refs` (
                        `id` INTEGER NOT NULL, `book_id` TEXT NOT NULL,
                        `chapter_id` TEXT NOT NULL, `para_seq` INTEGER NOT NULL,
                        `ref_seq` INTEGER NOT NULL, `ref_number` TEXT, `ref_note` TEXT,
                        PRIMARY KEY(`id`)
                    )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_lib_refs_para` ON `library_book_refs` (`book_id`, `chapter_id`, `para_seq`)")
                try { db.execSQL(FTS_TABLE_SQL) } catch (_: Exception) {}
            }
        }

        /** v3: adds library_book_selections table for user highlights/bookmarks. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `library_book_selections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `book_id` TEXT NOT NULL, `chapter_id` TEXT NOT NULL,
                        `para_seq` INTEGER NOT NULL, `sel_start` INTEGER NOT NULL,
                        `sel_end` INTEGER NOT NULL, `selected_text` TEXT NOT NULL,
                        `kind` TEXT NOT NULL DEFAULT 'highlight',
                        `created_at` INTEGER NOT NULL
                    )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_lib_sel_para` ON `library_book_selections` (`book_id`, `chapter_id`, `para_seq`)")
            }
        }

        private val FTS_TABLE_SQL = """
            CREATE VIRTUAL TABLE IF NOT EXISTS library_book_paragraphs_fts USING fts5(
                content, book_id UNINDEXED, chapter_id UNINDEXED, para_id UNINDEXED
            );""".trimIndent()
    }
}
