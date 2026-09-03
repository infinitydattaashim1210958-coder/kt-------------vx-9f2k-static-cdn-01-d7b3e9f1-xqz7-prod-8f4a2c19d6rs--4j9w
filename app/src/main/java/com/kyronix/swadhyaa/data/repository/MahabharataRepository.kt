package com.kyronix.swadhyaa.data.repository

import android.content.Context
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Adhyay(val id: Int, val chapterNo: Int, val title: String)
data class Upakhyan(val id: Int, val seq: Int, val bishoy: String?, val content: String)

/**
 * Each parba is a single downloadable pack (mahabharata_kaliprasanna/mahabharata_parba_<n>.db.gz)
 * containing BOTH structure and content — unlike Veda/Ramayana there's no separate "core" db:
 *   adhyayas(id, chapter_no, title)
 *   upakhyanas(id, adhyay_id, seq, upakhyan_key, bishoy, content)
 *
 * Only one translation source exists today — কালীপ্রসন্ন সিংহ অনূদিত — but this is kept as a
 * repository (not a hardcoded string) so more translators can be added the same way Veda scholars are.
 */
object MahabharataRepository {

    const val TRANSLATOR_LABEL = "কালীপ্রসন্ন সিংহ অনূদিত"
    private const val FOLDER = "mahabharata_kaliprasanna"

    fun isDownloaded(context: Context, parba: ParbaInfo): Boolean =
        PackDownloadManager.isDownloaded(context, FOLDER, parba.packFile)

    suspend fun downloadIfNeeded(
        context: Context,
        parba: ParbaInfo,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        PackDownloadManager.openPack(context, FOLDER, parba.packFile, onProgress)
            .map { it.close() }
    }

    suspend fun getAdhyayas(context: Context, parba: ParbaInfo): Result<List<Adhyay>> =
        withContext(Dispatchers.IO) {
            PackDownloadManager.openPack(context, FOLDER, parba.packFile).mapCatching { sqlite ->
                sqlite.use { database ->
                    val cursor = database.rawQuery(
                        "SELECT id, chapter_no, title FROM adhyayas ORDER BY chapter_no", null
                    )
                    val list = mutableListOf<Adhyay>()
                    cursor.use {
                        while (it.moveToNext()) {
                            list.add(Adhyay(it.getInt(0), it.getInt(1), it.getString(2)))
                        }
                    }
                    list
                }
            }
        }

    suspend fun getUpakhyanas(context: Context, parba: ParbaInfo, adhyayId: Int): Result<List<Upakhyan>> =
        withContext(Dispatchers.IO) {
            PackDownloadManager.openPack(context, FOLDER, parba.packFile).mapCatching { sqlite ->
                sqlite.use { database ->
                    val cursor = database.rawQuery(
                        "SELECT id, seq, bishoy, content FROM upakhyanas WHERE adhyay_id = ? ORDER BY seq",
                        arrayOf(adhyayId.toString())
                    )
                    val list = mutableListOf<Upakhyan>()
                    cursor.use {
                        while (it.moveToNext()) {
                            list.add(Upakhyan(it.getInt(0), it.getInt(1), it.getString(2), it.getString(3)))
                        }
                    }
                    list
                }
            }
        }
}
