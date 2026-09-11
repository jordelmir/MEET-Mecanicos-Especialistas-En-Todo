package com.elysium369.meet.ride.farecompare

import com.elysium369.meet.ride.domain.RideFareMode
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  R I D E   F A R E   C O M P A R A T O R   E N G I N E
 *  ──────────────────────────────────────────────────────────
 *  Honest, transparent fare comparison for passengers.
 *
 *  "No te vamos a esconder información. Te mostramos TODO
 *   para que decidas con datos, no con urgencia."
 *
 *  Shows:
 *  ┌─────────────────────────────────────────────┐
 *  │  ⏱️ Taxímetro:       ₡4,500 (fijo)         │
 *  │  💬 Oferta libre:    ₡3,800 — ₡5,200       │
 *  │  📊 Promedio ruta:   ₡4,200                 │
 *  │  🔥 Demanda actual:  Alta (+15%)            │
 *  │  💡 Recomendación:   Taxímetro (más barato) │
 *  └─────────────────────────────────────────────┘
 *
 *  Anti-manipulation:
 *  - Never hides the cheaper option
 *  - Shows historical average for context
 *  - Demand surge is capped and transparent
 *  - Recommendation is ALWAYS honest
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Fare Estimate ───

@Serializable
data class FareEstimate(
    val fareMode: RideFareMode,
    val estimatedAmount: Long,
    val minAmount: Long? = null,        // For OPEN_BID range
    val maxAmount: Long? = null,        // For OPEN_BID range
    val currency: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val surchargePercent: Double = 0.0, // Demand surge
    val surchargeReason: String? = null,
    val breakdown: FareBreakdown? = null,
) {
    val formattedAmount: String
        get() = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(estimatedAmount, currency)

    val formattedRange: String?
        get() = if (minAmount != null && maxAmount != null) {
            val low = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(minAmount, currency)
            val high = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(maxAmount, currency)
            "$low — $high"
        } else null

    val hasSurcharge: Boolean get() = surchargePercent > 0
}

@Serializable
data class FareBreakdown(
    val baseFare: Long,
    val distanceFare: Long,
    val timeFare: Long,
    val surcharge: Long = 0,
)

// ─── Route History ───

@Serializable
data class RouteHistoryStats(
    val routeKey: String,         // "pickup_hash|dropoff_hash"
    val averageFare: Long,
    val medianFare: Long,
    val minFare: Long,
    val maxFare: Long,
    val sampleCount: Int,
    val lastTripEpochMs: Long,
    val averageDurationMin: Int,
    val averageDistanceKm: Double,
    val currency: String = "CRC",
) {
    val formattedAverage: String
        get() = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(averageFare, currency)

    val isReliable: Boolean get() = sampleCount >= 5
}

// ─── Demand Level ───

enum class DemandLevel {
    LOW,        // Below average demand
    NORMAL,     // Normal demand
    HIGH,       // Above average (+10-20%)
    VERY_HIGH,  // Surge (+20-40%)
    EXTREME,    // Peak demand (+40%+ capped)
}

@Serializable
data class DemandInfo(
    val level: DemandLevel,
    val surchargePercent: Double,
    val activeDrivers: Int,
    val activeRequests: Int,
    val message: String,
) {
    val supplyDemandRatio: Double
        get() = if (activeRequests > 0) activeDrivers.toDouble() / activeRequests else 1.0
}

val DemandLevel.displayLabel: String
    get() = when (this) {
        DemandLevel.LOW -> "Baja demanda"
        DemandLevel.NORMAL -> "Demanda normal"
        DemandLevel.HIGH -> "Alta demanda"
        DemandLevel.VERY_HIGH -> "Demanda muy alta"
        DemandLevel.EXTREME -> "Demanda extrema"
    }

val DemandLevel.emoji: String
    get() = when (this) {
        DemandLevel.LOW -> "🟢"
        DemandLevel.NORMAL -> "🔵"
        DemandLevel.HIGH -> "🟡"
        DemandLevel.VERY_HIGH -> "🟠"
        DemandLevel.EXTREME -> "🔴"
    }

