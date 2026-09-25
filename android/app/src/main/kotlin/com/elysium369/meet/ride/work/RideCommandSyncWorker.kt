package com.elysium369.meet.ride.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import com.elysium369.meet.BuildConfig
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elysium369.meet.data.local.dao.RideDao
import com.elysium369.meet.data.local.entities.ActiveRideSelectionEntity
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.ride.data.local.RideCommandOutboxDao
import com.elysium369.meet.ride.data.local.RideCommandOutboxEntity
import com.elysium369.meet.ride.data.local.RideOutboxStatus
import com.elysium369.meet.ride.data.remote.RideCommandGateway
import com.elysium369.meet.ride.data.remote.RideCommandGatewayResult
import com.elysium369.meet.ride.data.remote.RideCommandPayload
import com.elysium369.meet.ride.data.remote.RideQueuedCommand
import com.elysium369.meet.ride.data.remote.RideSnapshotResult
import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.domain.RideIdempotencyKey
import com.elysium369.meet.ride.observability.RideObservability
import com.elysium369.meet.ride.observability.RideTelemetryEventType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.gotrue.auth
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

@HiltWorker
class RideCommandSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val outboxDao: RideCommandOutboxDao,
    private val rideDao: RideDao,
    private val gateway: RideCommandGateway,
) : CoroutineWorker(appContext, workerParameters) {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    override suspend fun doWork(): Result {
        val sessionUserId = SupabaseModule.client.auth.currentUserOrNull()?.id
            ?: return Result.retry()
        val startedAt = System.currentTimeMillis()

        // Auto-cancel rides stuck in PENDING_PUBLICATION for > 10 minutes
        val staleThreshold = startedAt - STALE_PUBLICATION_MS
        outboxDao.findStalePendingPublications(staleThreshold).forEach { rideId ->
            outboxDao.cancelStuckPendingPublication(rideId)
            rideDao.clearActiveRideSelectionsForRide(rideId)
        }

        // Deduplicate PENDING cancels and prune old completed commands
        outboxDao.supersedeDuplicatePendingCancels(startedAt)
        outboxDao.pruneCompletedCommands(startedAt - 24L * 60 * 60 * 1000)

        outboxDao.recoverStaleLeases(
            staleBefore = startedAt - STALE_LEASE_MS,
            now = startedAt,
        )
        val commands = outboxDao.acquireBatch(startedAt, BATCH_SIZE)
        if (commands.isEmpty()) return Result.success()

        var retryNeeded = false
        commands.forEach { entity ->
            val commandStartedAt = System.currentTimeMillis()
            if (entity.actorSessionUserId != sessionUserId ||
                SupabaseModule.client.auth.currentUserOrNull()?.id != entity.actorSessionUserId) {
                retryNeeded = true
                finishRetry(
                    entity = entity,
                    code = "AUTH_SESSION_MISMATCH",
                    message = "El comando pertenece a otra sesión autenticada",
                )
                return@forEach
            }

            var command = try {
                entity.decode(json)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: RuntimeException) {
                outboxDao.finishFailure(
                    idempotencyKey = entity.idempotencyKey,
                    status = RideOutboxStatus.DEAD_LETTER,
                    nextAttemptAt = Long.MAX_VALUE,
                    errorCode = "INVALID_LOCAL_COMMAND",
                    errorMessage = error.safeMessage(),
                    correlationId = null,
                    now = System.currentTimeMillis(),
                )
                return@forEach
            }

            // A version-zero cancellation is a durable intent.
            if (command.type == RideCommandType.CANCEL && command.expectedVersion == 0L) {
                when (val snapshot = gateway.fetchSnapshot(entity.rideId)) {
                    is RideSnapshotResult.Found -> {
                        val state = snapshot.snapshot.state
                        if (state in setOf("COMPLETED", "CANCELLED", "EXPIRED", "VOIDED")) {
                            // Trip is already terminated on the server.
                            rideDao.applyServerProjection(
                                requestId = entity.rideId,
                                legacyStatus = state,
                                serverState = state,
                                serverVersion = snapshot.snapshot.version,
                                finalPriceMinor = snapshot.snapshot.finalFareMinor,
                                syncedAt = System.currentTimeMillis(),
                                correlationId = "terminal_remote_snapshot",
                            )
                            rideDao.clearActiveRideSelectionsForRide(entity.rideId)
                            outboxDao.acknowledge(
                                idempotencyKey = entity.idempotencyKey,
                                correlationId = "already_terminal",
                                now = System.currentTimeMillis(),
                            )
                            return@forEach
                        }
                        if (snapshot.snapshot.version <= 0L ||
                            outboxDao.resolveCancellationVersion(entity.idempotencyKey, snapshot.snapshot.version) != 1
                        ) {
                            retryNeeded = true
                            finishRetry(entity, "CANCEL_AWAITING_PUBLICATION", "Esperando una versión autoritativa para cancelar.")
                            return@forEach
                        }
                        command = command.copy(expectedVersion = snapshot.snapshot.version)
                    }
                    is RideSnapshotResult.NotFound -> {
                        // The trip was never published to the remote authority or was removed.
                        // Reconcile locally as cancelled, cancel unsent publications, clear active selection, and acknowledge.
                        outboxDao.cancelPublicationCommands(entity.rideId)
                        outboxDao.cancelStuckPendingPublication(entity.rideId)
                        rideDao.markRequestCancelledLocally(entity.rideId)
                        rideDao.clearActiveRideSelectionsForRide(entity.rideId)
                        outboxDao.acknowledge(
                            idempotencyKey = entity.idempotencyKey,
                            correlationId = "local_cancel_unpublished",
                            now = System.currentTimeMillis(),
                        )
                        return@forEach
                    }
                    else -> {
                        // Network/transport error during snapshot check:
                        if (entity.attemptCount >= 2) {
                            outboxDao.cancelPublicationCommands(entity.rideId)
                            outboxDao.cancelStuckPendingPublication(entity.rideId)
                            rideDao.markRequestCancelledLocally(entity.rideId)
                            rideDao.clearActiveRideSelectionsForRide(entity.rideId)
                            outboxDao.acknowledge(
                                idempotencyKey = entity.idempotencyKey,
                                correlationId = "local_cancel_fallback",
                                now = System.currentTimeMillis(),
                            )
                            return@forEach
                        }
                        retryNeeded = true
                        finishRetry(entity, "CANCEL_AWAITING_PUBLICATION", "Cancelación guardada; esperando reconciliar la publicación.")
                        return@forEach
                    }
                }
            }

            when (val result = gateway.execute(command)) {
                is RideCommandGatewayResult.Accepted -> {
                    val now = System.currentTimeMillis()
                    if (BuildConfig.DEBUG) {
                        Log.i(
                            "ElysiumRides",
                            buildString {
                                append("COMMAND_ACK")
                                append(" type=")
                                append(entity.commandType)
                                append(" ride=")
                                append(entity.rideId.take(8))
                                append(" expectedVersion=")
                                append(entity.expectedVersion)
                                append(" serverVersion=")
                                append(result.serverVersion)
                                append(" status=")
                                append(result.status)
                                append(" correlation=")
                                append(result.correlationId)
                            }
                        )
                    }
                    RideObservability.record(
                        RideObservability.event(
                            type = entity.commandType.successTelemetryType(),
                            commandId = entity.idempotencyKey,
                            tripId = entity.rideId,
                            version = result.serverVersion,
                            latencyMs = now - commandStartedAt,
                            correlationId = result.correlationId,
                        ),
                    )
                    if (entity.attemptCount > 1) {
                        RideObservability.record(
                            RideObservability.event(
                                type = RideTelemetryEventType.SYNC_RECOVERED,
                                commandId = entity.idempotencyKey,
                                tripId = entity.rideId,
                                version = result.serverVersion,
                                latencyMs = now - commandStartedAt,
                                correlationId = result.correlationId,
                            ),
                        )
                    }
                    result.data.text("boarding_pin")?.let { pin ->
                        rideDao.storeAuthoritativeBoardingPin(
                            requestId = entity.rideId,
                            pin = pin,
                            expiresAt = result.data.text("expires_at")
                                ?.let { raw ->
                                    runCatching {
                                        Instant.parse(raw).toEpochMilli()
                                    }.getOrNull()
                                },
                        )
                    }
                    if (
                        result.status in setOf(
                            "PASSENGER_ONBOARD",
                            "IN_PROGRESS",
                            "COMPLETED",
                            "CANCELLED",
                        )
                    ) {
                        rideDao.clearAuthoritativeBoardingPin(entity.rideId)
                    }
                    outboxDao.acknowledge(
                        idempotencyKey = entity.idempotencyKey,
                        correlationId = result.correlationId,
                        now = now,
                    )
                    if (
                        !reconcileSnapshot(
                            entity = entity,
                            syncState = "SYNCED",
                            correlationId = result.correlationId,
                        )
                    ) {
                        val state = result.status.toServerState()
                        rideDao.applyServerProjection(
                            requestId = entity.rideId,
                            legacyStatus = state.toLegacyStatus(),
                            serverState = state,
                            serverVersion = result.serverVersion,
                            finalPriceMinor = result.finalPriceMinor,
                            syncedAt = now,
                            correlationId = result.correlationId,
                        )
                    }
                    if (result.status.equals("CANCELLED", ignoreCase = true)) {
                        // Cancellation is terminal. Remove every local owner
                        // pointer for this trip so process death, role changes,
                        // or a later projection refresh cannot resurrect it.
                        rideDao.clearActiveRideSelectionsForRide(entity.rideId)
                    }
                    if (entity.commandType == RideCommandType.CLAIM.name &&
                        result.status in setOf("CLAIMED", "ASSIGNED")
                    ) {
                        val ownerKey = com.elysium369.meet.ride.domain.RideRoleContextPolicy
                            .selectionOwnerKey(entity.actorSessionUserId, true)
                        if (ownerKey != null) {
                            rideDao.upsertActiveRideSelection(
                                ActiveRideSelectionEntity(
                                    ownerPrincipalId = ownerKey,
                                    rideRequestId = entity.rideId,
                                    updatedAtEpochMs = now,
                                ),
                            )
                        }
                    }
                }
                is RideCommandGatewayResult.Rejected -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(
                            "ElysiumRides",
                            buildString {
                                append("COMMAND_REJECTED")
                                append(" type=")
                                append(entity.commandType)
                                append(" ride=")
                                append(entity.rideId.take(8))
                                append(" expectedVersion=")
                                append(entity.expectedVersion)
                                append(" currentVersion=")
                                append(result.currentServerVersion)
                                append(" code=")
                                append(result.code)
                                append(" retryable=")
                                append(result.retryable)
                                append(" correlation=")
                                append(result.correlationId)
                            }
                        )
                    }
                    if (entity.commandType == RideCommandType.VERIFY_BOARDING_PIN.name &&
                        result.code.startsWith("BOARDING_PIN_")
                    ) {
                        // A rejected PIN leaves the trip ARRIVED. Restore its
                        // projection so the driver can try again or request a new PIN.
                        reconcileSnapshot(entity, syncState = "SYNCED", correlationId = result.correlationId)
                    }
                    RideObservability.record(
                        RideObservability.event(
                            type = result.rejectionTelemetryType(),
                            commandId = entity.idempotencyKey,
                            tripId = entity.rideId,
                            version = result.currentServerVersion,
                            latencyMs = System.currentTimeMillis() - commandStartedAt,
                            correlationId = result.correlationId,
                            errorCode = result.code,
                        ),
                    )
                    val isConflict = result.code in CONFLICT_CODES
                    if (isConflict) {
                        rideDao.markServerConflict(
                            requestId = entity.rideId,
                            currentServerVersion = result.currentServerVersion,
                            observedAt = System.currentTimeMillis(),
                        )
                        reconcileSnapshot(
                            entity = entity,
                            syncState = "CONFLICT",
                            correlationId = result.correlationId,
                        )
                    }
                    val isTerminal = result.code == "TERMINAL_STATE"
                    if (isTerminal || result.code == "NOT_FOUND") {
                        // Server is already in a terminal state (or trip not found on remote authority).
                        // Clear active ride selections, mark local request cancelled if cancel command, and acknowledge outbox.
                        rideDao.clearActiveRideSelectionsForRide(entity.rideId)
                        if (entity.commandType == RideCommandType.CANCEL.name) {
                            rideDao.markRequestCancelledLocally(entity.rideId)
                        }
                        outboxDao.acknowledge(
                            idempotencyKey = entity.idempotencyKey,
                            correlationId = result.correlationId ?: "terminal_ack",
                            now = System.currentTimeMillis(),
                        )
                        return@forEach
                    }

                    if (
                        result.retryable &&
                        !isConflict &&
                        (entity.commandType == RideCommandType.CANCEL.name || entity.attemptCount < MAX_ATTEMPTS)
                    ) {
                        retryNeeded = true
                        finishRetry(
                            entity = entity,
                            code = result.code,
                            message = result.message,
                            correlationId = result.correlationId,
                        )
                    } else {
                        outboxDao.finishFailure(
                            idempotencyKey = entity.idempotencyKey,
                            status = when {
                                isConflict -> RideOutboxStatus.CONFLICT
                                entity.attemptCount >= MAX_ATTEMPTS ->
                                    RideOutboxStatus.DEAD_LETTER
                                else -> RideOutboxStatus.FAILED
                            },
                            nextAttemptAt = Long.MAX_VALUE,
                            errorCode = result.code,
                            errorMessage = result.message.take(300),
                            correlationId = result.correlationId,
                            now = System.currentTimeMillis(),
                        )
                        if (isPublicationDeadLettered(entity)) {
                            outboxDao.cancelStuckPendingPublication(entity.rideId)
                            rideDao.clearActiveRideSelectionsForRide(entity.rideId)
                        }
                    }
                }
                is RideCommandGatewayResult.TransportFailure -> {
                    RideObservability.record(
                        RideObservability.event(
                            type = RideTelemetryEventType.SYNC_FAILED,
                            commandId = entity.idempotencyKey,
                            tripId = entity.rideId,
                            version = entity.expectedVersion,
                            latencyMs = System.currentTimeMillis() - commandStartedAt,
                            errorCode = result.code,
                        ),
                    )
                    if (entity.commandType == RideCommandType.CANCEL.name || entity.attemptCount < MAX_ATTEMPTS) {
                        retryNeeded = true
                        finishRetry(
                            entity = entity,
                            code = result.code,
                            message = result.message,
                        )
                    } else {
                        outboxDao.finishFailure(
                            idempotencyKey = entity.idempotencyKey,
                            status = RideOutboxStatus.DEAD_LETTER,
                            nextAttemptAt = Long.MAX_VALUE,
                            errorCode = result.code,
                            errorMessage = result.message.take(300),
                            correlationId = null,
                            now = System.currentTimeMillis(),
                        )
                        if (isPublicationDeadLettered(entity)) {
                            outboxDao.cancelStuckPendingPublication(entity.rideId)
                            rideDao.clearActiveRideSelectionsForRide(entity.rideId)
                        }
                    }
                }
            }
        }
        return if (retryNeeded) Result.retry() else Result.success()
    }

    private suspend fun reconcileSnapshot(
        entity: RideCommandOutboxEntity,
        syncState: String,
        correlationId: String?,
    ): Boolean = when (
        val snapshotResult = gateway.fetchSnapshot(entity.rideId)
    ) {
        is RideSnapshotResult.Found -> {
            val snapshot = snapshotResult.snapshot
            rideDao.reconcileServerSnapshot(
                requestId = snapshot.rideId,
                legacyStatus = snapshot.state.toLegacyStatus(),
                serverState = snapshot.state,
                serverVersion = snapshot.version,
                offeredFareMinor = snapshot.offeredFareMinor,
                finalFareMinor = snapshot.finalFareMinor,
                assignedDriverId = snapshot.assignedDriverId,
                assignedVehicleId = snapshot.assignedVehicleId,
                syncState = syncState,
                syncedAt = System.currentTimeMillis(),
                correlationId = correlationId,
            )
            true
        }
        RideSnapshotResult.NotFound,
        is RideSnapshotResult.Failure,
        -> false
    }

    private suspend fun finishRetry(
        entity: RideCommandOutboxEntity,
        code: String,
        message: String,
        correlationId: String? = null,
    ) {
        val now = System.currentTimeMillis()
        outboxDao.finishFailure(
            idempotencyKey = entity.idempotencyKey,
            status = RideOutboxStatus.RETRYABLE,
            nextAttemptAt = now + RideCommandRetryPolicy.delayMillis(
                attemptCount = entity.attemptCount,
                idempotencyKey = entity.idempotencyKey,
            ),
            errorCode = code,
            errorMessage = message.take(300),
            correlationId = correlationId,
            now = now,
        )
    }

    private fun isPublicationDeadLettered(entity: RideCommandOutboxEntity): Boolean =
        entity.commandType in setOf(
            RideCommandType.PUBLISH.name,
            RideCommandType.PUBLISH_GUEST.name,
        ) && entity.attemptCount >= MAX_ATTEMPTS

    private fun RideCommandOutboxEntity.decode(
        json: Json,
    ): RideQueuedCommand {
        RideIdempotencyKey.of(idempotencyKey)
        require(
            expectedVersion > 0 ||
                (
                    expectedVersion == 0L &&
                        commandType in setOf(
                            RideCommandType.PUBLISH.name,
                            RideCommandType.PUBLISH_GUEST.name,
                            RideCommandType.CANCEL.name,
                        )
                )
        ) { "Expected version is invalid for this command" }
        require(payloadVersion > 0) { "Payload version must be positive" }
        return RideQueuedCommand(
            rideId = rideId,
            expectedVersion = expectedVersion,
            idempotencyKey = idempotencyKey,
            type = RideCommandType.valueOf(commandType),
            payloadVersion = payloadVersion,
            payload = try {
                json.decodeFromString<RideCommandPayload>(payloadJson)
            } catch (error: SerializationException) {
                throw IllegalArgumentException("Invalid command payload", error)
            },
        )
    }

    companion object {
        const val IMMEDIATE_WORK_NAME = "ride_command_outbox_immediate"
        const val PERIODIC_WORK_NAME = "ride_command_outbox_periodic"
        private const val BATCH_SIZE = 20
        private const val MAX_ATTEMPTS = 8
        private const val STALE_PUBLICATION_MS = 10 * 60 * 1000L
        // RPC calls are bounded by the network stack. A longer lease strands
        // safety-critical cancellation after process death or connectivity loss.
        private const val STALE_LEASE_MS = 2 * 60 * 1000L
        private val CONFLICT_CODES = setOf(
            "VERSION_CONFLICT",
            "ALREADY_ASSIGNED",
            "ALREADY_SETTLED",
            "TERMINAL_STATE",
        )

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RideCommandSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                // A publication can be inserted while the previous worker is
                // finishing an empty batch. KEEP would discard this wake-up
                // and leave the command waiting for the periodic worker.
                // APPEND_OR_REPLACE guarantees a follow-up pass without
                // interrupting the worker that currently owns a lease.
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}

