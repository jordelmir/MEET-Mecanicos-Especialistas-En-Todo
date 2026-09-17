package com.elysium369.meet.ride.application

import com.elysium369.meet.ride.data.RideCommandEnqueueResult as RepoEnqueueResult
import com.elysium369.meet.ride.data.RideCommandRepository
import com.elysium369.meet.ride.data.remote.RideQueuedCommand
import com.elysium369.meet.ride.domain.RideCommandEnvelope
import com.elysium369.meet.ride.domain.RideId
import com.elysium369.meet.ride.domain.RideIdempotencyKey
import com.elysium369.meet.ride.domain.RidePayloadVersion
import com.elysium369.meet.ride.domain.RideVersion
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RideCommandEnqueueResult {
    data object Enqueued : RideCommandEnqueueResult
    data object AlreadyQueued : RideCommandEnqueueResult
    data class Rejected(
        val code: String,
        val message: String,
    ) : RideCommandEnqueueResult
}

interface RideCommandBus {
    suspend fun enqueue(
        command: RideQueuedCommand,
    ): RideCommandEnqueueResult
}

@Singleton
class DefaultRideCommandBus @Inject constructor(
    private val repository: RideCommandRepository,
) : RideCommandBus {
    override suspend fun enqueue(command: RideQueuedCommand): RideCommandEnqueueResult {
        val envelope = try {
            RideCommandEnvelope(
                rideId = RideId.of(command.rideId),
                expectedVersion = RideVersion.of(command.expectedVersion),
                idempotencyKey = RideIdempotencyKey.of(command.idempotencyKey),
                type = command.type,
                payloadVersion = RidePayloadVersion.of(command.payloadVersion),
            )
        } catch (e: Exception) {
            return RideCommandEnqueueResult.Rejected(
                code = "INVALID_COMMAND_SHAPE",
                message = e.message ?: "Forma de comando inválida",
            )
        }

        return when (val result = repository.enqueue(envelope, command.payload)) {
            RepoEnqueueResult.Enqueued -> RideCommandEnqueueResult.Enqueued
            RepoEnqueueResult.AlreadyQueued -> RideCommandEnqueueResult.AlreadyQueued
            RepoEnqueueResult.AuthenticationRequired -> RideCommandEnqueueResult.Rejected(
                code = "UNAUTHENTICATED",
                message = "Se requiere una sesión activa",
            )
            is RepoEnqueueResult.IdempotencyConflict -> RideCommandEnqueueResult.Rejected(
                code = "IDEMPOTENCY_CONFLICT",
                message = result.message,
            )
            is RepoEnqueueResult.InvalidCommand -> RideCommandEnqueueResult.Rejected(
                code = "INVALID_COMMAND",
                message = result.message,
            )
        }
    }
}
