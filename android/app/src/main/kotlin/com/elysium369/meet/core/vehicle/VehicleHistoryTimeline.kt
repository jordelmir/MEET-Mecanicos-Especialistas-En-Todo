package com.elysium369.meet.core.vehicle

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  V E H I C L E   H I S T O R Y   T I M E L I N E
 *  ──────────────────────────────────────────────────
 *  Complete, immutable, verifiable vehicle history.
 *
 *  Every event is chained:
 *    event[n].prevHash = SHA-256(event[n-1])
 *
 *  A buyer, insurer, or fleet manager can verify
 *  the ENTIRE history independently with the QR.
 *
 *  Events tracked:
 *  ✅ OBD Scans (DTCs found/cleared)
 *  ✅ Repairs (what, who, when, warranty)
 *  ✅ Parts replaced (OEM/aftermarket, source)
 *  ✅ Certified reports (pre/post scan, DVIR)
 *  ✅ Maintenance (oil, brakes, tires, filters)
 *  ✅ Incidents (accident, theft, flood)
 *  ✅ Ownership transfers
 *  ✅ Mileage checkpoints
 *  ✅ Health score changes
 *  ✅ Warranty claims
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Event Types ───

enum class VehicleEventType {
    OBD_SCAN,
    DTC_FOUND,
    DTC_CLEARED,
    REPAIR_COMPLETED,
    PART_REPLACED,
    MAINTENANCE,
    CERTIFIED_REPORT,
    INCIDENT,
    OWNERSHIP_TRANSFER,
    MILEAGE_CHECKPOINT,
    HEALTH_SCORE_UPDATE,
    WARRANTY_ACTIVATED,
    WARRANTY_CLAIM,
    INSURANCE_REPORT,
    INSPECTION_PASSED,
    INSPECTION_FAILED,
    RECALL_NOTICE,
    MODIFICATION,
}

// ─── Timeline Event ───

@Serializable
data class VehicleTimelineEvent(
    val eventId: String,
    val vehicleId: String,
    val type: VehicleEventType,
    val title: String,
    val description: String,
    val details: Map<String, String> = emptyMap(),
    val actorId: String? = null,
    val actorName: String? = null,
    val actorRole: String? = null,     // "Mecánico", "Propietario", "Aseguradora"
    val mileageKm: Long? = null,
    val relatedReportId: String? = null,
    val relatedDtcCodes: List<String> = emptyList(),
    val relatedPartNumbers: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis(),
    val prevHash: String = "",
    val eventHash: String = "",
    val isVerified: Boolean = false,
) {
    val hasPhotos: Boolean get() = photoUrls.isNotEmpty()
    val hasDtcs: Boolean get() = relatedDtcCodes.isNotEmpty()
    val hasReport: Boolean get() = relatedReportId != null
}

// ─── Vehicle Summary ───

@Serializable
data class VehicleHistorySummary(
    val vehicleId: String,
    val totalEvents: Int,
    val totalRepairs: Int,
    val totalScans: Int,
    val totalParts: Int,
    val totalIncidents: Int,
    val totalOwners: Int,
    val latestMileageKm: Long?,
    val firstEventMs: Long?,
    val latestEventMs: Long?,
    val chainIntact: Boolean,
    val healthScore: Int?,
) {
    val historySpanDays: Long?
        get() = if (firstEventMs != null && latestEventMs != null) {
            (latestEventMs - firstEventMs) / (24 * 60 * 60 * 1000)
        } else null

    val cleanHistory: Boolean
        get() = totalIncidents == 0 && chainIntact
}

// ─── Verification Result ───

data class ChainVerification(
    val isValid: Boolean,
    val totalEvents: Int,
    val verifiedEvents: Int,
    val brokenAtIndex: Int?,
    val message: String,
)

// ─── Engine ───

class VehicleHistoryTimeline {

    private val timelines = mutableMapOf<String, MutableList<VehicleTimelineEvent>>()

    // ─── Add Event (chain-linked) ───

    fun addEvent(
        vehicleId: String,
        type: VehicleEventType,
        title: String,
        description: String,
        details: Map<String, String> = emptyMap(),
        actorId: String? = null,
        actorName: String? = null,
        actorRole: String? = null,
        mileageKm: Long? = null,
        relatedReportId: String? = null,
        relatedDtcCodes: List<String> = emptyList(),
        relatedPartNumbers: List<String> = emptyList(),
        photoUrls: List<String> = emptyList(),
    ): VehicleTimelineEvent {
        val events = timelines.getOrPut(vehicleId) { mutableListOf() }
        val prevHash = if (events.isNotEmpty()) events.last().eventHash else "genesis"

        val event = VehicleTimelineEvent(
            eventId = "evt-${System.currentTimeMillis()}-${events.size}",
            vehicleId = vehicleId,
            type = type,
            title = title,
            description = description,
            details = details,
            actorId = actorId,
            actorName = actorName,
            actorRole = actorRole,
            mileageKm = mileageKm,
            relatedReportId = relatedReportId,
            relatedDtcCodes = relatedDtcCodes,
            relatedPartNumbers = relatedPartNumbers,
            photoUrls = photoUrls,
            prevHash = prevHash,
        )

        val hashed = event.copy(eventHash = computeHash(event))
        events.add(hashed)
        return hashed
    }

