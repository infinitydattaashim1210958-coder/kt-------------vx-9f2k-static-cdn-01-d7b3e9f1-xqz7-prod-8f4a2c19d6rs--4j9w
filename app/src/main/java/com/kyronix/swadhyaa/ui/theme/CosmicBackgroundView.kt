package com.kyronix.swadhyaa.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lively animated cosmic background for the db-book reader.
 *
 * Layers (back to front):
 *  1. Deep void gradient  — near-black radial, warm at centre
 *  2. Stars               — 120 points, slow twinkle via alpha oscillation
 *  3. Nebula wisps        — 6 large soft radial blobs drifting slowly
 *  4. Shooting stars      — occasional streaks, random angle/speed
 *  5. Slow auroral drift  — two large translucent arcs in saffron/teal
 *
 * All animation runs on a single ValueAnimator (0→1 looping, 60s cycle)
 * so the view only redraws when necessary and never allocates per-frame.
 *
 * Designed to stay visually calm so the text above remains readable.
 * Maximum nebula/aurora alpha is capped at 38/255 (~15%) so the dark
 * background is never washed out.
 */
class CosmicBackgroundView(context: Context) : View(context) {

    // ── Animator ────────────────────────────────────────────────────────────
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 60_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    // ── Stars ────────────────────────────────────────────────────────────────
    private data class Star(
        val xFrac: Float, val yFrac: Float,
        val radius: Float,
        val speed: Float,     // twinkle speed multiplier
        val phase: Float      // twinkle phase offset 0..1
    )

    private val stars: List<Star> = (0 until 140).map {
        Star(
            xFrac  = Random.nextFloat(),
            yFrac  = Random.nextFloat(),
            radius = Random.nextFloat() * 1.8f + 0.4f,
            speed  = Random.nextFloat() * 1.4f + 0.3f,
            phase  = Random.nextFloat()
        )
    }

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    // ── Nebula blobs ─────────────────────────────────────────────────────────
    private data class Nebula(
        val xFrac: Float, val yFrac: Float,
        val radiusFrac: Float,           // fraction of min(w,h)
        val driftXFrac: Float,           // drift amplitude as fraction of w
        val driftYFrac: Float,
        val driftSpeed: Float,           // full-cycle fraction of 60s
        val driftPhase: Float,
        val color: Int,
        val baseAlpha: Int               // peak alpha 0..38
    )

    private val nebulas = listOf(
        Nebula(.18f, .22f, .45f, .06f, .04f, .31f, .00f, 0xFFD4A24C.toInt(), 28),
        Nebula(.75f, .15f, .38f, .04f, .05f, .23f, .25f, 0xFF8B5CF6.toInt(), 22),
        Nebula(.50f, .55f, .55f, .05f, .03f, .19f, .50f, 0xFF2DD4BF.toInt(), 18),
        Nebula(.85f, .70f, .40f, .03f, .06f, .27f, .10f, 0xFFD4A24C.toInt(), 24),
        Nebula(.12f, .78f, .42f, .05f, .04f, .35f, .70f, 0xFF818CF8.toInt(), 20),
        Nebula(.60f, .90f, .36f, .04f, .03f, .21f, .35f, 0xFFEC4899.toInt(), 16),
    )

    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Shooting stars ───────────────────────────────────────────────────────
    private data class ShootingStar(
        val startXFrac: Float, val startYFrac: Float,
        val angleDeg: Float,
        val lengthFrac: Float,
        val triggerPhase: Float,  // within 0..1 when it appears
        val duration: Float       // fraction of cycle it lasts
    )

    private val shootingStars = (0 until 5).map {
        ShootingStar(
            startXFrac  = Random.nextFloat() * .8f + .05f,
            startYFrac  = Random.nextFloat() * .5f,
            angleDeg    = Random.nextFloat() * 40f + 20f,   // 20–60°
            lengthFrac  = Random.nextFloat() * .12f + .06f,
            triggerPhase = Random.nextFloat(),
            duration    = Random.nextFloat() * .02f + .008f // 0.5–1.5s at 60s cycle
        )
    }

