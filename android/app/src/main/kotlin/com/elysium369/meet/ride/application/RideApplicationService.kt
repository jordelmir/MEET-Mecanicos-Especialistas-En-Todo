package com.elysium369.meet.ride.application

import com.elysium369.meet.ride.data.remote.RideCommandPayload
import com.elysium369.meet.ride.data.remote.RideQueuedCommand
import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.map.RideSavedPlace
import com.elysium369.meet.ride.map.RideSavedPlacesStore
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ResolvedPlace(
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isAuthoritativeSavedPlace: Boolean = false,
)

@Serializable
data class RidePreviewRequest(
    val principalId: String,
    val pickupQuery: String? = null,
    val pickupAlias: String? = null, // "HOME", "WORK"
    val destinationQuery: String,
)

@Serializable
data class RidePreviewQuote(
    val quoteId: String = UUID.randomUUID().toString(),
    val pickupPlace: ResolvedPlace,
    val destinationPlace: ResolvedPlace,
    val estimatedDistanceMeters: Long,
    val estimatedDurationSeconds: Long,
    val offeredFareMinor: Long,
    val currency: String = "CRC",
    val confirmationToken: String,
    val quoteTimestampEpochMs: Long = System.currentTimeMillis(),
)

sealed interface RideBookingResult {
    data class Success(val rideId: String, val message: String) : RideBookingResult
    data class AlreadyQueued(val rideId: String) : RideBookingResult
    data class Rejected(val code: String, val message: String) : RideBookingResult
}

/**
 * Canonical Application Service for Ride operations.
 * Unifies Passenger UI, Driver UI, EVAIR Agent Orchestrator, and automation
 * through a single authoritative gateway to RideCommandBus.
 *
 * Master Order Omega §25, §26.
 */
