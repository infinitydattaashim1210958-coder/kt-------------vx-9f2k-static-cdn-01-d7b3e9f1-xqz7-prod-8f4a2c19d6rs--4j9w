package com.kyronix.swadhyaa.ui.theme

import android.content.Context
import android.graphics.Typeface

/**
 * Central font registry for স্বাধ্যায়.
 *
 * All fonts live under app/src/main/assets/fonts/ as .ttf files and are
 * loaded lazily from assets on first use. Two independent picker axes:
 *
 *   banglaFont     — used for Bengali UI text, commentary, subtitles
 *   devanagariFont — used for Sanskrit mantra text in ReaderActivity
 *
 * Font ID keys (stored in SettingsRepository) are the asset filename
 * stems (e.g. "noto_serif_devanagari"). "system" means use the platform
 * default (Roboto / whatever the device provides).
 */
object FontManager {

    // ── Bengali fonts ──────────────────────────────────────────────────
    val BANGLA_FONTS: List<FontEntry> = listOf(
        FontEntry("system",             "System Default",       null),
        FontEntry("ruposhi_bangla",     "রূপসী বাংলা",          null),
        FontEntry("rozha_one",          "Rozha One",            null),
        FontEntry("kalpana",            "কল্পনা",               null),
        FontEntry("li_alinur_akorshon", "Li Alinur Akorshon",  null),
        FontEntry("li_chayana_teesta",  "Li Chayana Teesta",   null),
        FontEntry("li_hasan_akibuki",   "Li Hasan Akibuki",    null),
        FontEntry("li_mahfuj_ak",       "Li Mahfuj AK",        null),
        FontEntry("li_sananda",         "Li Sananda",          null),
    )

    // ── Devanagari / Sanskrit fonts ────────────────────────────────────
    val DEVANAGARI_FONTS: List<FontEntry> = listOf(
        FontEntry("system",                  "System Default",            null),
        FontEntry("noto_serif_devanagari",   "Noto Serif Devanagari",    null),
        FontEntry("noto_sans_devanagari",    "Noto Sans Devanagari",     null),
        FontEntry("tiro_devanagari_sanskrit","Tiro Devanagari Sanskrit", null),
        FontEntry("tiro_devanagari_hindi",   "Tiro Devanagari Hindi",    null),
        FontEntry("eczar",                   "Eczar",                    null),
        FontEntry("sahitya",                 "Sahitya",                  null),
        FontEntry("yantramanav",             "Yantramanav",              null),
        FontEntry("jaini",                   "Jaini",                    null),
        FontEntry("jaini_purva",             "Jaini Purva",              null),
    )

    data class FontEntry(
        val id: String,
        val displayName: String,
        @Volatile private var cached: Typeface?
    ) {
        /** Returns the typeface, loading from assets on first call. */
        fun typeface(context: Context): Typeface {
            if (id == "system") return Typeface.DEFAULT
            cached?.let { return it }
            return synchronized(this) {
                cached ?: run {
                    val tf = try {
                        Typeface.createFromAsset(context.assets, "fonts/$id.ttf")
                    } catch (e: Exception) {
                        Typeface.DEFAULT
                    }
                    cached = tf
                    tf
                }
            }
        }
    }

    fun banglaEntry(id: String): FontEntry =
        BANGLA_FONTS.firstOrNull { it.id == id } ?: BANGLA_FONTS.first()

    fun devanagariEntry(id: String): FontEntry =
        DEVANAGARI_FONTS.firstOrNull { it.id == id } ?: DEVANAGARI_FONTS.first()

    /** Convenience: resolve the typeface directly from a stored font ID. */
    fun banglaTypeface(context: Context, id: String): Typeface =
        banglaEntry(id).typeface(context)

    fun devanagariTypeface(context: Context, id: String): Typeface =
        devanagariEntry(id).typeface(context)
}
