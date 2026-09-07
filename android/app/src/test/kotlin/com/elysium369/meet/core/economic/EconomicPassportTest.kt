package com.elysium369.meet.core.economic

import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import com.elysium369.meet.education.economic.SkillCredentialState
import org.junit.Assert.*
import org.junit.Test

class EconomicPassportTest {

    private fun emptyPassport() = EconomicPassport(
        ownerId = "provider-1",
        displayName = "Carlos Rodríguez",
    )

    // ─── Reputation From Verified Work (§46) ───

    @Test
    fun `reputation is domain-specific not aggregated`() {
        var passport = emptyPassport()

        // Record plumbing jobs
        passport = EconomicPassportEngine.recordOutcome(
            passport, "job-1", UniversalServiceDomain.PLUMBING,
            "Reparación fuga cocina", true, listOf("photo://after.jpg"), 45,
        )
        passport = EconomicPassportEngine.recordOutcome(
            passport, "job-2", UniversalServiceDomain.PLUMBING,
            "Instalación grifo baño", true, listOf("photo://grifo.jpg"), 30,
        )

        // Record auto job
        passport = EconomicPassportEngine.recordOutcome(
            passport, "job-3", UniversalServiceDomain.AUTO_MECHANICAL,
            "Cambio pastillas freno", true, listOf("scan://post_scan.pdf"), 60,
        )

        val plumbingScore = passport.reputationFor(UniversalServiceDomain.PLUMBING)
        val autoScore = passport.reputationFor(UniversalServiceDomain.AUTO_MECHANICAL)

        assertNotNull(plumbingScore)
        assertNotNull(autoScore)
        // Different domains, independent scores
        assertEquals(2, plumbingScore!!.completedJobs)
        assertEquals(1, autoScore!!.completedJobs)
    }

    @Test
    fun `unverified job does not count as verified outcome`() {
        var passport = emptyPassport()

        // No customer confirmation = not verified
        passport = EconomicPassportEngine.recordOutcome(
            passport, "job-1", UniversalServiceDomain.PLUMBING,
            "Trabajo sin confirmación", false, emptyList(), 30,
        )

        val score = passport.reputationFor(UniversalServiceDomain.PLUMBING)!!
        assertEquals(1, score.completedJobs)
        assertEquals(0, score.verifiedOutcomes)
        assertEquals(0.0, passport.overallVerifiedRate, 0.001)
    }

    @Test
    fun `verified requires customer confirmation AND evidence`() {
        var passport = emptyPassport()

        // Customer confirmed but no evidence = NOT verified
        passport = EconomicPassportEngine.recordOutcome(
            passport, "job-1", UniversalServiceDomain.ELECTRICAL,
            "Install", true, emptyList(), 20,
        )
        assertEquals(0, passport.reputationFor(UniversalServiceDomain.ELECTRICAL)!!.verifiedOutcomes)

        // Both confirmed AND evidence = verified
        passport = EconomicPassportEngine.recordOutcome(
            passport, "job-2", UniversalServiceDomain.ELECTRICAL,
            "Cableado", true, listOf("photo://wiring.jpg"), 40,
        )
        assertEquals(1, passport.reputationFor(UniversalServiceDomain.ELECTRICAL)!!.verifiedOutcomes)
    }

    // ─── Verified Skill Marketplace Visibility ───

    @Test
    fun `only DEMONSTRATED or higher skills are marketplace visible`() {
        val learned = VerifiedSkill(
            skillId = "s1", name = "PVC Assembly", domain = UniversalServiceDomain.PLUMBING,
            credentialState = SkillCredentialState.LEARNED,
        )
        val practiced = VerifiedSkill(
            skillId = "s2", name = "Valve Repair", domain = UniversalServiceDomain.PLUMBING,
            credentialState = SkillCredentialState.PRACTICED,
        )
        val demonstrated = VerifiedSkill(
            skillId = "s3", name = "Drain Clearing", domain = UniversalServiceDomain.PLUMBING,
            credentialState = SkillCredentialState.DEMONSTRATED,
        )
        val verified = VerifiedSkill(
            skillId = "s4", name = "Full Plumbing", domain = UniversalServiceDomain.PLUMBING,
            credentialState = SkillCredentialState.ELYSIUM_VERIFIED,
        )

        assertFalse(learned.isMarketplaceVisible)
        assertFalse(practiced.isMarketplaceVisible)
        assertTrue(demonstrated.isMarketplaceVisible)
        assertTrue(verified.isMarketplaceVisible)
    }

    // ─── Domain Score ───

    @Test
    fun `domain score requires minimum 3 jobs for track record`() {
        val score = DomainReputationScore(
            domain = UniversalServiceDomain.PLUMBING,
            completedJobs = 2,
            verifiedOutcomes = 2,
        )
        assertFalse(score.hasTrackRecord)

        val sufficient = score.copy(completedJobs = 3, verifiedOutcomes = 3)
        assertTrue(sufficient.hasTrackRecord)
    }

    @Test
    fun `score is weighted correctly`() {
        val score = DomainReputationScore(
            domain = UniversalServiceDomain.AUTO_MECHANICAL,
            completedJobs = 10,
            verifiedOutcomes = 9,       // 90% → ×0.40 = 0.36
            repeatCustomers = 5,         // 50% → ×0.25 = 0.125
            exceededExpectations = 3,    // 30% → ×0.20 = 0.06
            disputes = 0,               // 100% dispute-free → ×0.15 = 0.15
        )

        // Expected: ~0.695
        assertTrue(score.score > 0.5)
        assertTrue(score.score < 1.0)
    }

    // ─── External Credential ───

    @Test
    fun `expired credential is not verified`() {
        val cred = ExternalCredential(
            name = "Certificado INA Fontanería",
            issuedBy = "INA Costa Rica",
            domain = UniversalServiceDomain.PLUMBING,
            verifiedByPlatform = true,
            expiresAtEpochMs = 1_000, // expired
        )

        assertTrue(cred.isExpired)
        assertFalse(cred.isVerified)
    }

    @Test
    fun `non-platform-verified credential is not verified`() {
        val cred = ExternalCredential(
            name = "Self-claimed certification",
            issuedBy = "Self",
            domain = UniversalServiceDomain.ELECTRICAL,
            verifiedByPlatform = false,
        )

        assertFalse(cred.isVerified)
    }

    // ─── Passport History ───

    @Test
    fun `total verified jobs counts correctly`() {
        var passport = emptyPassport()
        passport = EconomicPassportEngine.recordOutcome(
            passport, "j1", UniversalServiceDomain.PLUMBING,
            "Job 1", true, listOf("e1"), 30,
        )
        passport = EconomicPassportEngine.recordOutcome(
            passport, "j2", UniversalServiceDomain.PLUMBING,
            "Job 2", false, emptyList(), 20,
        )
        passport = EconomicPassportEngine.recordOutcome(
            passport, "j3", UniversalServiceDomain.PLUMBING,
            "Job 3", true, listOf("e3"), 25,
        )

        assertEquals(3, passport.workHistory.size)
        assertEquals(2, passport.totalVerifiedJobs)
    }
}
