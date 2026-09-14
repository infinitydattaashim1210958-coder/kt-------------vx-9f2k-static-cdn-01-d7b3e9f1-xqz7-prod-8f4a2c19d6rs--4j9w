package com.kyronix.swadhyaa.presentation.splash

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.R
import com.kyronix.swadhyaa.data.local.DatabaseVerifier
import com.kyronix.swadhyaa.data.migration.LegacyMigrationEngine
import com.kyronix.swadhyaa.presentation.shell.ShellActivity
import com.kyronix.swadhyaa.ui.theme.AppColors
import com.kyronix.swadhyaa.ui.theme.FontManager
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * App entry point (the LAUNCHER activity — see AndroidManifest.xml;
 * ShellActivity no longer holds that role). Plays the KIG brand intro
 * video, then slides it away to reveal a welcome/loading screen ("ও৩ম্…
 * database loading") while real startup work runs, then proceeds to
 * ShellActivity.
 *
 * That startup work — DatabaseVerifier.verify() + LegacyMigrationEngine.
 * runIfNeeded() — used to run fire-and-forget in SwadhyayApp.onCreate(),
 * with no UI tied to it at all (see that file's history). Moved here so
 * "ডাটাবেস লোড হচ্ছে…" is describing something actually happening, not a
 * fixed timer — and so a slow device genuinely can't reach a
 * Veda-reading screen before verification/migration finishes, which the
 * old fire-and-forget approach didn't actually guarantee.
 *
 * The video and the startup work run concurrently (not sequentially) —
 * proceeding to ShellActivity waits on whichever finishes last, via the
 * two-flag videoDone/workDone check in maybeProceed(). In practice the
 * video (a few seconds) takes longer than the startup queries, so the
 * loading text is rarely on screen for long — that's intentional, not a
 * missing progress indicator.
 */
class SplashActivity : AppCompatActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private var videoDone = false
    private var workDone = false
    private var navigated = false

    private lateinit var videoContainer: FrameLayout
    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        lifecycleScope.launch {
            val report = DatabaseVerifier.verify(this@SplashActivity)
            if (report.ok) {
                LegacyMigrationEngine.runIfNeeded(this@SplashActivity)
            }
            // A failed report is intentionally not fatal here — matches
            // SwadhyayApp's previous behavior of logging and continuing
            // rather than blocking the user out of the app entirely over
            // a background integrity check.
            workDone = true
            maybeProceed()
        }

        videoView.setOnCompletionListener { onVideoFinished() }
        videoView.setOnErrorListener { _, _, _ -> onVideoFinished(); true }
        videoView.setOnPreparedListener { it.isLooping = false }
        val uri = Uri.parse("android.resource://$packageName/${R.raw.kig_intro}")
        videoView.setVideoURI(uri)
        videoView.start()
    }

    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(AppColors.bg)
        }

        root.addView(buildWelcomeLayer())
        root.addView(buildVideoLayer())

        return root
    }

    private fun buildVideoLayer(): FrameLayout {
        videoContainer = FrameLayout(this).apply {
            setBackgroundColor(AppColors.black)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Tap to skip straight to the welcome screen — a five-second
            // brand intro shouldn't be a wall between the user and the
            // app on every single launch.
            setOnClickListener { if (!videoDone) onVideoFinished() }
        }
        videoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        videoContainer.addView(videoView)
        return videoContainer
    }

    private fun buildWelcomeLayer(): LinearLayout {        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(AppColors.bg)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Ambient floating dots, behind the text — a handful of small,
        // faint, slowly-drifting circles for the same quiet-glow
        // atmosphere as the reference screenshot. Purely decorative.
        val dotLayer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        col.addView(dotLayer, 0)

        val om = TextView(this).apply {
            text = "ও৩ম্"
            setTextColor(AppColors.gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            gravity = Gravity.CENTER
            setShadowLayer(28f, 0f, 0f, AppColors.goldBright)
        }
        col.addView(om)

        val subtitle = TextView(this).apply {
            text = "নমস্কার, ডাটাবেস লোড হচ্ছে…"
            setTextColor(AppColors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        col.addView(subtitle)

        // Devanagari font, matching how ReaderActivity/GitaActivity/etc.
        // resolve it from Settings elsewhere in the app (see FontManager).
        // Splash renders before Settings would normally be read anywhere
        // else, so this uses FontManager's built-in default typeface
        // directly rather than blocking startup on a DataStore read here.
        om.typeface = FontManager.devanagariEntry("noto_serif_devanagari").typeface(this)

        dotLayer.post { scatterDots(dotLayer, dp(3), dp(5)) }

        return col
    }

    private fun scatterDots(layer: FrameLayout, minSize: Int, maxSize: Int) {
        val w = layer.width.takeIf { it > 0 } ?: return
        val h = layer.height.takeIf { it > 0 } ?: return
        repeat(6) {
            val size = Random.nextInt(minSize, maxSize + 1)
            val dot = View(this).apply {
                setBackgroundColor(Color.argb(90, 240, 194, 106)) // goldBright, faint
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    leftMargin = Random.nextInt(0, w - size)
                    topMargin = Random.nextInt(h / 3, h)
                }
            }
            layer.addView(dot)
            dot.animate()
                .translationY(-dp(24).toFloat())
                .alpha(0.2f)
                .setStartDelay(Random.nextLong(0, 1500))
                .setDuration(2200)
                .withEndAction {
                    dot.animate().translationY(0f).alpha(1f).setDuration(2200).start()
                }
                .start()
        }
    }

    private fun onVideoFinished() {
        if (videoDone) return
        videoContainer.animate()
            .translationY(videoContainer.height.toFloat())
            .alpha(0f)
            .setDuration(500)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                videoContainer.visibility = View.GONE
                videoDone = true
                maybeProceed()
            }
            .start()
    }

    private fun maybeProceed() {
        if (videoDone && workDone && !navigated) {
            navigated = true
            startActivity(Intent(this, ShellActivity::class.java))
            finish()
        }
    }
}
