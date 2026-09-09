package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RideFinalFarePresentationTest {
    @Test fun `missing final receipt never presents offered fare as total`() {
        val result = RideFinalFarePresentation.from(5000, null, "CRC", "COMPLETED", 1)
        assertEquals("5000 CRC", result.offered)
        assertEquals("Pendiente de validación", result.total)
        assertEquals("Pendiente de validación", result.difference)
    }
    @Test fun `confirmed totals preserve positive negative and zero net differences`() {
        listOf(5500L to "500 CRC", 4500L to "-500 CRC", 5000L to "0 CRC").forEach { (final, expected) ->
            val result = RideFinalFarePresentation.from(5000, final, "CRC", "COMPLETED", 1)
            assertEquals(expected, result.difference)
            assertEquals("$final CRC", result.total)
        }
    }
    @Test fun `legacy or unfinished state cannot certify final money`() {
        assertEquals("Pendiente de validación", RideFinalFarePresentation.from(5000, 5000, "CRC", "IN_PROGRESS", 1).total)
        assertEquals("Pendiente de validación", RideFinalFarePresentation.from(5000, 5000, "CRC", "COMPLETED", 0).total)
    }
    @Test fun `USD formats integer cents without floating point rounding`() {
        val result = RideFinalFarePresentation.from(1001, 1015, "USD", "COMPLETED", 1)
        assertEquals("10.15 USD", result.total)
        assertEquals("0.14 USD", result.difference)
    }
}
