package com.kyronix.swadhyaa.data.auth

/**
 * In-app Google identity session (authentication only).
 * Drive authorization is tracked separately in [DriveAuthState].
 *
 * Never stores access/refresh tokens. Identity fields come from Credential Manager
 * (Google ID token payload / profile). Real sign-in requires a valid Web client ID
 * configured via [GoogleAuthConfig] / BuildConfig — see docs/GOOGLE_OAUTH_SETUP.md.
 */
data class GoogleAccountSession(
    val subjectId: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val idToken: String? = null
)

enum class DriveAuthState {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    EXPIRED
}

enum class SyncState {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    NEEDS_ATTENTION
}
