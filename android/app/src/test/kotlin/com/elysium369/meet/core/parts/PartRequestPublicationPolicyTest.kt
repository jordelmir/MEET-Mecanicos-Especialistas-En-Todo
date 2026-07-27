package com.elysium369.meet.core.parts

import com.elysium369.meet.core.knowledge.graph.GraphBundleIntegrity
import com.elysium369.meet.core.knowledge.graph.GraphIntegrityStatus
import com.elysium369.meet.core.knowledge.graph.PartEvidenceGate
import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeBundle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartRequestPublicationPolicyTest {
    @Test
    fun `compatibility block cannot publish by name and phone alone`() {
        val decision = PartRequestPublicationPolicy.evaluate(
            partName = "Bomba de combustible",
            vehiclePresent = true,
            contactPresent = true,
            graphEvidenceRequired = true,
            compatibility = CompatibilityResult(
                confidence = CompatibilityConfidence.MEDIUM,
                warnings = listOf(
                    CompatibilityWarning("BLOCK", "Prueba pendiente", WarningSeverity.BLOCK)
                ),
                requiredConfirmations = emptyList(),
                crossReferenceNumbers = emptyList(),
                recommendedQuestions = emptyList(),
                rationale = emptyList()
            ),
            suggestion = PartSuggestion(
                partName = "Bomba de combustible",
                category = "ENGINE",
                position = PartPosition.ENGINE,
                priority = 1,
                rationale = "Prueba",
                canonicalKey = "fuel_pump"
            ),
            knowledge = null
        )

        assertFalse(decision.allowed)
        assertTrue(decision.reasons.size >= 2)
    }

    @Test
    fun `only matching graph exact gate overrides legacy block`() {
        val suggestion = PartSuggestion(
            partName = "Bomba de combustible",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 1,
            rationale = "Evidencia verificada",
            canonicalKey = "fuel_pump",
            evidenceState = PartSuggestionEvidenceState.PURCHASE_VERIFIED,
            requestAllowed = true
        )
        val knowledge = bundle("fuel_pump", purchaseAllowed = true)
        val compatibility = CompatibilityResult(
            confidence = CompatibilityConfidence.MEDIUM,
            warnings = listOf(
                CompatibilityWarning("P0230", "Bloqueo heredado", WarningSeverity.BLOCK)
            ),
            requiredConfirmations = emptyList(),
            crossReferenceNumbers = emptyList(),
            recommendedQuestions = emptyList(),
            rationale = emptyList()
        )

        val decision = PartRequestPublicationPolicy.evaluate(
            partName = suggestion.partName,
            vehiclePresent = true,
            contactPresent = true,
            graphEvidenceRequired = true,
            compatibility = compatibility,
            suggestion = suggestion,
            knowledge = knowledge
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun `mismatched canonical component never opens request`() {
        val suggestion = PartSuggestion(
            partName = "Relé",
            category = "ELECTRICAL",
            position = PartPosition.FUSE_BOX,
            priority = 1,
            rationale = "Prueba",
            canonicalKey = "fuel_pump_relay",
            evidenceState = PartSuggestionEvidenceState.PURCHASE_VERIFIED,
            requestAllowed = true
        )

        val decision = PartRequestPublicationPolicy.evaluate(
            partName = suggestion.partName,
            vehiclePresent = true,
            contactPresent = true,
            graphEvidenceRequired = true,
            compatibility = null,
            suggestion = suggestion,
            knowledge = bundle("fuel_pump", purchaseAllowed = true)
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun `canonical 3d reference may request quotes without claiming exact compatibility`() {
        val decision = PartRequestPublicationPolicy.evaluate(
            partName = "Cigüeñal",
            vehiclePresent = true,
            contactPresent = true,
            graphEvidenceRequired = true,
            compatibility = CompatibilityResult(
                confidence = CompatibilityConfidence.UNKNOWN,
                warnings = emptyList(),
                requiredConfirmations = listOf("Confirmar VIN y OEM"),
                crossReferenceNumbers = emptyList(),
                recommendedQuestions = emptyList(),
                rationale = listOf("Referencia 3D no dimensional"),
            ),
            suggestion = null,
            knowledge = null,
            canonicalReferenceId = "g4ed-027-ciguenal",
        )

        assertTrue(decision.allowed)
    }

    private fun bundle(
        canonicalKey: String,
        purchaseAllowed: Boolean
    ) = RepairKnowledgeBundle(
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
        partGate = PartEvidenceGate(
            componentCanonicalKey = canonicalKey,
            replacementAllowed = purchaseAllowed,
            purchaseAllowed = purchaseAllowed,
            purchaseCompatibility = if (purchaseAllowed) {
                CompatibilityConfidence.EXACT
            } else {
                CompatibilityConfidence.UNKNOWN
            },
            requiredTests = emptyList(),
            missingEvidence = emptyList(),
            missingRequirements = emptyList(),
            reason = "Prueba"
        ),
        visualTargets = emptyList(),
        citations = emptyList(),
        warnings = emptyList(),
        insufficientDataReasons = emptyList(),
        fallbackUsed = false,
        graphIntegrity = GraphBundleIntegrity(GraphIntegrityStatus.VALID, "a".repeat(64))
    )
}
