package com.elysium369.meet.core.trust

import com.elysium369.meet.core.identity.ActorCapability
import com.elysium369.meet.core.identity.ScopeType
import com.elysium369.meet.core.identity.TrustScope
import com.elysium369.meet.core.trust.engine.*
import org.junit.Assert.*
import org.junit.Test

class TrustCenterEngineTest {

    private val engine = TrustCenterEngine()

    @Test
    fun transparentRiskScore_ContainsReasonCodesAndEvidence() {
        val score = TransparentRiskScore(
            score = 92,
            reasonCodes = listOf("IDENTITY_VERIFIED", "NO_RECENT_DISPUTES"),
            evidenceList = listOf("COSEVI DB 2026 match", "45 trips with 5.0 rating"),
            ruleVersion = "v2.4",
        )

        assertEquals(92, score.score)
        assertTrue("Must have reason codes explaining score", score.reasonCodes.isNotEmpty())
        assertTrue("Must have concrete evidence", score.evidenceList.isNotEmpty())
        assertNotNull(score.ruleVersion)
    }

    @Test
    fun capabilityGating_ExpiredLicenseBlocksDriveCapability() {
        val validDoc = DocumentRecord(
            title = "Licencia B1",
            type = "LICENSE",
            status = ComplianceStatus.EXPIRED, // EXPIRED!
            issuedAtEpochMs = 1_000_000L,
            expiresAtEpochMs = System.currentTimeMillis() - 100_000L,
            issuer = "COSEVI",
        )

        val profile = ActorTrustProfile(
            principalId = "driver-1",
            identityVerified = true,
            phoneVerified = true,
            emailVerified = true,
            documents = listOf(validDoc),
            riskScore = TransparentRiskScore(50, listOf("LICENSE_EXPIRED"), listOf("COSEVI Expired")),
        )

        val decision = profile.canOperateCapability(ActorCapability.DRIVE_RIDE)
        assertTrue("Expired license must result in Denied decision", decision is CapabilityDecision.Denied)
        val denied = decision as CapabilityDecision.Denied
        assertEquals("LICENSE", denied.requiredDocumentType)
    }

    @Test
    fun capabilityGating_ValidDocumentsGrantCapability() {
        val validDoc = DocumentRecord(
            title = "Licencia B1",
            type = "LICENSE",
            status = ComplianceStatus.VALID,
            issuedAtEpochMs = System.currentTimeMillis() - 1_000_000L,
            expiresAtEpochMs = System.currentTimeMillis() + 10_000_000L,
            issuer = "COSEVI",
        )

        val profile = ActorTrustProfile(
            principalId = "driver-2",
            identityVerified = true,
            phoneVerified = true,
            emailVerified = true,
            documents = listOf(validDoc),
            riskScore = TransparentRiskScore(95, listOf("CLEAN_RECORD"), listOf("Active")),
        )

        val decision = profile.canOperateCapability(ActorCapability.DRIVE_RIDE)
        assertTrue("Valid license must grant capability", decision is CapabilityDecision.Granted)
    }

    @Test
    fun trustScope_CrossTenantIsolationEnforced() {
        val scope = TrustScope(
            principalId = "user-a",
            organizationId = "fleet-a",
            scopeType = ScopeType.FLEET,
            canManageTrust = false,
        )

        assertTrue("Can inspect member of own org", scope.canInspect(targetSubjectId = "driver-in-fleet-a", targetOrgId = "fleet-a"))
        assertFalse("Cannot inspect subject of different org", scope.canInspect(targetSubjectId = "driver-in-fleet-b", targetOrgId = "fleet-b"))
    }
}
