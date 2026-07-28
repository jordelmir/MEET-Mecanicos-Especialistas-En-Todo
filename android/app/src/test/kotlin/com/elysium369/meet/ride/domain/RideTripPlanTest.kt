package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTripPlanTest {
    @Test
    fun `stop order is rebuilt after removal`() {
        val normalized = RideTripPlanPolicy.normalize(
            listOf(
                RideStopSnapshot(4, "A", 9.9, -84.1),
                RideStopSnapshot(8, "B", 9.8, -84.0),
            ),
        )

        assertEquals(listOf(1, 2), normalized.map { it.order })
    }

    @Test
    fun `unresolved stop prevents dispatch`() {
        assertFalse(
            RideTripPlanPolicy.canDispatch(
                destinationResolved = true,
                stops = listOf(RideStopSnapshot(1, "San José")),
            ),
        )
        assertTrue(RideTripPlanPolicy.canDispatch(destinationResolved = true, stops = emptyList()))
    }
}
