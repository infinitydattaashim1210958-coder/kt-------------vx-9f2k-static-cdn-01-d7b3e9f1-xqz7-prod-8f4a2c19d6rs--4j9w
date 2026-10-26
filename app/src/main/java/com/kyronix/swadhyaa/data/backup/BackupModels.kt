package com.kyronix.swadhyaa.data.backup

/**
 * Versioned personal backup payload for Google Drive appDataFolder.
 *
 * Schema version history:
 * - 1: continue position, bookmarks, library selections, reader settings subset.
 *
 * Notes: there is no free-form notes table in the app yet; do not claim notes
 * are backed up. Future notes support requires a separate product decision.
 */
data class BackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAt: Long,
    val accountSubjectId: String,
    val appVersionName: String?,
    val continuePos: ContinuePosBackup?,
    val bookmarks: List<BookmarkBackup>,
    val librarySelections: List<LibrarySelectionBackup>,
    val readerSettings: ReaderSettingsBackup?,
    val contentSha256: String = ""
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MIN_SUPPORTED_SCHEMA_VERSION = 1
        const val BACKUP_FILE_NAME = "swadhyay_backup_v1.json"
    }
}

data class ContinuePosBackup(
    val kind: String,
    val corpusId: Int,
    val itemId: Int,
    val label: String,
    val scrollPercent: Int? = null,
    val legacyHash: String? = null
)

data class BookmarkBackup(
    val kind: String,
    val corpusId: Int,
    val itemId: Int,
    val label: String,
    val snippet: String,
    val savedAt: Long,
    val note: String? = null,
    val scrollPercent: Int? = null,
    val legacyId: String? = null,
    val legacyHash: String? = null
)

data class LibrarySelectionBackup(
    val bookId: String,
    val chapterId: String,
    val paraSeq: Int,
    val selStart: Int,
    val selEnd: Int,
    val selectedText: String,
    val kind: String,
    val createdAt: Long
)

/**
 * Only explicitly selected reader/appearance settings that are safe to roam
 * with the account. Language/script preferences are included; nothing that
 * embeds tokens or device-only state.
 */
data class ReaderSettingsBackup(
    val fontSize: Int,
    val fontFamily: String,
    val banglaFont: String,
    val devanagariFont: String,
    val theme: String,
    val accentTheme: String,
    val lineHeight: String,
    val pageMargin: String,
    val justifyText: Boolean,
    val keepAwake: Boolean,
    val language: String,
    val script: String,
    val transliteration: Boolean
)

sealed class BackupValidationResult {
    data class Valid(val payload: BackupPayload) : BackupValidationResult()
    data class Invalid(val reason: String) : BackupValidationResult()
}

sealed class RestoreDecision {
    /** Replace local user data with cloud backup. */
    data object Replace : RestoreDecision()
    /** Keep local; do not apply cloud. */
    data object KeepLocal : RestoreDecision()
    /** Merge by timestamps where possible (bookmarks/selections); continue uses newer. */
    data object MergeByTimestamp : RestoreDecision()
}
