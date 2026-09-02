package com.kyronix.swadhyaa.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Startup integrity check. Run once after Application.onCreate.
 * Fails fast if asset DBs are missing or corrupted.
 */
object DatabaseVerifier {

    private const val TAG = "DbVerifier"

    data class Report(
        val coreMantraCount: Int,
        val coreVedaCount: Int,
        val ramayanaShlokaCount: Int,
        val ramayanaKandaCount: Int,
        val ok: Boolean
    )

    suspend fun verify(context: Context): Report = withContext(Dispatchers.IO) {
        try {
            val core = CoreDatabase.getInstance(context)
            val ram = RamayanaCoreDatabase.getInstance(context)

            val mantraCount = core.vedaDao().getMantraCount(
                core.vedaDao().getVedaByCode("rigveda")?.id ?: 1
            )
            // Full count across all vedas
            val totalMantras = core.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM mantras").use { c ->
                    c.moveToFirst(); c.getInt(0)
                }
            val vedaCount = core.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM vedas").use { c ->
                    c.moveToFirst(); c.getInt(0)
                }
            val shlokaCount = ram.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM shlokas").use { c ->
                    c.moveToFirst(); c.getInt(0)
                }
            val kandaCount = ram.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM kandas").use { c ->
                    c.moveToFirst(); c.getInt(0)
                }

            val ok = totalMantras == 20380 &&
                    vedaCount == 4 &&
                    shlokaCount == 17802 &&
                    kandaCount == 6

            val report = Report(totalMantras, vedaCount, shlokaCount, kandaCount, ok)
            Log.i(TAG, "Verification: $report")
            if (!ok) Log.e(TAG, "DATABASE INTEGRITY FAILED — counts do not match expected")
            report
        } catch (e: Exception) {
            Log.e(TAG, "Verification crashed", e)
            Report(-1, -1, -1, -1, false)
        }
    }
}
