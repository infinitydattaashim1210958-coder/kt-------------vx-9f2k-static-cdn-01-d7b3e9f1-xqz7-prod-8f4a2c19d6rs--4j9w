package com.kyronix.swadhyaa.data.repository

import android.content.Context
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
 * SCHEMA ASSUMPTION — see [GitaManifest]'s doc comment for why. If a pack 404s
 * against [TABLE]/its columns, call [inspectSchema] against that pack to get
 * its real table/column names back instead of guessing again.
 */
object GitaBhashyaRepository {

    private const val FOLDER = "gita_bhasya"
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
     * [Result.failure] only for a real I/O/download/schema error.
     */
    suspend fun getBhashya(
        context: Context,
        scholar: GitaScholarInfo,
        adhyaya: Int,
        shloka: Int
    ): Result<List<BhashyaField>> = withContext(Dispatchers.IO) {
        PackDownloadManager.openPack(context, FOLDER, scholar.packFile).mapCatching { sqlite ->
            sqlite.use { database ->
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
    }

    /**
     * Diagnostic only — not used by the normal read path. Call against a pack
     * that returns an empty/failed [getBhashya] unexpectedly, to see what
     * tables/columns it actually has instead of guessing blind.
     */
    suspend fun inspectSchema(context: Context, scholar: GitaScholarInfo): Result<String> =
        withContext(Dispatchers.IO) {
            PackDownloadManager.openPack(context, FOLDER, scholar.packFile).mapCatching { sqlite ->
                sqlite.use { database ->
                    val tables = mutableListOf<String>()
                    database.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type = 'table'", null
                    ).use { c -> while (c.moveToNext()) tables.add(c.getString(0)) }

                    tables.joinToString("\n\n") { table ->
                        val cols = mutableListOf<String>()
                        database.rawQuery("PRAGMA table_info($table)", null).use { c ->
                            while (c.moveToNext()) cols.add(c.getString(1)) // column 1 = name
                        }
                        "$table(${cols.joinToString(", ")})"
                    }
                }
            }
        }
}
