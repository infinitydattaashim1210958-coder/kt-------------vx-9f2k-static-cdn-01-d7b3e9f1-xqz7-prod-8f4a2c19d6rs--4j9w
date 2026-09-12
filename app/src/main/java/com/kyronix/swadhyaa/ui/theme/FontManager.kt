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
 *
 * ── VEDIC ACCENT (স্বরচিহ্ন) COVERAGE ──
 * [supportsVedicAccents] was checked directly against each bundled .ttf's
 * cmap table for U+0951–0954 (udatta/anudatta/grave/acute) and the
 * U+1CD0–U+1CFF Vedic Extensions block:
 *   - noto_serif_devanagari / noto_sans_devanagari: full coverage of the
 *     marks actually used in printed Samhita/pada-patha text (41/48 of the
 *     Vedic Extensions block — the 7 missing codepoints, U+1CF7 and
 *     U+1CFA-1CFF, are rare Kashmiri-recension marks unlikely to appear in
 *     standard editions).
 *   - tiro_devanagari_sanskrit: same, minus U+0953/U+0954 (grave/acute —
 *     rarely used) and 5 of the same rare Kashmiri marks.
 *   - tiro_devanagari_hindi, eczar, sahitya, yantramanav, jaini,
 *     jaini_purva: ZERO Vedic Extensions glyphs (they're general Hindi/
 *     Devanagari display fonts, not Sanskrit/Vedic ones) — udatta/anudatta
 *     (U+0951/0952) themselves are present, but most other svara marks
 *     will silently fail to render or fall back to a mismatched system
 *     font (which then mispositions combining marks, since mark-to-base
 *     GPOS attachment doesn't work across two different fonts).
 * "system" (device default, usually Roboto) is marked unsupported — Roboto
 * has no Devanagari glyphs at all.
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
    // Ordered so the three Vedic-accent-safe choices come first.
    val DEVANAGARI_FONTS: List<FontEntry> = listOf(
        FontEntry("noto_serif_devanagari",   "Noto Serif Devanagari",    null, supportsVedicAccents = true),
        FontEntry("noto_sans_devanagari",    "Noto Sans Devanagari",     null, supportsVedicAccents = true),
        FontEntry("tiro_devanagari_sanskrit","Tiro Devanagari Sanskrit", null, supportsVedicAccents = true),
        FontEntry("system",                  "System Default",            null, supportsVedicAccents = false),
        FontEntry("tiro_devanagari_hindi",   "Tiro Devanagari Hindi",    null, supportsVedicAccents = false),
        FontEntry("eczar",                   "Eczar",                    null, supportsVedicAccents = false),
        FontEntry("sahitya",                 "Sahitya",                  null, supportsVedicAccents = false),
        FontEntry("yantramanav",             "Yantramanav",              null, supportsVedicAccents = false),
        FontEntry("jaini",                   "Jaini",                    null, supportsVedicAccents = false),
        FontEntry("jaini_purva",             "Jaini Purva",              null, supportsVedicAccents = false),
    )

    data class FontEntry(
        val id: String,
        val displayName: String,
        @Volatile private var cached: Typeface?,
        /**
         * False means this font is missing enough of the U+1CD0–1CFF Vedic
         * Extensions block (and/or U+0951–0954) that svara marks in mantra
         * text will drop or mis-render if this is picked as the Devanagari
         * font. See the file-level doc above for how this was verified.
         * Defaults to true for entries that don't set it explicitly
         * (Bengali fonts don't apply here).
         */
        val supportsVedicAccents: Boolean = true
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
