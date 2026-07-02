package com.elysium369.meet.data.car2db

import com.elysium369.meet.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.math.pow

/**
 * Cliente HTTP para Car2DB API v3.
 *
 * Características:
 * - Auth Bearer + Referer + Accept: application/ld+json requeridos.
 * - Retry exponencial con jitter para 5xx y timeout (sin retry en 4xx — el caller decide).
 * - 401 → [Car2DbResult.AuthMissing] sin tirar excepción (la app debe degradar a genéricos).
 * - Rate limit (429) → respeta Retry-After si está presente.
 * - Desactivado si no hay API key → todos los métodos devuelven [Car2DbResult.Disabled].
 *
 * Configuración:
 * - CAR2DB_API_KEY en local.properties → BuildConfig.CAR2DB_API_KEY.
 * - CAR2DB_ENABLED = true sólo si hay key configurada.
 */
open class Car2DbClient(
    private val config: Car2DbConfig = Car2DbConfig.default(),
    baseUrl: String = DEFAULT_BASE_URL
) {

    data class Car2DbConfig(
        val apiKey: String,
        val referer: String,
        val language: String,
        val timeoutMs: Long = 30_000L,
        val maxRetries: Int = 2
    ) {
        companion object {
            fun default(): Car2DbConfig = Car2DbConfig(
                apiKey = BuildConfig.CAR2DB_API_KEY,
                referer = BuildConfig.CAR2DB_REFERER,
                language = BuildConfig.CAR2DB_LANGUAGE
            )
        }
    }

    val isEnabled: Boolean
        get() = config.apiKey.isNotBlank()

    private val baseUrl: String = baseUrl.trimEnd('/')

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = false
    }

    private val httpClient: HttpClient by lazy {
        HttpClient(Android) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(this@Car2DbClient.json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = config.timeoutMs
                connectTimeoutMillis = min(config.timeoutMs, 15_000L)
                socketTimeoutMillis = config.timeoutMs
            }
            install(UserAgent) {
                agent = "Elysium-Vanguard/4.0.0 (Android; Forge)"
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = config.maxRetries)
                retryOnException(maxRetries = config.maxRetries, retryOnTimeout = true)
                exponentialDelay(base = 2.0, maxDelayMs = 8_000L)
            }
            defaultRequest {
                url(baseUrl)
            }
        }
    }

    /**
     * Resultado sellado que la UI consume. Nunca lanza excepciones — siempre degrada.
     */
    sealed class Car2DbResult<out T> {
        data class Success<T>(val value: T) : Car2DbResult<T>()
        data object Disabled : Car2DbResult<Nothing>()
        data class AuthMissing(val message: String) : Car2DbResult<Nothing>()
        data class RateLimited(val retryAfterSec: Long) : Car2DbResult<Nothing>()
        data class ApiError(val status: Int, val title: String, val detail: String) : Car2DbResult<Nothing>()
        data class NetworkError(val cause: Throwable) : Car2DbResult<Nothing>()
        data class Malformed(val reason: String, val rawSample: String? = null) : Car2DbResult<Nothing>()
    }

    // ----- Endpoints principales -----

    suspend fun searchVehicles(
        query: String,
        typeId: Int? = null,
        yearFrom: Int? = null,
        yearTo: Int? = null,
        page: Int = 1,
        itemsPerPage: Int = 25
    ): Car2DbResult<Car2DbSearchResponse> {
        if (!isEnabled) return Car2DbResult.Disabled
        val sanitized = sanitizeQuery(query)
        if (sanitized.isBlank()) {
            return Car2DbResult.Malformed("Empty query after sanitization")
        }
        val result = requestInternal<Car2DbCollection<Car2DbSearchTrim>>(
            path = "/search/vehicles",
            params = mapOf(
                "q" to sanitized,
                "page" to page.toString(),
                "itemsPerPage" to itemsPerPage.toString(),
                *(typeId?.let { arrayOf("typeId" to it.toString()) } ?: emptyArray()),
                *(yearFrom?.let { arrayOf("yearFrom" to it.toString()) } ?: emptyArray()),
                *(yearTo?.let { arrayOf("yearTo" to it.toString()) } ?: emptyArray())
            ).toMap()
        )
        return when (result) {
            is Car2DbResult.Success -> {
                val collection = result.value
                val groups = collection.member.groupBy { trim ->
                    Pair(
                        trim.breadcrumbs?.make?.name ?: "Unknown",
                        trim.breadcrumbs?.model?.name ?: "Unknown"
                    )
                }.map { (key, trims) ->
                    Car2DbSearchModelGroup(
                        model = Car2DbModel(name = key.second),
                        make = trims.firstOrNull()?.breadcrumbs?.make,
                        matchingTrimsCount = trims.size,
                        matchingTrims = trims.map { it.toSearchTrim() }
                    )
                }
                Car2DbResult.Success(
                    Car2DbSearchResponse(
                        query = sanitized,
                        results = groups,
                        totalTrims = groups.sumOf { it.matchingTrimsCount }
                    )
                )
            }
            is Car2DbResult.Disabled -> result
            is Car2DbResult.AuthMissing -> result
            is Car2DbResult.RateLimited -> result
            is Car2DbResult.ApiError -> result
            is Car2DbResult.NetworkError -> result
            is Car2DbResult.Malformed -> result
        }
    }

    suspend fun getTrimFull(trimId: Int): Car2DbResult<Car2DbVehicleLookup> {
        if (!isEnabled) return Car2DbResult.Disabled
        if (trimId <= 0) return Car2DbResult.Malformed("Invalid trimId: $trimId")
        return when (val result = requestInternal<Car2DbTrim>(path = "/trims/$trimId/full")) {
            is Car2DbResult.Success -> Car2DbResult.Success(result.value.toLookup())
            else -> @Suppress("UNCHECKED_CAST") (result as Car2DbResult<Car2DbVehicleLookup>)
        }
    }

    suspend fun getTrim(trimId: Int): Car2DbResult<Car2DbTrim> {
        if (!isEnabled) return Car2DbResult.Disabled
        if (trimId <= 0) return Car2DbResult.Malformed("Invalid trimId: $trimId")
        return requestInternal(path = "/trims/$trimId")
    }

    suspend fun getMakes(
        typeId: Int? = null,
        page: Int = 1,
        itemsPerPage: Int = 50
    ): Car2DbResult<Car2DbCollection<Car2DbMake>> {
        if (!isEnabled) return Car2DbResult.Disabled
        val params = mutableMapOf("page" to page.toString(), "itemsPerPage" to itemsPerPage.toString())
        typeId?.let { params["typeId"] = it.toString() }
        return requestInternal(path = "/makes", params = params)
    }

    suspend fun getModels(
        makeId: Int? = null,
        page: Int = 1,
        itemsPerPage: Int = 50
    ): Car2DbResult<Car2DbCollection<Car2DbModel>> {
        if (!isEnabled) return Car2DbResult.Disabled
        val params = mutableMapOf("page" to page.toString(), "itemsPerPage" to itemsPerPage.toString())
        makeId?.let { params["makeId"] = it.toString() }
        return requestInternal(path = "/models", params = params)
    }

    suspend fun getYear(year: Int): Car2DbResult<Car2DbYear> {
        if (!isEnabled) return Car2DbResult.Disabled
        if (year !in 1900..2100) return Car2DbResult.Malformed("Year out of range: $year")
        return requestInternal(path = "/years/$year")
    }

    fun close() {
        httpClient.close()
    }

    // ----- Internos -----

    private suspend inline fun <reified T> requestInternal(
        path: String,
        params: Map<String, String> = emptyMap()
    ): Car2DbResult<T> {
        return try {
            val response = httpClient.request(baseUrl + path) {
                method = HttpMethod.Get
                applyAuthHeaders()
                params.forEach { (k, v) -> parameter(k, v) }
            }
            handleResponse<T>(response)
        } catch (e: ResponseException) {
            Car2DbResult.ApiError(
                status = e.response.status.value,
                title = e.response.status.description,
                detail = e.message ?: "Response error"
            )
        } catch (e: java.io.IOException) {
            Car2DbResult.NetworkError(e)
        } catch (e: java.net.SocketTimeoutException) {
            Car2DbResult.NetworkError(e)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Car2DbResult.NetworkError(e)
        }
    }

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): Car2DbResult<T> {
        val status = response.status
        if (status.isSuccess()) {
            return try {
                val value: T = response.body()
                Car2DbResult.Success(value)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val raw = try { response.bodyAsText().take(500) } catch (_: Throwable) { null }
                Car2DbResult.Malformed(
                    "Failed to deserialize ${T::class.java.simpleName}: ${e.message}",
                    raw
                )
            }
        }
        return when (status.value) {
            401, 403 -> {
                val raw = try { response.bodyAsText() } catch (_: Throwable) { null }
                val err = parseError(raw)
                Car2DbResult.AuthMissing(err?.detail ?: err?.title ?: "Unauthorized")
            }
            429 -> {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: 60L
                Car2DbResult.RateLimited(retryAfter)
            }
            else -> {
                val raw = try { response.bodyAsText() } catch (_: Throwable) { null }
                val err = parseError(raw)
                Car2DbResult.ApiError(
                    status = status.value,
                    title = err?.title ?: status.description,
                    detail = err?.detail ?: "Unknown error"
                )
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuthHeaders() {
        header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
        header(HttpHeaders.Referrer, config.referer)
        header(HttpHeaders.Accept, "application/ld+json")
        if (config.language.isNotBlank()) {
            header(HttpHeaders.AcceptLanguage, config.language)
        }
    }

    private fun parseError(raw: String?): Car2DbError? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(Car2DbError.serializer(), raw)
        } catch (_: Throwable) {
            null
        }
    }

    private fun sanitizeQuery(input: String): String {
        val trimmed = input.trim()
        if (trimmed.length > 80) return trimmed.substring(0, 80)
        return trimmed.filter { c ->
            c.isLetterOrDigit() || c == ' ' || c == '-' || c == '.' || c == '+'
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://v3.api.car2db.com"

        fun backoffMs(attempt: Int, baseMs: Long = 1000L, jitterMs: Long = 250L, capMs: Long = 30_000L): Long {
            // Exponential base for this attempt: 2^attempt * baseMs.
            // Use Double to avoid Long overflow on large attempts.
            val base = (baseMs.toDouble() * 2.0.pow(attempt.toDouble())).toLong()
            // Jitter is bounded so the next attempt always exceeds this one:
            // jitter < nextBase - thisBase keeps the order guarantee intact.
            val nextBase = (baseMs.toDouble() * 2.0.pow((attempt + 1).toDouble())).toLong()
            val safeJitterMs = (nextBase - base).coerceAtMost(jitterMs).coerceAtLeast(0L)
            val jitter = (Math.random() * safeJitterMs.toDouble()).toLong()
            val raw = base + jitter
            return raw.coerceAtMost(capMs)
        }
    }
}