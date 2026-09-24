package com.elysium369.meet.core.emissions.regulations

/**
 * Official Costa Rica Vehicle Emissions Regulatory Engine (COSEVI / MOPT).
 * References:
 * - Manual de Procedimientos para la Revisión Técnica de Vehículos Automotores en las Estaciones de RTV (COSEVI/MOPT).
 * - Decreto Ejecutivo N° 37372-MOPT / Decreto N° 38516-MOPT.
 */
object CostaRicaGasolineRules {

    const val JURISDICTION = "CR"
    const val RULE_VERSION = "COSEVI_2026_GAS_4T"
    const val SOURCE_REFERENCE = "Manual de Procedimientos RTV / COSEVI / MOPT"

    fun resolveRuleSet(profile: RegulatoryVehicleProfile): EmissionRuleSet {
        // Effective year determination: prioritize Costa Rica entry date, fallback to model year
        val effectiveYear = resolveEffectiveYear(profile)

        val idleLimits = mutableListOf<EmissionLimit>()
        val accelLimits = mutableListOf<EmissionLimit>()

        when {
            effectiveYear != null && effectiveYear < 1995 -> {
                // Pre-1995 Gasoline 4-Stroke
                idleLimits.add(EmissionLimit(GasMetric.CO, max = 4.50, unit = "% vol"))
                idleLimits.add(EmissionLimit(GasMetric.HC, max = 650.0, unit = "ppm"))
                idleLimits.add(EmissionLimit(GasMetric.CO2, min = 10.0, unit = "% vol"))

                accelLimits.add(EmissionLimit(GasMetric.CO, max = 4.00, unit = "% vol"))
                accelLimits.add(EmissionLimit(GasMetric.HC, max = 600.0, unit = "ppm"))
                accelLimits.add(EmissionLimit(GasMetric.CO2, min = 10.0, unit = "% vol"))
            }
            effectiveYear != null && effectiveYear in 1995..1998 -> {
                // 1995 - 1998 Gasoline 4-Stroke
                idleLimits.add(EmissionLimit(GasMetric.CO, max = 1.00, unit = "% vol"))
                idleLimits.add(EmissionLimit(GasMetric.HC, max = 300.0, unit = "ppm"))
                idleLimits.add(EmissionLimit(GasMetric.CO2, min = 10.0, unit = "% vol"))

                accelLimits.add(EmissionLimit(GasMetric.CO, max = 0.80, unit = "% vol"))
                accelLimits.add(EmissionLimit(GasMetric.HC, max = 250.0, unit = "ppm"))
                accelLimits.add(EmissionLimit(GasMetric.CO2, min = 11.0, unit = "% vol"))
            }
            else -> {
                // Post-1999 Gasoline 4-Stroke (Standard for Hyundai Accent 2005, etc.)
                idleLimits.add(EmissionLimit(GasMetric.CO, max = 0.50, unit = "% vol"))
                idleLimits.add(EmissionLimit(GasMetric.HC, max = 125.0, unit = "ppm"))
                idleLimits.add(EmissionLimit(GasMetric.CO2, min = 10.0, unit = "% vol"))

                accelLimits.add(EmissionLimit(GasMetric.CO, max = 0.30, unit = "% vol"))
                accelLimits.add(EmissionLimit(GasMetric.HC, max = 100.0, unit = "ppm"))
                accelLimits.add(EmissionLimit(GasMetric.CO2, min = 12.0, unit = "% vol"))
            }
        }

        // Lambda Criteria: Applied to vehicles registered / imported on or after 2012-10-26
        if (isLambdaRegulationApplicable(profile)) {
            accelLimits.add(EmissionLimit(GasMetric.LAMBDA, min = 0.93, max = 1.07, unit = "ratio"))
        }

        return EmissionRuleSet(
            jurisdiction = JURISDICTION,
            version = RULE_VERSION,
            effectiveFrom = "1999-01-01",
            sourceReference = SOURCE_REFERENCE,
            idleLimits = idleLimits,
            acceleratedLimits = accelLimits
        )
    }

    fun isLambdaRegulationApplicable(profile: RegulatoryVehicleProfile): Boolean {
        val entryDateStr = profile.costaRicaEntryDate
        if (entryDateStr != null && entryDateStr.length >= 10) {
            // Compare ISO date string "YYYY-MM-DD" lexicographically
            return entryDateStr >= "2012-10-26"
        }
        val firstReg = profile.firstRegistrationDate
        if (firstReg != null && firstReg.length >= 10) {
            return firstReg >= "2012-10-26"
        }
        val year = profile.modelYear
        return year != null && year >= 2013
    }

    private fun resolveEffectiveYear(profile: RegulatoryVehicleProfile): Int? {
        val entryStr = profile.costaRicaEntryDate
        if (!entryStr.isNullOrBlank() && entryStr.length >= 4) {
            val year = entryStr.substring(0, 4).toIntOrNull()
            if (year != null) return year
        }
        val firstReg = profile.firstRegistrationDate
        if (!firstReg.isNullOrBlank() && firstReg.length >= 4) {
            val year = firstReg.substring(0, 4).toIntOrNull()
            if (year != null) return year
        }
        return profile.modelYear
    }
}
