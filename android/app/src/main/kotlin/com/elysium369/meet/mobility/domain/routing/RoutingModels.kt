package com.elysium369.meet.mobility.domain.routing

import java.time.Instant

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be in [-90, 90], got $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be in [-180, 180], got $longitude" }
    }
}

enum class RoutingProfile {
    DRIVING,
    DRIVING_TRAFFIC,
    WALKING,
    BICYCLE,
}

data class RoutingResult(
    val distanceMeters: Long,
    val durationSeconds: Long,
    val encodedPolyline: String?,
    val provider: String,
    val calculatedAt: Instant,
) {
    init {
        require(distanceMeters >= 0L) { "distanceMeters must be >= 0" }
        require(durationSeconds >= 0L) { "durationSeconds must be >= 0" }
    }
}

class RoutingUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

interface RoutingProvider {
    suspend fun route(
        origin: GeoCoordinate,
        stops: List<GeoCoordinate> = emptyList(),
        destination: GeoCoordinate,
        profile: RoutingProfile = RoutingProfile.DRIVING,
    ): RoutingResult
}

class RoutingProviderChain(
    private val providers: List<RoutingProvider>,
) : RoutingProvider {
    init {
        require(providers.isNotEmpty()) { "RoutingProviderChain requires at least one provider" }
    }

    override suspend fun route(
        origin: GeoCoordinate,
        stops: List<GeoCoordinate>,
        destination: GeoCoordinate,
        profile: RoutingProfile,
    ): RoutingResult {
        var lastFailure: Throwable? = null
        for (provider in providers) {
            try {
                return provider.route(origin, stops, destination, profile)
            } catch (t: Throwable) {
                lastFailure = t
            }
        }
        throw RoutingUnavailableException("All routing providers in chain failed", lastFailure)
    }
}
