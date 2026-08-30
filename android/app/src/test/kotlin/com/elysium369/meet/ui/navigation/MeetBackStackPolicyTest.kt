package com.elysium369.meet.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MeetBackStackPolicyTest {
    @Test
    fun deepHistoryPopsOneScreenAtATime() {
        assertEquals(
            MeetBackStackPolicy.Action.POP_ONE,
            MeetBackStackPolicy.action("repair/P0230", hasPreviousEntry = true),
        )
    }

    @Test
    fun orphanDeepLinkFallsBackToHome() {
        assertEquals(
            MeetBackStackPolicy.Action.NAVIGATE_HOME,
            MeetBackStackPolicy.action("inspection_session/vehicle", hasPreviousEntry = false),
        )
    }

    @Test
    fun homeIsNeverDuplicatedByBackFallback() {
        assertEquals(
            MeetBackStackPolicy.Action.STAY_HOME,
            MeetBackStackPolicy.action(MeetDestinations.HOME, hasPreviousEntry = true),
        )
    }
}
