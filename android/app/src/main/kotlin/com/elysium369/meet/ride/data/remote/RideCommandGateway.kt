package com.elysium369.meet.ride.data.remote

import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.ride.domain.RideCommandType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class RideCommandPayload(
    val vehicleId: String? = null,
    val reasonCode: String? = null,
    val detail: String? = null,
)

data class RideQueuedCommand(
    val rideId: String,
    val expectedVersion: Long,
    val idempotencyKey: String,
    val type: RideCommandType,
    val payloadVersion: Int,
    val payload: RideCommandPayload,
)

sealed interface RideCommandGatewayResult {
    data class Accepted(
        val status: String,
        val serverVersion: Long,
        val finalPriceMinor: Long?,
        val correlationId: String?,
        val data: JsonObject,
    ) : RideCommandGatewayResult

    data class Rejected(
        val code: String,
        val message: String,
        val retryable: Boolean,
        val currentServerVersion: Long?,
        val correlationId: String?,
    ) : RideCommandGatewayResult

    data class TransportFailure(
        val code: String,
        val message: String,
    ) : RideCommandGatewayResult
}

data class RideRemoteSnapshot(
    val rideId: String,
    val state: String,
    val version: Long,
    val offeredFareMinor: Long,
    val finalFareMinor: Long?,
    val currency: String,
    val assignedDriverId: String?,
    val assignedVehicleId: String?,
)

sealed interface RideSnapshotResult {
    data class Found(
        val snapshot: RideRemoteSnapshot,
    ) : RideSnapshotResult

    data object NotFound : RideSnapshotResult

    data class Failure(
        val code: String,
        val message: String,
    ) : RideSnapshotResult
}

interface RideCommandGateway {
    suspend fun execute(command: RideQueuedCommand): RideCommandGatewayResult
    suspend fun fetchSnapshot(rideId: String): RideSnapshotResult
}

@Serializable
private data class RideRemoteSnapshotWire(
    val id: String,
    val state: String,
    val version: Long,
    @SerialName("offered_fare_minor")
    val offeredFareMinor: Long,
    @SerialName("final_fare_minor")
    val finalFareMinor: Long? = null,
    val currency: String,
    @SerialName("assigned_driver_id")
    val assignedDriverId: String? = null,
    @SerialName("assigned_vehicle_id")
    val assignedVehicleId: String? = null,
)

@Serializable
private data class RideCommandWireError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val details: JsonObject = JsonObject(emptyMap()),
)

@Serializable
private data class RideCommandWireResponse(
    val ok: Boolean,
    val data: JsonObject? = null,
    val error: RideCommandWireError? = null,
    @SerialName("server_timestamp")
    val serverTimestamp: String? = null,
    @SerialName("correlation_id")
    val correlationId: String? = null,
)

@Singleton
class SupabaseRideCommandGateway @Inject constructor() : RideCommandGateway {
    override suspend fun execute(
        command: RideQueuedCommand,
    ): RideCommandGatewayResult {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) {
            return RideCommandGatewayResult.Rejected(
                code = "UNAUTHENTICATED",
                message = "Autenticación requerida",
                retryable = false,
                currentServerVersion = null,
                correlationId = null,
            )
        }

        val invocation = command.toRpcInvocation()
            ?: return RideCommandGatewayResult.Rejected(
                code = "UNSUPPORTED_COMMAND",
                message = "Comando aún no habilitado en la autoridad remota",
                retryable = false,
                currentServerVersion = null,
                correlationId = null,
            )

