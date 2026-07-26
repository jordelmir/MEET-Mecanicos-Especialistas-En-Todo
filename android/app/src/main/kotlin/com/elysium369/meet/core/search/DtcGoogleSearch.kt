package com.elysium369.meet.core.search

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * The complete allowlist of vehicle data that may leave MEET in a DTC search.
 *
 * VIN, plate, user ID, location and diagnostic evidence are intentionally not
 * representable in this type.
 */
data class DtcGoogleSearchVehicleContext(
    val make: String?,
    val model: String?,
    val year: Int?,
    val transmissionType: String?,
    val displacementCc: Int?
)

data class DtcGoogleSearchRequest(
    val dtc: String,
    val query: String,
    val googleUrl: String
)

/**
 * Pure, deterministic contract for external DTC searches.
 *
 * A request can only target Google's HTTPS search endpoint. Vehicle data
 * enriches the query only when it is explicitly supplied by the active
 * vehicle and passes the narrow validity checks below.
 */
object DtcGoogleSearch {
    private val dtcPattern = Regex("^[PBCU][0-9A-F]{4}$")
    private val whitespace = Regex("\\s+")
    private val nonAlphaNumeric = Regex("[^a-z0-9áéíóúüñ]+")
    private val placeholderValues = setOf(
        "n/a",
        "na",
        "unknown",
        "desconocido",
        "generic",
        "genérico",
        "por confirmar",
        "not set"
    )

    fun buildRequest(
        rawDtc: String,
        vehicle: DtcGoogleSearchVehicleContext?
    ): DtcGoogleSearchRequest? {
        val dtc = rawDtc.trim().uppercase(Locale.ROOT)
        if (!dtcPattern.matches(dtc)) return null

        val queryParts = buildList {
            add(dtc)
            vehicle?.let { activeVehicle ->
                safeVehicleText(activeVehicle.make)?.let(::add)
                safeVehicleText(activeVehicle.model)?.let(::add)
                activeVehicle.year
                    ?.takeIf { it in MIN_VEHICLE_YEAR..MAX_VEHICLE_YEAR }
                    ?.toString()
                    ?.let(::add)
                normalizeTransmission(activeVehicle.transmissionType)?.let(::add)
                activeVehicle.displacementCc
                    ?.takeIf { it in MIN_DISPLACEMENT_CC..MAX_DISPLACEMENT_CC }
                    ?.let { add("$it cc") }
            }
        }
        val query = queryParts.joinToString(" ")
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())

        return DtcGoogleSearchRequest(
            dtc = dtc,
            query = query,
            googleUrl = "$GOOGLE_SEARCH_ENDPOINT?q=$encodedQuery"
        )
    }

    private fun safeVehicleText(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.replace(whitespace, " ")
            ?.take(MAX_VEHICLE_TEXT_LENGTH)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        return normalized.takeUnless {
            it.lowercase(Locale.ROOT) in placeholderValues
        }
    }

    private fun normalizeTransmission(value: String?): String? {
        val normalized = safeVehicleText(value)?.lowercase(Locale.ROOT) ?: return null
        val compact = normalized.replace(nonAlphaNumeric, "")

        return when {
            "manual" in normalized ||
                compact == "mt" ||
                compact.matches(Regex("\\d+mt")) -> "manual"

            "auto" in normalized ||
                "cvt" in compact ||
                "dct" in compact ||
                "dsg" in compact ||
                compact == "at" ||
                compact.matches(Regex("\\d+at")) -> "automático"

            else -> null
        }
    }

    private const val GOOGLE_SEARCH_ENDPOINT = "https://www.google.com/search"
    private const val MIN_VEHICLE_YEAR = 1886
    private const val MAX_VEHICLE_YEAR = 2100
    private const val MIN_DISPLACEMENT_CC = 50
    private const val MAX_DISPLACEMENT_CC = 20_000
    private const val MAX_VEHICLE_TEXT_LENGTH = 80
}
