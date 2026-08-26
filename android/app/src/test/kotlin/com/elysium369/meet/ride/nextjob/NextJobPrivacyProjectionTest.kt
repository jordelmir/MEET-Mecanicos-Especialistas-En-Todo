package com.elysium369.meet.ride.nextjob

import org.junit.Assert.*
import org.junit.Test

class NextJobPrivacyProjectionTest {

    @Test
    fun formats_passenger_banner_without_leaking_previous_passenger_data() {
        val projection = NextJobPrivacyProjection(
            isChainedService = true,
            statusRaw = "RESERVED",
            availableInSeconds = 180, // 3 min
            pickupEtaSeconds = 420, // 7 min
        )

        assertEquals(
            "Conductor finalizando servicio anterior · Disponible en ~3 min (Llegada: ~7 min)",
            projection.formattedPassengerBanner
        )
    }

    @Test
    fun empty_banner_when_not_a_chained_service() {
        val directRide = NextJobPrivacyProjection(
            isChainedService = false,
        )
        assertEquals("", directRide.formattedPassengerBanner)
    }
}
