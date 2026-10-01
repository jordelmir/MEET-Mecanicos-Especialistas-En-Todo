package com.elysium.nexus.core.touch

import com.elysium.nexus.core.model.TouchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [TouchEventDispatcher] — the pure-Kotlin
 * translator from `MotionEvent` semantics to engine
 * submissions.
 */
class TouchEventDispatcherTest {

    /**
     * Build a dispatcher with a recording callback.
     */
    private fun recorder(): Pair<TouchEventDispatcher, RecordingCallback> {
        val rec = RecordingCallback()
        val d = TouchEventDispatcher { id, point, _ -> rec.record(id, point) }
        return d to rec
    }

    private class RecordingCallback {
        data class Entry(val id: Int, val point: TouchPoint?)
        val log: MutableList<Entry> = mutableListOf()
        fun record(id: Int, point: TouchPoint?) {
            log.add(Entry(id, point))
        }
        fun lastIsRemovalOf(id: Int): Boolean {
            val last = log.lastOrNull() ?: return false
            return last.id == id && last.point == null
        }
        fun lastIsAddOf(id: Int): Boolean {
            val last = log.lastOrNull() ?: return false
            return last.id == id && last.point != null
        }
    }

    @Test
    fun firstDownEmitsOneAdd() {
        val (d, rec) = recorder()
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        assertEquals(1, d.activeCount())
        assertEquals(1, rec.log.size)
        assertEquals(0, rec.log[0].id)
        assertEquals(TouchPoint(0, 0.5f, 0.5f, 0.5f), rec.log[0].point)
    }

    @Test
    fun moveEmitsUpdatesForEachActivePointer() {
        val (d, rec) = recorder()
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        d.process(TouchAction.Move, listOf(PointerInfo(0, 0.7f, 0.7f, 0.5f)))
        assertEquals(1, d.activeCount())
        assertEquals(2, rec.log.size)
        assertEquals(TouchPoint(0, 0.7f, 0.7f, 0.5f), rec.log[1].point)
    }

    @Test
    fun secondPointerDownAddsIt() {
        val (d, rec) = recorder()
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        d.process(
            TouchAction.PointerDown,
            listOf(PointerInfo(1, 0.3f, 0.3f, 0.5f))
        )
        assertEquals(2, d.activeCount())
        assertEquals(listOf(0, 1), d.activeIds())
        assertEquals(2, rec.log.size)
    }

    @Test
    fun pointerUpRemovesThatPointer() {
        val (d, rec) = recorder()
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        d.process(TouchAction.PointerDown, listOf(PointerInfo(1, 0.3f, 0.3f, 0.5f)))
        rec.log.clear()
        d.process(TouchAction.PointerUp, listOf(PointerInfo(1, 0.3f, 0.3f, 0.5f)))
        assertEquals(1, d.activeCount())
        assertEquals(listOf(0), d.activeIds())
        assertEquals(1, rec.log.size)
        assertTrue(rec.lastIsRemovalOf(1))
    }

    @Test
    fun upClearsAll() {
        val (d, rec) = recorder()
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        d.process(TouchAction.PointerDown, listOf(PointerInfo(1, 0.3f, 0.3f, 0.5f)))
        rec.log.clear()
        d.process(TouchAction.Up, listOf(PointerInfo(1, 0.3f, 0.3f, 0.5f)))
        assertEquals(0, d.activeCount())
        // Both pointers are removed; the dispatcher flushes
        // leftovers as a safety net.
        assertEquals(2, rec.log.size)
        assertTrue(rec.log.all { it.point == null })
    }

    @Test
    fun cancelClearsAllAndEmitsNullForEach() {
        val (d, rec) = recorder()
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        d.process(TouchAction.PointerDown, listOf(PointerInfo(1, 0.3f, 0.3f, 0.5f)))
        rec.log.clear()
        d.process(TouchAction.Cancel, emptyList())
        assertEquals(0, d.activeCount())
        // Both pointers get a `null` callback.
        assertEquals(2, rec.log.size)
        assertTrue(rec.log.all { it.point == null })
    }

