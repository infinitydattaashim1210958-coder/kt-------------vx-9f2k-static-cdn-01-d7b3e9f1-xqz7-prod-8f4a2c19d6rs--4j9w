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
 * Lively animated cosmic background — purple + pink + deep violet palette.
 *
 * Layers (back → front):
 *  1. Deep void radial gradient — near-black with warm violet centre
 *  2. 8 nebula blobs — slow drift, pulse; purples, pinks, magentas
 *  3. 150 stars — individual twinkle phase + speed
 *  4. 5 shooting stars — random angle/timing, sin fade
 *  5. 2 aurora arcs — magenta + violet, faint slow oscillation
 *
 * All animation on one 60s ValueAnimator → single invalidate() per frame.
 * Aurora/nebula max alpha capped at 40/255 so text stays readable.
 */
class CosmicBackgroundView(context: Context) : View(context) {

    // ── Animator ─────────────────────────────────────────────────────────────
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration       = 60_000L
        repeatCount    = ValueAnimator.INFINITE
        interpolator   = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    // ── Stars ─────────────────────────────────────────────────────────────────
    private data class Star(
        val xFrac: Float, val yFrac: Float,
        val radius: Float,
        val speed: Float,
        val phase: Float,
        val colorR: Int, val colorG: Int, val colorB: Int  // slight tint variety
    )

    private val stars: List<Star> = (0 until 150).map {
        // Mix of white, lavender and pale pink stars
        val tint = Random.nextInt(3)
        val (r, g, b) = when (tint) {
            0 -> Triple(255, 255, 255)            // white
            1 -> Triple(220, 200, 255)            // lavender
            else -> Triple(255, 200, 230)         // pale pink
        }
        Star(
            xFrac  = Random.nextFloat(),
            yFrac  = Random.nextFloat(),
            radius = Random.nextFloat() * 1.8f + 0.3f,
            speed  = Random.nextFloat() * 1.5f + 0.3f,
            phase  = Random.nextFloat(),
            colorR = r, colorG = g, colorB = b
        )
    }

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Nebula blobs ──────────────────────────────────────────────────────────
    private data class Nebula(
        val xFrac: Float, val yFrac: Float,
        val radiusFrac: Float,
        val driftXFrac: Float, val driftYFrac: Float,
        val driftSpeed: Float, val driftPhase: Float,
        val color: Int,
        val baseAlpha: Int
    )

    private val nebulas = listOf(
        // Deep violet centre-left
        Nebula(.15f, .20f, .50f, .05f, .04f, .28f, .00f, Color.parseColor("#7B2FBE"), 38),
        // Magenta top-right
        Nebula(.78f, .12f, .40f, .04f, .05f, .21f, .20f, Color.parseColor("#C2185B"), 32),
        // Purple centre
        Nebula(.50f, .50f, .60f, .04f, .03f, .17f, .45f, Color.parseColor("#6A0DAD"), 28),
        // Hot pink right
        Nebula(.88f, .65f, .38f, .03f, .05f, .25f, .10f, Color.parseColor("#E91E8C"), 30),
        // Indigo lower-left
        Nebula(.10f, .75f, .44f, .05f, .04f, .33f, .65f, Color.parseColor("#4527A0"), 25),
        // Rose lower-centre
        Nebula(.55f, .85f, .36f, .04f, .03f, .19f, .30f, Color.parseColor("#AD1457"), 22),
        // Soft violet top-left
        Nebula(.30f, .08f, .32f, .03f, .04f, .23f, .80f, Color.parseColor("#9C27B0"), 20),
        // Deep pink right-mid
        Nebula(.92f, .38f, .34f, .03f, .05f, .29f, .55f, Color.parseColor("#FF4081"), 26),
    )

    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Shooting stars ────────────────────────────────────────────────────────
    private data class ShootingStar(
        val startXFrac: Float, val startYFrac: Float,
        val angleDeg: Float,
        val lengthFrac: Float,
        val triggerPhase: Float,
        val duration: Float
    )

    private val shootingStars = (0 until 5).map {
        ShootingStar(
            startXFrac   = Random.nextFloat() * .8f + .05f,
            startYFrac   = Random.nextFloat() * .5f,
            angleDeg     = Random.nextFloat() * 40f + 20f,
            lengthFrac   = Random.nextFloat() * .12f + .06f,
            triggerPhase = Random.nextFloat(),
            duration     = Random.nextFloat() * .02f + .008f
        )
    }

