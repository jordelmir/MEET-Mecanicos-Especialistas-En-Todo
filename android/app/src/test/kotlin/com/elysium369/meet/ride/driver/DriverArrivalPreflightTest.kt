package com.elysium369.meet.ride.driver

import com.elysium369.meet.ride.map.RideGeoPoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverArrivalPreflightTest {

    @Test
    fun `arrival rejects stale location`() {
        val sample = DriverLocationSample(
            latitude = 9.9300,
            longitude = -84.0800,
            accuracyMeters = 8.0,
            capturedAt = Instant.parse("2026-09-16T20:00:00Z"),
            capturedAtElapsedRealtimeNanos = 10_000_000_000L,
        )
        val result = DriverArrivalPreflight.evaluate(
            location = sample,
            pickup = RideGeoPoint(
                latitude = 9.9300,
                longitude = -84.0800,
                accuracyMeters = null,
                capturedAtEpochMs = 0L,
            ),
            nowElapsedRealtimeNanos = 30_000_000_000L, // 20s later (> 15s)
            policy = ArrivalPolicy(
                maxLocationAgeMillis = 15_000L,
            ),
        )
        assertTrue("Expected StaleLocation", result is ArrivalPreflightResult.StaleLocation)
        assertEquals(20_000L, (result as ArrivalPreflightResult.StaleLocation).ageMillis)
    }

    @Test
    fun `arrival rejects driver far from pickup`() {
        val sample = DriverLocationSample(
            latitude = 9.9400,
            longitude = -84.0800,
            accuracyMeters = 5.0,
            capturedAt = Instant.parse("2026-09-16T20:00:00Z"),
            capturedAtElapsedRealtimeNanos = 10_000_000_000L,
        )
        val result = DriverArrivalPreflight.evaluate(
            location = sample,
            pickup = RideGeoPoint(
                latitude = 9.9300,
                longitude = -84.0800,
                accuracyMeters = null,
                capturedAtEpochMs = 0L,
            ),
            nowElapsedRealtimeNanos = 11_000_000_000L, // 1s later
            policy = ArrivalPolicy(
                maxDistanceMeters = 120.0,
            ),
        )
        assertTrue("Expected TooFar", result is ArrivalPreflightResult.TooFar)
        assertTrue((result as ArrivalPreflightResult.TooFar).distanceMeters > 1000.0)
    }

    @Test
    fun `arrival rejects poor accuracy`() {
        val sample = DriverLocationSample(
            latitude = 9.9300,
            longitude = -84.0800,
            accuracyMeters = 65.0, // > 50m
            capturedAt = Instant.parse("2026-09-16T20:00:00Z"),
            capturedAtElapsedRealtimeNanos = 10_000_000_000L,
        )
        val result = DriverArrivalPreflight.evaluate(
            location = sample,
            pickup = RideGeoPoint(
                latitude = 9.9300,
                longitude = -84.0800,
                accuracyMeters = null,
                capturedAtEpochMs = 0L,
            ),
            nowElapsedRealtimeNanos = 11_000_000_000L,
            policy = ArrivalPolicy(
                maxAccuracyMeters = 50.0,
            ),
        )
        assertTrue("Expected PoorAccuracy", result is ArrivalPreflightResult.PoorAccuracy)
        assertEquals(65.0, (result as ArrivalPreflightResult.PoorAccuracy).accuracyMeters, 0.001)
    }

    @Test
    fun `arrival allows fresh driver within radius and good accuracy`() {
        val sample = DriverLocationSample(
            latitude = 9.9300,
            longitude = -84.0800,
            accuracyMeters = 10.0,
            capturedAt = Instant.parse("2026-09-16T20:00:00Z"),
            capturedAtElapsedRealtimeNanos = 10_000_000_000L,
        )
        val result = DriverArrivalPreflight.evaluate(
            location = sample,
            pickup = RideGeoPoint(
                latitude = 9.9302,
                longitude = -84.0802,
                accuracyMeters = null,
                capturedAtEpochMs = 0L,
            ),
            nowElapsedRealtimeNanos = 12_000_000_000L, // 2s later
            policy = ArrivalPolicy(
                maxLocationAgeMillis = 15_000L,
                maxAccuracyMeters = 50.0,
                maxDistanceMeters = 120.0,
            ),
        )
        assertEquals(ArrivalPreflightResult.Allowed, result)
    }
}
