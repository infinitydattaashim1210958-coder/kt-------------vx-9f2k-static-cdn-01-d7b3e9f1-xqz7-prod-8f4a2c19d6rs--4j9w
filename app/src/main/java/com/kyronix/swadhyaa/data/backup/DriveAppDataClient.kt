package com.kyronix.swadhyaa.data.backup

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Drive REST client for appDataFolder only.
 * Uses a short-lived access token supplied by the caller (never stored here).
 *
 * Safe write pattern: upload under a temporary name, then rename/replace the
 * canonical backup file so a failed upload cannot destroy a valid existing backup.
 */
class DriveAppDataClient(
    private val http: OkHttpClient = defaultClient()
) {
    sealed class DriveOpResult {
        data class Ok(val fileId: String?, val content: String? = null) : DriveOpResult()
        data object NotFound : DriveOpResult()
        data class QuotaExceeded(val message: String) : DriveOpResult()
        data class AuthError(val message: String) : DriveOpResult()
        data class Error(val message: String) : DriveOpResult()
    }

    fun findBackupFile(accessToken: String, fileName: String = BackupPayload.BACKUP_FILE_NAME): DriveOpResult {
        val q = "name = '$fileName' and 'appDataFolder' in parents and trashed = false"
        val url = "$BASE/files?spaces=appDataFolder&q=${java.net.URLEncoder.encode(q, "UTF-8")}&fields=files(id,name,modifiedTime)"
        return execute(accessToken, Request.Builder().url(url).get().build()) { body ->
            val files = JSONObject(body).optJSONArray("files")
            if (files == null || files.length() == 0) DriveOpResult.NotFound
            else DriveOpResult.Ok(files.getJSONObject(0).getString("id"))
        }
    }

    fun downloadFile(accessToken: String, fileId: String): DriveOpResult {
        val url = "$BASE/files/$fileId?alt=media"
        return execute(accessToken, Request.Builder().url(url).get().build()) { body ->
            DriveOpResult.Ok(fileId, body)
        }
    }

    /**
     * Safe upload: write to temp name first, then patch name to canonical (or
     * create canonical if none exists). On failure the previous canonical file remains.
     */
    fun uploadBackupSafely(
        accessToken: String,
        content: String,
        fileName: String = BackupPayload.BACKUP_FILE_NAME
    ): DriveOpResult {
        val tempName = "${fileName}.tmp.${System.currentTimeMillis()}"
        val create = createInAppData(accessToken, tempName, content)
        if (create !is DriveOpResult.Ok || create.fileId == null) return create

        val existing = findBackupFile(accessToken, fileName)
        when (existing) {
            is DriveOpResult.Ok -> {
                // Delete old canonical, rename temp → canonical via update metadata.
                deleteFile(accessToken, existing.fileId!!)
                return renameFile(accessToken, create.fileId, fileName)
            }
            is DriveOpResult.NotFound -> {
                return renameFile(accessToken, create.fileId, fileName)
            }
            else -> {
                // Cleanup temp on unexpected error listing existing.
                deleteFile(accessToken, create.fileId)
                return existing
            }
        }
    }

    private fun createInAppData(accessToken: String, name: String, content: String): DriveOpResult {
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
        val boundary = "swadhyay_${System.currentTimeMillis()}"
        val bodyStr = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content)
            append("\r\n--$boundary--\r\n")
        }
        val request = Request.Builder()
            .url("$UPLOAD_BASE/files?uploadType=multipart&spaces=appDataFolder")
            .post(bodyStr.toRequestBody("multipart/relat
... 
