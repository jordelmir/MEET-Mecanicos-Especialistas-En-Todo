package com.elysium369.meet.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticProvenanceTest {

    @Test
    fun `Real is reliable for diagnosis`() {
        assertTrue(DiagnosticProvenance.Real.isReliableForDiagnosis)
    }

    @Test
    fun `Offline is reliable for diagnosis`() {
        assertTrue(DiagnosticProvenance.Offline.isReliableForDiagnosis)
    }

    @Test
    fun `Simulated is NOT reliable for diagnosis`() {
        assertFalse(DiagnosticProvenance.Simulated.isReliableForDiagnosis)
    }

    @Test
    fun `SinEnlace is NOT reliable`() {
        assertFalse(DiagnosticProvenance.SinEnlace.isReliableForDiagnosis)
    }

    @Test
    fun `RequiereHardware is NOT reliable`() {
        assertFalse(
            DiagnosticProvenance.RequiereHardware("Hantek USB").isReliableForDiagnosis
        )
    }

    @Test
    fun `NoSoportado is NOT reliable`() {
        assertFalse(
            DiagnosticProvenance.NoSoportado("PID 0A no soportado").isReliableForDiagnosis
        )
    }

    @Test
    fun `Inferred is NOT reliable`() {
        assertFalse(
            DiagnosticProvenance.Inferred("rules", 0.7).isReliableForDiagnosis
        )
    }

    @Test
    fun `ManualEntry is NOT reliable`() {
        assertFalse(
            DiagnosticProvenance.ManualEntry("user123").isReliableForDiagnosis
        )
    }

    @Test
    fun `Inferred rejects invalid confidence`() {
        try {
            DiagnosticProvenance.Inferred("rules", 1.5)
            assertTrue("Should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("confidence"))
        }
    }

    @Test
    fun `DiagnosticValue real factory wraps with Real provenance`() {
        val value = DiagnosticValue.real(85.5)
        assertEquals(85.5, value.value!!, 0.001)
        assertTrue(value.provenance is DiagnosticProvenance.Real)
    }

    @Test
    fun `DiagnosticValue sinEnlace returns null value`() {
        val value = DiagnosticValue.sinEnlace<Double>()
        assertNull(value.value)
        assertTrue(value.provenance is DiagnosticProvenance.SinEnlace)
    }

    @Test
    fun `display labels are user-readable`() {
        assertEquals("REAL", DiagnosticProvenance.Real.displayLabel)
        assertEquals("OFFLINE", DiagnosticProvenance.Offline.displayLabel)
        assertEquals("SIMULADO", DiagnosticProvenance.Simulated.displayLabel)
        assertEquals("SIN ENLACE", DiagnosticProvenance.SinEnlace.displayLabel)
        assertEquals(
            "REQUIERE Hantek USB",
            DiagnosticProvenance.RequiereHardware("Hantek USB").displayLabel
        )
        assertEquals(
            "NO SOPORTADO: PID 0A no soportado",
            DiagnosticProvenance.NoSoportado("PID 0A no soportado").displayLabel
        )
    }
}