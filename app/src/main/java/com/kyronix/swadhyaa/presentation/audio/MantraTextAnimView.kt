package com.kyronix.swadhyaa.presentation.audio

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.view.setPadding

/**
 * Displays Sanskrit mantra text with cinematic enter / exit animations:
 *
 *   Enter → grows from 0.4× scale + alpha 0 to full scale + alpha 1  (400 ms, decelerate)
 *   Exit  → shrinks to 0.4× + alpha 0 with blur feel                  (300 ms, accelerate)
 *
 * The "transparent square block with glowing text" look:
 *  - Near-black background (#CC000000)
 *  - Rounded corners (via background drawable or clip)
 *  - Golden glowing text using [Paint.setShadowLayer] on a custom TextView
 *
 * Usage in the Reader screen (e.g. VedaReaderActivity):
 * ```kotlin
 * val animView = MantraTextAnimView(context)
 * readerLayout.addView(animView, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
 *
 * // Observe AudioPlayerViewModel state:
 * lifecycleScope.launch {
 *     audioVm.state.collect { state ->
 *         when (state) {
 *             is MantraPlaybackState.Playing -> {
 *                 if (state.positionMs < 300) {  // just started
 *                     animView.showMantra(mantra.devanagariText, state.ref.displayLabel)
 *                 }
 *             }
 *             else -> animView.hideMantra()
 *         }
 *     }
 * }
 * ```
 *
 * In Listening Mode, the Reader should call [showMantra] whenever the
 * MantraPlaybackState.Playing ref changes to a new mantra, and [hideMantra]
 * right before the transition triggers so the exit animation plays cleanly.
 */
class MantraTextAnimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // ── Colours ───────────────────────────────────────────────────────────────
    private val cBg      = Color.parseColor("#CC0A0806")  // near-black, semi-transparent
    private val cGold    = Color.parseColor("#C4A574")
    private val cMuted   = Color.parseColor("#A89070")

    // ── Text views ────────────────────────────────────────────────────────────
    private val tvMantra: GlowTextView
    private val tvLabel:  TextView

    init {
        setBackgroundColor(cBg)
        val dm = context.resources.displayMetrics
        val p  = (20 * dm.density).toInt()
        setPadding(p)

        // Mantra text (Sanskrit / Devanagari)
        tvMantra = GlowTextView(context).apply {
            textSize  = 18f
            setTextColor(cGold)
            glowColor = Color.parseColor("#FFD700")
            glowRadius= 14f
            gravity   = Gravity.CENTER
        }
        addView(tvMantra, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER
        })

        // Label beneath mantra (e.g. "ঋগ্বেদ ১।২।২")
        tvLabel = TextView(context).apply {
            setTextColor(cMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, (10 * dm.density).toInt(), 0, 0)
        }
        addView(tvLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER or Gravity.BOTTOM
        })

        // Start invisible
        alpha     = 0f
        scaleX    = SCALE_OUT
        scaleY    = SCALE_OUT
        visibility = INVISIBLE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Display [devanagariText] with an entrance animation.
     * If already visible, it first plays the exit animation then re-enters.
     */
    fun showMantra(devanagariText: String, label: String = "") {
        if (visibility == VISIBLE && alpha > 0.5f) {
            animateOut {
                tvMantra.mantraText = devanagariText
                tvLabel.text        = label
                animateIn()
            }
        } else {
            tvMantra.mantraText = devanagariText
            tvLabel.text        = label
            animateIn()
        }
    }

    /** Hide with exit animation. Optional [onEnd] runs after the animation. */
    fun hideMantra(onEnd: (() -> Unit)? = null) {
        if (visibility != VISIBLE || alpha < 0.1f) { onEnd?.invoke(); return }
        animateOut { visibility = INVISIBLE; onEnd?.invoke() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Animations
    // ─────────────────────────────────────────────────────────────────────────

    private fun animateIn() {
        visibility = VISIBLE
        AnimatorSet().apply {
            playTogether(
                scaleAnim("scaleX", SCALE_OUT, 1f),
                scaleAnim("scaleY", SCALE_OUT, 1f),
                alphaAnim(0f, 1f)
            )
            duration    = DURATION_IN
            interpolator = DecelerateInterpolator(2f)
            start()
        }
    }

    private fun animateOut(onEnd: () -> Unit) {
        AnimatorSet().apply {
            playTogether(
                scaleAnim("scaleX", 1f, SCALE_OUT),
                scaleAnim("scaleY", 1f, SCALE_OUT),
                alphaAnim(1f, 0f)
            )
            duration     = DURATION_OUT
            interpolator = AccelerateInterpolator(2f)
            doOnEnd { onEnd() }
            start()
        }
    }

    private fun scaleAnim(prop: String, from: Float, to: Float) =
        ObjectAnimator.ofFloat(this, prop, from, to)

    private fun alphaAnim(from: Float, to: Float) =
        ObjectAnimator.ofFloat(this, "alpha", from, to)

    companion object {
        private const val SCALE_OUT    = 0.4f
        private const val DURATION_IN  = 420L
        private const val DURATION_OUT = 300L
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GlowTextView — inner custom TextView with text-shadow glow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A TextView that draws Devanagari text with a golden glow using
     * [android.graphics.Paint.setShadowLayer]. Hardware acceleration must
     * be enabled (default on API 14+).
     */
    class GlowTextView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
    ) : androidx.appcompat.widget.AppCompatTextView(context, attrs) {

        var glowColor:  Int   = Color.parseColor("#FFD700"); set(v) { field = v; applyGlow() }
        var glowRadius: Float = 12f;                         set(v) { field = v; applyGlow() }

        var mantraText: String = ""
            set(v) { field = v; text = v }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)  // needed for setShadowLayer
            applyGlow()
        }

        private fun applyGlow() {
            setShadowLayer(glowRadius, 0f, 0f, glowColor)
            invalidate()
        }
    }
}
