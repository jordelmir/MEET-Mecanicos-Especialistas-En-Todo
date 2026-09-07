package com.elysium369.meet.core.diagnostic

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  D I A G N O S T I C   W I S D O M   E N G I N E
 *  ──────────────────────────────────────────────────
 *  Crowd-sourced collective intelligence of mechanics.
 *
 *  "3,200 mecánicos resolvieron P0301 en Toyota Corolla 2015 así..."
 *
 *  Every repair outcome feeds back:
 *  ┌──────────────────────────────────────────────┐
 *  │  DTC found → Solutions ranked by success     │
 *  │  Mechanic applies fix → Pre/Post scan        │
 *  │  DTC cleared? → Solution confidence ↑        │
 *  │  DTC returned? → Solution confidence ↓       │
 *  │  Knowledge compounds across all mechanics    │
 *  └──────────────────────────────────────────────┘
 *
 *  Not invented data — only verified outcomes.
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── DTC Solution ───

@Serializable
data class DtcSolution(
    val solutionId: String,
    val dtcCode: String,
    val dtcDescription: String,
    val vehicleBrand: String = "",
    val vehicleModel: String = "",
    val vehicleYearMin: Int? = null,
    val vehicleYearMax: Int? = null,
    val engineType: String = "",
    // Solution
    val title: String,
    val description: String,
    val steps: List<String> = emptyList(),
    val partsNeeded: List<String> = emptyList(),
    val toolsRequired: List<String> = emptyList(),
    val estimatedTimeMinutes: Int? = null,
    val difficulty: Difficulty = Difficulty.MODERATE,
    val estimatedCost: Long? = null,
    val currency: String = "CRC",
    // Crowd metrics
    val contributorId: String,
    val contributorName: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val totalAttempts: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val lastUsedEpochMs: Long? = null,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
) {
    val successRate: Double
        get() = if (totalAttempts > 0) successCount.toDouble() / totalAttempts else 0.0

    val confidenceLevel: ConfidenceLevel
        get() = when {
            totalAttempts < 3 -> ConfidenceLevel.PRELIMINARY
            totalAttempts < 10 -> if (successRate > 0.7) ConfidenceLevel.MODERATE else ConfidenceLevel.LOW
            totalAttempts < 50 -> if (successRate > 0.8) ConfidenceLevel.HIGH else ConfidenceLevel.MODERATE
            else -> if (successRate > 0.85) ConfidenceLevel.VERIFIED else ConfidenceLevel.HIGH
        }

    val relevanceScore: Double
        get() = (successRate * 0.6) + (totalAttempts.coerceAtMost(100).toDouble() / 100 * 0.25) +
            (upvotes.toDouble() / (upvotes + downvotes + 1).toDouble() * 0.15)

    val formattedSuccessRate: String
        get() = "${(successRate * 100).toInt()}% éxito ($totalAttempts intentos)"
}

enum class Difficulty {
    EASY,       // < 30 min, basic tools
    MODERATE,   // 30-120 min, standard tools
    HARD,       // 2-8 hours, specialized tools
    EXPERT,     // Requires lift, special equipment
}

enum class ConfidenceLevel {
    PRELIMINARY,  // < 3 attempts
    LOW,          // Low success rate
    MODERATE,     // Growing confidence
    HIGH,         // Reliable
    VERIFIED,     // 50+ attempts, 85%+ success
}

val Difficulty.displayLabel: String
    get() = when (this) {
        Difficulty.EASY -> "Fácil (< 30 min)"
        Difficulty.MODERATE -> "Moderado (30-120 min)"
        Difficulty.HARD -> "Difícil (2-8 horas)"
        Difficulty.EXPERT -> "Experto (equipo especializado)"
    }

val ConfidenceLevel.displayLabel: String
    get() = when (this) {
        ConfidenceLevel.PRELIMINARY -> "Dato preliminar"
        ConfidenceLevel.LOW -> "Confianza baja"
        ConfidenceLevel.MODERATE -> "Confianza moderada"
        ConfidenceLevel.HIGH -> "Confianza alta"
        ConfidenceLevel.VERIFIED -> "Verificado por la comunidad"
    }

// ─── Repair Outcome (feedback loop) ───

@Serializable
data class RepairOutcome(
    val outcomeId: String,
    val solutionId: String,
    val mechanicId: String,
    val vehicleId: String,
    val dtcCode: String,
    val success: Boolean,
    val dtcClearedAfterRepair: Boolean,
    val dtcReturnedWithinDays: Int? = null,
    val preScanReportId: String? = null,
    val postScanReportId: String? = null,
    val actualTimeMinutes: Int? = null,
    val actualCost: Long? = null,
    val notes: String = "",
    val timestampMs: Long = System.currentTimeMillis(),
)

// ─── Contributor Stats ───

data class ContributorStats(
    val mechanicId: String,
    val totalSolutions: Int,
    val totalOutcomes: Int,
    val averageSuccessRate: Double,
    val topDtcCodes: List<String>,
    val rank: ContributorRank,
)

enum class ContributorRank {
    APPRENTICE,     // < 5 solutions
    PRACTITIONER,   // 5-19 solutions
    SPECIALIST,     // 20-49 solutions
    MASTER,         // 50-99 solutions
    SAGE,           // 100+ solutions
}

// ─── Engine ───

class DiagnosticWisdomEngine {

    private val solutions = mutableListOf<DtcSolution>()
    private val outcomes = mutableListOf<RepairOutcome>()

    // ─── Add Solution ───

