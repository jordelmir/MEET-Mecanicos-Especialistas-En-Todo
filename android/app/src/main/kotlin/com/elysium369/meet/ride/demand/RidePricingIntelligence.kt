package com.elysium369.meet.ride.demand

/**
 * Pure domain: Market pricing intelligence based on recent acceptance patterns.
 * NEVER removes user control. OPEN_BID remains supreme — this only recommends.
 *
 * Anti-poverty invariant: recommendedFare ≥ sustainableFloorMinor.
 * The sustainable floor is the minimum fare at which a driver covers vehicle TCO
 * plus a livable hourly rate. Without this floor, historical percentiles spiral
 * downward as desperate drivers accept ever-lower offers.
 */
object RidePricingIntelligence {

    data class MarketRange(
        val lowMinor: Long,
        val highMinor: Long,
        val medianMinor: Long,
        val recommendedMinor: Long,
        val currency: String = "CRC",
        val sampleSize: Int,
        val demandLevel: RideDemandLevel,
        val sustainableFloorApplied: Boolean,
    ) {
        val formattedRange: String
            get() {
                val lowFormatted = formatColones(lowMinor)
                val highFormatted = formatColones(highMinor)
                return "Rango reciente $lowFormatted – $highFormatted"
            }

        val formattedMedian: String
            get() = "Mediana: ${formatColones(medianMinor)}"

        val formattedRecommended: String
            get() = "Recomendado: ${formatColones(recommendedMinor)}"

        private fun formatColones(minor: Long): String {
            return com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(minor, currency)
        }
    }

    /**
     * Calculates recommended market range from recent accepted fares.
     * Returns null if insufficient data (< 3 samples).
     *
     * @param sustainableFloorMinor The minimum economically viable fare for this
     *   route, considering vehicle TCO and driver livable rate. When non-null,
     *   the recommended fare will never fall below this value. Pass null to
     *   preserve legacy behavior (pure historical percentiles).
     */
    fun calculateMarketRange(
        recentAcceptedFaresMinor: List<Long>,
        demandLevel: RideDemandLevel = RideDemandLevel.NORMAL,
        sustainableFloorMinor: Long? = null,
    ): MarketRange? {
        if (recentAcceptedFaresMinor.size < 3) return null

        val sorted = recentAcceptedFaresMinor.sorted()
        val p10Index = (sorted.size * 0.10).toInt().coerceIn(0, sorted.lastIndex)
        val p90Index = (sorted.size * 0.90).toInt().coerceIn(0, sorted.lastIndex)
        val medianIndex = sorted.size / 2

        val rawLow = sorted[p10Index]
        val rawMedian = sorted[medianIndex]
        val rawHigh = sorted[p90Index]

        // Anti-poverty: recommended fare must be at least the sustainable floor.
        val floor = sustainableFloorMinor?.coerceAtLeast(0L)
        val floorApplied = floor != null && rawMedian < floor
        val recommended = if (floor != null) maxOf(rawMedian, floor) else rawMedian

        return MarketRange(
            lowMinor = if (floor != null) maxOf(rawLow, floor) else rawLow,
            highMinor = rawHigh,
            medianMinor = rawMedian,
            recommendedMinor = recommended,
            sampleSize = sorted.size,
            demandLevel = demandLevel,
            sustainableFloorApplied = floorApplied,
        )
    }
}
