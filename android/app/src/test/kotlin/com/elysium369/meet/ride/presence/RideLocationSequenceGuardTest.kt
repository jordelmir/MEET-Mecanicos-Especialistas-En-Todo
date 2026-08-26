package com.elysium369.meet.ride.presence

import org.junit.Assert.*
import org.junit.Test

class RideLocationSequenceGuardTest {

    @Test
    fun rejects_stale_location_sequence() {
        val result = RideLocationSequenceGuard.validate(
            previousSeq = 10L,
            newSeq = 9L,
            previousTimestampMs = 1000L,
            newTimestampMs = 2000L,
            previousLat = 9.93,
            previousLon = -84.08,
            newLat = 9.931,
            newLon = -84.081,
        )
        assertFalse(result.valid)
    }

    @Test
    fun rejects_duplicate_sequence() {
        val result = RideLocationSequenceGuard.validate(
            previousSeq = 10L,
            newSeq = 10L,
            previousTimestampMs = 1000L,
            newTimestampMs = 2000L,
            previousLat = 9.93,
            previousLon = -84.08,
            newLat = 9.931,
            newLon = -84.081,
        )
        assertFalse(result.valid)
    }

    @Test
    fun accepts_increasing_sequence() {
        val result = RideLocationSequenceGuard.validate(
            previousSeq = 10L,
            newSeq = 11L,
            previousTimestampMs = 1000L,
            newTimestampMs = 2000L,
            previousLat = 9.93,
            previousLon = -84.08,
            newLat = 9.9301,
            newLon = -84.0801,
        )
        assertTrue(result.valid)
    }

    @Test
    fun rejects_time_travel() {
        val result = RideLocationSequenceGuard.validate(
            previousSeq = 10L,
            newSeq = 11L,
            previousTimestampMs = 2000L,
            newTimestampMs = 1500L,
            previousLat = 9.93,
            previousLon = -84.08,
            newLat = 9.931,
            newLon = -84.081,
        )
        assertFalse(result.valid)
    }

    @Test
    fun rejects_invalid_latitude_longitude() {
        val result = RideLocationSequenceGuard.validate(
            previousSeq = 10L,
            newSeq = 11L,
            previousTimestampMs = 1000L,
            newTimestampMs = 2000L,
            previousLat = 9.93,
            previousLon = -84.08,
            newLat = 95.0,
            newLon = -84.08,
        )
        assertFalse(result.valid)
    }

    @Test
    fun rejects_impossible_teleportation_speed() {
        val result = RideLocationSequenceGuard.validate(
            previousSeq = 10L,
            newSeq = 11L,
            previousTimestampMs = 1000L,
            newTimestampMs = 2000L, // 1 second later
            previousLat = 9.93,
            previousLon = -84.08,
            newLat = 10.50, // ~65 km away in 1 second
            newLon = -84.08,
        )
        assertFalse(result.valid)
    }

    @Test
    fun accepts_realistic_speed() {
        val result = RideLocationSequenceGuard.validate(
            previousSeq = 10L,
            newSeq = 11L,
            previousTimestampMs = 1000L,
            newTimestampMs = 11000L, // 10 seconds later
            previousLat = 9.9300,
            previousLon = -84.0800,
            newLat = 9.9310, // ~110m away
            newLon = -84.0800,
        )
        assertTrue(result.valid)
    }
}
