package com.elysium369.meet.core.parts

import com.elysium369.meet.core.knowledge.graph.GraphBundleIntegrity
import com.elysium369.meet.core.knowledge.graph.GraphIntegrityStatus
import com.elysium369.meet.core.knowledge.graph.PartEvidenceGate
import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartSuggestionEngineTest {

    @Test
    fun `P0230 returns at least 4 distinct suggestions`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230"))
        )
        assertTrue(s.size >= 4)
    }

    @Test
    fun `P0230 lists the relay BEFORE the fuel pump`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230"))
        )
        val relayIndex = s.indexOfFirst { it.partName.contains("Relé", ignoreCase = true) }
        val pumpIndex = s.indexOfFirst { it.riskPart && it.partName.contains("Bomba", ignoreCase = true) }
        assertTrue("relay index $relayIndex < pump index $pumpIndex", relayIndex in 0 until pumpIndex)
    }

    @Test
    fun `P0230 fuel pump is LAST and flagged as risk part`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230"))
        )
        val last = s.last()
        assertTrue(last.riskPart)
        assertNotNull(last.disclaimer)
        assertTrue(last.disclaimer!!.lowercase().contains("manómetro"))
    }

    @Test
    fun `P0230 does not invent a fuel pressure sensor candidate`() {
        val suggestions = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230")),
        )

        assertTrue(suggestions.none { it.partName.contains("sensor de presión", ignoreCase = true) })
    }

    @Test
    fun `P0230 rationales avoid unsourced frequency and price claims`() {
        val rationale = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230")),
        ).joinToString(" ") { it.rationale.lowercase() }

        assertTrue(listOf("commonly", "most common", "meaningful slice", "cheap").none(rationale::contains))
    }

    @Test
    fun `unknown DTC emits a single generic diagnostic suggestion`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P9999"))
        )
        assertEquals(1, s.size)
        assertTrue(s[0].partName.lowercase().contains("diagnóstico"))
    }

    @Test
    fun `P0420 marks the catalytic converter as risk part last`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0420"))
        )
        assertTrue(s.size >= 2)
        assertTrue(s.last().riskPart)
    }

    @Test
    fun `P0300 starts with spark plugs`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0300"))
        )
        assertTrue(s.first().partName.lowercase().contains("bujía"))
    }

    @Test
    fun `P0171 does not suggest a fuel cap replacement`() {
        val suggestions = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0171")),
        )

        assertTrue(suggestions.none { it.partName.contains("tapa", ignoreCase = true) })
    }

    @Test
    fun `3D component fuel_pump_assembly is risk part`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.FROM_3D_COMPONENT, componentSlug = "fuel_pump_assembly")
        )
        assertEquals(1, s.size)
        assertTrue(s[0].riskPart)
    }

    @Test
    fun `3D component fuel_pump_relay is NOT risk part`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.FROM_3D_COMPONENT, componentSlug = "fuel_pump_relay")
        )
        assertEquals(1, s.size)
        assertEquals(false, s[0].riskPart)
    }

    @Test
    fun `3D component abs_module emits safety disclaimer`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.FROM_3D_COMPONENT, componentSlug = "abs_module")
        )
        assertTrue(s[0].disclaimer!!.lowercase().contains("técnico"))
    }

    @Test
    fun `work order hint passes through verbatim`() {
        val s = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.WORK_ORDER, workOrderHint = "Pastilla freno trasero")
        )
        assertEquals(1, s.size)
        assertEquals("Pastilla freno trasero", s[0].partName)
    }

    @Test
    fun `legacy suggestions are informational and cannot silently open a request`() {
        val suggestions = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230"))
        )

        assertTrue(suggestions.all { it.evidenceState == PartSuggestionEvidenceState.INFORMATIONAL })
        assertTrue(suggestions.none { it.requestAllowed })
    }

    @Test
    fun `graph gate only opens the exact matching canonical component`() {
        val bundle = emptyBundle(
            PartEvidenceGate(
                componentCanonicalKey = "fuel_pump",
                replacementAllowed = true,
                purchaseAllowed = true,
                purchaseCompatibility = CompatibilityConfidence.EXACT,
                requiredTests = listOf("fuel_pressure_check"),
                missingEvidence = emptyList(),
                missingRequirements = emptyList(),
                reason = "Evidencia verificada."
            )
        )

        val suggestions = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230")),
            bundle
        )

        val pump = suggestions.single { it.canonicalKey == "fuel_pump" }
        assertEquals(PartSuggestionEvidenceState.PURCHASE_VERIFIED, pump.evidenceState)
        assertTrue(pump.requestAllowed)
        assertTrue(
            suggestions.filterNot { it.canonicalKey == "fuel_pump" }
                .none { it.requestAllowed }
        )
    }

    @Test
    fun `missing graph evidence is carried into a blocked part suggestion`() {
        val bundle = emptyBundle(
            PartEvidenceGate(
                componentCanonicalKey = "fuel_pump",
                replacementAllowed = false,
                purchaseAllowed = false,
                purchaseCompatibility = CompatibilityConfidence.UNKNOWN,
                requiredTests = listOf("fuel_pressure_check"),
                missingEvidence = listOf(
                    com.elysium369.meet.core.knowledge.graph.EvidenceKind.VIN,
                    com.elysium369.meet.core.knowledge.graph.EvidenceKind.OEM
                ),
                missingRequirements = listOf("pump_failure_confirmed"),
                reason = "Falta evidencia."
            )
        )

        val pump = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(source = SuggestionSource.DTC, dtcCodes = listOf("P0230")),
            bundle
        ).single { it.canonicalKey == "fuel_pump" }

        assertFalse(pump.requestAllowed)
        assertEquals(PartSuggestionEvidenceState.REQUIRES_TESTS, pump.evidenceState)
        assertTrue(pump.missingEvidence.containsAll(listOf("VIN", "OEM", "pump_failure_confirmed")))
    }

    private fun emptyBundle(partGate: PartEvidenceGate) = RepairKnowledgeBundle(
        observations = emptyList(),
        dtcs = emptyList(),
        invalidDtcInputs = emptyList(),
        sourceClaims = emptyList(),
        inferences = emptyList(),
        candidates = emptyList(),
        nextTests = emptyList(),
        doNotReplaceYet = emptyList(),
        procedures = emptyList(),
        tools = emptyList(),
        safetyNotices = emptyList(),
        partGate = partGate,
        visualTargets = emptyList(),
        citations = emptyList(),
        warnings = emptyList(),
        insufficientDataReasons = emptyList(),
        fallbackUsed = false,
        graphIntegrity = GraphBundleIntegrity(GraphIntegrityStatus.VALID, "a".repeat(64))
    )
}