private fun String.successTelemetryType(): RideTelemetryEventType = when (this) {
    RideCommandType.PUBLISH.name,
    RideCommandType.PUBLISH_GUEST.name,
    -> RideTelemetryEventType.RIDE_PUBLISHED
    RideCommandType.SUBMIT_OFFER.name -> RideTelemetryEventType.OFFER_SUBMITTED
    RideCommandType.ACCEPT_OFFER.name -> RideTelemetryEventType.OFFER_ACCEPTED
    RideCommandType.CLAIM.name -> RideTelemetryEventType.ASSIGNMENT_WON
    RideCommandType.DRIVER_EN_ROUTE.name -> RideTelemetryEventType.DRIVER_EN_ROUTE
    RideCommandType.DRIVER_ARRIVED.name -> RideTelemetryEventType.DRIVER_ARRIVED
    RideCommandType.ISSUE_BOARDING_PIN.name -> RideTelemetryEventType.PIN_ISSUED
    RideCommandType.VERIFY_BOARDING_PIN.name -> RideTelemetryEventType.PIN_VERIFIED
    RideCommandType.START.name -> RideTelemetryEventType.RIDE_STARTED
    RideCommandType.COMPLETE.name -> RideTelemetryEventType.RIDE_COMPLETED
    RideCommandType.CANCEL.name -> RideTelemetryEventType.RIDE_CANCELLED
    RideCommandType.SAFETY_SIGNAL.name -> RideTelemetryEventType.SAFETY_CHECK_TRIGGERED
    RideCommandType.OPEN_SUPPORT_CASE.name -> RideTelemetryEventType.SUPPORT_CASE_OPENED
    else -> RideTelemetryEventType.RIDE_CREATED
}

