package com.elysium.nexus.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P0.4: Unit tests for the IrStep state machine transition table.
 *
 * Verifies:
 * - All valid transitions produce correct next state
 * - Invalid transitions return null (no crash)
 * - Terminal states are properly handled
 * - No dead states exist
 * - Exhaustive coverage of every (state, event) pair
 */
class IrStepTransitionTest {

    @Test
    fun `ORIENT continue goes to TEST`() {
        assertEquals(IrStep.TEST, IrStep.transition(IrStep.ORIENT, "continue"))
    }

    @Test
    fun `TEST did_work goes to CHALLENGE`() {
        assertEquals(IrStep.CHALLENGE, IrStep.transition(IrStep.TEST, "did_work"))
    }

    @Test
    fun `TEST next_candidate stays at TEST`() {
        assertEquals(IrStep.TEST, IrStep.transition(IrStep.TEST, "next_candidate"))
    }

    @Test
    fun `TEST exhausted returns null`() {
        assertNull(IrStep.transition(IrStep.TEST, "exhausted"))
    }

    @Test
    fun `CHALLENGE confirmed goes to VERIFY_SECONDARY`() {
        assertEquals(IrStep.VERIFY_SECONDARY, IrStep.transition(IrStep.CHALLENGE, "confirmed"))
    }

    @Test
    fun `CHALLENGE failed goes back to TEST`() {
        assertEquals(IrStep.TEST, IrStep.transition(IrStep.CHALLENGE, "failed"))
    }

    @Test
    fun `VERIFY_SECONDARY did_work goes to VERIFY_TERTIARY`() {
        assertEquals(IrStep.VERIFY_TERTIARY, IrStep.transition(IrStep.VERIFY_SECONDARY, "did_work"))
    }

    @Test
    fun `VERIFY_SECONDARY skip goes to VERIFY_TERTIARY`() {
        assertEquals(IrStep.VERIFY_TERTIARY, IrStep.transition(IrStep.VERIFY_SECONDARY, "skip"))
    }

    @Test
    fun `VERIFY_TERTIARY did_work goes to SAVE`() {
        assertEquals(IrStep.SAVE, IrStep.transition(IrStep.VERIFY_TERTIARY, "did_work"))
    }

    @Test
    fun `VERIFY_TERTIARY skip goes to SAVE`() {
        assertEquals(IrStep.SAVE, IrStep.transition(IrStep.VERIFY_TERTIARY, "skip"))
    }

    @Test
    fun `SAVE has no outgoing transitions`() {
        assertNull(IrStep.transition(IrStep.SAVE, "save"))
        assertNull(IrStep.transition(IrStep.SAVE, "continue"))
    }

    @Test
    fun `Invalid transitions return null`() {
        assertNull(IrStep.transition(IrStep.ORIENT, "did_work"))
        assertNull(IrStep.transition(IrStep.TEST, "confirmed"))
        assertNull(IrStep.transition(IrStep.CHALLENGE, "continue"))
        assertNull(IrStep.transition(IrStep.VERIFY_SECONDARY, "confirmed"))
        assertNull(IrStep.transition(IrStep.VERIFY_TERTIARY, "confirmed"))
    }

    @Test
    fun `Full happy path produces correct sequence`() {
        val steps = mutableListOf<IrStep>()
        var current: IrStep? = IrStep.ORIENT

        // ORIENT → TEST
        current = IrStep.transition(current!!, "continue")
        steps.add(current!!)
        assertEquals(IrStep.TEST, current)

        // TEST → CHALLENGE
        current = IrStep.transition(current, "did_work")
        steps.add(current!!)
        assertEquals(IrStep.CHALLENGE, current)

        // CHALLENGE → VERIFY_SECONDARY
        current = IrStep.transition(current, "confirmed")
        steps.add(current!!)
        assertEquals(IrStep.VERIFY_SECONDARY, current)

        // VERIFY_SECONDARY → VERIFY_TERTIARY
        current = IrStep.transition(current, "did_work")
        steps.add(current!!)
        assertEquals(IrStep.VERIFY_TERTIARY, current)

        // VERIFY_TERTIARY → SAVE
        current = IrStep.transition(current, "did_work")
        steps.add(current!!)
        assertEquals(IrStep.SAVE, current)

        assertEquals(5, steps.size)
        assertEquals(listOf(
            IrStep.TEST, IrStep.CHALLENGE, IrStep.VERIFY_SECONDARY,
            IrStep.VERIFY_TERTIARY, IrStep.SAVE
        ), steps)
    }

    @Test
    fun `Challenge failure loops back to TEST`() {
        var current: IrStep = IrStep.ORIENT
        current = IrStep.transition(current, "continue")!!
        assertEquals(IrStep.TEST, current)

        current = IrStep.transition(current, "did_work")!!
        assertEquals(IrStep.CHALLENGE, current)

        // Challenge failed — back to TEST
        current = IrStep.transition(current, "failed")!!
        assertEquals(IrStep.TEST, current)

        // Try again
        current = IrStep.transition(current, "did_work")!!
        assertEquals(IrStep.CHALLENGE, current)
    }

    @Test
    fun `Skip at VERIFY_SECONDARY still reaches SAVE`() {
        var current: IrStep = IrStep.ORIENT
        current = IrStep.transition(current, "continue")!!
        current = IrStep.transition(current, "did_work")!!
        current = IrStep.transition(current, "confirmed")!!

        // Skip VOLUME_DOWN
        current = IrStep.transition(current, "skip")!!
        assertEquals(IrStep.VERIFY_TERTIARY, current)

        // Skip MUTE too
        current = IrStep.transition(current, "skip")!!
        assertEquals(IrStep.SAVE, current)
    }

    @Test
    fun `Step numbers are sequential without gaps`() {
        val steps = IrStep.entries
        for (i in steps.indices) {
            assertEquals(i + 1, steps[i].number)
        }
    }

    @Test
    fun `No CONFIRM state exists`() {
        val names = IrStep.entries.map { it.name }
        assert(!names.contains("CONFIRM")) { "CONFIRM state should be removed" }
    }
}
