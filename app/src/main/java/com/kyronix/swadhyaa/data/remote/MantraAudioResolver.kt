package com.kyronix.swadhyaa.data.remote

/**
 * Maps a veda code + mantra reference ID to the correct GitHub release
 * download URL for direct streaming.
 *
 * Audio is served from the CDN repository split across 23 audio releases.
 *
 * URL format:
 *   https://github.com/{OWNER}/{REPO}/releases/download/{TAG}/{FILENAME}.mp3
 *
 * mantraRefId formats per veda (as stored in core.db):
 *   Rigveda     → "rigved_{M}_{S}_{V}"   e.g. "rigved_1_1_1"
 *   Samaveda    → bare integer string      e.g. "1", "1001"
 *   Yajurveda   → "{CH}_{V}"             e.g. "18_31"
 *   Atharvaveda → "{K}_{S}_{V}"          e.g. "1_1_1"
 *
 * CDN filename conventions (must match exactly):
 *   Rigveda     → "rigved_M_S_V.mp3"
 *   Samaveda    → "samved_mantar_NNNN.mp3"  (4-digit zero-padded)
 *   Yajurveda   → "CH_V.mp3"
 *   Atharvaveda → "K_S_V.mp3"
 */
object MantraAudioResolver {

    private const val OWNER = "infinitydattaashim1210958-coder"
    private const val REPO  =
        "-------------vx-9f2k-static-cdn-01-d7b3e9f1-xqz7-prod-8f4a2c19d6rs--4j9w"
    private const val BASE  = "https://github.com/$OWNER/$REPO/releases/download"

    /**
     * Returns the full HTTPS stream URL, or null if no audio release covers
     * this mantra (e.g. the veda code is unknown, or the ref is out of range).
     *
     * A non-null return value means the URL is structurally valid; the file
     * may still be absent from the CDN for a particular mantra (recordings
     * don't cover every verse). MantraPlayerService handles that via the
     * MediaPlayer what=-38 error path.
     */
    fun resolveUrl(vedaCode: String, mantraRefId: String): String? {
        val code = vedaCode.trim().lowercase()
        val ref  = mantraRefId.trim()
        val tag  = resolveTag(code, ref) ?: return null
        return "$BASE/$tag/${buildFilename(code, ref)}"
    }

    /**
     * Construct the exact filename as uploaded to GitHub Releases.
     *
     * Rigveda:     mantraRefId = "rigved_M_S_V"  → "rigved_M_S_V.mp3"
     *   (DB includes the "rigved_" prefix as part of the ref; guard handles
     *    bare "M_S_V" in case a future DB version drops it.)
     *
     * Samaveda:    mantraRefId = bare int "N"     → "samved_mantar_000N.mp3"
     *   (CDN filenames are always zero-padded to 4 digits.)
     *
     * Yajurveda / Atharvaveda: mantraRefId IS the filename base → "$ref.mp3"
     */
    private fun buildFilename(vedaCode: String, ref: String): String = when (vedaCode) {
        "rigveda" ->
            "${if (ref.startsWith("rigved_")) ref else "rigved_$ref"}.mp3"

        "samaveda" -> {
            val n = ref.toIntOrNull() ?: 0
            "samved_mantar_${n.toString().padStart(4, '0')}.mp3"
        }

        else -> "$ref.mp3"   // yajurveda / atharvaveda: ref IS the filename without .mp3
    }

    /** Resolve only the release tag (exposed for diagnostics/testing). */
    fun resolveTag(vedaCode: String, mantraRefId: String): String? = when (vedaCode) {
        "rigveda"     -> rigvedaTag(mantraRefId)
        "samaveda"    -> samavedaTag(mantraRefId)
        "yajurveda"   -> yajurvedaTag(mantraRefId)
        "atharvaveda" -> atharvavedaTag(mantraRefId)
        else          -> null
    }

    // ── Rigveda ─────────────────────────────────────────────────────────────
    // mantraRefId = "rigved_{mandala}_{sukta}_{mantra}"
    // Strip the "rigved_" prefix before parsing numerics.
    private fun rigvedaTag(ref: String): String? {
        val body = ref.removePrefix("rigved_")   // "1_1_1"
        val p    = body.split("_")
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

    // ── Samaveda ────────────────────────────────────────────────────────────
    // mantraRefId = bare integer string "1"…"1875"
    private fun samavedaTag(ref: String): String? {
        val n = ref.trim().toIntOrNull() ?: return null
        return when {
            n in 1..1000    -> "audio-v1.0.0-samved-1000_upto"
            n in 1001..1875 -> "audio-v1.0.0-samved-0875"
            else            -> null
        }
    }

    // ── Yajurveda ───────────────────────────────────────────────────────────
    // mantraRefId = "{chapter}_{mantra}"
    private fun yajurvedaTag(ref: String): String? {
        val p  = ref.split("_")
        if (p.size < 2) return null
        val ch = p[0].toIntOrNull() ?: return null
        val mn = p[1].toIntOrNull() ?: return null
        return when {
            ch < 18                -> "audio-v1.0.0-yajurved-18_30-upto"
            ch == 18 && mn <= 30   -> "audio-v1.0.0-yajurved-18_30-upto"
            else                   -> "audio-v1.0.0-yajurved-from-18_31"
        }
    }

    // ── Atharvaveda ─────────────────────────────────────────────────────────
    // mantraRefId = "{kanda}_{sukta}_{mantra}"
    private fun atharvavedaTag(ref: String): String? {
        val p = ref.split("_")
        if (p.size < 3) return null
        val k = p[0].toIntOrNull() ?: return null
        val s = p[1].toIntOrNull() ?: return null
        val m = p[2].toIntOrNull() ?: return null
        return when {
            tri(k,s,m,  5, 8, 9) <= 0 -> "audio-v1.0.0-atharvaved-05_8_9-upto"
            tri(k,s,m,  7,99, 1) <= 0 -> "audio-v1.0.0-atharvaved-07_99_1-upto"
            tri(k,s,m, 11, 6,23) <= 0 -> "audio-v1.0.0-atharvaved-11_6_23-upto"
            tri(k,s,m, 15, 6, 6) <= 0 -> "audio-v1.0.0-atharvaved-15_6_6-upto"
            else                       -> null
        }
    }

    /** Ternary comparator: returns negative if (a1,a2,a3) < (b1,b2,b3), 0 if equal, positive otherwise. */
    private fun tri(a1:Int,a2:Int,a3:Int, b1:Int,b2:Int,b3:Int): Int =
        if (a1 != b1) a1-b1 else if (a2 != b2) a2-b2 else a3-b3
}
