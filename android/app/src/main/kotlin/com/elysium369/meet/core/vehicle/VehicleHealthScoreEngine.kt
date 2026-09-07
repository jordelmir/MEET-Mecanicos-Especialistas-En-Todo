package com.elysium369.meet.core.vehicle

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  V E H I C L E   H E A L T H   S C O R E
 *  ──────────────────────────────────────────
 *  "El score crediticio de tu vehículo."
 *
 *  0-1000 puntos basado en:
 *  ┌───────────────────────────────────────────────┐
 *  │  30% — DTCs activos y resueltos               │
 *  │  25% — Historial de mantenimiento             │
 *  │  20% — Edad y kilometraje                     │
 *  │  15% — Historial de reparaciones              │
 *  │  10% — Incidentes (accidentes, inundación)    │
 *  └───────────────────────────────────────────────┘
 *
 *  Tiers:
 *  🟢 900-1000: Excelente — "Como nuevo"
 *  🔵 750-899:  Bueno — "Bien mantenido"
 *  🟡 600-749:  Regular — "Necesita atención"
 *  🟠 400-599:  Bajo — "Múltiples problemas"
 *  🔴 0-399:    Crítico — "Riesgo de falla"
 *
 *  Verified by anyone with QR.
 *  Affects: resale value, fleet decisions, insurance rates.
 *  UNIQUE IN THE WORLD — no app does this.
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Health Tier ───

enum class HealthTier {
    EXCELLENT,  // 900-1000
    GOOD,       // 750-899
    FAIR,       // 600-749
    LOW,        // 400-599
    CRITICAL,   // 0-399
}

val HealthTier.displayLabel: String
    get() = when (this) {
        HealthTier.EXCELLENT -> "Excelente — Como nuevo"
        HealthTier.GOOD -> "Bueno — Bien mantenido"
        HealthTier.FAIR -> "Regular — Necesita atención"
        HealthTier.LOW -> "Bajo — Múltiples problemas"
        HealthTier.CRITICAL -> "Crítico — Riesgo de falla"
    }

val HealthTier.emoji: String
    get() = when (this) {
        HealthTier.EXCELLENT -> "🟢"
        HealthTier.GOOD -> "🔵"
        HealthTier.FAIR -> "🟡"
        HealthTier.LOW -> "🟠"
        HealthTier.CRITICAL -> "🔴"
    }

// ─── Score Factors ───

@Serializable
data class HealthScoreFactors(
    // DTC Factor (30%)
    val activeDtcCount: Int = 0,
    val resolvedDtcCount: Int = 0,
    val criticalDtcCount: Int = 0,         // P0xxx powertrain critical
    // Maintenance Factor (25%)
    val maintenanceOnSchedule: Boolean = true,
    val lastMaintenanceDaysAgo: Int = 0,
    val missedMaintenanceCount: Int = 0,
    // Age & Mileage Factor (20%)
    val vehicleAgeYears: Int = 0,
    val totalMileageKm: Long = 0,
    val averageAnnualKm: Long = 0,
    // Repair History Factor (15%)
    val totalRepairs: Int = 0,
    val majorRepairs: Int = 0,             // Engine, transmission, etc.
    val recurringIssues: Int = 0,          // Same DTC came back
    // Incident Factor (10%)
    val totalIncidents: Int = 0,
    val accidentCount: Int = 0,
    val floodDamage: Boolean = false,
    val theftRecovered: Boolean = false,
)

// ─── Score Breakdown ───

@Serializable
data class HealthScoreBreakdown(
    val dtcScore: Int,              // 0-300
    val maintenanceScore: Int,      // 0-250
    val ageMileageScore: Int,       // 0-200
    val repairScore: Int,           // 0-150
    val incidentScore: Int,         // 0-100
    val totalScore: Int,            // 0-1000
    val tier: HealthTier,
    val topIssues: List<String>,    // "2 DTCs activos", "Mantenimiento vencido"
    val recommendations: List<String>,
)

