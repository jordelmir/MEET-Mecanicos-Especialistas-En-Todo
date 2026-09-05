package com.elysium369.meet.ptt

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for the LiveKit Server-to-Server Media Plane Controller.
 * LiveKit server-side enforces canPublish on the WebRTC SFU room.
 */
fun interface LiveKitMediaPermissionController {
    suspend fun updatePublishPermission(
        channelId: String,
        principalId: String,
        canPublish: Boolean,
    ): Boolean
}

/**
 * FloorAuthority — Authoritative Floor Arbitration Engine for Vanguard PTT.
 *
 * Laws:
 * - FLOOR AUTHORITY DECIDES WHO MAY TRANSMIT.
 * - TWO-PHASE GRANT: DB Reservation -> LiveKit Server update -> FloorLease(GRANTED).
 * - FENCING TOKEN: Monotonically incrementing token per channel. Stale tokens are strictly rejected.
 * - IDEMPOTENCY: Re-issuing the same requestId returns the cached result without creating a new lease.
 * - PREEMPTION: High-priority (e.g. EMERGENCY) preempts lower-priority holder with clean media revocation.
 */
@Singleton
class FloorAuthority @Inject constructor(
    private val liveKitController: LiveKitMediaPermissionController,
) {
    private val channelMutexes = ConcurrentHashMap<String, Mutex>()
    private val currentLeases = ConcurrentHashMap<String, FloorLease>() // channelId -> active lease
    private val channelEpochs = ConcurrentHashMap<String, Long>() // channelId -> monotonic fencingToken counter
    private val requestCache = ConcurrentHashMap<String, FloorArbitrationResult>() // requestId -> result

    private fun getMutex(channelId: String): Mutex =
        channelMutexes.computeIfAbsent(channelId) { Mutex() }

    suspend fun requestFloor(
        request: FloorRequest,
        nowEpochMs: Long = System.currentTimeMillis(),
        leaseDurationMs: Long = 30_000L,
    ): FloorArbitrationResult {
        // Check idempotency cache first
        requestCache[request.requestId]?.let { return it }

        val mutex = getMutex(request.channelId)
        return mutex.withLock {
            // Re-check idempotency under lock
            requestCache[request.requestId]?.let { return@withLock it }

            val currentLease = currentLeases[request.channelId]

            // 1. Check if floor is currently occupied
            if (currentLease != null && currentLease.state.isHoldingFloor && !currentLease.isExpired(nowEpochMs)) {
                // Check preemption
                if (request.priority.canPreempt(currentLease.priority)) {
                    // Preempt current holder in two phases
                    val revokeSuccess = liveKitController.updatePublishPermission(
                        channelId = request.channelId,
                        principalId = currentLease.holderPrincipalId,
                        canPublish = false,
                    )
                    val preemptedLease = currentLease.copy(
                        state = FloorState.PREEMPTING,
                        revocationReason = "PREEMPTED_BY_${request.priority.name}",
                    )
                    currentLeases[request.channelId] = preemptedLease

                    // Now proceed to grant to new requester
                    return@withLock executeTwoPhaseGrant(request, nowEpochMs, leaseDurationMs)
                } else {
                    val result = FloorArbitrationResult.Denied("FLOOR_BUSY_HELD_BY_${currentLease.holderPrincipalId}")
                    requestCache[request.requestId] = result
                    return@withLock result
                }
            }

            // 2. Channel is free (or expired) -> Execute Two-Phase Grant
            executeTwoPhaseGrant(request, nowEpochMs, leaseDurationMs)
        }
    }

    private suspend fun executeTwoPhaseGrant(
        request: FloorRequest,
        nowEpochMs: Long,
        leaseDurationMs: Long,
    ): FloorArbitrationResult {
        val nextFencingToken = (channelEpochs[request.channelId] ?: 0L) + 1L

        // Phase 1: DB Reservation
        val reservedLease = FloorLease(
            floorLeaseId = UUID.randomUUID().toString(),
            channelId = request.channelId,
            holderPrincipalId = request.principalId,
            holderDeviceId = request.deviceId,
            requestId = request.requestId,
            priority = request.priority,
            state = FloorState.GRANTING_MEDIA_PERMISSION,
            fencingToken = nextFencingToken,
            grantedAt = nowEpochMs,
            expiresAt = nowEpochMs + leaseDurationMs,
        )
        currentLeases[request.channelId] = reservedLease

        // Phase 2: LiveKit Server Permission update
        val liveKitGranted = liveKitController.updatePublishPermission(
            channelId = request.channelId,
            principalId = request.principalId,
            canPublish = true,
        )

        if (!liveKitGranted) {
            // LiveKit failed -> DO NOT emit FloorGranted. Revert reservation.
            val failedLease = reservedLease.copy(state = FloorState.FAILED_SAFE, revocationReason = "LIVEKIT_PERMISSION_FAILED")
            currentLeases[request.channelId] = failedLease
            val failure = FloorArbitrationResult.Denied("LIVEKIT_MEDIA_PERMISSION_FAILED")
            requestCache[request.requestId] = failure
            return failure
        }

        // Commit GRANTED lease and increment channel epoch
        channelEpochs[request.channelId] = nextFencingToken
        val grantedLease = reservedLease.copy(state = FloorState.GRANTED)
        currentLeases[request.channelId] = grantedLease

        val success = FloorArbitrationResult.Granted(grantedLease)
        requestCache[request.requestId] = success
        return success
    }

    suspend fun releaseFloor(
        channelId: String,
        principalId: String,
        deviceId: String,
        fencingToken: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<Unit> {
        val mutex = getMutex(channelId)
        return mutex.withLock {
            val current = currentLeases[channelId]
                ?: return@withLock Result.failure(IllegalStateException("NO_ACTIVE_LEASE_FOR_CHANNEL"))

            // Fencing Token Invariant Check: Reject stale tokens
            val activeEpoch = channelEpochs[channelId] ?: 0L
            if (fencingToken != activeEpoch || current.fencingToken != fencingToken) {
                return@withLock Result.failure(IllegalStateException("REJECTED_STALE_FENCING_TOKEN: token=$fencingToken activeEpoch=$activeEpoch"))
            }

            if (current.holderPrincipalId != principalId || current.holderDeviceId != deviceId) {
                return@withLock Result.failure(IllegalStateException("CALLER_IS_NOT_LEASE_HOLDER"))
            }

            // Phase 1: Transition to REVOKING
            currentLeases[channelId] = current.copy(state = FloorState.REVOKING)

            // Phase 2: Revoke LiveKit publication permission
            liveKitController.updatePublishPermission(channelId, principalId, canPublish = false)

            // Phase 3: Finalize RELEASED
            currentLeases[channelId] = current.copy(state = FloorState.RELEASED)
            Result.success(Unit)
        }
    }

    fun getCurrentLease(channelId: String): FloorLease? = currentLeases[channelId]

    fun getCurrentChannelEpoch(channelId: String): Long = channelEpochs[channelId] ?: 0L
}
