package com.kyronix.swadhyaa.presentation.splash

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.R
import com.kyronix.swadhyaa.data.local.DatabaseVerifier
import com.kyronix.swadhyaa.data.migration.LegacyMigrationEngine
import com.kyronix.swadhyaa.presentation.shell.ShellActivity
import com.kyronix.swadhyaa.ui.theme.AppColors
import com.kyronix.swadhyaa.ui.theme.FontManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * App entry point (the LAUNCHER activity — see AndroidManifest.xml;
 * ShellActivity no longer holds that role).
 *
 * Sequence: KIG logo (white bg, res/drawable/kig_logo.webp) for
 * LOGO_DURATION_MS, cross-fades into the "ও৩ম্ / নমস্কার, ডাটাবেস লোড
 * হচ্ছে…" welcome screen, which stays up for at least
 * WELCOME_MIN_DURATION_MS — then proceeds to ShellActivity once that
 * minimum has elapsed AND real startup work has actually finished,
 * whichever is later.
 *
 * CHANGE (was video-based): the KIG intro video approach was replaced
 * with this fixed-timing static-logo version per updated instructions —
 * no VideoView, no res/raw video asset needed anymore.
 *
 * That startup work — DatabaseVerifier.verify() + LegacyMigrationEngine.
 * runIfNeeded() — used to run fire-and-forget in SwadhyayApp.onCreate(),
 * with no UI tied to it at all (see that file's history). Moved here so
 * "ডাটাবেস লোড হচ্ছে…" is describing something actually happening, not
 * just a fixed timer — and so a slow device genuinely can't reach a
 * Veda-reading screen before verification/migration finishes, which the
 * old fire-and-forget approach didn't actually guarantee. It runs
 * concurrently with the logo/welcome timing, not after it — proceeding
 * waits on whichever finishes last (see maybeProceed()).
 */
class SplashActivity : AppCompatActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private var workDone = false
    private var minDisplayDone = false
    private var navigated = false

    private lateinit var logoLayer: View
    private lateinit var welcomeLayer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        // Real startup work — runs in parallel with the fixed logo/
        // welcome timing below, not after it.
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

        // Fixed splash timing: logo for 3s, cross-fade, welcome for
        // another 3s minimum.
        lifecycleScope.launch {
            delay(LOGO_DURATION_MS)
            crossFadeToWelcome()
            delay(WELCOME_MIN_DURATION_MS)
            minDisplayDone = true
            maybeProceed()
        }
    }

    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(AppColors.bg)
        }
        welcomeLayer = buildWelcomeLayer().apply {
            visibility = View.INVISIBLE // laid out immediately, revealed at cross-fade
        }
        logoLayer = buildLogoLayer()
        root.addView(welcomeLayer)
        root.addView(logoLayer)
        return root
    }

    private fun buildLogoLayer(): View {
        val layer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.kig_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                val pad = dp(48)
                setMargins(pad, pad, pad, pad)
            }
        }
        layer.addView(logo)
        return layer
    }

    private fun buildWelcomeLayer(): LinearLayout {
        val col = LinearLayout(this).apply {
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
            text = "ओ३म्"
            setTextColor(AppColors.gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            gravity = Gravity.CENTER
            setShadowLayer(28f, 0f, 0f, AppColors.goldBright)
            // Devanagari font, matching how ReaderActivity/GitaActivity/
            // etc. resolve it from Settings elsewhere in the app (see
            // FontManager). Splash renders before Settings would
            // normally be read anywhere else, so this uses FontManager's
            // built-in default typeface directly rather than blocking
            // startup on a DataStore read here.
            typeface = FontManager.devanagariEntry("noto_serif_devanagari").typeface(this@SplashActivity)
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

    private fun crossFadeToWelcome() {
        welcomeLayer.alpha = 0f
        welcomeLayer.visibility = View.VISIBLE
        welcomeLayer.animate().alpha(1f).setDuration(CROSSFADE_MS).start()
        logoLayer.animate()
            .alpha(0f)
            .setDuration(CROSSFADE_MS)
            .withEndAction { logoLayer.visibility = View.GONE }
            .start()
    }

    private fun maybeProceed() {
        if (workDone && minDisplayDone && !navigated) {
            navigated = true
            startActivity(Intent(this, ShellActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val LOGO_DURATION_MS = 3000L
        private const val WELCOME_MIN_DURATION_MS = 3000L
        private const val CROSSFADE_MS = 600L
    }
}
