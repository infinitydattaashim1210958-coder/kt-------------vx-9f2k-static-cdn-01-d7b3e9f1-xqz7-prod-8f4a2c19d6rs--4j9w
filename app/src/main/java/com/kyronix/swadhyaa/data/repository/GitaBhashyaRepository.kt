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
 * here filters to just THIS row's [GitaScholarInfo.fields], so a query never
 * leaks another row's language into the wrong tab even though they share a file.
 *
 * Mirrors [BhashyaRepository] (Veda) / [RamayanaBhashyaRepository] (Ramayana):
 * open via [PackDownloadManager], plain `rawQuery`, never merged into
 * [com.kyronix.swadhyaa.data.local.MasterDatabase].
 *
 * SCHEMA STATUS (2026-09-11): [TABLE]'s name below is UNCONFIRMED — the sibling
 * assumption for the base-text pack (`shlokas`, in [GitaCoreTextRepository])
 * already turned out wrong (`Error: no such table: shlokas`), so treat this
 * one the same way until proven otherwise. Every query is wrapped by
 * [withPack] so a wrong table/column name doesn't just fail — the exception
 * message gets the pack's real tables/columns appended
 * ([GitaCoreTextRepository.dumpSchema]), and that message is exactly what
 * already reaches the screen via `GitaUiState.bhashyaError`. Fix [TABLE] and
 * the column names once a real bhashya pack's schema shows up there.
 */
object GitaBhashyaRepository {

    private const val FOLDER = "gita_bhasya"

    // ⚠ UNCONFIRMED — see class doc comment.
    private const val TABLE = "gita_bhashyas"

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
     * list (not a failure) if this scholar simply has no entry for this verse;
     * [Result.failure] for a real I/O/download/schema error — and per the class
     * doc comment, a schema error's message already tells you what's really
     * in the pack instead of leaving you to guess again.
     */
    suspend fun getBhashya(
        context: Context,
        scholar: GitaScholarInfo,
        adhyaya: Int,
        shloka: Int
    ): Result<List<BhashyaField>> = withContext(Dispatchers.IO) {
        withPack(context, scholar) { database ->
            val cursor = database.rawQuery(
                "SELECT field_key, value FROM $TABLE WHERE adhyaya = ? AND shloka = ?",
                arrayOf(adhyaya.toString(), shloka.toString())
            )
            val raw = mutableMapOf<String, String>()
            cursor.use {
                while (it.moveToNext()) raw[it.getString(0)] = it.getString(1)
            }
            // Only THIS row's fields, in its declared order — a pack shared
            // with other rows (e.g. sankar_et_ht_sc) never leaks another
            // row's language/field into this one.
            scholar.fields.mapNotNull { f ->
                raw[f.key]?.let { value -> BhashyaField(f.label, value) }
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
