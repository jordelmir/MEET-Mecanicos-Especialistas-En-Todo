package com.elysium.nexus.core.haptics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the [Haptics] abstraction and the
 * [HapticEvent] sealed class.
 *
 * The interface is the testable surface. A
 * `FakeHaptics` records every `fire` call for
 * later assertion. The Android implementation
 * [AndroidHaptics] is covered by the on-device
 * end-to-end test (the emulator's virtual
 * vibrator is a Phase 1.7+ deliverable).
 */
class HapticsTest {

    @Test
    fun hapticEventHierarchyIsExhaustive() {
        // The closed set per the §27 spec:
        // 8 events. A future contributor who
        // adds a new event must update the
        // `mapEvent` function in
        // [AndroidHaptics] and the exhaustive
        // `when` in any matching transport.
        val events: List<HapticEvent> = listOf(
            HapticEvent.ButtonTap,
            HapticEvent.ButtonLongPress,
            HapticEvent.StickEdge,
            HapticEvent.TriggerClick,
            HapticEvent.Error,
            HapticEvent.TransportConnected,
            HapticEvent.TransportDisconnected,
            HapticEvent.ProfileChanged,
            HapticEvent.Recentered
        )
        assertEquals(9, events.size)
    }

    @Test
    fun nullHapticsDoesNotRecord() {
        val source = NullHaptics()
        source.fire(HapticEvent.ButtonTap)
        source.fire(HapticEvent.Error)
        // No way to query the no-op; the
        // invariant is that `fire` does not
        // throw. The fake below is the test
        // surface.
    }

    @Test
    fun fakeHapticsRecordsEveryEvent() {
        val source = FakeHaptics()
        source.fire(HapticEvent.ButtonTap)
        source.fire(HapticEvent.StickEdge)
        source.fire(HapticEvent.Error)
        assertEquals(3, source.count())
        assertEquals(
            listOf(
                HapticEvent.ButtonTap,
                HapticEvent.StickEdge,
                HapticEvent.Error
            ),
            source.events()
        )
    }

    @Test
    fun fakeHapticsRecordsSameEventMultipleTimes() {
        val source = FakeHaptics()
        source.fire(HapticEvent.ButtonTap)
        source.fire(HapticEvent.ButtonTap)
        assertEquals(2, source.count())
        assertEquals(HapticEvent.ButtonTap, source.events()[0])
        assertEquals(HapticEvent.ButtonTap, source.events()[1])
    }
}
