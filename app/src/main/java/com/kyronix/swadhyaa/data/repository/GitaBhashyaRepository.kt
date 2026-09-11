package com.kyronix.swadhyaa.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * গীতা ভাষ্য (Bhagavad Gita commentary) — reads/downloads the pack behind one
 * [GitaScholarInfo] row. Several rows can point at the same [GitaScholarInfo.packFile]
 * (e.g. `sankar_et_ht_sc_db.gz` backs three rows, one per language) — each call
 * here reads only THIS row's [GitaScholarInfo.fields] columns, so a query never
 * leaks another row's language into the wrong tab even though they share a file.
 *
 * SCHEMA — CONFIRMED 2026-09-11 from a live run's self-diagnostic dump against
 * `adi_et_db.gz`:
 *
 *   commentary(id, chapter, verse, verse_id, author, et, ht, ec, hc, sc)
 *
 * This is a DIFFERENT shape than first assumed (and different from Veda's/
 * Ramayana's field_key+value row-per-field packs): ONE row per (chapter,
 * verse), with a dedicated column per language — et/ht/ec/hc/sc — populated
 * only where that scholar actually wrote in that language, the rest left
 * null/blank. Handily, [GitaFieldInfo.key] in [GitaManifest] already uses
 * exactly these five codes, so no manifest changes were needed — a field key
 * IS the column name here. `author` repeats the scholar's name per row
 * (redundant with [GitaScholarInfo.name], not currently read); `id`/`verse_id`
 * aren't needed since every query addresses by (chapter, verse), matching
 * [GitaCoreTextRepository]'s base-text pack. `sqlite_sequence` is SQLite's
 * own bookkeeping table, not app data.
 *
 * Every query still goes through [withPack], which appends the pack's real
 * schema to any exception it doesn't expect — kept as a safety net in case a
 * different scholar's pack turns out to deviate from this shape.
 */
object GitaBhashyaRepository {

    private const val FOLDER = "gita_bhasya"
    private const val TABLE = "commentary"

    /** Every possible language column, in the pack's own order — read once, filtered per scholar below. */
    private val ALL_COLUMNS = listOf("et", "ht", "ec", "hc", "sc")

    fun isDownloaded(context: Context, scholar: GitaScholarInfo): Boolean =
        PackDownloadManager.isDownloaded(context, FOLDER, scholar.packFile)

    suspend fun downloadIfNeeded(
        context: Context,
        scholar: GitaScholarInfo,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        PackDownloadManager.openPack(context, FOLDER, scholar.packFile, onProgress)
            .map { it.close() } // warm the cache; getBhashya reopens per-call below
    }

    /**
     * Reads [scholar]'s field(s) for verse (adhyaya, shloka). Returns an empty
     * list (not a failure) if this scholar simply has no row, or every column
     * they care about is null/blank, for this verse; [Result.failure] only
     * for a real I/O/download/schema error.
     */
    suspend fun getBhashya(
        context: Context,
        scholar: GitaScholarInfo,
        adhyaya: Int,
        shloka: Int
    ): Result<List<BhashyaField>> = withContext(Dispatchers.IO) {
        withPack(context, scholar) { database ->
            val cursor = database.rawQuery(
                "SELECT ${ALL_COLUMNS.joinToString(", ")} FROM $TABLE WHERE chapter = ? AND verse = ? LIMIT 1",
                arrayOf(adhyaya.toString(), shloka.toString())
            )
            val byColumn = mutableMapOf<String, String>()
            cursor.use {
                if (it.moveToFirst()) {
                    ALL_COLUMNS.forEachIndexed { i, col ->
                        val v = if (it.isNull(i)) null else it.getString(i)
                        if (!v.isNullOrBlank()) byColumn[col] = v
                    }
                }
            }
            // Only THIS row's fields, in its declared order — a pack shared
            // with other rows (e.g. sankar_et_ht_sc) never leaks another
            // row's language/column into this one.
            scholar.fields.mapNotNull { f ->
                byColumn[f.key]?.let { value -> BhashyaField(f.label, value) }
            }
        }
    }

    /**
     * Opens [scholar]'s pack and runs [block]; on ANY exception, re-throws
     * with the pack's real schema appended (via [GitaCoreTextRepository.dumpSchema],
     * shared rather than duplicated since the diagnostic logic is identical).
     */
    private suspend fun <T> withPack(
        context: Context,
        scholar: GitaScholarInfo,
        block: (SQLiteDatabase) -> T
    ): Result<T> =
        PackDownloadManager.openPack(context, FOLDER, scholar.packFile).mapCatching { sqlite ->
            sqlite.use { database ->
                try {
                    block(database)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "${e.message}\n\n--- ${scholar.packFile} এর প্রকৃত schema ---\n" +
                            GitaCoreTextRepository.dumpSchema(database), e
                    )
                }
            }
        }
}
