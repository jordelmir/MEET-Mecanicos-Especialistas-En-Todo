package com.elysium369.meet.ride.map

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class RideRoadRoute(
    val geometry: List<RideGeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val attribution: String,
    val source: RideMapDataSource = RideMapDataSource.NETWORK,
) {
    init {
        require(geometry.size >= 2) { "Road route requires at least two points" }
        require(distanceMeters >= 0.0) { "Route distance cannot be negative" }
        require(durationSeconds >= 0.0) { "Route duration cannot be negative" }
        require(attribution.isNotBlank()) { "Route attribution is required" }
    }
}

interface RideRoutingProvider {
    suspend fun route(waypoints: List<RideGeoPoint>): RideRoadRoute
}

class RideRoutingException(message: String) : Exception(message)

class OsrmRideRoutingProvider(
    private val endpoint: String,
    private val providerLabel: String = "OSRM",
) : RideRoutingProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun route(
        waypoints: List<RideGeoPoint>,
    ): RideRoadRoute = withContext(Dispatchers.IO) {
        require(waypoints.size in 2..34) {
            "Road route requires between 2 and 34 waypoints"
        }
        if (endpoint.isBlank()) {
            throw RideRoutingException("Proveedor vial no configurado")
        }
        val coordinates = waypoints.joinToString(";") {
            "${it.longitude},${it.latitude}"
        }
        val base = endpoint.trimEnd('/')
        val url = URI.create(
            "$base/route/v1/driving/$coordinates" +
                "?overview=full&geometries=geojson&steps=false",
        ).toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MEET-Rides-Android/1.0")
            if (connection.responseCode !in 200..299) {
                throw RideRoutingException(
                    "Routing HTTP ${connection.responseCode}",
                )
            }
            parseOsrmRoute(
                raw = connection.inputStream.bufferedReader().use { it.readText() },
                capturedAtEpochMs = System.currentTimeMillis(),
                json = json,
                providerLabel = providerLabel,
            )
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
private data class OsrmRouteResponse(
    val code: String = "",
    val routes: List<OsrmRouteWire> = emptyList(),
    val message: String? = null,
)

@Serializable
private data class OsrmRouteWire(
    val distance: Double = -1.0,
    val duration: Double = -1.0,
    val geometry: OsrmGeometryWire = OsrmGeometryWire(),
)

@Serializable
private data class OsrmGeometryWire(
    val type: String = "",
    val coordinates: List<List<Double>> = emptyList(),
)

internal fun parseOsrmRoute(
    raw: String,
    capturedAtEpochMs: Long,
    json: Json = Json { ignoreUnknownKeys = true },
    providerLabel: String = "OSRM",
): RideRoadRoute {
    val response = runCatching {
        json.decodeFromString<OsrmRouteResponse>(raw)
    }.getOrElse {
        throw RideRoutingException("Respuesta vial inválida")
    }
    if (response.code != "Ok") {
        throw RideRoutingException(
            response.message?.takeIf(String::isNotBlank)
                ?: "No se encontró una ruta vial",
        )
    }
    val route = response.routes.firstOrNull()
        ?: throw RideRoutingException("El proveedor no devolvió rutas")
    if (route.geometry.type != "LineString") {
        throw RideRoutingException("Geometría vial no compatible")
    }
    val points = route.geometry.coordinates.mapNotNull { coordinate ->
        val longitude = coordinate.getOrNull(0) ?: return@mapNotNull null
        val latitude = coordinate.getOrNull(1) ?: return@mapNotNull null
        runCatching {
            RideGeoPoint(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = null,
                capturedAtEpochMs = capturedAtEpochMs,
            )
        }.getOrNull()
    }
    if (points.size < 2 || route.distance < 0.0 || route.duration < 0.0) {
        throw RideRoutingException("Ruta vial incompleta")
    }
    return RideRoadRoute(
        geometry = points,
        distanceMeters = route.distance,
        durationSeconds = route.duration,
        attribution = "Ruta $providerLabel · © OpenStreetMap contributors",
    )
}
