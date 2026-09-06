package com.kyronix.swadhyaa.data.repository

import android.content.Context
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Adhyay(val id: Int, val chapterNo: Int, val title: String)
data class Upakhyan(val id: Int, val seq: Int, val bishoy: String?, val content: String)
data class AdjacentAdhyayas(val prevId: Int?, val nextId: Int?)
data class UpakhyanSearchHit(val id: Int, val adhyayId: Int, val bishoy: String?, val adhyayTitle: String, val snippet: String)

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

    /**
     * Matches legacy master-db.js's getMahabharataAdjacentAdhyayas exactly:
     * prev = highest id below adhyayId, next = lowest id above adhyayId,
     * both WITHIN this parba's own adhyayas table. Ordered by row id, NOT
     * chapter_no — verified directly against source before writing this,
     * since assuming chapter_no would have been a plausible but wrong guess.
     */
    suspend fun getAdjacentAdhyayas(context: Context, parba: ParbaInfo, adhyayId: Int): Result<AdjacentAdhyayas> =
        withContext(Dispatchers.IO) {
            PackDownloadManager.openPack(context, FOLDER, parba.packFile).mapCatching { sqlite ->
                sqlite.use { database ->
                    val prevId = database.rawQuery(
                        "SELECT id FROM adhyayas WHERE id < ? ORDER BY id DESC LIMIT 1",
                        arrayOf(adhyayId.toString())
                    ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }

                    val nextId = database.rawQuery(
                        "SELECT id FROM adhyayas WHERE id > ? ORDER BY id ASC LIMIT 1",
                        arrayOf(adhyayId.toString())
                    ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }

                    AdjacentAdhyayas(prevId, nextId)
                }
            }
        }

    /**
     * Matches legacy master-db.js's searchMahabharataParva: FTS5 over
     * upakhyanas content first, LIKE fallback if the FTS table doesn't
     * exist in this particular downloaded pack (schema across older/newer
     * pack builds isn't guaranteed identical — legacy itself defends
     * against this with the same try/catch-and-fallback shape). Reuses
     * SearchRepository's crash-safe FTS escaping (RISK_REGISTER.md R10) —
     * a bare, unescaped MATCH query would carry the exact same crash risk
     * here as it did for Veda/Ramayana search, for the same reasons.
     */
    suspend fun searchInParba(
        context: Context,
        parba: ParbaInfo,
        term: String,
        limit: Int = 50
    ): Result<List<UpakhyanSearchHit>> = withContext(Dispatchers.IO) {
        val q = term.trim()
        if (q.length < 2) return@withContext Result.success(emptyList())

        PackDownloadManager.openPack(context, FOLDER, parba.packFile).mapCatching { sqlite ->
            sqlite.use { database ->
                val hasFts = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='upakhyanas_fts'", null
                ).use { it.count > 0 }

                val hits = mutableListOf<UpakhyanSearchHit>()
                if (hasFts) {
                    val fts = SearchRepository.escapeFtsQuery(q)
                    if (fts.isEmpty()) return@use hits
                    try {
                        database.rawQuery(
                            """
                            SELECT f.upakhyan_id, f.adhyay_id, f.bishoy, a.title,
                                   substr(f.content, 1, 120)
                            FROM upakhyanas_fts f
                            JOIN adhyayas a ON a.id = f.adhyay_id
                            WHERE upakhyanas_fts MATCH ?
                            LIMIT ?
                            """.trimIndent(),
                            arrayOf(fts, limit.toString())
                        ).use { c ->
                            while (c.moveToNext()) {
                                hits += UpakhyanSearchHit(
                                    id = c.getInt(0),
                                    adhyayId = c.getInt(1),
                                    bishoy = c.getString(2),
                                    adhyayTitle = c.getString(3),
                                    snippet = c.getString(4) ?: ""
                                )
                            }
                        }
                        return@use hits
                    } catch (_: Exception) {
                        // FTS table exists but query failed for some other
                        // reason (e.g. corrupt index) — fall through to LIKE.
                    }
                }

                val esc = "%${SearchRepository.escapeLike(q)}%"
                database.rawQuery(
                    """
                    SELECT u.id, u.adhyay_id, u.bishoy, a.title, substr(u.content, 1, 120)
                    FROM upakhyanas u
                    JOIN adhyayas a ON a.id = u.adhyay_id
                    WHERE u.content LIKE ? ESCAPE '\' OR u.bishoy LIKE ? ESCAPE '\'
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(esc, esc, limit.toString())
                ).use { c ->
                    while (c.moveToNext()) {
                        hits += UpakhyanSearchHit(
                            id = c.getInt(0),
                            adhyayId = c.getInt(1),
                            bishoy = c.getString(2),
                            adhyayTitle = c.getString(3),
                            snippet = c.getString(4) ?: ""
                        )
                    }
                }
                hits
            }
        }
    }
}
