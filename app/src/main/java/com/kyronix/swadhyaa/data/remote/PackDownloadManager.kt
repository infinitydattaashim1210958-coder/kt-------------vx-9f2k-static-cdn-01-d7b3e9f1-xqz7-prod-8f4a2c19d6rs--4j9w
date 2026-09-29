package com.kyronix.swadhyaa.data.remote

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Downloads gz-compressed SQLite "packs" and decompresses them.
 *
 * Two modes:
 *  - folder/fileName → builds URL from REPO_RAW_BASE (existing behaviour, unchanged)
 *  - explicitUrl     → downloads directly from that URL (e.g. a GitHub Release asset)
 *
 * OkHttp follows redirects automatically (followRedirects=true), so GitHub
 * Release's 302 redirect to objects.githubusercontent.com is handled transparently.
 */
object PackDownloadManager {

    private const val REPO_OWNER = "infinitydattaashim1210958-coder"
    private const val REPO_NAME  = "-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w"
    private const val BRANCH     = "main"
    private const val BASE_URL   = "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/$BRANCH"

    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun packsDir(context: Context): File =
        File(context.filesDir, "packs").apply { mkdirs() }

    fun isDownloaded(context: Context, folder: String, fileName: String): Boolean =
        localDbFile(context, fileName).exists()

    private fun localDbFile(context: Context, fileName: String): File {
        val plain = fileName.removeSuffix(".gz")
        return File(packsDir(context), plain)
    }

    /**
     * Downloads (if needed) and returns an open [SQLiteDatabase].
     *
     * @param folder      subfolder under the raw CDN base (ignored when explicitUrl is set)
     * @param fileName    e.g. "rabindra.db.gz"
     * @param explicitUrl full download URL override — use for Release assets
     */
    suspend fun openPack(
        context: Context,
        folder: String,
        fileName: String,
        explicitUrl: String? = null,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<SQLiteDatabase> = withContext(Dispatchers.IO) {
        try {
            val dest = localDbFile(context, fileName)
            if (!dest.exists()) {
                val url = explicitUrl?.takeIf { it.isNotBlank() }
                    ?: "$BASE_URL/$folder/$fileName"
                download(url, dest, onProgress)
            }
            val db = SQLiteDatabase.openDatabase(
                dest.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            Result.success(db)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun download(
        url: String,
        dest: File,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw java.io.IOException("Download failed (${response.code}) for $url")
            val body = response.body ?: throw java.io.IOException("Empty body for $url")
            val total = body.contentLength()
            val tmpGz = File(dest.parentFile, "${dest.name}.gz.part")
            var downloaded = 0L
            body.byteStream().use { input ->
                tmpGz.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        onProgress?.invoke(downloaded, total)
                    }
                }
            }
            GZIPInputStream(tmpGz.inputStream()).use { gzIn ->
                dest.outputStream().use { out -> gzIn.copyTo(out) }
            }
            tmpGz.delete()
        }
    }

    fun clearAll(context: Context) {
        packsDir(context).listFiles()?.forEach { it.delete() }
    }

    fun deleteLocalPack(context: Context, fileName: String) {
        localDbFile(context, fileName).delete()
    }
}
