package com.kyronix.swadhyaa.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kyronix.swadhyaa.data.prefs.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class DownloadedHtmlBook(
    val id: String,
    val title: String,
    val filename: String,
    val downloadedAt: Long
)

/**
 * "html"-type library books (the majority of the catalog — see
 * LibraryManifest doc). Matches lib.js's downloadBook/getManifest/
 * deleteBook exactly for this type: download saves the real HTML file
 * locally; there is no in-app reader for it at all — legacy opens it in
 * the system browser via an Intent, preserving the original page's own
 * interactivity (search, tabs, etc.) rather than re-implementing it.
 * LibraryActivity does the same via getShareableUri() + ACTION_VIEW.
 */
object LibraryHtmlBookRepository {

    private val client by lazy { OkHttpClient.Builder().build() }
    private val keyDownloadedManifest = stringPreferencesKey("library_html_downloaded_manifest_json")

    private fun booksDir(context: Context): File =
        File(context.filesDir, "library_books").apply { mkdirs() }

    private fun localFile(context: Context, filename: String): File =
        File(booksDir(context), filename)

    suspend fun isDownloaded(context: Context, book: LibraryBookInfo): Boolean =
        localFile(context, book.filename).exists()

    suspend fun download(
        context: Context,
        book: LibraryBookInfo,
        onProgress: ((String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke("ডাউনলোড হচ্ছে…")
            val url = LibraryManifest.bookDownloadUrl(book.filename)
            val request = Request.Builder().url(url).build()
            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("ডাউনলোড ব্যর্থ (HTTP ${response.code})")
                response.body?.string() ?: throw IOException("Empty response body")
            }

            onProgress?.invoke("ফোনে সেভ হচ্ছে…")
            localFile(context, book.filename).writeText(html, Charsets.UTF_8)

            val manifest = getDownloadedManifest(context).toMutableMap()
            manifest[book.id] = DownloadedHtmlBook(
                id = book.id,
                title = book.title,
                filename = book.filename,
                downloadedAt = System.currentTimeMillis()
            )
            saveDownloadedManifest(context, manifest)
            Result.success(Unit)
        } catch (networkErr: java.net.UnknownHostException) {
            Result.failure(IOException("নেটওয়ার্ক সংযোগ পাওয়া যায়নি।", networkErr))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(context: Context, bookId: String) = withContext(Dispatchers.IO) {
        val manifest = getDownloadedManifest(context).toMutableMap()
        val entry = manifest.remove(bookId)
        if (entry != null) {
            localFile(context, entry.filename).delete()
            saveDownloadedManifest(context, manifest)
        }
    }

    /**
     * A content:// Uri suitable for ACTION_VIEW in the system browser.
     * Raw file:// Uris are blocked (FileUriExposedException) on API 24+
     * for exported intents — see AndroidManifest.xml's FileProvider entry
     * and res/xml/file_paths.xml.
     */
    fun getShareableUri(context: Context, filename: String): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, localFile(context, filename))
    }

    suspend fun getDownloadedManifest(context: Context): Map<String, DownloadedHtmlBook> {
        val json = context.dataStore.data.first()[keyDownloadedManifest] ?: return emptyMap()
        return try {
            parseManifestJson(json)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun saveDownloadedManifest(context: Context, manifest: Map<String, DownloadedHtmlBook>) {
        context.dataStore.edit { it[keyDownloadedManifest] = manifestToJson(manifest) }
    }

    /** Pure JSON (de)serialization — testable without Context. */
    internal fun manifestToJson(manifest: Map<String, DownloadedHtmlBook>): String {
        val obj = JSONObject()
        manifest.forEach { (id, entry) ->
            obj.put(
                id,
                JSONObject()
                    .put("title", entry.title)
                    .put("filename", entry.filename)
                    .put("downloadedAt", entry.downloadedAt)
            )
        }
        return obj.toString()
    }

    internal fun parseManifestJson(json: String): Map<String, DownloadedHtmlBook> {
        val obj = JSONObject(json)
        val out = mutableMapOf<String, DownloadedHtmlBook>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val o = obj.getJSONObject(id)
            out[id] = DownloadedHtmlBook(
                id = id,
                title = o.getString("title"),
                filename = o.getString("filename"),
                downloadedAt = o.optLong("downloadedAt")
            )
        }
        return out
    }
}
