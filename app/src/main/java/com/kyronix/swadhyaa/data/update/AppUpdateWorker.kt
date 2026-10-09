package com.kyronix.swadhyaa.data.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kyronix.swadhyaa.R
import com.kyronix.swadhyaa.presentation.shell.ShellActivity
import java.util.concurrent.TimeUnit

/**
 * Background periodic worker (WorkManager, every 6 h, requires network).
 *
 * On each run it calls [UpdateChecker.check].  If a newer build exists it
 * posts a heads-up notification.  Tapping the notification opens
 * [ShellActivity] with [ShellActivity.EXTRA_SHOW_UPDATE] = true so the
 * settings tab scrolls straight to the update card.
 *
 * Schedule once from [com.kyronix.swadhyaa.SwadhyayApp.onCreate].
 */
class AppUpdateWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val info = UpdateChecker.check(ctx) ?: return Result.success()
        if (info.isNewer) notify(ctx, info)
        return Result.success()
    }

    private fun notify(context: Context, info: UpdateChecker.UpdateInfo) {
        val tapIntent = Intent(context, ShellActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ShellActivity.EXTRA_SHOW_UPDATE, true)
        }
        val pi = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("স্বাধ্যায় আপডেট পাওয়া গেছে")
            .setContentText("বিল্ড #${info.availableBuild} প্রস্তুত — ট্যাপ করে ইন্সটল করুন")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("নতুন বিল্ড #${info.availableBuild} পাওয়া গেছে (বর্তমান: #${info.currentBuild})। স্বাধ্যায় অ্যাপে ট্যাপ করে আপডেট নিন।")
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, notif)
    }

    companion object {
        const val CHANNEL_ID = "swadhyay_updates"
        const val CHANNEL_NAME = "স্বাধ্যায় আপডেট"
        private const val NOTIF_ID = 8001

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<AppUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "swadhyay_update_check",
                    ExistingPeriodicWorkPolicy.KEEP,
                    req
                )
        }
    }
}
