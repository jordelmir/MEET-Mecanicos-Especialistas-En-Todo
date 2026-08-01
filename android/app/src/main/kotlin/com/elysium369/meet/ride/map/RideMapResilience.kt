package com.elysium369.meet.ride.map

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.round

/**
 * A provider endpoint is deliberately configuration-only. Public endpoints are
 * suitable for a small pilot; an operator can point the same clients at a
 * self-hosted Photon/OSRM instance without changing the route or search API.
 */
data class RideMapProviderEndpoint(
    val id: String,
    val url: String,
    val publicPilotEndpoint: Boolean,
) {
    init {
        require(id.isNotBlank()) { "Map provider id is required" }
    }

    val isConfigured: Boolean get() = url.isNotBlank()
}

enum class RideMapProviderAvailability {
    AVAILABLE,
    COOLING_DOWN,
    UNCONFIGURED,
}

data class RideMapProviderHealth(
    val id: String,
    val availability: RideMapProviderAvailability,
    val consecutiveFailures: Int = 0,
    val lastFailureMessage: String? = null,
)

/**
 * Small in-memory circuit breaker. It protects community/demo endpoints from
 * retry storms and makes a configured fallback useful after a genuine outage.
 */
internal class RideMapCircuitBreaker(
    private val failureThreshold: Int = 2,
    private val coolDownMs: Long = 30_000L,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    init {
        require(failureThreshold > 0) { "Failure threshold must be positive" }
        require(coolDownMs > 0L) { "Cooldown must be positive" }
    }

    private var consecutiveFailures = 0
    private var blockedUntilEpochMs = 0L

    fun canAttempt(): Boolean = nowEpochMs() >= blockedUntilEpochMs

    fun recordSuccess() {
        consecutiveFailures = 0
        blockedUntilEpochMs = 0L
    }

    fun recordFailure() {
        consecutiveFailures += 1
        if (consecutiveFailures >= failureThreshold) {
            blockedUntilEpochMs = nowEpochMs() + coolDownMs
        }
    }

    fun health(id: String, configured: Boolean, lastFailureMessage: String?): RideMapProviderHealth =
        RideMapProviderHealth(
            id = id,
            availability = when {
                !configured -> RideMapProviderAvailability.UNCONFIGURED
                canAttempt() -> RideMapProviderAvailability.AVAILABLE
                else -> RideMapProviderAvailability.COOLING_DOWN
            },
            consecutiveFailures = consecutiveFailures,
            lastFailureMessage = lastFailureMessage,
        )
}

private class RideTtlLruCache<K, V>(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val nowEpochMs: () -> Long,
) {
    init {
        require(maxEntries > 0) { "Cache size must be positive" }
        require(ttlMs > 0L) { "Cache TTL must be positive" }
    }

    private data class Entry<V>(val value: V, val expiresAtEpochMs: Long)

    private val values = object : LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean =
            size > maxEntries
    }

    fun get(key: K): V? {
        val entry = values[key] ?: return null
        if (entry.expiresAtEpochMs <= nowEpochMs()) {
            values.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) {
        values[key] = Entry(value, nowEpochMs() + ttlMs)
    }
}

private data class RidePlaceSearchCacheKey(
    val normalizedQuery: String,
    val latitudeBias: Double?,
    val longitudeBias: Double?,
    val limit: Int,
)

private fun buildRidePlaceSearchCacheKey(
    query: String,
    latitude: Double?,
    longitude: Double?,
    limit: Int,
): RidePlaceSearchCacheKey =
    RidePlaceSearchCacheKey(
        normalizedQuery = query.trim().lowercase(),
        latitudeBias = latitude?.let { round(it * 1_000.0) / 1_000.0 },
        longitudeBias = longitude?.let { round(it * 1_000.0) / 1_000.0 },
        limit = limit.coerceIn(1, 10),
    )

private data class RideRouteCacheKey(
    val waypoints: List<Pair<Double, Double>>,
)

