package com.kyronix.swadhyaa.data.repository

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.content.Context
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One verse of the base Gita text.
 * [speaker] is a bonus the real schema turned out to carry (see class doc
 * comment) — who says this verse (কৃষ্ণ/অর্জুন/সঞ্জয়/ধৃতরাষ্ট্র). Null if
 * the pack row has it blank; not every verse dataset fills this in.
 */
data class GitaShloka(
    val adhyaya: Int,
    val shloka: Int,
    val devanagari: String,
    val transliteration: String?,
    val speaker: String? = null
)

/**
 * Base Gita verse text (Devanagari + transliteration), downloaded from
 * `gita_bhasya/slok_deva_translit_db.gz`. Unlike Veda mantras / Ramayana
 * shlokas — bundled into `core.db` / `ramayana_core.db` and covered by the
 * Database Gate — this text is NOT bundled: it's an on-demand pack like every
 * scholar's bhashya. [GitaActivity] gates the whole reader on [isDownloaded]
 * before any [GitaBhashyaRepository] entry can be shown against a verse.
 *
 * SCHEMA — CONFIRMED 2026-09-11 from a live run's self-diagnostic dump (see
 * [dumpSchema] / the "no such table: shlokas" error it replaced):
 *
 *   shlok(id, chapter, verse, verse_id, speaker, slok, transliteration)
 *
 * i.e. the original guess had both the table name (`shlokas` → `shlok`) and
 * every column name wrong (`adhyaya`→`chapter`, `shloka`→`verse`,
 * `deva_text`→`slok`, `translit_text`→`transliteration`) — this matches the
 * column layout of several well-known public Gita verse datasets, for
 * reference if another pack in this family needs the same treatment.
 * `verse_id` (a probable global 1..~700 running number) and `id` (likely the
 * SQLite rowid) both exist but aren't needed here — every query below
 * addresses by (chapter, verse) instead, which is stable and human-readable.
 * `sqlite_sequence` is SQLite's own autoincrement bookkeeping table, not
 * app data — ignored.
 *
 * The self-diagnosing [withPack] wrapper is kept even though the schema is
 * now known, since [GitaBhashyaRepository]'s scholar-pack schema is still
 * unconfirmed and may need the same treatment.
 */
object GitaCoreTextRepository {

    private const val FOLDER = "gita_bhasya"
    private const val TABLE = "shlok"

    /** The Gita always has 18 adhyayas — this is textual fact, not pack-dependent. */
    val ADHYAYA_OPTIONS: List<Int> = (1..18).toList()

    // Real column names aliased to the domain names used everywhere else in
    // this file/GitaShloka, so only this one constant needs to change if a
    // future pack in the same family uses yet another naming convention.
    private const val COLUMNS =
        "chapter AS adhyaya, verse AS shloka, slok AS deva_text, transliteration AS translit_text, speaker"

    fun isDownloaded(context: Context): Boolean =
        PackDownloadManager.isDownloaded(context, FOLDER, GitaManifest.CORE_TEXT_PACK_FILE)

    suspend fun downloadIfNeeded(
        context: Context,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        PackDownloadManager.openPack(context, FOLDER, GitaManifest.CORE_TEXT_PACK_FILE, onProgress)
            .map { it.close() }
    }

    /** Every shloka number within one adhyaya, ascending — for the শ্লোক jump box. */
    suspend fun getShlokaOptions(context: Context, adhyaya: Int): Result<List<Int>> =
        withContext(Dispatchers.IO) {
            withPack(context) { database ->
                val list = mutableListOf<Int>()
                database.rawQuery(
                    "SELECT verse FROM $TABLE WHERE chapter = ? ORDER BY verse",
                    arrayOf(adhyaya.toString())
                ).use { c -> while (c.moveToNext()) list.add(c.getInt(0)) }
                list
            }
        }

