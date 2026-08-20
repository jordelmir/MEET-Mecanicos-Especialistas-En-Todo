package com.elysium369.meet.ui.screens.home.adaptive

import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.navigation.MeetDestinations
import org.junit.Assert.*
import org.junit.Test

class HomeActionEngineTest {

    @Test
    fun testNoVehicleTriggersGarageAction() {
        val actions = HomeActionEngine.derivePrioritizedActions(
            hasVehicle = false,
            vehicleId = null,
            obdState = ObdState.DISCONNECTED,
            activeDtcs = emptyList(),
            healthScore = 0,
            monitorsReady = 0,
            monitorsTotal = 0
        )

        assertEquals(1, actions.size)
        assertEquals("ACT_NO_VEHICLE", actions.first().id)
        assertEquals(MeetDestinations.GARAGE, actions.first().destination)
        assertEquals(HomeActionPriority.CRITICAL, actions.first().priority)
    }

    @Test
    fun testActiveDtcsDeduplicatedIntoSingleCriticalAction() {
        val actions = HomeActionEngine.derivePrioritizedActions(
            hasVehicle = true,
            vehicleId = "V-001",
            obdState = ObdState.CONNECTED,
            activeDtcs = listOf("P0301", "P0230", "P0420"),
            healthScore = 75,
            monitorsReady = 6,
            monitorsTotal = 8
        )

        val dtcAction = actions.find { it.id == "ACT_DTC_FAULT" }
        assertNotNull(dtcAction)
        assertEquals(HomeActionPriority.CRITICAL, dtcAction?.priority)
        assertEquals(MeetDestinations.DTCS, dtcAction?.destination)
        assertTrue(dtcAction?.title?.contains("3 Códigos de Falla") == true)
        assertEquals(3, dtcAction?.evidenceRefs?.size)
    }

    @Test
    fun testDisconnectedObdTriggersConnectionAction() {
        val actions = HomeActionEngine.derivePrioritizedActions(
            hasVehicle = true,
            vehicleId = "V-001",
            obdState = ObdState.DISCONNECTED,
            activeDtcs = emptyList(),
            healthScore = 95,
            monitorsReady = 8,
            monitorsTotal = 8
        )

        val connectAction = actions.find { it.id == "ACT_CONNECT_OBD" }
        assertNotNull(connectAction)
        assertEquals(MeetDestinations.SCANNER, connectAction?.destination)
    }

    @Test
    fun testNominalStateProvidesAllClearAction() {
        val actions = HomeActionEngine.derivePrioritizedActions(
            hasVehicle = true,
            vehicleId = "V-001",
            obdState = ObdState.CONNECTED,
            activeDtcs = emptyList(),
            healthScore = 98,
            monitorsReady = 8,
            monitorsTotal = 8
        )

        assertEquals(1, actions.size)
        assertEquals("ACT_ALL_CLEAR", actions.first().id)
        assertEquals(HomeActionPriority.LOW, actions.first().priority)
    }
}