    @Test
    fun downResetsAnyPriorState() {
        // Per the dispatcher contract, a `Down` clears any
        // leftover state. This is the safety net for a missed
        // `Cancel` or a gesture that was interrupted.
        val (d, rec) = recorder()
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        d.process(TouchAction.Down, listOf(PointerInfo(7, 0.3f, 0.3f, 0.5f)))
        assertEquals(1, d.activeCount())
        assertEquals(listOf(7), d.activeIds())
        // The dispatcher does not emit a removal callback for
        // the discarded pointer; the engine is expected to
        // be neutral when a new gesture starts. The 0.5
        // callback count is the *two* adds.
        assertEquals(2, rec.log.size)
    }

    @Test
    fun explicitResetEmitsNullForEach() {
        val (d, rec) = recorder()
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        d.process(TouchAction.PointerDown, listOf(PointerInfo(1, 0.3f, 0.3f, 0.5f)))
        rec.log.clear()
        d.reset()
        assertEquals(0, d.activeCount())
        assertEquals(2, rec.log.size)
        assertTrue(rec.log.all { it.point == null })
    }

    @Test
    fun moveForUnknownPointerTreatedAsFreshDown() {
        // Defensive: if a `Move` arrives for a pointer we
        // have not seen, the dispatcher treats it as a fresh
        // PointerDown for the same id. This protects the
        // engine from a malformed event sequence.
        val (d, rec) = recorder()
        d.process(TouchAction.Move, listOf(PointerInfo(2, 0.5f, 0.5f, 0.5f)))
        assertEquals(1, d.activeCount())
        assertEquals(2, rec.log[0].id)
        assertTrue(rec.log[0].point != null)
    }

    @Test
    fun pointerCoordinatesArePreserved() {
        val (d, rec) = recorder()
        d.process(
            TouchAction.Down,
            listOf(PointerInfo(0, 0.1f, 0.2f, 0.7f))
        )
        assertEquals(TouchPoint(0, 0.1f, 0.2f, 0.7f), rec.log[0].point)
    }

    @Test
    fun outOfRangeCoordinatesAreClamped() {
        val (d, rec) = recorder()
        d.process(
            TouchAction.Down,
            listOf(PointerInfo(0, 1.5f, -0.1f, 0.5f))
        )
        // PointerInfo.toTouchPoint() clamps to [0, 1].
        assertEquals(TouchPoint(0, 1.0f, 0.0f, 0.5f), rec.log[0].point)
    }

    @Test
    fun negativePointerIdIsRejected() {
        val (d, _) = recorder()
        try {
            d.process(
                TouchAction.Down,
                listOf(PointerInfo(-1, 0.5f, 0.5f, 0.5f))
            )
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun fullLifecycleStaysConsistent() {
        val (d, rec) = recorder()
        // Down
        d.process(TouchAction.Down, listOf(PointerInfo(0, 0.5f, 0.5f, 0.5f)))
        // Move
        d.process(TouchAction.Move, listOf(PointerInfo(0, 0.6f, 0.6f, 0.5f)))
        // Second pointer down
        d.process(
            TouchAction.PointerDown,
            listOf(PointerInfo(1, 0.3f, 0.3f, 0.5f))
        )
        // Both move
        d.process(
            TouchAction.Move,
            listOf(
                PointerInfo(0, 0.7f, 0.7f, 0.5f),
                PointerInfo(1, 0.4f, 0.4f, 0.5f)
            )
        )
        // First pointer up
        d.process(TouchAction.PointerUp, listOf(PointerInfo(0, 0.7f, 0.7f, 0.5f)))
        // Second pointer up
        d.process(TouchAction.Up, listOf(PointerInfo(1, 0.4f, 0.4f, 0.5f)))
        assertEquals(0, d.activeCount())
        // 1 (down) + 1 (move) + 1 (pointerDown) + 2 (move) +
        // 1 (pointerUp, removal) + 1 (up, removal) = 7
        assertEquals(7, rec.log.size)
    }
}
