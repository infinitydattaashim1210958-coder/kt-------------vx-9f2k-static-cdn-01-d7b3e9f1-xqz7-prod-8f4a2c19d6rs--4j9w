package com.kyronix.swadhyaa.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * JSON serialization + integrity hash for [BackupPayload].
 * Pure JVM-friendly so unit tests do not need Android runtime.
 */
object BackupSerializer {

    fun toJson(payload: BackupPayload, includeHash: Boolean = true): String {
        val root = JSONObject()
        root.put("schemaVersion", payload.schemaVersion)
        root.put("createdAt", payload.createdAt)
        root.put("accountSubjectId", payload.accountSubjectId)
        root.put("appVersionName", payload.appVersionName ?: JSONObject.NULL)

        if (payload.continuePos != null) {
            root.put("continuePos", continueToJson(payload.continuePos))
        } else {
            root.put("continuePos", JSONObject.NULL)
        }

        val bookmarksArr = JSONArray()
        payload.bookmarks.forEach { bookmarksArr.put(bookmarkToJson(it)) }
        root.put("bookmarks", bookmarksArr)

        val selArr = JSONArray()
        payload.librarySelections.forEach { selArr.put(selectionToJson(it)) }
        root.put("librarySelections", selArr)

        if (payload.readerSettings != null) {
            root.put("readerSettings", settingsToJson(payload.readerSettings))
        } else {
            root.put("readerSettings", JSONObject.NULL)
        }

        // Hash is computed over the document without contentSha256, then inserted.
        if (includeHash) {
            val withoutHash = root.toString()
            val hash = sha256Hex(withoutHash)
            root.put("contentSha256", hash)
        } else if (payload.contentSha256.isNotEmpty()) {
            root.put("contentSha256", payload.contentSha256)
        }

        return root.toString()
    }

    fun fromJson(json: String): BackupValidationResult {
        return try {
            val root = JSONObject(json)
            val version = root.getInt("schemaVersion")
            if (version < BackupPayload.MIN_SUPPORTED_SCHEMA_VERSION ||
                version > BackupPayload.CURRENT_SCHEMA_VERSION
            ) {
                return BackupValidationResult.Invalid(
                    "Unsupported schema version $version (supported ${BackupPayload.MIN_SUPPORTED_SCHEMA_VERSION}..${BackupPayload.CURRENT_SCHEMA_VERSION})"
                )
            }

            val storedHash = root.optString("contentSha256", "")
            if (storedHash.isNotBlank()) {
                val copy = JSONObject(json)
                copy.remove("contentSha256")
                val expected = sha256Hex(copy.toString())
                if (!expected.equals(storedHash, ignoreCase = true)) {
                    return BackupValidationResult.Invalid("Integrity check failed (contentSha256 mismatch)")
                }
            }

            val accountId = root.getString("accountSubjectId")
            if (accountId.isBlank()) {
                return BackupValidationResult.Invalid("Missing accountSubjectId")
            }

            val continuePos = if (root.isNull("continuePos")) null
            else parseContinue(root.getJSONObject("continuePos"))

            val bookmarks = mutableListOf<BookmarkBackup>()
            val bArr = root.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until bArr.length()) {
                bookmarks.add(parseBookmark(bArr.getJSONObject(i)))
            }

            val selections = mutableListOf<LibrarySelectionBackup>()
            val sArr = root.optJSONArray("librarySelections") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                selections.add(parseSelection(sArr.getJSONObject(i)))
            }

            val settings = if (root.isNull("readerSettings")) null
            else parseSettings(root.getJSONObject("readerSettings"))

            val payload = BackupPayload(
                schemaVersion = version,
                createdAt = root.getLong("createdAt"),
                accountSubjectId = accountId,
                appVersionName = if (root.isNull("appVersionName")) null else root.o
... 
