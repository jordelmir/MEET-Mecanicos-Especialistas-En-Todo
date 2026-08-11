package com.elysium369.meet.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class RepairVerificationEngineTest {
    @Test
    fun `procedure completion without post scan is not resolution`() {
        val result = RepairVerificationEngine.evaluate(
            RepairVerificationEvidence("finding", 1L, null, false, null, null, null, null, true, emptySet()),
        )
        assertEquals(RepairVerificationState.PROCEDURE_COMPLETED, result.state)
    }

    @Test
    fun `absent finding still waits for required drive cycle`() {
        val result = RepairVerificationEngine.evaluate(
            RepairVerificationEvidence("finding", 1L, "scan", true, false, true, true, false, true, setOf("scan")),
        )
        assertEquals(RepairVerificationState.PENDING_DRIVE_CYCLE, result.state)
    }

    @Test
    fun `recurrence always wins`() {
        val result = RepairVerificationEngine.evaluate(
            RepairVerificationEvidence("finding", 1L, "scan", true, true, true, true, true, false, setOf("obs")),
        )
        assertEquals(RepairVerificationState.RECURRED, result.state)
    }
}
