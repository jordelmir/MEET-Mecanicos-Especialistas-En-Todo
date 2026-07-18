package com.elysium369.meet.core.parts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartQuoteRankerTest {
    private val baseQuote = RankablePartQuote(
        id = "q1",
        price = 100.0,
        warrantyDays = 30,
        estimatedDeliveryHours = 24,
        compatibilityConfidence = CompatibilityConfidence.MEDIUM,
        ratingAvg = 4.0,
    )

    @Test
    fun `score weights compatibility at fifty five percent`() {
        val exact = PartQuoteRanker.scoreQuote(
            baseQuote.copy(compatibilityConfidence = CompatibilityConfidence.EXACT),
        )
        val unknown = PartQuoteRanker.scoreQuote(
            baseQuote.copy(compatibilityConfidence = CompatibilityConfidence.UNKNOWN),
        )

        assertEquals(0.55, exact - unknown, 0.01)
    }

    @Test
    fun `ranker sorts by composite score descending`() {
        val ranked = PartQuoteRanker.rankQuotes(
            listOf(
                baseQuote.copy(id = "low", compatibilityConfidence = CompatibilityConfidence.LOW),
                baseQuote.copy(id = "high", compatibilityConfidence = CompatibilityConfidence.HIGH),
                baseQuote.copy(id = "medium", compatibilityConfidence = CompatibilityConfidence.MEDIUM),
            ),
        )

        assertEquals(listOf("high", "medium", "low"), ranked.map { it.id })
    }

    @Test
    fun `ranker tags safest leader and useful alternatives`() {
        val ranked = PartQuoteRanker.rankQuotes(
            listOf(
                baseQuote.copy(
                    id = "best",
                    compatibilityConfidence = CompatibilityConfidence.HIGH,
                    price = 200.0,
                    estimatedDeliveryHours = 24,
                ),
                baseQuote.copy(
                    id = "cheap",
                    compatibilityConfidence = CompatibilityConfidence.MEDIUM,
                    price = 50.0,
                    estimatedDeliveryHours = 24,
                ),
                baseQuote.copy(
                    id = "fast",
                    compatibilityConfidence = CompatibilityConfidence.MEDIUM,
                    price = 180.0,
                    estimatedDeliveryHours = 2,
                ),
            ),
        )

        assertEquals(QuotePrimaryTag.BEST_COMPAT, ranked.first { it.id == "best" }.primaryTag)
        assertEquals(QuotePrimaryTag.CHEAPEST, ranked.first { it.id == "cheap" }.primaryTag)
        assertTrue(ranked.any { it.primaryTag == QuotePrimaryTag.FASTEST })
    }
}
