package com.elysium369.meet.ride.data.remote

import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class RideDriverPilotEnrollment(
    val driverDisplayName: String,
    val countryCode: String,
    val currency: String,
    val vehicleReference: String,
    val vehicleDisplayName: String,
    val seats: Int,
    val evidenceManifestSha256: String,
)

sealed interface RideDriverEnrollmentResult {
    data class Accepted(
        val vehicleId: String,
        val status: String,
        val documentReviewStatus: String,
        val expiresAt: String,
    ) : RideDriverEnrollmentResult

    data class Rejected(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : RideDriverEnrollmentResult

    data class TransportFailure(val message: String) : RideDriverEnrollmentResult
}

interface RideDriverEnrollmentGateway {
    suspend fun enroll(
        enrollment: RideDriverPilotEnrollment,
        idempotencyKey: String,
    ): RideDriverEnrollmentResult
}

@Serializable
private data class EnrollmentWireError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

@Serializable
private data class EnrollmentWireResponse(
    val ok: Boolean,
    val data: JsonObject? = null,
    val error: EnrollmentWireError? = null,
    @SerialName("server_timestamp")
    val serverTimestamp: String? = null,
)

@Singleton
class SupabaseRideDriverEnrollmentGateway @Inject constructor() :
    RideDriverEnrollmentGateway {
    override suspend fun enroll(
        enrollment: RideDriverPilotEnrollment,
        idempotencyKey: String,
    ): RideDriverEnrollmentResult {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) {
            return RideDriverEnrollmentResult.Rejected(
                code = "UNAUTHENTICATED",
                message = "Autenticación requerida",
                retryable = true,
            )
        }
        return try {
            val response = client.postgrest.rpc(
                function = "ride_enroll_driver_pilot_v2",
                parameters = buildJsonObject {
                    put("p_driver_display_name", enrollment.driverDisplayName)
                    put("p_country_code", enrollment.countryCode)
                    put("p_currency", enrollment.currency)
                    put("p_vehicle_reference", enrollment.vehicleReference)
                    put("p_vehicle_display_name", enrollment.vehicleDisplayName)
                    put("p_seats", enrollment.seats)
                    put(
                        "p_evidence_manifest_sha256",
                        enrollment.evidenceManifestSha256,
                    )
                    put("p_idempotency_key", idempotencyKey)
                },
            ).decodeAs<EnrollmentWireResponse>()
            if (response.ok) {
                val data = requireNotNull(response.data)
                RideDriverEnrollmentResult.Accepted(
                    vehicleId = data.text("vehicle_id")
                        ?: error("Enrollment response omitted vehicle_id"),
                    status = data.text("status") ?: "PILOT_ATTESTED",
                    documentReviewStatus =
                        data.text("document_review_status") ?: "UNDER_REVIEW",
                    expiresAt = data.text("pilot_access_expires_at")
                        ?: error("Enrollment response omitted expiration"),
                )
            } else {
                val error = requireNotNull(response.error)
                RideDriverEnrollmentResult.Rejected(
                    code = error.code,
                    message = error.message,
                    retryable = error.retryable,
                )
            }
        } catch (error: Exception) {
            RideDriverEnrollmentResult.TransportFailure(
                (error.message ?: "No se pudo sincronizar el alta").take(300),
            )
        }
    }
}

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.content
