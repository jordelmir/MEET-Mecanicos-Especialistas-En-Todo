package com.elysium369.meet.ride.summary

import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideState
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  R I D E   T R I P   S U M M A R Y   E N G I N E
 *  ──────────────────────────────────────────────────
 *  Complete post-trip experience:
 *  - Detailed receipt with fare breakdown
 *  - Split fare with friends (Venmo-style)
 *  - Trip history with search/filter
 *  - Share receipt via WhatsApp/PDF
 *  - Post-ride tipping
 *  - Issue reporting
 *  - Trip statistics (monthly, lifetime)
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Trip Receipt ───

@Serializable
data class TripReceipt(
    val receiptId: String,
    val rideId: String,
    val passengerId: String,
    val driverId: String,
    val driverName: String,
    val driverRating: Double? = null,
    val vehicleDescription: String = "",
    val plate: String = "",
    // Route
    val pickupName: String,
    val dropoffName: String,
    val intermediateStops: List<String> = emptyList(),
    val distanceKm: Double,
    val durationMinutes: Int,
    val routePolyline: String? = null,   // Encoded polyline for map
    // Fare breakdown
    val baseFare: Long,
    val distanceFare: Long,
    val timeFare: Long,
    val surcharge: Long = 0,             // Night, holiday, etc.
    val discount: Long = 0,              // Promo codes
    val tipAmount: Long = 0,
    val totalFare: Long,
    val currency: String = "CRC",
    val fareMode: RideFareMode,
    // Payment
    val paymentMethod: String,
    val paymentStatus: PaymentStatus = PaymentStatus.COMPLETED,
    // Split fare
    val splitWith: List<FareSplitParticipant> = emptyList(),
    val myShareAmount: Long = totalFare,
    // Timestamps
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    // Ratings
    val driverRatingGiven: Int? = null,   // 1-5 stars
    val riderRatingGiven: Int? = null,
    val compliments: List<String> = emptyList(),
    val issueReported: TripIssue? = null,
) {
    val formattedTotal: String
        get() = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(totalFare, currency)

    val formattedMyShare: String
        get() = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(myShareAmount, currency)

    val hasTip: Boolean get() = tipAmount > 0
    val isSplit: Boolean get() = splitWith.isNotEmpty()
    val hasIssue: Boolean get() = issueReported != null
    val isMultiStop: Boolean get() = intermediateStops.isNotEmpty()

    val fareBreakdown: List<Pair<String, Long>>
        get() = buildList {
            add("Tarifa base" to baseFare)
            add("Distancia (${String.format("%.1f", distanceKm)} km)" to distanceFare)
            add("Tiempo ($durationMinutes min)" to timeFare)
            if (surcharge > 0) add("Recargo" to surcharge)
            if (discount > 0) add("Descuento" to -discount)
            if (tipAmount > 0) add("Propina" to tipAmount)
        }
}

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED,
}

// ─── Fare Split ───

@Serializable
data class FareSplitParticipant(
    val userId: String,
    val displayName: String,
    val shareAmount: Long,
    val status: SplitStatus = SplitStatus.PENDING,
)

enum class SplitStatus {
    PENDING,    // Invitation sent
    ACCEPTED,   // Agreed to pay their share
    PAID,       // Payment processed
    DECLINED,   // Declined to split
}

// ─── Trip Issue ───

@Serializable
data class TripIssue(
    val issueId: String,
    val category: IssueCategory,
    val description: String,
    val status: IssueStatus = IssueStatus.OPEN,
    val resolution: String? = null,
)

enum class IssueCategory {
    WRONG_ROUTE,
    SAFETY_CONCERN,
    OVERCHARGED,
    VEHICLE_CONDITION,
    DRIVER_BEHAVIOR,
    LOST_ITEM,
    APP_ERROR,
    OTHER,
}

enum class IssueStatus {
    OPEN, IN_REVIEW, RESOLVED, CLOSED,
}

// ─── Trip Statistics ───

@Serializable
data class TripStatistics(
    val totalTrips: Int,
    val totalDistanceKm: Double,
    val totalSpent: Long,
    val totalTips: Long,
    val averageFare: Long,
    val averageDistance: Double,
    val averageDuration: Int,
    val favoriteDestination: String?,
    val mostUsedPayment: String?,
    val currency: String,
) {
    val formattedTotalSpent: String
        get() = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(totalSpent, currency)
}

// ─── Tip Presets ───

data class TipPreset(
    val label: String,
    val amount: Long,
    val isPercentage: Boolean = false,
    val percentage: Int = 0,
)

// ─── Engine ───

class RideTripSummaryEngine {

    private val receipts = mutableMapOf<String, TripReceipt>()

    fun defaultTipPresets(currency: String = "CRC") = listOf(
        TipPreset("Sin propina", 0),
        TipPreset(com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(500, currency), 500),
        TipPreset(com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(1000, currency), 1000),
        TipPreset(com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.formatFare(2000, currency), 2000),
        TipPreset("10%", 0, isPercentage = true, percentage = 10),
        TipPreset("15%", 0, isPercentage = true, percentage = 15),
        TipPreset("20%", 0, isPercentage = true, percentage = 20),
    )

    // ─── Generate Receipt ───

