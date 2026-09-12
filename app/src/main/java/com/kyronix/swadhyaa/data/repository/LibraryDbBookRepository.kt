package com.kyronix.swadhyaa.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.kyronix.swadhyaa.data.local.MasterDatabase
import com.kyronix.swadhyaa.data.local.entity.InstalledPackageEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookChapterEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookParagraphEntity
import com.kyronix.swadhyaa.data.local.entity.LibraryBookRefEntity
import com.kyronix.swadhyaa.data.local.dao.LibrarySearchRow
import com.kyronix.swadhyaa.data.remote.PackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibraryChapter(
    val chapterId: String,
    val seq: Int?,
    val heading: String?,
    val isCover: Boolean,
    val headingBold: Boolean,
    val headingCenter: Boolean,
    val headingUnderline: Boolean,
    val headingSize: Double
)

data class LibraryRef(val paraSeq: Int, val refSeq: Int, val refNumber: String?, val refNote: String?)

data class LibraryParagraph(
    val seq: Int?,
    val content: String?,
    val isBold: Boolean,
    val isCenter: Boolean,
    val isRight: Boolean,
    val isUnderline: Boolean,
    val fontSize: Double,
    val refs: List<LibraryRef>
)

/**
 * "db"-type library books — downloaded pack.gz merged into MasterDatabase,
 * matching legacy's mergeLibraryBookPack/getLibraryBookChapters/
 * getLibraryBookParagraphs exactly (see MasterEntities.kt's Library
 * section doc for the full schema evidence). This is the one place in
 * this session's work that actually wires MasterDatabase into a live code
 * path — see RISK_REGISTER.md R5.
 */
object LibraryDbBookRepository {

    private const val FOLDER = "library_books"

