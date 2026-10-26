package com.kyronix.swadhyaa.data.backup

import android.content.Context
import com.kyronix.swadhyaa.BuildConfig
import com.kyronix.swadhyaa.data.auth.AuthSessionStore
import com.kyronix.swadhyaa.data.auth.DriveAuthState
import com.kyronix.swadhyaa.data.auth.SyncState
import com.kyronix.swadhyaa.data.local.MasterDatabase
import com.kyronix.swadhyaa.data.local.entity.LibraryBookSelectionEntity
import com.kyronix.swadhyaa.data.prefs.ReaderSettings
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.data.prefs.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Collects local personal data, uploads/downloads versioned backups via Drive appDataFolder.
 * Never touches core.db / ramayana_core.db.
 *
 * Account isolation: refuses to upload if the active session subject does not match
 * the last backup account (or the cloud payload's accountSubjectId on restore).
 */
class PersonalBackupRepository(
    private val context: Context,
    private val userPrefs: UserPrefs = UserPrefs(context),
    private val settingsRepo: SettingsRepository = SettingsRepository(context),
    private val sessionStore: AuthSessionStore = AuthSessionStore(context),
    private val driveClient: DriveAppDataClient = DriveAppDataClient()
) {
    sealed class OpResult {
        data object Success : OpResult()
        data class BackupMissing(val message: String) : OpResult()
        data class ValidationFailed(val message: String) : OpResult()
        data class AccountMismatch(val message: String) : OpResult()
        data class AuthRequired(val message: String) : OpResult()
        data class QuotaError(val message: String) : OpResult()
        data class Failed(val message: String) : OpResult()
        data class NeedsConsent(val message: String = "Drive permission required") : OpResult()
    }

    suspend fun collectLocalPayload(accountSubjectId: String): BackupPayload = withContext(Dispatchers.IO) {
        val continuePos = userPrefs.continueFlow.first()?.let {
            ContinuePosBackup(
                kind = it.kind,
                corpusId = it.corpusId,
                itemId = it.itemId,
                label = it.label,
                scrollPercent = it.scrollPercent,
                legacyHash = it.legacyHash
            )
        }
        val bookmarks = userPrefs.bookmarksFlow.first().map {
            BookmarkBackup(
                kind = it.kind,
                corpusId = it.corpusId,
                itemId = it.itemId,
                label = it.label,
                snippet = it.snippet,
                savedAt = it.savedAt,
                note = it.note,
                scrollPercent = it.scrollPercent,
                legacyId = it.legacyId,
                legacyHash = it.legacyHash
            )
        }
        val dao = MasterDatabase.getInstance(context).masterDao()
        val selections = dao.getAllLibrarySelections().map {
            LibrarySelectionBackup(
                bookId = it.bookId,
                chapterId = it.chapterId,
                paraSeq = it.paraSeq,
                selStart = it.selStart,
                selEnd = it.selEnd,
                selectedText = it.selectedText,
                kind = it.kind,
                createdAt = it.createdAt
            )
        }
        val settings = settingsRepo.settingsFlow.first().let {
            ReaderSettingsBackup(
                fontSize = it.fontSize,
                fontFamily = it.fontFamily,
                banglaFont = it.banglaFont,
                devanagariFont = it.devanagariFont,
                theme = it.theme,
                accentTheme = it.accentTheme,
                lineHeight = it.lineHeight,
                pageMargin = it.pageMargin,
                justifyText = it.justifyText,
                keepAwake = it.keepAwake,
                language = it.language,
                script = it.script,
                transliteration = it.transliteration
            )
        }
        BackupPayload(
            crea
... 
