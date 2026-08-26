package com.elysium369.meet.ride.payment

import org.junit.Assert.*
import org.junit.Test

class RidePaymentStatusTest {

    @Test
    fun only_BANK_CONFIRMED_is_settled() {
        for (status in RidePaymentStatus.entries) {
            if (status == RidePaymentStatus.BANK_CONFIRMED) {
                assertTrue("$status should be settled", status.isSettled)
            } else {
                assertFalse("$status should NOT be settled", status.isSettled)
            }
        }
    }

    @Test
    fun SINPE_selection_is_not_proof_of_payment() {
        val sinpeSelected = RidePaymentStatus.PAYMENT_METHOD_SELECTED
        assertFalse(sinpeSelected.isSettled)
    }

    @Test
    fun USER_MARKED_SENT_is_not_proof_of_payment() {
        val userMarked = RidePaymentStatus.USER_MARKED_SENT
        assertFalse(userMarked.isSettled)
        assertEquals("Pasajero marcó como enviado", userMarked.displayLabelEs)
    }

    @Test
    fun EXTERNAL_SETTLEMENT_ATTESTED_is_not_bank_confirmed() {
        val attested = RidePaymentStatus.EXTERNAL_SETTLEMENT_ATTESTED
        assertFalse(attested.isSettled)
    }

    @Test
    fun DISPUTED_is_correctly_flagged() {
        assertTrue(RidePaymentStatus.DISPUTED.isDisputed)
        assertFalse(RidePaymentStatus.BANK_CONFIRMED.isDisputed)
    }
}
