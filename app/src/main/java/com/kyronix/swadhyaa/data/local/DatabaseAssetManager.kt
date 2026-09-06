package com.kyronix.swadhyaa.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Downloads, verifies and installs the two core SQLite databases from the
 * authoritative DB source repository GitHub Release.
 *
 * FIX: Removed hardcoded size checks — they broke on every DB update.
 *      Integrity is now verified by checking the extracted .db file size
 *      (> 1 MB for core, > 500 KB for ramayana) which is far more reliable.
 */
object DatabaseAssetManager {

    private const val TAG = "DbAssetManager"

    private const val REPO_OWNER = "infinitydattaashim1210958-coder"
    private const val REPO_NAME = "-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w"
    private const val CURRENT_RELEASE_TAG = "v1"

    private const val CORE_GZ     = "core.db.gz"
    private const val RAMAYANA_GZ = "ramayana_core.db.gz"
    private const val CORE_DB     = "core.db"
    private const val RAMAYANA_DB = "ramayana_core.db"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val mutex = Mutex()

    // ── Public state for UI ───────────────────────────────────────────
    enum class State {
        IDLE, CHECKING, DOWNLOADING, VERIFYING,
        EXTRACTING, INSTALLING, COMPLETED, FAILED
    }

    data class Progress(
        val state: State = State.IDLE,
        val currentAsset: String? = null,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val message: String? = null,
        val error: String? = null
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    // ── Paths ─────────────────────────────────────────────────────────
    private fun databasesDir(context: Context): File =
        File(context.filesDir, "databases").also { it.mkdirs() }

    fun coreDbFile(context: Context): File =
        File(databasesDir(context), CORE_DB)

    fun ramayanaDbFile(context: Context): File =
        File(databasesDir(context), RAMAYANA_DB)

    fun isCoreReady(context: Context): Boolean =
        coreDbFile(context).let { it.exists() && it.length() > 1_000_000 }

    fun isRamayanaReady(context: Context): Boolean =
        ramayanaDbFile(context).let { it.exists() && it.length() > 500_000 }

    fun areBothReady(context: Context): Boolean =
        isCoreReady(context) && isRamayanaReady(context)

    // ── Main entry point ──────────────────────────────────────────────
    suspend fun ensureReady(context: Context): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                update(State.CHECKING)

                if (areBothReady(context)) {
                    Log.i(TAG, "Both databases already present and valid")
                    update(State.COMPLETED)
                    return@withContext true
                }

                if (!isCoreReady(context)) {
                    installAsset(context, CORE_GZ, CORE_DB, minExtractedBytes = 1_000_000)
                }

                if (!isRamayanaReady(context)) {
                    installAsset(context, RAMAYANA_GZ, RAMAYANA_DB, minExtractedBytes = 500_000)
                }

                val ok = areBothReady(context)
                if (ok) {
                    update(State.COMPLETED, message = "Databases ready")
                    Log.i(TAG, "Database installation completed successfully")
                } else {
                    update(State.FAILED, error = "Database verification failed after install")
                }
                ok
            } catch (e: Exception) {
                Log.e(TAG, "ensureReady failed", e)
                update(State.FAILED, error = e.message ?: e.toString())
                false
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────
    private suspend fun installAsset(
        context: Context,
        gzName: String,
        dbName: String,
        minExtractedBytes: Long   // integrity check on the extracted DB, not the gz
    ) {
        val dir   = databasesDir(context)
        val finalDb = File(dir, dbName)
        val tmpGz   = File(dir, "$gzName.part")
        val tmpDb   = File(dir, "$dbName.part")

        try {
            // 1. Download gz (no size pre-check — actual size varies per release)
            update(State.DOWNLOADING, currentAsset = gzName)
            download(gzName, tmpGz)

            if (tmpGz.length() < 100_000) {
                throw IOException("Downloaded $gzName is too small (${tmpGz.length()} bytes) — download may have failed")
            }
            Log.i(TAG, "$gzName downloaded (${tmpGz.length()} bytes)")

            // 2. Extract
            update(State.EXTRACTING, currentAsset = gzName)
            GZIPInputStream(tmpGz.inputStream().buffered()).use { gzIn ->
                tmpDb.outputStream().buffered().use { out -> gzIn.copyTo(out) }
            }

            // 3. Verify extracted DB is plausible
            update(State.VERIFYING, currentAsset = dbName)
            if (tmpDb.length() < minExtractedBytes) {
                throw IOException(
                    "Extracted $dbName too small: ${tmpDb.length()} bytes (expected > $minExtractedBytes)"
                )
            }
            Log.i(TAG, "Extracted $dbName (${tmpDb.length()} bytes) — OK")

            // 4. Atomic install
            update(State.INSTALLING, currentAsset = dbName)
            if (finalDb.exists()) finalDb.delete()
            if (!tmpDb.renameTo(finalDb)) {
                tmpDb.copyTo(finalDb, overwrite = true)
                tmpDb.delete()
            }
            Log.i(TAG, "Installed $dbName → ${finalDb.absolutePath}")

        } finally {
            tmpGz.delete()
            tmpDb.delete()
        }
    }

    private fun download(assetName: String, dest: File) {
        val url = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$CURRENT_RELEASE_TAG/$assetName"
        Log.i(TAG, "Downloading $url")

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $assetName")
            }
            val body  = response.body ?: throw IOException("Empty body for $assetName")
            val total = body.contentLength().coerceAtLeast(1L)

            body.byteStream().use { input ->
                dest.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        read += n
                        _progress.value = _progress.value.copy(
                            downloadedBytes = read,
                            totalBytes = total
                        )
                    }
                }
            }
        }
    }

    private fun update(
        state: State,
        currentAsset: String? = null,
        message: String? = null,
        error: String? = null
    ) {
        _progress.value = Progress(
            state = state,
            currentAsset = currentAsset ?: _progress.value.currentAsset,
            downloadedBytes = if (state == State.DOWNLOADING) _progress.value.downloadedBytes else 0,
            totalBytes      = if (state == State.DOWNLOADING) _progress.value.totalBytes else 0,
            message = message,
            error   = error
        )
    }

    fun clearCache(context: Context) {
        databasesDir(context).listFiles()?.forEach { it.delete() }
        _progress.value = Progress(state = State.IDLE)
        Log.i(TAG, "Database cache cleared")
    }
}
