package com.elysium369.meet.ride.domain

import org.junit.Assert.*
import org.junit.Test

class RideAuthorityEvidenceTest {
    @Test fun cancelledLocalVersionAndMissingReceiptNeverCloseTrip() {
        assertNull(RideAuthorityEvidence.validVersion(null))
        assertNull(RideAuthorityEvidence.validVersion(0))
        assertNull(RideAuthorityEvidence.validVersion(-1))
        for(state in listOf("CANCELLED","COMPLETED","EXPIRED","VOIDED")) {
            assertFalse(RideAuthorityEvidence.isTerminalSnapshot(state,0))
            assertTrue(RideAuthorityEvidence.isTerminalSnapshot(state,1))
        }
    }
    @Test fun authoritativeActiveTripStillCannotBeDismissedAsTerminal() {
        for(state in listOf(null,"SEARCHING","ACCEPTED","ARRIVED","IN_PROGRESS","PENDING_PUBLICATION"))
            assertFalse(RideAuthorityEvidence.isTerminalSnapshot(state,7))
    }
}