// ─── Comparison Result ───

data class FareComparison(
    val meteredEstimate: FareEstimate,
    val openBidEstimate: FareEstimate,
    val routeHistory: RouteHistoryStats?,
    val demand: DemandInfo,
    val recommendation: FareRecommendation,
    val savingsIfRecommended: Long,
) {
    val comparisonSummary: String
        get() = buildString {
            appendLine("━━━ Comparación de Tarifas ━━━")
            appendLine("⏱️ Taxímetro: ${meteredEstimate.formattedAmount}")
            appendLine("💬 Oferta libre: ${openBidEstimate.formattedRange ?: openBidEstimate.formattedAmount}")
            routeHistory?.let {
                if (it.isReliable) appendLine("📊 Promedio ruta: ${it.formattedAverage} (${it.sampleCount} viajes)")
            }
            appendLine("${demand.level.emoji} Demanda: ${demand.level.displayLabel}")
            if (demand.surchargePercent > 0) appendLine("   Recargo: +${demand.surchargePercent.toInt()}%")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💡 ${recommendation.message}")
            if (savingsIfRecommended > 0) {
                appendLine("💰 Ahorro estimado: ₡$savingsIfRecommended")
            }
        }
}

data class FareRecommendation(
    val recommendedMode: RideFareMode,
    val confidence: Double,    // 0.0 - 1.0
    val reason: RecommendationReason,
    val message: String,
)

enum class RecommendationReason {
    METERED_CHEAPER,           // Taxímetro is cheaper
    OPEN_BID_CHEAPER,          // Negotiation likely cheaper
    HIGH_DEMAND_USE_METERED,   // Surge pricing, metered is safer
    LOW_DEMAND_NEGOTIATE,      // Low demand, can negotiate down
    SHORT_TRIP_METERED,        // Short trips favor metered
    LONG_TRIP_NEGOTIATE,       // Long trips favor negotiation
    HISTORICAL_METERED_WINS,   // History shows metered is better here
    HISTORICAL_BID_WINS,       // History shows bids are better here
    NO_DATA_METERED_SAFER,     // No history, metered is safer bet
}

// ─── Engine ───

class RideFareComparatorEngine {

    companion object {
        const val MAX_SURGE_PERCENT = 50.0    // Cap surge at 50%
        const val SHORT_TRIP_KM = 5.0
        const val LONG_TRIP_KM = 20.0
        const val BID_VARIANCE_PERCENT = 15.0  // ±15% for bid range
    }

    private val routeHistory = mutableMapOf<String, MutableList<Long>>()
    private val routeDurations = mutableMapOf<String, MutableList<Int>>()
    private val routeDistances = mutableMapOf<String, MutableList<Double>>()

    // ─── Record Historical Fare ───

    fun recordFare(
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double,
        fare: Long, durationMin: Int, distanceKm: Double,
    ) {
        val key = routeKey(pickupLat, pickupLng, dropoffLat, dropoffLng)
        routeHistory.getOrPut(key) { mutableListOf() }.add(fare)
        routeDurations.getOrPut(key) { mutableListOf() }.add(durationMin)
        routeDistances.getOrPut(key) { mutableListOf() }.add(distanceKm)
    }

    // ─── Get Route Stats ───

    fun getRouteStats(
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double,
    ): RouteHistoryStats? {
        val key = routeKey(pickupLat, pickupLng, dropoffLat, dropoffLng)
        val fares = routeHistory[key] ?: return null
        if (fares.isEmpty()) return null

        val sorted = fares.sorted()
        return RouteHistoryStats(
            routeKey = key,
            averageFare = fares.average().toLong(),
            medianFare = sorted[sorted.size / 2],
            minFare = sorted.first(),
            maxFare = sorted.last(),
            sampleCount = fares.size,
            lastTripEpochMs = System.currentTimeMillis(),
            averageDurationMin = routeDurations[key]?.average()?.toInt() ?: 0,
            averageDistanceKm = routeDistances[key]?.average() ?: 0.0,
        )
    }