// ─── Score History Entry ───

@Serializable
data class HealthScoreEntry(
    val vehicleId: String,
    val score: Int,
    val tier: HealthTier,
    val factors: HealthScoreFactors,
    val breakdown: HealthScoreBreakdown,
    val computedAtEpochMs: Long = System.currentTimeMillis(),
)

// ─── Engine ───

class VehicleHealthScoreEngine {

    private val scoreHistory = mutableMapOf<String, MutableList<HealthScoreEntry>>()

    // ─── Compute Score ───

    fun computeScore(vehicleId: String, factors: HealthScoreFactors): HealthScoreEntry {
        val dtcScore = computeDtcScore(factors)
        val maintenanceScore = computeMaintenanceScore(factors)
        val ageMileageScore = computeAgeMileageScore(factors)
        val repairScore = computeRepairScore(factors)
        val incidentScore = computeIncidentScore(factors)

        val total = dtcScore + maintenanceScore + ageMileageScore + repairScore + incidentScore
        val tier = tierFromScore(total)
        val issues = identifyIssues(factors)
        val recommendations = generateRecommendations(factors, tier)

        val breakdown = HealthScoreBreakdown(
            dtcScore = dtcScore,
            maintenanceScore = maintenanceScore,
            ageMileageScore = ageMileageScore,
            repairScore = repairScore,
            incidentScore = incidentScore,
            totalScore = total,
            tier = tier,
            topIssues = issues,
            recommendations = recommendations,
        )

        val entry = HealthScoreEntry(
            vehicleId = vehicleId,
            score = total,
            tier = tier,
            factors = factors,
            breakdown = breakdown,
        )

        scoreHistory.getOrPut(vehicleId) { mutableListOf() }.add(entry)
        return entry
    }

    // ─── Score Trend ───

    fun getScoreTrend(vehicleId: String): List<HealthScoreEntry> =
        scoreHistory[vehicleId]?.sortedBy { it.computedAtEpochMs } ?: emptyList()

    fun getLatestScore(vehicleId: String): HealthScoreEntry? =
        scoreHistory[vehicleId]?.maxByOrNull { it.computedAtEpochMs }

    fun getScoreDelta(vehicleId: String): Int? {
        val history = getScoreTrend(vehicleId)
        if (history.size < 2) return null
        return history.last().score - history[history.size - 2].score
    }

    // ─── Share ───

