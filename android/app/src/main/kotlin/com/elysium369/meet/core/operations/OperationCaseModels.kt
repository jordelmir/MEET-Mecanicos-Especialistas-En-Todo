package com.elysium369.meet.core.operations

import com.elysium369.meet.core.finance.Money
import kotlinx.serialization.Serializable
import java.util.UUID

enum class CaseState {
    OPEN,
    AUTO_PROCESSING,
    AUTO_RESOLVED,
    REQUIRES_OWNER,
    OWNER_APPROVED,
    OWNER_REJECTED,
    WAITING_EXTERNAL,
    CLOSED,
}

enum class CaseSeverity {
    P0, // Critical platform, safety, or money failure
    P1, // High business impact
    P2, // Moderate issue
    P3, // Low priority / informational
}

enum class AutonomyMode {
    AUTO,            // Fully autonomous resolution permitted
    AUTO_BOUNDED,    // Autonomous within explicit bounded limits
    OWNER_APPROVAL,  // Requires explicit human approval from platform owner
    PROHIBITED,      // Autonomous action strictly prohibited
}

@Serializable
data class OperationCase(
    val id: String = UUID.randomUUID().toString(),
    val correlationId: String,
    val domain: String, // "SRE", "FINANCE", "SECURITY", "TRUST", "SUPPORT"
    val severity: CaseSeverity,
    val state: CaseState = CaseState.OPEN,
    val title: String,
    val whatHappened: String,
    val whatAutomationDid: String,
    val evidenceSummary: String,
    val whatRemainsUncertain: String,
    val requestedOwnerAction: String,
    val consequenceOfInaction: String,
    val moneyExposure: Money? = null,
    val eventCount: Int = 1,
    val occurredAtEpochMs: Long = System.currentTimeMillis(),
    val resolvedAtEpochMs: Long? = null,
    val resolutionReason: String? = null,
) {
    val isResolved: Boolean
        get() = state == CaseState.AUTO_RESOLVED || state == CaseState.CLOSED || state == CaseState.OWNER_APPROVED || state == CaseState.OWNER_REJECTED

    val requiresHumanAttention: Boolean
        get() = state == CaseState.REQUIRES_OWNER || (severity == CaseSeverity.P0 && !isResolved)
}

@Serializable
data class CorrelatedIncident(
    val incidentId: String = UUID.randomUUID().toString(),
    val domain: String,
    val title: String,
    val severity: CaseSeverity,
    val eventCount: Int,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
    val isAutoRemediated: Boolean,
    val activeCaseId: String? = null,
)