    // ─── Compute Demand ───

    fun computeDemand(activeDrivers: Int, activeRequests: Int): DemandInfo {
        val ratio = if (activeRequests > 0) activeDrivers.toDouble() / activeRequests else 2.0

        val (level, surge) = when {
            ratio >= 1.5 -> DemandLevel.LOW to 0.0
            ratio >= 1.0 -> DemandLevel.NORMAL to 0.0
            ratio >= 0.7 -> DemandLevel.HIGH to 15.0
            ratio >= 0.4 -> DemandLevel.VERY_HIGH to 30.0
            else -> DemandLevel.EXTREME to MAX_SURGE_PERCENT
        }

        val message = when (level) {
            DemandLevel.LOW -> "Hay más conductores que pasajeros. Buen momento para negociar."
            DemandLevel.NORMAL -> "Oferta y demanda equilibradas."
            DemandLevel.HIGH -> "Demanda elevada. Puede haber recargo de +${surge.toInt()}%."
            DemandLevel.VERY_HIGH -> "Muchos pasajeros buscando. Recargo de +${surge.toInt()}%."
            DemandLevel.EXTREME -> "Demanda extrema. Recargo máximo de +${surge.toInt()}% (tope)."
        }

        return DemandInfo(level, surge, activeDrivers, activeRequests, message)
    }

    // ─── Full Comparison ───

    fun compare(
        distanceKm: Double,
        durationMinutes: Int,
        baseFare: Long,
        distanceRate: Long,    // per km
        timeRate: Long,        // per minute
        currency: String = "CRC",
        activeDrivers: Int = 10,
        activeRequests: Int = 8,
        pickupLat: Double = 0.0,
        pickupLng: Double = 0.0,
        dropoffLat: Double = 0.0,
        dropoffLng: Double = 0.0,
    ): FareComparison {
        val demand = computeDemand(activeDrivers, activeRequests)
        val history = getRouteStats(pickupLat, pickupLng, dropoffLat, dropoffLng)

        // Metered fare: deterministic
        val distanceCost = (distanceKm * distanceRate).toLong()
        val timeCost = (durationMinutes * timeRate).toLong()
        val meteredBase = baseFare + distanceCost + timeCost
        val meteredSurcharge = (meteredBase * demand.surchargePercent / 100).toLong()
        val meteredTotal = meteredBase + meteredSurcharge

        val meteredEstimate = FareEstimate(
            fareMode = RideFareMode.METERED_TIME_DISTANCE,
            estimatedAmount = meteredTotal,
            currency = currency,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            surchargePercent = demand.surchargePercent,
            surchargeReason = if (demand.surchargePercent > 0) "Demanda ${demand.level.displayLabel.lowercase()}" else null,
            breakdown = FareBreakdown(baseFare, distanceCost, timeCost, meteredSurcharge),
        )

        // Open bid: range estimate
        val bidBase = if (history != null && history.isReliable) {
            history.averageFare
        } else {
            meteredBase  // Use metered as base if no history
        }
        val bidVariance = (bidBase * BID_VARIANCE_PERCENT / 100).toLong()
        val bidMin = (bidBase - bidVariance).coerceAtLeast(baseFare) // Never below base
        val bidMax = bidBase + bidVariance + (bidBase * demand.surchargePercent / 100).toLong()

        val openBidEstimate = FareEstimate(
            fareMode = RideFareMode.OPEN_BID,
            estimatedAmount = bidBase,
            currency = currency,
            minAmount = bidMin,
            maxAmount = bidMax,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            surchargePercent = demand.surchargePercent,
        )

        // Recommendation
        val recommendation = computeRecommendation(
            meteredTotal, bidBase, bidMin, bidMax, demand, distanceKm, history,
        )

        val savings = when (recommendation.recommendedMode) {
            RideFareMode.METERED_TIME_DISTANCE -> (bidBase - meteredTotal).coerceAtLeast(0)
            RideFareMode.OPEN_BID -> (meteredTotal - bidBase).coerceAtLeast(0)
        }

        return FareComparison(
            meteredEstimate = meteredEstimate,
            openBidEstimate = openBidEstimate,
            routeHistory = history,
            demand = demand,
            recommendation = recommendation,
            savingsIfRecommended = savings,
        )
    }

