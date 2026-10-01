package com.elysium369.meet.core.agent.capability.mechanic

import com.elysium369.meet.domain.diagnostics.VehicleEvidenceGraph
import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M A S T E R   M E C H A N I C   E V I D E N C E   C O N T R A C T S
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §6, §17, §18
 * ══════════════════════════════════════════════════════════════════════
 */

@Serializable
data class ObservedDtc(
    val code: String,
    val status: String = "ACTIVE",
    val description: String = "",
)

@Serializable
data class ObservedMeasurement(
    val pid: String,
    val name: String,
    val value: Double,
    val unit: String,
)

@Serializable
data class Mode06Observation(
    val testId: String,
    val componentId: String,
    val value: Double,
    val minLimit: Double?,
    val maxLimit: Double?,
    val passed: Boolean,
)

@Serializable
data class FreezeFrameEvidence(
    val dtc: String,
    val frameData: Map<String, String>,
)

@Serializable
data class ReadinessEvidence(
    val monitorsComplete: Int,
    val monitorsTotal: Int,
    val isReadyForItv: Boolean,
)

data class DiagnosticEvidenceSnapshot(
    val vehicleId: String,
    val bindingId: String,
    val dtcs: List<ObservedDtc>,
    val measurements: List<ObservedMeasurement> = emptyList(),
    val mode06: List<Mode06Observation> = emptyList(),
    val freezeFrames: List<FreezeFrameEvidence> = emptyList(),
    val readiness: ReadinessEvidence? = null,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
)

interface DiagnosticEvidenceProvider {
    suspend fun capture(vehicleId: String): Result<DiagnosticEvidenceSnapshot>
}

data class DiagnosticTestRecommendation(
    val testId: String,
    val name: String,
    val expectedInformationGain: Double,
    val estimatedCostMinor: Long = 0L,
    val estimatedMinutes: Int = 10,
    val riskPenalty: Double = 0.0,
) {
    val score: Double
        get() = expectedInformationGain / (1.0 + estimatedMinutes / 10.0 + riskPenalty)
}

data class DiagnosticHypothesis(
    val id: String,
    val title: String,
    val probability: Double,
    val supportingEvidenceIds: Set<String>,
    val contradictingEvidenceIds: Set<String>,
    val nextDiscriminatingTest: DiagnosticTestRecommendation?,
)

interface DiagnosticReasoningEngine {
    fun rank(
        evidence: DiagnosticEvidenceSnapshot,
        graph: VehicleEvidenceGraph,
    ): List<DiagnosticHypothesis>
}
