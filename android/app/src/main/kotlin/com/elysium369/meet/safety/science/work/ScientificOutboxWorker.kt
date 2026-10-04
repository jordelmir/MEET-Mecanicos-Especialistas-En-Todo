package com.elysium369.meet.safety.science.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.elysium369.meet.safety.science.data.CommandStatus
import com.elysium369.meet.safety.science.data.ScientificAuthorityDao
import com.elysium369.meet.safety.science.data.SafetyScientificGateway
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════
// BLOQUE 1 — OUTBOX DELIVERY WORKER
//
// Pattern:
//   Room outbox (PENDING) → WorkManager → Supabase RPC → ACK → Room
//
// Delivery: AT_LEAST_ONCE
// Every server command MUST be idempotent (commandId = idempotency key).
// Never assume exactly-once network delivery.
// ═══════════════════════════════════════════════════════════════════

@HiltWorker
class ScientificOutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authorityDao: ScientificAuthorityDao,
    private val gateway: SafetyScientificGateway,
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "ScientificOutbox"
        private const val MAX_RETRIES = 5
        private const val BASE_BACKOFF_MS = 5_000L

        /**
         * Enqueue periodic outbox drain.
         * Runs every 15 minutes when network is available.
         */
        fun enqueuePeriodicDrain(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ScientificOutboxWorker>(
                15, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        /**
         * Enqueue one-shot drain for immediate delivery.
         */
        fun enqueueImmediateDrain(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<ScientificOutboxWorker>()
                .setConstraints(constraints)
                .addTag("$TAG-immediate")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "$TAG-immediate",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting outbox drain")

        val now = System.currentTimeMillis()
        var processed = 0
        var failed = 0

        try {
            // 1. Process PENDING commands
            val pending = authorityDao.getPendingCommands(limit = 50)
            for (command in pending) {
                processCommand(command.id, command.commandType, command.payload, command.commandId)
                    .onSuccess { serverVersion ->
                        authorityDao.acknowledgeCommand(
                            id = command.id,
                            serverVersion = serverVersion,
                            completedAt = System.currentTimeMillis(),
                        )
                        processed++
                    }
                    .onFailure { error ->
                        handleFailure(command.id, command.attemptCount, error)
                        failed++
                    }
            }

            // 2. Process RETRYABLE commands
            val retryable = authorityDao.getRetryableCommands(now = now, limit = 20)
            for (command in retryable) {
                processCommand(command.id, command.commandType, command.payload, command.commandId)
                    .onSuccess { serverVersion ->
                        authorityDao.acknowledgeCommand(
                            id = command.id,
                            serverVersion = serverVersion,
                            completedAt = System.currentTimeMillis(),
                        )
                        processed++
                    }
                    .onFailure { error ->
                        handleFailure(command.id, command.attemptCount, error)
                        failed++
                    }
            }

            Log.d(TAG, "Outbox drain complete: processed=$processed failed=$failed")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Outbox drain error", e)
            return Result.retry()
        }
    }

    private suspend fun processCommand(
        id: String,
        commandType: String,
        payload: String,
        commandId: String,
    ): kotlin.Result<Long?> {
        // Mark as IN_FLIGHT
        authorityDao.updateCommandStatus(
            id = id,
            status = CommandStatus.IN_FLIGHT.name,
            error = null,
            nextAttempt = 0,
        )

        return try {
            val result = dispatchToGateway(commandType, payload)
            result.map { it?.serverVersion }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    private suspend fun dispatchToGateway(
        commandType: String,
        payload: String,
    ): kotlin.Result<com.elysium369.meet.safety.science.data.ServerAckResponse?> {
        // Route to appropriate gateway method based on command type.
        // Each gateway method handles its own deserialization.
        // The gateway returns typed responses; we unify to ServerAckResponse.
        return when (commandType) {
            "CREATE_ENTITY" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateEntityCommand>(payload)
                gateway.createEntity(cmd).map {
                    com.elysium369.meet.safety.science.data.ServerAckResponse(
                        status = it.status,
                        serverId = it.entityId,
                        serverVersion = it.serverVersion,
                    )
                }
            }
            "CREATE_CLAIM" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateClaimCommand>(payload)
                gateway.createClaim(cmd).map {
                    com.elysium369.meet.safety.science.data.ServerAckResponse(
                        status = it.status,
                        serverId = it.claimId,
                        serverVersion = it.serverVersion,
                    )
                }
            }
            "TRANSITION_CLAIM" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.TransitionClaimCommand>(payload)
                gateway.transitionClaim(cmd).map {
                    com.elysium369.meet.safety.science.data.ServerAckResponse(
                        status = it.status,
                        serverId = it.transitionId,
                        serverVersion = it.serverVersion,
                    )
                }
            }
            "ATTACH_EVIDENCE" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.AttachEvidenceCommand>(payload)
                gateway.attachEvidence(cmd)
            }
            "CREATE_KNOWLEDGE_EVENT" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateKnowledgeEventCommand>(payload)
                gateway.recordKnowledgeEvent(cmd)
            }
            "CREATE_AUTHORITY_ASSERTION" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateAuthorityCommand>(payload)
                gateway.createAuthorityAssertion(cmd)
            }
            "CREATE_DUTY_ASSERTION" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateDutyCommand>(payload)
                gateway.createDutyAssertion(cmd)
            }
            "CREATE_ACCOUNTABILITY_ACTION" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateAccountabilityCommand>(payload)
                gateway.createAccountabilityAction(cmd)
            }
            "CREATE_HYPOTHESIS" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateHypothesisCommand>(payload)
                gateway.createHypothesis(cmd).map {
                    com.elysium369.meet.safety.science.data.ServerAckResponse(
                        status = it.status,
                        serverId = it.hypothesisId,
                        serverVersion = it.serverVersion,
                    )
                }
            }
            "CREATE_DATASET" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateDatasetCommand>(payload)
                gateway.createDataset(cmd)
            }
            "CREATE_RESEARCH_RUN" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateResearchRunCommand>(payload)
                gateway.submitResearchRun(cmd)
            }
            "CREATE_REPLICATION" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateReplicationCommand>(payload)
                gateway.submitReplication(cmd)
            }
            "CREATE_PEER_REVIEW" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreatePeerReviewCommand>(payload)
                gateway.submitPeerReview(cmd)
            }
            "REQUEST_PUBLICATION" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.RequestPublicationCommand>(payload)
                gateway.requestPublication(cmd).map {
                    com.elysium369.meet.safety.science.data.ServerAckResponse(
                        status = it.status,
                        serverId = it.publicationId,
                        serverVersion = it.serverVersion,
                    )
                }
            }
            "CREATE_CHECKPOINT" -> {
                val cmd = Json.decodeFromString<com.elysium369.meet.safety.science.data.CreateCheckpointCommand>(payload)
                gateway.createCheckpoint(cmd).map {
                    com.elysium369.meet.safety.science.data.ServerAckResponse(
                        status = it.status,
                        serverId = it.checkpointId,
                        serverVersion = it.serverVersion,
                    )
                }
            }
            else -> {
                kotlin.Result.failure(
                    IllegalArgumentException("UNKNOWN_COMMAND_TYPE: $commandType"),
                )
            }
        }
    }

    private suspend fun handleFailure(id: String, attemptCount: Int, error: Throwable) {
        val newAttempt = attemptCount + 1
        val isPermanent = newAttempt >= MAX_RETRIES ||
            error.message?.contains("PERMANENT") == true ||
            error.message?.contains("UNAUTHORIZED") == true ||
            error.message?.contains("AI_ELEVATION_BLOCKED") == true

        val status = if (isPermanent) {
            CommandStatus.PERMANENT_FAILURE.name
        } else {
            CommandStatus.RETRYABLE_FAILURE.name
        }

        val backoff = BASE_BACKOFF_MS * (1L shl minOf(newAttempt, 10))
        val nextAttempt = System.currentTimeMillis() + backoff

        authorityDao.updateCommandStatus(
            id = id,
            status = status,
            error = error.message?.take(500),
            nextAttempt = if (isPermanent) 0 else nextAttempt,
        )

        Log.w(TAG, "Command $id failed (attempt $newAttempt): ${error.message}")
    }
}