    // ─── Internal ───

    private fun computeRecommendation(
        meteredTotal: Long,
        bidBase: Long,
        bidMin: Long,
        bidMax: Long,
        demand: DemandInfo,
        distanceKm: Double,
        history: RouteHistoryStats?,
    ): FareRecommendation {
        // High demand → metered is safer (surge is capped, bids go wild)
        if (demand.level in listOf(DemandLevel.VERY_HIGH, DemandLevel.EXTREME)) {
            return FareRecommendation(
                RideFareMode.METERED_TIME_DISTANCE, 0.85,
                RecommendationReason.HIGH_DEMAND_USE_METERED,
                "Con demanda ${demand.level.displayLabel.lowercase()}, el taxímetro te protege del sobreprecio.",
            )
        }

        // Low demand → negotiate
        if (demand.level == DemandLevel.LOW && bidMin < meteredTotal) {
            return FareRecommendation(
                RideFareMode.OPEN_BID, 0.75,
                RecommendationReason.LOW_DEMAND_NEGOTIATE,
                "Demanda baja. Puedes negociar un mejor precio (~₡$bidMin).",
            )
        }

        // Short trip → metered is usually better
        if (distanceKm < SHORT_TRIP_KM) {
            return FareRecommendation(
                RideFareMode.METERED_TIME_DISTANCE, 0.70,
                RecommendationReason.SHORT_TRIP_METERED,
                "Viaje corto. El taxímetro suele ser más exacto.",
            )
        }

        // Long trip → negotiation can save
        if (distanceKm > LONG_TRIP_KM && bidBase < meteredTotal) {
            return FareRecommendation(
                RideFareMode.OPEN_BID, 0.70,
                RecommendationReason.LONG_TRIP_NEGOTIATE,
                "Viaje largo. Negociar puede ahorrarte ~₡${meteredTotal - bidBase}.",
            )
        }

        // Historical data
        if (history != null && history.isReliable) {
            return if (history.averageFare < meteredTotal) {
                FareRecommendation(
                    RideFareMode.OPEN_BID, 0.80,
                    RecommendationReason.HISTORICAL_BID_WINS,
                    "En esta ruta, la oferta libre suele ser más barata (promedio: ${history.formattedAverage}).",
                )
            } else {
                FareRecommendation(
                    RideFareMode.METERED_TIME_DISTANCE, 0.80,
                    RecommendationReason.HISTORICAL_METERED_WINS,
                    "En esta ruta, el taxímetro suele ser mejor (promedio ruta: ${history.formattedAverage}).",
                )
            }
        }

        // Default: metered is safest if no data
        return if (meteredTotal <= bidBase) {
            FareRecommendation(
                RideFareMode.METERED_TIME_DISTANCE, 0.60,
                RecommendationReason.METERED_CHEAPER,
                "El taxímetro es más barato en esta estimación.",
            )
        } else {
            FareRecommendation(
                RideFareMode.OPEN_BID, 0.55,
                RecommendationReason.OPEN_BID_CHEAPER,
                "La oferta libre podría ser más económica.",
            )
        }
    }

    private fun routeKey(lat1: Double, lng1: Double, lat2: Double, lng2: Double): String {
        // Round to 3 decimal places (~111m precision) for route grouping
        val p = "%.3f,%.3f".format(lat1, lng1)
        val d = "%.3f,%.3f".format(lat2, lng2)
        return "$p|$d"
    }

    val totalRoutes: Int get() = routeHistory.size
}
