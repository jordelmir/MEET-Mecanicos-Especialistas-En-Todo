package com.elysium369.meet.core.dispute

import org.junit.Assert.*
import org.junit.Test

class DisputeResolutionEngineTest {
    @Test fun `open dispute with hash`() {
        val e = DisputeResolutionEngine()
        val d = e.openDispute("r1", DisputeCategory.OVERCHARGED, "c1", "Ana", "m1", "Carlos", "v1", 50000, "Me cobraron de más")
        assertTrue(d.isOpen)
        assertTrue(d.integrityHash.length == 64)
    }
    @Test fun `submit evidence from both parties`() {
        val e = DisputeResolutionEngine()
        val d = e.openDispute("r1", DisputeCategory.WORK_NOT_PERFORMED, "c1", "Ana", "m1", "Carlos", "v1", description = "No hicieron el trabajo")
        e.submitEvidence(d.disputeId, DisputeParty.CUSTOMER, EvidenceType.CERTIFIED_POST_SCAN, "Post-scan", "DTC sigue activo")
        e.submitEvidence(d.disputeId, DisputeParty.MECHANIC, EvidenceType.TESTIMONY, "Testimonio", "Sí lo hicimos")
        val updated = e.getDispute(d.disputeId)!!
        assertEquals(2, updated.totalEvidence)
    }
    @Test fun `auto-arbitrate favors stronger evidence`() {
        val e = DisputeResolutionEngine()
        val d = e.openDispute("r1", DisputeCategory.WORK_NOT_PERFORMED, "c1", "Ana", "m1", "Carlos", "v1", 30000, "No arreglaron")
        e.submitEvidence(d.disputeId, DisputeParty.CUSTOMER, EvidenceType.CERTIFIED_POST_SCAN, "Post-scan", "DTC activo", referenceId = "rpt-1")
        e.submitEvidence(d.disputeId, DisputeParty.MECHANIC, EvidenceType.TESTIMONY, "Testimonio", "Lo arreglé")
        val resolution = e.autoArbitrate(d.disputeId)!!
        assertEquals(DisputeStatus.RESOLVED_CUSTOMER, e.getDispute(d.disputeId)!!.status)
        assertTrue(resolution.refundAmount > 0)
    }
    @Test fun `evidence weights match type`() {
        assertTrue(EvidenceType.CERTIFIED_PRE_SCAN.defaultWeight > EvidenceType.TESTIMONY.defaultWeight)
        assertTrue(EvidenceType.SIGNED_QUOTE.defaultWeight > EvidenceType.CHAT_HISTORY.defaultWeight)
    }
    @Test fun `escalate dispute`() {
        val e = DisputeResolutionEngine()
        val d = e.openDispute("r1", DisputeCategory.DAMAGE_CAUSED, "c1", "Ana", "m1", "Carlos", "v1", description = "Dañaron mi carro")
        assertTrue(e.escalate(d.disputeId))
        assertEquals(DisputeStatus.ESCALATED, e.getDispute(d.disputeId)!!.status)
    }
    @Test fun `dispute history per user`() {
        val e = DisputeResolutionEngine()
        e.openDispute("r1", DisputeCategory.OVERCHARGED, "c1", "Ana", "m1", "Carlos", "v1", description = "Test")
        Thread.sleep(2)
        e.openDispute("r2", DisputeCategory.MISDIAGNOSIS, "c1", "Ana", "m2", "Pedro", "v2", description = "Test2")
        assertEquals(2, e.getDisputeHistory("c1").size)
    }
}
