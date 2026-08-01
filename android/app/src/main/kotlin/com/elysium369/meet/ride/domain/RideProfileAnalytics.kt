package com.elysium369.meet.ride.domain

import com.elysium369.meet.data.local.entities.RideRequestEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class RideMoneyWindows(
    val today: Double,
    val week: Double,
    val month: Double,
    val year: Double,
    val rollingThreeYears: Double,
)

data class RideProfileSummary(
    val completedTrips: Int,
    val cancelledTrips: Int,
    val activeTrips: Int,
    val totalDistanceKm: Double,
    val averageRating: Double?,
    val capturedRatings: Int,
    val ratingDistribution: Map<Int, Int>,
    val money: RideMoneyWindows,
    val firstTripAt: Long?,
    val recognitions: List<String>,
    val acceptanceRatePercent: Double?,
    val completionRatePercent: Double?,
)

object RideProfileAnalytics {
    fun passenger(
        rides: List<RideRequestEntity>,
        passengerId: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RideProfileSummary = summarize(
        rides = rides.filter { it.passengerId == passengerId },
        ratingOf = RideRequestEntity::driverRating,
        nowEpochMs = nowEpochMs,
        zoneId = zoneId,
        recognitionsForDriver = false,
    )

    fun driver(
        rides: List<RideRequestEntity>,
        driverId: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RideProfileSummary = summarize(
        rides = rides.filter { it.assignedDriverId == driverId },
        ratingOf = RideRequestEntity::passengerRating,
        nowEpochMs = nowEpochMs,
        zoneId = zoneId,
        recognitionsForDriver = true,
    )

    private fun summarize(
        rides: List<RideRequestEntity>,
        ratingOf: (RideRequestEntity) -> Double?,
        nowEpochMs: Long,
        zoneId: ZoneId,
        recognitionsForDriver: Boolean,
    ): RideProfileSummary {
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        val completed = rides.filter { it.status == "COMPLETED" }
        val capturedRatings = completed.mapNotNull(ratingOf).filter { it in 1.0..5.0 }
        fun amount(ride: RideRequestEntity) = ride.finalPrice ?: ride.priceOffer
        fun completedMoment(ride: RideRequestEntity): ZonedDateTime =
            Instant.ofEpochMilli(ride.completedAt ?: ride.createdAt).atZone(zoneId)
        fun sumWhere(predicate: (ZonedDateTime) -> Boolean): Double =
            completed.filter { predicate(completedMoment(it)) }.sumOf(::amount)

        val startOfWeek = now.toLocalDate().minusDays((now.dayOfWeek.value - 1).toLong())
        val money = RideMoneyWindows(
            today = sumWhere { it.toLocalDate() == now.toLocalDate() },
            week = sumWhere { !it.toLocalDate().isBefore(startOfWeek) },
            month = sumWhere { it.year == now.year && it.month == now.month },
            year = sumWhere { it.year == now.year },
            rollingThreeYears = sumWhere { !it.isBefore(now.minusYears(3)) },
        )
        val average = capturedRatings.takeIf(List<Double>::isNotEmpty)?.average()
        val cancelled = rides.count { it.status == "CANCELLED" }
        val completionDenominator = completed.size + cancelled
        val recognition = buildList {
            if (completed.size >= 5) add("Primeros 5 viajes completados")
            if (completed.size >= 100) add("100 viajes verificados")
            if (average != null && capturedRatings.size >= 10 && average >= 4.8) {
                add("Excelencia · 4.8+ con 10 calificaciones")
            }
            if (recognitionsForDriver && completed.size >= 20 && cancelled * 20 <= rides.size.coerceAtLeast(1)) {
                add("Confiabilidad operativa")
            }
        }
        return RideProfileSummary(
            completedTrips = completed.size,
            cancelledTrips = cancelled,
            activeTrips = rides.count { it.status !in setOf("COMPLETED", "CANCELLED") },
            totalDistanceKm = completed.sumOf { it.estimatedDistanceKm.coerceAtLeast(0.0) },
            averageRating = average,
            capturedRatings = capturedRatings.size,
            ratingDistribution = (1..5).associateWith { stars ->
                capturedRatings.count { kotlin.math.round(it).toInt() == stars }
            },
            money = money,
            firstTripAt = rides.minOfOrNull(RideRequestEntity::createdAt),
            recognitions = recognition,
            acceptanceRatePercent = null,
            completionRatePercent = if (recognitionsForDriver && completionDenominator > 0) {
                completed.size * 100.0 / completionDenominator
            } else {
                null
            },
        )
    }
}
