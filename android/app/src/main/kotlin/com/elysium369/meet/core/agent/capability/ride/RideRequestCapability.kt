package com.elysium369.meet.core.agent.capability.ride

import com.elysium369.meet.core.agent.capability.AgentCapability
import com.elysium369.meet.core.agent.capability.AgentExecutionContext
import com.elysium369.meet.core.agent.capability.AgentResult
import com.elysium369.meet.core.agent.capability.AgentRisk
import com.elysium369.meet.core.agent.capability.CapabilityId
import com.elysium369.meet.core.agent.capability.CapabilityValidation
import com.elysium369.meet.ride.application.RideApplicationService
import com.elysium369.meet.ride.application.RideBookingResult
import com.elysium369.meet.ride.application.RidePreviewQuote
import com.elysium369.meet.ride.application.RidePreviewRequest
import kotlinx.serialization.Serializable

@Serializable
data class RideCapabilityInput(
    val destinationQuery: String,
    val pickupQuery: String? = null,
    val pickupAlias: String? = null,
)

@Serializable
data class RideCapabilityOutput(
    val rideId: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val estimatedFareFormatted: String,
    val message: String,
)

/**
 * Ride Request Capability for EVAIR and Elysium Agent OS.
 * Enforces Master Order Omega §21 (COMMITTING Risk), §22 (Confirmation),
 * §24 (Idempotency), and §25/§26 (Ride Application Service).
 */
class RideRequestCapability(
    private val rideService: RideApplicationService,
) : AgentCapability<RideCapabilityInput, RideCapabilityOutput> {

    override val id: CapabilityId = CapabilityId.of("ride.request")
    override val risk: AgentRisk = AgentRisk.COMMITTING
    override val requiresConfirmation: Boolean = true
    override val requiresIdempotency: Boolean = true

    override suspend fun validate(input: RideCapabilityInput, context: AgentExecutionContext): CapabilityValidation {
        if (input.destinationQuery.isBlank()) {
            return CapabilityValidation.Invalid(
                reason = "El destino del viaje no puede estar vacío",
                missingFields = listOf("destinationQuery"),
            )
        }
        if (context.principalId.isBlank()) {
            return CapabilityValidation.Invalid(
                reason = "Se requiere un usuario autenticado para solicitar un viaje",
                missingFields = listOf("principalId"),
            )
        }
        return CapabilityValidation.Valid
    }

    override suspend fun execute(input: RideCapabilityInput, context: AgentExecutionContext): AgentResult<RideCapabilityOutput> {
        val previewRequest = RidePreviewRequest(
            principalId = context.principalId,
            pickupQuery = input.pickupQuery,
            pickupAlias = input.pickupAlias,
            destinationQuery = input.destinationQuery,
        )

        val quote: RidePreviewQuote = rideService.generatePreviewQuote(context.principalId, previewRequest)
            ?: return AgentResult.Failure(
                code = "PLACE_RESOLUTION_FAILED",
                message = "No fue posible determinar el punto de partida '${input.pickupAlias ?: input.pickupQuery}'. Por favor verifica tus lugares guardados o escribe la dirección.",
            )

        // 1. If user has not yet confirmed the quote, demand explicit confirmation (§22, §26)
        if (!context.userConfirmed) {
            val fareFormatted = "₡${quote.offeredFareMinor} ${quote.currency}"
            val distanceKm = String.format("%.1f", quote.estimatedDistanceMeters / 1000.0)
            val durationMin = quote.estimatedDurationSeconds / 60

            val prompt = "Viaje desde ${quote.pickupPlace.label} hacia ${quote.destinationPlace.label} por $fareFormatted ($distanceKm km, ~$durationMin min). ¿Confirmas solicitar el viaje?"

            val previewPayload = mapOf(
                "pickup" to quote.pickupPlace.address,
                "destination" to quote.destinationPlace.address,
                "fare" to fareFormatted,
                "distanceKm" to distanceKm,
                "durationMin" to durationMin.toString(),
                "confirmationToken" to quote.confirmationToken,
            )

            return AgentResult.ConfirmationRequired(
                prompt = prompt,
                payloadPreview = previewPayload,
                risk = risk,
                confirmationToken = quote.confirmationToken,
            )
        }

        // 2. User confirmed! Enforce idempotency key presence (§24)
        val idempotencyKey = context.idempotencyKey
            ?: return AgentResult.Failure(
                code = "MISSING_IDEMPOTENCY_KEY",
                message = "Se requiere una clave de idempotencia para confirmar el viaje.",
            )

        return when (val booking = rideService.bookRide(context.principalId, quote, idempotencyKey)) {
            is RideBookingResult.Success -> {
                AgentResult.Success(
                    data = RideCapabilityOutput(
                        rideId = booking.rideId,
                        pickupAddress = quote.pickupPlace.address,
                        destinationAddress = quote.destinationPlace.address,
                        estimatedFareFormatted = "₡${quote.offeredFareMinor} ${quote.currency}",
                        message = booking.message,
                    ),
                    summary = "Viaje solicitado exitosamente (ID: ${booking.rideId}).",
                )
            }
            is RideBookingResult.AlreadyQueued -> {
                AgentResult.Success(
                    data = RideCapabilityOutput(
                        rideId = booking.rideId,
                        pickupAddress = quote.pickupPlace.address,
                        destinationAddress = quote.destinationPlace.address,
                        estimatedFareFormatted = "₡${quote.offeredFareMinor} ${quote.currency}",
                        message = "El viaje ya había sido encolado previamente (operación idempotente).",
                    ),
                    summary = "Viaje existente reconfirmado de manera idempotente.",
                )
            }
            is RideBookingResult.Rejected -> {
                AgentResult.Failure(
                    code = booking.code,
                    message = booking.message,
                )
            }
        }
    }
}
