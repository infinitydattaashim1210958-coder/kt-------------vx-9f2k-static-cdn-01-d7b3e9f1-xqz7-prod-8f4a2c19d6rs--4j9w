package com.kyronix.swadhyaa.data.repository

import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchHit(
    val kind: String,       // veda | ramayana
    val corpusId: Int,
    val itemId: Int,
    val label: String,
    val snippet: String
)

/**
 * Offline search.
 * Uses FTS tables when present (search_index / shlokas_fts);
 * falls back to LIKE on sanskrit_text / sanskrit.
 */
class SearchRepository(
    private val core: CoreDatabase,
    private val ramayana: RamayanaCoreDatabase
) {
    suspend fun search(query: String, limit: Int = 40): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            val hits = mutableListOf<SearchHit>()
            hits += searchVedas(q, limit)
            if (hits.size < limit) {
                hits += searchRamayana(q, limit - hits.size)
            }
            hits
        }

    private fun searchVedas(q: String, limit: Int): List<SearchHit> {
        val db = core.openHelper.readableDatabase
        // Prefer FTS if table exists
        val hasFts = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='search_index'"
        ).use { it.count > 0 }

        return if (hasFts) {
            val fts = escapeFtsQuery(q)
            if (fts.isEmpty()) return emptyList()
            db.query(
                """
                SELECT m.id, m.veda_id, m.level1, m.level2, m.mantra_no,
                       substr(m.sanskrit_text,1,80)
                FROM mantras m
                JOIN search_index si ON si.rowid = m.id
                WHERE search_index MATCH ?
                LIMIT ?
                """.trimIndent(),
                arrayOf(fts, limit.toString())
            ).use { c ->
                val out = mutableListOf<SearchHit>()
                while (c.moveToNext()) {
                    val id = c.getInt(0)
                    val vedaId = c.getInt(1)
                    val l1 = if (c.isNull(2)) null else c.getInt(2)
                    val l2 = if (c.isNull(3)) null else c.getInt(3)
                    val no = if (c.isNull(4)) null else c.getInt(4)
                    val snip = c.getString(5) ?: ""
                    out += SearchHit(
                        kind = "veda",
                        corpusId = vedaId,
                        itemId = id,
                        label = "Veda $vedaId · ${l1 ?: "-"}/${l2 ?: "-"}/${no ?: "-"}",
                        snippet = snip
                    )
                }
                out
            }
        } else {
            db.query(
                """
                SELECT id, veda_id, level1, level2, mantra_no, substr(sanskrit_text,1,80)
                FROM mantras
                WHERE sanskrit_text LIKE ? ESCAPE '\'
                LIMIT ?
                """.trimIndent(),
                arrayOf("%${escapeLike(q)}%", limit.toString())
            ).use { c ->
                val out = mutableListOf<SearchHit>()
                while (c.moveToNext()) {
                    out += SearchHit(
                        kind = "veda",
                        corpusId = c.getInt(1),
                        itemId = c.getInt(0),
                        label = "Veda ${c.getInt(1)} · ${c.getInt(2)}/${c.getInt(3)}/${c.getInt(4)}",
                        snippet = c.getString(5) ?: ""
                    )
                }
                out
            }
        }
    }

    private fun searchRamayana(q: String, limit: Int): List<SearchHit> {
        val db = ramayana.openHelper.readableDatabase
        val hasFts = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='shlokas_fts'"
        ).use { it.count > 0 }

        return if (hasFts) {
            val fts = escapeFtsQuery(q)
            if (fts.isEmpty()) return emptyList()
            db.query(
                """
                SELECT s.id, s.kanda_id, s.sarga_id, substr(s.sanskrit,1,80)
                FROM shlokas s
                JOIN shlokas_fts fts ON fts.rowid = s.id
                WHERE shlokas_fts MATCH ?
                LIMIT ?
                """.trimIndent(),
                arrayOf(fts, limit.toString())
            ).use { c ->
                val out = mutableListOf<SearchHit>()
                while (c.moveToNext()) {
                    out += SearchHit(
                        kind = "ramayana",
                        corpusId = c.getInt(1),
                        itemId = c.getInt(0),
                        label = "Kanda ${c.getInt(1)} · Sarga ${c.getInt(2)}",
                        snippet = c.getString(3) ?: ""
                    )
                }
                out
            }
        } else {
            db.query(
                """
                SELECT id, kanda_id, sarga_id, substr(sanskrit,1,80)
                FROM shlokas WHERE sanskrit LIKE ? ESCAPE '\' LIMIT ?
                """.trimIndent(),
                arrayOf("%${escapeLike(q)}%", limit.toString())
            ).use { c ->
                val out = mutableListOf<SearchHit>()
                while (c.moveToNext()) {
                    out += SearchHit(
                        kind = "ramayana",
                        corpusId = c.getInt(1),
                        itemId = c.getInt(0),
                        label = "Kanda ${c.getInt(1)} · Sarga ${c.getInt(2)}",
                        snippet = c.getString(3) ?: ""
                    )
                }
                out
            }
        }
    }

    companion object {
        /**
         * Builds a crash-safe FTS5 MATCH expression with per-token prefix
         * matching. See RISK_REGISTER.md R10.
         *
         * FTS5 treats the matched string as its own query language —
         * quotes, colons, parentheses, AND/OR/NOT, NEAR() are all
         * significant regardless of parameter binding. The naive
         * `"$token*"` this replaces threw a runtime SQLiteException from a
         * plain search box on an unbalanced quote, a dangling "AND"/"OR",
         * "col:value" syntax, an unclosed paren, or a leading hyphen —
         * empirically confirmed against real SQLite FTS5 before and after
         * this fix, including Bengali/Devanagari input.
         *
         * Each token is quoted as its own literal phrase (embedded quotes
         * doubled) with the `*` prefix operator placed OUTSIDE the quotes
         * — FTS5 permits `"term"*` as a quoted-prefix query. This keeps
         * per-token prefix matching (a UX improvement over legacy's
         * whole-query exact-phrase match — see FEATURE_PARITY_MATRIX.md)
         * while making the expression syntax-error-proof for any input.
         *
         * Returns "" for a query with no real tokens (e.g. all whitespace)
         * — callers must check for that before running MATCH.
         */
        internal fun escapeFtsQuery(query: String): String =
            query.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .joinToString(" ") { token ->
                    "\"${token.replace("\"", "\"\"")}\"*"
                }

        /** Escapes % and _ for a LIKE fallback query using ESCAPE '\'. */
        internal fun escapeLike(query: String): String =
            query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
