package com.kyronix.swadhyaa.data.auth

import com.kyronix.swadhyaa.BuildConfig

/**
 * OAuth client configuration for Google Sign-In (Credential Manager).
 *
 * The Web client ID is required by GetSignInWithGoogleOption / GetGoogleIdOption.
 * It must be supplied via BuildConfig from local.properties or CI secrets —
 * never hardcode client secrets or private keys.
 *
 * Until a real client ID is configured, [isConfigured] is false and the UI
 * must show that Google Sign-In is not ready (do not claim sign-in works).
 *
 * See docs/GOOGLE_OAUTH_SETUP.md for Console steps (package names, SHA-1,
 * Web + Android OAuth clients, google-services.json).
 */
object GoogleAuthConfig {

    /**
     * OAuth 2.0 Web client ID (type: Web application) from Google Cloud Console.
     * Used as serverClientId for Credential Manager Sign in with Google.
     * Empty string until the product owner supplies a real value.
     */
    val webClientId: String
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()

    val isConfigured: Boolean
        get() = webClientId.isNotEmpty() &&
            webClientId != "UNCONFIGURED" &&
            !webClientId.contains("YOUR_") &&
            webClientId.endsWith(".apps.googleusercontent.com")
}
