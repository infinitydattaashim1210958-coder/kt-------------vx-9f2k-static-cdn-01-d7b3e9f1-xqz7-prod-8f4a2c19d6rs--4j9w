package com.kyronix.swadhyaa.presentation.audio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated 3D-style ওঁ (Om / Aum) symbol view for the Swadhyay Listening Mode.
 *
 * Visual layers (back → front):
 *  1. Deep cosmic radial gradient background
 *  2. Rotating halo ring — saffron/gold sweep gradient
 *  3. Pulsing glow orb behind the symbol
 *  4. Om text (ওঁ in Devanagari) — rendered with 3D-depth illusion via
 *     layered shadow/highlight draws + rotate transform
 *  5. Particle sparks orbiting at varying distances
 *
 * Two ValueAnimators:
 *   • rotAnim  (20s loop) — slow rotation of halo and 3D tilt
 *   • pulseAnim (2.5s loop) — breathing glow scale + alpha
 *
 * Drop this into any FrameLayout or use as a background layer in
 * ListeningModeActivity.
 */
class OmSymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Palette ─────────────────────────────────────────────────────────────
    private val cVoid    = Color.parseColor("#06030F")
    private val cGold    = Color.parseColor("#D4A24C")
    private val cSaffron = Color.parseColor("#FF9A3C")
    private val cAmber   = Color.parseColor("#F0C26A")
    private val cCrimson = Color.parseColor("#C44B2A")
    private val cPink    = Color.parseColor("#FF3C8C")
    private val cWhite   = Color.parseColor("#FFF5E0")

    // ── Animators ────────────────────────────────────────────────────────────
    private val rotAnim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration     = 20_000L
        repeatCount  = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }
    private val pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration    = 2_500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { /* rotAnim drives invalidate() */ }
    }

    // ── Paints ───────────────────────────────────────────────────────────────
    private val bgPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = android.graphics.Typeface.DEFAULT_BOLD
        setLayerType(LAYER_TYPE_SOFTWARE, this)
    }
    private val sparkPaint  = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Particles ────────────────────────────────────────────────────────────
    private data class Spark(
        val orbitRadius: Float,  // fraction of min(w,h)/2
        val speed: Float,        // radians per animation tick
        val phase: Float,        // initial angle offset
        val size: Float,
        val alpha: Int
    )
    private val sparks: List<Spark> = (0 until 24).map { i ->
        Spark(
            orbitRadius = 0.42f + (i % 5) * 0.055f,
            speed       = 0.28f + (i % 7) * 0.04f,
            phase       = i * (Math.PI * 2 / 24).toFloat(),
            size        = 2.5f + (i % 4) * 1.2f,
            alpha       = 120 + (i % 5) * 24
        )
    }

    // ── Rebuilt on size change ────────────────────────────────────────────────
    private var bgShader: RadialGradient? = null
    private var cx = 0f; private var cy = 0f; private var r = 0f
    private var symbolSizePx = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        cx = w / 2f; cy = h / 2f; r = min(w, h) / 2f
        symbolSizePx = r * 0.72f
        symbolPaint.textSize = symbolSizePx
        symbolPaint.setShadowLayer(symbolSizePx * 0.18f, 0f, 0f, cGold)
        setLayerType(LAYER_TYPE_SOFTWARE, symbolPaint)

        bgShader = RadialGradient(
            cx, cy * 0.85f, r * 1.3f,
            intArrayOf(
                Color.parseColor("#1A0A2E"),
                Color.parseColor("#0D0518"),
                cVoid
            ),
            floatArrayOf(0f, .55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val rot   = rotAnim.animatedValue   as Float   // 0..360
        val pulse = pulseAnim.animatedValue as Float   // 0..1
        val TWO_PI = (Math.PI * 2).toFloat()
        val pulseSin  = (sin(pulse * TWO_PI) * 0.5f + 0.5f)   // 0..1 smooth
        val pulseSin2 = (sin(pulse * TWO_PI * 1.3f) * 0.5f + 0.5f)

        // ── 1. Background ────────────────────────────────────────────────────
        bgShader?.let { bgPaint.shader = it }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // ── 2. Halo ring ─────────────────────────────────────────────────────
        val haloAlpha = (140 + 60 * pulseSin).toInt()
        val matrix = Matrix()
        matrix.postRotate(rot, cx, cy)
        val haloShader = SweepGradient(cx, cy,
            intArrayOf(
                Color.argb(0, 212, 162, 76),
                Color.argb(haloAlpha, 240, 194, 106),
                Color.argb(haloAlpha / 2, 255, 154, 60),
                Color.argb(haloAlpha, 212, 162, 76),
                Color.argb(0, 212, 162, 76)
            ),
            floatArrayOf(0f, .2f, .5f, .8f, 1f)
        )
        haloShader.setLocalMatrix(matrix)
        haloPaint.shader = haloShader
        haloPaint.strokeWidth = 5f + 3f * pulseSin
        canvas.drawCircle(cx, cy, r * 0.78f, haloPaint)

        // Second inner halo — thinner, slightly crimson
        val innerMatrix = Matrix()
        innerMatrix.postRotate(-rot * 0.6f, cx, cy)
        val innerShader = SweepGradient(cx, cy,
            intArrayOf(
                Color.argb(0, 196, 75, 42),
                Color.argb(80, 255, 120, 60),
                Color.argb(40, 196, 75, 42),
                Color.argb(0, 196, 75, 42)
            ),
            floatArrayOf(0f, .3f, .7f, 1f)
        )
        innerShader.setLocalMatrix(innerMatrix)
        haloPaint.shader = innerShader
        haloPaint.strokeWidth = 2.5f
        canvas.drawCircle(cx, cy, r * 0.62f, haloPaint)

        // ── 3. Glow orb ──────────────────────────────────────────────────────
        val glowRadius = r * (0.55f + 0.12f * pulseSin)
        val glowAlpha  = (55 + 40 * pulseSin).toInt()
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(
                Color.argb(glowAlpha, 212, 162, 76),
                Color.argb(glowAlpha / 3, 255, 154, 60),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, .5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // ── 4. Om symbol — 3D depth illusion ─────────────────────────────────
        // Tilt simulation: shift shadow opposite to rotation direction
        val tiltX = cos(Math.toRadians(rot.toDouble())).toFloat() * r * 0.025f
        val tiltY = sin(Math.toRadians(rot.toDouble())).toFloat() * r * 0.018f

        // Deep shadow layer (furthest back)
        symbolPaint.color = Color.argb(100, 80, 30, 0)
        symbolPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        canvas.drawText("ॐ", cx + tiltX * 3, cy - symbolSizePx * 0.12f + tiltY * 3, symbolPaint)

        // Mid shadow — warm crimson
        symbolPaint.color = Color.argb(160, 196, 75, 42)
        canvas.drawText("ॐ", cx + tiltX * 1.5f, cy - symbolSizePx * 0.12f + tiltY * 1.5f, symbolPaint)

        // Main symbol with gold glow
        symbolPaint.color = cAmber
        symbolPaint.setShadowLayer(symbolSizePx * 0.20f, 0f, 0f, Color.argb(200, 255, 200, 80))
        canvas.drawText("ॐ", cx, cy - symbolSizePx * 0.12f, symbolPaint)

        // Highlight shimmer — white on bright side
        val shimmerAlpha = (80 + 80 * pulseSin2).toInt()
        symbolPaint.color = Color.argb(shimmerAlpha, 255, 245, 200)
        symbolPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        canvas.drawText("ॐ", cx - tiltX * 0.6f, cy - symbolSizePx * 0.12f - tiltY * 0.6f, symbolPaint)

        // ── 5. Sparks ────────────────────────────────────────────────────────
        val rotRad = Math.toRadians(rot.toDouble()).toFloat()
        for (s in sparks) {
            val angle = rotRad * s.speed + s.phase
            val sr = r * s.orbitRadius
            val sx = cx + sr * cos(angle)
            val sy = cy + sr * sin(angle)
            val sparkAlpha = ((s.alpha) * (0.6f + 0.4f * pulseSin)).toInt().coerceIn(0, 255)
            // Alternate gold and saffron for visual depth
            val sparkColor = if ((sparks.indexOf(s) % 3) == 0) cSaffron else cGold
            sparkPaint.color = Color.argb(sparkAlpha, Color.red(sparkColor), Color.green(sparkColor), Color.blue(sparkColor))
            sparkPaint.setShadowLayer(s.size * 2.5f, 0f, 0f, sparkColor)
            canvas.drawCircle(sx.toFloat(), sy.toFloat(), s.size, sparkPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rotAnim.start()
        pulseAnim.start()
    }

    override fun onDetachedFromWindow() {
        rotAnim.cancel()
        pulseAnim.cancel()
        super.onDetachedFromWindow()
    }
}
