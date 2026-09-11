package com.kyronix.swadhyaa.data.repository

/**
 * গীতা ভাষ্য (Bhagavad Gita commentary) manifest — one row per (scholar, language)
 * combination found in the `gita_bhasya/` folder of the DB repo
 * (infinitydattaashim1210958-coder/-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w).
 *
 * A single pack file can carry more than one field (e.g. `sankar_et_ht_sc_db.gz`
 * has et/ht/sc = English + Hindi + Sanskrit all in one file). Two fields from the
 * SAME pack become ONE [GitaScholarInfo] row with multiple [fields] when they're
 * the same language (rams_ht_hc: both Hindi → shown together, like Ramayana's
 * pratipada/tat/comment) — but become SEPARATE rows, one per language tab, when
 * the fields span different languages (sankar_et_ht_sc → 3 rows: one under
 * English, one under Hindi, one under Sanskrit), so the reader UI's per-language
 * tabs (mirroring [com.kyronix.swadhyaa.presentation.reader.ReaderActivity]'s
 * Bengali/English/Hindi/… tabs) work the same way they already do for Veda.
 *
 * UPDATE 2026-09-11 — [name] is now the actual published work's title (as
 * researched and supplied by the maintainer), not the scholar's bare personal
 * name, per request: the scholar list should read like a bibliography ("Sri
 * Ramanuja Gita Bhasya, tr. Swami Adidevananda") rather than just a name. The
 * commentator's plain name is kept as a trailing `//` comment on each entry
 * for anyone maintaining this list later. [workTitle] — previously used for
 * this same citation, shown as a subtitle under [name] — is now redundant and
 * left null everywhere; kept as a field in case a future entry wants a SHORT
 * name plus a LONGER citation shown separately again.
 *
 * Schema for `commentary` packs is CONFIRMED (see [GitaBhashyaRepository]'s
 * doc comment): `commentary(id, chapter, verse, verse_id, author, et, ht, ec,
 * hc, sc)` — one wide row per verse, dedicated column per language. The base
 * verse-text pack's schema is CONFIRMED too (see [GitaCoreTextRepository]):
 * `shlok(id, chapter, verse, verse_id, speaker, slok, transliteration)`.
 *
 * Known gaps carried over from earlier passes:
 *   - `gambir_et_db.gz` (no "h") is the real on-disk filename.
 *   - `slok_deva_translit_db.gz` is the base verse text (not a scholar pack).
 *   - `san_et_db.gz` is labeled "স্বামী স্বরূপানন্দ" provisionally; still
 *     unconfirmed against the original 21-scholar list.
 */
data class GitaFieldInfo(
    /** Matches a column name in the `commentary` table: et/ht/ec/hc/sc. */
    val key: String,
    /** Bengali label for this field, shown above its text in the bhashya panel. */
    val label: String
)

data class GitaScholarInfo(
    /** Unique slug — one per (scholar, language) row, e.g. "raman_en", "raman_sa". */
    val id: String,
    /**
     * Display text for this row — the actual published work's title (see
     * class doc comment for why this changed from a bare scholar name), used
     * both in the scholar list and as the bold header in the bhashya panel.
     */
    val name: String,
    /** Which language tab this row appears under: "english" | "hindi" | "sanskrit". */
    val language: String,
    /** One or more field_key+label pairs read from [packFile], shown together. */
    val fields: List<GitaFieldInfo>,
    /** Exact filename in `gita_bhasya/` on the DB repo (verified against the repo listing). */
    val packFile: String,
    /** Currently unused everywhere (see class doc comment) — reserved for a future secondary citation line. */
    val workTitle: String? = null
)

/** A scholar confirmed as a real, relevant Gita commentator with no pack file yet. */
data class GitaPendingScholar(val name: String, val workTitle: String, val note: String)

object GitaManifest {

    /** Base Gita verse text (Devanagari + transliteration) — see [GitaCoreTextRepository]. */
    const val CORE_TEXT_PACK_FILE = "slok_deva_translit_db.gz"

