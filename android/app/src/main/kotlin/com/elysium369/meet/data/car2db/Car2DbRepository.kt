package com.elysium369.meet.data.car2db

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Repositorio de Car2DB con caché LRU en memoria + persistencia opcional.
 *
 * Estrategia:
 * - Caché en memoria por (query+page) o (trimId) con TTL de 30 días.
 * - No persistimos a disco por defecto — los datos OEM caducan rápido y ocupan espacio.
 * - Mutex para evitar thundering herd en cold start.
 *
 * Honra el flag isEnabled del cliente: si no hay API key, todos los métodos devuelven Disabled.
 */
class Car2DbRepository(
    private val client: Car2DbClient = Car2DbClient(),
    private val ttlMs: Long = TimeUnit.DAYS.toMillis(30),
    private val maxEntries: Int = 256
) {

    private data class CacheEntry<T>(val value: T, val insertedAtMs: Long)

    private val searchCache = LinkedHashMap<String, CacheEntry<Car2DbSearchResponse>>(16, 0.75f, true)
    private val trimCache = LinkedHashMap<Int, CacheEntry<Car2DbVehicleLookup>>(16, 0.75f, true)
    private val mutex = Mutex()

    val isEnabled: Boolean get() = client.isEnabled

    suspend fun search(
        query: String,
        typeId: Int? = null,
        yearFrom: Int? = null,
        yearTo: Int? = null,
        page: Int = 1
    ): Car2DbClient.Car2DbResult<Car2DbSearchResponse> {
        if (!isEnabled) return Car2DbClient.Car2DbResult.Disabled
        val cacheKey = buildString {
            append("s:").append(query.lowercase())
            typeId?.let { append(":t").append(it) }
            yearFrom?.let { append(":yf").append(it) }
            yearTo?.let { append(":yt").append(it) }
            append(":p").append(page)
        }
        mutex.withLock {
            pruneIfNeeded()
            searchCache[cacheKey]?.let { entry ->
                if (!isExpired(entry)) return@withLock
            }
        }
        val fresh = client.searchVehicles(query, typeId, yearFrom, yearTo, page)
        if (fresh is Car2DbClient.Car2DbResult.Success) {
            mutex.withLock {
                searchCache[cacheKey] = CacheEntry(fresh.value, System.currentTimeMillis())
            }
        }
        return fresh
    }

    suspend fun getTrimFull(trimId: Int): Car2DbClient.Car2DbResult<Car2DbVehicleLookup> {
        if (!isEnabled) return Car2DbClient.Car2DbResult.Disabled
        mutex.withLock {
            trimCache[trimId]?.let { entry ->
                if (!isExpired(entry)) return@withLock
            }
        }
        val fresh = client.getTrimFull(trimId)
        if (fresh is Car2DbClient.Car2DbResult.Success) {
            mutex.withLock {
                trimCache[trimId] = CacheEntry(fresh.value, System.currentTimeMillis())
            }
        }
        return fresh
    }

    suspend fun lookupByDtc(dtcCode: String): Car2DbClient.Car2DbResult<Car2DbVehicleLookup?> {
        if (!isEnabled) return Car2DbClient.Car2DbResult.Disabled
        val query = dtcToSearchQuery(dtcCode)
        if (query.isBlank()) return Car2DbClient.Car2DbResult.Malformed("Invalid DTC code: $dtcCode")
        val searchResult = search(query)
        return when (searchResult) {
            is Car2DbClient.Car2DbResult.Success -> {
                // Tomamos el primer trim del primer modelo — heurística simple.
                val firstTrim = searchResult.value.results.firstOrNull()
                    ?.matchingTrims?.firstOrNull()
                if (firstTrim != null) {
                    getTrimFull(firstTrim.id)
                } else {
                    Car2DbClient.Car2DbResult.Success(null)
                }
            }
            else -> @Suppress("UNCHECKED_CAST") (searchResult as Car2DbClient.Car2DbResult<Car2DbVehicleLookup?>)
        }
    }

    fun clearCache() {
        searchCache.clear()
        trimCache.clear()
    }

    fun cacheStats(): CacheStats {
        return CacheStats(
            searchEntries = searchCache.size,
            trimEntries = trimCache.size
        )
    }

    data class CacheStats(val searchEntries: Int, val trimEntries: Int)

    private fun isExpired(entry: CacheEntry<*>): Boolean {
        return System.currentTimeMillis() - entry.insertedAtMs > ttlMs
    }

    private fun pruneIfNeeded() {
        while (searchCache.size + trimCache.size > maxEntries) {
            // LinkedHashMap con accessOrder=true → remove eldest.
            if (searchCache.isNotEmpty()) {
                val it = searchCache.entries.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            } else if (trimCache.isNotEmpty()) {
                val it = trimCache.entries.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            } else break
        }
    }

    fun close() {
        client.close()
    }
}