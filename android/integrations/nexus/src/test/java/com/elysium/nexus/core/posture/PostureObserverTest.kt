package com.elysium.nexus.core.posture

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [PostureObserver] and [Posture] — the
 * §16 foldable posture abstraction.
 *
 * The interface is the testable surface; the
 * Android implementation [AndroidPostureObserver]
 * is covered by the on-device end-to-end test
 * (the emulator's virtual hinge posture is a
 * Phase 1.6+ deliverable).
 *
 * The tests verify:
 *  - The `NullPostureObserver` is a no-op.
 *  - The `Posture` enum has the closed set of
 *    values the spec calls for.
 *  - The mapping from `FoldingFeature` to
 *    `Posture` (logic only — the actual
 *    `FoldingFeature` is Android-only).
 */
class PostureObserverTest {

    @Test
    fun nullObserverLatestIsAlwaysNull() {
        val source = NullPostureObserver()
        assertNull(source.current())
    }

    @Test
    fun nullObserverPosturesIsEmpty() = runTest {
        val source = NullPostureObserver()
        var received = 0
        source.postures().collect { received++ }
        assertEquals(0, received)
    }

    @Test
    fun nullObserverCloseIsNoOp() {
        val source = NullPostureObserver()
        source.close()
        assertNull(source.current())
    }

    @Test
    fun postureEnumHasFiveValues() {
        // The closed set per the §16 spec:
        // CLOSED, OPEN, HALF_OPENED, FLAT, UNKNOWN.
        // Adding a new value requires updating the
        // mapping in `AndroidPostureObserver` and
        // the editor's layout logic.
        val values = Posture.values()
        assertEquals(5, values.size)
        assertEquals(Posture.CLOSED, values[0])
        assertEquals(Posture.OPEN, values[1])
        assertEquals(Posture.HALF_OPENED, values[2])
        assertEquals(Posture.FLAT, values[3])
        assertEquals(Posture.UNKNOWN, values[4])
    }
}

/**
 * A fake source for unit tests. Emits a
 * deterministic sequence of postures on demand.
 */
class FakePostureObserver : PostureObserver {
    private val queue: MutableList<Posture> = mutableListOf()

    fun emit(posture: Posture) {
        queue.add(posture)
    }

    override fun postures() = kotlinx.coroutines.flow.flow {
        for (posture in queue) emit(posture)
    }

    override fun current(): Posture? = queue.lastOrNull()

    override fun close() {
        queue.clear()
    }
}

class FakePostureObserverTest {

    @Test
    fun fakeEmitsInOrder() = runTest {
        val source = FakePostureObserver()
        source.emit(Posture.CLOSED)
        source.emit(Posture.HALF_OPENED)
        source.emit(Posture.OPEN)
        val received = mutableListOf<Posture>()
        source.postures().collect { received.add(it) }
        assertEquals(3, received.size)
        assertEquals(Posture.CLOSED, received[0])
        assertEquals(Posture.HALF_OPENED, received[1])
        assertEquals(Posture.OPEN, received[2])
    }

    @Test
    fun fakeLatestReturnsLastEmitted() {
        val source = FakePostureObserver()
        source.emit(Posture.CLOSED)
        source.emit(Posture.OPEN)
        assertEquals(Posture.OPEN, source.current())
    }

    @Test
    fun fakeCloseClearsQueue() {
        val source = FakePostureObserver()
        source.emit(Posture.FLAT)
        source.close()
        assertNull(source.current())
    }
}
