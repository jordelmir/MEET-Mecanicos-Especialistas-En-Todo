package com.elysium369.meet.ride.safety

/**
 * Guardian Mobility: Pure domain anomaly detector for ride safety.
 *
 * Generates SafetySignal observations from sensor data.
 * Does NOT declare criminal activity — only observes and classifies.
 * Evidence provenance is always OBSERVED or DERIVED, never ASSERTED.
 */
object GuardianRideMonitor {

    data class SafetySignal(
        val type: SafetySignalType,
        val severity: SafetySignalSeverity,
        val evidenceProvenance: String, // "OBSERVED" | "DERIVED" | "UNKNOWN"
        val description: String,
    )

    /**
     * Detects route deviation by comparing expected bearing to actual bearing.
     * Threshold: > 45° deviation sustained for > 60 seconds.
     */
    fun detectRouteDeviation(
        expectedBearingDeg: Float,
        actualBearingDeg: Float,
        deviationDurationSeconds: Int,
    ): SafetySignal? {
        val diff = angleDifference(expectedBearingDeg, actualBearingDeg)
        if (diff > 45f && deviationDurationSeconds > 60) {
            return SafetySignal(
                type = SafetySignalType.ROUTE_DEVIATION,
                severity = if (diff > 90f) SafetySignalSeverity.HIGH else SafetySignalSeverity.MEDIUM,
                evidenceProvenance = "DERIVED",
                description = "Desviación de ${diff.toInt()}° sostenida por ${deviationDurationSeconds}s",
            )
        }
        return null
    }

    /**
     * Detects unexpected stop: vehicle stationary (< 1 m/s) for > thresholdSeconds
     * during an active trip segment that should be moving.
     */
    fun detectUnexpectedStop(
        speedMps: Float,
        stationaryDurationSeconds: Int,
        thresholdSeconds: Int = 180,
    ): SafetySignal? {
        if (speedMps < 1.0f && stationaryDurationSeconds > thresholdSeconds) {
            return SafetySignal(
                type = SafetySignalType.UNEXPECTED_STOP,
                severity = if (stationaryDurationSeconds > 300) SafetySignalSeverity.HIGH else SafetySignalSeverity.MEDIUM,
                evidenceProvenance = "OBSERVED",
                description = "Vehículo detenido ${stationaryDurationSeconds}s durante viaje activo",
            )
        }
        return null
    }

    /**
     * Detects extreme speed: > 150 km/h sustained.
     */
    fun detectExtremeSpeed(speedMps: Float): SafetySignal? {
        val speedKmh = speedMps * 3.6f
        if (speedKmh > 150f) {
            return SafetySignal(
                type = SafetySignalType.EXTREME_SPEED,
                severity = SafetySignalSeverity.CRITICAL,
                evidenceProvenance = "OBSERVED",
                description = "Velocidad extrema: ${speedKmh.toInt()} km/h",
            )
        }
        return null
    }

    /**
     * Detects crash-like deceleration: > 4g instantaneous.
     * Typical vehicle crash produces 20-60g; 4g threshold catches severe braking.
     */
    fun detectCrashAcceleration(accelerationMagnitudeMs2: Float): SafetySignal? {
        val gForce = accelerationMagnitudeMs2 / 9.81f
        if (gForce > 4.0f) {
            return SafetySignal(
                type = SafetySignalType.CRASH_ACCELERATION,
                severity = SafetySignalSeverity.CRITICAL,
                evidenceProvenance = "OBSERVED",
                description = "Desaceleración de impacto: ${String.format("%.1f", gForce)}g",
            )
        }
        return null
    }

    private fun angleDifference(a: Float, b: Float): Float {
        val diff = Math.abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }
}
