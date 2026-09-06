package com.kyronix.swadhyaa.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression test for MahabharataManifest.parbaNoFromLegacyId/byParbaNo —
 * added alongside LegacyMigrationEngine's Mahabharata resolver. Values
 * below (301..318) are copied directly from legacy mahabharata.js's
 * MAHABHARATA_PARBAS literal, not derived from the formula being tested,
 * so this actually catches a wrong offset rather than just restating it.
 */
class MahabharataManifestTest {

    @Test
    fun parbaNoFromLegacyId_matchesAllEighteenLegacyIdsExactly() {
        // id -> expected parba_no, transcribed directly from mahabharata.js
        val legacyIdToParbaNo = mapOf(
            301 to 1, 302 to 2, 303 to 3, 304 to 4, 305 to 5, 306 to 6,
            307 to 7, 308 to 8, 309 to 9, 310 to 10, 311 to 11, 312 to 12,
            313 to 13, 314 to 14, 315 to 15, 316 to 16, 317 to 17, 318 to 18
        )
        legacyIdToParbaNo.forEach { (legacyId, expectedParbaNo) ->
            assertEquals(
                "legacy id $legacyId should map to parba_no $expectedParbaNo",
                expectedParbaNo,
                MahabharataManifest.parbaNoFromLegacyId(legacyId)
            )
        }
    }

    @Test
    fun parbaNoFromLegacyId_returnsNullOutsideKnownRange() {
        assertNull(MahabharataManifest.parbaNoFromLegacyId(300))
        assertNull(MahabharataManifest.parbaNoFromLegacyId(319))
        assertNull(MahabharataManifest.parbaNoFromLegacyId(1)) // a bare parba_no is NOT a valid legacy id
    }

    @Test
    fun byParbaNo_returnsMatchingParbaInfo() {
        val parba = MahabharataManifest.byParbaNo(1)
        assertEquals("আদিপর্ব", parba?.name)
        assertEquals("mahabharata_parba_1.db.gz", parba?.packFile)
    }

    @Test
    fun byParbaNo_returnsNullOutsideRange() {
        assertNull(MahabharataManifest.byParbaNo(0))
        assertNull(MahabharataManifest.byParbaNo(19))
    }
}
