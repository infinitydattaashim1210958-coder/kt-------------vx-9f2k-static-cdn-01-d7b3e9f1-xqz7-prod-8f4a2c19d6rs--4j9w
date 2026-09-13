package com.kyronix.swadhyaa

import android.app.Application
import android.util.Log
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.ui.theme.AppColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application entry point.
 *
 * core.db / ramayana_core.db are bundled APK assets (Room createFromAsset)
 * — no network dependency on first launch, matching legacy's offline
 * guarantee. See RISK_REGISTER.md R9 for why this used to be a runtime
 * download and why that was reverted.
 *
 * Startup sequence:
 * 0. Apply the stored accent color (RISK_REGISTER.md R6) — synchronously,
 *    via runBlocking, because it must be ready before the first Activity's
 *    onCreate() reads AppColors to draw its first frame. This is a single
 *    fast local DataStore read at process start (before any Activity
 *    exists yet) — a different, defensible situation from blocking the
 *    main thread during an Activity's own lifecycle, which this is not.
 *    Falls back to the default (gold) accent on any read failure rather
 *    than crashing app startup over a cosmetic setting.
 *
 * BUGFIX/CHANGE: integrity verification (DatabaseVerifier) and the
 * legacy-data migration engine (LegacyMigrationEngine, RISK_REGISTER.md
 * R3) used to run right here, fire-and-forget in a background
 * CoroutineScope with no UI tied to them at all — meaning a fast device
 * could reach a Veda-reading screen before verification/migration had
 * actually finished, and neither ever surfaced to the user in any way
 * (Logcat only). Both moved to SplashActivity (the new LAUNCHER activity
 * — see AndroidManifest.xml), which now genuinely waits on them before
 * proceeding to ShellActivity, and shows "নমস্কার, ডাটাবেস লোড হচ্ছে…"
 * while they run. See SplashActivity's doc comment for the detail.
 *
 * Hard database gate remains the unit test in CI (DatabaseVerificationTest),
 * which checks the exact same file this runtime check does — both read
 * from the same source (app/src/main/assets/databases/), not disconnected
 * copies.
 */
class SwadhyayApp : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            val accent = runBlocking { SettingsRepository(this@SwadhyayApp).settingsFlow.first().accentTheme }
            AppColors.applyAccent(accent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply stored accent, using default", e)
            AppColors.applyAccent("gold")
        }
    }

    companion object {
        private const val TAG = "SwadhyayApp"
    }
}
