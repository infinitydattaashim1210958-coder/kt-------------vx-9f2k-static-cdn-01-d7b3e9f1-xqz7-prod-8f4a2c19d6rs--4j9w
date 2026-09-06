package com.kyronix.swadhyaa.presentation.library

import com.kyronix.swadhyaa.data.repository.LibraryRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootnoteMarkerInserterTest {

    @Test
    fun toSuperscriptDigits_mapsEachDigitCorrectly() {
        assertEquals("¹²³", FootnoteMarkerInserter.toSuperscriptDigits("123"))
        assertEquals("⁰⁹", FootnoteMarkerInserter.toSuperscriptDigits("09"))
    }

    @Test
    fun insert_placesMarkerAtPlainDigitMatch() {
        val content = "এই কথা1 সত্য।"
        val refs = listOf(LibraryRef(paraSeq = 1, refSeq = 1, refNumber = "1", refNote = "টীকা এক"))
        val result = FootnoteMarkerInserter.insert(content, refs)

        assertEquals(1, result.markers.size)
        assertTrue(result.footnotes[0].placed)
        assertEquals("টীকা এক", result.footnotes[0].note)
        // the plain digit is preserved at the matched span, not replaced with a glyph
        val (start, end) = result.markers[0]
        assertEquals("1", result.text.substring(start, end))
    }

    @Test
    fun insert_doesNotMatchDigitAdjacentToBengaliDigit() {
        // "১23৪" — a bare "23" search should not match inside/adjacent to
        // Bengali digits due to the negative lookaround, matching legacy.
        val content = "মূল্য ২3৪ টাকা"
        val refs = listOf(LibraryRef(paraSeq = 1, refSeq = 1, refNumber = "3", refNote = "note"))
        val result = FootnoteMarkerInserter.insert(content, refs)
        assertFalse("ref adjacent to Bengali digits should not match", result.footnotes[0].placed)
    }

    @Test
    fun insert_prefersSuperscriptGlyphFormWhenPresent() {
        val content = "কথা³ শেষ"
        val refs = listOf(LibraryRef(paraSeq = 1, refSeq = 1, refNumber = "3", refNote = "note"))
        val result = FootnoteMarkerInserter.insert(content, refs)
        assertTrue(result.footnotes[0].placed)
        // superscript glyph "³" (1 char) was replaced by plain "3" (1 char) — same length here, but content differs
        assertEquals("কথা3 শেষ", result.text)
    }

    @Test
    fun insert_unmatchedRefNumberStillProducesFootnoteButNotPlaced() {
        val content = "কোনো সংখ্যা নেই এখানে"
        val refs = listOf(LibraryRef(paraSeq = 1, refSeq = 1, refNumber = "9", refNote = "note"))
        val result = FootnoteMarkerInserter.insert(content, refs)
        assertEquals(1, result.footnotes.size)
        assertFalse(result.footnotes[0].placed)
        assertEquals(0, result.markers.size)
        assertEquals(content, result.text) // unchanged when nothing matched
    }

    @Test
    fun insert_multipleRefsInOneParagraph() {
        val content = "প্রথম1 এবং দ্বিতীয়2 কথা"
        val refs = listOf(
            LibraryRef(paraSeq = 1, refSeq = 1, refNumber = "1", refNote = "এক"),
            LibraryRef(paraSeq = 1, refSeq = 2, refNumber = "2", refNote = "দুই")
        )
        val result = FootnoteMarkerInserter.insert(content, refs)
        assertEquals(2, result.markers.size)
        assertTrue(result.footnotes.all { it.placed })
    }
}