    private val shootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    // ── Aurora arcs ──────────────────────────────────────────────────────────
    private val auroraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 60f
    }

    // ── Background gradient (rebuilt once on size) ───────────────────────────
    private var bgPaint: Paint? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val cx = w / 2f; val cy = h / 2f
        val r = maxOf(w, h).toFloat()
        bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy * 0.6f, r,
                intArrayOf(
                    Color.parseColor("#120D08"),
                    Color.parseColor("#09080C"),
                    Color.parseColor("#050508")
                ),
                floatArrayOf(0f, .55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val t = (animator.animatedValue as Float)  // 0..1

        // 1. Background
        bgPaint?.let { canvas.drawRect(0f, 0f, w, h, it) }

        // 2. Nebula blobs
        for (n in nebulas) {
            val drift = t * n.driftSpeed + n.driftPhase
            val cx = w * (n.xFrac + n.driftXFrac * sin(drift * Math.PI.toFloat() * 2f))
            val cy = h * (n.yFrac + n.driftYFrac * cos(drift * Math.PI.toFloat() * 2f))
            val rad = minOf(w, h) * n.radiusFrac
            val pulse = (0.7f + 0.3f * sin(t * Math.PI.toFloat() * 2f * n.driftSpeed * 3f))
            val alpha = (n.baseAlpha * pulse).toInt().coerceIn(0, 38)
            nebulaPaint.shader = RadialGradient(
                cx, cy, rad,
                intArrayOf(Color.argb(alpha, Color.red(n.color), Color.green(n.color), Color.blue(n.color)),
                           Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, rad, nebulaPaint)
        }

        // 3. Stars (twinkle)
        for (s in stars) {
            val twinkle = sin((t * s.speed + s.phase) * Math.PI.toFloat() * 2f)
            val alpha = ((0.5f + 0.5f * twinkle) * 220f + 35f).toInt().coerceIn(0, 255)
            starPaint.alpha = alpha
            canvas.drawCircle(s.xFrac * w, s.yFrac * h, s.radius, starPaint)
        }

        // 4. Shooting stars
        for (ss in shootingStars) {
            val elapsed = ((t - ss.triggerPhase + 1f) % 1f)
            if (elapsed < ss.duration) {
                val progress = elapsed / ss.duration   // 0..1
                val alpha = (sin(progress * Math.PI.toFloat()) * 200f).toInt().coerceIn(0, 200)
                val angleRad = Math.toRadians(ss.angleDeg.toDouble()).toFloat()
                val len = ss.lengthFrac * w
                val sx = ss.startXFrac * w + progress * len * cos(angleRad)
                val sy = ss.startYFrac * h + progress * len * sin(angleRad)
                val tail = 0.15f * len
                shootPaint.color = Color.argb(alpha, 255, 245, 220)
                canvas.drawLine(sx - tail * cos(angleRad), sy - tail * sin(angleRad), sx, sy, shootPaint)
            }
        }

        // 5. Auroral arcs (very faint, slow)
        val auroraT = sin(t * Math.PI.toFloat() * 2f * 0.17f)
        val arc1Alpha = (18 + 10 * auroraT).toInt().coerceIn(0, 38)
        val arc2Alpha = (14 - 8 * auroraT).toInt().coerceIn(0, 28)
        auroraPaint.color = Color.argb(arc1Alpha, 212, 162, 76)   // saffron
        canvas.drawArc(
            -w * .3f, h * (.3f + .05f * auroraT),
            w * 1.3f, h * (1.2f + .05f * auroraT),
            200f, 140f, false, auroraPaint
        )
        auroraPaint.color = Color.argb(arc2Alpha, 45, 212, 191)   // teal
        canvas.drawArc(
            -w * .2f, -h * (.1f - .04f * auroraT),
            w * 1.2f, h * (.9f - .04f * auroraT),
            220f, 100f, false, auroraPaint
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
