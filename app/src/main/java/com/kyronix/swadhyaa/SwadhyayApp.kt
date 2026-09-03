package com.kyronix.swadhyaa

import android.app.Application
import android.util.Log
import com.kyronix.swadhyaa.data.local.DatabaseAssetManager
import com.kyronix.swadhyaa.data.local.DatabaseVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Startup sequence:
 * 1. Download + install core DBs from GitHub Release (if missing)
 * 2. Run integrity verification (counts)
 *
 * Hard database gate remains the unit test in CI.
 */
class SwadhyayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            // 1. Ensure databases are present (download from release if needed)
            val ready = DatabaseAssetManager.ensureReady(this@SwadhyayApp)
            if (!ready) {
                Log.e(TAG, "DATABASE DOWNLOAD/INSTALL FAILED: ${DatabaseAssetManager.progress.value.error}")
                return@launch
            }

            // 2. Integrity check
            val report = DatabaseVerifier.verify(this@SwadhyayApp)
            if (!report.ok) {
                Log.e(TAG, "DATABASE INTEGRITY GATE FAILED: $report")
            } else {
                Log.i(TAG, "DATABASE INTEGRITY GATE PASSED: $report")
            }
        }
    }

    companion object {
        private const val TAG = "SwadhyayApp"
    }
}
