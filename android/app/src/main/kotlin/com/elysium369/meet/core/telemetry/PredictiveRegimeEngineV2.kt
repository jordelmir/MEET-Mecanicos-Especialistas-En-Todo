package com.elysium369.meet.core.telemetry

enum class VehicleOperatingRegime {
    IDLE,
    CITY_CRUISE,
    HIGHWAY_CRUISE,
    WIDE_OPEN_THROTTLE_ACCELERATION,
    DECELERATION_FUEL_CUT,
    COLD_START_WARMUP,
    UNKNOWN_INSUFFICIENT_TELEMETRY,
}

data class RegimeObservation(
    val regime: VehicleOperatingRegime,
    val confidence: Double,
    val rationale: String,
)

data class RegimeSensorSnapshot(
    val rpm: Float?,
    val speedKmh: Float?,
    val throttlePercent: Float?,
    val engineLoadPercent: Float?,
    val coolantTempC: Float?,
    val sessionElapsedSeconds: Long,
)

/**
 * PredictiveRegimeEngineV2 — Classifies vehicle operating regimes based strictly on physical evidence.
 * Prevents false positive anomaly flags during legitimate high-load/warmup transients.
 */
object PredictiveRegimeEngineV2 {

    fun classifyRegime(snapshot: RegimeSensorSnapshot): RegimeObservation {
        val rpm = snapshot.rpm
        val speed = snapshot.speedKmh
        val throttle = snapshot.throttlePercent
        val load = snapshot.engineLoadPercent
        val coolant = snapshot.coolantTempC

        if (rpm == null || speed == null) {
            return RegimeObservation(
                regime = VehicleOperatingRegime.UNKNOWN_INSUFFICIENT_TELEMETRY,
                confidence = 0.0,
                rationale = "Missing essential telemetry (RPM or Speed missing)",
            )
        }

        // 1. Cold Start Warmup: Coolant < 50°C during the first 5 minutes
        if (coolant != null && coolant < 50f && snapshot.sessionElapsedSeconds < 300) {
            return RegimeObservation(
                regime = VehicleOperatingRegime.COLD_START_WARMUP,
                confidence = 95.0,
                rationale = "Coolant temperature ($coolant°C) below operating threshold in early drive",
            )
        }

        // 2. Wide Open Throttle (WOT)
        if ((throttle != null && throttle > 75f) || (load != null && load > 85f)) {
            return RegimeObservation(
                regime = VehicleOperatingRegime.WIDE_OPEN_THROTTLE_ACCELERATION,
                confidence = 98.0,
                rationale = "High throttle ($throttle%) / engine load ($load%) demand",
            )
        }

        // 3. Deceleration Fuel Cut-Off (DFCO)
        if (speed > 20f && rpm > 1200f && throttle != null && throttle <= 2f) {
            return RegimeObservation(
                regime = VehicleOperatingRegime.DECELERATION_FUEL_CUT,
                confidence = 92.0,
                rationale = "Closed throttle ($throttle%) with rolling vehicle speed ($speed km/h)",
            )
        }

        // 4. Idle
        if (speed < 3f && rpm in 500f..1150f && (throttle == null || throttle < 5f)) {
            return RegimeObservation(
                regime = VehicleOperatingRegime.IDLE,
                confidence = 96.0,
                rationale = "Stationary vehicle with idle engine RPM ($rpm)",
            )
        }

        // 5. Highway Cruise
        if (speed >= 65f && rpm in 1400f..3500f && (load == null || load in 15f..75f)) {
            return RegimeObservation(
                regime = VehicleOperatingRegime.HIGHWAY_CRUISE,
                confidence = 90.0,
                rationale = "Sustained highway speed ($speed km/h) under moderate engine load",
            )
        }

        // 6. City Cruise
        if (speed in 3f..65f && rpm < 3000f) {
            return RegimeObservation(
                regime = VehicleOperatingRegime.CITY_CRUISE,
                confidence = 88.0,
                rationale = "Urban driving speed ($speed km/h) with typical RPM profile",
            )
        }

        return RegimeObservation(
            regime = VehicleOperatingRegime.UNKNOWN_INSUFFICIENT_TELEMETRY,
            confidence = 40.0,
            rationale = "Transient unclassified regime dynamics",
        )
    }
}
