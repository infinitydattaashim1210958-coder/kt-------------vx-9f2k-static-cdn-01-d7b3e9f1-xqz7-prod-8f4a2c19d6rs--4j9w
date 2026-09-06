package com.kyronix.swadhyaa.ui.theme

import android.graphics.Color

/**
 * Design tokens — illuminated manuscript (dark).
 *
 * Colors verified directly against legacy's www/css/app.css `:root` and
 * `[data-accent="..."]` blocks — not an independent approximation. An
 * earlier version of this file used different, unverified hex values;
 * corrected here for real visual parity (RISK_REGISTER.md R6).
 *
 * IMPORTANT — theme (light/dark) and accent (gold/emerald/indigo/violet)
 * are NOT symmetric in legacy, confirmed by reading the full stylesheet:
 * accent switching is fully implemented (four real CSS palettes). A light
 * theme is NOT — legacy's settings.js exposes a "theme" setting
 * (auto/light/dark) and themes.js sets a `data-theme` attribute for it,
 * but there is no `[data-theme="light"]` CSS block and no
 * `prefers-color-scheme` media query anywhere in app.css or
 * divine-home.css. Selecting "light" in the real legacy app today has
 * ZERO visual effect — it is functionally dark-only. This was confirmed
 * by grepping the full CSS for both patterns, not assumed from the
 * setting's existence. This class therefore only implements accent
 * switching; "theme" is still stored via SettingsRepository (for
 * settings/migration parity — an existing user's stored value shouldn't
 * vanish) but intentionally does not change these colors, matching
 * legacy's real behavior rather than inventing a light mode legacy
 * itself never shipped.
 *
 * All accent-dependent properties are `var ... private set` rather than
 * `val` so [applyAccent] can change every consumer's *next* render
 * without any existing call site (ShellActivity, ReaderActivity,
 * LibraryActivity, etc.) needing to change — they already just reference
 * AppColors.<name> at render time, so this is a drop-in upgrade from
 * static colors to live ones.
 */
object AppColors {

    // ── Base tokens — accent-independent, verified against :root ──────
    var bg = Color.parseColor("#07080A"); private set          // --void
    var ivory = Color.parseColor("#EDE6D6"); private set        // --parchment
    var muted = Color.parseColor("#8D92A0"); private set        // --ash
    var mutedDim = Color.parseColor("#5A5F6C"); private set     // --ash-dim
    var vermilion = Color.parseColor("#E8756C"); private set    // .deleteBtn color
    val black = Color.BLACK

    // ── Accent-dependent tokens — recomputed by applyAccent() ─────────
    var gold = Color.parseColor("#D4A24C"); private set         // --gold (default)
    var goldBright = Color.parseColor("#F0C26A"); private set   // --gold-bright (default)

    /**
     * CTA/highlight text color. Legacy has no separate fixed token for
     * this (an earlier version of this file used a hardcoded "saffron"
     * hue that never changed with accent) — aliasing it to the live
     * accent instead means CTAs correctly re-theme when the user picks
     * emerald/indigo/violet, which is strictly more correct.
     */
    var saffron = gold; private set

    // --panel-border, alpha verified per accent (.18 default / .25 emerald / .22 indigo / .26 violet)
    var border = Color.argb(46, 212, 162, 76); private set

    // Real alpha compositing over `bg`, matching CSS's rgba(20,17,14,.72)
    // --panel exactly (184 ≈ .72 * 255), rather than a pre-blended solid
    // approximation.
    val surface = Color.argb(184, 20, 17, 14)

    // No direct CSS equivalent found — legacy's :root has no distinct
    // "elevated" surface variable. Kept as a Kotlin-only design addition
    // (slightly lighter than `surface`, for visual hierarchy on cards
    // above cards), not a verified port.
    val elevated = Color.argb(210, 28, 23, 18)

    /**
     * Recomputes the accent-dependent tokens above. Values verified
     * directly against app.css's four [data-accent="..."] blocks — "gold"
     * (the default / no-attribute case in legacy) uses the :root values.
     * Call once at startup (from stored settings) and again whenever the
     * user changes the accent in Settings, followed by recreate()-ing the
     * currently visible Activity so it re-reads these at render time.
     */
    fun applyAccent(accent: String) {
        when (accent) {
            "emerald" -> {
                gold = Color.parseColor("#4F8C6B")
                goldBright = Color.parseColor("#7AC99A")
                border = Color.argb(64, 79, 140, 107) // alpha .25
            }
            "indigo" -> {
                gold = Color.parseColor("#6482DC")
                goldBright = Color.parseColor("#94B0F8")
                border = Color.argb(56, 100, 130, 220) // alpha .22
            }
            "violet" -> {
                gold = Color.parseColor("#9B6FD6")
                goldBright = Color.parseColor("#C9A6F5")
                border = Color.argb(66, 155, 111, 214) // alpha .26
            }
            else -> { // "gold" or unrecognized — legacy's own default fallback
                gold = Color.parseColor("#D4A24C")
                goldBright = Color.parseColor("#F0C26A")
                border = Color.argb(46, 212, 162, 76) // alpha .18
            }
        }
        saffron = gold
    }
}
