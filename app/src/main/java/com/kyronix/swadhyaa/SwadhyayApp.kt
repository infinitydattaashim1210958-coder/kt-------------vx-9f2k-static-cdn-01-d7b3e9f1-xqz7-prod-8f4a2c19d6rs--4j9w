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
 * Hard database gate is the unit test in CI.
 */
class SwadhyayApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
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
