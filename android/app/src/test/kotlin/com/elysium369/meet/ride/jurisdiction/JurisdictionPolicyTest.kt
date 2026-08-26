package com.elysium369.meet.ride.jurisdiction

import org.junit.Assert.*
import org.junit.Test

class JurisdictionPolicyTest {

    @Test
    fun creates_frozen_trip_legal_snapshot_with_audit_summary() {
        val snapshot = TripLegalSnapshot(
            tripId = "trip-123",
            jurisdictionPolicyId = "CR-SJO-V1",
            jurisdictionPolicyVersion = 1,
            pricingPolicyVersion = 2,
            driverEligibilityPolicyVersion = 1,
        )

        assertEquals("trip-123", snapshot.tripId)
        assertTrue(snapshot.auditSummary.contains("CR-SJO-V1"))
        assertTrue(snapshot.auditSummary.contains("v1"))
        assertTrue(snapshot.auditSummary.contains("tarifa v2"))
    }
}
