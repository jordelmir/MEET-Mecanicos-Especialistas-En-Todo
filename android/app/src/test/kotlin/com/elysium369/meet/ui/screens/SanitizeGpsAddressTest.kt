package com.elysium369.meet.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SanitizeGpsAddressTest {

    @Test
    fun `address with raw lat lng is sanitized`() {
        // Patron real que produce ObdViewModel.resolveLocationDetails cuando Geocoder falla.
        val raw = "Ubicación GPS (37.7749, -122.4194)"
        assertEquals(
            "Ubicación GPS detectada",
            sanitizeGpsAddress(raw)
        )
    }

    @Test
    fun `address without raw lat lng is preserved verbatim`() {
        val raw = "Calle 5, Avenida Central, San José"
        assertEquals(raw, sanitizeGpsAddress(raw))
    }

    @Test
    fun `empty string passes through unchanged`() {
        assertEquals("", sanitizeGpsAddress(""))
    }

    @Test
    fun `negative lat and lng are sanitized`() {
        // Cubre el rango de valores negativos sin signo + y sin signo -.
        val raw = "Ubicación GPS (-12.5, 80.3)"
        assertEquals(
            "Ubicación GPS detectada",
            sanitizeGpsAddress(raw)
        )
    }

    @Test
    fun `integer coordinates are sanitized`() {
        val raw = "Ubicación GPS (10, 20)"
        assertEquals("Ubicación GPS detectada", sanitizeGpsAddress(raw))
    }

    @Test
    fun `partial match is preserved (false positives avoided)`() {
        // "Ubicacion GPS" sin acento, o con texto adicional, NO debe matchear.
        val raw = "Ubicación GPS detectada pero offline"
        assertEquals(raw, sanitizeGpsAddress(raw))
    }

    @Test
    fun `address that just mentions coords but not in the fallback pattern is preserved`() {
        // Una dirección normal que contiene numeros debe preservarse verbatim.
        val raw = "100m Norte de Plaza Central, casa #42"
        assertEquals(raw, sanitizeGpsAddress(raw))
    }
}
