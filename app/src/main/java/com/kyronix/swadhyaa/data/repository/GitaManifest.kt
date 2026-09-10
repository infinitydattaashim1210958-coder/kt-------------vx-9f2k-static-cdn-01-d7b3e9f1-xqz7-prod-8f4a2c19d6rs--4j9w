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
 * Updated 2026-09-10 against a second pass of real-book-title research the
 * maintainer supplied (title, author, sometimes translator) for most of the
 * 18 packs — see [WORK_TITLE_NOTES] below for what changed and what was
 * flagged rather than applied.
 *
 * Pack schema is still an ASSUMPTION, unverified against `gita_bhasya/manifest.json`
 * (not reachable from this environment) — see [GitaBhashyaRepository]'s doc
 * comment. Known gaps carried over from the first pass:
 *   - `gambir_et_db.gz` (no "h") is the real on-disk filename; "গম্ভীরানন্দ" is
 *     the correct spelling, used only in the label.
 *   - `slok_deva_translit_db.gz` is treated as the base verse text (not a
 *     scholar pack) — see [GitaManifest.CORE_TEXT_PACK_FILE].
 *   - `san_et_db.gz` is labeled "স্বামী স্বরূপানন্দ" provisionally; still
 *     unconfirmed against the 21-scholar list.
 */
data class GitaFieldInfo(
    /** Matches a `field_key` value inside the pack (assumption — see doc comment above). */
    val key: String,
    /** Bengali label for this field, shown above its text in the bhashya panel. */
    val label: String
)

data class GitaScholarInfo(
    /** Unique slug — one per (scholar, language) row, e.g. "raman_en", "raman_sa". */
    val id: String,
    /** Bengali display name of the scholar/commentator. */
    val name: String,
    /** Which language tab this row appears under: "english" | "hindi" | "sanskrit". */
    val language: String,
    /** One or more field_key+label pairs read from [packFile], shown together. */
    val fields: List<GitaFieldInfo>,
    /** Exact filename in `gita_bhasya/` on the DB repo (verified against the repo listing). */
    val packFile: String,
    /**
     * The actual published work this row's text comes from, where confirmed by
     * the maintainer's research — shown as a citation under the scholar's name
     * in the bhashya panel. Null where not yet confirmed (unchanged from the
     * generic description in the original mapping table).
     */
    val workTitle: String? = null
)

/** A scholar confirmed as a real, relevant Gita commentator with no pack file yet. */
data class GitaPendingScholar(val name: String, val workTitle: String, val note: String)

object GitaManifest {

    /** Base Gita verse text (Devanagari + transliteration) — see [GitaCoreTextRepository]. */
    const val CORE_TEXT_PACK_FILE = "slok_deva_translit_db.gz"

    // Field-type codes decoded from the repo's own filename convention.
    private val ET = GitaFieldInfo("et", "ইংরেজি অনুবাদ")     // English Translation
    private val HT = GitaFieldInfo("ht", "হিন্দি অনুবাদ")      // Hindi Translation
    private val SC = GitaFieldInfo("sc", "সংস্কৃত ভাষ্য")      // Sanskrit Commentary
    private val HC = GitaFieldInfo("hc", "হিন্দি ভাষ্য")       // Hindi Commentary
    private val EC = GitaFieldInfo("ec", "ইংরেজি ভাষ্য")       // English Commentary

