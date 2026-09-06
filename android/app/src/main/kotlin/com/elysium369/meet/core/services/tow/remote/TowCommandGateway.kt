package com.elysium369.meet.core.services.tow.remote

import com.elysium369.meet.core.services.tow.TowCapabilities
import com.elysium369.meet.core.services.tow.TowState
import java.util.UUID

/**
 * Result of executing an authoritative Tow operation against the server.
 */
sealed interface TowServerResult {
    data class Accepted(
        val jobId: String,
        val state: TowState,
        val version: Long,
        val assignedOperatorId: UUID? = null,
        val assignedTowUnitId: UUID? = null,
        val rawResponse: String? = null,
    ) : TowServerResult

    data class Conflict(
        val jobId: String,
        val errorCode: String,
        val message: String,
        val currentVersion: Long?,
        val currentState: TowState?,
    ) : TowServerResult

    data class Rejected(
        val errorCode: String,
        val message: String,
        val retryable: Boolean = false,
    ) : TowServerResult

    data class NetworkFailure(
        val error: Throwable,
    ) : TowServerResult
}

/**
 * Authoritative command to request a new roadside tow job over the network.
 */
data class RequestTowRemoteCommand(
    val vehicleSummary: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val pickupAddress: String,
    val pickupAccuracyMeters: Float? = null,
    val destLat: Double? = null,
    val destLng: Double? = null,
    val destAddress: String? = null,
    val requiredCapabilities: Set<TowCapabilities> = emptySet(),
    val vehicleVin: String? = null,
    val notes: String? = null,
    val quotedPriceMinor: Long? = null,
    val idempotencyKey: String,
    val requestHash: String,
    val correlationId: String,
)

/**
 * Authoritative command to claim an open tow job via distributed CAS.
 */
data class ClaimTowRemoteCommand(
    val jobId: String,
    val towUnitId: UUID,
    val expectedVersion: Long,
    val idempotencyKey: String,
    val requestHash: String,
)

/**
 * Authoritative command to execute a lifecycle state transition.
 */
data class TransitionTowRemoteCommand(
    val jobId: String,
    val expectedVersion: Long,
    val commandType: String,
    val targetState: TowState,
    val idempotencyKey: String,
    val requestHash: String,
    val evidenceId: UUID? = null,
    val evidenceHash: String? = null,
    val notes: String? = null,
)

/**
 * Privacy-filtered discovery item for nearby tow operators.
 */
data class TowDiscoveryItem(
    val jobId: String,
    val approximateDistanceMeters: Int?,
    val urgency: String?,
    val requiredCapabilities: Set<TowCapabilities>,
    val version: Long,
)

/**
 * Gateway contract for communicating with authoritative Tow PostgreSQL/Supabase backend.
 */
interface TowCommandGateway {
    suspend fun requestTow(command: RequestTowRemoteCommand): TowServerResult
    suspend fun claimTow(command: ClaimTowRemoteCommand): TowServerResult
    suspend fun transition(command: TransitionTowRemoteCommand): TowServerResult
    suspend fun discoverJobs(towUnitId: UUID, limit: Int = 20): Result<List<TowDiscoveryItem>>
}
