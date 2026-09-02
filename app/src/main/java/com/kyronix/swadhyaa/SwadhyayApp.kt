package com.kyronix.swadhyaa

import android.app.Application
import android.util.Log
import com.kyronix.swadhyaa.data.local.DatabaseVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Database integrity verification runs only in debug builds / CI.
 * Release builds skip the hard gate to avoid crashing users on
 * legitimate future schema evolution.
 */
class SwadhyayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            appScope.launch {
                val report = DatabaseVerifier.verify(this@SwadhyayApp)
                if (!report.ok) {
                    Log.e(TAG, "DATABASE INTEGRITY GATE FAILED: $report")
                    // In CI the unit test is the authoritative gate.
                    // Here we only log so the process can still start for debugging.
                } else {
                    Log.i(TAG, "DATABASE INTEGRITY GATE PASSED: $report")
                }
            }
        }
    }

    companion object {
        private const val TAG = "SwadhyayApp"
    }
}
