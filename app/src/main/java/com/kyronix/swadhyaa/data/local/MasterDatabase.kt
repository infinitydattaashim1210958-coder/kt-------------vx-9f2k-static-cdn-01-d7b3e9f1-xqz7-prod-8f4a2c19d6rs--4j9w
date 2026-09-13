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
 *
 * ── SCHEMA VERSION HISTORY (crash fix, see BUGFIX below) ──
 * v1: installed_packages, veda_bhashya_contents, ramayana_kanda_bhashya_contents,
 *     mahabharata_adhyayas, mahabharata_upakhyanas.
 * v2: added library_book_chapters / library_book_paragraphs / library_book_refs
 *     (LibraryBookChapterEntity/ParagraphEntity/RefEntity, added this pass for
 *     Digital Library) + the library_book_paragraphs_fts virtual table.
 *
 * BUGFIX: these three Library entities were added to the `entities = [...]`
 * list below without bumping `version`. Room stores a schema identity hash
 * inside the db file itself and validates it on every open — regardless of
 * whether `version` changed — so ANY device that already had a
 * swadhyay_master.db file on disk from before this pass (i.e. had ever
 * opened this database even once) would fail that validation with
 * `IllegalStateException: Room cannot verify the data integrity...` the
 * moment MasterDatabase.getInstance() is next called. Fixed by bumping
 * version to 2 and adding MIGRATION_1_2 below, which creates exactly the
 * tables/index Room now expects instead of silently redefining the whole
 * database.
 *
 * ⚠ Going forward: any time a new @Entity is added to (or a column/index is
 * changed in) the `entities` list below, `version` MUST be bumped and a
 * matching Migration MUST be added here — otherwise this exact crash comes
 * back for anyone who already has the app installed.
 *
 * ── PRAGMA-INSIDE-TRANSACTION BUGFIXES ──
 * Room's onCreate() callback runs its entire body inside one implicit
 * transaction (so schema creation is atomic). SQLite refuses to change
 * "safety level" pragmas — journal_mode, synchronous — while a transaction
 * is open ("Safety level may not be changed inside a transaction"), and
 * silently no-ops foreign_keys in the same situation. Two of these were
 * previously sent via execSQL() inside onCreate() and both failed the
 * moment onCreate() actually ran for the first time (it never had, until
 * the schema-version crash above was fixed):
 *   - journal_mode=WAL additionally throws its own separate error even
 *     outside a transaction, because it's the one pragma that always
 *     returns its resulting value as a row — fixed by using Room's own
 *     .setJournalMode() builder option instead of raw SQL at all.
 *   - synchronous=NORMAL and foreign_keys=ON are fixed by moving them to
 *     onOpen(), which runs after onCreate/migration's transaction has
 *     already committed, and — usefully for foreign_keys specifically —
 *     on every single database open, which it needs anyway since SQLite
 *     doesn't persist that pragma across connections the way it persists
 *     journal_mode in the db file.
 * This whole family of bugs is already documented, independently, in
 * master-db.js's own comments (the JS reference implementation this was
 * ported from) — that file already solved it with a transaction-free
 * "bare statement" path. These fixes are the Room-idiomatic equivalent.
 *
 * ── FTS5-UNAVAILABLE-ON-DEVICE BUGFIX ──
 * Some Android builds' system SQLite has no FTS5 module compiled in at
 * all ("no such module: fts5") — a device/OS capability gap, not a bug in
 * this SQL. FTS5 has only been reliably bundled since Android 11; older
 * versions and some OEM builds never compiled it in. Since creating the
 * FTS5 table used to happen unguarded inside onCreate(), hitting this on
 * such a device made MasterDatabase.getInstance() itself throw — breaking
 * Digital Library entirely, not just search. Table creation is now
 * wrapped in try/catch (both onCreate and the migration), and
 * MasterDao's installLibraryDbBook/removeLibraryDbBook also wrap their
 * FTS reads/writes for the same reason (see MasterDao.kt) — library book
 * search already has a LIKE-based fallback (searchLibraryBookLike) for
 * exactly this situation; the rest of the app just needs to not crash
 * before reaching it.
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
    version = 2,
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
                // journal_mode=WAL must go through Room's own builder option,
                // not raw SQL — see the class-level doc above.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Room only creates tables for its declared @Entity
                        // classes — it has no concept of FTS5 virtual
                        // tables, so this one (needed for library book
                        // search) has to be created by hand, exactly as
                        // legacy's master-db.js does with its own msExec()
                        // call. Verified against that exact DDL, including
                        // the UNINDEXED columns and the rowid = paragraph
                        // id convention LibraryDbBookRepository relies on.
                        //
                        // Only runs for a database file created fresh at
                        // the CURRENT version — a device migrating up from
                        // v1 never gets this callback (see MIGRATION_1_2,
                        // which creates this same table by hand too).
                        //
                        // Wrapped: see the FTS5-unavailable-on-device
                        // bugfix note in the class-level doc above.
                        try {
                            db.execSQL(FTS_TABLE_SQL)
                        } catch (e: Exception) {
                            // No FTS5 module on this device — library book
                            // search will use the LIKE fallback instead.
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Runs after any onCreate/migration transaction has
                        // committed — safe for "safety level" pragmas — and
                        // on EVERY open, which foreign_keys specifically
                        // needs: SQLite does not persist that pragma in the
                        // db file the way it persists journal_mode, so it
                        // has to be re-applied per connection, not just once
                        // at creation time. See class-level doc above.
                        db.execSQL("PRAGMA synchronous=NORMAL;")
                        db.execSQL("PRAGMA foreign_keys=ON;")
                    }
                })
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        /**
         * Recreates, by hand, exactly what Room's compiler generates today
         * for LibraryBookChapterEntity / LibraryBookParagraphEntity /
         * LibraryBookRefEntity (see MasterEntities.kt) — column names/types/
         * nullability and indices all matched field-for-field against those
         * @Entity/@ColumnInfo/@Index annotations — plus the FTS5 table
         * (see onCreate's comment above for why that needs re-creating here
         * too, since onCreate() won't fire for an already-existing db file).
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_book_chapters` (
                        `book_id` TEXT NOT NULL,
                        `chapter_id` TEXT NOT NULL,
                        `seq` INTEGER,
                        `heading` TEXT,
                        `is_cover` INTEGER NOT NULL,
                        `heading_bold` INTEGER NOT NULL,
                        `heading_center` INTEGER NOT NULL,
                        `heading_underline` INTEGER NOT NULL,
                        `heading_size` REAL NOT NULL,
                        PRIMARY KEY(`book_id`, `chapter_id`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_book_paragraphs` (
                        `id` INTEGER NOT NULL,
                        `book_id` TEXT NOT NULL,
                        `chapter_id` TEXT NOT NULL,
                        `seq` INTEGER,
                        `content` TEXT,
                        `is_bold` INTEGER NOT NULL,
                        `is_center` INTEGER NOT NULL,
                        `is_right` INTEGER NOT NULL,
                        `is_underline` INTEGER NOT NULL,
                        `font_size` REAL NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_lib_para_chapter` " +
                        "ON `library_book_paragraphs` (`book_id`, `chapter_id`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_book_refs` (
                        `id` INTEGER NOT NULL,
                        `book_id` TEXT NOT NULL,
                        `chapter_id` TEXT NOT NULL,
                        `para_seq` INTEGER NOT NULL,
                        `ref_seq` INTEGER NOT NULL,
                        `ref_number` TEXT,
                        `ref_note` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_lib_refs_para` " +
                        "ON `library_book_refs` (`book_id`, `chapter_id`, `para_seq`)"
                )

                // Wrapped: see the FTS5-unavailable-on-device bugfix note
                // in the class-level doc above (same guard as onCreate).
                try {
                    db.execSQL(FTS_TABLE_SQL)
                } catch (e: Exception) {
                    // No FTS5 module on this device — LIKE fallback will
                    // handle search.
                }
            }
        }

        private val FTS_TABLE_SQL = """
            CREATE VIRTUAL TABLE IF NOT EXISTS library_book_paragraphs_fts USING fts5(
                content, book_id UNINDEXED, chapter_id UNINDEXED, para_id UNINDEXED
            );
        """.trimIndent()
    }
}
