package com.elysium369.meet.core.services.kernel

import com.elysium369.meet.core.obd.DiagnosticModuleDiscoveryState
import com.elysium369.meet.core.obd.DiagnosticNamespace
import com.elysium369.meet.core.obd.DiagnosticScanMode
import com.elysium369.meet.core.obd.DiagnosticScanPlanCompiler
import com.elysium369.meet.core.obd.DiagnosticFindingKey
import com.elysium369.meet.core.obd.NetworkModule
import com.elysium369.meet.core.obd.ObdProtocol
import com.elysium369.meet.core.obd.ClearVerificationPlan
import com.elysium369.meet.core.obd.ClearVerificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ServicePlatformKernelTest {

    @Test
    fun testMoneyMinorUnitsFormattingAndArithmetic() {
        val colones = Money.ofCrc(25000L)
        assertEquals("₡25,000", colones.formatted())
        assertEquals(CurrencyCode.CRC, colones.currency)

        val dollars = Money.ofUsdCents(2550L)
        assertEquals("$25.50", dollars.formatted())

        val moreDollars = Money.ofUsdCents(1450L)
        val sum = dollars + moreDollars
        assertEquals(4000L, sum.amountMinor)
        assertEquals("$40.00", sum.formatted())

        val diff = sum - moreDollars
        assertEquals(2550L, diff.amountMinor)
    }

    @Test(expected = UnsupportedCurrencyException::class)
    fun testUnknownCurrencyFailsClosed() {
        CurrencyCode.fromString("UNKNOWN_OR_INVALID")
    }

    @Test
    fun testCustomerServiceActorPermissions() {
        val authId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val customer = ServiceActor.customer(authId, profileId, "Carlos Mendoza")

        assertTrue(customer.hasPermission(ServicePermission.CREATE_REQUEST))
        assertTrue(customer.hasPermission(ServicePermission.ACCEPT_OFFER))
        assertTrue(customer.hasPermission(ServicePermission.CONFIRM_PARTS_RECEIPT))
        assertFalse(customer.hasPermission(ServicePermission.PERFORM_REPAIR))
        assertFalse(customer.isVerifiedProvider)
    }

    @Test
    fun testProviderTrustMustMatchActorProfileTest() {
        val authId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val providerA = UUID.randomUUID()
        val providerB = UUID.randomUUID()

        // Snapshot belongs to provider B, but actor has provider A ID
        val snapshotB = ProviderTrustSnapshot(
            providerProfileId = providerB,
            verificationState = VerificationState.APPROVED,
            isActive = true,
            trustVersion = 1,
            verifiedAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = System.currentTimeMillis() + 86400000L,
            revokedAtEpochMs = null,
            verifiedByAdminId = UUID.randomUUID(),
            eligibleServiceVerticals = setOf(ServiceVertical.REPAIR),
        )

        val actorA = ServiceActor.provider(
            authUserId = authId,
            userProfileId = profileId,
            providerProfileId = providerA,
            role = ServiceRole.TECHNICIAN,
            displayName = "Taller Central",
            trustSnapshot = snapshotB,
        )

        // Binding between actor providerProfileId and trustSnapshot providerProfileId MUST match
        assertFalse(actorA.isVerifiedProvider)
        assertFalse(actorA.hasPermission(ServicePermission.PERFORM_REPAIR, ServiceVertical.REPAIR))
    }

    @Test
    fun testProviderPermissionRequiresTrustTest() {
        val authId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val providerId = UUID.randomUUID()

        val unverifiedTechnician = ServiceActor.provider(
            authUserId = authId,
            userProfileId = profileId,
            providerProfileId = providerId,
            role = ServiceRole.TECHNICIAN,
            displayName = "Taller Los Santos",
            trustSnapshot = null,
        )
        assertFalse(unverifiedTechnician.isVerifiedProvider)
        // Provider cannot execute repair operations without verified trust center authorization
        assertFalse(unverifiedTechnician.hasPermission(ServicePermission.PERFORM_REPAIR))
        assertFalse(unverifiedTechnician.hasPermission(ServicePermission.SUBMIT_POST_SCAN))
    }

    @Test
    fun testProviderVerticalAuthorizationTest() {
        val authId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val providerId = UUID.randomUUID()

        // Provider authorized ONLY for TOW
        val towSnapshot = ProviderTrustSnapshot(
            providerProfileId = providerId,
            verificationState = VerificationState.APPROVED,
            isActive = true,
            trustVersion = 1,
            verifiedAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = System.currentTimeMillis() + 86400000L,
            revokedAtEpochMs = null,
            verifiedByAdminId = UUID.randomUUID(),
            eligibleServiceVerticals = setOf(ServiceVertical.TOW),
        )

        val towOperator = ServiceActor.provider(
            authUserId = authId,
            userProfileId = profileId,
            providerProfileId = providerId,
            role = ServiceRole.TOW_OPERATOR,
            displayName = "Grúas San José",
            trustSnapshot = towSnapshot,
        )

        assertTrue(towOperator.isVerifiedProvider)
        assertTrue(towOperator.hasPermission(ServicePermission.START_ROUTE, ServiceVertical.TOW))
        // Attempting to execute in REPAIR vertical is rejected by vertical gating
        assertFalse(towOperator.hasPermission(ServicePermission.PERFORM_REPAIR, ServiceVertical.REPAIR))
    }

    @Test
    fun testTowProviderCanonicalTypeRoundTrip() {
        assertEquals(ProviderType.TOW_PROVIDER, ProviderType.fromDbValue("tow_provider"))
        assertEquals(ProviderType.TOW_PROVIDER, ProviderType.fromDbValue("TOW_TRUCK"))
        assertEquals(ProviderType.MECHANIC, ProviderType.fromDbValue("mechanic"))
        assertEquals(ProviderType.WORKSHOP, ProviderType.fromDbValue("workshop"))
        assertEquals(ProviderType.PARTS_STORE, ProviderType.fromDbValue("parts_store"))
        assertEquals(ProviderType.RIDE_DRIVER, ProviderType.fromDbValue("ride_driver"))
        assertEquals(ProviderType.SERVICE_PROVIDER, ProviderType.fromDbValue("SERVICE_PROVIDER"))
    }

    @Test
    fun testCanFdProtocolDetectionTest() {
        assertEquals(ObdProtocol.CAN_FD_11BIT, ObdProtocol.fromString("D"))
        assertEquals(ObdProtocol.CAN_FD_29BIT, ObdProtocol.fromString("E"))
        assertEquals(ObdProtocol.DOIP_ISO13400, ObdProtocol.fromString("F"))
        assertEquals(ObdProtocol.CAN_11BIT_500K, ObdProtocol.fromString("6"))
        assertEquals(ObdProtocol.CAN_29BIT_500K, ObdProtocol.fromString("7"))
    }

    @Test
    fun testClearVerifyFailClosedTest() {
        val confirmedModules = listOf(
            NetworkModule(id = "7E0", responseId = "7E8", name = "Engine", isAlive = true)
        )
        // Verification plan targets a TCM (7E1) that was not confirmed alive
        val plan = ClearVerificationPlan(
            requestedAtMs = System.currentTimeMillis(),
            targets = listOf(
                ClearVerificationTarget(
                    findingId = "f_01",
                    vehicleId = "veh_01",
                    findingKey = DiagnosticFindingKey(
                        vehicleId = "veh_01",
                        namespace = DiagnosticNamespace.SAE_OBD,
                        moduleIdentity = "7E1",
                        rawDtcIdentity = "0700",
                        displayCode = "P0700"
                    ),
                    requiredSemantics = emptySet(),
                    sourceService = "03"
                )
            ),
            preClearReport = null
        )

        val compiledTargets = DiagnosticScanPlanCompiler.compile(
            mode = DiagnosticScanMode.CLEAR_VERIFY,
            confirmedModules = confirmedModules,
            discoveryCandidates = emptyMap(),
            clearVerificationPlan = plan
        )

        // Fail-closed: Must return empty list instead of scanning unrelated ECUs
        assertTrue(compiledTargets.isEmpty())
    }
}
