package com.elysium369.meet.core.obd

/**
 * TurboBoostGauge — Medidor de presión de boost para vehículos turbo/supercargados.
 * Usa sensor MAP vs presión barométrica para calcular boost positivo (PSI/Bar).
 * Incluye historial de pico y protección de sobreboost.
 */
class TurboBoostGauge {

    data class BoostSnapshot(
        val boostPsi: Float,      // Presión de boost en PSI (positivo = boost, negativo = vacío)
        val boostBar: Float,      // Presión de boost en Bar
        val boostKpa: Float,      // kPa sobre atmosférica
        val vacuumInHg: Float,    // Vacío en inHg (para NA)
        val peakBoostPsi: Float,  // Máximo boost registrado
        val mapKpa: Float,        // MAP absoluto
        val baroKpa: Float,       // Presión barométrica
        val isBoosting: Boolean,  // ¿Está en boost positivo?
        val isOverboost: Boolean, // Advertencia de sobreboost
        val wastegateEstimate: String // Estado estimado del wastegate
    )

    private var peakBoost = 0f
    private var overboostThresholdPsi = 22f // Default, configurable

    fun setOverboostThreshold(psi: Float) { overboostThresholdPsi = psi }
    fun resetPeak() { peakBoost = 0f }

    fun calculate(data: Map<String, Float>): BoostSnapshot {
        val mapKpa = data["010B"] ?: data["MAP"] ?: 101.3f
        val baroKpa = data["0133"] ?: data["BARO"] ?: 101.3f // Barometric if available
        val rpm = data["010C"] ?: data["RPM"] ?: 0f

        val boostKpa = mapKpa - baroKpa
        val boostPsi = boostKpa * 0.14504f
        val boostBar = boostKpa / 100f
        val vacuumInHg = if (boostKpa < 0) boostKpa * -0.2953f else 0f

        if (boostPsi > peakBoost) peakBoost = boostPsi
        val isBoosting = boostPsi > 0.5f
        val isOverboost = boostPsi > overboostThresholdPsi

        val wastegate = when {
            !isBoosting -> "N/A (vacío)"
            boostPsi < 5f -> "Abriendo"
            boostPsi < 15f -> "Controlando"
            boostPsi > overboostThresholdPsi -> "⚠️ SOBREBOOST"
            else -> "Boost alto normal"
        }

        return BoostSnapshot(
            boostPsi = boostPsi,
            boostBar = boostBar,
            boostKpa = boostKpa,
            vacuumInHg = vacuumInHg,
            peakBoostPsi = peakBoost,
            mapKpa = mapKpa,
            baroKpa = baroKpa,
            isBoosting = isBoosting,
            isOverboost = isOverboost,
            wastegateEstimate = wastegate
        )
    }
}
