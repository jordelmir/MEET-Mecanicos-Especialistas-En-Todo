package com.elysium369.meet.core.parts

enum class QuotePrimaryTag {
    BEST_COMPAT, CHEAPEST, FASTEST, TOP_RATED
}

data class RankablePartQuote(
    val id: String,
    val price: Double,
    val warrantyDays: Int,
    val estimatedDeliveryHours: Int,
    val compatibilityConfidence: CompatibilityConfidence,
    val ratingAvg: Double,
)

data class RankedPartQuote(
    val id: String,
    val compositeScore: Double,
    val primaryTag: QuotePrimaryTag?,
)

object PartQuoteRanker {
    private const val WEIGHT_COMPAT = 0.55
    private const val WEIGHT_REPUTATION = 0.20
    private const val WEIGHT_DELIVERY = 0.15
    private const val WEIGHT_WARRANTY = 0.10

    private fun compatScore(confidence: CompatibilityConfidence): Double = when (confidence) {
        CompatibilityConfidence.EXACT -> 1.0
        CompatibilityConfidence.HIGH -> 0.8
        CompatibilityConfidence.MEDIUM -> 0.55
        CompatibilityConfidence.LOW -> 0.25
        CompatibilityConfidence.UNKNOWN -> 0.0
    }

    private fun warrantyScore(days: Int): Double = when {
        days >= 90 -> 1.0
        days >= 30 -> 0.6
        days > 0 -> 0.3
        else -> 0.0
    }

    fun scoreQuote(quote: RankablePartQuote): Double {
        val reputation = (quote.ratingAvg / 5.0).coerceIn(0.0, 1.0)
        val delivery = (1.0 - quote.estimatedDeliveryHours / 168.0).coerceIn(0.0, 1.0)
        return compatScore(quote.compatibilityConfidence) * WEIGHT_COMPAT +
            reputation * WEIGHT_REPUTATION +
            delivery * WEIGHT_DELIVERY +
            warrantyScore(quote.warrantyDays) * WEIGHT_WARRANTY
    }

    fun rankQuotes(candidates: List<RankablePartQuote>): List<RankedPartQuote> {
        if (candidates.isEmpty()) return emptyList()

        val scored = candidates
            .map { quote ->
                RankedScratch(
                    quote = quote,
                    compositeScore = scoreQuote(quote),
                    primaryTag = null,
                )
            }
            .sortedByDescending { it.compositeScore }
            .toMutableList()

        val top = scored.first()
        val threshold = top.compositeScore * 0.5
        fun worthyAlternative(item: RankedScratch): Boolean =
            item.compositeScore >= threshold &&
                item.quote.compatibilityConfidence in setOf(
                    CompatibilityConfidence.EXACT,
                    CompatibilityConfidence.HIGH,
                    CompatibilityConfidence.MEDIUM,
                )

        if (top.quote.compatibilityConfidence == CompatibilityConfidence.EXACT ||
            top.quote.compatibilityConfidence == CompatibilityConfidence.HIGH
        ) {
            scored[0] = scored[0].copy(primaryTag = QuotePrimaryTag.BEST_COMPAT)
        }

        val alternatives = scored.filter(::worthyAlternative)
        if (alternatives.isNotEmpty()) {
            alternatives.minByOrNull { it.quote.price }?.let { cheapest ->
                if (cheapest.quote.id != top.quote.id) tag(scored, cheapest.quote.id, QuotePrimaryTag.CHEAPEST)
            }
            alternatives.minByOrNull { it.quote.estimatedDeliveryHours }?.let { fastest ->
                if (fastest.quote.id != top.quote.id) tagIfEmpty(scored, fastest.quote.id, QuotePrimaryTag.FASTEST)
            }
            alternatives.maxByOrNull { it.quote.ratingAvg }?.let { topRated ->
                if (topRated.quote.id != top.quote.id) tagIfEmpty(scored, topRated.quote.id, QuotePrimaryTag.TOP_RATED)
            }
        }

        return scored.map {
            RankedPartQuote(
                id = it.quote.id,
                compositeScore = it.compositeScore,
                primaryTag = it.primaryTag,
            )
        }
    }

    private fun tag(items: MutableList<RankedScratch>, id: String, tag: QuotePrimaryTag) {
        val index = items.indexOfFirst { it.quote.id == id }
        if (index >= 0) items[index] = items[index].copy(primaryTag = tag)
    }

    private fun tagIfEmpty(items: MutableList<RankedScratch>, id: String, tag: QuotePrimaryTag) {
        val index = items.indexOfFirst { it.quote.id == id }
        if (index >= 0 && items[index].primaryTag == null) {
            items[index] = items[index].copy(primaryTag = tag)
        }
    }

    private data class RankedScratch(
        val quote: RankablePartQuote,
        val compositeScore: Double,
        val primaryTag: QuotePrimaryTag?,
    )
}
