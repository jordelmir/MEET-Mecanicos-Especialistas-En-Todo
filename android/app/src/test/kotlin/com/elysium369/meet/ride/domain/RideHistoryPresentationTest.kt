package com.elysium369.meet.ride.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class RideHistoryPresentationTest {
    private fun details(status: String, distance: Double, final: Long? = null, version: Long = 1): String =
        RideHistoryPresentation.details(3300, final, "CRC", status, status, version, distance, Locale.US)

    @Test fun `accepted ride shows offer and unknown distance without inventing zero kilometers`() {
        assertEquals("Tarifa ofrecida: 3300 CRC · Distancia no capturada", details("ASSIGNED", 0.0))
    }
    @Test fun `all unavailable distance values stay unknown`() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertEquals("Tarifa ofrecida: 3300 CRC · Distancia no capturada", details("IN_PROGRESS", it))
        }
    }
    @Test fun `completed history requires final authoritative receipt`() {
        assertEquals("Total: Pendiente de validación · Distancia no capturada", details("COMPLETED", 0.0))
        assertEquals("Total: Pendiente de validación · Distancia no capturada", details("COMPLETED", 0.0, 3500, 0))
        assertEquals("Total: 3500 CRC · Distancia estimada: 12.5 km", details("COMPLETED", 12.5, 3500))
    }
    @Test fun `cancelled fare stays an offer and never implies a charge`() {
        assertEquals("Tarifa ofrecida: 3300 CRC · Distancia estimada: 2.0 km", details("CANCELLED", 2.0))
    }
}
