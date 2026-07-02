package com.elysium369.meet.data.car2db

import com.elysium.vanguard.forge.domain.SafetyClassification
import java.util.concurrent.TimeUnit

/**
 * Adaptadores de modelo Car2DB → modelo de dominio Forge.
 *
 * Mantener este archivo como "puente" entre el cliente y el resto de la app:
 * - Si Car2DB cambia el schema, sólo modificamos [Car2DbTrim.toLookup] y [Car2DbSearchTrim.toSearchTrim].
 * - Si Forge cambia su modelo de dominio, también sólo aquí.
 */

internal fun Car2DbTrim.toLookup(): Car2DbVehicleLookup {
    return Car2DbVehicleLookup(
        trimId = id,
        make = breadcrumbs?.make?.name ?: "",
        model = breadcrumbs?.model?.name ?: "",
        trimName = name,
        yearBegin = yearBegin,
        yearEnd = yearEnd,
        engineDisplacementL = keySpecifications?.engineVolume,
        powerHp = keySpecifications?.power,
        torqueNm = keySpecifications?.torque,
        transmission = keySpecifications?.transmission,
        drivetrain = keySpecifications?.drivetrain,
        fuelType = keySpecifications?.fuelType,
        bodyType = keySpecifications?.bodyType,
        lengthMm = keySpecifications?.lengthMm,
        widthMm = keySpecifications?.widthMm,
        heightMm = keySpecifications?.heightMm,
        wheelbaseMm = keySpecifications?.wheelbaseMm,
        curbWeightKg = keySpecifications?.curbWeightKg,
        topSpeedKph = keySpecifications?.topSpeedKph,
        acceleration0To100 = keySpecifications?.acceleration0To100,
        rawSpecifications = specifications,
        safetyClassification = SafetyClassification.SAFETY_CRITICAL_UNCERTIFIED,
        fetchedAtMs = System.currentTimeMillis()
    )
}

internal fun Car2DbSearchTrim.toSearchTrim(): Car2DbSearchTrim {
    // No-op aquí — se usa directamente. Reservado para transformaciones futuras
    // (normalización de transmission strings, fuel types, etc.).
    return this
}

internal fun Car2DbSearchTrim.makeName(): String =
    // No podemos inferir make desde un search trim sin breadcrumbs; usamos heurística.
    "Unknown"

/**
 * Sanitiza un DTC code para construir una query de búsqueda útil.
 * P0301 → "P0301 spark plug misfire"
 */
fun dtcToSearchQuery(dtcCode: String): String {
    val code = dtcCode.uppercase().trim()
    if (!code.matches(Regex("^[A-Z]\\d{4}$"))) return ""
    val prefix = code.first()
    val hint = when (prefix) {
        'P' -> "engine"
        'B' -> "body"
        'C' -> "chassis"
        'U' -> "network"
        else -> ""
    }
    return if (hint.isNotBlank()) "$code $hint" else code
}

/**
 * Backoff strategy wrapper. Expone `delaySeconds` para uso con `delay()`.
 */
class BackoffStrategy(
    private val maxAttempts: Int = 3,
    private val baseMs: Long = 1_000L,
    private val maxDelaySec: Long = 30L
) {
    fun delayMs(attempt: Int): Long {
        val capped = attempt.coerceIn(0, maxAttempts - 1)
        val raw = baseMs * (1L shl capped)
        val cappedMs = raw.coerceAtMost(TimeUnit.SECONDS.toMillis(maxDelaySec))
        val jitter = (Math.random() * 250.0).toLong()
        return cappedMs + jitter
    }
}