    fun generateReceipt(
        rideId: String,
        passengerId: String,
        driverId: String,
        driverName: String,
        pickupName: String,
        dropoffName: String,
        distanceKm: Double,
        durationMinutes: Int,
        baseFare: Long,
        distanceFare: Long,
        timeFare: Long,
        fareMode: RideFareMode,
        paymentMethod: String,
        surcharge: Long = 0,
        discount: Long = 0,
    ): TripReceipt {
        val total = baseFare + distanceFare + timeFare + surcharge - discount
        val receipt = TripReceipt(
            receiptId = "rcpt-${System.currentTimeMillis()}",
            rideId = rideId,
            passengerId = passengerId,
            driverId = driverId,
            driverName = driverName,
            pickupName = pickupName,
            dropoffName = dropoffName,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            baseFare = baseFare,
            distanceFare = distanceFare,
            timeFare = timeFare,
            surcharge = surcharge,
            discount = discount,
            totalFare = total,
            fareMode = fareMode,
            paymentMethod = paymentMethod,
            startedAtEpochMs = System.currentTimeMillis() - durationMinutes * 60 * 1000L,
            completedAtEpochMs = System.currentTimeMillis(),
        )
        receipts[receipt.receiptId] = receipt
        return receipt
    }

    // ─── Tipping ───

    fun addTip(receiptId: String, tipAmount: Long): TripReceipt? {
        val receipt = receipts[receiptId] ?: return null
        val updated = receipt.copy(
            tipAmount = tipAmount,
            totalFare = receipt.totalFare - receipt.tipAmount + tipAmount,
            myShareAmount = receipt.myShareAmount - receipt.tipAmount + tipAmount,
        )
        receipts[receiptId] = updated
        return updated
    }

    fun calculatePercentageTip(receiptId: String, percentage: Int): Long {
        val receipt = receipts[receiptId] ?: return 0
        val fareBeforeTip = receipt.totalFare - receipt.tipAmount
        return (fareBeforeTip * percentage / 100)
    }

    // ─── Split Fare ───

    fun splitFare(receiptId: String, participants: List<Pair<String, String>>): TripReceipt? {
        val receipt = receipts[receiptId] ?: return null
        val totalPeople = participants.size + 1 // +1 for the payer
        val shareEach = receipt.totalFare / totalPeople
        val remainder = receipt.totalFare % totalPeople

        val splits = participants.map { (userId, name) ->
            FareSplitParticipant(userId, name, shareEach)
        }

        val updated = receipt.copy(
            splitWith = splits,
            myShareAmount = shareEach + remainder, // Payer absorbs remainder
        )
        receipts[receiptId] = updated
        return updated
    }

    // ─── Rating ───

    fun rateDriver(receiptId: String, stars: Int, compliments: List<String> = emptyList()): Boolean {
        val receipt = receipts[receiptId] ?: return false
        if (stars !in 1..5) return false
        receipts[receiptId] = receipt.copy(
            driverRatingGiven = stars,
            compliments = compliments,
        )
        return true
    }

    // ─── Issue Reporting ───

    fun reportIssue(receiptId: String, category: IssueCategory, description: String): TripIssue? {
        val receipt = receipts[receiptId] ?: return null
        val issue = TripIssue(
            issueId = "issue-${System.currentTimeMillis()}",
            category = category,
            description = description,
        )
        receipts[receiptId] = receipt.copy(issueReported = issue)
        return issue
    }

    // ─── History & Search ───

    fun getTripHistory(passengerId: String): List<TripReceipt> {
        return receipts.values
            .filter { it.passengerId == passengerId }
            .sortedByDescending { it.completedAtEpochMs }
    }

    fun searchTrips(passengerId: String, query: String): List<TripReceipt> {
        return getTripHistory(passengerId).filter { receipt ->
            receipt.pickupName.contains(query, ignoreCase = true) ||
                receipt.dropoffName.contains(query, ignoreCase = true) ||
                receipt.driverName.contains(query, ignoreCase = true)
        }
    }

    // ─── Statistics ───

    fun computeStatistics(passengerId: String): TripStatistics {
        val trips = getTripHistory(passengerId)
        if (trips.isEmpty()) return TripStatistics(0, 0.0, 0, 0, 0, 0.0, 0, null, null, "CRC")

        val totalDist = trips.sumOf { it.distanceKm }
        val totalSpent = trips.sumOf { it.totalFare }
        val totalTips = trips.sumOf { it.tipAmount }

        val destinations = trips.groupBy { it.dropoffName }
            .maxByOrNull { it.value.size }?.key

        val payments = trips.groupBy { it.paymentMethod }
            .maxByOrNull { it.value.size }?.key

        return TripStatistics(
            totalTrips = trips.size,
            totalDistanceKm = totalDist,
            totalSpent = totalSpent,
            totalTips = totalTips,
            averageFare = totalSpent / trips.size,
            averageDistance = totalDist / trips.size,
            averageDuration = trips.sumOf { it.durationMinutes } / trips.size,
            favoriteDestination = destinations,
            mostUsedPayment = payments,
            currency = trips.firstOrNull()?.currency ?: "CRC",
        )
    }

    // ─── Share Receipt ───

    fun generateShareText(receiptId: String): String {
        val r = receipts[receiptId] ?: return ""
        return buildString {
            appendLine("🚗 Recibo de Viaje — ELYSIUM")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📍 ${r.pickupName} → ${r.dropoffName}")
            appendLine("📏 ${String.format("%.1f", r.distanceKm)} km · ${r.durationMinutes} min")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            r.fareBreakdown.forEach { (label, amount) ->
                appendLine("  $label: ₡$amount")
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("💰 Total: ${r.formattedTotal}")
            if (r.isSplit) appendLine("🤝 Mi parte: ${r.formattedMyShare}")
            appendLine("🚘 ${r.driverName}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }

    fun getReceipt(id: String): TripReceipt? = receipts[id]
    val totalReceipts: Int get() = receipts.size
}
