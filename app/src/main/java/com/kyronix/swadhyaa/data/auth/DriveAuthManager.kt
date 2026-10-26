package com.kyronix.swadhyaa.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Separate Drive authorization using AuthorizationClient (GIS).
 * Scope: [DriveScopes.DRIVE_APPDATA] only (hidden appDataFolder).
 *
 * Access tokens are obtained on demand for each sync/backup operation and
 * are never written to plaintext preferences. Callers hold the token in
 * memory for the duration of the network call only.
 *
 * Requires the user to already have a Google identity session for the app;
 * identity and Drive consent remain independent flows.
 */
class DriveAuthManager(
    private val context: Context,
    private val sessionStore: AuthSessionStore = AuthSessionStore(context)
) {
    sealed class DriveAuthResult {
        data class Granted(val accessToken: String) : DriveAuthResult()
        data class NeedsUserConsent(val intentSenderRequest: IntentSenderRequest) : DriveAuthResult()
        data object Denied : DriveAuthResult()
        data class Error(val message: String, val cause: Throwable? = null) : DriveAuthResult()
    }

    /**
     * Request or reuse Drive appDataFolder authorization.
     * If user consent UI is required, returns [DriveAuthResult.NeedsUserConsent];
     * the Activity must launch the IntentSender and then call [handleAuthorizationResult].
     */
    suspend fun requestAccess(activity: Activity): DriveAuthResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
                .build()
            val result = authorize(activity, request)
            processResult(result)
        } catch (e: Exception) {
            Log.w(TAG, "Drive authorize failed", e)
            DriveAuthResult.Error(e.message ?: "Drive authorization failed", e)
        }
    }

    fun handleAuthorizationResult(data: Intent?): DriveAuthResult {
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            processResult(result)
        } catch (e: Exception) {
            Log.w(TAG, "handleAuthorizationResult failed", e)
            DriveAuthResult.Error(e.message ?: "Failed to parse authorization result", e)
        }
    }

    /**
     * Obtain a fresh access token if previously granted (no UI).
     * Returns null if consent is missing or expired — caller should run [requestAccess].
     */
    suspend fun getAccessTokenIfGranted(activity: Activity): String? {
        return when (val r = requestAccess(activity)) {
            is DriveAuthResult.Granted -> r.accessToken
            else -> null
        }
    }

    suspend fun markDenied() {
        sessionStore.setDriveAuthState(DriveAuthState.DENIED)
    }

    suspend fun clearDriveAuth() {
        sessionStore.setDriveAuthState(DriveAuthState.NOT_REQUESTED)
        try {
            // Best-effort clear of cached tokens for this app.
            Identity.getAuthorizationClient(context)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun processResult(result: AuthorizationResult): DriveAuthResult {
        return when {
            result.hasResolution() -> {
                val pi = result.pendingIntent
                    ?: return DriveAuthResult.Error("Authorization resolution missing PendingIntent")
                DriveAuthResult.NeedsUserConsent(
                    IntentSenderRequest.Builder(pi.intentSender).build()
                )
            }
            !result.accessToken.isN
... 
