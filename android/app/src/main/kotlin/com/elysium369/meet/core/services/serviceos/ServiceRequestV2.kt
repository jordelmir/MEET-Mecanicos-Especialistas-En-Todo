package com.elysium369.meet.core.services.serviceos

import com.elysium369.meet.core.money.Money
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.services.repair.RepairState
import java.util.UUID

/**
 * Urgency level for the service request.
 */
enum class ServiceRequestUrgency(val displayName: String, val badgeColorHex: Long) {
    URGENT_BREAKDOWN("🚨 Varado / Emergencia", 0xFFFF3B30),
    NEXT_AVAILABLE_SLOT("⚡ Próximo Turno", 0xFFFF9500),
    SCHEDULED_MAINTENANCE("📅 Programable / Cotización", 0xFF00E676)
}

/**
 * Mobility condition of the vehicle.
 */
enum class MobilityCondition(val displayName: String) {
    DRIVABLE_NORMAL("Se puede conducir normalmente"),
    SHORT_DISTANCE_ONLY("Solo trayectos cortos / con precaución"),
    IMMOBILIZED_TOW_REQUIRED("No arranca / Inmóvil (Requiere Grúa)"),
    UNCERTAIN("Condición incierta / Desconocida")
}

/**
 * Desired modality of service delivery.
 */
enum class ServiceModality(val displayName: String) {
    WORKSHOP_FACILITY("Taller Físico"),
    MOBILE_MECHANIC("Mecánico a Domicilio / Móvil"),
    REMOTE_DIAGNOSTICS("Diagnóstico Remoto / Telemétrico"),
    TOW_FIRST("Grúa Primero al Taller"),
    RECOMMEND_FOR_ME("Recomendar según diagnóstico")
}

/**
 * Privacy-preserving geographical zone for initial bidding.
 */
data class PrivacyLocationZone(
    val approximateZoneName: String,
    val approximateRadiusKm: Double,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val exactAddressMasked: String = "Ubicación exacta compartida solo al asignar proveedor"
)

/**
 * Structured facts and telemetry evidence captured by MEET.
 */
data class ServiceEvidencePayload(
    val vehicleId: String,
    val vehicleDisplayName: String,
    val maskedVin: String?,
    val activeDtcs: List<String> = emptyList(),
    val freezeFrameSummary: String? = null,
    val odometertKm: Double? = null,
    val batteryVoltage: Double? = null,
    val userReportedSymptom: String,
    val userSymptomCategory: String,
    val photoUris: List<String> = emptyList(),
    val videoUris: List<String> = emptyList(),
    val audioUris: List<String> = emptyList()
)

/**
 * Structured Service Request V2.
 */
data class ServiceRequestV2(
    val id: String = UUID.randomUUID().toString(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val urgency: ServiceRequestUrgency,
    val mobility: MobilityCondition,
    val preferredModality: ServiceModality,
    val locationZone: PrivacyLocationZone,
    val evidence: ServiceEvidencePayload,
    val estimatedBudget: Money? = null,
    val state: RepairState = RepairState.PUBLISHED,
    val offersReceivedCount: Int = 0,
    val selectedOfferId: String? = null
)

/**
 * Structured Service Offer submitted by a certified workshop or technician.
 */
data class StructuredServiceOffer(
    val offerId: String = UUID.randomUUID().toString(),
    val requestId: String,
    val providerId: String,
    val providerName: String,
    val providerRating: Double,
    val providerVerifiedCasesCount: Int,
    val technicalHypothesis: String,
    val proposedScopeOfWork: String,
    val laborCost: Money,
    val diagnosticCost: Money,
    val partsIncluded: Boolean,
    val estimatedDurationHours: Double,
    val estimatedArrivalOrStartMs: Long,
    val warrantyDays: Int,
    val warrantyTerms: String,
    val modality: ServiceModality,
    val roadDistanceKm: Double,
    val positiveMatchingSignals: List<String> = emptyList(),
    val potentialMatchingWarnings: List<String> = emptyList()
) {
    val totalEstimatedCost: Money
        get() = laborCost.plus(diagnosticCost)
}

/**
 * Comparative matrix for evaluating multiple offers.
 */
data class OfferComparisonRow(
    val offer: StructuredServiceOffer,
    val matchScorePercent: Int,
    val isRecommended: Boolean,
    val recommendationRationale: String? = null
)
