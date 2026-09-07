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

    @Test
    fun `certified competence bonus boosts score and tags leader as CERTIFIED_EXPERT_FIT`() {
        val expertQuote = baseQuote.copy(
            id = "expert_mep",
            compatibilityConfidence = CompatibilityConfidence.EXACT,
            certifiedCompetenceBonus = 1.0,
        )
        val standardQuote = baseQuote.copy(
            id = "standard",
            compatibilityConfidence = CompatibilityConfidence.EXACT,
            certifiedCompetenceBonus = 0.0,
        )

        val scoreExpert = PartQuoteRanker.scoreQuote(expertQuote)
        val scoreStandard = PartQuoteRanker.scoreQuote(standardQuote)
        assertEquals(0.05, scoreExpert - scoreStandard, 0.001)

        val ranked = PartQuoteRanker.rankQuotes(listOf(expertQuote, standardQuote))
        assertEquals("expert_mep", ranked.first().id)
        assertEquals(QuotePrimaryTag.CERTIFIED_EXPERT_FIT, ranked.first().primaryTag)
    }

    @Test
    fun `alternative with verified competence receives CERTIFIED_EXPERT_FIT tag`() {
        val leader = baseQuote.copy(
            id = "leader",
            compatibilityConfidence = CompatibilityConfidence.EXACT,
            ratingAvg = 5.0,
            certifiedCompetenceBonus = 0.0,
        )
        val expertAlternative = baseQuote.copy(
            id = "expert_alt",
            compatibilityConfidence = CompatibilityConfidence.HIGH,
            ratingAvg = 4.2,
            certifiedCompetenceBonus = 0.95,
        )

        val ranked = PartQuoteRanker.rankQuotes(listOf(leader, expertAlternative))
        assertEquals(QuotePrimaryTag.BEST_COMPAT, ranked.first { it.id == "leader" }.primaryTag)
        assertEquals(QuotePrimaryTag.CERTIFIED_EXPERT_FIT, ranked.first { it.id == "expert_alt" }.primaryTag)
    }
}

