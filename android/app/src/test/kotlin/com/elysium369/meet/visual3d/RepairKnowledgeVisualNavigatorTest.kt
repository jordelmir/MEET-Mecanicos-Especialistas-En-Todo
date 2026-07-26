package com.elysium369.meet.visual3d

import com.elysium369.meet.core.knowledge.graph.ApplicabilityDecision
import com.elysium369.meet.core.knowledge.graph.GraphBundleIntegrity
import com.elysium369.meet.core.knowledge.graph.GraphConfidence
import com.elysium369.meet.core.knowledge.graph.GraphIntegrityStatus
import com.elysium369.meet.core.knowledge.graph.PartEvidenceGate
import com.elysium369.meet.core.knowledge.graph.RepairCandidate
import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeAuthority
import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeBundle
import com.elysium369.meet.core.knowledge.graph.RepairVisualAuthority
import com.elysium369.meet.core.knowledge.graph.VehicleApplicabilityState
import com.elysium369.meet.core.knowledge.graph.VisualFocusTarget
import com.elysium369.meet.core.parts.CompatibilityConfidence
import com.elysium369.meet.visual3d.domain.RepairKnowledgeVisualNavigator
import com.elysium369.meet.visual3d.domain.RepairVisualAssetClass
import com.elysium369.meet.visual3d.domain.RepairVisualDisposition
import com.elysium369.meet.visual3d.domain.VisualAuthority
import com.elysium369.meet.visual3d.domain.applyRepairKnowledge
import com.elysium369.meet.visual3d.ui.TwinFocusMode
import com.elysium369.meet.visual3d.ui.VehicleTwinViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairKnowledgeVisualNavigatorTest {
    @Test
    fun `P0230 relay target resolves to procedural mesh without OEM authority`() {
        val plan = RepairKnowledgeVisualNavigator.plan(
            bundle(
                target = target("fuel_pump_relay", RepairVisualAuthority.PROCEDURAL_SCHEMATIC),
                applicability = VehicleApplicabilityState.CONFIRMED
            )
        )

        val relay = requireNotNull(plan.primaryTarget)
        assertEquals("relay_fuel_pump", relay.meshKey)
        assertEquals(RepairVisualAssetClass.PROCEDURAL_DIAGNOSTIC, relay.assetClass)
        assertEquals(VisualAuthority.GENERIC_SCHEMATIC, relay.visualAuthority)
        assertFalse(relay.isDimensionalModel)
        assertTrue(relay.exactnessDisclaimer.contains("Confirmar", ignoreCase = true))
    }

    @Test
    fun `negative vehicle applicability keeps matching mesh educational only`() {
        val plan = RepairKnowledgeVisualNavigator.plan(
            bundle(
                target = target("maf_sensor", RepairVisualAuthority.UNAVAILABLE),
                applicability = VehicleApplicabilityState.NOT_DOCUMENTED
            )
        )

        val maf = plan.targets.single()
        assertEquals(RepairVisualDisposition.EDUCATIONAL_ONLY, maf.disposition)
        assertFalse(maf.canFocus)
        assertFalse(plan.canNavigate)
        assertTrue(plan.warnings.any { it.contains("educativa") })
    }

    @Test
    fun `unknown semantic component fails closed without fake mesh`() {
        val plan = RepairKnowledgeVisualNavigator.plan(
            bundle(
                target = target("component_not_in_visual_contract", RepairVisualAuthority.UNAVAILABLE),
                applicability = VehicleApplicabilityState.GENERIC
            )
        )

        assertEquals(RepairVisualDisposition.UNAVAILABLE, plan.targets.single().disposition)
        assertEquals(null, plan.targets.single().meshKey)
        assertFalse(plan.canNavigate)
    }

    @Test
    fun `viewport enters focused xray mode only for focusable plan`() {
        val plan = RepairKnowledgeVisualNavigator.plan(
            bundle(
                target = target("fuel_pump_relay", RepairVisualAuthority.PROCEDURAL_SCHEMATIC),
                applicability = VehicleApplicabilityState.CONFIRMED
            )
        )

        val state = VehicleTwinViewportState().applyRepairKnowledge(plan)

        assertEquals(TwinFocusMode.COMPONENT, state.focusMode)
        assertTrue(state.xRayEnabled)
        assertFalse(state.autoRotateEnabled)
        assertEquals(1, state.cameraResetNonce)
    }

    private fun target(
        canonicalKey: String,
        authority: RepairVisualAuthority
    ) = VisualFocusTarget(
        semanticNodeId = "node_$canonicalKey",
        componentCanonicalKey = canonicalKey,
        label = canonicalKey,
        authority = authority,
        reason = "Enfoque emitido por el grafo.",
        isDimensionalModel = false,
        citationIds = listOf("citation_$canonicalKey")
    )

    private fun bundle(
        target: VisualFocusTarget,
        applicability: VehicleApplicabilityState
    ): RepairKnowledgeBundle {
        val decision = ApplicabilityDecision(
            state = applicability,
            confidence = GraphConfidence.MEDIUM,
            reason = "Prueba",
            evidenceUsed = emptyList(),
            missingEvidence = emptyList(),
            missingRequirements = emptyList(),
            warnings = emptyList(),
            educationAllowed = true,
            diagnosisAllowed = applicability !in setOf(
                VehicleApplicabilityState.NOT_DOCUMENTED,
                VehicleApplicabilityState.NOT_APPLICABLE,
                VehicleApplicabilityState.CONFLICTED
            ),
            replacementAllowed = false,
            purchaseAllowed = false,
            purchaseCompatibility = CompatibilityConfidence.UNKNOWN
        )
        return RepairKnowledgeBundle(
            observations = emptyList(),
            dtcs = emptyList(),
            invalidDtcInputs = emptyList(),
            sourceClaims = emptyList(),
            inferences = emptyList(),
            candidates = listOf(
                RepairCandidate(
                    nodeId = target.semanticNodeId,
                    canonicalKey = target.componentCanonicalKey,
                    label = target.label,
                    rank = 1,
                    reason = target.reason,
                    authority = RepairKnowledgeAuthority.REVIEWED_GRAPH,
                    applicability = decision,
                    citationIds = target.citationIds
                )
            ),
            nextTests = emptyList(),
            doNotReplaceYet = emptyList(),
            procedures = emptyList(),
            tools = emptyList(),
            safetyNotices = emptyList(),
            partGate = PartEvidenceGate(
                componentCanonicalKey = null,
                replacementAllowed = false,
                purchaseAllowed = false,
                purchaseCompatibility = CompatibilityConfidence.UNKNOWN,
                requiredTests = emptyList(),
                missingEvidence = emptyList(),
                missingRequirements = emptyList(),
                reason = "No autorizado."
            ),
            visualTargets = listOf(target),
            citations = emptyList(),
            warnings = emptyList(),
            insufficientDataReasons = emptyList(),
            fallbackUsed = false,
            graphIntegrity = GraphBundleIntegrity(GraphIntegrityStatus.VALID, "a".repeat(64))
        )
    }
}
