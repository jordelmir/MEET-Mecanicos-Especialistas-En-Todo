package com.elysium369.meet.ride.dispatch

import org.junit.Assert.*
import org.junit.Test

class RideExposureTrackerTest {

    @Test
    fun does_not_ack_immediately_when_card_becomes_visible() {
        val tracker = RideExposureTracker()
        val shouldAck = tracker.onRequestVisible("req1", nowMs = 1000L)
        assertFalse(shouldAck)
    }

    @Test
    fun acks_when_card_has_been_visible_for_500ms() {
        val tracker = RideExposureTracker()
        tracker.onRequestVisible("req1", nowMs = 1000L)
        val shouldAck = tracker.onRequestVisible("req1", nowMs = 1500L) // +500ms
        assertTrue(shouldAck)
    }

    @Test
    fun does_not_ack_if_card_was_hidden_before_500ms() {
        val tracker = RideExposureTracker()
        tracker.onRequestVisible("req1", nowMs = 1000L)
        tracker.onRequestHidden("req1") // Hidden at 1300ms
        
        val shouldAck = tracker.onRequestVisible("req1", nowMs = 1400L) // Re-entered, timer reset
        assertFalse(shouldAck)
    }

    @Test
    fun does_not_re_ack_already_acknowledged_request() {
        val tracker = RideExposureTracker()
        tracker.onRequestVisible("req1", nowMs = 1000L)
        tracker.markAcknowledged("req1")
        
        val shouldAck = tracker.onRequestVisible("req1", nowMs = 2000L)
        assertFalse(shouldAck)
    }
}
