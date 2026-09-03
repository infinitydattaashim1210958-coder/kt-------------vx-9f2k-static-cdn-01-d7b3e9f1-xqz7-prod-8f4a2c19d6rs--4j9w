package com.kyronix.swadhyaa.data.repository

import android.content.Context
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ramayana has exactly one bhashya source (unlike the Veda, which has many scholars),
 * split into one downloadable pack per kanda: ramayana-kanpur-iit/ramayana_kanda_<n>.db.gz
 * Pack schema: ramayana_bhashyas(id, shloka_id, field_key, value)
 * field_key values: pratipada (word meaning), tat (translation), comment (commentary)
 */
object RamayanaBhashyaRepository {

    const val LANGUAGE_LABEL = "হিন্দি"
    const val SOURCE_LABEL = "বাল্মীকি রামায়ণ পোর্টাল (আইআইটি কানপুর)"

    private const val FOLDER = "ramayana-kanpur-iit"

    private val FIELD_LABELS = mapOf(
        "pratipada" to "পদার্থ",
        "tat" to "অনুবাদ",
        "comment" to "টীকা"
    )
    private val FIELD_ORDER = listOf("pratipada", "tat", "comment")

    private fun packFile(kandaId: Int) = "ramayana_kanda_$kandaId.db.gz"

    fun isDownloaded(context: Context, kandaId: Int): Boolean =
        PackDownloadManager.isDownloaded(context, FOLDER, packFile(kandaId))

    suspend fun downloadIfNeeded(
        context: Context,
        kandaId: Int,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        PackDownloadManager.openPack(context, FOLDER, packFile(kandaId), onProgress)
            .map { it.close() }
    }

    suspend fun getBhashya(context: Context, kandaId: Int, shlokaId: Int): Result<List<BhashyaField>> =
        withContext(Dispatchers.IO) {
            PackDownloadManager.openPack(context, FOLDER, packFile(kandaId)).mapCatching { sqlite ->
                sqlite.use { database ->
                    val cursor = database.rawQuery(
                        "SELECT field_key, value FROM ramayana_bhashyas WHERE shloka_id = ?",
                        arrayOf(shlokaId.toString())
                    )
                    val raw = mutableMapOf<String, String>()
                    cursor.use {
                        while (it.moveToNext()) raw[it.getString(0)] = it.getString(1)
                    }
                    val ordered = FIELD_ORDER.filter { raw.containsKey(it) } +
                        raw.keys.filter { it !in FIELD_ORDER }
                    ordered.map { key -> BhashyaField(FIELD_LABELS[key] ?: key, raw.getValue(key)) }
                }
            }
        }
}
