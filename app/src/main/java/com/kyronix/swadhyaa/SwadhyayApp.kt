package com.kyronix.swadhyaa

import android.app.Application
import android.util.Log
import com.kyronix.swadhyaa.data.local.DatabaseVerifier
import com.kyronix.swadhyaa.data.migration.LegacyMigrationEngine
import com.kyronix.swadhyaa.data.prefs.SettingsRepository
import com.kyronix.swadhyaa.ui.theme.AppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * 1. Run integrity verification (row counts) against the bundled assets —
 *    catches a corrupted/mismatched asset early, in Logcat, before any
 *    screen tries to query it.
 * 2. Run the legacy-data migration engine (RISK_REGISTER.md R3), only if
 *    it hasn't already completed for the current schema version. Runs
 *    after step 1 because Veda-mantra hash resolution during migration
 *    needs CoreDatabase to be openable. Safe to run on every launch
 *    (no-ops immediately once done) and safe on a fresh install (no-ops
 *    when no legacy data is found).
 *
 * Hard database gate remains the unit test in CI (DatabaseVerificationTest),
 * which now checks the exact same file this runtime check does — both read
 * from the same source (app/src/main/assets/databases/), not disconnected
 * copies.
 */
class SwadhyayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        try {
            val accent = runBlocking { SettingsRepository(this@SwadhyayApp).settingsFlow.first().accentTheme }
            AppColors.applyAccent(accent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply stored accent, using default", e)
            AppColors.applyAccent("gold")
        }

        appScope.launch {
            val report = DatabaseVerifier.verify(this@SwadhyayApp)
            if (!report.ok) {
                Log.e(TAG, "DATABASE INTEGRITY GATE FAILED: $report")
                // Do not attempt migration against a DB that failed its
                // own integrity check — Veda-mantra hash resolution would
                // just fail too, and noisily so. Migration will retry on
                // the next launch (it hasn't been marked done).
                return@launch
            }
            Log.i(TAG, "DATABASE INTEGRITY GATE PASSED: $report")

            val migration = LegacyMigrationEngine.runIfNeeded(this@SwadhyayApp)
            if (migration.errors.isNotEmpty()) {
                Log.e(TAG, "LEGACY MIGRATION completed with errors: $migration")
            } else {
                Log.i(TAG, "LEGACY MIGRATION: $migration")
            }
        }
    }

    companion object {
        private const val TAG = "SwadhyayApp"
    }
}
