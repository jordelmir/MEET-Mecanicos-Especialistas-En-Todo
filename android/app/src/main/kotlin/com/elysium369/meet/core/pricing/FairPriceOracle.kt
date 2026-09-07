package com.elysium369.meet.core.pricing

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  F A I R   P R I C E   O R A C L E
 *  ────────────────────────────────────
 *  "If a mechanic quotes $800 for brakes and the network average
 *   is $200, the customer SEES the comparison."
 *
 *  We don't control prices. We INFORM with transparency.
 *  Price transparency kills exploitation.
 *
 *  CONSTITUTIONAL INVARIANTS:
 *  1. NEVER hide price data from the customer
 *  2. NEVER force a provider to change their price
 *  3. ALWAYS show the evidence basis for comparisons
 *  4. ALWAYS declare when data is insufficient
 *  5. Platform fee NEVER exceeds 5% (from PlatformEthicsGuard)
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Price Data Point ───

@Serializable
data class PriceDataPoint(
    val providerId: String,
    val domain: UniversalServiceDomain,
    val serviceDescription: String,
    val serviceCategory: String,
    val priceUsd: Double,
    val locationRegion: String,
    val vehicleBrand: String = "",
    val vehicleModel: String = "",
    val vehicleYear: Int = 0,
    val wasAccepted: Boolean = true,
    val customerRating: Int? = null, // 1-5
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

// ─── Fair Price Analysis ───

enum class PriceFairness(val label: String, val emoji: String) {
    EXCELLENT("Excelente — por debajo del promedio", "🟢"),
    FAIR("Justo — dentro del rango normal", "🟡"),
    HIGH("Alto — por encima del promedio", "🟠"),
    OUTLIER("Muy alto — significativamente sobre el promedio", "🔴"),
    INSUFFICIENT_DATA("Datos insuficientes para comparar", "⚪"),
}

@Serializable
data class FairPriceAnalysis(
    val quotedPriceUsd: Double,
    val fairness: PriceFairness,
    val networkAverageUsd: Double,
    val networkMedianUsd: Double,
    val networkMinUsd: Double,
    val networkMaxUsd: Double,
    val dataPoints: Int,
    val percentile: Int, // Where this quote falls (0-100)
    val suggestedRangeMin: Double,
    val suggestedRangeMax: Double,
    val disclosure: String,
    val disclaimer: String,
) {
    val deviationPercent: Double
        get() = if (networkAverageUsd == 0.0) 0.0
        else ((quotedPriceUsd - networkAverageUsd) / networkAverageUsd * 100)
}

// ─── Fair Price Oracle Engine ───

class FairPriceOracle {

    companion object {
        /** Minimum data points for a reliable comparison */
        const val MIN_DATA_POINTS_RELIABLE = 5
        /** Minimum data points for any comparison */
        const val MIN_DATA_POINTS_ANY = 2
        /** Threshold for OUTLIER detection (times the median) */
        const val OUTLIER_MULTIPLIER = 2.5
        /** Threshold for HIGH detection (times the median) */
        const val HIGH_MULTIPLIER = 1.5
    }

    private val priceData = mutableListOf<PriceDataPoint>()

    fun recordPrice(dataPoint: PriceDataPoint) {
        priceData.add(dataPoint)
    }

    /**
     * Analyzes a quoted price against the collective price data.
     * Returns a transparent, honest comparison.
     */
    fun analyzeQuote(
        quotedPriceUsd: Double,
        serviceCategory: String,
        domain: UniversalServiceDomain,
        region: String? = null,
        vehicleBrand: String? = null,
    ): FairPriceAnalysis {
        val comparable = priceData.filter { dp ->
            dp.serviceCategory.equals(serviceCategory, ignoreCase = true) &&
                dp.domain == domain &&
                (region == null || dp.locationRegion.equals(region, ignoreCase = true)) &&
                (vehicleBrand == null || dp.vehicleBrand.equals(vehicleBrand, ignoreCase = true))
        }

        if (comparable.size < MIN_DATA_POINTS_ANY) {
            return FairPriceAnalysis(
                quotedPriceUsd = quotedPriceUsd,
                fairness = PriceFairness.INSUFFICIENT_DATA,
                networkAverageUsd = 0.0, networkMedianUsd = 0.0,
                networkMinUsd = 0.0, networkMaxUsd = 0.0,
                dataPoints = comparable.size, percentile = 50,
                suggestedRangeMin = 0.0, suggestedRangeMax = 0.0,
                disclosure = "Sin datos suficientes para comparar este servicio.",
                disclaimer = "Dato no capturado — se necesitan al menos " +
                    "$MIN_DATA_POINTS_ANY cotizaciones similares para comparar.",
            )
        }

        val prices = comparable.map { it.priceUsd }.sorted()
        val average = prices.average()
        val median = prices[prices.size / 2]
        val min = prices.first()
        val max = prices.last()

        // Percentile: what % of prices are below this quote
        val belowCount = prices.count { it < quotedPriceUsd }
        val percentile = ((belowCount.toDouble() / prices.size) * 100).toInt()

        // Suggested range: 25th to 75th percentile
        val p25 = prices[(prices.size * 0.25).toInt().coerceAtMost(prices.size - 1)]
        val p75 = prices[(prices.size * 0.75).toInt().coerceAtMost(prices.size - 1)]

        val fairness = when {
            quotedPriceUsd <= median -> PriceFairness.EXCELLENT
            quotedPriceUsd <= median * HIGH_MULTIPLIER -> PriceFairness.FAIR
            quotedPriceUsd <= median * OUTLIER_MULTIPLIER -> PriceFairness.HIGH
            else -> PriceFairness.OUTLIER
        }

        val reliabilityNote = if (comparable.size >= MIN_DATA_POINTS_RELIABLE)
            "Basado en ${comparable.size} cotizaciones verificadas."
        else "Confianza limitada — solo ${comparable.size} cotizaciones disponibles."

        return FairPriceAnalysis(
            quotedPriceUsd = quotedPriceUsd,
            fairness = fairness,
            networkAverageUsd = average,
            networkMedianUsd = median,
            networkMinUsd = min,
            networkMaxUsd = max,
            dataPoints = comparable.size,
            percentile = percentile,
            suggestedRangeMin = p25,
            suggestedRangeMax = p75,
            disclosure = buildDisclosure(quotedPriceUsd, median, fairness, comparable.size),
            disclaimer = reliabilityNote,
        )
    }

    private fun buildDisclosure(
        quoted: Double,
        median: Double,
        fairness: PriceFairness,
        count: Int,
    ): String {
        val deviation = if (median > 0) ((quoted - median) / median * 100).toInt() else 0
        return "${fairness.emoji} ${fairness.label} | " +
            "Tu cotización: \$${String.format("%.0f", quoted)} | " +
            "Mediana de la red: \$${String.format("%.0f", median)} | " +
            "${if (deviation > 0) "+$deviation%" else "$deviation%"} | " +
            "Basado en $count cotizaciones"
    }

    /**
     * Detects potential price gouging.
     * NOT to punish — to INFORM.
     */
    fun detectAnomalies(
        domain: UniversalServiceDomain,
        serviceCategory: String,
    ): List<PriceAnomaly> {
        val comparable = priceData.filter {
            it.serviceCategory.equals(serviceCategory, ignoreCase = true) &&
                it.domain == domain
        }
        if (comparable.size < MIN_DATA_POINTS_RELIABLE) return emptyList()

        val median = comparable.map { it.priceUsd }.sorted()[comparable.size / 2]
        val threshold = median * OUTLIER_MULTIPLIER

        return comparable.filter { it.priceUsd > threshold }.map { dp ->
            PriceAnomaly(
                providerId = dp.providerId,
                quotedPriceUsd = dp.priceUsd,
                networkMedianUsd = median,
                deviationPercent = ((dp.priceUsd - median) / median * 100),
                serviceCategory = serviceCategory,
                note = "Cotización ${String.format("%.0f", (dp.priceUsd - median) / median * 100)}% " +
                    "por encima de la mediana. Esto NO significa fraude — puede haber razones " +
                    "legítimas (complejidad, piezas premium, urgencia).",
            )
        }
    }

    val totalDataPoints: Int get() = priceData.size
}

@Serializable
data class PriceAnomaly(
    val providerId: String,
    val quotedPriceUsd: Double,
    val networkMedianUsd: Double,
    val deviationPercent: Double,
    val serviceCategory: String,
    val note: String,
)
