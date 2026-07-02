package com.elysium369.meet.diagnosis

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticValue
import kotlinx.serialization.Serializable

/**
 * Entrada del motor de diagnóstico probabilístico.
 * Combina DTC, freeze frame, live PIDs, vehículo, síntomas e historial.
 */
@Serializable
data class DiagnosisContext(
    val dtcCode: String,
    val dtcStatus: DtcStatus = DtcStatus.ACTIVE,
    val freezeFrame: Map<String, Double> = emptyMap(),
    val livePids: Map<String, DiagnosticValue<Double>> = emptyMap(),
    val vehicleMake: String = "Generic",
    val vehicleModel: String = "Generic",
    val vehicleYear: Int? = null,
    val reportedSymptoms: List<String> = emptyList(),
    val repairHistory: List<String> = emptyList(),
    val provenance: DiagnosticProvenance,
    val communitySignals: Int = 0  // opcional: reports validados de la comunidad
) {
    init {
        require(dtcCode.isNotBlank()) { "dtcCode cannot be blank" }
    }

    /** Multiplicador de confianza basado en provenance. SinEnlace/Manual/Simulated reducen. */
    val confidenceMultiplier: Double
        get() = when (provenance) {
            is DiagnosticProvenance.Real -> 1.0
            is DiagnosticProvenance.Offline -> 0.85
            is DiagnosticProvenance.Inferred -> 0.7
            is DiagnosticProvenance.ManualEntry -> 0.6
            is DiagnosticProvenance.SinEnlace -> 0.5
            is DiagnosticProvenance.Simulated -> 0.3
            is DiagnosticProvenance.NoSoportado -> 0.4
            is DiagnosticProvenance.RequiereHardware -> 0.5
        }
}

@Serializable
enum class DtcStatus { ACTIVE, PENDING, PERMANENT, HISTORICAL }

/**
 * Causa probable individual con toda la metadata necesaria.
 * probability es 0..1; las probabilidades del reporte NO necesariamente suman 1.0
 * (representan likelihoods independientes, no partición).
 */
@Serializable
data class ProbableCause(
    val cause: String,
    val probability: Double,
    val severity: Int,  // 0..4
    val estimatedCostUsd: Double? = null,
    val difficulty: ProcedureDifficulty = ProcedureDifficulty.MEDIUM,
    val requiredTools: List<String> = emptyList(),
    val mandatoryTests: List<String> = emptyList(),
    val possibleFalsePositives: List<String> = emptyList(),
    val doNotReplaceYet: List<String> = emptyList()
) {
    init {
        require(probability.isFinite() && probability in 0.0..1.0) {
            "probability must be in [0,1]"
        }
        require(severity in 0..4) { "severity must be in [0,4]" }
    }
}

@Serializable
enum class ProcedureDifficulty { EASY, MEDIUM, HARD, EXPERT_ONLY }

/**
 * Reporte de diagnóstico probabilístico.
 *
 * Regla crítica: nunca `mustReplace = true`. Decir "probable causa X requiere test Y
 * antes de condenar parte Z".
 *
 * Confidence global = probabilidad promedio ponderada × multiplier del provenance.
 */
@Serializable
data class ProbabilisticDiagnosisReport(
    val dtcCode: String,
    val probableCauses: List<ProbableCause>,
    val confidenceOverall: Double,
    val recommendedNextTest: String,
    val safetyWarnings: List<String>,
    val provenance: DiagnosticProvenance,
    val educationalExplanation: String
) {
    init {
        require(confidenceOverall.isFinite() && confidenceOverall in 0.0..1.0)
    }

    /** Causa más probable. */
    val topCause: ProbableCause?
        get() = probableCauses.maxByOrNull { it.probability }

    /** ¿Hay pruebas pendientes antes de reemplazar nada? */
    val hasOpenTests: Boolean
        get() = probableCauses.any { it.mandatoryTests.isNotEmpty() }

    /** Lista de piezas que NO deben reemplazarse todavía. */
    val doNotReplaceYet: List<String>
        get() = probableCauses.flatMap { it.doNotReplaceYet }.distinct()
}