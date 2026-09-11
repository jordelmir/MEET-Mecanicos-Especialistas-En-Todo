package com.elysium369.meet.ride.domain

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money

/** Presentation truth only; never grants server authorization or settles a payment. */
object RideTrackingTruthPolicy {
    fun formatFare(amountMinor: Long, currency: String): String {
        val code = CurrencyCode.entries.singleOrNull { it.name == currency }
            ?: return "Moneda no disponible"
        if (amountMinor < 0L) return "Tarifa no disponible"
        return "${code.name} ${Money(amountMinor, code).formatted()}"
    }

    fun actions(state: RideState, settled: Boolean, paymentConnected: Boolean, ratingConnected: Boolean) =
        RideTrackingActions(
            canPay = state == RideState.COMPLETED && !settled && paymentConnected,
            canRate = state == RideState.COMPLETED && settled && ratingConnected
        )

    fun freshness(
        latitude: Double?, longitude: Double?, accuracy: Float?, capturedAt: Long?,
        receivedAt: Long?, sequenceId: Long?, source: String?, now: Long
    ): TrackingFreshness {
        if (latitude == null || !latitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude == null || !longitude.isFinite() || longitude !in -180.0..180.0 ||
            accuracy == null || !accuracy.isFinite() || accuracy < 0f || accuracy > 100f ||
            capturedAt == null || capturedAt <= 0 || receivedAt == null || receivedAt <= 0 ||
            sequenceId == null || sequenceId < 0 || source.isNullOrBlank() ||
            now < capturedAt || now < receivedAt
        ) return TrackingFreshness.UNKNOWN
        // Both capture and reception age matter; a late delivery cannot refresh old GPS.
        val age = maxOf(now - capturedAt, now - receivedAt)
        return when {
            age <= 15_000L -> TrackingFreshness.LIVE
            age <= 60_000L -> TrackingFreshness.RECENT
            else -> TrackingFreshness.STALE
        }
    }
}

data class RideTrackingActions(val canPay: Boolean, val canRate: Boolean)

enum class TrackingFreshness(val label: String) {
    LIVE("Ubicación en vivo"),
    RECENT("Ubicación reciente"),
    STALE("Ubicación desactualizada"),
    UNKNOWN("Ubicación no disponible")
}
