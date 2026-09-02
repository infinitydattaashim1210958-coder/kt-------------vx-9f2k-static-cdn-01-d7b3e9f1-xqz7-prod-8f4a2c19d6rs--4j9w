package com.kyronix.swadhyaa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/**
 * Pure JVM database integrity test.
 * Runs on GitHub Actions without emulator or device.
 *
 * Opens the real production .db assets with sqlite-jdbc and asserts
 * the exact expected row counts. Any mismatch fails the build.
 */
class DatabaseVerificationTest {

    @Test
    fun coreDatabase_hasExpectedCounts() {
        val dbFile = locateAsset("core.db")
        assertTrue("core.db not found at ${dbFile.absolutePath}", dbFile.exists())

        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            val vedas = count(conn, "vedas")
            val mantras = count(conn, "mantras")

            println("DATABASE VERIFICATION")
            println("core.db")
            println("  vedas   = $vedas")
            println("  mantras = $mantras")

            assertEquals("vedas count mismatch", 4, vedas)
            assertEquals("mantras count mismatch", 20380, mantras)
        }
    }

    @Test
    fun ramayanaCoreDatabase_hasExpectedCounts() {
        val dbFile = locateAsset("ramayana_core.db")
        assertTrue("ramayana_core.db not found at ${dbFile.absolutePath}", dbFile.exists())

        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            val kandas = count(conn, "kandas")
            val shlokas = count(conn, "shlokas")

            println("ramayana_core.db")
            println("  kandas  = $kandas")
            println("  shlokas = $shlokas")

            assertEquals("kandas count mismatch", 6, kandas)
            assertEquals("shlokas count mismatch", 17802, shlokas)
        }
    }

    @Test
    fun coreDatabase_hasRequiredTables() {
        val dbFile = locateAsset("core.db")
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            val tables = tableNames(conn)
            assertTrue("missing vedas", "vedas" in tables)
            assertTrue("missing mantras", "mantras" in tables)
            assertTrue("missing scholars", "scholars" in tables)
            assertTrue("missing search_index (FTS5)", tables.any { it.startsWith("search_index") })
        }
    }

    @Test
    fun ramayanaCoreDatabase_hasRequiredTables() {
        val dbFile = locateAsset("ramayana_core.db")
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            val tables = tableNames(conn)
            assertTrue("missing kandas", "kandas" in tables)
            assertTrue("missing sargas", "sargas" in tables)
            assertTrue("missing shlokas", "shlokas" in tables)
            assertTrue("missing shlokas_fts", tables.any { it.startsWith("shlokas_fts") })
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun locateAsset(name: String): File {
        // Works both in local Gradle and GitHub Actions:
        // projectDir/app/src/main/assets/databases/<name>
        val candidates = listOf(
            File("src/main/assets/databases/$name"),
            File("app/src/main/assets/databases/$name"),
            File("../app/src/main/assets/databases/$name")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate asset $name. Searched: $candidates")
    }

    private fun count(conn: java.sql.Connection, table: String): Int {
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM $table").use { rs ->
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    private fun tableNames(conn: java.sql.Connection): Set<String> {
        val result = mutableSetOf<String>()
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT name FROM sqlite_master WHERE type IN ('table','view')"
            ).use { rs ->
                while (rs.next()) result += rs.getString(1)
            }
        }
        return result
    }
}
