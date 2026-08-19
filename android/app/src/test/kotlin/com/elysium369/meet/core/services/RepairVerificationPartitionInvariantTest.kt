package com.elysium369.meet.core.services

import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.core.services.repair.RepairAction
import com.elysium369.meet.core.services.repair.RepairState
import com.elysium369.meet.core.services.repair.RepairStateEngine
import com.elysium369.meet.core.services.repair.RepairVerificationBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RepairVerificationPartitionInvariantTest {

    @Test
    fun `bundle rejects non-disjoint cleared and remaining findings`() {
        assertThrows(IllegalArgumentException::class.java) {
            RepairVerificationBundle(
                workOrderId = UUID.randomUUID(),
                vehicleId = "veh_123",
                vehicleBindingId = "bind_123",
                preScanReportHash = "hash_pre",
                postScanReportHash = "hash_post",
                requiredFindingIds = setOf("P0300", "P0171"),
                clearedFindingIds = setOf("P0300"),
                remainingFindingIds = setOf("P0300", "P0171"), // P0300 is in both
            )
        }
    }

    @Test
    fun `bundle rejects incomplete partition missing required findings`() {
        assertThrows(IllegalArgumentException::class.java) {
            RepairVerificationBundle(
                workOrderId = UUID.randomUUID(),
                vehicleId = "veh_123",
                vehicleBindingId = "bind_123",
                preScanReportHash = "hash_pre",
                postScanReportHash = "hash_post",
                requiredFindingIds = setOf("P0300", "P0171"),
                clearedFindingIds = setOf("P0300"),
                remainingFindingIds = emptySet(), // P0171 vanished
            )
        }
    }

    @Test
    fun `bundle clean pass requires zero remaining findings and all required cleared and all monitors passed`() {
        val cleanPassBundle = RepairVerificationBundle(
            workOrderId = UUID.randomUUID(),
            vehicleId = "veh_123",
            vehicleBindingId = "bind_123",
            preScanReportHash = "hash_pre",
            postScanReportHash = "hash_post",
            requiredFindingIds = setOf("P0300", "P0171"),
            clearedFindingIds = setOf("P0300", "P0171"),
            remainingFindingIds = emptySet(),
            allMonitorsPassed = true,
        )
        assertTrue(cleanPassBundle.isCleanPass)

        val nextState = RepairStateEngine.getNextState(
            fromState = RepairState.VALIDATION_PENDING,
            action = RepairAction.SubmitPostScanValidation(cleanPassBundle),
            actorRole = ServiceRole.TECHNICIAN,
        )
        assertEquals(RepairState.VALIDATION_PASSED, nextState)
    }

    @Test
    fun `bundle with unpassed monitors fails validation`() {
        val monitorFailedBundle = RepairVerificationBundle(
            workOrderId = UUID.randomUUID(),
            vehicleId = "veh_123",
            vehicleBindingId = "bind_123",
            preScanReportHash = "hash_pre",
            postScanReportHash = "hash_post",
            requiredFindingIds = setOf("P0300"),
            clearedFindingIds = setOf("P0300"),
            remainingFindingIds = emptySet(),
            allMonitorsPassed = false,
        )
        assertFalse(monitorFailedBundle.isCleanPass)

        val nextState = RepairStateEngine.getNextState(
            fromState = RepairState.VALIDATION_PENDING,
            action = RepairAction.SubmitPostScanValidation(monitorFailedBundle),
            actorRole = ServiceRole.TECHNICIAN,
        )
        assertEquals(RepairState.VALIDATION_FAILED, nextState)
    }

    @Test
    fun `unknown repair state fails closed and yields UNKNOWN enum`() {
        val state = RepairState.fromDbValue("arbitrary_unknown_state_from_future")
        assertEquals(RepairState.UNKNOWN, state)
        assertFalse(state.isActiveWork)
        assertFalse(state.isTerminal)

        val nextState = RepairStateEngine.getNextState(
            fromState = state,
            action = RepairAction.StartInspection,
            actorRole = ServiceRole.TECHNICIAN,
        )
        assertNull(nextState)
    }
}
