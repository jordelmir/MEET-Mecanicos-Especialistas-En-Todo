package com.elysium369.meet.ai.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AiCommercialPlan {
    FREE,
    PRO,
    WORKSHOP,
    FLEET,
    CREDIT
}

@Serializable
@JvmInline
value class CostMicrosUsd(val value: Long) {
    operator fun plus(other: CostMicrosUsd): CostMicrosUsd = CostMicrosUsd(Math.addExact(value, other.value))
}

@Serializable
enum class AiCaseStatus {
    OPEN,
    RESOLVED,
    CLOSED,
    BUDGET_EXHAUSTED,
    BLOCKED
}

@Serializable
data class AiQuotaSnapshot(
    val plan: AiCommercialPlan,
    val periodKey: String,
    val includedCases: Int,
    val consumedCases: Int,
    val purchasedCases: Int
) {
    val remainingCases: Int get() = (includedCases + purchasedCases - consumedCases).coerceAtLeast(0)
    val hasQuota: Boolean get() = remainingCases > 0
}

@Serializable
data class AiDiagnosticCase(
    val id: String,
    val ownerId: String,
    val vehicleId: String,
    val diagnosticCaseId: String? = null,
    val plan: AiCommercialPlan,
    val status: AiCaseStatus,
    val turnsUsed: Int = 0,
    val accumulatedTokens: Long = 0L,
    val accumulatedCostMicrosUsd: Long = 0L,
    val originRepairIntentId: String? = null,
    val downstreamServiceRequestId: String? = null,
    val startedAt: String,
    val closedAt: String? = null
)

@Serializable
data class VehicleEvidencePacket(
    val vehicleId: String,
    val vehicleName: String,
    val dtcCodes: List<String> = emptyList(),
    val freezeFrameSignals: Map<String, String> = emptyMap(),
    val liveSignals: Map<String, String> = emptyMap(),
    val mode06Available: Boolean = false,
    val unavailableEvidence: List<String> = emptyList(),
    val symptoms: List<String> = emptyList()
)

@Serializable
data class AiHypothesis(
    val cause: String,
    val confidence: Double,
    val evidenceFor: List<String> = emptyList(),
    val evidenceAgainst: List<String> = emptyList(),
    val truthState: String = "DERIVED"
)

@Serializable
data class AiDiagnosticTest(
    val test: String,
    val reason: String,
    val safetyLevel: String = "LOW",
    val requiresPhysicalVerification: Boolean = true
)

@Serializable
data class DiagnosticAiAnswer(
    val summary: String,
    val hypotheses: List<AiHypothesis> = emptyList(),
    val recommendedTests: List<AiDiagnosticTest> = emptyList(),
    val partsRecommendationAllowed: Boolean = false,
    val safetyWarnings: List<String> = emptyList(),
    val abstain: Boolean = false,
    val evidenceRefs: List<String> = emptyList()
)

@Serializable
data class OpenAiDiagnosticCaseRequest(
    val vehicleId: String,
    val diagnosticCaseId: String? = null
)

@Serializable
data class SubmitAiCaseTurnRequest(
    val caseId: String,
    val message: String,
    val evidenceRefs: List<String> = emptyList()
)

@Serializable
data class AiCaseTurnResponse(
    val caseId: String,
    val turnId: String,
    val answer: DiagnosticAiAnswer,
    val quota: AiQuotaSnapshot,
    val modelUsed: String,
    val providerUsed: String,
    val costMicrosUsd: Long,
    val createdAt: String
)

@Serializable
data class AiRouteDecision(
    val providerId: String,
    val modelId: String,
    val reasoningEffort: String,
    val maxOutputTokens: Int,
    val allowEscalation: Boolean,
    val reason: String
)

@Serializable
data class AiRoutingContext(
    val taskClass: String,
    val plan: AiCommercialPlan,
    val caseCostMicrosUsd: Long,
    val caseBudgetMicrosUsd: Long,
    val complexity: String,
    val evidenceQuality: String
)
