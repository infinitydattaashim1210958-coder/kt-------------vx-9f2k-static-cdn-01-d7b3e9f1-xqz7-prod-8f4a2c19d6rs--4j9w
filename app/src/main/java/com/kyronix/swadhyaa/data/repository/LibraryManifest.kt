package com.kyronix.swadhyaa.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kyronix.swadhyaa.data.prefs.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * One entry in library_books/manifest.json.
 *
 * type: "html" or "db".
 * url: optional override download URL (e.g. a GitHub Release asset).
 *      When absent the app builds the URL from REPO_RAW_BASE + filename
 *      as before, so existing entries keep working unchanged.
 */
data class LibraryBookInfo(
    val id: String,
    val title: String,
    val filename: String,
    val date: String,
    val type: String,
    val url: String? = null          // ← নতুন: Release URL বা যেকোনো direct link
)

object LibraryManifest {

    private const val REPO_RAW_BASE =
        "https://raw.githubusercontent.com/infinitydattaashim1210958-coder/" +
            "-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w/main/library_books/"
    private const val MANIFEST_URL = REPO_RAW_BASE + "manifest.json"

    /**
     * Returns the download URL for a book.
     * If the manifest entry carries an explicit `url`, that wins.
     * Otherwise falls back to the raw-content CDN path (previous behaviour).
     */
    fun bookDownloadUrl(book: LibraryBookInfo): String =
        book.url?.takeIf { it.isNotBlank() } ?: (REPO_RAW_BASE + book.filename)

    private val keyManifestCache = stringPreferencesKey("library_manifest_cache_json")

    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)        // GitHub Release redirect স্বয়ংক্রিয়ভাবে follow করবে
            .followSslRedirects(true)
            .build()
    }

    suspend fun fetch(context: Context): Result<List<LibraryBookInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(MANIFEST_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body?.string() ?: throw IOException("Empty response body")
                val books = parseManifestJson(body)
                context.dataStore.edit { it[keyManifestCache] = body }
                Result.success(books)
            }
        } catch (networkErr: Exception) {
            val cached = context.dataStore.data.first()[keyManifestCache]
            if (cached != null) {
                try {
                    Result.success(parseManifestJson(cached))
                } catch (parseErr: Exception) {
                    Result.failure(networkErr)
                }
            } else {
                Result.failure(IOException("বইয়ের তালিকা পাওয়া যায়নি। একবার ইন্টারনেট চালু করে লাইব্রেরি খুলুন।", networkErr))
            }
        }
    }

    internal fun parseManifestJson(json: String): List<LibraryBookInfo> {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            LibraryBookInfo(
                id       = b.getString("id"),
                title    = b.getString("title"),
                filename = b.getString("filename"),
                date     = b.optString("date", ""),
                type     = b.optString("type", "html"),
                url      = b.optString("url", "").takeIf { it.isNotBlank() }   // ← নতুন
            )
        }
    }
}
