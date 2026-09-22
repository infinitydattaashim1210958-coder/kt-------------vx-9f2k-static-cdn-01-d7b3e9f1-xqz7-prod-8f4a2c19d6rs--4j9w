package com.kyronix.swadhyaa.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Generates the glowing-mantra artwork bitmap used as:
 *  • Lock-screen / notification album art
 *  • The "transparent square block" described in the design brief
 *
 * Design:
 *   - Near-black semi-transparent background with rounded corners
 *   - Faint golden ॐ watermark centred in the background
 *   - Sanskrit mantra text in golden colour with a multi-layered glow
 *
 * All drawing happens on the calling thread; call from a background thread
 * or from the service scope if performance matters.
 */
object MantraArtGenerator {

    // ── Palette ───────────────────────────────────────────────────────────────
    private val COLOR_BG        = Color.argb(220, 10, 8, 6)          // #CC0A0806
    private val COLOR_GOLD      = Color.parseColor("#C4A574")
    private val COLOR_GOLD_DIM  = Color.argb(80, 196, 165, 116)
    private val COLOR_GLOW_1    = Color.argb(160, 255, 200, 100)     // warm glow
    private val COLOR_GLOW_2    = Color.argb(60,  255, 200, 100)     // outer glow
    private val COLOR_OM        = Color.argb(18, 196, 165, 116)      // watermark

    // ── Sizes ─────────────────────────────────────────────────────────────────
    private const val SIZE    = 512
    private const val RADIUS  = 48f   // rounded corner radius

    /**
     * @param devanagariText Sanskrit mantra text (may be empty → shows only ॐ)
     * @param label          Short label e.g. "ঋগ্বেদ ১।২।২" shown below the mantra
     * @param size           Output bitmap size in pixels (square)
     */
    fun generate(
        devanagariText: String,
        label: String = "",
        size: Int = SIZE
    ): Bitmap {
        val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s      = size.toFloat()

        // ── Rounded-rect background ───────────────────────────────────────────
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_BG }
        canvas.drawRoundRect(RectF(0f, 0f, s, s), RADIUS, RADIUS, bgPaint)

