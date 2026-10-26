package com.kyronix.swadhyaa.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.Color as AndroidColor
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.kyronix.swadhyaa.ui.theme.AppColors

/**
 * Reusable programmatic View builders for the shell and related screens.
 * Does not own repositories or navigation — pure presentation helpers.
 */
object UiComponents {

    fun screenTitle(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppColors.ivory)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, UiTokens.TYPE_TITLE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, UiTokens.dp(context, UiTokens.SPACE_SM))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

    fun sectionHeader(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppColors.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, UiTokens.TYPE_SECTION)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            setPadding(
                0,
                UiTokens.dp(context, UiTokens.SPACE_LG),
                0,
                UiTokens.dp(context, UiTokens.SPACE_SM)
            )
        }

    fun subtitle(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppColors.gold)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, UiTokens.TYPE_SECTION)
            setPadding(0, 0, 0, UiTokens.dp(context, UiTokens.SPACE_LG))
        }

    fun body(context: Context, text: String, muted: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(if (muted) AppColors.muted else AppColors.ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, UiTokens.TYPE_BODY)
        }

    fun meta(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(AppColors.mutedDim)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, UiTokens.TYPE_META)
        }

    /**
     * Surface card with restrained border (no glow). Optional press ripple.
     */
    fun card(
        context: Context,
        clickable: Boolean = false,
        onClick: (() -> Unit)? = null,
        block: LinearLayout.() -> Unit
    ): LinearLayout {
        val pad = UiTokens.dp(context, UiTokens.SPACE_MD)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = cardBackground(context, elevated = false)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = UiTokens.dp(context, UiTokens.SPACE_SM)
            }
            layoutParams = lp
            if (clickable && onClick != null) {
                isClickable = true
                isFocusable = true
                minimumHeight = UiTokens.dp(context, UiTokens.TOUCH_MIN)
                setOnClickListener { onClick() }
            }
            block()
        }
    }

    fun emptyState(
        context: Context,
        title: String,
        detail: String? = null
    ): LinearLayout {
        val pad = UiTokens.dp(context, UiTokens.SPACE_XL)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(context).apply {
                text = title
                setTextColor(AppColors.muted)
                setTe
... 
