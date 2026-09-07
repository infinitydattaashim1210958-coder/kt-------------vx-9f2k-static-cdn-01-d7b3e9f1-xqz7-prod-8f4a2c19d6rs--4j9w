package com.kyronix.swadhyaa.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

/**
 * Regression test for RISK_REGISTER.md R10.
 *
 * The FTS query construction this replaced (`"$token*"`, unescaped) threw
 * a runtime SQLiteException from a plain search box on ordinary-looking
 * input: an unbalanced quote, a dangling "AND"/"OR", "col:value" syntax, an
 * unclosed paren, or a leading hyphen. This test proves the replacement
 * (`SearchRepository.escapeFtsQuery`) survives all of those inputs against
 * a real FTS5 table — not just a documentation claim.
 *
 * Pure JVM, no Android framework dependency — runs the same way
 * DatabaseVerificationTest does, no emulator required.
 */
class SearchRepositoryFtsEscapingTest {

    /** Inputs that crashed the old, unescaped per-token prefix query. */
    private val previouslyCrashingInputs = listOf(
        "\"unbalanced",
        "fire AND",
        "title:soma",
        "a OR b",
        "(unclosed",
        "-exclude",
        "NEAR(a b)",
        "*",
        "\"\"",
        "\"",
    )

    /** Inputs the app must keep working correctly, including its actual content scripts. */
    private val mustStillMatchInputs = mapOf(
        "mantra" to "rigveda mantra one two three",
        "rigveda mantra" to "rigveda mantra one two three",
        "ঋগ্বেদ" to "ঋগ্বেদ মন্ত্র পরীক্ষা",
        "अग्नि" to "अग्निमीळे पुरोहितं",
    )

    private fun freshFtsConnection(): java.sql.Connection {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { it.execute("CREATE VIRTUAL TABLE t USING fts5(body)") }
        return conn
    }

    private fun insert(conn: java.sql.Connection, body: String) {
        conn.prepareStatement("INSERT INTO t(body) VALUES (?)").use { ps ->
            ps.setString(1, body)
            ps.executeUpdate()
        }
    }

    @Test
    fun escapeFtsQuery_neverThrowsOnPreviouslyCrashingInput() {
        freshFtsConnection().use { conn ->
            insert(conn, "rigveda mantra one two three")
            insert(conn, "another verse about fire and soma")

            for (raw in previouslyCrashingInputs) {
                val escaped = SearchRepository.escapeFtsQuery(raw)
                if (escaped.isEmpty()) continue // caller-side guard, not this function's job
                conn.prepareStatement("SELECT body FROM t WHERE t MATCH ?").use { ps ->
                    ps.setString(1, escaped)
                    // The assertion IS that this line does not throw.
                    ps.executeQuery().use { /* no-op: absence of exception is the pass */ }
                }
            }
        }
    }

    @Test
    fun escapeFtsQuery_stillMatchesRealContentIncludingBengaliAndDevanagari() {
        freshFtsConnection().use { conn ->
            mustStillMatchInputs.values.toSet().forEach { insert(conn, it) }

            for ((query, expectedRow) in mustStillMatchInputs) {
                val escaped = SearchRepository.escapeFtsQuery(query)
                assertFalse("escaped query for '$query' must not be empty", escaped.isEmpty())

                conn.prepareStatement("SELECT body FROM t WHERE t MATCH ?").use { ps ->
                    ps.setString(1, escaped)
                    ps.executeQuery().use { rs ->
                        val found = mutableListOf<String>()
                        while (rs.next()) found += rs.getString(1)
                        assertTrue(
                            "query '$query' (escaped: $escaped) should match '$expectedRow', got $found",
                            found.contains(expectedRow)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun escapeFtsQuery_blankInputProducesEmptyString() {
        assertTrue(SearchRepository.escapeFtsQuery("   ").isEmpty())
        assertTrue(SearchRepository.escapeFtsQuery("").isEmpty())
    }

    @Test
    fun escapeLike_endToEnd_literalPercentMatchesOnlyWithEscapeClause() {
        // Regression test for a real bug found this session: escapeLike()
        // escapes % and _ with a backslash, but that only means anything
        // to SQLite if the query ALSO says `ESCAPE '\'` — without it,
        // LIKE treats backslash as a literal character and the escaping
        // silently does nothing. Verified empirically (not just reasoned
        // about) before fixing SearchRepository's two LIKE fallback
        // queries, both of which were missing this clause.
        val conn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use {
            it.execute("CREATE TABLE t(body TEXT)")
            it.execute("INSERT INTO t VALUES ('discount is 50% off today')")
        }
        val pattern = "%${SearchRepository.escapeLike("50%")}%"

        // Without ESCAPE — reproduces the bug: must NOT match (proves the
        // bug existed; if this ever starts matching, LIKE's default
        // behavior changed and the whole premise here needs re-checking).
        conn.prepareStatement("SELECT body FROM t WHERE body LIKE ?").use { ps ->
            ps.setString(1, pattern)
            ps.executeQuery().use { rs ->
                assertTrue("expected no match without ESCAPE clause (reproducing the bug)", !rs.next())
            }
        }

        // With ESCAPE — the actual fix: must match.
        conn.prepareStatement("SELECT body FROM t WHERE body LIKE ? ESCAPE '\\'").use { ps ->
            ps.setString(1, pattern)
            ps.executeQuery().use { rs ->
                assertTrue("expected a match with ESCAPE clause (the fix)", rs.next())
                assertEquals("discount is 50% off today", rs.getString(1))
            }
        }
    }

    @Test
    fun escapeLike_escapesPercentAndUnderscoreWildcards() {
        // A literal "_" or "%" in a user's search term must not act as a
        // SQL LIKE wildcard in the fallback (non-FTS) search path.
        val escaped = SearchRepository.escapeLike("50%_off")
        assertTrue(escaped.contains("\\%"))
        assertTrue(escaped.contains("\\_"))
    }
}
