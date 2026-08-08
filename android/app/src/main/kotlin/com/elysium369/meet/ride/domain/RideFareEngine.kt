package com.elysium369.meet.ride.domain

import kotlinx.serialization.Serializable

@Serializable
enum class RideFareMode {
    OPEN_BID,
    METERED_TIME_DISTANCE,
}

data class RideFareQuote(
    val mode: RideFareMode,
    val currency: CurrencyCode,
    val estimatedDistanceMeters: Long,
    val estimatedDurationSeconds: Long,
    val distanceRateMinorPerKm: Long,
    val timeRateMinorPerMinute: Long,
    val distanceFareMinor: Long,
    val timeFareMinor: Long,
    val estimatedTotalMinor: Long,
    val rateCardVersion: Long,
    val allowsStopsDuringTrip: Boolean,
)

/**
 * Pure, deterministic fare contract. PostgreSQL repeats this calculation and
 * remains authoritative; Android only renders the same explainable estimate.
 */
object RideFareEngine {
    const val COSTA_RICA_CURRENCY = "CRC"
    const val CRC_DISTANCE_RATE_MINOR_PER_KM = 300L
    const val CRC_TIME_RATE_MINOR_PER_MINUTE = 60L
    const val COSTA_RICA_RATE_CARD_VERSION = 1L

    fun quoteCostaRica(
        distanceMeters: Long,
        durationSeconds: Long,
    ): RideFareQuote {
        require(distanceMeters >= 0) { "Distance cannot be negative" }
        require(durationSeconds >= 0) { "Duration cannot be negative" }

        val distanceNumerator = Math.multiplyExact(
            distanceMeters,
            CRC_DISTANCE_RATE_MINOR_PER_KM,
        )
        val timeNumerator = Math.multiplyExact(
            durationSeconds,
            CRC_TIME_RATE_MINOR_PER_MINUTE,
        )
        val distanceFare = ceilDivide(distanceNumerator, 1_000L)
        val timeFare = ceilDivide(timeNumerator, 60L)
        val total = Math.addExact(distanceFare, timeFare)

        return RideFareQuote(
            mode = RideFareMode.METERED_TIME_DISTANCE,
            currency = CurrencyCode.of(COSTA_RICA_CURRENCY),
            estimatedDistanceMeters = distanceMeters,
            estimatedDurationSeconds = durationSeconds,
            distanceRateMinorPerKm = CRC_DISTANCE_RATE_MINOR_PER_KM,
            timeRateMinorPerMinute = CRC_TIME_RATE_MINOR_PER_MINUTE,
            distanceFareMinor = distanceFare,
            timeFareMinor = timeFare,
            estimatedTotalMinor = total,
            rateCardVersion = COSTA_RICA_RATE_CARD_VERSION,
            allowsStopsDuringTrip = true,
        )
    }

    fun allowsStopsDuringTrip(mode: RideFareMode): Boolean =
        mode == RideFareMode.METERED_TIME_DISTANCE

    fun canChangeStops(
        mode: RideFareMode,
        serverState: String,
    ): Boolean = when (mode) {
        RideFareMode.OPEN_BID -> serverState in setOf("DRAFT", "LOCAL_ONLY")
        RideFareMode.METERED_TIME_DISTANCE -> serverState in setOf(
            "DRAFT",
            "LOCAL_ONLY",
            "SEARCHING",
            "OFFERED",
            "ASSIGNED",
            "DRIVER_EN_ROUTE",
            "ARRIVED",
            "PASSENGER_ONBOARD",
            "IN_PROGRESS",
        )
    }

    private fun ceilDivide(numerator: Long, denominator: Long): Long {
        require(numerator >= 0 && denominator > 0)
        if (numerator == 0L) return 0L
        return Math.addExact(numerator, denominator - 1L) / denominator
    }
}
