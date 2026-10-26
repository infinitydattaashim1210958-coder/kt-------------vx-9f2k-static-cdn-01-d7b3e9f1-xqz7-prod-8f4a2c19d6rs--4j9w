package com.kyronix.swadhyaa.ui.components

import android.content.Context
import android.util.TypedValue
import com.kyronix.swadhyaa.ui.theme.AppColors

/**
 * Shared spacing, shape, and type scale for programmatic Views.
 * Colors always come from [AppColors] so accent changes stay coherent.
 */
object UiTokens {
    /** Spacing scale (dp). */
    const val SPACE_XS = 4
    const val SPACE_SM = 8
    const val SPACE_MD = 12
    const val SPACE_LG = 16
    const val SPACE_XL = 24
    const val SPACE_XXL = 32

    /** Corner radius (dp). */
    const val RADIUS_SM = 8
    const val RADIUS_MD = 12
    const val RADIUS_LG = 16

    /** Minimum touch target (dp) — Material / a11y guidance. */
    const val TOUCH_MIN = 48

    /** Type scale (sp). */
    const val TYPE_TITLE = 22f
    const val TYPE_SECTION = 13f
    const val TYPE_BODY = 15f
    const val TYPE_META = 12f
    const val TYPE_TAB = 11f
    const val TYPE_CAPTION = 12f

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()

    fun sp(context: Context, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            context.resources.displayMetrics
        )

    /** Semantic colors aliased from AppColors for component APIs. */
    fun bg() = AppColors.bg
    fun surface() = AppColors.surface
    fun elevated() = AppColors.elevated
    fun ink() = AppColors.ink
    fun ivory() = AppColors.ivory
    fun muted() = AppColors.muted
    fun mutedDim() = AppColors.mutedDim
    fun accent() = AppColors.gold
    fun accentBright() = AppColors.goldBright
    fun border() = AppColors.border
    fun saffron() = AppColors.saffron
}
