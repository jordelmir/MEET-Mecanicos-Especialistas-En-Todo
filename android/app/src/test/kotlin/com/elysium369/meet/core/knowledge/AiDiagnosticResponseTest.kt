package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDiagnosticResponseTest {

    @Test
    fun `confidence must be in 0 to 1 range`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiDiagnosticResponse(
                summary = "x", topCauses = emptyList(), nextTests = emptyList(),
                riskAssessment = "x", explanation = "x", confidence = 1.5
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiDiagnosticResponse(
                summary = "x", topCauses = emptyList(), nextTests = emptyList(),
                riskAssessment = "x", explanation = "x", confidence = -0.1
            )
        }
    }

    @Test
    fun `P0230 response includes avoidReplacing for PCM`() {
        val r = AiDiagnosticResponse(
            summary = "P0230 con bateria 11.2V indica problema en alimentacion antes que bomba.",
            topCauses = listOf(
                "Bateria / tierra (32%)",
                "Relay bomba (24%)",
                "Fusible / feed (18%)",
                "Conector sulfatado (12%)",
                "Bomba combustible (10%)",
                "PCM driver (4%)"
            ),
            nextTests = listOf(
                "Medir voltaje bateria KOEO",
                "Inspeccionar fusible bomba",
                "Verificar relay con prueba bidireccional"
            ),
            riskAssessment = "Riesgo de no-start; evitar trayecto largo si DTC activo.",
            explanation = "Con bateria 11.2V la prioridad es el circuito de alimentacion. Reemplazar bomba sin verificar voltaje es desperdicio.",
            missingData = listOf("Presion de combustible medida"),
            avoidReplacing = listOf("PCM (sin evidencia suficiente)", "Bomba (sin verificar voltage)"),
            confidence = 0.87
        )
        val s = r.formatForUser()
        assertTrue("must include NO REEMPLACES", s.contains("NO REEMPLACES"))
        assertTrue("must mention PCM", s.contains("PCM"))
        assertTrue("must show battery", s.contains("Bateria / tierra"))
        assertTrue("must show confidence", s.contains("87%"))
    }

    @Test
    fun `format includes all sections when populated`() {
        val r = AiDiagnosticResponse(
            summary = "DTC code P0420 catalyst efficiency below threshold.",
            topCauses = listOf("Catalyst aging", "O2 sensor lazy", "Exhaust leak"),
            nextTests = listOf("Check O2 sensor voltage", "Inspect exhaust"),
            riskAssessment = "Emissions; drive cycle to confirm.",
            explanation = "P0420 alone does not condemn the catalyst.",
            contradictions = listOf("O2 sensor heater OK"),
            missingData = listOf("Long-term fuel trim"),
            internetValidationQueries = listOf("P0420 Honda Civic 2015 TSB"),
            safetyWarnings = listOf("Exhaust fumes - ensure ventilated area"),
            partsLikelyNeeded = listOf("Catalytic converter (after verification)"),
            avoidReplacing = listOf("Catalyst (verify O2 sensors first)"),
            confidence = 0.72
        )
        val s = r.formatForUser()
        assertTrue(s.contains("Resumen"))
        assertTrue(s.contains("Causas principales"))
        assertTrue(s.contains("NO REEMPLACES"))
        assertTrue(s.contains("Siguientes pruebas"))
        assertTrue(s.contains("Datos faltantes"))
        assertTrue(s.contains("Advertencias de seguridad"))
        assertTrue(s.contains("72%"))
    }
}
