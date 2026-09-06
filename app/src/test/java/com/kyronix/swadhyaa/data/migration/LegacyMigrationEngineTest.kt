package com.kyronix.swadhyaa.data.migration

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the parts of LegacyMigrationEngine that don't need a
 * real Android Context — JSON parsing and the hash→identity fallback
 * rules. What this does NOT cover (and can't, without Robolectric, which
 * isn't a project dependency): the actual SharedPreferences file lookup
 * (findLegacySharedPreferences) and Veda mantra resolution (needs a real
 * CoreDatabase/Room instance). Those need an instrumented (androidTest)
 * run instead — see CHANGES.md for that gap.
 */
class LegacyMigrationEngineTest {

    // ── Bookmark JSON parsing ───────────────────────────────────────

    @Test
    fun parseLegacyBookmarks_parsesAllFieldsIncludingNoteAndScrollPercent() {
        val json = """
            [
              {"id":"bm_1","hash":"#/mantra/rigveda/1.1.1","title":"Rig 1.1.1",
               "note":"remember this for the essay","scrollPercent":42,"createdAt":1700000000000},
              {"id":"bm_2","hash":"#/mahabharata/parba/1/adhyay/3","title":"Adi Parba 3","createdAt":1700000001000}
            ]
        """.trimIndent()

        val parsed = LegacyMigrationEngine.parseLegacyBookmarks(json)

        assertEquals(2, parsed.size)
        assertEquals("bm_1", parsed[0].id)
        assertEquals("#/mantra/rigveda/1.1.1", parsed[0].hash)
        assertEquals("remember this for the essay", parsed[0].note)
        assertEquals(42, parsed[0].scrollPercent)
        // second record has no "note"/"scrollPercent" key at all — must not throw, must be null
        assertNull(parsed[1].note)
        assertNull(parsed[1].scrollPercent)
    }

    @Test
    fun parseLegacyBookmarks_emptyArrayProducesEmptyList() {
        assertTrue(LegacyMigrationEngine.parseLegacyBookmarks("[]").isEmpty())
    }

    // ── Reading-position JSON parsing ───────────────────────────────

    @Test
    fun parseLegacyReadingPositions_parsesMultipleScriptureKeys() {
        val json = """
            {
              "veda:rigveda": {"hash":"#/mantra/rigveda/2.3.1","title":"Rig 2.3.1","scrollPercent":10,"updatedAt":1000},
              "ramayana": {"hash":"#/ramayana/shloka/1.1.1","title":"Bala Kanda 1.1.1","updatedAt":2000},
              "mahabharata": {"hash":"#/mahabharata/parba/1/adhyay/1","updatedAt":500}
            }
        """.trimIndent()

        val parsed = LegacyMigrationEngine.parseLegacyReadingPositions(json)
        assertEquals(3, parsed.size)
        assertEquals(setOf("veda:rigveda", "ramayana", "mahabharata"), parsed.map { it.scriptureKey }.toSet())
    }

    @Test
    fun pickLatestReadingPosition_picksHighestUpdatedAt() {
        val json = """
            {
              "veda:rigveda": {"hash":"#/mantra/rigveda/2.3.1","updatedAt":1000},
              "ramayana": {"hash":"#/ramayana/shloka/1.1.1","updatedAt":9999},
              "mahabharata": {"hash":"#/mahabharata/parba/1/adhyay/1","updatedAt":500}
            }
        """.trimIndent()

        val latest = LegacyMigrationEngine.pickLatestReadingPosition(json)
        assertEquals("ramayana", latest?.scriptureKey)
    }

    // ── Settings translation ────────────────────────────────────────

    /** Minimal fake — only getString()/contains() are exercised by migrateSettings(). */
    private class FakeSharedPreferences(private val values: Map<String, String>) : SharedPreferences {
        override fun getAll() = throw UnsupportedOperationException()
        override fun getString(key: String?, defValue: String?) = values[key] ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = throw UnsupportedOperationException()
        override fun getInt(key: String?, defValue: Int) = throw UnsupportedOperationException()
        override fun getLong(key: String?, defValue: Long) = throw UnsupportedOperationException()
        override fun getFloat(key: String?, defValue: Float) = throw UnsupportedOperationException()
        override fun getBoolean(key: String?, defValue: Boolean) = throw UnsupportedOperationException()
        override fun contains(key: String?) = values.containsKey(key)
        override fun edit() = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun migrateSettings_returnsNullWhenNoLegacySettingsPresent() {
        val sp = FakeSharedPreferences(emptyMap())
        assertNull(LegacyMigrationEngine.migrateSettings(sp))
    }

    @Test
    fun migrateSettings_readsPresentValuesAndFillsDefaultsForMissingOnes() {
        // Only a subset of the 11 legacy keys present — the rest must fall
        // back to ReaderSettings()'s defaults, not crash or leave nulls.
        val sp = FakeSharedPreferences(
            mapOf(
                "fontSize" to "22",
                "theme" to "dark",
                "justifyText" to "false"
            )
        )

        val settings = LegacyMigrationEngine.migrateSettings(sp)

        assertEquals(22, settings?.fontSize)
        assertEquals("dark", settings?.theme)
        assertEquals(false, settings?.justifyText)
        // untouched keys fall back to legacy's own documented defaults
        assertEquals("gold", settings?.accentTheme)
        assertEquals(false, settings?.keepAwake) // ReaderSettings() default
        assertEquals("বাংলা", settings?.language)
    }

    @Test
    fun migrateSettings_malformedFontSizeFallsBackToDefaultRatherThanThrowing() {
        val sp = FakeSharedPreferences(mapOf("fontSize" to "not-a-number"))
        val settings = LegacyMigrationEngine.migrateSettings(sp)
        assertEquals(18, settings?.fontSize) // ReaderSettings() default
    }

    // ── Ramayana ref regex ───────────────────────────────────────────
    // Pattern verified byte-for-byte against legacy ramayana.js's
    // ramGetShlokaByRef: /^K(\d+)\.S(\d+)\.(\d+)$/

    @Test
    fun ramayanaRefPattern_matchesValidRefAndCapturesAllThreeIds() {
        val m = LegacyMigrationEngine.ramayanaRefPattern.matchEntire("K1.S2.3")
        assertTrue(m != null)
        val (kanda, sarga, shloka) = m!!.destructured
        assertEquals("1", kanda)
        assertEquals("2", sarga)
        assertEquals("3", shloka)
    }

    @Test
    fun ramayanaRefPattern_matchesMultiDigitIds() {
        val m = LegacyMigrationEngine.ramayanaRefPattern.matchEntire("K6.S128.15234")
        assertTrue(m != null)
        val (kanda, sarga, shloka) = m!!.destructured
        assertEquals("6", kanda)
        assertEquals("128", sarga)
        assertEquals("15234", shloka)
    }

    @Test
    fun ramayanaRefPattern_rejectsMalformedRefs() {
        val malformed = listOf(
            "K1.S2",           // missing shloka id
            "1.2.3",           // missing K/S prefixes
            "K1.S2.3.4",       // trailing extra segment
            "Ka.Sb.c",         // non-numeric
            " K1.S2.3",        // leading space
            "K1.S2.3 ",        // trailing space
        )
        malformed.forEach { ref ->
            assertTrue(
                "expected '$ref' to NOT match, but it did",
                LegacyMigrationEngine.ramayanaRefPattern.matchEntire(ref) == null
            )
        }
    }
}
