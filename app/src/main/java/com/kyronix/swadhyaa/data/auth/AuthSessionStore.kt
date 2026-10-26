package com.kyronix.swadhyaa.data.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kyronix.swadhyaa.data.prefs.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists Google identity session metadata and sync flags in the shared DataStore.
 * Does NOT store access tokens, refresh tokens, or ID tokens long-term.
 * ID token may be held briefly in memory only by [GoogleAuthManager].
 */
class AuthSessionStore(private val context: Context) {

    private object Keys {
        val subjectId = stringPreferencesKey("auth_google_subject_id")
        val email = stringPreferencesKey("auth_google_email")
        val displayName = stringPreferencesKey("auth_google_display_name")
        val photoUrl = stringPreferencesKey("auth_google_photo_url")
        val driveAuthState = stringPreferencesKey("auth_drive_state")
        val cloudSyncEnabled = booleanPreferencesKey("auth_cloud_sync_enabled")
        val syncState = stringPreferencesKey("auth_sync_state")
        val lastSyncedAt = longPreferencesKey("auth_last_synced_at")
        val pendingDirty = booleanPreferencesKey("auth_pending_dirty")
        val lastBackupAccountId = stringPreferencesKey("auth_last_backup_account_id")
        val lastLocalChangeAt = longPreferencesKey("auth_last_local_change_at")
    }

    val sessionFlow: Flow<GoogleAccountSession?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.subjectId] ?: return@map null
        GoogleAccountSession(
            subjectId = id,
            email = prefs[Keys.email],
            displayName = prefs[Keys.displayName],
            photoUrl = prefs[Keys.photoUrl],
            idToken = null
        )
    }

    val driveAuthStateFlow: Flow<DriveAuthState> = context.dataStore.data.map { prefs ->
        runCatching { DriveAuthState.valueOf(prefs[Keys.driveAuthState] ?: DriveAuthState.NOT_REQUESTED.name) }
            .getOrDefault(DriveAuthState.NOT_REQUESTED)
    }

    val syncStateFlow: Flow<SyncState> = context.dataStore.data.map { prefs ->
        runCatching { SyncState.valueOf(prefs[Keys.syncState] ?: SyncState.LOCAL_ONLY.name) }
            .getOrDefault(SyncState.LOCAL_ONLY)
    }

    val cloudSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.cloudSyncEnabled] ?: false
    }

    suspend fun saveSession(session: GoogleAccountSession) {
        context.dataStore.edit { prefs ->
            prefs[Keys.subjectId] = session.subjectId
            session.email?.let { prefs[Keys.email] = it } ?: prefs.remove(Keys.email)
            session.displayName?.let { prefs[Keys.displayName] = it } ?: prefs.remove(Keys.displayName)
            session.photoUrl?.let { prefs[Keys.photoUrl] = it } ?: prefs.remove(Keys.photoUrl)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.subjectId)
            prefs.remove(Keys.email)
            prefs.remove(Keys.displayName)
            prefs.remove(Keys.photoUrl)
            prefs[Keys.driveAuthState] = DriveAuthState.NOT_REQUESTED.name
            prefs[Keys.cloudSyncEnabled] = false
            prefs[Keys.syncState] = SyncState.LOCAL_ONLY.name
            // Keep lastBackupAccountId so we can refuse cross-account upload after re-login.
        }
    }

    suspend fun setDriveAuthState(state: DriveAuthState) {
        context.dataStore.edit { it[Keys.driveAuthState] = state.name }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.cloudSyncEnabled] = enabled }
    }

    suspend fun setSyncState(state: SyncState) {
        context.dataStore.edit { it[Keys.syncState] = state.name }
    }

    suspend fun markSynced(at: Long = System.currentTimeMillis()) {
        context.dataStore.edit {
            it[K
... 
