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
    fun testProviderServiceActorPermissions() {
        val authId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val technician = ServiceActor.provider(
            authUserId = authId,
            userProfileId = profileId,
            providerProfileId = providerId,
            role = ServiceRole.TECHNICIAN,
            displayName = "Taller Los Santos",
            phone = "+506 2222 3333",
        )

        assertTrue(technician.isVerifiedProvider)
        assertTrue(technician.hasPermission(ServicePermission.SUBMIT_OFFER))
        assertTrue(technician.hasPermission(ServicePermission.START_ROUTE))
        assertTrue(technician.hasPermission(ServicePermission.PERFORM_REPAIR))
        assertTrue(technician.hasPermission(ServicePermission.SUBMIT_POST_SCAN))
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