        return try {
            val response = client.postgrest
                .rpc(invocation.functionName, invocation.parameters)
                .decodeAs<RideCommandWireResponse>()

            if (response.ok) {
                val data = requireNotNull(response.data) {
                    "Successful command response omitted data"
                }
                RideCommandGatewayResult.Accepted(
                    status = data.text("status") ?: "ACCEPTED",
                    serverVersion = data.long("version")
                        ?: error("Successful command response omitted version"),
                    finalPriceMinor = data.long("customer_total_minor"),
                    correlationId = response.correlationId,
                    data = data,
                )
            } else {
                val error = requireNotNull(response.error) {
                    "Rejected command response omitted error"
                }
                RideCommandGatewayResult.Rejected(
                    code = error.code,
                    message = error.message,
                    retryable = error.retryable,
                    currentServerVersion = error.details.long("current_version"),
                    correlationId = response.correlationId,
                )
            }
        } catch (error: Exception) {
            RideCommandGatewayResult.TransportFailure(
                code = "REMOTE_TRANSPORT_FAILURE",
                message = error.safeMessage(),
            )
        }
    }

    override suspend fun fetchSnapshot(rideId: String): RideSnapshotResult {
        if (rideId.isBlank()) {
            return RideSnapshotResult.Failure(
                code = "VALIDATION_ERROR",
                message = "Ride ID is required",
            )
        }
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) {
            return RideSnapshotResult.Failure(
                code = "UNAUTHENTICATED",
                message = "Autenticación requerida",
            )
        }
        return try {
            val wire = client.postgrest["ride_requests"]
                .select {
                    filter {
                        eq("id", rideId)
                    }
                    limit(1)
                }
                .decodeSingleOrNull<RideRemoteSnapshotWire>()
                ?: return RideSnapshotResult.NotFound
            RideSnapshotResult.Found(
                RideRemoteSnapshot(
                    rideId = wire.id,
                    state = wire.state,
                    version = wire.version,
                    offeredFareMinor = wire.offeredFareMinor,
                    finalFareMinor = wire.finalFareMinor,
                    currency = wire.currency,
                    assignedDriverId = wire.assignedDriverId,
                    assignedVehicleId = wire.assignedVehicleId,
                ),
            )
        } catch (error: Exception) {
            RideSnapshotResult.Failure(
                code = "REMOTE_READ_FAILURE",
                message = error.safeMessage(),
            )
        }
    }

    private fun RideQueuedCommand.toRpcInvocation(): RpcInvocation? {
        val common = buildJsonObject {
            put("p_expected_version", expectedVersion)
            put("p_idempotency_key", idempotencyKey)
        }
        return when (type) {
            RideCommandType.CLAIM -> {
                val vehicleId = payload.vehicleId?.takeIf(String::isNotBlank)
                    ?: return null
                RpcInvocation(
                    functionName = "ride_claim_request_v2",
                    parameters = JsonObject(
                        common + mapOf(
                            "p_request_id" to stringJson(rideId),
                            "p_vehicle_id" to stringJson(vehicleId),
                        ),
                    ),
                )
            }
            RideCommandType.CANCEL -> {
                val reason = payload.reasonCode?.takeIf(String::isNotBlank)
                    ?: return null
                RpcInvocation(
                    functionName = "ride_cancel_trip_v2",
                    parameters = JsonObject(
                        common + buildJsonObject {
                            put("p_trip_id", rideId)
                            put("p_reason_code", reason)
                            payload.detail?.takeIf(String::isNotBlank)?.let {
                                put("p_detail", it)
                            }
                        },
                    ),
                )
            }
            RideCommandType.COMPLETE -> RpcInvocation(
                functionName = "ride_complete_trip_v2",
                parameters = JsonObject(
                    common + mapOf("p_trip_id" to stringJson(rideId)),
                ),
            )
            else -> null
        }
    }

    private data class RpcInvocation(
        val functionName: String,
        val parameters: JsonObject,
    )
}

private fun stringJson(value: String) =
    kotlinx.serialization.json.JsonPrimitive(value)

private fun JsonObject.text(key: String): String? =
    this[key]?.let { element ->
        (element as? kotlinx.serialization.json.JsonPrimitive)?.content
    }

private fun JsonObject.long(key: String): Long? =
    this[key]?.let { element ->
        (element as? kotlinx.serialization.json.JsonPrimitive)
            ?.content
            ?.toLongOrNull()
    }

private fun Throwable.safeMessage(): String =
    (message ?: this::class.simpleName ?: "Remote transport failure")
        .replace(Regex("(?i)(apikey|authorization|bearer)\\s*[:=]?\\s*\\S+"), "$1=[redacted]")
        .take(300)
