package com.kyronix.swadhyaa.presentation.library

import com.kyronix.swadhyaa.data.repository.LibraryRef

/**
 * Port of legacy app.js's renderParagraphWithRefs/toSuperscriptDigits
 * (verified against source, including a correction made while writing
 * this: legacy's marker is the PLAIN ref_number wrapped in an HTML <sup>
 * tag — CSS renders the superscript effect — not a Unicode superscript
 * glyph substituted into the text. This port inserts the plain digit and
 * returns its position so the caller can apply a real SuperscriptSpan,
 * which is the faithful Android equivalent of <sup>, rather than
 * fabricating a Unicode-glyph-based marker legacy doesn't actually use.)
 *
 * Matching order — verified byte-for-byte against source: try the
 * superscript-glyph form of ref_number first (source content may already
 * embed one, an unambiguous marker), then fall back to the plain digit
 * with a Bengali-digit negative lookaround so a ref number doesn't
 * accidentally merge with an adjacent Bengali digit in body text.
 *
 * Legacy also makes each marker clickable (scrolls to the footnote,
 * random per-render DOM anchor id) — that's a click-to-scroll UI
 * interaction with no direct equivalent in a plain Android TextView/
 * ScrollView without significantly more span/anchor machinery. This port
 * keeps marker placement and footnote numbering (what the reader
 * actually sees) and omits click-to-scroll as an honest scope reduction,
 * flagged here rather than silently dropped.
 */
object FootnoteMarkerInserter {

    private val superscriptDigits = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹'
    )

    fun toSuperscriptDigits(numStr: String): String = numStr.map { superscriptDigits[it] ?: it }.joinToString("")

    data class Footnote(val num: String, val note: String, val placed: Boolean)

    /** A footnote marker to render as superscript at [start] until [end] (exclusive) in the returned text. */
    data class MarkerSpan(val start: Int, val end: Int)

    data class Result(val text: String, val markers: List<MarkerSpan>, val footnotes: List<Footnote>)

    /** refs should already be ordered by ref_seq (MasterDao's getLibraryRefs does this). */
    fun insert(content: String, refs: List<LibraryRef>): Result {
        var text = content
        val markers = mutableListOf<MarkerSpan>()
        val footnotes = mutableListOf<Footnote>()

        refs.forEach { r ->
            val refNumber = r.refNumber
            var placed = false
            if (!refNumber.isNullOrEmpty()) {
                val superscriptForm = toSuperscriptDigits(refNumber)
                var matchIndex = text.indexOf(superscriptForm)
                var matchLength = superscriptForm.length

                if (matchIndex < 0) {
                    val escaped = Regex.escape(refNumber)
                    val plainPattern = Regex("(?<![০-৯])$escaped(?![০-৯])")
                    val m = plainPattern.find(text)
                    if (m != null) {
                        matchIndex = m.range.first
                        matchLength = m.value.length
                    }
                }

                if (matchIndex >= 0) {
                    // Replace whatever matched (plain digit or an
                    // already-superscript-glyph form) with the plain
                    // digit text, then remember its span so the caller
                    // can apply a SuperscriptSpan there — this is what
                    // makes it render small-and-raised like legacy's
                    // CSS-styled <sup>, without baking a Unicode glyph
                    // into the stored/displayed string.
                    text = text.substring(0, matchIndex) + refNumber + text.substring(matchIndex + matchLength)
                    markers += MarkerSpan(matchIndex, matchIndex + refNumber.length)
                    placed = true
                }
            }
            footnotes += Footnote(num = refNumber ?: "?", note = r.refNote ?: "", placed = placed)
        }

        return Result(text, markers, footnotes)
    }
}
