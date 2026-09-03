package com.kyronix.swadhyaa.data.packs

import android.content.Context
import com.kyronix.swadhyaa.data.prefs.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads offline bhāṣya / commentary packs into app filesDir.
 * Base URL points at the separated DB repository Releases (override as needed).
 *
 * Pack files are expected as: {packFile} under release assets.
 */
class PackDownloader(
    private val context: Context,
    private val prefs: UserPrefs,
    private val baseReleaseUrl: String =
        "https://github.com/infinitydattaashim1210958-coder/-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w/releases/download/v1"
) {
    data class PackInfo(
        val id: String,
        val title: String,
        val fileName: String,
        val sizeLabel: String
    )

    fun packsDir(): File = File(context.filesDir, "packs").also { it.mkdirs() }

    fun localFile(fileName: String): File = File(packsDir(), fileName)

    suspend fun isReady(fileName: String): Boolean = withContext(Dispatchers.IO) {
        localFile(fileName).exists() || prefs.isPackDownloaded(fileName)
    }

    /**
     * Downloads pack. Returns local path or throws.
     * Uses plain HttpURLConnection (no extra deps).
     */
    suspend fun download(fileName: String, onProgress: ((Int) -> Unit)? = null): File =
        withContext(Dispatchers.IO) {
            val dest = localFile(fileName)
            if (dest.exists() && dest.length() > 0) {
                prefs.markPackDownloaded(fileName)
                return@withContext dest
            }
            val url = URL("$baseReleaseUrl/$fileName")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ${conn.responseCode} for $fileName")
                }
                val total = conn.contentLengthLong.coerceAtLeast(1L)
                val tmp = File(dest.absolutePath + ".part")
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            read += n
                            onProgress?.invoke(((read * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                prefs.markPackDownloaded(fileName)
                dest
            } finally {
                conn.disconnect()
            }
        }
}
