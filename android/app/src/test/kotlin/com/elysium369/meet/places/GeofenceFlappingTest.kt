package com.elysium369.meet.places

import com.elysium369.meet.presence.PresenceSample
import org.junit.Assert.assertEquals
import org.junit.Test

class GeofenceFlappingTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `dual boundary hysteresis and dwell debounce completely eliminate geofence flapping`() {
        val place = Place(
            placeId = "place-home",
            ownerPrincipalId = "user-alice",
            name = "Casa",
            latitude = 9.9333,
            longitude = -84.0833,
            radiusMeters = 100.0, // 100m radius
        )

        var obs = PlaceObservationState(
            placeId = place.placeId,
            principalId = "user-alice",
        )

        // 1. Initial observation inside (50m from center)
        val sample1 = createSample(9.9333, -84.08285, now) // ~50m
        obs = PlaceEvaluationEngine.evaluate(place, obs, sample1, now)
        // Must be DWELLING initially (dwell debounce)
        assertEquals(PlaceState.DWELLING, obs.currentState)
        assertEquals(0, obs.enterTransitionCount)

        // 2. 60 seconds later, still inside -> Transitions to INSIDE
        val sample2 = createSample(9.9333, -84.08285, now + 65_000L)
        obs = PlaceEvaluationEngine.evaluate(place, obs, sample2, now + 65_000L)
        assertEquals(PlaceState.INSIDE, obs.currentState)
        assertEquals(1, obs.enterTransitionCount)

        // 3. Flapping jitter: user moves to 110m (outside 100m radius, but within 100m + 30m hysteresis)
        val sample3 = createSample(9.9333, -84.0823, now + 70_000L) // ~110m
        obs = PlaceEvaluationEngine.evaluate(place, obs, sample3, now + 70_000L)
        // Must PRESERVE INSIDE state! No premature exit event!
        assertEquals(PlaceState.INSIDE, obs.currentState)
        assertEquals(0, obs.exitTransitionCount)

        // 4. Jitter moves back to 95m
        val sample4 = createSample(9.9333, -84.08244, now + 75_000L) // ~95m
        obs = PlaceEvaluationEngine.evaluate(place, obs, sample4, now + 75_000L)
        assertEquals(PlaceState.INSIDE, obs.currentState)
        assertEquals(1, obs.enterTransitionCount)
        assertEquals(0, obs.exitTransitionCount)

        // 5. User genuinely leaves beyond hysteresis (140m > 130m buffer)
        val sample5 = createSample(9.9333, -84.0820, now + 80_000L) // ~145m
        obs = PlaceEvaluationEngine.evaluate(place, obs, sample5, now + 80_000L)
        assertEquals(PlaceState.OUTSIDE, obs.currentState)
        assertEquals(1, obs.exitTransitionCount)

        // 6. User lingers at 110m on the outside -> Remains OUTSIDE because entry requires <= 100m
        val sample6 = createSample(9.9333, -84.0823, now + 85_000L) // ~110m
        obs = PlaceEvaluationEngine.evaluate(place, obs, sample6, now + 85_000L)
        assertEquals(PlaceState.OUTSIDE, obs.currentState)
        assertEquals(1, obs.enterTransitionCount) // Has NOT flapped back to inside!
    }

    private fun createSample(lat: Double, lon: Double, timeMs: Long): PresenceSample {
        return PresenceSample(
            sampleId = "samp-$timeMs",
            principalId = "user-alice",
            deviceId = "dev-alice",
            streamId = "stream-1",
            sequence = timeMs,
            capturedAt = timeMs,
            receivedAt = timeMs,
            latitude = lat,
            longitude = lon,
            accuracyMeters = 5f,
        )
    }
}
