package com.elysium369.meet.ride.demand

/**
 * Pure domain: Market pricing intelligence based on recent acceptance patterns.
 * NEVER removes user control. OPEN_BID remains supreme — this only recommends.
 */
object RidePricingIntelligence {

    data class MarketRange(
        val lowMinor: Long,
        val highMinor: Long,
        val medianMinor: Long,
        val currency: String = "CRC",
        val sampleSize: Int,
        val demandLevel: RideDemandLevel,
    ) {
        val formattedRange: String
            get() {
                val lowFormatted = formatColones(lowMinor)
                val highFormatted = formatColones(highMinor)
                return "Rango reciente $lowFormatted – $highFormatted"
            }

        val formattedMedian: String
            get() = "Mediana: ${formatColones(medianMinor)}"

        private fun formatColones(minor: Long): String {
            val whole = minor / 100
            val cents = minor % 100
            return if (cents > 0) "₡$whole.${cents.toString().padStart(2, '0')}"
            else "₡$whole"
        }
    }

    /**
     * Calculates recommended market range from recent accepted fares.
     * Returns null if insufficient data (< 3 samples).
     */
    fun calculateMarketRange(
        recentAcceptedFaresMinor: List<Long>,
        demandLevel: RideDemandLevel = RideDemandLevel.NORMAL,
    ): MarketRange? {
        if (recentAcceptedFaresMinor.size < 3) return null

        val sorted = recentAcceptedFaresMinor.sorted()
        val p10Index = (sorted.size * 0.10).toInt().coerceIn(0, sorted.lastIndex)
        val p90Index = (sorted.size * 0.90).toInt().coerceIn(0, sorted.lastIndex)
        val medianIndex = sorted.size / 2

        return MarketRange(
            lowMinor = sorted[p10Index],
            highMinor = sorted[p90Index],
            medianMinor = sorted[medianIndex],
            sampleSize = sorted.size,
            demandLevel = demandLevel,
        )
    }
}
