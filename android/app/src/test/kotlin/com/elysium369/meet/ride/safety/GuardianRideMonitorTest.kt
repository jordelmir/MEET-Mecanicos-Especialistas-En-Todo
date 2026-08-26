package com.elysium369.meet.ride.safety

import org.junit.Assert.*
import org.junit.Test

class GuardianRideMonitorTest {

    @Test
    fun detects_route_deviation_when_exceeding_threshold() {
        val signal = GuardianRideMonitor.detectRouteDeviation(
            expectedBearingDeg = 0f,
            actualBearingDeg = 60f,
            deviationDurationSeconds = 75,
        )

        assertNotNull(signal)
        assertEquals(SafetySignalType.ROUTE_DEVIATION, signal?.type)
        assertEquals(SafetySignalSeverity.MEDIUM, signal?.severity)
        assertEquals("DERIVED", signal?.evidenceProvenance)
    }

    @Test
    fun does_not_detect_deviation_under_duration_threshold() {
        val signal = GuardianRideMonitor.detectRouteDeviation(
            expectedBearingDeg = 0f,
            actualBearingDeg = 60f,
            deviationDurationSeconds = 30, // < 60s
        )

        assertNull(signal)
    }

    @Test
    fun detects_unexpected_stop_during_active_trip() {
        val signal = GuardianRideMonitor.detectUnexpectedStop(
            speedMps = 0.2f,
            stationaryDurationSeconds = 200,
            thresholdSeconds = 180,
        )

        assertNotNull(signal)
        assertEquals(SafetySignalType.UNEXPECTED_STOP, signal?.type)
        assertEquals(SafetySignalSeverity.MEDIUM, signal?.severity)
        assertEquals("OBSERVED", signal?.evidenceProvenance)
    }

    @Test
    fun detects_extreme_speed_exceeding_150_kmh() {
        val signal = GuardianRideMonitor.detectExtremeSpeed(
            speedMps = 45f, // 162 km/h
        )

        assertNotNull(signal)
        assertEquals(SafetySignalType.EXTREME_SPEED, signal?.type)
        assertEquals(SafetySignalSeverity.CRITICAL, signal?.severity)
    }

    @Test
    fun detects_severe_braking_or_crash_acceleration() {
        val signal = GuardianRideMonitor.detectCrashAcceleration(
            accelerationMagnitudeMs2 = 45.0f, // ~4.6g
        )

        assertNotNull(signal)
        assertEquals(SafetySignalType.CRASH_ACCELERATION, signal?.type)
        assertEquals(SafetySignalSeverity.CRITICAL, signal?.severity)
    }
}
