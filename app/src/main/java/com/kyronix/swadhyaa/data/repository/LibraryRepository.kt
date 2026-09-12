package com.kyronix.swadhyaa.data.repository

import android.content.Context
import android.net.Uri

enum class LibraryBookStatus { NOT_DOWNLOADED, DOWNLOADED }

data class LibraryBookWithStatus(val info: LibraryBookInfo, val status: LibraryBookStatus)

/**
 * Facade over LibraryManifest + LibraryHtmlBookRepository +
 * LibraryDbBookRepository — the UI layer (LibraryViewModel) only needs
 * this one entry point and doesn't need to know which of the two very
 * different download/storage models (raw file vs. merged-into-Room) a
 * given book uses.
 */
object LibraryRepository {

    suspend fun getCatalog(context: Context): Result<List<LibraryBookWithStatus>> {
        val manifestResult = LibraryManifest.fetch(context)
        val books = manifestResult.getOrElse { return Result.failure(it) }

        // BUGFIX: this loop calls LibraryDbBookRepository.isDownloaded(),
        // which opens MasterDatabase — previously NOT wrapped, so any
        // failure here (the Room schema-mismatch crash this app just hit,
        // or any future DB/IO problem) propagated out of this suspend
        // function uncaught and crashed the whole app instead of showing
        // an error card like a manifest-fetch failure already does above.
        val withStatus = try {
            books.map { book ->
                val downloaded = when (book.type) {
                    "db" -> LibraryDbBookRepository.isDownloaded(context, book.id)
                    else -> LibraryHtmlBookRepository.isDownloaded(context, book)
                }
                LibraryBookWithStatus(book, if (downloaded) LibraryBookStatus.DOWNLOADED else LibraryBookStatus.NOT_DOWNLOADED)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(withStatus)
    }

    suspend fun download(context: Context, book: LibraryBookInfo, onProgress: ((String) -> Unit)? = null): Result<Unit> =
        when (book.type) {
            "db" -> LibraryDbBookRepository.downloadAndMerge(context, book, onProgress)
            else -> LibraryHtmlBookRepository.download(context, book, onProgress)
        }

    suspend fun delete(context: Context, book: LibraryBookInfo) {
        when (book.type) {
            "db" -> LibraryDbBookRepository.remove(context, book.id)
            else -> LibraryHtmlBookRepository.delete(context, book.id)
        }
    }

    /**
     * Only meaningful for "html" books — returns a content:// Uri to open
     * externally. "db" books have no single file to open; the caller
     * should launch LibraryDbBookReaderActivity instead when
     * book.type == "db".
     */
    suspend fun getHtmlShareableUri(context: Context, book: LibraryBookInfo): Uri? {
        if (book.type == "db") return null
        val entry = LibraryHtmlBookRepository.getDownloadedManifest(context)[book.id] ?: return null
        return LibraryHtmlBookRepository.getShareableUri(context, entry.filename)
    }
}
