package com.kyronix.swadhyaa.data.repository

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.content.Context
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One verse of the base Gita text. [transliteration] is nullable in case a
 *  given pack row only carries the Devanagari form. */
data class GitaShloka(
    val adhyaya: Int,
    val shloka: Int,
    val devanagari: String,
    val transliteration: String?
)

/**
 * Base Gita verse text (Devanagari + transliteration), downloaded from
 * `gita_bhasya/slok_deva_translit_db.gz`. Unlike Veda mantras / Ramayana
 * shlokas — bundled into `core.db` / `ramayana_core.db` and covered by the
 * Database Gate — this text is NOT bundled: it's an on-demand pack like every
 * scholar's bhashya. [GitaActivity] gates the whole reader on [isDownloaded]
 * before any [GitaBhashyaRepository] entry can be shown against a verse.
 *
 * SCHEMA STATUS (2026-09-11): the original `shlokas(adhyaya, shloka,
 * deva_text, translit_text)` guess was WRONG — confirmed by a live run:
 * `Error: no such table: shlokas`. Rather than guess a second time blind,
 * every query below is wrapped by [withPack] so that on ANY failure — table
 * or column name wrong, doesn't matter which — the real table/column names
 * get appended to the exception message via [dumpSchema]. That message is
 * exactly what [com.kyronix.swadhyaa.presentation.gita.GitaViewModel] already
 * surfaces verbatim in `GitaUiState.error`, and exactly what the reader
 * screen already renders on screen (as seen in the "Error: no such table:
 * shlokas…" screenshot) — so the ACTUAL schema will appear on-screen on the
 * next attempt with no new UI needed. Once that's known, replace [TABLE]
 * and the column names in every query below with the real ones.
 */
object GitaCoreTextRepository {

    private const val FOLDER = "gita_bhasya"

    // ⚠ UNCONFIRMED — replace once the on-screen error (see doc comment above)
    // shows the real name after this file is deployed.
    private const val TABLE = "shlokas"

    /** The Gita always has 18 adhyayas — this is textual fact, not pack-dependent. */
    val ADHYAYA_OPTIONS: List<Int> = (1..18).toList()

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
                    "SELECT shloka FROM $TABLE WHERE adhyaya = ? ORDER BY shloka",
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
                    "SELECT adhyaya, shloka, deva_text, translit_text FROM $TABLE " +
                        "WHERE adhyaya = ? AND shloka = ? LIMIT 1",
                    arrayOf(adhyaya.toString(), shloka.toString())
                ).use { c -> if (c.moveToFirst()) c.toShloka() else null }
            }
        }

    /** The very first verse (1/1) — used when the reader opens with no saved position. */
    suspend fun getFirstVerse(context: Context): Result<GitaShloka?> =
        withContext(Dispatchers.IO) {
            withPack(context) { database ->
                database.rawQuery(
                    "SELECT adhyaya, shloka, deva_text, translit_text FROM $TABLE " +
                        "ORDER BY adhyaya, shloka LIMIT 1", null
                ).use { c -> if (c.moveToFirst()) c.toShloka() else null }
            }
        }

    /**
     * Next verse in reading order, spanning adhyaya boundaries. Ordered by
     * (adhyaya, shloka) rather than assuming a contiguous numbering — same
     * defensive approach as [MahabharataRepository.getAdjacentAdhyayas].
     */
    suspend fun getNext(context: Context, adhyaya: Int, shloka: Int): Result<GitaShloka?> =
        withContext(Dispatchers.IO) {
            withPack(context) { database ->
                database.rawQuery(
                    "SELECT adhyaya, shloka, deva_text, translit_text FROM $TABLE " +
                        "WHERE (adhyaya = ? AND shloka > ?) OR adhyaya > ? " +
                        "ORDER BY adhyaya ASC, shloka ASC LIMIT 1",
                    arrayOf(adhyaya.toString(), shloka.toString(), adhyaya.toString())
                ).use { c -> if (c.moveToFirst()) c.toShloka() else null }
            }
        }

    suspend fun getPrev(context: Context, adhyaya: Int, shloka: Int): Result<GitaShloka?> =
        withContext(Dispatchers.IO) {
            withPack(context) { database ->
                database.rawQuery(
                    "SELECT adhyaya, shloka, deva_text, translit_text FROM $TABLE " +
                        "WHERE (adhyaya = ? AND shloka < ?) OR adhyaya < ? " +
                        "ORDER BY adhyaya DESC, shloka DESC LIMIT 1",
                    arrayOf(adhyaya.toString(), shloka.toString(), adhyaya.toString())
                ).use { c -> if (c.moveToFirst()) c.toShloka() else null }
            }
        }

    /**
     * Opens the pack and runs [block]; on ANY exception (wrong table name,
     * wrong column name, anything), re-throws with the pack's real schema
     * appended so the error reaching the screen is self-explanatory instead
     * of a dead end. See class doc comment for why this exists.
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
        transliteration = getStringOrNull(3)
    )

    /** [Cursor] has no built-in null-safe string getter; small local helper for readability. */
    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