    // Field-type codes decoded from the repo's own filename convention —
    // also the real `commentary` table's column names (confirmed).
    private val ET = GitaFieldInfo("et", "ইংরেজি অনুবাদ")     // English Translation
    private val HT = GitaFieldInfo("ht", "হিন্দি অনুবাদ")      // Hindi Translation
    private val SC = GitaFieldInfo("sc", "সংস্কৃত ভাষ্য")      // Sanskrit Commentary
    private val HC = GitaFieldInfo("hc", "হিন্দি ভাষ্য")       // Hindi Commentary
    private val EC = GitaFieldInfo("ec", "ইংরেজি ভাষ্য")       // English Commentary

    val SCHOLARS: List<GitaScholarInfo> = listOf(
        // ── English ──────────────────────────────────────────────────────
        GitaScholarInfo( // Swami Adidevananda
            "adi_en", "Sri Ramanuja Gita Bhasya, tr. Swami Adidevananda",
            "english", listOf(ET), "adi_et_db.gz"
        ),
        GitaScholarInfo( // Swami Gambhirananda — on-disk filename lacks the "h"
            "gambhir_en", "Bhagavad Gita: With the Commentary of Shankaracharya by Swami Gambhirananda",
            "english", listOf(ET), "gambir_et_db.gz"
        ),
        GitaScholarInfo( // Purohit Swami
            "purohit_en", "The Bhagavad Gita: The Philosophy of Life by Shri Purohit Swami",
            "english", listOf(ET), "purohit_et_db.gz"
        ),
        GitaScholarInfo( // Sri Ramanuja (English side of raman_et_sc_db.gz)
            "raman_en", "Bhagavad Gita: Based on Sri Ramanuja's Gitabhashyam, tr. Sri Veeravalli Jagannathanand",
            "english", listOf(ET), "raman_et_sc_db.gz"
        ),
        GitaScholarInfo( // Adi Shankaracharya (English side of sankar_et_ht_sc_db.gz)
            "sankar_en", "The Bhagavad Gita with the Commentary of Sri Sankaracharya, tr. Alladi Mahadeva Sastry",
            "english", listOf(ET), "sankar_et_ht_sc_db.gz"
        ),
        GitaScholarInfo( // Swami Sivananda
            "siva_en", "The Bhagavad Gita (Swami Sivananda, Divine Life Society)",
            "english", listOf(ET, EC), "siva_et_ec_db.gz"
        ),
        GitaScholarInfo( // provisional identity — unconfirmed against the 21-scholar list
            "san_en", "Srimad Bhagavad Gita (Swami Swarupananda)",
            "english", listOf(ET), "san_et_db.gz"
        ),

        // ── Hindi ────────────────────────────────────────────────────────
        GitaScholarInfo( // Swami Chinmayananda
            "chinmay_hi", "श्रीमद्भगवद्गीता - स्वामी चिन्मयानन्द भाष्य",
            "hindi", listOf(HC), "chinmay_hc_db.gz"
        ),
        GitaScholarInfo( // Swami Ramsukhdas
            "rams_hi", "Sadhaka Sanjeevani (Swami Ramsukhdas)",
            "hindi", listOf(HT, HC), "rams_ht_hc_db.gz"
        ),
        GitaScholarInfo( // Adi Shankaracharya (Hindi side of sankar_et_ht_sc_db.gz) — Keshavlal Shastri's translation
            "sankar_hi", "শঙ্করভাষ্য ও আনন্দগিরি-ব্যাখ্যার হিন্দি অনুবাদ, tr. Acharya Keshavlal Shastri",
            "hindi", listOf(HT), "sankar_et_ht_sc_db.gz"
        ),
        GitaScholarInfo( // Swami Tejomayananda
            "tej_hi", "Talks on Shrimad Bhagavad Gita (Swami Tejomayananda)",
            "hindi", listOf(HT), "tej_ht_db.gz"
        ),

        // ── Sanskrit ─────────────────────────────────────────────────────
        GitaScholarInfo( // Anandagiri
            "anand_sa", "श्रीमद्भगवद्गीता(आनन्दगिरिकृतटीकासहितशाङ्करभाष्यसंवलिता)",
            "sanskrit", listOf(SC), "anand_sc_db.gz"
        ),
        GitaScholarInfo( // Dhanapati Suri
            "dhan_sa", "परमार्थ प्रपा (श्री धनपति)",
            "sanskrit", listOf(SC), "dhan_sc_db.gz"
        ),
        GitaScholarInfo( // Sri Madhvacharya
            "madhav_sa", "गीता तात्पर्यनिर्णयः (श्री माधवाचार्य)",
            "sanskrit", listOf(SC), "madhav_sc_db.gz"
        ),
        GitaScholarInfo( // Neelakantha Chaturdhara — NOT "Shri Krishnayan / Gopal
            // Neelkanth Dandekar", a 20th-c. Marathi novel by an unrelated "Neelkanth".
            "neel_sa", "तत्त्वप्रकाशिका (गीता नीलकण्ठी) - श्री नीलकंठ चतुर्धर",
            "sanskrit", listOf(SC), "neel_sc_db.gz"
        ),
        GitaScholarInfo( // Purushottama
            "puru_sa", "अमृत तरङ्गिनी (श्री पुरुषोत्तमजी)",
            "sanskrit", listOf(SC), "puru_sc_db.gz"
        ),
        GitaScholarInfo( // Sri Ramanuja (Sanskrit side of raman_et_sc_db.gz)
            "raman_sa", "मूल श्रीभाष्यम् (गीता भाष्यम्) - श्री रामानुज",
            "sanskrit", listOf(SC), "raman_et_sc_db.gz"
        ),
        GitaScholarInfo( // Adi Shankaracharya (Sanskrit side of sankar_et_ht_sc_db.gz)
            "sankar_sa", "शाङ्कर गीताभाष्य",
            "sanskrit", listOf(SC), "sankar_et_ht_sc_db.gz"
        ),
        GitaScholarInfo( // Vallabhacharya — NOT "Sri Subodhini", which is his
            // commentary on the Bhagavata Purana, a different text.
            "vallabh_sa", "तत्त्व दीपिका - श्री वल्लभाचार्य",
            "sanskrit", listOf(SC), "vallabh_sc_db.gz"
        ),
        GitaScholarInfo( // Vedanta Desika — NOT "Bhagavad Gita As Viewed by Swami
            // Vivekananda", a title/author mismatch from the earlier research pass.
            "venkat_sa", "तात्पर्य चन्द्रिका (श्री वेदान्त देशिक)",
            "sanskrit", listOf(SC), "venkat_sc_db.gz"
        )
    )

