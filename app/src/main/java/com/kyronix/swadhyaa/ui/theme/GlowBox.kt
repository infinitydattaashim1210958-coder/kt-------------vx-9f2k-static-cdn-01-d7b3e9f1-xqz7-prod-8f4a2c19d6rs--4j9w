package com.kyronix.swadhyaa.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View

/**
 * Neon "glow box" backgrounds — the layered-stroke illusion of a soft outer
 * glow around a rounded rectangle, requested to visually separate content
 * sections throughout the app (mantra cards, bhashya panels, chips, tabs,
 * jump pickers, buttons), matching the reference glowing-border style.
 *
 * WHY LAYERED STROKES INSTEAD OF A REAL BLUR
 * -------------------------------------------
 * Classic Android [View] backgrounds have no built-in Gaussian blur —
 * `RenderEffect.createBlurEffect` only exists from API 31, and this app's
 * `minSdk` is 24 (see app/build.gradle.kts), so it can't be relied on. A true
 * frosted glow would also need a `RenderNode`/hardware layer per box, which
 * is heavy to repeat on nearly every screen. Instead this approximates the
 * glow by stacking three concentric rounded-rect strokes of the SAME color
 * at decreasing alpha and increasing size — a bright, thin core border
 * fading out to a soft, wide, faint halo. This is a standard glow-without-
 * blur technique, reads correctly at normal phone viewing distance, and
 * costs only three shape draws (no bitmaps, no RenderEffect, works down to
 * minSdk).
 *
 * HOW ROOM FOR THE HALO IS FOUND
 * --------------------------------
 * A background [Drawable]'s bounds are exactly its [View]'s bounds — a
 * drawable can never paint outside them. So the halo needs the view to be a
 * few dp bigger than its visible content on every side. [applyTo] provides
 * this by ADDING [haloDp] to whatever padding the caller already set, so
 * call it AFTER `setPadding(...)`/content is otherwise configured, not
 * before — calling it first would have its extra padding overwritten by a
 * later `setPadding` call.
 */
object GlowBox {

    /** One ring in the glow stack. */
    private data class Ring(val insetDp: Float, val strokeWidthDp: Float, val alpha: Int)

    // Outermost (widest, faintest) first; innermost (sharp core border) last,
    // so later/core layers paint on top of the softer halo beneath them.
    private val GLOW_RINGS = listOf(
        Ring(insetDp = 0f, strokeWidthDp = 10f, alpha = 40),
        Ring(insetDp = 3f, strokeWidthDp = 6f, alpha = 90),
        Ring(insetDp = 6f, strokeWidthDp = 2f, alpha = 255)
    )

    /** Extra dp of transparent room the glow needs on every side of a view; see [applyTo]. */
    const val DEFAULT_HALO_DP = 8

    /**
     * A glow-bordered, FILLED panel drawable — for content sections (a
     * mantra box, a bhashya block, a settings card) that need a visible
     * interior, not just an outline.
     *
     * @param color the glow/border color. Pass an [AppColors] token (e.g.
     *   [AppColors.gold]/[AppColors.saffron]) rather than a literal hex so
     *   the glow re-themes automatically when [AppColors.applyAccent] runs.
     * @param fillColor the panel's interior fill, usually [AppColors.surface]
     *   or [AppColors.elevated].
     * @param cornerRadiusDp corner radius of the sharp core border; the
     *   halo rings shrink their own radius to stay roughly parallel to it.
     */
    fun panel(
        context: Context,
        color: Int,
        fillColor: Int,
        cornerRadiusDp: Float = 14f
    ): LayerDrawable {
        val density = context.resources.displayMetrics.density
        val safeRadius = cornerRadiusDp.coerceAtLeast(0f)

        val layers: Array<Drawable> = GLOW_RINGS.mapIndexed { index, ring ->
            val isCore = index == GLOW_RINGS.lastIndex
            val gd = GradientDrawable()
            gd.setShape(GradientDrawable.RECTANGLE)
            gd.setCornerRadius((safeRadius - ring.insetDp).coerceAtLeast(2f) * density)
            gd.setStroke(
                ((ring.strokeWidthDp * density).toInt()).coerceAtLeast(1),
                withAlpha(color, ring.alpha)
            )
            // Only the innermost/core ring carries the panel's actual fill —
            // the halo rings must stay hollow, or they'd paint a solid block
            // over the rings beneath them instead of just an outline.
            gd.setColor(if (isCore) fillColor else Color.TRANSPARENT)
            gd as Drawable
        }.toTypedArray()

        return LayerDrawable(layers).apply {
            GLOW_RINGS.forEachIndexed { i, ring ->
                val insetPx = (ring.insetDp * density).toInt()
                setLayerInset(i, insetPx, insetPx, insetPx, insetPx)
            }
        }
    }

