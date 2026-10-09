package com.kyronix.swadhyaa.data.update

import android.content.Context
import androidx.core.content.edit
import com.kyronix.swadhyaa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks the GitHub dev-latest release for a newer build and reports
 * whether an update is available for download.
 *
 * The workflow publishes two files on every successful main-branch push:
 *   • version.json  — {"buildNumber": N, "apkName": "app-dev-debug.apk"}
 *   • app-dev-debug.apk — the installable APK
 *
 * Both live at the fixed tag dev-latest in the app's own GitHub repo:
 *   https://github.com/{GITHUB_REPO}/releases/download/dev-latest/{file}
 */
object UpdateChecker {

    private const val PREFS = "update_prefs"
    private const val KEY_LAST_CHECK_MS  = "last_check_ms"
    private const val KEY_AVAIL_BUILD    = "available_build"
    private const val KEY_APK_URL        = "apk_url"
    private const val MIN_CHECK_INTERVAL = 6 * 60 * 60 * 1000L // 6 hours

    // Stable URLs — only change if the workflow tag or APK name changes.
    private val baseUrl get() = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/dev-latest"
    val versionJsonUrl  get() = "$baseUrl/version.json"
    val apkUrl          get() = "$baseUrl/app-dev-debug.apk"

    data class UpdateInfo(
        val availableBuild: Int,
        val currentBuild: Int,
        val apkUrl: String
    ) {
        val isNewer get() = availableBuild > currentBuild
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)   // GitHub redirects release asset downloads
            .build()
    }

    /**
     * Fetches version.json from GitHub and returns [UpdateInfo] if a newer
     * build exists, or null on error / unchanged.  Uses a 6-hour cooldown
     * so it doesn't spam GitHub on every app open.
     */
    suspend fun check(context: Context, force: Boolean = false): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            if (!force && now - prefs.getLong(KEY_LAST_CHECK_MS, 0) < MIN_CHECK_INTERVAL) {
                return@withContext getSaved(context) // return cached result
            }

            try {
                val req = Request.Builder().url(versionJsonUrl)
                    .header("Cache-Control", "no-cache").build()
                val body = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body?.string() ?: return@withContext null
                }
                val json = JSONObject(body)
                val remoteBuild = json.getInt("buildNumber")
                val currentBuild = BuildConfig.VERSION_CODE

                prefs.edit {
                    putLong(KEY_LAST_CHECK_MS, now)
                    putInt(KEY_AVAIL_BUILD, remoteBuild)
                    putString(KEY_APK_URL, apkUrl)
                }

                UpdateInfo(remoteBuild, currentBuild, apkUrl).takeIf { it.isNewer }
            } catch (_: Exception) {
                null
            }
        }

    /** Returns the last-seen update info without a network call. */
    fun getSaved(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val build = prefs.getInt(KEY_AVAIL_BUILD, 0)
        val url   = prefs.getString(KEY_APK_URL, null) ?: return null
        val info  = UpdateInfo(build, BuildConfig.VERSION_CODE, url)
        return info.takeIf { it.isNewer }
    }

    fun clearSaved(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putInt(KEY_AVAIL_BUILD, 0) }
    }
}
