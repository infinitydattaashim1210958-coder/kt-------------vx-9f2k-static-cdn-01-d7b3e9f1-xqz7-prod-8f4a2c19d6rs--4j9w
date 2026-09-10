package com.kyronix.swadhyaa.data.repository

import android.database.Cursor
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

/** Result of a next/prev lookup — null fields mean "no such neighbour" (start/end of text). */
data class GitaAdjacentVerse(val adhyaya: Int?, val shloka: Int?)

/**
 * Base Gita verse text (Devanagari + transliteration), downloaded from
 * `gita_bhasya/slok_deva_translit_db.gz`. Unlike Veda mantras / Ramayana
 * shlokas — bundled into `core.db` / `ramayana_core.db` and covered by the
 * Database Gate — this text is NOT bundled: it's an on-demand pack like every
 * scholar's bhashya (see [GitaManifest]'s doc comment for why this file, not
 * a scholar pack, was picked as the source of the base text). [GitaActivity]
 * gates the whole reader on [isDownloaded] the same way
 * [com.kyronix.swadhyaa.presentation.mahabharata.MahabharataActivity] gates
 * on its parba pack, before any [GitaBhashyaRepository] entry can be shown
 * against a specific verse.
 *
 * SCHEMA ASSUMPTION (unverified — see [GitaBhashyaRepository]'s doc comment
 * and its `inspectSchema` helper, which works against this pack too):
 *   shlokas(adhyaya INTEGER, shloka INTEGER, deva_text TEXT, translit_text TEXT)
 */
object GitaCoreTextRepository {

    private const val FOLDER = "gita_bhasya"
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

    private suspend fun <T> withPack(context: Context, block: (android.database.sqlite.SQLiteDatabase) -> T): Result<T> =
        PackDownloadManager.openPack(context, FOLDER, GitaManifest.CORE_TEXT_PACK_FILE).mapCatching { sqlite ->
            sqlite.use { database -> block(database) }
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
