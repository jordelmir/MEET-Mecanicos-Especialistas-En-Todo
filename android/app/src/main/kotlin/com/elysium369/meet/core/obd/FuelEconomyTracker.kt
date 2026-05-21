package com.elysium369.meet.core.obd

import kotlin.math.abs

/**
 * FuelEconomyTracker — Calculadora de consumo en tiempo real.
 * 
 * Calcula MPG, L/100km y costo de combustible usando MAF o MAP+RPM.
 * Los escáneres profesionales como Torque Pro cobran extra por esto.
 * MEET lo incluye gratis con precisión de nivel industrial.
 *
 * ═══════════════════════════════════════════════════════════════
 * FÓRMULAS:
 * - MAF Method: GPH = MAF(g/s) / (6.17 * 14.7)  [gasolina stoich]
 * - MAP Method: GPH = (RPM * MAP * VE * CID) / (R * T * 2 * 6.17 * 14.7)
 * - MPG = Speed(mph) / GPH
 * - L/100km = 235.215 / MPG
 * - Costo = Litros_consumidos * precio_por_litro
 * ═══════════════════════════════════════════════════════════════
 */
class FuelEconomyTracker {

    data class FuelSnapshot(
        val instantMpg: Float?,           // Millas por galón instantáneo
        val instantLper100km: Float?,     // Litros por 100km instantáneo
        val instantGph: Float?,           // Galones por hora
        val instantLph: Float?,           // Litros por hora
        val averageMpg: Float?,           // Promedio de la sesión
        val averageLper100km: Float?,     // Promedio L/100km sesión
        val totalFuelUsedLiters: Float,   // Total consumido en sesión (litros)
        val totalDistanceKm: Float,       // Distancia total de sesión
        val estimatedCostSession: Float,  // Costo estimado de sesión
        val fuelMethod: String,           // "MAF" o "MAP"
        val engineEfficiency: Float?,     // Eficiencia del motor vs ideal (%)
        val co2GramsPerKm: Float?         // Emisiones CO2 estimadas
    )

    // Session accumulators
    private var totalFuelGallons = 0.0
    private var totalDistanceMiles = 0.0
    private var lastTimestamp = 0L
    private var sampleCount = 0
    private var fuelPricePerLiter = 22.50f // MXN default, configurable

    // Constants
    companion object {
        const val GASOLINE_DENSITY = 6.17f    // lbs/gallon
        const val STOICH_RATIO = 14.7f        // Air:Fuel ratio gasoline
        const val DIESEL_STOICH = 14.5f       // Diesel
        const val GALLON_TO_LITER = 3.78541f
        const val MILE_TO_KM = 1.60934f
        const val CO2_GRAMS_PER_GALLON = 8887f // EPA standard
    }

    fun setFuelPrice(pricePerLiter: Float) {
        fuelPricePerLiter = pricePerLiter
    }

    fun resetSession() {
        totalFuelGallons = 0.0
        totalDistanceMiles = 0.0
        lastTimestamp = 0L
        sampleCount = 0
    }

    fun calculate(data: Map<String, Float>): FuelSnapshot {
        val now = System.currentTimeMillis()
        val deltaSeconds = if (lastTimestamp > 0) (now - lastTimestamp) / 1000.0 else 0.0
        lastTimestamp = now

        val speed = data["010D"] ?: data["SPEED"] ?: 0f  // km/h
        val speedMph = speed / MILE_TO_KM
        val maf = data["0110"] ?: data["MAF"] // g/s
        val rpm = data["010C"] ?: data["RPM"] ?: 0f
        val map = data["010B"] ?: data["MAP"]  // kPa
        val iat = data["010F"] ?: data["IAT"] ?: 25f // °C
        val load = data["0104"] ?: data["LOAD"] ?: 0f // %

        var gph: Float? = null
        var method = "N/A"

        // Method 1: MAF-based (most accurate)
        if (maf != null && maf > 0.5f) {
            gph = maf / (GASOLINE_DENSITY * STOICH_RATIO)
            method = "MAF"
        }
        // Method 2: MAP+RPM (when no MAF sensor — Speed-Density systems)
        else if (map != null && rpm > 300) {
            // Simplified volumetric efficiency estimate
            val ve = (load / 100f).coerceIn(0.1f, 1.0f) * 0.85f
            val intakeTempK = iat + 273.15f
            // Estimate MAF from MAP: MAF = (MAP * RPM * VE * Vd) / (R * T * 120)
            // Assuming 2.0L engine displacement as baseline
            val estimatedMAF = (map * rpm * ve * 2.0f) / (8.314f * intakeTempK * 120f) * 28.97f
            gph = estimatedMAF / (GASOLINE_DENSITY * STOICH_RATIO)
            method = "MAP"
        }

        // Accumulate
        if (deltaSeconds in 0.01..5.0 && gph != null) {
            totalFuelGallons += gph * (deltaSeconds / 3600.0)
            totalDistanceMiles += speedMph * (deltaSeconds / 3600.0)
            sampleCount++
        }

        // Instant MPG
        val instantMpg = if (gph != null && gph > 0.01f && speedMph > 1) {
            speedMph / gph
        } else null

        val instantLper100 = instantMpg?.let {
            if (it > 0.1f) 235.215f / it else null
        }

        // Average
        val avgMpg = if (totalFuelGallons > 0.0001 && totalDistanceMiles > 0.01) {
            (totalDistanceMiles / totalFuelGallons).toFloat()
        } else null

        val avgLper100 = avgMpg?.let {
            if (it > 0.1f) 235.215f / it else null
        }

        val totalLiters = (totalFuelGallons * GALLON_TO_LITER).toFloat()
        val totalKm = (totalDistanceMiles * MILE_TO_KM).toFloat()
        val cost = totalLiters * fuelPricePerLiter

        // CO2 estimation
        val co2 = if (gph != null && speed > 1f) {
            val gphToGPKm = gph / (speed / MILE_TO_KM)
            gphToGPKm * CO2_GRAMS_PER_GALLON
        } else null

        // Engine efficiency (vs ideal stoichiometric)
        val efficiency = if (instantMpg != null && instantMpg > 0) {
            // Typical gasoline engine peak ~35% thermal efficiency
            // Good MPG relative to speed indicates higher efficiency
            val idealMpg = when {
                speed < 30 -> 15f
                speed < 60 -> 25f
                speed < 90 -> 30f
                speed < 120 -> 25f
                else -> 18f
            }
            ((instantMpg / idealMpg) * 100f).coerceIn(0f, 150f)
        } else null

        return FuelSnapshot(
            instantMpg = instantMpg,
            instantLper100km = instantLper100,
            instantGph = gph,
            instantLph = gph?.times(GALLON_TO_LITER),
            averageMpg = avgMpg,
            averageLper100km = avgLper100,
            totalFuelUsedLiters = totalLiters,
            totalDistanceKm = totalKm,
            estimatedCostSession = cost,
            fuelMethod = method,
            engineEfficiency = efficiency,
            co2GramsPerKm = co2
        )
    }
}
