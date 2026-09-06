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
import com.kyronix.swadhyaa.data.local.entity.MahabharataAdhyayaEntity
import com.kyronix.swadhyaa.data.local.entity.MahabharataUpakhyanaEntity
import com.kyronix.swadhyaa.data.local.entity.RamayanaBhashyaContentEntity
import com.kyronix.swadhyaa.data.local.entity.VedaBhashyaContentEntity

/**
 * Writable master database. Created empty on first launch.
 * All content arrives via pack merges (WorkManager).
 *
 * Migration policy: additive only. Never drop tables or columns.
 *
 * ── ARCHITECTURE STATUS (see RISK_REGISTER.md R5, DATABASE_CONTRACT.md §4) ──
 * UPDATE: now wired in for Digital Library's "db"-type books
 * (LibraryDbBookRepository merges downloaded book packs here and queries
 * them here — see that class). Veda/Ramayana/Mahabharata bhāṣya remain on
 * the pre-existing direct-pack-query path (BhashyaRepository,
 * RamayanaBhashyaRepository, MahabharataRepository via
 * PackDownloadManager) — this pass deliberately did not touch those three,
 * to avoid risking already-working functionality without the ability to
 * run a real build in this environment. So: this class is no longer
 * entirely dead code, but it's also not yet the single consistent
 * architecture DATABASE_CONTRACT.md §4 discusses — Library uses it,
 * the other three corpora's bhāṣya packs still don't. That remains a
 * decision for a future pass: finish the consolidation (wire the other
 * three in too) or accept the split deliberately and document it as such
 * rather than as an oversight.
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
        LibraryBookRefEntity::class
    ],
    version = 1,
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
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Match master-db.js PRAGMAs for crash safety + concurrent reads.
                        db.execSQL("PRAGMA journal_mode=WAL;")
                        db.execSQL("PRAGMA synchronous=NORMAL;")
                        db.execSQL("PRAGMA foreign_keys=ON;")

                        // Room only creates tables for its declared @Entity
                        // classes — it has no concept of FTS5 virtual
                        // tables, so this one (needed for library book
                        // search) has to be created by hand, exactly as
                        // legacy's master-db.js does with its own msExec()
                        // call. Verified against that exact DDL, including
                        // the UNINDEXED columns and the rowid = paragraph
                        // id convention LibraryDbBookRepository relies on.
                        db.execSQL(
                            """
                            CREATE VIRTUAL TABLE IF NOT EXISTS library_book_paragraphs_fts USING fts5(
                                content, book_id UNINDEXED, chapter_id UNINDEXED, para_id UNINDEXED
                            );
                            """.trimIndent()
                        )
                    }
                })
                // Future migrations go here. Additive only.
                // .addMigrations(MIGRATION_1_2)
                .build()
        }

        // Example future migration template (do not enable until needed)
        /*
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE installed_packages ADD COLUMN extra TEXT")
            }
        }
        */
    }
}