private fun buildRideRouteCacheKey(waypoints: List<RideGeoPoint>): RideRouteCacheKey =
    RideRouteCacheKey(
        waypoints = waypoints.map { point ->
            round(point.latitude * 100_000.0) / 100_000.0 to
                round(point.longitude * 100_000.0) / 100_000.0
        },
    )

data class RidePlaceSearchProviderCandidate(
    val endpoint: RideMapProviderEndpoint,
    val provider: RidePlaceSearchProvider,
)

/**
 * Caches successful end-user lookups and only invokes a configured fallback
 * after a real provider failure. It intentionally does not use public
 * Nominatim as an autocomplete fallback because that violates its policy.
 */
class ResilientRidePlaceSearchProvider(
    candidates: List<RidePlaceSearchProviderCandidate>,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : RidePlaceSearchProvider {
    private val candidates = candidates.distinctBy { it.endpoint.id }
    private val cache = RideTtlLruCache<RidePlaceSearchCacheKey, List<RidePlaceSuggestion>>(
        maxEntries = 96,
        ttlMs = 10 * 60_000L,
        nowEpochMs = nowEpochMs,
    )
    private val mutex = Mutex()
    private val breakers = this.candidates.associate { candidate ->
        candidate.endpoint.id to RideMapCircuitBreaker(nowEpochMs = nowEpochMs)
    }
    private val failures = mutableMapOf<String, String?>()

    init {
        require(this.candidates.isNotEmpty()) { "At least one place search provider is required" }
    }

    fun health(): List<RideMapProviderHealth> = candidates.map { candidate ->
        breakers.getValue(candidate.endpoint.id).health(
            id = candidate.endpoint.id,
            configured = candidate.endpoint.isConfigured,
            lastFailureMessage = failures[candidate.endpoint.id],
        )
    }

    override suspend fun search(
        query: String,
        biasLatitude: Double?,
        biasLongitude: Double?,
        limit: Int,
    ): List<RidePlaceSuggestion> = mutex.withLock {
        if (query.trim().length < 3) return@withLock emptyList()
        val key = buildRidePlaceSearchCacheKey(query, biasLatitude, biasLongitude, limit)
        cache.get(key)?.let { cached ->
            return@withLock cached.map {
                it.copy(
                    source = RideMapDataSource.CACHE,
                    attribution = "${it.attribution} · caché local reciente",
                )
            }
        }

        var lastFailure: RidePlaceSearchException? = null
        candidates.forEach { candidate ->
            val breaker = breakers.getValue(candidate.endpoint.id)
            if (!candidate.endpoint.isConfigured || !breaker.canAttempt()) return@forEach
            val result = runCatching {
                candidate.provider.search(query, biasLatitude, biasLongitude, limit)
            }
            result.onSuccess { suggestions ->
                breaker.recordSuccess()
                failures.remove(candidate.endpoint.id)
                if (suggestions.isNotEmpty()) cache.put(key, suggestions)
                return@withLock suggestions
            }.onFailure { error ->
                breaker.recordFailure()
                val safeMessage = error.message?.take(160) ?: "Fallo de proveedor"
                failures[candidate.endpoint.id] = safeMessage
                lastFailure = RidePlaceSearchException(safeMessage)
            }
        }
        throw lastFailure ?: RidePlaceSearchException(
            "Búsqueda temporalmente no disponible; los proveedores están en recuperación",
        )
    }
}

data class RideRoutingProviderCandidate(
    val endpoint: RideMapProviderEndpoint,
    val provider: RideRoutingProvider,
)

/**
 * Routes are cached briefly only for identical waypoints. A cached route is
 * labelled in the UI and never turns a provider failure into a straight line.
 */
class ResilientRideRoutingProvider(
    candidates: List<RideRoutingProviderCandidate>,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : RideRoutingProvider {
    private val candidates = candidates.distinctBy { it.endpoint.id }
    private val cache = RideTtlLruCache<RideRouteCacheKey, RideRoadRoute>(
        maxEntries = 32,
        ttlMs = 3 * 60_000L,
        nowEpochMs = nowEpochMs,
    )
    private val mutex = Mutex()
    private val breakers = this.candidates.associate { candidate ->
        candidate.endpoint.id to RideMapCircuitBreaker(nowEpochMs = nowEpochMs)
    }
    private val failures = mutableMapOf<String, String?>()

    init {
        require(this.candidates.isNotEmpty()) { "At least one routing provider is required" }
    }

    fun health(): List<RideMapProviderHealth> = candidates.map { candidate ->
        breakers.getValue(candidate.endpoint.id).health(
            id = candidate.endpoint.id,
            configured = candidate.endpoint.isConfigured,
            lastFailureMessage = failures[candidate.endpoint.id],
        )
    }

    override suspend fun route(waypoints: List<RideGeoPoint>): RideRoadRoute = mutex.withLock {
        val key = buildRideRouteCacheKey(waypoints)
        cache.get(key)?.let { cached ->
            return@withLock cached.copy(
                source = RideMapDataSource.CACHE,
                attribution = "${cached.attribution} · caché local reciente",
            )
        }

        var lastFailure: RideRoutingException? = null
        candidates.forEach { candidate ->
            val breaker = breakers.getValue(candidate.endpoint.id)
            if (!candidate.endpoint.isConfigured || !breaker.canAttempt()) return@forEach
            val result = runCatching { candidate.provider.route(waypoints) }
            result.onSuccess { route ->
                breaker.recordSuccess()
                failures.remove(candidate.endpoint.id)
                cache.put(key, route)
                return@withLock route
            }.onFailure { error ->
                breaker.recordFailure()
                val safeMessage = error.message?.take(160) ?: "Fallo de proveedor"
                failures[candidate.endpoint.id] = safeMessage
                lastFailure = RideRoutingException(safeMessage)
            }
        }
        throw lastFailure ?: RideRoutingException(
            "Ruta vial temporalmente no disponible; los proveedores están en recuperación",
        )
    }
}

fun resilientRidePlaceSearchProvider(
    primaryEndpoint: String,
    fallbackEndpoint: String,
): ResilientRidePlaceSearchProvider =
    ResilientRidePlaceSearchProvider(
        candidates = listOf(
            RidePlaceSearchProviderCandidate(
                endpoint = RideMapProviderEndpoint(
                    id = "photon-primary",
                    url = primaryEndpoint,
                    publicPilotEndpoint = true,
                ),
                provider = PhotonRidePlaceSearchProvider(primaryEndpoint, providerLabel = "Photon"),
            ),
            RidePlaceSearchProviderCandidate(
                endpoint = RideMapProviderEndpoint(
                    id = "photon-fallback",
                    url = fallbackEndpoint,
                    publicPilotEndpoint = false,
                ),
                provider = PhotonRidePlaceSearchProvider(fallbackEndpoint, providerLabel = "Photon alternativo"),
            ),
        ),
    )

fun resilientRideRoutingProvider(
    primaryEndpoint: String,
    fallbackEndpoint: String,
): ResilientRideRoutingProvider =
    ResilientRideRoutingProvider(
        candidates = listOf(
            RideRoutingProviderCandidate(
                endpoint = RideMapProviderEndpoint(
                    id = "osrm-primary",
                    url = primaryEndpoint,
                    publicPilotEndpoint = true,
                ),
                provider = OsrmRideRoutingProvider(primaryEndpoint, providerLabel = "OSRM"),
            ),
            RideRoutingProviderCandidate(
                endpoint = RideMapProviderEndpoint(
                    id = "osrm-fallback",
                    url = fallbackEndpoint,
                    publicPilotEndpoint = false,
                ),
                provider = OsrmRideRoutingProvider(fallbackEndpoint, providerLabel = "OSRM alternativo"),
            ),
        ),
    )
