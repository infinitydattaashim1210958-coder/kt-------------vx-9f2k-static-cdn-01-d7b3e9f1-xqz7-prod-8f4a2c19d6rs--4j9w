package com.kyronix.swadhyaa.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Reader/appearance settings. New this session — previously 0% implemented
 * (RISK_REGISTER.md R6). Built now because the legacy-data migration
 * engine (R3) needs a real destination to migrate legacy settings into.
 *
 * Defaults and value shapes below are copied exactly from legacy
 * settings.js's `defaults` object and `save()`/`load()` calls — re-read
 * directly from source before writing this, not from memory or the
 * earlier (partially incorrect) DATA_MIGRATION_PLAN.md draft. See that
 * file's corrected §1 for the native-vs-web key-name distinction this
 * class's sibling, LegacyMigrationEngine, depends on.
 *
 * theme: "auto" | "light" | "dark"
 * accentTheme: "gold" | "emerald" | "indigo"
 * lineHeight: "compact" | "normal" | "relaxed"
 * pageMargin: "narrow" | "normal" | "wide"
 * fontFamily: "default" | (other legacy-defined families — themes.js/
 *   settings.js only reference "default" as the shipped default; the full
 *   enumerated list lives in the reader UI, not settings.js itself, and
 *   was not independently re-verified this session)
 *
 * This class deliberately stores fontSize as Int (legacy stores it as the
 * string "18") — the string/number conversion happens once, at the
 * DataStore boundary here, so every other call site in Kotlin gets a
 * proper Int rather than repeating String.toIntOrNull() everywhere.
 */
data class ReaderSettings(
    val fontSize: Int = 18,
    val fontFamily: String = "default",
    val banglaFont: String = "system",
    val devanagariFont: String = "noto_serif_devanagari",
    val theme: String = "auto",
    val accentTheme: String = "gold",
    val lineHeight: String = "normal",
    val pageMargin: String = "normal",
    val justifyText: Boolean = true,
    val keepAwake: Boolean = false,
    val language: String = "বাংলা",
    val script: String = "বাংলা",
    val transliteration: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val fontSize = intPreferencesKey("setting_fontSize")
        val fontFamily = stringPreferencesKey("setting_fontFamily")
        val banglaFont = stringPreferencesKey("setting_banglaFont")
        val devanagariFont = stringPreferencesKey("setting_devanagariFont")
        val theme = stringPreferencesKey("setting_theme")
        val accentTheme = stringPreferencesKey("setting_accentTheme")
        val lineHeight = stringPreferencesKey("setting_lineHeight")
        val pageMargin = stringPreferencesKey("setting_pageMargin")
        val justifyText = booleanPreferencesKey("setting_justifyText")
        val keepAwake = booleanPreferencesKey("setting_keepAwake")
        val language = stringPreferencesKey("setting_language")
        val script = stringPreferencesKey("setting_script")
        val transliteration = booleanPreferencesKey("setting_transliteration")
    }

    val settingsFlow: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        val defaults = ReaderSettings()
        ReaderSettings(
            fontSize = prefs[Keys.fontSize] ?: defaults.fontSize,
            fontFamily = prefs[Keys.fontFamily] ?: defaults.fontFamily,
            banglaFont = prefs[Keys.banglaFont] ?: defaults.banglaFont,
            devanagariFont = prefs[Keys.devanagariFont] ?: defaults.devanagariFont,
            theme = prefs[Keys.theme] ?: defaults.theme,
            accentTheme = prefs[Keys.accentTheme] ?: defaults.accentTheme,
            lineHeight = prefs[Keys.lineHeight] ?: defaults.lineHeight,
            pageMargin = prefs[Keys.pageMargin] ?: defaults.pageMargin,
            justifyText = prefs[Keys.justifyText] ?: defaults.justifyText,
            keepAwake = prefs[Keys.keepAwake] ?: defaults.keepAwake,
            language = prefs[Keys.language] ?: defaults.language,
            script = prefs[Keys.script] ?: defaults.script,
            transliteration = prefs[Keys.transliteration] ?: defaults.transliteration
        )
    }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        // Read-modify-write against current stored values (not just
        // defaults), so a partial update never clobbers unrelated fields.
        applyFull(transform(readOnce()))
    }

    suspend fun setFontSize(v: Int) = applyFull(readOnce().copy(fontSize = v))
    suspend fun setFontFamily(v: String) = applyFull(readOnce().copy(fontFamily = v))
    suspend fun setBanglaFont(v: String) = applyFull(readOnce().copy(banglaFont = v))
    suspend fun setDevanagariFont(v: String) = applyFull(readOnce().copy(devanagariFont = v))
    suspend fun setTheme(v: String) = applyFull(readOnce().copy(theme = v))
    suspend fun setAccentTheme(v: String) = applyFull(readOnce().copy(accentTheme = v))
    suspend fun setLineHeight(v: String) = applyFull(readOnce().copy(lineHeight = v))
    suspend fun setPageMargin(v: String) = applyFull(readOnce().copy(pageMargin = v))
    suspend fun setJustifyText(v: Boolean) = applyFull(readOnce().copy(justifyText = v))
    suspend fun setKeepAwake(v: Boolean) = applyFull(readOnce().copy(keepAwake = v))
    suspend fun setLanguage(v: String) = applyFull(readOnce().copy(language = v))
    suspend fun setScript(v: String) = applyFull(readOnce().copy(script = v))
    suspend fun setTransliteration(v: Boolean) = applyFull(readOnce().copy(transliteration = v))

    /**
     * A single snapshot of current settings.
     *
     * Uses Flow.first(), not collect{} — DataStore's `.data` Flow is hot
     * and never completes on its own (it keeps emitting on every future
     * change), so a bare `.collect { ... }` here would suspend forever on
     * the very first call instead of returning after one value. first()
     * takes exactly one emission and returns; reuses the same mapping
     * settingsFlow already does, so the two can't drift out of sync.
     */
    private suspend fun readOnce(): ReaderSettings = settingsFlow.first()

    private suspend fun applyFull(s: ReaderSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.fontSize] = s.fontSize
            prefs[Keys.fontFamily] = s.fontFamily
            prefs[Keys.banglaFont] = s.banglaFont
            prefs[Keys.devanagariFont] = s.devanagariFont
            prefs[Keys.theme] = s.theme
            prefs[Keys.accentTheme] = s.accentTheme
            prefs[Keys.lineHeight] = s.lineHeight
            prefs[Keys.pageMargin] = s.pageMargin
            prefs[Keys.justifyText] = s.justifyText
            prefs[Keys.keepAwake] = s.keepAwake
            prefs[Keys.language] = s.language
            prefs[Keys.script] = s.script
            prefs[Keys.transliteration] = s.transliteration
        }
    }

    /** Used only by LegacyMigrationEngine — writes a full settings snapshot atomically. */
    suspend fun applyMigrated(s: ReaderSettings) = applyFull(s)
}
