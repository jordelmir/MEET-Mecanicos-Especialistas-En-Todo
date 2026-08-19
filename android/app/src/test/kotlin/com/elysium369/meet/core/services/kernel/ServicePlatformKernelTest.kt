package com.elysium369.meet.core.services.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ServicePlatformKernelTest {

    @Test
    fun testMoneyMinorUnitsFormattingAndArithmetic() {
        val colones = Money.fromColones(25000L)
        assertEquals("₡25,000", colones.formattedString)
        assertEquals(CurrencyCode.CRC, colones.currency)

        val dollars = Money.fromCents(2550L, CurrencyCode.USD)
        assertEquals("$25.50", dollars.formattedString)

        val moreDollars = Money.fromCents(1450L, CurrencyCode.USD)
        val sum = dollars + moreDollars
        assertEquals(4000L, sum.amountMinor)
        assertEquals("$40.00", sum.formattedString)

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
        val customer = ServiceActor.customer(authId, profileId, "Carlos Mendoza", "+506 8888 1234")

        assertTrue(customer.hasPermission(ServicePermission.CREATE_REQUEST))
        assertTrue(customer.hasPermission(ServicePermission.ACCEPT_OFFER))
        assertTrue(customer.hasPermission(ServicePermission.CONFIRM_PARTS_RECEIPT))
        assertFalse(customer.hasPermission(ServicePermission.PERFORM_REPAIR))
        assertFalse(customer.isVerifiedProvider)
    }

    @Test
    fun testProviderRoleCannotImplyVerificationWithoutTrustSnapshot() {
        val authId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val providerId = UUID.randomUUID()

        // Unverified provider without Trust Center snapshot
        val unverifiedTechnician = ServiceActor.provider(
            authUserId = authId,
            userProfileId = profileId,
            providerProfileId = providerId,
            role = ServiceRole.TECHNICIAN,
            displayName = "Taller Los Santos",
            phone = "+506 2222 3333",
            trustSnapshot = null,
        )
        assertFalse(unverifiedTechnician.isVerifiedProvider)

        // Verified provider with valid snapshot
        val snapshot = ProviderTrustSnapshot(
            providerProfileId = providerId,
            verificationState = VerificationState.APPROVED,
            isActive = true,
            trustVersion = 1L,
            verifiedAtEpochMs = System.currentTimeMillis(),
            expiresAtEpochMs = System.currentTimeMillis() + 86400000L,
            revokedAtEpochMs = null,
            eligibleServiceVerticals = setOf(ServiceVertical.MOBILE_MECHANIC, ServiceVertical.WORKSHOP),
        )
        val verifiedTechnician = ServiceActor.provider(
            authUserId = authId,
            userProfileId = profileId,
            providerProfileId = providerId,
            role = ServiceRole.TECHNICIAN,
            displayName = "Taller Los Santos",
            phone = "+506 2222 3333",
            trustSnapshot = snapshot,
        )
        assertTrue(verifiedTechnician.isVerifiedProvider)
        assertTrue(verifiedTechnician.hasPermission(ServicePermission.SUBMIT_OFFER))
        assertTrue(verifiedTechnician.hasPermission(ServicePermission.START_ROUTE))
        assertTrue(verifiedTechnician.hasPermission(ServicePermission.PERFORM_REPAIR))
        assertTrue(verifiedTechnician.hasPermission(ServicePermission.SUBMIT_POST_SCAN))
    }

    @Test
    fun testTowProviderCanonicalTypeRoundTrip() {
        assertEquals(ProviderType.TOW_PROVIDER, ProviderType.fromDbValue("tow_provider"))
        assertEquals(ProviderType.MECHANIC, ProviderType.fromDbValue("mechanic"))
        assertEquals(ProviderType.WORKSHOP, ProviderType.fromDbValue("workshop"))
        assertEquals(ProviderType.PARTS_STORE, ProviderType.fromDbValue("parts_store"))
        assertEquals(ProviderType.RIDE_DRIVER, ProviderType.fromDbValue("ride_driver"))
    }

    @Test
    fun testServiceCommandEnvelopeIdempotency() {
        val aggId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val envelope = ServiceCommandEnvelope(
            aggregateId = aggId,
            aggregateType = "REPAIR_WORK_ORDER",
            commandType = "ACCEPT_OFFER",
            actorId = actorId,
            expectedVersion = 1,
            payloadJson = "{\"offerId\":\"123\"}",
        )

        assertTrue(envelope.idempotencyKey.isNotBlank())
        assertEquals(1, envelope.expectedVersion)
        assertEquals("REPAIR_WORK_ORDER", envelope.aggregateType)
    }
}
