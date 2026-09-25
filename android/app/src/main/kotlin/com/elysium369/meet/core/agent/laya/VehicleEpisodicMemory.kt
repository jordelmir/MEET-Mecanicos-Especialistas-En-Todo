package com.elysium369.meet.core.agent.laya

/**
 * An immutable episodic memory entry for a vehicle's historical log.
 */
data class VehicleMemoryEntry(
    val entryId: String,
    val vehicleId: String,
    val timestamp: Long,
    val category: String, // "REPAIR", "DTC_EVENT", "CERTIFIED_REPORT", "INSPECTION_RTV", "ANOMALY"
    val summary: String,
    val oemPartsMentioned: List<String> = emptyList(),
    val dtcsInvolved: List<String> = emptyList(),
    val integrityHash: String? = null,
)

/**
 * Contextual forensic memory synthesis for a vehicle.
 */
data class VehicleMemorySummary(
    val vehicleId: String,
    val totalRepairsLogged: Int,
    val knownRecurrentDtcs: List<String>,
    val recentOemParts: List<String>,
    val lastCertifiedReportHash: String?,
    val forensicObservations: List<String>,
)

/**
 * VehicleEpisodicMemory — In-memory & local forensic knowledge graph.
 *
 * Persists and correlates historical repairs, recurring fault codes, certified reports,
 * and parts installations to give Laya AI historical context.
 *
 * Prevents mechanics from selling duplicate repairs or scamming clients on components
 * that were already replaced recently.
 */
class VehicleEpisodicMemory {

    private val entriesByVehicle = mutableMapOf<String, MutableList<VehicleMemoryEntry>>()

    fun recordEntry(entry: VehicleMemoryEntry) {
        entriesByVehicle.getOrOrPut(entry.vehicleId) { mutableListOf() }.add(entry)
    }

    private fun MutableMap<String, MutableList<VehicleMemoryEntry>>.getOrOrPut(
        key: String,
        defaultValue: () -> MutableList<VehicleMemoryEntry>
    ): MutableList<VehicleMemoryEntry> {
        return this.getOrPut(key, defaultValue)
    }

    fun getForensicSummary(vehicleId: String): VehicleMemorySummary {
        val list = entriesByVehicle[vehicleId] ?: emptyList()
        val recurrentDtcs = list.flatMap { it.dtcsInvolved }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 2 }
            .keys
            .toList()

        val recentParts = list.filter { it.category == "REPAIR" }
            .flatMap { it.oemPartsMentioned }
            .distinct()

        val lastReport = list.filter { it.category == "CERTIFIED_REPORT" }
            .maxByOrNull { it.timestamp }

        val observations = mutableListOf<String>()
        if (recurrentDtcs.isNotEmpty()) {
            observations.add("Fallas DTC recurrentes en este vehículo: ${recurrentDtcs.joinToString(", ")}.")
        }
        if (recentParts.isNotEmpty()) {
            observations.add("Repuestos OEM instalados recientemente: ${recentParts.joinToString(", ")}.")
        }
        if (lastReport != null) {
            observations.add("Último reporte pericial certificado: Hash=${lastReport.integrityHash?.take(12)}... en fecha ${lastReport.timestamp}.")
        }

        return VehicleMemorySummary(
            vehicleId = vehicleId,
            totalRepairsLogged = list.count { it.category == "REPAIR" },
            knownRecurrentDtcs = recurrentDtcs,
            recentOemParts = recentParts,
            lastCertifiedReportHash = lastReport?.integrityHash,
            forensicObservations = observations,
        )
    }

    /**
     * Checks if a proposed repair quote attempts to replace a part that was already replaced recently.
     */
    fun checkDuplicateRepairAlert(vehicleId: String, proposedPartName: String): String? {
        val list = entriesByVehicle[vehicleId] ?: return null
        val lower = proposedPartName.lowercase()

        val recentRepair = list.filter { it.category == "REPAIR" }
            .find { entry ->
                entry.summary.lowercase().contains(lower) ||
                entry.oemPartsMentioned.any { oem -> lower.contains(oem.lowercase()) }
            }

        return if (recentRepair != null) {
            "ALERTA ANTI-FRAUDE FORENSE: El componente '$proposedPartName' ya figura reemplazado en el historial de este vehículo ('${recentRepair.summary}'). Verifique garantía antes de autorizar."
        } else {
            null
        }
    }
}
