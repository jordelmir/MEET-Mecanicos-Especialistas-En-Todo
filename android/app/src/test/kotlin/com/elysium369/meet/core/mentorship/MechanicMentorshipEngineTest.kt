package com.elysium369.meet.core.mentorship

import org.junit.Assert.*
import org.junit.Test

class MechanicMentorshipEngineTest {
    @Test fun `start mentorship relationship`() {
        val e = MechanicMentorshipEngine()
        val rel = e.startMentorship("master1", "Don Pedro", "app1", "Luis", MechanicSkillDomain.BRAKES)
        assertTrue(rel.isActive)
        assertEquals("Don Pedro", rel.masterName)
        assertEquals(MechanicSkillDomain.BRAKES, rel.domain)
    }
    @Test fun `assign and submit challenge`() {
        val e = MechanicMentorshipEngine()
        val rel = e.startMentorship("m1", "Pedro", "a1", "Luis", MechanicSkillDomain.ENGINE)
        val ch = e.assignChallenge(rel.relationId, "Cambiar bujías", "Sin ayuda en 30 min")!!
        assertEquals(ChallengeStatus.ASSIGNED, ch.status)
        assertTrue(e.submitChallenge(ch.challengeId, preScanId = "pre-1", postScanId = "post-1"))
    }
    @Test fun `approve challenge increments completion`() {
        val e = MechanicMentorshipEngine()
        val rel = e.startMentorship("m1", "Pedro", "a1", "Luis", MechanicSkillDomain.BRAKES)
        val ch = e.assignChallenge(rel.relationId, "Cambiar pastillas", "Test")!!
        e.submitChallenge(ch.challengeId)
        assertTrue(e.approveChallenge(ch.challengeId, "Buen trabajo"))
        val reviews = e.getPendingReviews("m1")
        assertEquals(0, reviews.size) // Already approved
    }
    @Test fun `fail challenge`() {
        val e = MechanicMentorshipEngine()
        val rel = e.startMentorship("m1", "Pedro", "a1", "Luis", MechanicSkillDomain.ELECTRICAL)
        val ch = e.assignChallenge(rel.relationId, "Test", "Test")!!
        e.submitChallenge(ch.challengeId)
        assertTrue(e.failChallenge(ch.challengeId, "Necesita más práctica"))
    }
    @Test fun `issue certification with SHA-256`() {
        val e = MechanicMentorshipEngine()
        val cert = e.issueCertification("a1", "Luis", MechanicSkillDomain.BRAKES, SkillLevel.APPRENTICE, "m1", "Don Pedro")
        assertTrue(cert.integrityHash.length == 64)
        assertTrue(cert.isActive)
        assertEquals("Frenos — Aprendiz (por Don Pedro)", cert.summary)
    }
    @Test fun `skill progress tracking`() {
        val e = MechanicMentorshipEngine()
        val rel = e.startMentorship("m1", "Pedro", "a1", "Luis", MechanicSkillDomain.DIAGNOSTICS_OBD)
        val ch = e.assignChallenge(rel.relationId, "Leer DTCs", "Usar scanner")!!
        e.submitChallenge(ch.challengeId)
        e.approveChallenge(ch.challengeId)
        val progress = e.getSkillProgress("a1", MechanicSkillDomain.DIAGNOSTICS_OBD)
        assertEquals(1, progress.challengesCompleted)
        assertTrue(progress.progressPercent > 0)
    }
    @Test fun `certifications per mechanic`() {
        val e = MechanicMentorshipEngine()
        e.issueCertification("a1", "Luis", MechanicSkillDomain.BRAKES, SkillLevel.APPRENTICE, "m1", "Pedro")
        e.issueCertification("a1", "Luis", MechanicSkillDomain.ENGINE, SkillLevel.JOURNEYMAN, "m2", "Carlos")
        assertEquals(2, e.getCertifications("a1").size)
    }
    @Test fun `skill domain labels in Spanish`() {
        assertEquals("Frenos", MechanicSkillDomain.BRAKES.displayLabel)
        assertEquals("Motor", MechanicSkillDomain.ENGINE.displayLabel)
        assertEquals("Diagnóstico OBD", MechanicSkillDomain.DIAGNOSTICS_OBD.displayLabel)
    }
    @Test fun `skill level labels in Spanish`() {
        assertEquals("Novato", SkillLevel.NOVICE.displayLabel)
        assertEquals("Maestro", SkillLevel.MASTER.displayLabel)
    }
}
