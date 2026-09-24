package com.elysium369.meet.safety

import com.elysium369.meet.safety.domain.ClaimState
import org.junit.Assert.*
import org.junit.Test

class SafetyClaimTransitionTest {

    @Test
    fun `All expected values exist and total count is exactly 10`() {
        val states = ClaimState.values()
        assertEquals(10, states.size)
    }

    @Test
    fun `valueOf works for each`() {
        val states = ClaimState.values()
        states.forEach { state ->
            assertEquals(state, ClaimState.valueOf(state.name))
        }
    }
}
