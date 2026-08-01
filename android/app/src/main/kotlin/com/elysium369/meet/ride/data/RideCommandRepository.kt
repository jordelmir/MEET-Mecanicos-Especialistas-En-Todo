package com.elysium369.meet.ride.data

import android.content.Context
import com.elysium369.meet.data.local.dao.RideDao
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.ride.data.local.RideCommandOutboxDao
import com.elysium369.meet.ride.data.local.RideCommandOutboxEntity
import com.elysium369.meet.ride.data.local.RideOutboxStatus
import com.elysium369.meet.ride.data.remote.RideCommandPayload
import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.domain.RideCommandEnvelope
import com.elysium369.meet.ride.work.RideCommandSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface RideCommandEnqueueResult {
    data object Enqueued : RideCommandEnqueueResult
    data object AlreadyQueued : RideCommandEnqueueResult
    data object AuthenticationRequired : RideCommandEnqueueResult
    data class IdempotencyConflict(
        val message: String,
    ) : RideCommandEnqueueResult
    data class InvalidCommand(
        val message: String,
    ) : RideCommandEnqueueResult
}

@Singleton
class RideCommandRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outboxDao: RideCommandOutboxDao,
    private val rideDao: RideDao,
) {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun pendingCount(): Flow<Int> = outboxDao.pendingCount()

    suspend fun enqueue(
        envelope: RideCommandEnvelope,
        payload: RideCommandPayload = RideCommandPayload(),
        now: Long = System.currentTimeMillis(),
    ): RideCommandEnqueueResult {
        if (
            envelope.expectedVersion.value <= 0 &&
            envelope.type != RideCommandType.PUBLISH
        ) {
            return RideCommandEnqueueResult.InvalidCommand(
                "La versión remota debe ser positiva",
            )
        }
        if (
            envelope.type == RideCommandType.PUBLISH &&
            envelope.expectedVersion.value != 0L
        ) {
            return RideCommandEnqueueResult.InvalidCommand(
                "Una solicitud nueva debe comenzar en versión cero",
            )
        }
        if (envelope.type !in SUPPORTED_COMMANDS) {
            return RideCommandEnqueueResult.InvalidCommand(
                "El comando ${envelope.type.name} aún no tiene RPC autoritativa",
            )
        }
        if (
            envelope.type == RideCommandType.CLAIM &&
            payload.vehicleId.isNullOrBlank()
        ) {
            return RideCommandEnqueueResult.InvalidCommand(
                "CLAIM requiere un vehículo verificado",
            )
        }
        if (
            envelope.type == RideCommandType.CANCEL &&
            payload.reasonCode.isNullOrBlank()
        ) {
            return RideCommandEnqueueResult.InvalidCommand(
                "CANCEL requiere una razón tipada",
            )
        }
        if (
            envelope.type == RideCommandType.VERIFY_BOARDING_PIN &&
            payload.boardingPin?.matches(Regex("[0-9]{4}")) != true
        ) {
            return RideCommandEnqueueResult.InvalidCommand(
                "VERIFY_BOARDING_PIN requiere cuatro dígitos",
            )
        }
        if (
            envelope.type == RideCommandType.DRIVER_ARRIVED &&
            listOf(
                payload.driverLatitude,
                payload.driverLongitude,
                payload.driverAccuracyMeters,
                payload.driverCapturedAt,
            ).any { it.isNullOrBlank() }
        ) {
            return RideCommandEnqueueResult.InvalidCommand(
                "DRIVER_ARRIVED requiere una prueba GPS reciente",
            )
        }
        if (
            envelope.type == RideCommandType.SAFETY_SIGNAL &&
            payload.safetySignalType.isNullOrBlank()
        ) {
            return RideCommandEnqueueResult.InvalidCommand(
                "SAFETY_SIGNAL requiere una señal tipada",
            )
        }
        if (
            envelope.type == RideCommandType.OPEN_SUPPORT_CASE &&
            (
                payload.supportCategory.isNullOrBlank() ||
                    payload.supportSummary?.trim()?.length !in 10..1_000
            )
        ) {
            return RideCommandEnqueueResult.InvalidCommand(
                "OPEN_SUPPORT_CASE requiere categoría y descripción válida",
            )
        }
        val sessionUserId = SupabaseModule.client.auth
            .currentUserOrNull()
            ?.id
            ?: return RideCommandEnqueueResult.AuthenticationRequired

        val candidate = RideCommandOutboxEntity(
            idempotencyKey = envelope.idempotencyKey.value,
            rideId = envelope.rideId.value,
            actorSessionUserId = sessionUserId,
            commandType = envelope.type.name,
            expectedVersion = envelope.expectedVersion.value,
            payloadVersion = envelope.payloadVersion.value,
            payloadJson = json.encodeToString(payload),
            status = RideOutboxStatus.PENDING,
            attemptCount = 0,
            nextAttemptAt = now,
            leaseStartedAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            correlationId = null,
            createdAt = now,
            updatedAt = now,
        )
        val inserted = outboxDao.insert(candidate)
        if (inserted == -1L) {
            val existing = outboxDao.byIdempotencyKey(
                candidate.idempotencyKey,
            )
            return if (existing?.sameCommandAs(candidate) == true) {
                RideCommandSyncWorker.enqueueNow(context)
                RideCommandEnqueueResult.AlreadyQueued
            } else {
                RideCommandEnqueueResult.IdempotencyConflict(
                    "La clave local ya identifica otro comando",
                )
            }
        }

        rideDao.markCommandPending(envelope.rideId.value)
        RideCommandSyncWorker.enqueueNow(context)
        return RideCommandEnqueueResult.Enqueued
    }

    private fun RideCommandOutboxEntity.sameCommandAs(
        other: RideCommandOutboxEntity,
    ): Boolean =
        rideId == other.rideId &&
            actorSessionUserId == other.actorSessionUserId &&
            commandType == other.commandType &&
            expectedVersion == other.expectedVersion &&
            payloadVersion == other.payloadVersion &&
            payloadJson == other.payloadJson

    private companion object {
        val SUPPORTED_COMMANDS = setOf(
            RideCommandType.PUBLISH,
            RideCommandType.SUBMIT_OFFER,
            RideCommandType.ACCEPT_OFFER,
            RideCommandType.CLAIM,
            RideCommandType.DRIVER_EN_ROUTE,
            RideCommandType.DRIVER_ARRIVED,
            RideCommandType.ISSUE_BOARDING_PIN,
            RideCommandType.VERIFY_BOARDING_PIN,
            RideCommandType.START,
            RideCommandType.CANCEL,
            RideCommandType.COMPLETE,
            RideCommandType.SAFETY_SIGNAL,
            RideCommandType.OPEN_SUPPORT_CASE,
        )
    }
}
