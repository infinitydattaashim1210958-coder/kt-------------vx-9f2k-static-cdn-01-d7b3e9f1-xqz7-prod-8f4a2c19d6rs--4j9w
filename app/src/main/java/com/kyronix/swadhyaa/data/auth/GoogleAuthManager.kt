package com.kyronix.swadhyaa.data.auth

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Google-only sign-in via Credential Manager + Sign in with Google button option.
 *
 * Does NOT request Drive scopes. Drive authorization is a separate flow
 * ([DriveAuthManager]).
 *
 * Real sign-in requires [GoogleAuthConfig.isConfigured] == true.
 * When not configured, [signIn] returns [AuthResult.NotConfigured].
 */
class GoogleAuthManager(
    private val context: Context,
    private val sessionStore: AuthSessionStore = AuthSessionStore(context)
) {
    private val credentialManager = CredentialManager.create(context)

    sealed class AuthResult {
        data class Success(val session: GoogleAccountSession) : AuthResult()
        data object Cancelled : AuthResult()
        data object NotConfigured : AuthResult()
        data class Error(val message: String, val cause: Throwable? = null) : AuthResult()
    }

    suspend fun signIn(activity: Activity): AuthResult {
        if (!GoogleAuthConfig.isConfigured) {
            return AuthResult.NotConfigured
        }
        return try {
            val nonce = createNonce()
            val option = GetSignInWithGoogleOption.Builder(GoogleAuthConfig.webClientId)
                .setNonce(nonce)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                val session = sessionFromIdToken(googleId)
                sessionStore.saveSession(session)
                AuthResult.Success(session)
            } else {
                AuthResult.Error("Unexpected credential type: ${credential.type}")
            }
        } catch (_: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (_: NoCredentialException) {
            AuthResult.Error("No Google account available on this device")
        } catch (e: GoogleIdTokenParsingException) {
            AuthResult.Error("Failed to parse Google ID token", e)
        } catch (e: GetCredentialException) {
            AuthResult.Error(e.message ?: "Sign-in failed", e)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unexpected sign-in error", e)
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "clearCredentialState failed (continuing local clear)", e)
        }
        sessionStore.clearSession()
    }

    suspend fun currentSession(): GoogleAccountSession? = sessionStore.getSessionOnce()

    private fun sessionFromIdToken(googleId: GoogleIdTokenCredential): GoogleAccountSession {
        val subject = extractSubject(googleId.idToken) ?: googleId.id
        return GoogleAccountSession(
            subjectId = subject,
            email = googleId.id.takeIf { it.contains("@") } ?: googleId.id,
      
... 
