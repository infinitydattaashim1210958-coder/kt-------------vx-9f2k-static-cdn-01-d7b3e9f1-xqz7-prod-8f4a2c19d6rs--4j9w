package com.kyronix.swadhyaa.data.repository

import android.content.Context
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.entity.ScholarEntity
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BhashyaField(val label: String, val value: String)

/**
 * Bhashya (commentary/translation) for a single Veda mantra.
 * Pack schema (bhashya_packs/scholar_<id>.db.gz):
 *   bhashyas(id, mantra_id, scholar_id, field_key, value)
 * field ordering/labels come from core.db's scholar_fields table.
 */
class BhashyaRepository(
    private val db: CoreDatabase
) {
    private val dao get() = db.vedaDao()

    /** All scholars whose pack actually has an entry for this mantra. */
    suspend fun getScholarsForMantra(mantraId: Int): List<ScholarEntity> =
        dao.getScholarsForMantra(mantraId)

    fun isDownloaded(context: Context, scholar: ScholarEntity): Boolean {
        val file = scholar.packFile ?: return false
        return PackDownloadManager.isDownloaded(context, "bhashya_packs", file)
    }

    suspend fun downloadIfNeeded(
        context: Context,
        scholar: ScholarEntity,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val file = scholar.packFile
            ?: return@withContext Result.failure(IllegalStateException("No pack_file for scholar ${scholar.id}"))
        PackDownloadManager.openPack(context, "bhashya_packs", file, onProgress)
            .map { it.close() } // just warm the cache; queries reopen per-call below
    }

    /** Reads the bhashya fields for [mantraId] from [scholar]'s already-downloaded pack. */
    suspend fun getBhashya(
        context: Context,
        scholar: ScholarEntity,
        mantraId: Int
    ): Result<List<BhashyaField>> = withContext(Dispatchers.IO) {
        val file = scholar.packFile
            ?: return@withContext Result.failure(IllegalStateException("No pack_file for scholar ${scholar.id}"))
        val scholarId = scholar.id
            ?: return@withContext Result.failure(IllegalStateException("No id for scholar"))

        val fieldOrder = dao.getFieldsForScholar(scholarId).map { it.fieldKey }

        PackDownloadManager.openPack(context, "bhashya_packs", file).mapCatching { sqlite ->
            sqlite.use { database ->
                val cursor = database.rawQuery(
                    "SELECT field_key, value FROM bhashyas WHERE mantra_id = ? AND scholar_id = ?",
                    arrayOf(mantraId.toString(), scholarId.toString())
                )
                val raw = mutableMapOf<String, String>()
                cursor.use {
                    while (it.moveToNext()) {
                        raw[it.getString(0)] = it.getString(1)
                    }
                }
                // Preserve scholar_fields display order; fall back to insertion order
                // for any field present in the pack but not declared in scholar_fields.
                val ordered = fieldOrder.filter { raw.containsKey(it) } +
                    raw.keys.filter { it !in fieldOrder }
                ordered.map { key -> BhashyaField(key, raw.getValue(key)) }
            }
        }
    }
}