    /**
     * Scholars confirmed as real, relevant Gita commentators by the maintainer's
     * research but with no `.db.gz` pack in the repo yet. Kept here (rather than
     * silently dropped) so the next person adding a pack doesn't have to redo
     * the identification work — add a [GitaScholarInfo] entry above once the
     * corresponding file exists.
     */
    val PENDING_NO_PACK_YET: List<GitaPendingScholar> = listOf(
        GitaPendingScholar(
            "শ্রী অভিনব গুপ্ত", "গীতার্থসংগ্রহ (Gitartha Samgraha)",
            "Confirmed via 'Abhinavagupta's Commentary on the Bhagavad-Gita' — no pack file yet."
        ),
        GitaPendingScholar(
            "শ্রী জয়তীর্থ", "গীতাভাষ্যম্ (with Sri Raghavendratirtha's sub-commentary)",
            "Confirmed via 'Gita Bhashyam With the Commentary of Sri Jayatirtha and Sri Raghavendratirtha' — no pack file yet."
        ),
        GitaPendingScholar(
            "শ্রী এম. সরস্বতী (মধুসূদন সরস্বতী)", "গূঢ়ার্থদীপিকা (Gudhartha Dipika)",
            "Confirmed via 'Madhusudana Saraswati: The Bhagavad-Gita with the Annotation Gudhartha Dipika' — no pack file yet."
        ),
        GitaPendingScholar(
            "শ্রী শ্রীধর স্বামী", "শ্রীধরী টীকা (tr. Swami Vireswarananda)",
            "Confirmed via 'Swami Vireswarananda - Srimad Bhagavad Gita - Sridhara Swami Gloss' — no pack file yet."
        )
    )

    /** Still entirely unidentified — no pack, no confirmed work title either. */
    val STILL_UNIDENTIFIED: List<String> = listOf("ড. এস. শঙ্করনারায়ণ")

    fun byId(id: String): GitaScholarInfo? = SCHOLARS.find { it.id == id }

    /** Rows for one language tab, in a stable reading order. */
    fun byLanguage(language: String): List<GitaScholarInfo> =
        SCHOLARS.filter { it.language == language }

    val AVAILABLE_LANGUAGES: List<String> = SCHOLARS.map { it.language }.distinct()
}