    fun addSolution(
        dtcCode: String,
        dtcDescription: String,
        title: String,
        description: String,
        steps: List<String> = emptyList(),
        partsNeeded: List<String> = emptyList(),
        estimatedTimeMinutes: Int? = null,
        difficulty: Difficulty = Difficulty.MODERATE,
        estimatedCost: Long? = null,
        contributorId: String,
        contributorName: String,
        vehicleBrand: String = "",
        vehicleModel: String = "",
    ): DtcSolution {
        val solution = DtcSolution(
            solutionId = "sol-${System.currentTimeMillis()}-${solutions.size}",
            dtcCode = dtcCode.uppercase(),
            dtcDescription = dtcDescription,
            title = title,
            description = description,
            steps = steps,
            partsNeeded = partsNeeded,
            estimatedTimeMinutes = estimatedTimeMinutes,
            difficulty = difficulty,
            estimatedCost = estimatedCost,
            contributorId = contributorId,
            contributorName = contributorName,
            vehicleBrand = vehicleBrand,
            vehicleModel = vehicleModel,
        )
        solutions.add(solution)
        return solution
    }

    // ─── Record Outcome (feedback loop) ───

    fun recordOutcome(
        solutionId: String,
        mechanicId: String,
        vehicleId: String,
        dtcCode: String,
        success: Boolean,
        dtcClearedAfterRepair: Boolean,
        actualTimeMinutes: Int? = null,
        actualCost: Long? = null,
        notes: String = "",
    ): RepairOutcome? {
        val idx = solutions.indexOfFirst { it.solutionId == solutionId }
        if (idx < 0) return null

        val outcome = RepairOutcome(
            outcomeId = "out-${System.currentTimeMillis()}-${outcomes.size}",
            solutionId = solutionId,
            mechanicId = mechanicId,
            vehicleId = vehicleId,
            dtcCode = dtcCode.uppercase(),
            success = success,
            dtcClearedAfterRepair = dtcClearedAfterRepair,
            actualTimeMinutes = actualTimeMinutes,
            actualCost = actualCost,
            notes = notes,
        )
        outcomes.add(outcome)

        // Update solution metrics
        val sol = solutions[idx]
        solutions[idx] = sol.copy(
            totalAttempts = sol.totalAttempts + 1,
            successCount = sol.successCount + if (success) 1 else 0,
            failureCount = sol.failureCount + if (!success) 1 else 0,
            lastUsedEpochMs = System.currentTimeMillis(),
        )
        return outcome
    }

    // ─── Vote ───

    fun upvote(solutionId: String): Boolean {
        val idx = solutions.indexOfFirst { it.solutionId == solutionId }
        if (idx < 0) return false
        solutions[idx] = solutions[idx].copy(upvotes = solutions[idx].upvotes + 1)
        return true
    }

    fun downvote(solutionId: String): Boolean {
        val idx = solutions.indexOfFirst { it.solutionId == solutionId }
        if (idx < 0) return false
        solutions[idx] = solutions[idx].copy(downvotes = solutions[idx].downvotes + 1)
        return true
    }

    // ─── Search Solutions ───

    fun findSolutions(dtcCode: String): List<DtcSolution> =
        solutions.filter { it.dtcCode == dtcCode.uppercase() }
            .sortedByDescending { it.relevanceScore }

    fun findSolutionsForVehicle(
        dtcCode: String,
        brand: String = "",
        model: String = "",
        year: Int? = null,
    ): List<DtcSolution> {
        return findSolutions(dtcCode).filter { sol ->
            val brandMatch = sol.vehicleBrand.isBlank() || sol.vehicleBrand.equals(brand, ignoreCase = true)
            val modelMatch = sol.vehicleModel.isBlank() || sol.vehicleModel.equals(model, ignoreCase = true)
            val yearMatch = year == null || (sol.vehicleYearMin == null && sol.vehicleYearMax == null) ||
                (year >= (sol.vehicleYearMin ?: 0) && year <= (sol.vehicleYearMax ?: 9999))
            brandMatch && modelMatch && yearMatch
        }
    }

    fun getTopSolution(dtcCode: String): DtcSolution? = findSolutions(dtcCode).firstOrNull()

    // ─── Contributor Stats ───

    fun getContributorStats(mechanicId: String): ContributorStats {
        val mySolutions = solutions.filter { it.contributorId == mechanicId }
        val myOutcomes = outcomes.filter { it.mechanicId == mechanicId }
        val avgSuccess = if (myOutcomes.isNotEmpty()) {
            myOutcomes.count { it.success }.toDouble() / myOutcomes.size
        } else 0.0
        val topDtcs = mySolutions.groupBy { it.dtcCode }
            .entries.sortedByDescending { it.value.size }
            .take(5).map { it.key }

        val rank = when {
            mySolutions.size >= 100 -> ContributorRank.SAGE
            mySolutions.size >= 50 -> ContributorRank.MASTER
            mySolutions.size >= 20 -> ContributorRank.SPECIALIST
            mySolutions.size >= 5 -> ContributorRank.PRACTITIONER
            else -> ContributorRank.APPRENTICE
        }

        return ContributorStats(mechanicId, mySolutions.size, myOutcomes.size, avgSuccess, topDtcs, rank)
    }

    // ─── Queries ───

    fun getMostCommonDtcs(limit: Int = 10): List<Pair<String, Int>> =
        solutions.groupBy { it.dtcCode }
            .map { (code, sols) -> code to sols.sumOf { it.totalAttempts } }
            .sortedByDescending { it.second }
            .take(limit)

    val totalSolutions: Int get() = solutions.size
    val totalOutcomes: Int get() = outcomes.size
    val totalContributors: Int get() = solutions.map { it.contributorId }.distinct().size
}
