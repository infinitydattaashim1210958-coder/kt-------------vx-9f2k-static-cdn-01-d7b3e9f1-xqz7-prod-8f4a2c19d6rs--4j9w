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
 * type: "html" (repo-hosted interactive HTML page, opened externally —
 * see LibraryHtmlBookRepository) or "db" (db.gz pack merged into
 * MasterDatabase — see LibraryDbBookRepository). Verified directly
 * against lib.js's fetchBlogBooks(): older manifest entries have no type
 * field at all and must default to "html" so they keep working unchanged.
 */
data class LibraryBookInfo(
    val id: String,
    val title: String,
    val filename: String,
    val date: String,
    val type: String
)

object LibraryManifest {

    private const val REPO_RAW_BASE =
        "https://raw.githubusercontent.com/infinitydattaashim1210958-coder/" +
            "-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w/main/library_books/"
    private const val MANIFEST_URL = REPO_RAW_BASE + "manifest.json"

    fun bookDownloadUrl(filename: String): String = REPO_RAW_BASE + filename

    private val keyManifestCache = stringPreferencesKey("library_manifest_cache_json")

    private val client by lazy { OkHttpClient.Builder().build() }

    /**
     * Fetches the live manifest; on any network failure, falls back to
     * the last successfully-cached copy (matches lib.js exactly, including
     * its Bengali error message for the "no network and no cache" case —
     * kept in the source language rather than translated, since it's
     * user-facing).
     */
    suspend fun fetch(context: Context): Result<List<LibraryBookInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(MANIFEST_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body?.string() ?: throw IOException("Empty response body")
                val books = parseManifestJson(body) // validate before caching a body that might not even parse
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

    /** Pure JSON parsing — testable without network or Context. */
    internal fun parseManifestJson(json: String): List<LibraryBookInfo> {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            LibraryBookInfo(
                id = b.getString("id"),
                title = b.getString("title"),
                filename = b.getString("filename"),
                date = b.optString("date", ""),
                type = b.optString("type", "html")
            )
        }
    }
}