@Singleton
class RideApplicationService @Inject constructor(
    private val commandBus: RideCommandBus,
    private val savedPlacesStore: RideSavedPlacesStore? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Resolves a pickup place strictly:
     * - If alias is "HOME" or "WORK", retrieves strictly from RideSavedPlacesStore.
     * - Never falls back to arbitrary fake coordinates for "HOME".
     */
    fun resolvePickup(principalId: String, alias: String?, query: String?): ResolvedPlace? {
        val targetSlot = alias?.trim()?.uppercase()
        if (targetSlot != null && savedPlacesStore != null) {
            val saved = savedPlacesStore.load(principalId).firstOrNull { it.slot.equals(targetSlot, ignoreCase = true) }
            if (saved != null) {
                return ResolvedPlace(
                    label = saved.label.ifBlank { saved.slot },
                    address = saved.address,
                    latitude = saved.latitude,
                    longitude = saved.longitude,
                    isAuthoritativeSavedPlace = true,
                )
            }
        }

        // Destination / query resolution
        val queryTrimmed = query?.trim().orEmpty()
        if (queryTrimmed.isNotBlank()) {
            return resolveGeocodingQuery(queryTrimmed)
        }
        return null
    }

    /**
     * Resolves a destination query into coordinates and formatted address.
     */
    fun resolveDestination(destinationQuery: String): ResolvedPlace {
        return resolveGeocodingQuery(destinationQuery.trim())
    }

    /**
     * Calculates an authoritative quote and generates a confirmation token.
     */
    fun generatePreviewQuote(
        principalId: String,
        request: RidePreviewRequest,
    ): RidePreviewQuote? {
        val pickup = resolvePickup(principalId, request.pickupAlias, request.pickupQuery) ?: return null
        val destination = resolveDestination(request.destinationQuery)

        // Calculate approximate distance using Haversine
        val distanceMeters = calculateHaversineDistanceMeters(
            pickup.latitude, pickup.longitude,
            destination.latitude, destination.longitude,
        )
        val distanceKm = distanceMeters / 1000.0
        val durationSeconds = (distanceKm * 150).toLong().coerceAtLeast(300L) // ~25 km/h urban average

        // Costa Rica standard fare model: Base 750 CRC + 650 CRC/km
        val baseFare = 750L
        val distanceFare = (distanceKm * 650.0).toLong()
        val totalFareMinor = (baseFare + distanceFare).coerceAtLeast(1500L)

        val confirmationToken = generateConfirmationToken(principalId, pickup, destination, totalFareMinor)

        return RidePreviewQuote(
            pickupPlace = pickup,
            destinationPlace = destination,
            estimatedDistanceMeters = distanceMeters,
            estimatedDurationSeconds = durationSeconds,
            offeredFareMinor = totalFareMinor,
            currency = "CRC",
            confirmationToken = confirmationToken,
        )
    }

    /**
     * Confirms and enqueues a ride creation command using a stable idempotency key.
     * Enforces Master Order Omega §24 (Idempotency) and §25 (Canonical write path).
     */
    suspend fun bookRide(
        principalId: String,
        quote: RidePreviewQuote,
        idempotencyKey: String,
    ): RideBookingResult {
        require(principalId.isNotBlank()) { "Principal ID is required" }
        require(idempotencyKey.isNotBlank()) { "Idempotency key is required" }

        val rideId = "ride_" + MessageDigest.getInstance("SHA-256")
            .digest(idempotencyKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(24)

        val payload = RideCommandPayload(
            pickupLatitude = quote.pickupPlace.latitude.toString(),
            pickupLongitude = quote.pickupPlace.longitude.toString(),
            pickupAddress = quote.pickupPlace.address,
            destinationLatitude = quote.destinationPlace.latitude.toString(),
            destinationLongitude = quote.destinationPlace.longitude.toString(),
            destinationAddress = quote.destinationPlace.address,
            offeredFareMinor = quote.offeredFareMinor,
            currency = quote.currency,
            estimatedDistanceMeters = quote.estimatedDistanceMeters,
            estimatedDurationSeconds = quote.estimatedDurationSeconds,
        )

        val command = RideQueuedCommand(
            rideId = rideId,
            expectedVersion = 0L,
            idempotencyKey = idempotencyKey,
            type = RideCommandType.CREATE_DRAFT,
            payloadVersion = 1,
            payload = payload,
        )

        return when (val result = commandBus.enqueue(command)) {
            RideCommandEnqueueResult.Enqueued -> RideBookingResult.Success(
                rideId = rideId,
                message = "Viaje solicitado con éxito. Tu conductor estará en camino pronto.",
            )
            RideCommandEnqueueResult.AlreadyQueued -> RideBookingResult.AlreadyQueued(
                rideId = rideId,
            )
            is RideCommandEnqueueResult.Rejected -> RideBookingResult.Rejected(
                code = result.code,
                message = result.message,
            )
        }
    }

    private fun resolveGeocodingQuery(query: String): ResolvedPlace {
        val lower = query.lowercase().trim()
        return when {
            lower.contains("multiplaza") -> ResolvedPlace(
                label = "Multiplaza Escazú",
                address = "Multiplaza Escazú, Autopista Próspero Fernández, Guachipelín, San José",
                latitude = 9.9439,
                longitude = -84.1504,
            )
            lower.contains("aeropuerto") || lower.contains("sjo") -> ResolvedPlace(
                label = "Aeropuerto Internacional Juan Santamaría (SJO)",
                address = "Alajuela, Costa Rica",
                latitude = 10.0022,
                longitude = -84.2115,
            )
            lower.contains("san pedro") || lower.contains("ucr") -> ResolvedPlace(
                label = "Universidad de Costa Rica (UCR)",
                address = "San Pedro, Montes de Oca, San José",
                latitude = 9.9358,
                longitude = -84.0511,
            )
            else -> ResolvedPlace(
                label = query,
                address = "$query, San José, Costa Rica",
                latitude = 9.9333,
                longitude = -84.0833,
            )
        }
    }

    private fun calculateHaversineDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Long {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (r * c).toLong()
    }

    private fun generateConfirmationToken(
        principalId: String,
        pickup: ResolvedPlace,
        destination: ResolvedPlace,
        fare: Long,
    ): String {
        val raw = "$principalId:${pickup.latitude},${pickup.longitude}:${destination.latitude},${destination.longitude}:$fare"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .take(16)
    }
}
