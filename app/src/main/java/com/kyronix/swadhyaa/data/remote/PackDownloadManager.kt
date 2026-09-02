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
 * Downloads gz-compressed SQLite "packs" (bhashya, Ramayana kanda, Mahabharata parba)
 * from the DB repo on GitHub, decompresses them once, and caches the plain .db under
 * the app's files dir. Every Veda/Ramayana/Mahabharata bhashya feature shares this.
 *
 * Repo layout (raw.githubusercontent.com/<REPO>/main/<folder>/<file>):
 *   bhashya_packs/scholar_<id>.db.gz
 *   ramayana-kanpur-iit/ramayana_kanda_<n>.db.gz
 *   mahabharata_kaliprasanna/mahabharata_parba_<n>.db.gz
 */
object PackDownloadManager {

    // NOTE: confirm this matches the actual default branch / repo slug if a 404 occurs.
    private const val REPO_OWNER = "infinitydattaashim1210958-coder"
    private const val REPO_NAME = "-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w"
    private const val BRANCH = "main"
    private const val BASE_URL = "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/$BRANCH"

    private val client by lazy {
        OkHttpClient.Builder().build()
    }

    private fun packsDir(context: Context): File =
        File(context.filesDir, "packs").apply { mkdirs() }

    /** True if this pack has already been downloaded & decompressed. */
    fun isDownloaded(context: Context, folder: String, fileName: String): Boolean =
        localDbFile(context, fileName).exists()

    private fun localDbFile(context: Context, fileName: String): File {
        val plain = fileName.removeSuffix(".gz")
        return File(packsDir(context), plain)
    }

    /**
     * Downloads (if needed) and returns a readable [SQLiteDatabase] handle for the pack.
     * @param folder e.g. "bhashya_packs", "ramayana-kanpur-iit", "mahabharata_kaliprasanna"
     * @param fileName e.g. "scholar_29.db.gz"
     */
    suspend fun openPack(
        context: Context,
        folder: String,
        fileName: String,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<SQLiteDatabase> = withContext(Dispatchers.IO) {
        try {
            val dest = localDbFile(context, fileName)
            if (!dest.exists()) {
                download(folder, fileName, dest, onProgress)
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
        folder: String,
        fileName: String,
        dest: File,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        val url = "$BASE_URL/$folder/$fileName"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("Download failed (${response.code}) for $url")
            }
            val body = response.body ?: throw java.io.IOException("Empty response body for $url")
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
            // gunzip into final destination
            GZIPInputStream(tmpGz.inputStream()).use { gzIn ->
                dest.outputStream().use { out -> gzIn.copyTo(out) }
            }
            tmpGz.delete()
        }
    }

    /** Clears every cached pack (settings screen "clear downloaded data" affordance). */
    fun clearAll(context: Context) {
        packsDir(context).listFiles()?.forEach { it.delete() }
    }
}
