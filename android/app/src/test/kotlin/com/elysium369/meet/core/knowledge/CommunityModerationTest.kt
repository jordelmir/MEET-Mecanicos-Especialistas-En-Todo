package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityModerationTest {

    private val mod = CommunityModeration()

    private fun baseCase(
        dtcCode: String = "P0230",
        symptoms: String = "no start",
        confirmedCause: String = "fuel pump relay",
        repairDone: String = "replaced relay",
        licenseConsent: Boolean = true,
        evidence: String = "voltage at pump < 1V"
    ) = CommunityCase(
        id = "case_1",
        country = "CR",
        vehicleMake = "Hyundai",
        vehicleModel = "Accent",
        vehicleYear = 2005,
        dtcCode = dtcCode,
        symptoms = symptoms,
        confirmedCause = confirmedCause,
        repairDone = repairDone,
        evidence = evidence,
        resolved = true,
        licenseConsent = licenseConsent,
        submittedBy = "user_42",
        submittedAt = 0L
    )

    @Test
    fun `valid case is accepted`() {
        val r = mod.moderate(baseCase())
        assertTrue("must accept, got: ${r.reason}", r.accepted)
        assertEquals(CommunityCaseStatus.AI_REVIEWED, r.suggestedStatus)
    }

    @Test
    fun `missing fields rejected`() {
        val r = mod.moderate(baseCase(dtcCode = ""))
        assertFalse(r.accepted)
        assertEquals(CommunityCaseStatus.NEEDS_MORE_EVIDENCE, r.suggestedStatus)
    }

    @Test
    fun `missing license consent rejected`() {
        val r = mod.moderate(baseCase(licenseConsent = false))
        assertFalse(r.accepted)
        assertTrue(r.reason.contains("consentimiento"))
    }

    @Test
    fun `dangerous advice rejected`() {
        val r = mod.moderate(baseCase(repairDone = "just replace the pump, skip the test"))
        assertFalse(r.accepted)
        assertEquals(CommunityCaseStatus.REJECTED, r.suggestedStatus)
    }

    @Test
    fun `VIN in plain text rejected`() {
        val r = mod.moderate(baseCase(evidence = "My VIN is 1HGCM82633A123456 was bad"))
        assertFalse(r.accepted)
    }

    @Test
    fun `reputation trust level rises with verified cases`() {
        val verified = (1..20).map { i ->
            baseCase().copy(
                id = "c$i",
                status = CommunityCaseStatus.MECHANIC_VERIFIED
            )
        }
        val rep = mod.computeReputation(verified)
        assertEquals(20, rep.verifiedCases)
        assertTrue(rep.trustLevel().ordinal >= TrustLevel.MEDIUM.ordinal)
    }

    @Test
    fun `empty cases returns new reputation`() {
        val rep = mod.computeReputation(emptyList())
        assertEquals(TrustLevel.NEW, rep.trustLevel())
    }

    @Test
    fun `reputation high with many verified cases and high confidence`() {
        val cases = (1..60).map { i ->
            baseCase().copy(
                id = "c$i",
                status = CommunityCaseStatus.MECHANIC_VERIFIED,
                resolved = true
            )
        }
        val rep = mod.computeReputation(cases)
        assertEquals(TrustLevel.HIGH, rep.trustLevel())
    }
}
