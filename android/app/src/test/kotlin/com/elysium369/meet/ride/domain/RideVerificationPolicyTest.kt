package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideVerificationPolicyTest {

    @Test
    fun `local pilot grants immediate provisional access when explicitly enabled`() {
        val decision = RideVerificationPolicy.decide(
            localAutoApprovalEnabled = true,
            evidenceReady = true,
            nowEpochMs = 12_345L,
        )

        assertEquals("PILOT_APPROVED", decision.status)
        assertEquals(12_345L, decision.approvedAtEpochMs)
        assertEquals(true, RideVerificationPolicy.grantsAccess(decision.status))
    }

    @Test
    fun `production review remains pending when bypass is disabled`() {
        val decision = RideVerificationPolicy.decide(
            localAutoApprovalEnabled = false,
            evidenceReady = true,
            nowEpochMs = 12_345L,
        )

        assertEquals("PENDING", decision.status)
        assertNull(decision.approvedAtEpochMs)
        assertEquals(false, RideVerificationPolicy.grantsAccess(decision.status))
    }

    @Test
    fun `remote approval and local pilot approval both grant access`() {
        assertEquals(true, RideVerificationPolicy.grantsAccess("APPROVED"))
        assertEquals(true, RideVerificationPolicy.grantsAccess("PILOT_APPROVED"))
        assertEquals(false, RideVerificationPolicy.grantsAccess("REJECTED"))
        assertEquals(false, RideVerificationPolicy.grantsAccess(null))
    }

    @Test
    fun `pilot never grants access when required evidence is incomplete`() {
        val decision = RideVerificationPolicy.decide(
            localAutoApprovalEnabled = true,
            evidenceReady = false,
            nowEpochMs = 12_345L,
        )

        assertEquals("INCOMPLETE", decision.status)
        assertNull(decision.approvedAtEpochMs)
        assertEquals(false, RideVerificationPolicy.grantsAccess(decision.status))
    }
}