private fun RideCommandGatewayResult.Rejected.rejectionTelemetryType():
    RideTelemetryEventType = when (code) {
        "ALREADY_ASSIGNED" -> RideTelemetryEventType.ASSIGNMENT_LOST
        else -> RideTelemetryEventType.SYNC_FAILED
    }

object RideCommandRetryPolicy {
    private const val BASE_DELAY_MS = 15_000L
    private const val MAX_DELAY_MS = 15 * 60 * 1000L

    fun delayMillis(
        attemptCount: Int,
        idempotencyKey: String,
    ): Long {
        val exponent = (attemptCount - 1).coerceIn(0, 10)
        val exponential = (BASE_DELAY_MS shl exponent)
            .coerceAtMost(MAX_DELAY_MS)
        val jitterWindow = (exponential / 4).coerceAtMost(30_000L)
        val stableHash = idempotencyKey.hashCode().toLong() and 0x7fffffffL
        val jitter = if (jitterWindow == 0L) 0L else stableHash % jitterWindow
        return (exponential + jitter).coerceAtMost(MAX_DELAY_MS)
    }
}

private fun String.toServerState(): String = when (this) {
    "CLAIMED" -> "ASSIGNED"
    "CANCELLED" -> "CANCELLED"
    "COMPLETED" -> "COMPLETED"
    else -> this
}

private fun String.toLegacyStatus(): String = when (this) {
    "SEARCHING", "OFFERED" -> "OPEN"
    "ASSIGNED", "DRIVER_EN_ROUTE" -> "ACCEPTED"
    else -> this
}

private fun kotlinx.serialization.json.JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

private fun Throwable.safeMessage(): String =
    (message ?: this::class.simpleName ?: "Invalid local command").take(300)