    fun generateShareText(vehicleId: String): String {
        val entry = getLatestScore(vehicleId) ?: return "Sin puntuación disponible"
        val delta = getScoreDelta(vehicleId)
        return buildString {
            appendLine("🏥 Health Score Vehicular — ELYSIUM")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("${entry.tier.emoji} Puntuación: ${entry.score}/1000")
            appendLine("📊 ${entry.tier.displayLabel}")
            delta?.let {
                val arrow = if (it >= 0) "↑" else "↓"
                appendLine("📈 Tendencia: $arrow${kotlin.math.abs(it)} puntos")
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🔧 DTCs: ${entry.breakdown.dtcScore}/300")
            appendLine("📋 Mantenimiento: ${entry.breakdown.maintenanceScore}/250")
            appendLine("📅 Edad/Km: ${entry.breakdown.ageMileageScore}/200")
            appendLine("🔩 Reparaciones: ${entry.breakdown.repairScore}/150")
            appendLine("⚠️ Incidentes: ${entry.breakdown.incidentScore}/100")
            if (entry.breakdown.topIssues.isNotEmpty()) {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("Problemas:")
                entry.breakdown.topIssues.forEach { appendLine("  · $it") }
            }
        }
    }

    val totalVehiclesScored: Int get() = scoreHistory.size

    // ─── Score Calculations ───

    private fun computeDtcScore(f: HealthScoreFactors): Int {
        var score = 300
        score -= f.activeDtcCount * 40           // Each active DTC = -40
        score -= f.criticalDtcCount * 60         // Critical DTCs = -60 each
        score += (f.resolvedDtcCount * 5).coerceAtMost(50) // Bonus for resolved
        return score.coerceIn(0, 300)
    }

    private fun computeMaintenanceScore(f: HealthScoreFactors): Int {
        var score = 250
        if (!f.maintenanceOnSchedule) score -= 80
        score -= f.missedMaintenanceCount * 30
        if (f.lastMaintenanceDaysAgo > 365) score -= 50
        else if (f.lastMaintenanceDaysAgo > 180) score -= 20
        return score.coerceIn(0, 250)
    }

    private fun computeAgeMileageScore(f: HealthScoreFactors): Int {
        var score = 200
        // Age penalty
        when {
            f.vehicleAgeYears > 20 -> score -= 80
            f.vehicleAgeYears > 15 -> score -= 50
            f.vehicleAgeYears > 10 -> score -= 30
            f.vehicleAgeYears > 5 -> score -= 10
        }
        // Mileage penalty
        when {
            f.totalMileageKm > 300_000 -> score -= 80
            f.totalMileageKm > 200_000 -> score -= 50
            f.totalMileageKm > 100_000 -> score -= 25
        }
        return score.coerceIn(0, 200)
    }

    private fun computeRepairScore(f: HealthScoreFactors): Int {
        var score = 150
        score -= f.majorRepairs * 25
        score -= f.recurringIssues * 20
        if (f.totalRepairs > 10) score -= 15
        return score.coerceIn(0, 150)
    }

    private fun computeIncidentScore(f: HealthScoreFactors): Int {
        var score = 100
        score -= f.accidentCount * 30
        if (f.floodDamage) score -= 50
        if (f.theftRecovered) score -= 20
        return score.coerceIn(0, 100)
    }

    private fun tierFromScore(score: Int): HealthTier = when {
        score >= 900 -> HealthTier.EXCELLENT
        score >= 750 -> HealthTier.GOOD
        score >= 600 -> HealthTier.FAIR
        score >= 400 -> HealthTier.LOW
        else -> HealthTier.CRITICAL
    }

    private fun identifyIssues(f: HealthScoreFactors): List<String> = buildList {
        if (f.activeDtcCount > 0) add("${f.activeDtcCount} DTC(s) activos")
        if (f.criticalDtcCount > 0) add("${f.criticalDtcCount} DTC(s) críticos")
        if (!f.maintenanceOnSchedule) add("Mantenimiento fuera de agenda")
        if (f.missedMaintenanceCount > 0) add("${f.missedMaintenanceCount} mantenimientos perdidos")
        if (f.majorRepairs > 2) add("${f.majorRepairs} reparaciones mayores")
        if (f.recurringIssues > 0) add("${f.recurringIssues} problema(s) recurrente(s)")
        if (f.accidentCount > 0) add("${f.accidentCount} accidente(s)")
        if (f.floodDamage) add("Daño por inundación")
        if (f.totalMileageKm > 200_000) add("Alto kilometraje: ${f.totalMileageKm} km")
    }

    private fun generateRecommendations(f: HealthScoreFactors, tier: HealthTier): List<String> = buildList {
        if (f.activeDtcCount > 0) add("Resolver ${f.activeDtcCount} código(s) de falla activos")
        if (!f.maintenanceOnSchedule) add("Poner al día el mantenimiento preventivo")
        if (f.lastMaintenanceDaysAgo > 180) add("Programar revisión general")
        if (f.recurringIssues > 0) add("Investigar causa raíz de problemas recurrentes")
        if (tier == HealthTier.CRITICAL) add("Consultar mecánico certificado urgentemente")
        if (f.totalMileageKm > 150_000 && f.majorRepairs == 0) {
            add("Inspección preventiva de motor y transmisión recomendada")
        }
    }
}
