package com.kyronix.swadhyaa.data.agent

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.MasterDatabase
import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import com.kyronix.swadhyaa.data.repository.MahabharataManifest
import com.kyronix.swadhyaa.data.repository.SearchRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Evidence retriever for the scripture agent. 100 % on-device and read-only:
 * it NEVER downloads anything (a download would spend the user's data without being asked).
 *
 * Every passage handed to the AI carries a stable id (V123, B5:123:meaning, R77, M1:45, G2.47, L901)
 * and its verbatim database text. The AI may only cite those ids; the page re-checks every citation,
 * quote and number against these exact strings before showing anything (see assets/agent/core.js).
 *
 * Searched: Veda mantras (+ installed bhashya of the top hits), Ramayana shlokas (+ installed bhashya of
 * the top hits), downloaded Mahabharata parba packs, downloaded Gita verse pack, installed Digital Library books.
 *
 * Known limit: the Veda/Ramayana *commentary* tables have no FTS index, so they are not keyword-scanned
 * (a LIKE scan over them would freeze the phone); commentary is attached to the mantras/shlokas that DID match.
 */
class GroundedRetriever(private val context: Context) {

    private data class Passage(
        val id: String, val source: String, val label: String, val text: String, val score: Int = 0
    )

    private val warnings = mutableListOf<String>()

    // ── public API (called from AgentBridge on a WebView bridge thread) ──────────────────────

    /** request: {"terms":[...], "gita_refs":[{"chapter":2,"verse":47}]} → {"passages":[...],"warnings":[...]} */
    @Synchronized
    fun searchJson(requestJson: String): String {
        warnings.clear()
        val req = JSONObject(requestJson)
        val terms = cleanTerms(req.optJSONArray("terms"))
        val found = LinkedHashMap<String, Passage>()

        fun add(list: List<Passage>) { for (p in list) found.putIfAbsent(p.id, p) }

        // Exact references first (they are the most precise evidence)
        req.optJSONArray("gita_refs")?.let { refs ->
            for (i in 0 until minOf(refs.length(), 3)) {
                val r = refs.optJSONObject(i) ?: continue
                guarded("ভগবদ্গীতা") { add(gitaExact(r.optInt("chapter"), r.optInt("verse"))) }
            }
        }

        val vedaHits = mutableListOf<Passage>()
        val ramHits = mutableListOf<Passage>()
        for (t in terms) {
            guarded("বেদ-মন্ত্র") { vedaHits += searchVedas(t, 6) }
            guarded("রামায়ণ") { ramHits += searchRamayana(t, 6) }
            guarded("মহাভারত") { add(searchMahabharata(t, terms)) }
            guarded("ভগবদ্গীতা") { add(searchGita(t, 5)) }
            guarded("লাইব্রেরি") { add(searchLibrary(t, 5, terms)) }
        }
        val topVeda = rank(vedaHits.distinctBy { it.id }, terms).take(8)
        val topRam = rank(ramHits.distinctBy { it.id }, terms).take(8)
        add(topVeda); add(topRam)
        guarded("বেদ-ভাষ্য") { add(attachVedaBhashya(topVeda.take(4), terms)) }
        guarded("রামায়ণ-ভাষ্য") { add(attachRamayanaBhashya(topRam.take(3), terms)) }

        val ranked = rank(found.values.toList(), terms).take(TOTAL_LIMIT)
        val out = JSONObject()
        out.put("passages", JSONArray().apply {
            ranked.forEach { p ->
                put(JSONObject().put("id", p.id).put("source", p.source).put("label", p.label)
                    .put("text", p.text).put("score", p.score))
            }
        })
        out.put("warnings", JSONArray(warnings.toList()))
        return out.toString()
    }

    /** What is installed — lets the AI say "this pack is not on your phone" instead of guessing. */
    fun coverageJson(): String {
        val o = JSONObject()
        o.put("veda_bhashya", tableHasRows("veda_bhashya_contents"))
        o.put("ramayana_bhashya", tableHasRows("ramayana_kanda_bhashya_contents"))
        o.put("mahabharata", JSONArray().apply {
            MahabharataManifest.PARBAS.filter { packFile(it.packFile).exists() }.forEach { put(it.name) }
        })
        o.put("gita", packFile(GITA_PACK).exists())
        o.put("library", JSONArray().apply { libraryTitles().forEach { put(it) } })
        return o.toString()
    }

    // ── sources ──────────────────────────────────────────────────────────────────────────────

    private fun searchVedas(term: String, limit: Int): List<Passage> {
        val db = CoreDatabase.getInstance(context).openHelper.readableDatabase
        val base = """
            SELECT m.id, v.name, m.level1, m.level2, m.level3, m.mantra_no, m.mantra_ref_id,
                   m.sanskrit_text, m.devata, m.rishi, m.chhanda
            FROM mantras m JOIN vedas v ON v.id = m.veda_id
        """.trimIndent()
        val fts = hasTable(db, "search_index")
        return ftsThenLike(
            db, term, limit,
            ftsSql = if (fts) "$base JOIN search_index ON search_index.rowid = m.id WHERE search_index MATCH ? LIMIT ?" else null,
            likeSql = "$base WHERE m.sanskrit_text LIKE ? ESCAPE '\\' LIMIT ?"
        ) { c ->
            val ref = listOf(c.intOrNull(2), c.intOrNull(3), c.intOrNull(4), c.intOrNull(5)).filterNotNull().joinToString(".")
            val meta = listOfNotNull(
                c.strOrNull(8)?.takeIf { it.isNotBlank() }?.let { "দেবতা: $it" },
                c.strOrNull(9)?.takeIf { it.isNotBlank() }?.let { "ঋষি: $it" },
                c.strOrNull(10)?.takeIf { it.isNotBlank() }?.let { "ছন্দ: $it" }
            ).joinToString(" · ")
            val text = (c.strOrNull(7) ?: "") + if (meta.isNotEmpty()) "\n$meta" else ""
            Passage("V${c.getInt(0)}", "veda", "বেদ: ${c.getString(1)} $ref".trim(), window(text, listOf(term)))
        }
    }

    private fun searchRamayana(term: String, limit: Int): List<Passage> {
        val db = RamayanaCoreDatabase.getInstance(context).openHelper.readableDatabase
        val base = """
            SELECT s.id, k.name, sg.chapter, s.sanskrit
            FROM shlokas s JOIN kandas k ON k.id = s.kanda_id JOIN sargas sg ON sg.id = s.sarga_id
        """.trimIndent()
        val fts = hasTable(db, "shlokas_fts")
        return ftsThenLike(
            db, term, limit,
            ftsSql = if (fts) "$base JOIN shlokas_fts ON shlokas_fts.rowid = s.id WHERE shlokas_fts MATCH ? LIMIT ?" else null,
            likeSql = "$base WHERE s.sanskrit LIKE ? ESCAPE '\\' LIMIT ?"
        ) { c ->
            Passage("R${c.getInt(0)}", "ramayana", "রামায়ণ: ${c.getString(1)} · সর্গ ${c.getInt(2)}",
                window(c.getString(3) ?: "", listOf(term)))
        }
    }

    /** Only parba packs already on the phone. Same SQL shapes as MahabharataRepository.searchInParba. */
    private fun searchMahabharata(term: String, allTerms: List<String>): List<Passage> {
        val out = mutableListOf<Passage>()
        for (parba in MahabharataManifest.PARBAS) {
            val f = packFile(parba.packFile)
            if (!f.exists()) continue
            openPack(f)?.use { db ->
                val fts = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='upakhyanas_fts'", null)
                    .use { it.count > 0 }
                var rows = emptyList<Passage>()
                val map = { c: Cursor ->
                    val bishoy = c.strOrNull(2)?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                    Passage("M${parba.parbaNo}:${c.getInt(0)}", "mahabharata",
                        "মহাভারত (কালীপ্রসন্ন সিংহ): ${parba.name} · ${c.getString(3) ?: ""}$bishoy",
                        window(c.getString(4) ?: "", allTerms))
                }
                if (fts) {
                    val q = SearchRepository.escapeFtsQuery(term)
                    if (q.isNotEmpty()) rows = try {
                        db.rawQuery(
                            """SELECT f.upakhyan_id, f.adhyay_id, f.bishoy, a.title, f.content
                               FROM upakhyanas_fts f JOIN adhyayas a ON a.id = f.adhyay_id
                               WHERE upakhyanas_fts MATCH ? LIMIT 3""".trimIndent(), arrayOf(q)
                        ).use { c -> c.mapAll(map) }
                    } catch (_: Exception) { emptyList() }
                }
                if (rows.isEmpty()) {
                    val like = "%${SearchRepository.escapeLike(term)}%"
                    rows = db.rawQuery(
                        """SELECT u.id, u.adhyay_id, u.bishoy, a.title, u.content
                           FROM upakhyanas u JOIN adhyayas a ON a.id = u.adhyay_id
                           WHERE u.content LIKE ? ESCAPE '\' OR u.bishoy LIKE ? ESCAPE '\' LIMIT 3""".trimIndent(),
                        arrayOf(like, like)
                    ).use { c -> c.mapAll(map) }
                }
                out += rows
            }
        }
        return out
    }

    private fun searchGita(term: String, limit: Int): List<Passage> {
        val f = packFile(GITA_PACK)
        if (!f.exists()) return emptyList()
        val like = "%${SearchRepository.escapeLike(term)}%"
        return openPack(f)?.use { db ->
            db.rawQuery(
                """SELECT chapter, verse, slok, transliteration, speaker FROM shlok
                   WHERE slok LIKE ? ESCAPE '\' OR transliteration LIKE ? ESCAPE '\' LIMIT ?""".trimIndent(),
                arrayOf(like, like, limit.toString())
            ).use { c -> c.mapAll(::gitaPassage) }
        } ?: emptyList()
    }

    private fun gitaExact(chapter: Int, verse: Int): List<Passage> {
        val f = packFile(GITA_PACK)
        if (!f.exists()) { warnings += "গীতার শ্লোক-প্যাক ডাউনলোড করা নেই"; return emptyList() }
        return openPack(f)?.use { db ->
            db.rawQuery(
                "SELECT chapter, verse, slok, transliteration, speaker FROM shlok WHERE chapter = ? AND verse = ? LIMIT 1",
                arrayOf(chapter.toString(), verse.toString())
            ).use { c -> c.mapAll(::gitaPassage) }
        } ?: emptyList()
    }

    private fun gitaPassage(c: Cursor): Passage {
        val speaker = c.strOrNull(4)?.takeIf { it.isNotBlank() }?.let { "বক্তা: $it\n" } ?: ""
        val translit = c.strOrNull(3)?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""
        return Passage("G${c.getInt(0)}.${c.getInt(1)}", "gita", "ভগবদ্গীতা ${c.getInt(0)}.${c.getInt(1)}",
            speaker + (c.getString(2) ?: "") + translit)
    }

    private fun searchLibrary(term: String, limit: Int, allTerms: List<String>): List<Passage> {
        val db = MasterDatabase.getInstance(context).openHelper.readableDatabase
        val titles = libraryTitleMap(db)
        if (titles.isEmpty()) return emptyList()
        val fts = hasTable(db, "library_book_paragraphs_fts")
        return ftsThenLike(
            db, term, limit,
            ftsSql = if (fts) "SELECT para_id, book_id, chapter_id, content FROM library_book_paragraphs_fts WHERE library_book_paragraphs_fts MATCH ? LIMIT ?" else null,
            likeSql = "SELECT id, book_id, chapter_id, content FROM library_book_paragraphs WHERE content LIKE ? ESCAPE '\\' LIMIT ?"
        ) { c ->
            val book = c.getString(1)
            Passage("L${c.getString(0)}", "library", "${titles[book] ?: book}", window(c.getString(3) ?: "", allTerms))
        }
    }

    private fun attachVedaBhashya(top: List<Passage>, terms: List<String>): List<Passage> {
        if (top.isEmpty()) return emptyList()
        val master = MasterDatabase.getInstance(context).openHelper.readableDatabase
        val scholarIds = master.query("SELECT DISTINCT scholar_id FROM veda_bhashya_contents").use { c ->
            c.mapAll { it.getInt(0) }
        }
        if (scholarIds.isEmpty()) return emptyList()
        val core = CoreDatabase.getInstance(context).openHelper.readableDatabase
        val names = core.query("SELECT id, name FROM scholars").use { c -> c.mapAll { it.getInt(0) to (it.getString(1) ?: "") }.toMap() }
        val inList = scholarIds.joinToString(",")
        val out = mutableListOf<Passage>()
        for (m in top) {
            val mantraId = m.id.removePrefix("V").toIntOrNull() ?: continue
            master.query(
                "SELECT scholar_id, field_key, value FROM veda_bhashya_contents WHERE mantra_id = ? AND scholar_id IN ($inList) LIMIT 8",
                arrayOf(mantraId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val sid = c.getInt(0); val key = c.getString(1) ?: ""; val v = c.getString(2) ?: continue
                    if (v.isBlank()) continue
                    out += Passage("B$sid:$mantraId:$key", "veda_bhashya",
                        "ভাষ্য — ${names[sid] ?: "পণ্ডিত #$sid"} ($key) · ${m.label}", window(v, terms, 1000))
                }
            }
        }
        return out
    }

    private fun attachRamayanaBhashya(top: List<Passage>, terms: List<String>): List<Passage> {
        if (top.isEmpty() || !tableHasRows("ramayana_kanda_bhashya_contents")) return emptyList()
        val master = MasterDatabase.getInstance(context).openHelper.readableDatabase
        val out = mutableListOf<Passage>()
        for (s in top) {
            val id = s.id.removePrefix("R").toIntOrNull() ?: continue
            master.query(
                "SELECT scholar_id, field_key, value FROM ramayana_kanda_bhashya_contents WHERE shloka_id = ? LIMIT 6",
                arrayOf(id.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val v = c.getString(2) ?: continue
                    if (v.isBlank()) continue
                    out += Passage("RB${c.getInt(0)}:$id:${c.getString(1)}", "ramayana_bhashya",
                        "রামায়ণ-ভাষ্য (পণ্ডিত #${c.getInt(0)}) · ${s.label}", window(v, terms, 1000))
                }
            }
        }
        return out
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private inline fun guarded(name: String, block: () -> Unit) {
        try { block() } catch (e: Exception) {
            val msg = "$name অনুসন্ধান ব্যর্থ: ${e.message ?: e.javaClass.simpleName}"
            if (msg !in warnings) warnings += msg
        }
    }

    private fun cleanTerms(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val seen = HashSet<String>(); val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val t = arr.optString(i, "").trim()
            if (t.length in 2..60 && seen.add(t.lowercase())) out += t
            if (out.size >= MAX_TERMS) break
        }
        return out
    }

    private fun rank(list: List<Passage>, terms: List<String>): List<Passage> {
        val low = terms.map { it.lowercase() }
        return list.map { p ->
            val hay = (p.text + " " + p.label).lowercase()
            p.copy(score = low.count { hay.contains(it) })
        }.sortedWith(compareByDescending<Passage> { it.score }.thenBy { it.source }.thenBy { it.id })
    }

    /** Text window around the first matching term so long passages stay short but still contain the match. */
    private fun window(text: String, terms: List<String>, max: Int = 1200): String {
        if (text.length <= max) return text
        var idx = -1
        for (t in terms) { idx = text.indexOf(t, ignoreCase = true); if (idx >= 0) break }
        val start = if (idx < 0) 0 else (idx - 250).coerceAtLeast(0)
        val end = (start + max).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
    }

    private fun hasTable(db: SupportSQLiteDatabase, name: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE name = ?", arrayOf(name)).use { it.count > 0 }

    private fun tableHasRows(table: String): Boolean = try {
        MasterDatabase.getInstance(context).openHelper.readableDatabase
            .query("SELECT 1 FROM $table LIMIT 1").use { it.count > 0 }
    } catch (_: Exception) { false }

    private fun libraryTitleMap(db: SupportSQLiteDatabase): Map<String, String> {
        val ids = db.query("SELECT DISTINCT book_id FROM library_book_chapters").use { c -> c.mapAll { it.getString(0) } }
        val titles = db.query("SELECT source_id_text, title FROM installed_packages WHERE source_id_text IS NOT NULL")
            .use { c -> c.mapAll { (it.getString(0) ?: "") to (it.getString(1) ?: "") }.toMap() }
        return ids.associateWith { titles[it]?.takeIf { t -> t.isNotBlank() } ?: it }
    }

    private fun libraryTitles(): List<String> = try {
        libraryTitleMap(MasterDatabase.getInstance(context).openHelper.readableDatabase).values.toList()
    } catch (_: Exception) { emptyList() }

    /** FTS first (prefix match, crash-safe escaping); if it finds nothing or fails, LIKE substring match. */
    private fun ftsThenLike(
        db: SupportSQLiteDatabase, term: String, limit: Int, ftsSql: String?, likeSql: String,
        map: (Cursor) -> Passage
    ): List<Passage> {
        if (ftsSql != null) {
            val q = SearchRepository.escapeFtsQuery(term)
            if (q.isNotEmpty()) {
                val r = try { db.query(ftsSql, arrayOf(q, limit.toString())).use { it.mapAll(map) } } catch (_: Exception) { emptyList() }
                if (r.isNotEmpty()) return r
            }
        }
        val like = "%${SearchRepository.escapeLike(term)}%"
        return db.query(likeSql, arrayOf(like, limit.toString())).use { it.mapAll(map) }
    }

    private fun packFile(gzName: String) = File(File(context.filesDir, "packs"), gzName.removeSuffix(".gz"))

    private fun openPack(f: File): SQLiteDatabase? = try {
        SQLiteDatabase.openDatabase(f.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    } catch (e: Exception) { warnings += "${f.name} খোলা যায়নি: ${e.message}"; null }

    private inline fun <T> Cursor.mapAll(f: (Cursor) -> T): List<T> {
        val out = mutableListOf<T>(); while (moveToNext()) out += f(this); return out
    }
    private fun Cursor.strOrNull(i: Int): String? = if (isNull(i)) null else getString(i)
    private fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)

    companion object {
        private const val GITA_PACK = "slok_deva_translit_db.gz"   // = GitaManifest.CORE_TEXT_PACK_FILE
        private const val MAX_TERMS = 12
        private const val TOTAL_LIMIT = 22
    }
}
