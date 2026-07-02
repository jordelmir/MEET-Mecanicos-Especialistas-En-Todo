package com.elysium369.meet.diagnosis

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbabilisticDiagnosisEngineTest {

    private val engine = ProbabilisticDiagnosisEngine()

    private fun context(
        dtcCode: String,
        provenance: DiagnosticProvenance = DiagnosticProvenance.Real,
        symptoms: List<String> = emptyList()
    ): DiagnosisContext = DiagnosisContext(
        dtcCode = dtcCode,
        provenance = provenance,
        vehicleMake = "Toyota",
        vehicleModel = "Camry",
        vehicleYear = 2018,
        reportedSymptoms = symptoms
    )

    // ─────────── P0230 ───────────

    @Test
    fun `P0230 produces multiple causes with descending probability`() {
        val report = engine.diagnose(context("P0230"))
        assertEquals("P0230", report.dtcCode)
        assertTrue("Must have ≥3 causes", report.probableCauses.size >= 3)
        // Descending.
        for (i in 0 until report.probableCauses.size - 1) {
            assertTrue(
                "Causes must be sorted descending by probability",
                report.probableCauses[i].probability >= report.probableCauses[i + 1].probability
            )
        }
    }

    @Test
    fun `P0230 top cause is not the most expensive part`() {
        val report = engine.diagnose(context("P0230"))
        val top = report.topCause!!
        assertEquals("Relé de bomba de combustible defectuoso o fusible quemado", top.cause)
        assertFalse("Top cause should NOT be the expensive pump",
            top.cause.contains("Bomba de combustible defectuosa"))
    }

    @Test
    fun `P0230 every cause has mandatory tests`() {
        val report = engine.diagnose(context("P0230"))
        assertTrue("Every cause must have ≥1 mandatory test",
            report.probableCauses.all { it.mandatoryTests.isNotEmpty() })
    }

    // ─────────── P0301 ───────────

    @Test
    fun `P0301 top cause is spark plug (cheap, common)`() {
        val report = engine.diagnose(context("P0301"))
        val top = report.topCause!!
        assertTrue("Top cause should be spark plug related",
            top.cause.contains("Bujía"))
    }

    @Test
    fun `P0301 lists do-not-replace-yet items`() {
        val report = engine.diagnose(context("P0301"))
        assertTrue("Must include do-not-replace-yet list",
            report.doNotReplaceYet.isNotEmpty())
    }

    @Test
    fun `P0301 has open mandatory tests`() {
        val report = engine.diagnose(context("P0301"))
        assertTrue("Report should signal pending tests before replacing parts",
            report.hasOpenTests)
    }

    // ─────────── P0171 ───────────

    @Test
    fun `P0171 top cause is vacuum leak (most common)`() {
        val report = engine.diagnose(context("P0171"))
        val top = report.topCause!!
        assertTrue("Top cause should be vacuum leak",
            top.cause.contains("Fuga de vacío"))
    }

    // ─────────── P0420 ───────────

    @Test
    fun `P0420 top cause is catalytic converter`() {
        val report = engine.diagnose(context("P0420"))
        val top = report.topCause!!
        assertTrue("Top cause should mention catalyst",
            top.cause.contains("Catalizador"))
    }

    @Test
    fun `P0420 exhaust leak is NOT top cause`() {
        val report = engine.diagnose(context("P0420"))
        val top = report.topCause!!
        assertFalse("Exhaust leak should not be top cause for P0420",
            top.cause.startsWith("Fuga de escape"))
    }

    // ─────────── P0128 ───────────

    @Test
    fun `P0128 thermostat is top cause`() {
        val report = engine.diagnose(context("P0128"))
        val top = report.topCause!!
        assertTrue("Top cause should be thermostat",
            top.cause.contains("Termostato"))
    }

    // ─────────── Reglas de provenance ───────────

    @Test
    fun `SinEnlace reduces all probabilities and warns`() {
        val real = engine.diagnose(context("P0301", provenance = DiagnosticProvenance.Real))
        val sinEnlace = engine.diagnose(context("P0301", provenance = DiagnosticProvenance.SinEnlace))
        assertTrue("SinEnlace confidence should be lower",
            sinEnlace.confidenceOverall < real.confidenceOverall)
        assertTrue("Must warn SinEnlace",
            sinEnlace.safetyWarnings.any { it.contains("Sin enlace", ignoreCase = true) })
    }

    @Test
    fun `Simulated is heavily penalized`() {
        val sim = engine.diagnose(context("P0301", provenance = DiagnosticProvenance.Simulated))
        assertTrue("Simulated must warn",
            sim.safetyWarnings.any { it.contains("simulado", ignoreCase = true) })
    }

    @Test
    fun `ManualEntry warns about manual input`() {
        val manual = engine.diagnose(context("P0301", provenance = DiagnosticProvenance.ManualEntry("u1")))
        assertTrue("ManualEntry must warn",
            manual.safetyWarnings.any { it.contains("manualmente", ignoreCase = true) })
    }

    @Test
    fun `safety warning always includes the NEVER-CONFIRM rule`() {
        val report = engine.diagnose(context("P0301"))
        assertTrue("Must include the never-confirm rule",
            report.safetyWarnings.any { it.contains("NO confirma pieza", ignoreCase = true) })
    }

    @Test
    fun `confidenceOverall is in 0 to 1`() {
        val report = engine.diagnose(context("P0301"))
        assertTrue("Confidence must be in [0,1]",
            report.confidenceOverall in 0.0..1.0)
    }

    // ─────────── DTC desconocido ───────────

    @Test
    fun `unknown DTC returns insufficient data`() {
        val report = engine.diagnose(context("Z9999"))
        assertEquals(0, report.probableCauses.size)
        assertEquals(0.0, report.confidenceOverall, 0.001)
        assertTrue("Must warn about unknown DTC",
            report.safetyWarnings.any { it.contains("no está en el catálogo", ignoreCase = true) })
    }

    // ─────────── Suggest from symptoms ───────────

    @Test
    fun `symptom misfire suggests misfire DTCs`() {
        val suggestions = engine.suggestDtcFromSymptoms(listOf("engine misfire", "rough idle"))
        assertTrue("Should suggest P0301", "P0301" in suggestions)
        assertTrue("Should suggest P0300", "P0300" in suggestions)
    }

    @Test
    fun `symptom no arranca suggests P0230`() {
        val suggestions = engine.suggestDtcFromSymptoms(listOf("no arranca", "no enciende"))
        assertTrue("Should suggest P0230", "P0230" in suggestions)
    }

    @Test
    fun `empty symptoms returns empty list`() {
        assertEquals(emptyList<String>(), engine.suggestDtcFromSymptoms(emptyList()))
    }
}