package com.elysium369.meet.core.apprenticeship

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import com.elysium369.meet.education.economic.SkillCredentialState
import org.junit.Assert.*
import org.junit.Test

class ApprenticeshipProtocolTest {

    private fun masterProfile(
        id: String = "master-1",
        credential: SkillCredentialState = SkillCredentialState.ELYSIUM_VERIFIED,
    ) = MasterProfile(
        userId = id, displayName = "Maestro $id",
        domain = UniversalServiceDomain.PLUMBING,
        yearsOfExperience = 15,
        credentialState = credential,
    )

    @Test
    fun `master must be DEMONSTRATED or higher to teach`() {
        val learned = masterProfile(credential = SkillCredentialState.LEARNED)
        assertFalse(learned.isQualifiedToTeach)
        val demonstrated = masterProfile(credential = SkillCredentialState.DEMONSTRATED)
        assertTrue(demonstrated.isQualifiedToTeach)
    }

    @Test
    fun `unqualified master cannot be registered`() {
        val engine = ApprenticeshipEngine()
        val result = engine.registerMaster(
            masterProfile(credential = SkillCredentialState.PRACTICED)
        )
        assertFalse(result)
    }

    @Test
    fun `request apprenticeship creates relationship`() {
        val engine = ApprenticeshipEngine()
        engine.registerMaster(masterProfile())
        val apprenticeship = engine.requestApprenticeship(
            "apprentice-1", "Carlos", "master-1",
            UniversalServiceDomain.PLUMBING, "PVC welding",
            listOf("Basic joints", "Pressure testing", "Full installation"),
        )
        assertNotNull(apprenticeship)
        assertEquals(ApprenticeshipStatus.REQUESTED, apprenticeship!!.status)
        assertEquals(3, apprenticeship.totalMilestones)
    }

    @Test
    fun `master accepts and activates apprenticeship`() {
        val engine = ApprenticeshipEngine()
        engine.registerMaster(masterProfile())
        val a = engine.requestApprenticeship(
            "a1", "Carlos", "master-1",
            UniversalServiceDomain.PLUMBING, "Pipes", listOf("M1"),
        )!!
        assertTrue(engine.acceptApprenticeship(a.id, "master-1"))
    }

    @Test
    fun `milestone completion tracks progress`() {
        val engine = ApprenticeshipEngine()
        engine.registerMaster(masterProfile())
        val a = engine.requestApprenticeship(
            "a1", "Carlos", "master-1",
            UniversalServiceDomain.PLUMBING, "Pipes",
            listOf("Basic", "Intermediate", "Advanced"),
        )!!
        engine.acceptApprenticeship(a.id, "master-1")
        engine.completeMilestone(a.id, "ms-0", "photo://evidence.jpg")

        val updated = engine.apprenticeshipsFor("a1").first()
        assertEquals(1, updated.completedMilestones)
        assertEquals(33, updated.progressPercent) // 1/3
    }

    @Test
    fun `graduation requires 70 percent milestones`() {
        val engine = ApprenticeshipEngine()
        engine.registerMaster(masterProfile())
        val a = engine.requestApprenticeship(
            "a1", "Carlos", "master-1",
            UniversalServiceDomain.PLUMBING, "Pipes",
            listOf("M1", "M2", "M3", "M4", "M5"),
        )!!
        engine.acceptApprenticeship(a.id, "master-1")
        // Only 2/5 = 40% — not enough
        engine.completeMilestone(a.id, "ms-0", "ev1")
        engine.completeMilestone(a.id, "ms-1", "ev2")
        val result = engine.graduate(a.id, "Good student")
        assertNull(result) // Cannot graduate at 40%
    }

    @Test
    fun `graduation succeeds at 80 percent milestones`() {
        val engine = ApprenticeshipEngine()
        engine.registerMaster(masterProfile())
        val a = engine.requestApprenticeship(
            "a1", "Carlos", "master-1",
            UniversalServiceDomain.PLUMBING, "Pipes",
            listOf("M1", "M2", "M3", "M4", "M5"),
        )!!
        engine.acceptApprenticeship(a.id, "master-1")
        // 4/5 = 80% — enough
        engine.completeMilestone(a.id, "ms-0", "ev1")
        engine.completeMilestone(a.id, "ms-1", "ev2")
        engine.completeMilestone(a.id, "ms-2", "ev3")
        engine.completeMilestone(a.id, "ms-3", "ev4")
        val result = engine.graduate(a.id, "Excellent apprentice")
        assertNotNull(result)
        assertEquals(ApprenticeshipStatus.GRADUATED, result!!.status)
        assertEquals("Excellent apprentice", result.masterEndorsement)
    }

    @Test
    fun `find masters by domain`() {
        val engine = ApprenticeshipEngine()
        engine.registerMaster(masterProfile("m1"))
        engine.registerMaster(masterProfile("m2"))
        val masters = engine.findMasters(UniversalServiceDomain.PLUMBING)
        assertEquals(2, masters.size)
    }

    @Test
    fun `master has capacity limit of 3`() {
        val master = masterProfile(credential = SkillCredentialState.ELYSIUM_VERIFIED)
        assertEquals(3, master.maxApprentices)
        assertTrue(master.hasCapacity)
    }
}
