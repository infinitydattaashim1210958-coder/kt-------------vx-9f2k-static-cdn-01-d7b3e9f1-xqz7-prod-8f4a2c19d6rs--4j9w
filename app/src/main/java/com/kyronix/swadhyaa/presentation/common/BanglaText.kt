package com.kyronix.swadhyaa.presentation.common

import com.kyronix.swadhyaa.data.local.entity.ScholarEntity

/**
 * Bangla display names for languages and scholar/bhashya titles.
 *
 * `scholars.language` and `scholars.name` in core.db are stored in English/Latin
 * transliteration (e.g. "Chaturveda Shatakam Bangladesh Agniveertranslatedfrom
 * Achyutanand Saraswatis Book"). We show curated Bangla titles for scholars we know
 * about (SCHOLAR_NAME_BN, keyed by scholar id — stable across app updates), and fall
 * back to a lightly cleaned-up English name for the rest, so nothing is ever blank.
 *
 * TODO: extend SCHOLAR_NAME_BN with more entries as they're translated; this is a
 * living map, not something the app can auto-translate reliably.
 */
object BanglaText {

    private val LANGUAGE_BN: Map<String, String> = mapOf(
        "bengali" to "বাংলা",
        "hindi" to "হিন্দি",
        "english" to "ইংরেজি",
        "gujarati" to "গুজরাটি",
        "marathi" to "মারাঠি",
        "nepali" to "নেপালি",
        "sanskrit" to "সংস্কৃত",
        "tamil" to "তামিল",
        "urdu" to "উর্দু",
        "hinglish" to "হিংলিশ"
    )

    fun languageBn(language: String?): String {
        val key = language?.trim()?.lowercase() ?: return "অন্যান্য"
        return LANGUAGE_BN[key] ?: language
    }

    /**
     * scholar_id -> Bangla title. The four Chaturveda Shatakam packs (one per veda,
     * ids 27–30) are the ones explicitly confirmed; add more as they're translated.
     */
    private val SCHOLAR_NAME_BN: Map<Int, String> = mapOf(
        27 to "চতুর্বেদ শতকম - অচ্যুতানন্দ সরস্বতী (বাংলাদেশ অগ্নিবীর)", // Samaveda
        28 to "চতুর্বেদ শতকম - অচ্যুতানন্দ সরস্বতী (বাংলাদেশ অগ্নিবীর)", // Yajurveda
        29 to "চতুর্বেদ শতকম - অচ্যুতানন্দ সরস্বতী (বাংলাদেশ অগ্নিবীর)", // Rigveda
        30 to "চতুর্বেদ শতকম - অচ্যুতানন্দ সরস্বতী (বাংলাদেশ অগ্নিবীর)"  // Atharvaveda
    )

    /** Best available display name for a scholar/bhashya pack. */
    fun scholarDisplayName(scholar: ScholarEntity): String {
        val id = scholar.id
        if (id != null) SCHOLAR_NAME_BN[id]?.let { return it }
        return cleanupEnglishName(scholar.name)
    }

    /** Inserts spaces before capital letters run together (e.g. "SwamiDayanand" -> "Swami Dayanand"). */
    private fun cleanupEnglishName(raw: String): String {
        val spaced = raw.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        return spaced.trim().replace(Regex("\\s+"), " ")
    }
}