    private val shootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.6f
        style       = Paint.Style.STROKE
    }

    // ── Aurora arcs ───────────────────────────────────────────────────────────
    private val auroraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 55f
    }

    // ── Background gradient (rebuilt on size change) ──────────────────────────
    private var bgPaint: Paint? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.45f, h * 0.35f, maxOf(w, h).toFloat(),
                intArrayOf(
                    Color.parseColor("#120818"),   // deep purple void centre
                    Color.parseColor("#0A060F"),   // near-black mid
                    Color.parseColor("#050307")    // pure void edge
                ),
                floatArrayOf(0f, .50f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val t = animator.animatedValue as Float  // 0..1

        // 1. Background
        bgPaint?.let { canvas.drawRect(0f, 0f, w, h, it) }

        val TWO_PI = (Math.PI * 2).toFloat()

        // 2. Nebula blobs
        for (n in nebulas) {
            val drift = t * n.driftSpeed + n.driftPhase
            val cx = w * (n.xFrac + n.driftXFrac * sin(drift * TWO_PI))
            val cy = h * (n.yFrac + n.driftYFrac * cos(drift * TWO_PI))
            val rad = minOf(w, h) * n.radiusFrac
            val pulse = 0.65f + 0.35f * sin(t * TWO_PI * n.driftSpeed * 3.1f)
            val alpha = (n.baseAlpha * pulse).toInt().coerceIn(0, 40)
            val nr = Color.red(n.color)
            val ng = Color.green(n.color)
            val nb = Color.blue(n.color)
            nebulaPaint.shader = RadialGradient(
                cx, cy, rad,
                intArrayOf(Color.argb(alpha, nr, ng, nb), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, rad, nebulaPaint)
        }

        // 3. Stars (twinkle)
        for (s in stars) {
            val twinkle = sin((t * s.speed + s.phase) * TWO_PI)
            val alpha = ((0.45f + 0.55f * twinkle) * 230f + 25f).toInt().coerceIn(0, 255)
            starPaint.color = Color.argb(alpha, s.colorR, s.colorG, s.colorB)
            canvas.drawCircle(s.xFrac * w, s.yFrac * h, s.radius, starPaint)
        }

        // 4. Shooting stars
        for (ss in shootingStars) {
            val elapsed = ((t - ss.triggerPhase + 1f) % 1f)
            if (elapsed < ss.duration) {
                val progress  = elapsed / ss.duration
                val alpha     = (sin(progress * Math.PI.toFloat()) * 210f).toInt().coerceIn(0, 210)
                val angleRad  = Math.toRadians(ss.angleDeg.toDouble()).toFloat()
                val len       = ss.lengthFrac * w
                val sx        = ss.startXFrac * w + progress * len * cos(angleRad)
                val sy        = ss.startYFrac * h + progress * len * sin(angleRad)
                val tail      = 0.18f * len
                shootPaint.color = Color.argb(alpha, 255, 220, 255)  // pink-white streak
                canvas.drawLine(
                    sx - tail * cos(angleRad), sy - tail * sin(angleRad),
                    sx, sy, shootPaint
                )
            }
        }

        // 5. Aurora arcs — magenta + deep violet
        val aT = sin(t * TWO_PI * 0.14f)
        val arc1Alpha = (20 + 12 * aT).toInt().coerceIn(0, 40)
        val arc2Alpha = (14 -  8 * aT).toInt().coerceIn(0, 30)

        // Magenta arc
        auroraPaint.color = Color.argb(arc1Alpha, 200, 0, 150)
        canvas.drawArc(
            -w * .25f, h * (.28f + .06f * aT),
            w * 1.25f, h * (1.15f + .06f * aT),
            195f, 150f, false, auroraPaint
        )
        // Deep violet arc
        auroraPaint.color = Color.argb(arc2Alpha, 90, 0, 180)
        canvas.drawArc(
            -w * .15f, -h * (.08f - .05f * aT),
            w * 1.15f,  h * (.92f - .05f * aT),
            215f, 110f, false, auroraPaint
        )
    }

    override fun onAttachedToWindow()  { super.onAttachedToWindow();  animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
}