    /**
     * A glow-bordered outline drawable for chips/tabs/buttons (veda chips,
     * language tabs, jump pickers, scholar rows, prev/next buttons) —
     * legacy used a flat solid fill to show a "selected" state; this keeps
     * that same signal by making [filled] chips noticeably brighter/denser
     * than hollow ones, rather than replacing the selection cue outright.
     *
     * @param filled true for an active/selected chip or a CTA button
     *   (near-opaque interior + usually a brighter [color] such as
     *   [AppColors.gold]) — deliberately close to fully solid, NOT a faint
     *   tint, because every call site pairs a filled chip with plain BLACK
     *   text (mirroring the flat solid-color fills this replaces); a light
     *   tint here would make that text unreadable. false for an inactive
     *   chip (hollow interior, usually a dim [color] such as
     *   [AppColors.muted]) so the two states stay visually distinct.
     */
    fun chip(
        context: Context,
        color: Int,
        cornerRadiusDp: Float = 10f,
        filled: Boolean = false
    ): LayerDrawable {
        val fill = if (filled) withAlpha(color, 232) else Color.TRANSPARENT
        return panel(context, color, fill, cornerRadiusDp)
    }

    /**
     * A thin horizontal glow rule for section dividers — a 1–2dp line has
     * no room for concentric rings, so this fades the color in from
     * transparent on both ends instead, giving a "glow line" rather than a
     * flat bar. Height/width come from the view's own LayoutParams, same
     * as the flat divider it replaces.
     */
    fun glowLine(color: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, withAlpha(color, 220), Color.TRANSPARENT)
        ).apply { setShape(GradientDrawable.RECTANGLE) }

    /** Same [color] with a different alpha (0–255, clamped), preserving RGB. */
    private fun withAlpha(color: Int, alpha: Int): Int {
        val clamped = alpha.coerceIn(0, 255)
        return Color.argb(clamped, Color.red(color), Color.green(color), Color.blue(color))
    }

    /**
     * Applies [drawable] as [view]'s background and grows its EXISTING
     * padding by [haloDp] on every side so the outer glow rings have room
     * to render without being clipped by the view's own content.
     *
     * Idempotency: guarded by [View.getTag]/[View.setTag] so calling this
     * twice on the same view instance (e.g. a re-bound row) does not keep
     * inflating its padding on every call. No call site in this codebase
     * currently reuses view instances that way (each render pass builds
     * fresh views), but every list-rendering function here rebuilds its
     * rows from scratch on each state update, so this is a defensive
     * guard against that changing later, not a currently-exercised path.
     * NOTE: this repurposes the view's plain (untagged) `tag` field —
     * if a call site later needs `view.tag` for its own data, switch this
     * to a keyed tag (`View.setTag(id, value)`) instead.
     */
    fun applyTo(view: View, drawable: LayerDrawable, haloDp: Int = DEFAULT_HALO_DP) {
        view.background = drawable
        if (view.tag == GLOW_PADDED_TAG) return
        val density = view.resources.displayMetrics.density
        val extra = (haloDp * density).toInt()
        view.setPadding(
            view.paddingLeft + extra,
            view.paddingTop + extra,
            view.paddingRight + extra,
            view.paddingBottom + extra
        )
        view.tag = GLOW_PADDED_TAG
    }

    private const val GLOW_PADDED_TAG = "glow_box_padded"
}
