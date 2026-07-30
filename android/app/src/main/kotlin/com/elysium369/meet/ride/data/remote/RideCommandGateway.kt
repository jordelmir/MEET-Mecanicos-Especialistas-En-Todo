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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class RideCommandPayload(
    val vehicleId: String? = null,
    val reasonCode: String? = null,
    val detail: String? = null,
    val displayName: String? = null,
    val countryCode: String? = null,
    val pickupLatitude: String? = null,
    val pickupLongitude: String? = null,
    val pickupAddress: String? = null,
    val destinationLatitude: String? = null,
    val destinationLongitude: String? = null,
    val destinationAddress: String? = null,
    val offeredFareMinor: Long? = null,
    val currency: String? = null,
    val paymentMethod: String? = null,
    val stopsJson: String? = null,
    val offerId: String? = null,
    val fareMinor: Long? = null,
    val etaSeconds: Int? = null,
    val boardingPin: String? = null,
    val safetySignalType: String? = null,
    val supportCategory: String? = null,
    val supportSummary: String? = null,
    val evidenceManifestSha256: String? = null,
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
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

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
            RideCommandType.PUBLISH -> {
                val displayName = payload.displayName.nonBlank() ?: return null
                val countryCode = payload.countryCode.nonBlank() ?: return null
                val pickupLatitude = payload.pickupLatitude.jsonNumber() ?: return null
                val pickupLongitude = payload.pickupLongitude.jsonNumber() ?: return null
                val pickupAddress = payload.pickupAddress.nonBlank() ?: return null
                val destinationLatitude =
                    payload.destinationLatitude.jsonNumber() ?: return null
                val destinationLongitude =
                    payload.destinationLongitude.jsonNumber() ?: return null
                val destinationAddress =
                    payload.destinationAddress.nonBlank() ?: return null
                val offeredFareMinor =
                    payload.offeredFareMinor?.takeIf { it > 0 } ?: return null
                val currency = payload.currency.nonBlank() ?: return null
                val paymentMethod = payload.paymentMethod.nonBlank() ?: return null
                val stops = payload.stopsJson
                    ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
                    ?: return null
                RpcInvocation(
                    functionName = "ride_create_request_v2",
                    parameters = buildJsonObject {
                        put("p_request_id", rideId)
                        put("p_display_name", displayName)
                        put("p_country_code", countryCode)
                        put("p_pickup_latitude", pickupLatitude)
                        put("p_pickup_longitude", pickupLongitude)
                        put("p_pickup_address", pickupAddress)
                        put("p_destination_latitude", destinationLatitude)
                        put("p_destination_longitude", destinationLongitude)
                        put("p_destination_address", destinationAddress)
                        put("p_offered_fare_minor", offeredFareMinor)
                        put("p_currency", currency)
                        put("p_payment_method", paymentMethod)
                        put("p_stops", stops)
                        put("p_idempotency_key", idempotencyKey)
                    },
                )
            }
            RideCommandType.SUBMIT_OFFER -> {
                val offerId = payload.offerId.nonBlank() ?: return null
                val vehicleId = payload.vehicleId.nonBlank() ?: return null
                val fareMinor = payload.fareMinor?.takeIf { it > 0 } ?: return null
                val currency = payload.currency.nonBlank() ?: return null
                RpcInvocation(
                    functionName = "ride_submit_offer_v2",
                    parameters = JsonObject(
                        common + buildJsonObject {
                            put("p_request_id", rideId)
                            put("p_offer_id", offerId)
                            put("p_vehicle_id", vehicleId)
                            put("p_fare_minor", fareMinor)
                            put("p_currency", currency)
                            payload.etaSeconds?.let { put("p_eta_seconds", it) }
                        },
                    ),
                )
            }
            RideCommandType.ACCEPT_OFFER -> {
                val offerId = payload.offerId.nonBlank() ?: return null
                RpcInvocation(
                    functionName = "ride_accept_offer_v2",
                    parameters = JsonObject(
                        common + mapOf(
                            "p_request_id" to stringJson(rideId),
                            "p_offer_id" to stringJson(offerId),
                        ),
                    ),
                )
            }
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
            RideCommandType.DRIVER_EN_ROUTE,
            RideCommandType.DRIVER_ARRIVED,
            RideCommandType.START,
            -> RpcInvocation(
                functionName = "ride_driver_transition_v2",
                parameters = JsonObject(
                    common + mapOf(
                        "p_trip_id" to stringJson(rideId),
                        "p_command_type" to stringJson(type.name),
                    ),
                ),
            )
            RideCommandType.ISSUE_BOARDING_PIN -> RpcInvocation(
                functionName = "ride_issue_boarding_pin_v2",
                parameters = JsonObject(
                    common + mapOf("p_trip_id" to stringJson(rideId)),
                ),
            )
            RideCommandType.VERIFY_BOARDING_PIN -> {
                val pin = payload.boardingPin
                    ?.takeIf { it.matches(Regex("[0-9]{4}")) }
                    ?: return null
                RpcInvocation(
                    functionName = "ride_verify_boarding_pin_v2",
                    parameters = JsonObject(
                        common + mapOf(
                            "p_trip_id" to stringJson(rideId),
                            "p_pin" to stringJson(pin),
                        ),
                    ),
                )
            }
            RideCommandType.SAFETY_SIGNAL -> {
                val signalType = payload.safetySignalType.nonBlank() ?: return null
                RpcInvocation(
                    functionName = "ride_signal_safety_v2",
                    parameters = JsonObject(
                        common + buildJsonObject {
                            put("p_trip_id", rideId)
                            put("p_signal_type", signalType)
                            payload.detail?.takeIf(String::isNotBlank)?.let {
                                put("p_detail", it)
                            }
                        },
                    ),
                )
            }
            RideCommandType.OPEN_SUPPORT_CASE -> {
                val category = payload.supportCategory.nonBlank() ?: return null
                val summary = payload.supportSummary.nonBlank() ?: return null
                RpcInvocation(
                    functionName = "ride_open_support_case_v2",
                    parameters = JsonObject(
                        common + buildJsonObject {
                            put("p_trip_id", rideId)
                            put("p_category", category)
                            put("p_issue_summary", summary)
                            payload.evidenceManifestSha256
                                ?.takeIf(String::isNotBlank)
                                ?.let { put("p_evidence_manifest_sha256", it) }
                        },
                    ),
                )
            }
            else -> null
        }
    }

    private data class RpcInvocation(
        val functionName: String,
        val parameters: JsonObject,
    )
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String?.jsonNumber(): kotlinx.serialization.json.JsonElement? {
    val normalized = nonBlank() ?: return null
    if (!normalized.matches(Regex("-?([0-9]+([.][0-9]+)?|[.][0-9]+)"))) {
        return null
    }
    return runCatching {
        Json.parseToJsonElement(normalized)
    }.getOrNull()
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
