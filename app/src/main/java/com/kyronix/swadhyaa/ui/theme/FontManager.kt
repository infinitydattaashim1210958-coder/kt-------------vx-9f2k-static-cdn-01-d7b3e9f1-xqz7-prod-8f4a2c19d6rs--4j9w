package com.kyronix.swadhyaa.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.kyronix.swadhyaa.R
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Central font registry for স্বাধ্যায়.
 *
 * FIXED FONTS (no user picker any more):
 *
 *   Bengali UI text  → Hind Siliguri (Regular + Bold), bundled in
 *                      res/font/. The system default font is never used
 *                      for Bengali text anywhere in the app — see
 *                      [FontEnforcer], which swaps every leftover
 *                      platform-default typeface for Hind Siliguri.
 *
 *   Devanagari mantra text → Noto Serif Devanagari (assets/fonts/).
 *                      Chosen after rendering Vedic svara marks
 *                      (U+0951/0952 + U+1CD0–1CFF incl. ॒ ॑ stacked on
 *                      anusvara/visarga/conjuncts) in Noto Serif, Noto Sans
 *                      and Tiro Sanskrit: Noto Serif keeps every mark
 *                      correctly attached (Tiro floats some marks away from
 *                      their base), it has full U+0953/0954 + U+A8E0–A8FF
 *                      coverage, and it is the closest match to the
 *                      reference (old JS app) look.
 *
 * NOTE: Hind Siliguri has NO Devanagari glyphs (only danda/double-danda),
 * so Devanagari text must always be given the Devanagari typeface
 * explicitly (ReaderActivity etc. already do; [FontEnforcer] covers any
 * remaining Devanagari TextView).
 */
object FontManager {

    const val BANGLA_ID = "hind_siliguri"
    const val DEVANAGARI_ID = "noto_serif_devanagari"

    // Typefaces handed out by this registry — lets FontEnforcer tell "our"
    // typefaces apart from platform defaults (Typeface.DEFAULT etc.).
    private val appTypefaces: MutableSet<Typeface> =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<Typeface, Boolean>()))

    fun isAppTypeface(tf: Typeface?): Boolean = tf != null && appTypefaces.contains(tf)

    private fun register(tf: Typeface): Typeface { appTypefaces.add(tf); return tf }

    // ── Bengali font (single, fixed) ───────────────────────────────────
    val BANGLA_FONTS: List<FontEntry> = listOf(
        FontEntry(BANGLA_ID, "Hind Siliguri", null)
    )

    // ── Devanagari / Sanskrit font (single, fixed) ─────────────────────
    val DEVANAGARI_FONTS: List<FontEntry> = listOf(
        FontEntry(DEVANAGARI_ID, "Noto Serif Devanagari", null, supportsVedicAccents = true)
    )

    data class FontEntry(
        val id: String,
        val displayName: String,
        @Volatile private var cached: Typeface?,
        val supportsVedicAccents: Boolean = true
    ) {
        /** Returns the typeface, loading it on first call. */
        fun typeface(context: Context): Typeface {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: run {
                    val tf = try {
                        if (id == BANGLA_ID) {
                            ResourcesCompat.getFont(context.applicationContext, R.font.hind_siliguri_regular)
                                ?: Typeface.DEFAULT
                        } else {
                            Typeface.createFromAsset(context.applicationContext.assets, "fonts/$id.ttf")
                        }
                    } catch (e: Exception) {
                        Typeface.DEFAULT
                    }
                    if (tf !== Typeface.DEFAULT) register(tf)
                    cached = tf
                    tf
                }
            }
        }
    }

    @Volatile private var banglaBoldCached: Typeface? = null

    /** Hind Siliguri Bold. */
    fun banglaBold(context: Context): Typeface {
        banglaBoldCached?.let { return it }
        return synchronized(this) {
            banglaBoldCached ?: run {
                val tf = try {
                    ResourcesCompat.getFont(context.applicationContext, R.font.hind_siliguri_bold)
                        ?: Typeface.DEFAULT_BOLD
                } catch (e: Exception) {
                    Typeface.DEFAULT_BOLD
                }
                if (tf !== Typeface.DEFAULT_BOLD) register(tf)
                banglaBoldCached = tf
                tf
            }
        }
    }

    // The lookups below ignore the stored id on purpose: old installs may
    // still have a removed font (ruposhi_bangla, "system", Tiro, …) saved
    // in settings — it must never resurface.
    @Suppress("UNUSED_PARAMETER")
    fun banglaEntry(id: String): FontEntry = BANGLA_FONTS.first()

    @Suppress("UNUSED_PARAMETER")
    fun devanagariEntry(id: String): FontEntry = DEVANAGARI_FONTS.first()

    fun banglaTypeface(context: Context, id: String): Typeface = banglaEntry(id).typeface(context)

    fun devanagariTypeface(context: Context, id: String): Typeface = devanagariEntry(id).typeface(context)
}