    val SCHOLARS: List<GitaScholarInfo> = listOf(
        GitaScholarInfo(
            "adi_en", "স্বামী আদিদেবানন্দ", "english", listOf(ET), "adi_et_db.gz",
            workTitle = "শ্রী রামানুজ-গীতাভাষ্যের ইংরেজি অনুবাদ (Sri Ramanuja Gita Bhasya, tr. Swami Adidevananda)"
        ),
        GitaScholarInfo("anand_sa", "শ্রী আনন্দগিরি", "sanskrit", listOf(SC), "anand_sc_db.gz"),
        GitaScholarInfo("chinmay_hi", "স্বামী চিন্ময়ানন্দ", "hindi", listOf(HC), "chinmay_hc_db.gz"),
        GitaScholarInfo(
            "dhan_sa", "শ্রী ধনপতি", "sanskrit", listOf(SC), "dhan_sc_db.gz",
            workTitle = "পরমার্থ-প্রপা (Paramartha-Prapa)"
        ),
        // on-disk filename lacks the "h" — see doc comment above
        GitaScholarInfo(
            "gambhir_en", "স্বামী গম্ভীরানন্দ", "english", listOf(ET), "gambir_et_db.gz",
            workTitle = "শঙ্করাচার্যের ভাষ্যের ইংরেজি অনুবাদ (Bhagavad Gita: With the Commentary of Shankaracharya)"
        ),
        GitaScholarInfo(
            "madhav_sa", "শ্রী মাধবাচার্য", "sanskrit", listOf(SC), "madhav_sc_db.gz",
            workTitle = "গীতাতাৎপর্যনির্ণয়ঃ (Gita Tatparya Nirnaya)"
        ),
        // NOT "Shri Krishnayan / Gopal Neelkanth Dandekar" — that's a 20th-c.
        // Marathi novel by a different, unrelated "Neelkanth"; see WORK_TITLE_NOTES.
        GitaScholarInfo("neel_sa", "শ্রী নীলকণ্ঠ", "sanskrit", listOf(SC), "neel_sc_db.gz"),
        GitaScholarInfo(
            "purohit_en", "শ্রী পুরোহিত স্বামী", "english", listOf(ET), "purohit_et_db.gz",
            workTitle = "The Bhagavad Gita: The Philosophy of Life"
        ),
        GitaScholarInfo(
            "puru_sa", "শ্রী পুরুষোত্তমজী", "sanskrit", listOf(SC), "puru_sc_db.gz",
            workTitle = "অমৃততরঙ্গিণী (Amrita-Tarangini)"
        ),
        GitaScholarInfo(
            "raman_en", "শ্রী রামানুজ", "english", listOf(ET), "raman_et_sc_db.gz",
            workTitle = "Bhagavad Gita: Based on Sri Ramanuja's Gitabhashyam, tr. Sri Veeravalli Jagannathanand"
        ),
        GitaScholarInfo(
            "raman_sa", "শ্রী রামানুজ", "sanskrit", listOf(SC), "raman_et_sc_db.gz",
            workTitle = "মূল শ্রীভাষ্যম্ (গীতাভাষ্যম্) — সংস্কৃত"
        ),
        GitaScholarInfo(
            "rams_hi", "স্বামী রামসুখদাস", "hindi", listOf(HT, HC), "rams_ht_hc_db.gz",
            workTitle = "সাধক সঞ্জীবনী (Sadhaka Sanjeevani)"
        ),
        // provisional identity — unconfirmed against the 21-scholar list
        GitaScholarInfo("san_en", "স্বামী স্বরূপানন্দ", "english", listOf(ET), "san_et_db.gz"),
        GitaScholarInfo(
            "sankar_en", "শ্রী শঙ্করাচার্য", "english", listOf(ET), "sankar_et_ht_sc_db.gz",
            workTitle = "The Bhagavad Gita with the Commentary of Sri Sankaracharya, tr. Alladi Mahadeva Sastry"
        ),
        GitaScholarInfo(
            "sankar_hi", "শ্রী শঙ্করাচার্য", "hindi", listOf(HT), "sankar_et_ht_sc_db.gz",
            workTitle = "শঙ্করভাষ্য ও আনন্দগিরি-ব্যাখ্যার হিন্দি অনুবাদ, translator Acharya Keshavlal Shastri"
        ),
        GitaScholarInfo("sankar_sa", "শ্রী শঙ্করাচার্য", "sanskrit", listOf(SC), "sankar_et_ht_sc_db.gz"),
        GitaScholarInfo(
            "siva_en", "স্বামী শিবানন্দ", "english", listOf(ET, EC), "siva_et_ec_db.gz",
            workTitle = "The Bhagavad Gita (Swami Sivananda, Divine Life Society)"
        ),
        GitaScholarInfo("tej_hi", "স্বামী তেজোময়ানন্দ", "hindi", listOf(HT), "tej_ht_db.gz",
            workTitle = "Talks on Shrimad Bhagavad Gita"
        ),
        // NOT "Sri Subodhini" — that's Vallabhacharya's commentary on the
        // Bhagavata Purana, a different text; see WORK_TITLE_NOTES.
        GitaScholarInfo(
            "vallabh_sa", "শ্রী বল্লভাচার্য", "sanskrit", listOf(SC), "vallabh_sc_db.gz",
            workTitle = "তত্ত্বদীপিকা (Tattva-Dipika)"
        ),
        // NOT "Bhagavad Gita As Viewed by Swami Vivekananda" — title/author
        // mismatch, doesn't clearly belong to Vedanta Desika; see WORK_TITLE_NOTES.
        GitaScholarInfo(
            "venkat_sa", "বেদান্তদেশিক (বেঙ্কটনাথ)", "sanskrit", listOf(SC), "venkat_sc_db.gz",
            workTitle = "তাৎপর্যচন্দ্রিকা (Tatparya-Chandrika)"
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