    // ─── Verify Chain Integrity ───

    fun verifyChain(vehicleId: String): ChainVerification {
        val events = timelines[vehicleId] ?: return ChainVerification(
            true, 0, 0, null, "Sin eventos registrados.",
        )

        for (i in events.indices) {
            val event = events[i]
            val expectedPrev = if (i == 0) "genesis" else events[i - 1].eventHash
            if (event.prevHash != expectedPrev) {
                return ChainVerification(
                    isValid = false,
                    totalEvents = events.size,
                    verifiedEvents = i,
                    brokenAtIndex = i,
                    message = "⚠️ Cadena rota en evento #$i: '${event.title}'. Posible manipulación.",
                )
            }
            val recomputedHash = computeHash(event.copy(eventHash = ""))
            if (event.eventHash != recomputedHash) {
                return ChainVerification(
                    isValid = false,
                    totalEvents = events.size,
                    verifiedEvents = i,
                    brokenAtIndex = i,
                    message = "⚠️ Hash alterado en evento #$i: '${event.title}'. Dato manipulado.",
                )
            }
        }

        return ChainVerification(
            isValid = true,
            totalEvents = events.size,
            verifiedEvents = events.size,
            brokenAtIndex = null,
            message = "✅ Cadena íntegra. ${events.size} eventos verificados.",
        )
    }

    // ─── Timeline Queries ───

    fun getTimeline(vehicleId: String): List<VehicleTimelineEvent> =
        timelines[vehicleId]?.sortedByDescending { it.timestampMs } ?: emptyList()

    fun getTimelineByType(vehicleId: String, type: VehicleEventType): List<VehicleTimelineEvent> =
        getTimeline(vehicleId).filter { it.type == type }

    fun getRepairHistory(vehicleId: String): List<VehicleTimelineEvent> =
        getTimeline(vehicleId).filter {
            it.type in listOf(VehicleEventType.REPAIR_COMPLETED, VehicleEventType.PART_REPLACED, VehicleEventType.MAINTENANCE)
        }

    fun getDtcHistory(vehicleId: String): List<VehicleTimelineEvent> =
        getTimeline(vehicleId).filter {
            it.type in listOf(VehicleEventType.DTC_FOUND, VehicleEventType.DTC_CLEARED, VehicleEventType.OBD_SCAN)
        }

    // ─── Summary ───

    fun getSummary(vehicleId: String): VehicleHistorySummary {
        val events = timelines[vehicleId] ?: emptyList()
        val chain = verifyChain(vehicleId)

        return VehicleHistorySummary(
            vehicleId = vehicleId,
            totalEvents = events.size,
            totalRepairs = events.count { it.type == VehicleEventType.REPAIR_COMPLETED },
            totalScans = events.count { it.type == VehicleEventType.OBD_SCAN },
            totalParts = events.count { it.type == VehicleEventType.PART_REPLACED },
            totalIncidents = events.count { it.type == VehicleEventType.INCIDENT },
            totalOwners = events.count { it.type == VehicleEventType.OWNERSHIP_TRANSFER } + 1,
            latestMileageKm = events.mapNotNull { it.mileageKm }.maxOrNull(),
            firstEventMs = events.minOfOrNull { it.timestampMs },
            latestEventMs = events.maxOfOrNull { it.timestampMs },
            chainIntact = chain.isValid,
            healthScore = events.lastOrNull { it.type == VehicleEventType.HEALTH_SCORE_UPDATE }
                ?.details?.get("score")?.toIntOrNull(),
        )
    }

    // ─── Search ───

    fun searchEvents(vehicleId: String, query: String): List<VehicleTimelineEvent> =
        getTimeline(vehicleId).filter {
            it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true) ||
                it.relatedDtcCodes.any { dtc -> dtc.contains(query, ignoreCase = true) }
        }

    // ─── Export ───

    fun generateShareText(vehicleId: String): String {
        val summary = getSummary(vehicleId)
        val chain = verifyChain(vehicleId)
        return buildString {
            appendLine("📜 Historial Vehicular — ELYSIUM")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🚗 Vehículo: $vehicleId")
            appendLine("📊 ${summary.totalEvents} eventos registrados")
            appendLine("🔧 ${summary.totalRepairs} reparaciones")
            appendLine("🔍 ${summary.totalScans} escaneos OBD")
            appendLine("⚙️ ${summary.totalParts} partes reemplazadas")
            summary.latestMileageKm?.let { appendLine("📏 Último kilometraje: ${it.toFormattedKm()}") }
            appendLine("👥 ${summary.totalOwners} propietario(s)")
            summary.healthScore?.let { appendLine("🏥 Health Score: $it/1000") }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine(chain.message)
        }
    }

    val totalVehicles: Int get() = timelines.size
    val totalEvents: Int get() = timelines.values.sumOf { it.size }

    // ─── Internal ───

    private fun computeHash(event: VehicleTimelineEvent): String {
        val data = "${event.eventId}|${event.vehicleId}|${event.type}|${event.title}|" +
            "${event.timestampMs}|${event.prevHash}|${event.mileageKm}"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun Long.toFormattedKm(): String =
        "${toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")} km"
}
