package com.kyronix.swadhyaa.presentation.splash

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
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
import com.kyronix.swadhyaa.ui.theme.FontManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * App entry point (LAUNCHER activity).
 *
 * ONE screen, no logo-first phase any more:
 *   - exact centre of the screen: glowing gold "ओ३म्" with
 *     "নমস্কার, ডাটাবেস লোড হচ্ছে…" underneath (soft radial halo, gold
 *     gradient glyphs, slowly pulsing glow, faint floating dots);
 *   - bottom of the screen: the KIG (Kyronix Innovation Group) logo at
 *     medium size.
 *
 * It proceeds to ShellActivity once MIN_DISPLAY_MS has passed AND the real
 * startup work (DatabaseVerifier + LegacyMigrationEngine) has finished —
 * whichever is later.
 *
 * Runs under Theme.Swadhyay.Splash (black, no action bar), so the system
 * "স্বাধ্যায়" title bar no longer shows here.
 */
class SplashActivity : AppCompatActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private var workDone = false
    private var minDisplayDone = false
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        // Real startup work, in parallel with the minimum display time.
        lifecycleScope.launch {
            val report = DatabaseVerifier.verify(this@SplashActivity)
            if (report.ok) {
                LegacyMigrationEngine.runIfNeeded(this@SplashActivity)
            }
            // A failed integrity report is intentionally not fatal.
            workDone = true
            maybeProceed()
        }

        lifecycleScope.launch {
            delay(MIN_DISPLAY_MS)
            minDisplayDone = true
            maybeProceed()
        }
    }

    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // Floating dots (behind everything).
        val dotLayer = FrameLayout(this)
        root.addView(dotLayer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        dotLayer.post { scatterDots(dotLayer) }

        // ── Centre block: halo + ओ३म् + subtitle ──────────────────────────
        val halo = View(this).apply {
            background = GradientDrawable().apply {
                setShape(GradientDrawable.RECTANGLE)
                setGradientType(GradientDrawable.RADIAL_GRADIENT)
                setGradientRadius(dp(190).toFloat())
                setColors(intArrayOf(Color.argb(70, 232, 168, 64), Color.argb(0, 232, 168, 64)))
            }
        }
        root.addView(halo, FrameLayout.LayoutParams(dp(380), dp(380), Gravity.CENTER))
        ObjectAnimator.ofFloat(halo, View.ALPHA, 0.55f, 1f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }

        val centre = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val om = TextView(this).apply {
            text = "ओ३म्"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 76f)
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = FontManager.devanagariTypeface(this@SplashActivity, FontManager.DEVANAGARI_ID)
            // setShadowLayer() can make the whole glyph vanish on
            // hardware-accelerated views on some GPUs — software layer
            // keeps the glow reliable (see earlier black-screen bugfix).
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            setShadowLayer(dp(14).toFloat(), 0f, 0f, Color.argb(200, 240, 194, 106))
            setTextColor(Color.parseColor("#E8B24E"))
            // Gold gradient glyphs (light gold on top → deeper gold below).
            post {
                if (height > 0) {
                    paint.shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        Color.parseColor("#F4CB72"), Color.parseColor("#BF8A32"),
                        Shader.TileMode.CLAMP
                    )
                    invalidate()
                }
            }
        }
        centre.addView(om)

        val subtitle = TextView(this).apply {
            text = "নমস্কার, ডাটাবেস লোড হচ্ছে…"
            setTextColor(Color.parseColor("#B9B5AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            typeface = FontManager.banglaTypeface(this@SplashActivity, FontManager.BANGLA_ID)
            setPadding(0, dp(18), 0, 0)
        }
        centre.addView(subtitle)

        root.addView(centre, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        // ── Bottom: KIG logo, medium size ────────────────────────────────
        val logoWidth = (resources.displayMetrics.widthPixels * 0.55f).toInt()
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.kig_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        root.addView(logo, FrameLayout.LayoutParams(
            logoWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(36) })

        // Gentle fade-in of the whole composition.
        centre.alpha = 0f; logo.alpha = 0f
        centre.animate().alpha(1f).setDuration(700).start()
        logo.animate().alpha(1f).setStartDelay(250).setDuration(700).start()

        return root
    }

    private fun scatterDots(layer: FrameLayout) {
        val w = layer.width.takeIf { it > 0 } ?: return
        val h = layer.height.takeIf { it > 0 } ?: return
        repeat(7) {
            val size = Random.nextInt(dp(3), dp(5) + 1)
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    setShape(GradientDrawable.OVAL)
                    setColor(Color.argb(110, 240, 194, 106))
                }
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    leftMargin = Random.nextInt(0, (w - size).coerceAtLeast(1))
                    topMargin = Random.nextInt(h / 2, (h - size).coerceAtLeast(h / 2 + 1))
                }
            }
            layer.addView(dot)
            dot.animate()
                .translationY(-dp(26).toFloat())
                .alpha(0.2f)
                .setStartDelay(Random.nextLong(0, 1500))
                .setDuration(2400)
                .withEndAction {
                    dot.animate().translationY(0f).alpha(1f).setDuration(2400).start()
                }
                .start()
        }
    }

    private fun maybeProceed() {
        if (workDone && minDisplayDone && !navigated) {
            navigated = true
            startActivity(Intent(this, ShellActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val MIN_DISPLAY_MS = 2500L
    }
}
