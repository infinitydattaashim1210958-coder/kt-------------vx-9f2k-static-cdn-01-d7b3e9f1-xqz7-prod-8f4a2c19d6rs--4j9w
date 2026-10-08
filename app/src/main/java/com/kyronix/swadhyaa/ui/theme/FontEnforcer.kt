package com.kyronix.swadhyaa.ui.theme

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import java.util.WeakHashMap

/**
 * Guarantees that NO text in the app is ever drawn with the platform
 * default font.
 *
 * Most screens build their views in code and many set
 * `typeface = Typeface.DEFAULT_BOLD` (or nothing at all), which is the
 * device's system font. Instead of touching hundreds of call sites, this
 * hooks every Activity's window: right before each frame is drawn it walks
 * the view tree and replaces any platform-default typeface with
 *
 *   - Noto Serif Devanagari — if the text is Devanagari (and not Bengali)
 *   - Hind Siliguri Bold    — if the view asked for a bold default
 *   - Hind Siliguri Regular — everything else
 *
 * Views that were already given one of the app's own typefaces (mantra
 * text, Bengali text via FontManager) are left alone. Dialog windows are
 * covered separately by the theme-level android:fontFamily
 * (res/values-v26/themes.xml).
 *
 * Install once from Application.onCreate().
 */
object FontEnforcer {

    // Last typeface we observed on a view after processing it. Some devices
    // (e.g. "Bold font" accessibility option) hand back a wrapped copy of
    // the typeface we set — remembering what we saw prevents an endless
    // re-apply loop in that case.
    private val processed = WeakHashMap<TextView, Any>()
    private val attached = WeakHashMap<Activity, Boolean>()

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) { attach(activity) }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) { attached.remove(activity) }
        })
    }

    private fun attach(activity: Activity) {
        if (attached.containsKey(activity)) return
        val decor = activity.window?.decorView ?: return
        val ctx = activity.applicationContext
        try {
            decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    // If anything changed, setTypeface() has already
                    // requested a new layout pass — skip drawing this
                    // frame so the wrong font is never visible.
                    return !walk(decor, ctx)
                }
            })
            attached[activity] = true
        } catch (e: IllegalStateException) {
            // ViewTreeObserver not alive — nothing to do.
        }
    }

    private fun walk(v: View, ctx: Context): Boolean {
        var changed = false
        if (v is TextView && fix(v, ctx)) changed = true
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                if (walk(v.getChildAt(i), ctx)) changed = true
            }
        }
        return changed
    }

    private fun fix(tv: TextView, ctx: Context): Boolean {
        val cur = tv.typeface
        if (FontManager.isAppTypeface(cur)) return false
        if (cur != null && processed[tv] === cur) return false

        val target = pick(tv, ctx, cur?.isBold == true)
        tv.typeface = target
        processed[tv] = tv.typeface ?: target
        return true
    }

    private fun pick(tv: TextView, ctx: Context, bold: Boolean): android.graphics.Typeface {
        val text = tv.text
        var hasDev = false
        var hasBn = false
        val n = minOf(text?.length ?: 0, 120)
        for (i in 0 until n) {
            val c = text[i]
            if (c in '\u0900'..'\u097F' || c in '\u1CD0'..'\u1CFF' || c in '\uA8E0'..'\uA8FF') hasDev = true
            else if (c in '\u0980'..'\u09FF') hasBn = true
        }
        return when {
            hasDev && !hasBn -> FontManager.devanagariTypeface(ctx, FontManager.DEVANAGARI_ID)
            bold -> FontManager.banglaBold(ctx)
            else -> FontManager.banglaTypeface(ctx, FontManager.BANGLA_ID)
        }
    }
}