    /** A single verse, or null if (adhyaya, shloka) doesn't exist in the pack. */
    suspend fun getVerse(context: Context, adhyaya: Int, shloka: Int): Result<GitaShloka?> =
        withContext(Dispatchers.IO) {
            withPack(context) { database ->
                database.rawQuery(
                    "SELECT $COLUMNS FROM $TABLE WHERE chapter = ? AND verse = ? LIMIT 1",
                    arrayOf(adhyaya.toString(), shloka.toString())
                ).use { c -> if (c.moveToFirst()) c.toShloka() else null }
            }
        }

    /** The very first verse (1/1) — used when the reader opens with no saved position. */
    suspend fun getFirstVerse(context: Context): Result<GitaShloka?> =
        withContext(Dispatchers.IO) {
            withPack(context) { database ->
                database.rawQuery(
                    "SELECT $COLUMNS FROM $TABLE ORDER BY chapter, verse LIMIT 1", null
                ).use { c -> if (c.moveToFirst()) c.toShloka() else null }
            }
        }

    /**
     * Next verse in reading order, spanning adhyaya boundaries. Ordered by
     * (chapter, verse) rather than assuming a contiguous `verse_id` — same
     * defensive approach as [MahabharataRepository.getAdjacentAdhyayas].
     */
    suspend fun getNext(context: Context, adhyaya: Int, shloka: Int): Result<GitaShloka?> =
        withContext(Dispatchers.IO) {
            withPack(context) { database ->
                database.rawQuery(
                    "SELECT $COLUMNS FROM $TABLE " +
                        "WHERE (chapter = ? AND verse > ?) OR chapter > ? " +
                        "ORDER BY chapter ASC, verse ASC LIMIT 1",
                    arrayOf(adhyaya.toString(), shloka.toString(), adhyaya.toString())
                ).use { c -> if (c.moveToFirst()) c.toShloka() else null }
            }
        }

    suspend fun getPrev(context: Context, adhyaya: Int, shloka: Int): Result<GitaShloka?> =
        withContext(Dispatchers.IO) {
            withPack(context) { database ->
                database.rawQuery(
                    "SELECT $COLUMNS FROM $TABLE " +
                        "WHERE (chapter = ? AND verse < ?) OR chapter < ? " +
                        "ORDER BY chapter DESC, verse DESC LIMIT 1",
                    arrayOf(adhyaya.toString(), shloka.toString(), adhyaya.toString())
                ).use { c -> if (c.moveToFirst()) c.toShloka() else null }
            }
        }

    /**
     * Opens the pack and runs [block]; on ANY exception (wrong table/column
     * name, anything), re-throws with the pack's real schema appended so a
     * future mismatch (e.g. in a sibling pack) is self-explanatory instead of
     * a dead end. This is how the `shlok` schema above was actually found.
     */
    private suspend fun <T> withPack(context: Context, block: (SQLiteDatabase) -> T): Result<T> =
        PackDownloadManager.openPack(context, FOLDER, GitaManifest.CORE_TEXT_PACK_FILE).mapCatching { sqlite ->
            sqlite.use { database ->
                try {
                    block(database)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "${e.message}\n\n--- এই প্যাকের প্রকৃত schema ---\n${dumpSchema(database)}", e
                    )
                }
            }
        }

    /** Public too, in case something wants to check schema without waiting for a failure. */
    fun dumpSchema(database: SQLiteDatabase): String {
        val tables = mutableListOf<String>()
        database.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null)
            .use { c -> while (c.moveToNext()) tables.add(c.getString(0)) }
        if (tables.isEmpty()) return "(কোনো table পাওয়া যায়নি)"
        return tables.joinToString("\n") { table ->
            val cols = mutableListOf<String>()
            database.rawQuery("PRAGMA table_info($table)", null)
                .use { c -> while (c.moveToNext()) cols.add(c.getString(1)) } // column 1 = name
            "$table(${cols.joinToString(", ")})"
        }
    }

    private fun Cursor.toShloka() = GitaShloka(
        adhyaya = getInt(0),
        shloka = getInt(1),
        devanagari = getString(2),
        transliteration = getStringOrNull(3),
        speaker = getStringOrNull(4)
    )

    /** [Cursor] has no built-in null-safe string getter; small local helper for readability. */
    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
