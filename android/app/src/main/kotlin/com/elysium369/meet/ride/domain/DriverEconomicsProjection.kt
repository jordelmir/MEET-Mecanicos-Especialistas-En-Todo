package com.elysium369.meet.ride.domain

import kotlin.math.roundToLong

/**
 * Authoritative economic model for driver earnings transparency and viability.
 * Protects drivers from hidden costs (deadhead travel, fuel consumption, platform take).
 *
 * Invariant: Net earnings = Gross Fare - Platform Fee - Estimated Fuel Cost.
 * Platform fee is strictly 500 basis points (5.0%) for CRC GAM.
 */
data class DriverEconomicsProjection(
    val grossFareMinor: Long,
    val platformFeeMinor: Long,
    val grossDriverPayoutMinor: Long,
    val tripDistanceMeters: Long,
    val pickupDeadheadDistanceMeters: Long,
    val totalDistanceMeters: Long,
    val tripDurationSeconds: Long,
    val pickupDeadheadDurationSeconds: Long,
    val totalDurationSeconds: Long,
    val estimatedFuelExpenseMinor: Long,
    val projectedNetEarningsMinor: Long,
    val projectedNetPerHourMinor: Long,
    val currency: String = "CRC",
    val isViable: Boolean,
) {
    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0
    val totalDurationHours: Double get() = totalDurationSeconds / 3600.0

    val formattedNetEarnings: String
        get() = RideTrackingTruthPolicy.formatFare(projectedNetEarningsMinor, currency)

    val formattedNetPerHour: String
        get() = "${RideTrackingTruthPolicy.formatFare(projectedNetPerHourMinor, currency)}/h"

    companion object {
        const val DEFAULT_PLATFORM_COMMISSION_BPS = 500L // 5.0%
        const val DEFAULT_CRC_FUEL_COST_PER_KM_MINOR = 65L // ₡65/km (~8-9L/100km regular gas @ ₡720/L)
        const val DEFAULT_CRC_MIN_HOURLY_FLOOR_MINOR = 2500L // ₡2,500/h minimum livable driver net floor

        fun calculate(
            grossFareMinor: Long,
            tripDistanceMeters: Long,
            tripDurationSeconds: Long,
            pickupDeadheadDistanceMeters: Long = 0L,
            pickupDeadheadDurationSeconds: Long = 0L,
            fuelCostPerKmMinor: Long = DEFAULT_CRC_FUEL_COST_PER_KM_MINOR,
            commissionBps: Long = DEFAULT_PLATFORM_COMMISSION_BPS,
            minHourlyFloorMinor: Long = DEFAULT_CRC_MIN_HOURLY_FLOOR_MINOR,
            currency: String = "CRC",
        ): DriverEconomicsProjection {
            require(grossFareMinor >= 0) { "Gross fare cannot be negative" }
            require(tripDistanceMeters >= 0) { "Trip distance cannot be negative" }
            require(tripDurationSeconds >= 0) { "Trip duration cannot be negative" }

            val safePickupDist = pickupDeadheadDistanceMeters.coerceAtLeast(0L)
            val safePickupDur = pickupDeadheadDurationSeconds.coerceAtLeast(0L)
            val totalDist = tripDistanceMeters + safePickupDist
            val totalDur = tripDurationSeconds + safePickupDur

            // Platform commission (5.0%)
            val platformFee = (grossFareMinor * commissionBps) / 10000L
            val driverGross = grossFareMinor - platformFee

            // Fuel cost
            val totalDistanceKm = totalDist / 1000.0
            val estimatedFuelExpense = (totalDistanceKm * fuelCostPerKmMinor).roundToLong()

            // Net earnings after direct operating cash expenses
            val projectedNet = driverGross - estimatedFuelExpense

            // Net per hour calculation
            val totalHours = totalDur / 3600.0
            val netPerHour = if (totalHours > 0.05) {
                (projectedNet / totalHours).roundToLong()
            } else {
                projectedNet * 12 // fallback for extremely short trips (~5 mins)
            }

            val isViable = projectedNet > 0 && netPerHour >= minHourlyFloorMinor

            return DriverEconomicsProjection(
                grossFareMinor = grossFareMinor,
                platformFeeMinor = platformFee,
                grossDriverPayoutMinor = driverGross,
                tripDistanceMeters = tripDistanceMeters,
                pickupDeadheadDistanceMeters = safePickupDist,
                totalDistanceMeters = totalDist,
                tripDurationSeconds = tripDurationSeconds,
                pickupDeadheadDurationSeconds = safePickupDur,
                totalDurationSeconds = totalDur,
                estimatedFuelExpenseMinor = estimatedFuelExpense,
                projectedNetEarningsMinor = projectedNet,
                projectedNetPerHourMinor = netPerHour,
                currency = currency,
                isViable = isViable,
            )
        }
    }
}
