package com.elysium369.meet.ride.map

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class RidePlaceSuggestion(
    val providerId: String,
    val primaryLabel: String,
    val secondaryLabel: String,
    val latitude: Double,
    val longitude: Double,
    val attribution: String,
) {
    val displayLabel: String =
        listOf(primaryLabel, secondaryLabel).filter(String::isNotBlank).distinct().joinToString(", ")
}

interface RidePlaceSearchProvider {
    suspend fun search(
        query: String,
        biasLatitude: Double?,
        biasLongitude: Double?,
        limit: Int = 6,
    ): List<RidePlaceSuggestion>
}

class RidePlaceSearchException(message: String) : Exception(message)

class PhotonRidePlaceSearchProvider(
    private val endpoint: String,
) : RidePlaceSearchProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(
        query: String,
        biasLatitude: Double?,
        biasLongitude: Double?,
        limit: Int,
    ): List<RidePlaceSuggestion> = withContext(Dispatchers.IO) {
        if (query.trim().length < 3 || endpoint.isBlank()) return@withContext emptyList()
        val params = buildList {
            add("q=${URLEncoder.encode(query.trim(), Charsets.UTF_8.name())}")
            add("limit=${limit.coerceIn(1, 10)}")
            if (biasLatitude != null && biasLongitude != null) {
                add("lat=$biasLatitude")
                add("lon=$biasLongitude")
                add("zoom=12")
                add("location_bias_scale=0.15")
            }
        }.joinToString("&")
        val separator = if (endpoint.contains('?')) "&" else "?"
        val connection = URI.create(endpoint + separator + params).toURL()
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("Accept", "application/json")
            // photon public instances may not support `lang=es`. Omitting the
            // query parameter lets the server use this header and then the
            // local OSM name instead of failing the whole request with 400.
            connection.setRequestProperty("Accept-Language", "es-CR,es;q=0.9,en;q=0.6")
            connection.setRequestProperty("User-Agent", "MEET-Rides-Android/1.0")
            if (connection.responseCode !in 200..299) {
                throw RidePlaceSearchException("Photon HTTP ${connection.responseCode}")
            }
            parsePhotonResponse(connection.inputStream.bufferedReader().use { it.readText() }, json)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun RidePlaceSuggestion.distanceKmFrom(
    latitude: Double?,
    longitude: Double?,
): Double? {
    if (latitude == null || longitude == null) return null
    val earthRadiusKm = 6_371.0088
    val dLat = Math.toRadians(this.latitude - latitude)
    val dLon = Math.toRadians(this.longitude - longitude)
    val originLat = Math.toRadians(latitude)
    val destinationLat = Math.toRadians(this.latitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(originLat) * cos(destinationLat) * sin(dLon / 2) * sin(dLon / 2)
    return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
}

@Serializable
private data class PhotonResponse(
    val features: List<PhotonFeature> = emptyList(),
)

@Serializable
private data class PhotonFeature(
    val properties: PhotonProperties = PhotonProperties(),
    val geometry: PhotonGeometry = PhotonGeometry(),
)

@Serializable
private data class PhotonProperties(
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("osm_type") val osmType: String? = null,
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val district: String? = null,
    val city: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
)

@Serializable
private data class PhotonGeometry(
    val coordinates: List<Double> = emptyList(),
)

internal fun parsePhotonResponse(
    raw: String,
    json: Json = Json { ignoreUnknownKeys = true },
): List<RidePlaceSuggestion> = runCatching {
    json.decodeFromString<PhotonResponse>(raw).features.mapNotNull { feature ->
        val longitude = feature.geometry.coordinates.getOrNull(0) ?: return@mapNotNull null
        val latitude = feature.geometry.coordinates.getOrNull(1) ?: return@mapNotNull null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
        val properties = feature.properties
        val primary = properties.name
            ?: listOfNotNull(properties.street, properties.housenumber)
                .joinToString(" ")
                .takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val secondary = listOfNotNull(
            properties.district,
            properties.city,
            properties.county,
            properties.state,
            properties.country,
        ).filter(String::isNotBlank).distinct().joinToString(", ")
        RidePlaceSuggestion(
            providerId = listOfNotNull(properties.osmType, properties.osmId?.toString())
                .joinToString(":")
                .ifBlank { "$latitude,$longitude" },
            primaryLabel = primary,
            secondaryLabel = secondary,
            latitude = latitude,
            longitude = longitude,
            attribution = "© OpenStreetMap contributors · Photon",
        )
    }
}.getOrDefault(emptyList())
