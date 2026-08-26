package com.elysium369.meet.ride.presence

import org.junit.Assert.*
import org.junit.Test

class RideLocationSamplerTest {

    @Test
    fun interval_matches_availability_state() {
        assertEquals(8000L, RideLocationSampler.intervalMs(RideDriverAvailability.AVAILABLE))
        assertEquals(2000L, RideLocationSampler.intervalMs(RideDriverAvailability.IN_TRIP))
        assertEquals(Long.MAX_VALUE, RideLocationSampler.intervalMs(RideDriverAvailability.OFFLINE))
    }

    @Test
    fun should_report_when_time_interval_exceeded() {
        val prev = RideLocationSampler.LocationSample(
            latitude = 9.93,
            longitude = -84.08,
            accuracyM = 5f,
            heading = 0,
            speedMps = 0f,
            timestampMs = 1000L,
        )
        val current = RideLocationSampler.LocationSample(
            latitude = 9.93,
            longitude = -84.08,
            accuracyM = 5f,
            heading = 0,
            speedMps = 0f,
            timestampMs = 10_000L, // 9s elapsed > 8s interval
        )
        assertTrue(RideLocationSampler.shouldReport(prev, current, RideDriverAvailability.AVAILABLE))
    }

    @Test
    fun should_report_when_distance_threshold_exceeded() {
        val prev = RideLocationSampler.LocationSample(
            latitude = 9.9300,
            longitude = -84.0800,
            accuracyM = 5f,
            heading = 0,
            speedMps = 10f,
            timestampMs = 1000L,
        )
        val current = RideLocationSampler.LocationSample(
            latitude = 9.9306, // ~66 meters away
            longitude = -84.0800,
            accuracyM = 5f,
            heading = 0,
            speedMps = 10f,
            timestampMs = 2000L, // Only 1s elapsed
        )
        assertTrue(RideLocationSampler.shouldReport(prev, current, RideDriverAvailability.AVAILABLE))
    }

    @Test
    fun should_not_report_when_stationary_and_within_interval() {
        val prev = RideLocationSampler.LocationSample(
            latitude = 9.9300,
            longitude = -84.0800,
            accuracyM = 5f,
            heading = 0,
            speedMps = 0f,
            timestampMs = 1000L,
        )
        val current = RideLocationSampler.LocationSample(
            latitude = 9.9300,
            longitude = -84.0800,
            accuracyM = 5f,
            heading = 0,
            speedMps = 0f,
            timestampMs = 3000L, // 2s elapsed < 8s interval
        )
        assertFalse(RideLocationSampler.shouldReport(prev, current, RideDriverAvailability.AVAILABLE))
    }
}
