package com.elysium369.meet.core.warranty

import org.junit.Assert.*
import org.junit.Test

class WarrantyEngineTest {
    @Test fun `create warranty with hash`() {
        val e = WarrantyEngine()
        val w = e.createWarranty("v1", "m1", "Carlos", "Cambio de frenos", coveredParts = listOf("Pastillas"))
        assertTrue(w.isActive)
        assertTrue(w.integrityHash.length == 64)
        assertTrue(w.remainingDays > 0)
    }
    @Test fun `submit claim on active warranty`() {
        val e = WarrantyEngine()
        val w = e.createWarranty("v1", "m1", "Carlos", "Frenos")
        val claim = e.submitClaim(w.warrantyId, "Frenos siguen sonando", ClaimCategory.SAME_ISSUE_RETURNED,
            ClaimEvidence(dtcCodes = listOf("C0035")))
        assertNotNull(claim)
        assertEquals(WarrantyStatus.CLAIMED, e.getWarranty(w.warrantyId)!!.status)
    }
    @Test fun `approve claim`() {
        val e = WarrantyEngine()
        val w = e.createWarranty("v1", "m1", "Carlos", "Frenos")
        val claim = e.submitClaim(w.warrantyId, "Issue", ClaimCategory.WORKMANSHIP_ISSUE, ClaimEvidence())!!
        assertTrue(e.approveClaim(w.warrantyId, claim.claimId, "Se reprogramará"))
        assertEquals(WarrantyStatus.CLAIM_APPROVED, e.getWarranty(w.warrantyId)!!.status)
    }
    @Test fun `deny claim`() {
        val e = WarrantyEngine()
        val w = e.createWarranty("v1", "m1", "Carlos", "Motor")
        val claim = e.submitClaim(w.warrantyId, "Issue", ClaimCategory.PART_DEFECTIVE, ClaimEvidence())!!
        assertTrue(e.denyClaim(w.warrantyId, claim.claimId, "Uso indebido"))
        assertEquals(WarrantyStatus.CLAIM_DENIED, e.getWarranty(w.warrantyId)!!.status)
    }
    @Test fun `void warranty`() {
        val e = WarrantyEngine()
        val w = e.createWarranty("v1", "m1", "Carlos", "Motor")
        assertTrue(e.voidWarranty(w.warrantyId, VoidReason.TAMPERING))
        assertEquals(WarrantyStatus.VOIDED, e.getWarranty(w.warrantyId)!!.status)
    }
    @Test fun `expiring warranties detected`() {
        val e = WarrantyEngine()
        e.createWarranty("v1", "m1", "Carlos", "Frenos", durationDays = 10)
        val expiring = e.getExpiringWarranties("v1", withinDays = 30)
        assertEquals(1, expiring.size)
    }
    @Test fun `warranty type labels in Spanish`() {
        assertEquals("Garantía completa", WarrantyType.FULL_REPAIR.displayLabel)
        assertEquals("Garantía de mano de obra", WarrantyType.LABOR.displayLabel)
    }
}
