package com.kyronix.swadhyaa.data.remote

/**
 * Maps a veda code + mantra reference ID to the correct GitHub release
 * download URL for direct streaming.
 *
 * Audio is served from the CDN repository split across 24 releases
 * (see the full release list in the project docs).
 *
 * URL format:
 *   https://github.com/{OWNER}/{REPO}/releases/download/{TAG}/{REF}.mp3
 *
 * Ref formats by veda:
 *   Rigveda    → "mandala_sukta_mantra"   e.g. "1_2_2"
 *   Samaveda   → sequential integer        e.g. "1001"
 *   Yajurveda  → "chapter_mantra"         e.g. "18_31"
 *   Atharvaveda→ "kanda_sukta_mantra"     e.g. "5_8_9"
 */
object MantraAudioResolver {

    private const val OWNER = "infinitydattaashim1210958-coder"
    private const val REPO  =
        "-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w"
    private const val BASE  = "https://github.com/$OWNER/$REPO/releases/download"

    /**
     * Returns the full HTTPS stream URL or null if this mantra has no audio.
     *
     * @param vedaCode    "rigveda" | "samaveda" | "yajurveda" | "atharvaveda"
     * @param mantraRefId Reference string from MantraEntity.mantraRefId
     */
    fun resolveUrl(vedaCode: String, mantraRefId: String): String? {
        val ref = mantraRefId.trim()
        val tag = resolveTag(vedaCode.trim().lowercase(), ref) ?: return null
        return "$BASE/$tag/$ref.mp3"
    }

    fun resolveTag(vedaCode: String, mantraRefId: String): String? = when (vedaCode) {
        "rigveda"     -> rigvedaTag(mantraRefId)
        "samaveda"    -> samavedaTag(mantraRefId)
        "yajurveda"   -> yajurvedaTag(mantraRefId)
        "atharvaveda" -> atharvavedaTag(mantraRefId)
        else          -> null
    }

    // ── Rigveda ── ref: "mandala_sukta_mantra" ────────────────────────────────
    private fun rigvedaTag(ref: String): String? {
        val p = ref.split("_")
        if (p.size < 2) return null
        val m = p[0].toIntOrNull() ?: return null   // mandala
        val s = p[1].toIntOrNull() ?: return null   // sukta
        return when (m) {
            1  -> when {
                s <= 90  -> "audio-v1.0.0-mandala-01_90_9"
                s <= 189 -> "audio-v1.0.0-mandala-01_189_8-upto"
                else     -> "audio-v1.0.0-mandala-01_190_1"
            }
            2  -> "audio-v1.0.0-mandala-02"
            3  -> "audio-v1.0.0-mandala-03"
            4  -> "audio-v1.0.0-mandala-04"
            5  -> "audio-v1.0.0-mandala-05"
            6  -> "audio-v1.0.0-mandala-06"
            7  -> "audio-v1.0.0-mandala-07"
            8  -> if (s <= 45) "audio-v1.0.0-mandala-08"
                 else          "audio-v1.0.0-mandala-08_46_1"
            9  -> if (s <= 100) "audio-v1.0.0-mandala-09_100_9"
                 else            "audio-v1.0.0-mandala-09_101_1"
            10 -> if (s <= 89) "audio-v1.0.0-mandala-10_89_18"
                 else           "audio-v1.0.0-mandala-10_90_1"
            else -> null
        }
    }

    // ── Samaveda ── ref: sequential integer "1"…"1875" ───────────────────────
    private fun samavedaTag(ref: String): String? {
        val n = ref.trim().toIntOrNull() ?: return null
        return when {
            n in 1..1000    -> "audio-v1.0.0-samved-1000_upto"
            n in 1001..1875 -> "audio-v1.0.0-samved-0875"
            else            -> null
        }
    }

    // ── Yajurveda ── ref: "chapter_mantra" ───────────────────────────────────
    private fun yajurvedaTag(ref: String): String? {
        val p = ref.split("_")
        if (p.size < 2) return null
        val ch = p[0].toIntOrNull() ?: return null
        val mn = p[1].toIntOrNull() ?: return null
        return when {
            ch < 18                  -> "audio-v1.0.0-yajurved-18_30-upto"
            ch == 18 && mn <= 30     -> "audio-v1.0.0-yajurved-18_30-upto"
            else                     -> "audio-v1.0.0-yajurved-from-18_31"
        }
    }

    // ── Atharvaveda ── ref: "kanda_sukta_mantra" ─────────────────────────────
    private fun atharvavedaTag(ref: String): String? {
        val p = ref.split("_")
        if (p.size < 3) return null
        val k = p[0].toIntOrNull() ?: return null
        val s = p[1].toIntOrNull() ?: return null
        val m = p[2].toIntOrNull() ?: return null
        return when {
            tri(k,s,m, 5, 8, 9)  <= 0 -> "audio-v1.0.0-atharvaved-05_8_9-upto"
            tri(k,s,m, 7,99, 1)  <= 0 -> "audio-v1.0.0-atharvaved-07_99_1-upto"
            tri(k,s,m,11, 6,23)  <= 0 -> "audio-v1.0.0-atharvaved-11_6_23-upto"
            tri(k,s,m,15, 6, 6)  <= 0 -> "audio-v1.0.0-atharvaved-15_6_6-upto"
            else                       -> null
        }
    }

    private fun tri(a1:Int,a2:Int,a3:Int, b1:Int,b2:Int,b3:Int): Int =
        if (a1 != b1) a1-b1 else if (a2 != b2) a2-b2 else a3-b3
}
