package com.elysium369.meet.core.intelligence

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  C O L L E C T I V E   I N T E L L I G E N C E   E N G I N E
 *  ──────────────────────────────────────────────────────────────
 *  1,000 mechanics know things that no individual mechanic knows.
 *
 *  "DTCs P0300+P0171 on Toyota Corolla 2015 1.8L =
 *   intake manifold gasket 87% of the time."
 *
 *  Every successful repair teaches the ENTIRE network.
 *  Every failed diagnosis warns the ENTIRE network.
 *  Individual knowledge becomes SPECIES-LEVEL knowledge.
 *
 *  This is not AI hallucination — this is REAL DATA from REAL repairs
 *  performed by REAL humans, verified by REAL outcomes.
 *
 *  CONSTITUTIONAL: Every insight MUST declare its evidence basis.
 *  "Dato no capturado" or "Confianza limitada" when data is sparse.
 * ══════════════════════════════════════════════════════════════════════
 */

// ─── Repair Outcome (the atomic unit of collective knowledge) ───

@Serializable
data class RepairOutcome(
    val outcomeId: String,
    val mechanicId: String,
    val domain: UniversalServiceDomain,
    val vehicleBrand: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val engineType: String = "",
    val dtcCodes: List<String>,
    val diagnosisDescription: String,
    val repairAction: String,
    val wasSuccessful: Boolean,
    val customerConfirmed: Boolean = false,
    val partsUsed: List<String> = emptyList(),
    val laborMinutes: Int = 0,
    val costUsd: Double = 0.0,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

// ─── Pattern (emerged from collective data) ───

@Serializable
data class CollectivePattern(
    val patternId: String,
    val dtcCombination: List<String>,
    val vehicleSelector: VehicleSelector,
    val mostLikelyDiagnosis: String,
    val mostLikelyRepair: String,
    val confidencePercent: Double,
    val dataPoints: Int,
    val successRate: Double,
    val averageCostUsd: Double,
    val averageLaborMinutes: Int,
    val commonParts: List<String>,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
) {
    val evidenceBasis: String
        get() = when {
            dataPoints >= 50 -> "Alta confianza — $dataPoints reparaciones verificadas"
            dataPoints >= 10 -> "Confianza moderada — $dataPoints reparaciones"
            dataPoints >= 3 -> "Confianza limitada — solo $dataPoints casos"
            else -> "Dato preliminar — requiere más evidencia"
        }

    val isReliable: Boolean get() = dataPoints >= 10 && confidencePercent >= 60.0
}

@Serializable
data class VehicleSelector(
    val brand: String? = null,
    val model: String? = null,
    val yearMin: Int? = null,
    val yearMax: Int? = null,
    val engineType: String? = null,
) {
    fun matches(brand: String, model: String, year: Int, engine: String): Boolean {
        if (this.brand != null && !brand.equals(this.brand, ignoreCase = true)) return false
        if (this.model != null && !model.equals(this.model, ignoreCase = true)) return false
        if (this.yearMin != null && year < this.yearMin) return false
        if (this.yearMax != null && year > this.yearMax) return false
        if (this.engineType != null && !engine.equals(this.engineType, ignoreCase = true)) return false
        return true
    }

    val description: String
        get() = listOfNotNull(brand, model, yearMin?.let { "$it-${yearMax ?: "present"}" }, engineType)
            .joinToString(" ").ifBlank { "Todos los vehículos" }
}

// ─── Collective Intelligence Engine ───

class CollectiveIntelligenceEngine {

    private val outcomes = mutableListOf<RepairOutcome>()
    private val patterns = mutableListOf<CollectivePattern>()

    /**
     * Records a repair outcome into the collective knowledge base.
     * Every repair teaches the network.
     */
    fun recordOutcome(outcome: RepairOutcome) {
        outcomes.add(outcome)
    }

    /**
     * Queries the collective knowledge for a DTC combination + vehicle.
     * Returns patterns sorted by confidence.
     */
    fun queryPatterns(
        dtcCodes: List<String>,
        brand: String? = null,
        model: String? = null,
        year: Int? = null,
    ): List<CollectivePattern> {
        val dtcSet = dtcCodes.toSet()

        // First check pre-computed patterns
        val precomputed = patterns.filter { pattern ->
            pattern.dtcCombination.toSet() == dtcSet &&
                (brand == null || pattern.vehicleSelector.brand.equals(brand, true))
        }

        if (precomputed.isNotEmpty()) return precomputed.sortedByDescending { it.confidencePercent }

        // Compute from raw outcomes
        return computePatterns(dtcCodes, brand, model, year)
    }

    /**
     * Discovers patterns from raw outcome data.
     * This is the collective intelligence at work.
     */
    fun computePatterns(
        dtcCodes: List<String>,
        brand: String? = null,
        model: String? = null,
        year: Int? = null,
    ): List<CollectivePattern> {
        val dtcSet = dtcCodes.toSet()

        // Find matching outcomes
        val matching = outcomes.filter { outcome ->
            outcome.dtcCodes.toSet() == dtcSet &&
                (brand == null || outcome.vehicleBrand.equals(brand, true)) &&
                (model == null || outcome.vehicleModel.equals(model, true)) &&
                (year == null || outcome.vehicleYear == year)
        }

        if (matching.isEmpty()) return emptyList()

        // Group by diagnosis
        val byDiagnosis = matching.groupBy { it.diagnosisDescription }

        return byDiagnosis.map { (diagnosis, cases) ->
            val successful = cases.count { it.wasSuccessful }
            val confirmed = cases.count { it.customerConfirmed }
            val parts = cases.flatMap { it.partsUsed }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }
                .take(5).map { it.key }

            CollectivePattern(
                patternId = "cp-${dtcCodes.joinToString("-")}-${diagnosis.hashCode().toUInt()}",
                dtcCombination = dtcCodes,
                vehicleSelector = VehicleSelector(brand, model),
                mostLikelyDiagnosis = diagnosis,
                mostLikelyRepair = cases.first().repairAction,
                confidencePercent = (successful.toDouble() / cases.size * 100),
                dataPoints = cases.size,
                successRate = successful.toDouble() / cases.size,
                averageCostUsd = cases.map { it.costUsd }.average(),
                averageLaborMinutes = cases.map { it.laborMinutes }.average().toInt(),
                commonParts = parts,
            )
        }.sortedByDescending { it.confidencePercent }
    }

    /**
     * Gets the network's collective wisdom for a specific scenario.
     * Returns human-readable insights with honest confidence levels.
     */
    fun getInsight(
        dtcCodes: List<String>,
        brand: String,
        model: String,
        year: Int,
    ): CollectiveInsight {
        val patterns = queryPatterns(dtcCodes, brand, model, year)

        if (patterns.isEmpty()) {
            return CollectiveInsight(
                dtcCodes = dtcCodes,
                vehicleDescription = "$brand $model $year",
                topDiagnosis = null,
                alternativeDiagnoses = emptyList(),
                networkDataPoints = 0,
                disclaimer = "Sin datos colectivos para esta combinación. " +
                    "Requiere diagnóstico individual.",
            )
        }

        val top = patterns.first()
        val alternatives = patterns.drop(1).take(3)

        return CollectiveInsight(
            dtcCodes = dtcCodes,
            vehicleDescription = "$brand $model $year",
            topDiagnosis = top,
            alternativeDiagnoses = alternatives,
            networkDataPoints = patterns.sumOf { it.dataPoints },
            disclaimer = top.evidenceBasis,
        )
    }

    /**
     * Network-wide statistics.
     */
    fun networkStats(): NetworkKnowledgeStats {
        return NetworkKnowledgeStats(
            totalOutcomes = outcomes.size,
            totalPatterns = patterns.size,
            uniqueDtcCombinations = outcomes.map { it.dtcCodes.sorted().joinToString(",") }.distinct().size,
            uniqueVehicles = outcomes.map { "${it.vehicleBrand} ${it.vehicleModel}" }.distinct().size,
            contributingMechanics = outcomes.map { it.mechanicId }.distinct().size,
            averageSuccessRate = if (outcomes.isEmpty()) 0.0
                else outcomes.count { it.wasSuccessful }.toDouble() / outcomes.size,
        )
    }

    val totalKnowledge: Int get() = outcomes.size
}

@Serializable
data class CollectiveInsight(
    val dtcCodes: List<String>,
    val vehicleDescription: String,
    val topDiagnosis: CollectivePattern?,
    val alternativeDiagnoses: List<CollectivePattern>,
    val networkDataPoints: Int,
    val disclaimer: String,
)

@Serializable
data class NetworkKnowledgeStats(
    val totalOutcomes: Int,
    val totalPatterns: Int,
    val uniqueDtcCombinations: Int,
    val uniqueVehicles: Int,
    val contributingMechanics: Int,
    val averageSuccessRate: Double,
)