        // ── Subtle top-to-bottom gradient overlay ─────────────────────────────
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, s,
                intArrayOf(Color.argb(40, 196, 165, 116), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, s, s * 0.5f), RADIUS, RADIUS, gradPaint)

        // ── ॐ watermark ───────────────────────────────────────────────────────
        val omPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize  = s * 0.55f
            color     = COLOR_OM
        }
        canvas.drawText("ॐ", s / 2f, s * 0.72f, omPaint)

        // ── Main mantra text with glow layers ─────────────────────────────────
        val text = devanagariText.ifEmpty { "ॐ" }
        drawGlowText(canvas, text, s)

        // ── Label below mantra ────────────────────────────────────────────────
        if (label.isNotEmpty()) {
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize  = s * 0.045f
                color     = COLOR_GOLD_DIM
            }
            canvas.drawText(label, s / 2f, s * 0.90f, labelPaint)
        }

        return bmp
    }

    // ── Glow text helper ──────────────────────────────────────────────────────

    private fun drawGlowText(canvas: Canvas, text: String, s: Float) {
        val baseFontSize = s * 0.075f
        val maxWidth     = s * 0.84f

        // Three passes: outer glow → inner glow → solid text
        val passes = listOf(
            GlowPass(COLOR_GLOW_2, s * 0.09f, baseFontSize * 1.02f),
            GlowPass(COLOR_GLOW_1, s * 0.04f, baseFontSize),
            GlowPass(COLOR_GOLD,   0f,          baseFontSize)
        )

        for (pass in passes) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize  = pass.fontSize
                color     = pass.color
                if (pass.glowRadius > 0f)
                    setShadowLayer(pass.glowRadius, 0f, 0f, pass.color)
            }
            drawWrapped(canvas, text, s, paint, maxWidth)
        }
    }

    private data class GlowPass(val color: Int, val glowRadius: Float, val fontSize: Float)

    private fun drawWrapped(
        canvas: Canvas,
        text:   String,
        s:      Float,
        paint:  Paint,
        maxW:   Float
    ) {
        val lines      = softWrap(text, paint, maxW)
        val lineH      = paint.textSize * 1.45f
        val totalH     = lines.size * lineH
        // Center vertically with a slight upward offset for the label
        var y = (s - totalH) / 2f + paint.textSize - s * 0.03f

        for (line in lines) {
            canvas.drawText(line, s / 2f, y, paint)
            y += lineH
        }
    }

    /** Greedy word-wrap using half-width spaces as break points. */
    private fun softWrap(text: String, paint: Paint, maxW: Float): List<String> {
        // Try character-based splitting for Devanagari (no spaces)
        if (!text.contains(' ') && paint.measureText(text) > maxW) {
            return splitByWidth(text, paint, maxW)
        }
        val words   = text.split(" ")
        val lines   = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxW) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun splitByWidth(text: String, paint: Paint, maxW: Float): List<String> {
        val lines   = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in text) {
            val candidate = current.toString() + ch
            if (paint.measureText(candidate) <= maxW) current.append(ch)
            else { if (current.isNotEmpty()) lines += current.toString(); current = StringBuilder(ch.toString()) }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    // ── Notification action icons (drawn with Canvas) ─────────────────────────

    /** ▶ Play icon bitmap */
    fun playIcon(size: Int = 96, color: Int = COLOR_GOLD): Bitmap =
        iconBitmap(size) { canvas, s, paint ->
            paint.color = color
            val path = Path().apply {
                moveTo(s * 0.25f, s * 0.15f)
                lineTo(s * 0.82f, s * 0.50f)
                lineTo(s * 0.25f, s * 0.85f)
                close()
            }
            canvas.drawPath(path, paint)
        }

    /** ⏸ Pause icon bitmap */
    fun pauseIcon(size: Int = 96, color: Int = COLOR_GOLD): Bitmap =
        iconBitmap(size) { canvas, s, paint ->
            paint.color = color
            canvas.drawRoundRect(RectF(s*0.20f, s*0.15f, s*0.42f, s*0.85f), 4f, 4f, paint)
            canvas.drawRoundRect(RectF(s*0.58f, s*0.15f, s*0.80f, s*0.85f), 4f, 4f, paint)
        }

    /** ⏭ Next icon bitmap */
    fun nextIcon(size: Int = 96, color: Int = COLOR_GOLD): Bitmap =
        iconBitmap(size) { canvas, s, paint ->
            paint.color = color
            val path = Path().apply {
                moveTo(s*0.15f, s*0.18f); lineTo(s*0.60f, s*0.50f); lineTo(s*0.15f, s*0.82f); close()
            }
            canvas.drawPath(path, paint)
            canvas.drawRoundRect(RectF(s*0.65f, s*0.18f, s*0.82f, s*0.82f), 4f, 4f, paint)
        }

    /** ⏮ Prev icon bitmap */
    fun prevIcon(size: Int = 96, color: Int = COLOR_GOLD): Bitmap =
        iconBitmap(size) { canvas, s, paint ->
            paint.color = color
            val path = Path().apply {
                moveTo(s*0.85f, s*0.18f); lineTo(s*0.40f, s*0.50f); lineTo(s*0.85f, s*0.82f); close()
            }
            canvas.drawPath(path, paint)
            canvas.drawRoundRect(RectF(s*0.18f, s*0.18f, s*0.35f, s*0.82f), 4f, 4f, paint)
        }

    /** ✕ Close icon bitmap */
    fun closeIcon(size: Int = 96, color: Int = Color.parseColor("#A89070")): Bitmap =
        iconBitmap(size) { canvas, s, paint ->
            paint.color  = color
            paint.strokeWidth = s * 0.12f
            paint.style  = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(s*0.2f, s*0.2f, s*0.8f, s*0.8f, paint)
            canvas.drawLine(s*0.8f, s*0.2f, s*0.2f, s*0.8f, paint)
        }

    private fun iconBitmap(
        size:  Int,
        draw: (Canvas, Float, Paint) -> Unit
    ): Bitmap {
        val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        draw(canvas, size.toFloat(), paint)
        return bmp
    }
}