    /**
     * Root cause of the earlier "Queries can be performed using
     * SQLiteDatabase query or rawQuery methods only" crash traced (via
     * full stack trace) to MasterDatabase's onCreate() — a raw
     * `execSQL("PRAGMA journal_mode=WAL;")`, not anything in this DAO
     * call. Fixed there (see MasterDatabase.kt). getLibraryChapters()
     * itself was fine all along. Kept a short wrapper here (rather than
     * none) since there's no adb/logcat access — if isDownloaded ever
     * fails again for a different reason, naming the call directly in
     * the on-screen message is still cheap insurance.
     */
    suspend fun isDownloaded(context: Context, bookId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            MasterDatabase.getInstance(context).masterDao().getLibraryChapters(bookId).isNotEmpty()
        } catch (e: Exception) {
            throw IllegalStateException(
                "getLibraryChapters(bookId=$bookId) failed — ${e.javaClass.simpleName}: ${e.message}",
                e
            )
        }
    }

    /**
     * Downloads book.filename (a .db.gz pack), reads its own chapters/
     * paragraphs/refs tables, merges them into MasterDatabase, then
     * deletes the temp pack file — matching legacy's download → merge →
     * fs.deleteFile pipeline. Idempotent: safe to call again after an
     * interrupted download or to force a re-merge.
     */
    suspend fun downloadAndMerge(
        context: Context,
        book: LibraryBookInfo,
        onProgress: ((String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        onProgress?.invoke("ডাউনলোড হচ্ছে…")
        val openResult = PackDownloadManager.openPack(context, FOLDER, book.filename)
        val sourceDb = openResult.getOrElse { return@withContext Result.failure(it) }

        try {
            onProgress?.invoke("একত্রিত হচ্ছে…")
            val (chapters, paragraphs, refs) = sourceDb.use { readSourcePack(it, book.id) }

            val pkg = InstalledPackageEntity(
                packageId = "libbook_${book.id}",
                category = "library_book",
                sourceId = 0, // unused placeholder for this category — see MasterEntities.kt doc
                title = book.title,
                sourceIdText = book.id
            )
            MasterDatabase.getInstance(context).masterDao()
                .installLibraryDbBook(pkg, chapters, paragraphs, refs)

            onProgress?.invoke("সম্পন্ন!")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Source pack is only ever a temp file for this book type —
            // its content now lives in MasterDatabase. Matches legacy's
            // non-fatal best-effort cleanup (try/catch around deleteFile).
            PackDownloadManager.deleteLocalPack(context, book.filename)
        }
    }

    suspend fun remove(context: Context, bookId: String) = withContext(Dispatchers.IO) {
        MasterDatabase.getInstance(context).masterDao().removeLibraryDbBook(bookId, "libbook_$bookId")
    }

    suspend fun getChapters(context: Context, bookId: String): List<LibraryChapter> = withContext(Dispatchers.IO) {
        MasterDatabase.getInstance(context).masterDao().getLibraryChapters(bookId).map {
            LibraryChapter(
                chapterId = it.chapterId,
                seq = it.seq,
                heading = it.heading,
                isCover = it.isCover,
                headingBold = it.headingBold,
                headingCenter = it.headingCenter,
                headingUnderline = it.headingUnderline,
                headingSize = it.headingSize
            )
        }
    }

    /** Paragraphs for one chapter, each with its footnote refs already attached (joined by para_seq == paragraph.seq). */
    suspend fun getParagraphs(context: Context, bookId: String, chapterId: String): List<LibraryParagraph> =
        withContext(Dispatchers.IO) {
            val dao = MasterDatabase.getInstance(context).masterDao()
            val paras = dao.getLibraryParagraphs(bookId, chapterId)
            val refs = dao.getLibraryRefs(bookId, chapterId)
            val refsBySeq = refs.groupBy { it.paraSeq }
            paras.map { p ->
                LibraryParagraph(
                    seq = p.seq,
                    content = p.content,
                    isBold = p.isBold,
                    isCenter = p.isCenter,
                    isRight = p.isRight,
                    isUnderline = p.isUnderline,
                    fontSize = p.fontSize,
                    refs = (refsBySeq[p.seq] ?: emptyList()).map {
                        LibraryRef(it.paraSeq, it.refSeq, it.refNumber, it.refNote)
                    }
                )
            }
        }

    suspend fun search(context: Context, bookId: String, term: String, limit: Int = 50): Result<List<LibrarySearchRow>> =
        withContext(Dispatchers.IO) {
            val q = term.trim()
            if (q.length < 2) return@withContext Result.success(emptyList())
            val dao = MasterDatabase.getInstance(context).masterDao()
            try {
                val fts = SearchRepository.escapeFtsQuery(q)
                if (fts.isEmpty()) return@withContext Result.success(emptyList())
                Result.success(dao.searchLibraryBookFts(bookId, fts, limit))
            } catch (_: Exception) {
                val esc = "%${SearchRepository.escapeLike(q)}%"
                Result.success(dao.searchLibraryBookLike(bookId, esc, limit))
            }
        }

    /**
     * Reads the downloaded pack's OWN chapters/paragraphs/refs tables
     * (unprefixed table names — "library_book_" prefixes only exist in
     * the merged master DB, verified against mergeLibraryBookPack's
     * `FROM ${alias}.chapters` etc.). Builds one SELECT per table using
     * either the real column name or a literal default value for columns
     * an older pack might not have yet — matching legacy's own
     * chapCol()/paraCol() fallback helpers exactly, and done as a single
     * query per table rather than one extra round-trip per row per
     * optional column.
     */
    private fun readSourcePack(
        db: SQLiteDatabase,
        bookId: String
    ): Triple<List<LibraryBookChapterEntity>, List<LibraryBookParagraphEntity>, List<LibraryBookRefEntity>> {
        val chapterCols = tableColumns(db, "chapters")
        val paraCols = tableColumns(db, "paragraphs")

        fun chapCol(name: String, default: String) = if (name in chapterCols) name else default
        fun paraCol(name: String, default: String) = if (name in paraCols) name else default

        val chapters = mutableListOf<LibraryBookChapterEntity>()
        db.rawQuery(
            """
            SELECT chapter_id, seq, heading, is_cover,
                   ${chapCol("heading_bold", "0")} AS heading_bold,
                   ${chapCol("heading_center", "0")} AS heading_center,
                   ${chapCol("heading_underline", "0")} AS heading_underline,
                   ${chapCol("heading_size", "12.0")} AS heading_size
            FROM chapters
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                chapters += LibraryBookChapterEntity(
                    bookId = bookId,
                    chapterId = c.getString(0),
                    seq = if (!c.isNull(1)) c.getInt(1) else null,
                    heading = c.getString(2),
                    isCover = !c.isNull(3) && c.getInt(3) != 0,
                    headingBold = c.getInt(4) != 0,
                    headingCenter = c.getInt(5) != 0,
                    headingUnderline = c.getInt(6) != 0,
                    headingSize = c.getDouble(7)
                )
            }
        }

        val paragraphs = mutableListOf<LibraryBookParagraphEntity>()
        db.rawQuery(
            """
            SELECT chapter_id, seq, content,
                   ${paraCol("is_bold", "0")} AS is_bold,
                   ${paraCol("is_center", "0")} AS is_center,
                   ${paraCol("is_right", "0")} AS is_right,
                   ${paraCol("is_underline", "0")} AS is_underline,
                   ${paraCol("font_size", "12.0")} AS font_size
            FROM paragraphs
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                paragraphs += LibraryBookParagraphEntity(
                    bookId = bookId,
                    chapterId = c.getString(0),
                    seq = if (!c.isNull(1)) c.getInt(1) else null,
                    content = c.getString(2),
                    isBold = c.getInt(3) != 0,
                    isCenter = c.getInt(4) != 0,
                    isRight = c.getInt(5) != 0,
                    isUnderline = c.getInt(6) != 0,
                    fontSize = c.getDouble(7)
                )
            }
        }

        val refs = mutableListOf<LibraryBookRefEntity>()
        if (tableExists(db, "refs")) {
            db.rawQuery(
                "SELECT chapter_id, para_seq, ref_seq, ref_number, ref_note FROM refs", null
            ).use { c ->
                while (c.moveToNext()) {
                    refs += LibraryBookRefEntity(
                        bookId = bookId,
                        chapterId = c.getString(0),
                        paraSeq = c.getInt(1),
                        refSeq = c.getInt(2),
                        refNumber = c.getString(3),
                        refNote = c.getString(4)
                    )
                }
            }
        }

        return Triple(chapters, paragraphs, refs)
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { it.count > 0 }

    private fun tableColumns(db: SQLiteDatabase, table: String): Set<String> {
        val cols = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (nameIdx >= 0) cols += c.getString(nameIdx)
            }
        }
        return cols
    }
}
