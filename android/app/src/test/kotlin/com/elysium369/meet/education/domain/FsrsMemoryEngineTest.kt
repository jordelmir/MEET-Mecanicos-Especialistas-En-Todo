package com.elysium369.meet.education.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsMemoryEngineTest {

    @Test
    fun `initial card retrievability starts at 1_0 and decays according to stability`() {
        val now = System.currentTimeMillis()
        val card = FsrsCardState(
            conceptId = "cr_mat1_c_spatial_pos",
            stabilityDays = 2.0,
            difficulty = 5.0,
            lastReviewTimestampMs = now
        )

        // At t = 0, R = 1.0
        val r0 = FsrsMemoryEngine.computeRetrievability(card, now)
        assertEquals(1.0, r0, 0.001)
        assertFalse(FsrsMemoryEngine.isDueForReview(card, now))

        // At t = S (2 days), R should be approximately 0.90
        val twoDaysLater = now + (2L * 24 * 60 * 60 * 1000)
        val rS = FsrsMemoryEngine.computeRetrievability(card, twoDaysLater)
        assertEquals(0.90, rS, 0.02)

        // At t = 10 days, R should have decayed significantly
        val tenDaysLater = now + (10L * 24 * 60 * 60 * 1000)
        val r10 = FsrsMemoryEngine.computeRetrievability(card, tenDaysLater)
        assertTrue("Retrievability after 10 days should decay below 0.70", r10 < 0.70)
        assertTrue(FsrsMemoryEngine.isDueForReview(card, tenDaysLater))
    }

    @Test
    fun `successful review increases stability and reduces forgetting rate`() {
        val now = System.currentTimeMillis()
        val initialCard = FsrsCardState(
            conceptId = "cr_elec9_c_ley_ohm",
            stabilityDays = 1.5,
            difficulty = 5.0,
            lastReviewTimestampMs = now - (1L * 24 * 60 * 60 * 1000) // reviewed 1 day ago
        )

        val updatedCard = FsrsMemoryEngine.review(initialCard, ReviewRating.GOOD, now)

        assertTrue("Stability should increase after a GOOD review", updatedCard.stabilityDays > initialCard.stabilityDays)
        assertEquals(1, updatedCard.repetitionCount)
        assertEquals(0, updatedCard.lapsesCount)
        assertEquals(now, updatedCard.lastReviewTimestampMs)
    }

    @Test
    fun `failed review resets stability and increments lapses`() {
        val now = System.currentTimeMillis()
        val card = FsrsCardState(
            conceptId = "cr_mat8_c_algebra",
            stabilityDays = 10.0,
            difficulty = 4.0,
            lastReviewTimestampMs = now - (5L * 24 * 60 * 60 * 1000),
            repetitionCount = 3,
            lapsesCount = 0
        )

        val failedCard = FsrsMemoryEngine.review(card, ReviewRating.AGAIN, now)

        assertTrue("Stability should plummet after a lapse", failedCard.stabilityDays < 3.0)
        assertEquals(4, failedCard.repetitionCount)
        assertEquals(1, failedCard.lapsesCount)
        assertTrue("Difficulty should increase after failure", failedCard.difficulty > card.difficulty)
    }
}
