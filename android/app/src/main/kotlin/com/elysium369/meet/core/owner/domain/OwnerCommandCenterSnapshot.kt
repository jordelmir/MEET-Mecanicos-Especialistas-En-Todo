package com.elysium369.meet.core.owner.domain

import com.elysium369.meet.core.finance.Money
import kotlinx.serialization.Serializable

/**
 * Data freshness classifications as dictated by ELYSIUM VANGUARD MASTER ORDER OMEGA §46.
 */
enum class DataFreshness {
    LIVE,
    STALE,
    DEGRADED,
    UNAVAILABLE,
}

@Serializable
data class OwnerMoneySnapshot(
    val totalGmv: Money = Money.zero(),
    val platformRevenue: Money = Money.zero(),
    val netRevenue: Money = Money.zero(),
)

@Serializable
data class OwnerMobilitySnapshot(
    val activeUsers: Int = 0,
    val activeProviders: Int = 0,
    val completedTrips: Int = 0,
)

@Serializable
data class OwnerTrustSnapshot(
    val alertCount: Int = 0,
    val expiringDocumentsCount: Int = 0,
)

@Serializable
data class OwnerSystemHealthSnapshot(
    val apiAvailabilityPercent: Double = 100.0,
    val p95LatencyMs: Long = 0L,
    val outboxLagSeconds: Long = 0L,
    val deadLetterCount: Int = 0,
    val workersHealthy: Boolean = true,
)

@Serializable
data class OwnerCaseSummary(
    val caseId: String,
    val severity: String,
    val title: String,
    val description: String,
    val requiresOwnerApproval: Boolean,
    val suggestedAction: String? = null,
)

/**
 * Authoritative Owner Command Center Snapshot.
 * Zero synthetic figures. Must originate from authoritative server RPC or real local state.
 */
@Serializable
data class OwnerCommandCenterSnapshot(
    val asOfEpochMs: Long = System.currentTimeMillis(),
    val freshness: DataFreshness = DataFreshness.UNAVAILABLE,
    val money: OwnerMoneySnapshot = OwnerMoneySnapshot(),
    val mobility: OwnerMobilitySnapshot = OwnerMobilitySnapshot(),
    val trust: OwnerTrustSnapshot = OwnerTrustSnapshot(),
    val systemHealth: OwnerSystemHealthSnapshot = OwnerSystemHealthSnapshot(),
    val pendingCases: List<OwnerCaseSummary> = emptyList(),
    val servicesCompleted: Int = 0,
    val towCallsCompleted: Int = 0,
)
