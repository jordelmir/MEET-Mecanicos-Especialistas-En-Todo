package com.elysium369.meet.core.knowledge.graph

import com.elysium369.meet.core.parts.CompatibilityConfidence
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleApplicabilityResolverTest {
    private val resolver = VehicleApplicabilityResolver()
    private val component = component("fuel_pump")
    private val profile = referenceProfile()

    @Test
    fun `missing active vehicle stays generic education only`() {
        val decision = resolver.resolve(
            vehicle = null,
            component = component,
            knowledge = VehicleApplicabilityKnowledge(),
            evidence = emptyList()
        )

        assertEquals(VehicleApplicabilityState.GENERIC, decision.state)
        assertTrue(decision.educationAllowed)
        assertFalse(decision.diagnosisAllowed)
        assertFalse(decision.replacementAllowed)
        assertFalse(decision.purchaseAllowed)
        assertEquals(CompatibilityConfidence.UNKNOWN, decision.purchaseCompatibility)
    }

    @Test
    fun `generic MAF never becomes an Accent fact`() {
        val maf = component("maf_sensor")
        val rule = rule(
            canonicalKey = "maf_sensor",
            state = VehicleApplicabilityState.NOT_DOCUMENTED,
            reviewState = GraphReviewState.REVIEW_REQUIRED,
            evidenceRequired = listOf("physical_sensor_inventory", "oem_engine_diagram")
        )

        val decision = resolver.resolve(
            vehicle = accent(),
            component = maf,
            knowledge = VehicleApplicabilityKnowledge(profile = profile, rule = rule),
            evidence = emptyList()
        )

        assertEquals(VehicleApplicabilityState.NOT_DOCUMENTED, decision.state)
        assertFalse(decision.diagnosisAllowed)
        assertFalse(decision.replacementAllowed)
        assertTrue(EvidenceKind.OEM in decision.missingEvidence)
        assertTrue(EvidenceKind.PHYSICAL_INVENTORY in decision.missingEvidence)
    }

    @Test
    fun `unreviewed negative exclusion degrades to not documented`() {
        val app = component("app_sensor")
        val decision = resolver.resolve(
            vehicle = accent(),
            component = app,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                rule = rule(
                    canonicalKey = "app_sensor",
                    state = VehicleApplicabilityState.NOT_APPLICABLE,
                    reviewState = GraphReviewState.REVIEW_REQUIRED
                )
            ),
            evidence = emptyList()
        )

        assertEquals(VehicleApplicabilityState.NOT_DOCUMENTED, decision.state)
        assertTrue(
            decision.warnings.any {
                it.code == ApplicabilityWarningCode.REVIEW_REQUIRED
            }
        )
    }

    @Test
    fun `verified physical presence conflicting with a negative rule fails closed`() {
        val turbo = component("turbocharger")
        val decision = resolver.resolve(
            vehicle = accent(),
            component = turbo,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                rule = rule(
                    canonicalKey = "turbocharger",
                    state = VehicleApplicabilityState.NOT_APPLICABLE
                )
            ),
            evidence = listOf(
                evidence(
                    id = "physical-turbo",
                    kind = EvidenceKind.PHYSICAL_INVENTORY,
                    assertion = EvidenceAssertion.PRESENT,
                    subject = "turbocharger"
                )
            )
        )

        assertEquals(VehicleApplicabilityState.CONFLICTED, decision.state)
        assertFalse(decision.diagnosisAllowed)
        assertFalse(decision.replacementAllowed)
        assertFalse(decision.purchaseAllowed)
    }

    @Test
    fun `aliases and labels never act as vehicle evidence`() {
        val alias = component("fuel_pump").copy(
            id = "alias_bomba",
            type = KnowledgeNodeType.ALIAS,
            label = "Bomba de gasolina"
        )

        val decision = resolver.resolve(
            vehicle = accent(),
            component = alias,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(profileEdge(alias, VehicleApplicabilityState.CONFIRMED))
            ),
            evidence = emptyList()
        )

        assertEquals(VehicleApplicabilityState.GENERIC, decision.state)
        assertFalse(decision.diagnosisAllowed)
        assertTrue(
            decision.warnings.any {
                it.code == ApplicabilityWarningCode.CANONICAL_COMPONENT_REQUIRED
            }
        )
    }

    @Test
    fun `reference tuple without verified market and VIN is capped at conditional`() {
        val decision = resolver.resolve(
            vehicle = accent(vin = null, market = null),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                )
            ),
            evidence = emptyList()
        )

        assertEquals(VehicleApplicabilityState.CONDITIONAL, decision.state)
        assertTrue(decision.diagnosisAllowed)
        assertFalse(decision.replacementAllowed)
        assertTrue(EvidenceKind.VIN in decision.missingEvidence)
        assertTrue(EvidenceKind.MARKET in decision.missingEvidence)
        assertTrue(EvidenceKind.ENGINE_CODE in decision.missingEvidence)
    }

    @Test
    fun `VIN text without matching verified evidence never promotes applicability`() {
        val decision = resolver.resolve(
            vehicle = accent(vin = VALID_VIN, market = "CR"),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                )
            ),
            evidence = emptyList()
        )

        assertEquals(VehicleApplicabilityState.CONDITIONAL, decision.state)
        assertTrue(EvidenceKind.VIN in decision.missingEvidence)
        assertFalse(decision.replacementAllowed)
    }

    @Test
    fun `verified component observation confirms presence but no gate means no replacement`() {
        val decision = resolver.resolve(
            vehicle = accent(),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                )
            ),
            evidence = listOf(
                evidence(
                    id = "pump-present",
                    kind = EvidenceKind.PHYSICAL_INVENTORY,
                    assertion = EvidenceAssertion.PRESENT,
                    subject = "fuel_pump"
                )
            )
        )

        assertEquals(VehicleApplicabilityState.CONFIRMED, decision.state)
        assertTrue(decision.diagnosisAllowed)
        assertFalse(decision.replacementAllowed)
        assertTrue(
            decision.warnings.any {
                it.code == ApplicabilityWarningCode.REPLACEMENT_GATE_MISSING
            }
        )
    }

    @Test
    fun `all diagnostic gate requirements allow replacement but not an unproven purchase`() {
        val decision = resolver.resolve(
            vehicle = accent(),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                ),
                replacementGateEdges = listOf(replacementGate(component))
            ),
            evidence = confirmedPumpEvidence()
        )

        assertEquals(VehicleApplicabilityState.CONFIRMED, decision.state)
        assertTrue(decision.replacementAllowed)
        assertFalse(decision.purchaseAllowed)
        assertFalse(decision.purchaseCompatibility == CompatibilityConfidence.EXACT)
        assertTrue(EvidenceKind.OEM in decision.missingEvidence)
    }

    @Test
    fun `completed gate measurements without component failure confirmation still block replacement`() {
        val decision = resolver.resolve(
            vehicle = accent(),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                ),
                replacementGateEdges = listOf(replacementGate(component))
            ),
            evidence = listOf(
                evidence(
                    id = "pump-present",
                    kind = EvidenceKind.PHYSICAL_INVENTORY,
                    assertion = EvidenceAssertion.PRESENT,
                    subject = "fuel_pump"
                ),
                evidence(
                    id = "loaded-voltage",
                    kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
                    assertion = EvidenceAssertion.PASSED,
                    subject = "fuel_pump",
                    requirementKey = "loaded_voltage_at_pump"
                ),
                evidence(
                    id = "ground-drop",
                    kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
                    assertion = EvidenceAssertion.PASSED,
                    subject = "fuel_pump",
                    requirementKey = "ground_drop"
                )
            )
        )

        assertEquals(VehicleApplicabilityState.CONFIRMED, decision.state)
        assertFalse(decision.replacementAllowed)
        assertTrue("component_failure_confirmation" in decision.missingRequirements)
    }

    @Test
    fun `vehicle identity evidence cannot masquerade as a diagnostic gate result`() {
        val decision = resolver.resolve(
            vehicle = accent(vin = VALID_VIN),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                ),
                replacementGateEdges = listOf(replacementGate(component))
            ),
            evidence = confirmedPumpEvidence().filterNot {
                it.requirementKey == "loaded_voltage_at_pump"
            } + evidence(
                id = "forged-gate-vin",
                kind = EvidenceKind.VIN,
                assertion = EvidenceAssertion.MATCHES,
                value = VALID_VIN,
                requirementKey = "loaded_voltage_at_pump"
            )
        )

        assertFalse(decision.replacementAllowed)
        assertTrue("loaded_voltage_at_pump" in decision.missingRequirements)
    }

    @Test
    fun `verified VIN and component-bound OEM allow exact purchase after repair gates`() {
        val evidence = confirmedPumpEvidence() + listOf(
            evidence(
                id = "vin-proof",
                kind = EvidenceKind.VIN,
                assertion = EvidenceAssertion.MATCHES,
                value = VALID_VIN
            ),
            evidence(
                id = "pump-oem",
                kind = EvidenceKind.OEM,
                assertion = EvidenceAssertion.MATCHES,
                subject = "fuel_pump",
                value = "OEM-OWNER-OBSERVED"
            )
        )

        val decision = resolver.resolve(
            vehicle = accent(vin = VALID_VIN),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                ),
                replacementGateEdges = listOf(replacementGate(component))
            ),
            evidence = evidence
        )

        assertTrue(decision.replacementAllowed)
        assertTrue(decision.purchaseAllowed)
        assertEquals(CompatibilityConfidence.EXACT, decision.purchaseCompatibility)
    }

    @Test
    fun `OEM from another component cannot authorize exact purchase`() {
        val evidence = confirmedPumpEvidence() + listOf(
            evidence(
                id = "vin-proof",
                kind = EvidenceKind.VIN,
                assertion = EvidenceAssertion.MATCHES,
                value = VALID_VIN
            ),
            evidence(
                id = "relay-oem",
                kind = EvidenceKind.OEM,
                assertion = EvidenceAssertion.MATCHES,
                subject = "fuel_pump_relay",
                value = "OTHER-COMPONENT"
            )
        )

        val decision = resolver.resolve(
            vehicle = accent(vin = VALID_VIN),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                ),
                replacementGateEdges = listOf(replacementGate(component))
            ),
            evidence = evidence
        )

        assertTrue(decision.replacementAllowed)
        assertFalse(decision.purchaseAllowed)
        assertFalse(decision.purchaseCompatibility == CompatibilityConfidence.EXACT)
    }

    @Test
    fun `approved physical part match is an alternate exact purchase path`() {
        val decision = resolver.resolve(
            vehicle = accent(),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                ),
                replacementGateEdges = listOf(replacementGate(component))
            ),
            evidence = confirmedPumpEvidence() + evidence(
                id = "approved-match",
                kind = EvidenceKind.APPROVED_PHYSICAL_MATCH,
                assertion = EvidenceAssertion.MATCHES,
                subject = "fuel_pump"
            )
        )

        assertTrue(decision.replacementAllowed)
        assertTrue(decision.purchaseAllowed)
        assertEquals(CompatibilityConfidence.EXACT, decision.purchaseCompatibility)
    }

    @Test
    fun `contradictory verified observations fail closed independent of input order`() {
        val present = evidence(
            id = "b-present",
            kind = EvidenceKind.PHYSICAL_INVENTORY,
            assertion = EvidenceAssertion.PRESENT,
            subject = "fuel_pump"
        )
        val absent = evidence(
            id = "a-absent",
            kind = EvidenceKind.PHYSICAL_INVENTORY,
            assertion = EvidenceAssertion.ABSENT,
            subject = "fuel_pump"
        )
        val knowledge = VehicleApplicabilityKnowledge(
            profile = profile,
            applicabilityEdges = listOf(profileEdge(component, VehicleApplicabilityState.PROBABLE))
        )

        val first = resolver.resolve(accent(), component, knowledge, listOf(present, absent))
        val second = resolver.resolve(accent(), component, knowledge, listOf(absent, present))

        assertEquals(VehicleApplicabilityState.CONFLICTED, first.state)
        assertEquals(first, second)
        assertEquals(listOf("a-absent", "b-present"), first.evidenceUsed.map(VehicleEvidence::id))
    }

    @Test
    fun `conflicting graph applicability edges fail closed`() {
        val probable = profileEdge(component, VehicleApplicabilityState.PROBABLE)
        val excluded = profileEdge(component, VehicleApplicabilityState.NOT_APPLICABLE).copy(
            id = "edge_profile_fuel_pump_conflict"
        )

        val decision = resolver.resolve(
            vehicle = accent(),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(probable, excluded)
            ),
            evidence = emptyList()
        )

        assertEquals(VehicleApplicabilityState.CONFLICTED, decision.state)
        assertFalse(decision.diagnosisAllowed)
        assertFalse(decision.purchaseAllowed)
    }

    @Test
    fun `verified market evidence that disagrees with active vehicle fails closed`() {
        val decision = resolver.resolve(
            vehicle = accent(market = "CR"),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                )
            ),
            evidence = listOf(
                evidence(
                    id = "market-proof",
                    kind = EvidenceKind.MARKET,
                    assertion = EvidenceAssertion.MATCHES,
                    value = "US"
                )
            )
        )

        assertEquals(VehicleApplicabilityState.CONFLICTED, decision.state)
        assertFalse(decision.diagnosisAllowed)
    }

    @Test
    fun `duplicate evidence identifiers fail closed`() {
        val first = evidence(
            id = "same-evidence",
            kind = EvidenceKind.PHYSICAL_INVENTORY,
            assertion = EvidenceAssertion.PRESENT,
            subject = "fuel_pump"
        )
        val duplicate = first.copy(assertion = EvidenceAssertion.ABSENT)

        val decision = resolver.resolve(
            vehicle = accent(),
            component = component,
            knowledge = VehicleApplicabilityKnowledge(
                profile = profile,
                applicabilityEdges = listOf(
                    profileEdge(component, VehicleApplicabilityState.PROBABLE)
                )
            ),
            evidence = listOf(first, duplicate)
        )

        assertEquals(VehicleApplicabilityState.CONFLICTED, decision.state)
        assertFalse(decision.replacementAllowed)
    }

    @Test
    fun `real graph repository applies the Accent negative MAF rule`() {
        val repository = AutomotiveKnowledgeGraphRepository {
            graphAsset().readBytes()
        }
        val maf = requireNotNull(repository.node("maf_sensor"))

        val decision = resolver.resolve(
            repository = repository,
            vehicle = accent(),
            component = maf,
            evidence = emptyList()
        )

        assertEquals(VehicleApplicabilityState.NOT_DOCUMENTED, decision.state)
        assertFalse(decision.diagnosisAllowed)
        assertTrue(EvidenceKind.OEM in decision.missingEvidence)
    }

    private fun confirmedPumpEvidence(): List<VehicleEvidence> = listOf(
        evidence(
            id = "pump-confirmed",
            kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
            assertion = EvidenceAssertion.MATCHES,
            subject = "fuel_pump"
        ),
        evidence(
            id = "loaded-voltage",
            kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
            assertion = EvidenceAssertion.PASSED,
            subject = "fuel_pump",
            requirementKey = "loaded_voltage_at_pump"
        ),
        evidence(
            id = "ground-drop",
            kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
            assertion = EvidenceAssertion.PASSED,
            subject = "fuel_pump",
            requirementKey = "ground_drop"
        )
    )

    private fun accent(
        vin: String? = null,
        market: String? = null
    ) = ActiveVehicleIdentity(
        selectedProfileId = "hyundai_accent_verna_2005_1_6_at",
        make = "Hyundai",
        model = "Accent",
        year = 2005,
        engine = "1.6L",
        transmission = "Automatic",
        market = market,
        vin = vin
    )

    private fun component(canonicalKey: String) = KnowledgeNode(
        id = canonicalKey,
        type = KnowledgeNodeType.COMPONENT,
        label = canonicalKey,
        canonicalKey = canonicalKey,
        sourceBlockIds = emptyList(),
        sourceRefs = emptyList(),
        curatedSourceIds = listOf("test_pack")
    )

    private fun referenceProfile() = VehicleGraphProfile(
        id = "hyundai_accent_verna_2005_1_6_at",
        nodeId = "hyundai_accent_verna_2005_1_6_at",
        make = "Hyundai",
        models = listOf("Accent", "Verna"),
        year = 2005,
        engine = "1.6L; engine code and market require VIN or OEM confirmation",
        transmission = "Automatic; exact variant requires VIN or OEM confirmation",
        marketState = VehicleMarketState.UNCONFIRMED,
        requiresVinConfirmation = true,
        applicability = VehicleApplicabilityState.CONDITIONAL,
        confidence = GraphConfidence.MEDIUM,
        reviewState = GraphReviewState.REVIEW_REQUIRED,
        sourceBlockIds = emptyList(),
        sourceRefs = emptyList(),
        curatedSourceIds = listOf("test_pack"),
        evidenceRequired = listOf("vin", "market", "engine_code", "physical_inventory")
    )

    private fun rule(
        canonicalKey: String,
        state: VehicleApplicabilityState,
        reviewState: GraphReviewState = GraphReviewState.REVIEW_REQUIRED,
        evidenceRequired: List<String> = listOf("physical_inventory")
    ) = VehicleApplicabilityRule(
        id = "rule_$canonicalKey",
        profileId = profile.id,
        canonicalKey = canonicalKey,
        state = state,
        confidence = GraphConfidence.MEDIUM,
        reviewState = reviewState,
        reason = "fixture",
        sourceBlockIds = emptyList(),
        sourceRefs = emptyList(),
        curatedSourceIds = listOf("test_pack"),
        evidenceRequired = evidenceRequired
    )

    private fun profileEdge(
        target: KnowledgeNode,
        state: VehicleApplicabilityState
    ) = edge(
        id = "edge_profile_${target.id}",
        from = profile.nodeId,
        to = target.id,
        type = KnowledgeEdgeType.APPLIES_TO,
        applicability = state,
        evidenceRequired = listOf("vin_engine_variant")
    )

    private fun replacementGate(target: KnowledgeNode) = edge(
        id = "edge_gate_${target.id}",
        from = target.id,
        to = "test_${target.id}",
        type = KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE,
        applicability = VehicleApplicabilityState.CONDITIONAL,
        evidenceRequired = listOf("loaded_voltage_at_pump", "ground_drop")
    )

    private fun edge(
        id: String,
        from: String,
        to: String,
        type: KnowledgeEdgeType,
        applicability: VehicleApplicabilityState,
        evidenceRequired: List<String>
    ) = KnowledgeEdge(
        id = id,
        from = from,
        to = to,
        type = type,
        sourceBlockIds = emptyList(),
        sourceRefs = emptyList(),
        curatedSourceIds = listOf("test_pack"),
        observedEvidenceIds = emptyList(),
        evidenceRequired = evidenceRequired,
        reviewState = GraphReviewState.REVIEW_REQUIRED,
        applicability = applicability,
        confidence = GraphConfidence.MEDIUM
    )

    private fun evidence(
        id: String,
        kind: EvidenceKind,
        assertion: EvidenceAssertion,
        subject: String? = null,
        value: String? = null,
        requirementKey: String? = null
    ) = VehicleEvidence(
        id = id,
        kind = kind,
        status = VehicleEvidenceStatus.VERIFIED,
        assertion = assertion,
        subjectCanonicalKey = subject,
        value = value,
        requirementKey = requirementKey
    )

    private fun graphAsset(): File = listOf(
        File("src/main/assets/knowledge/graph/automotive_knowledge_graph.json"),
        File("app/src/main/assets/knowledge/graph/automotive_knowledge_graph.json"),
        File("android/app/src/main/assets/knowledge/graph/automotive_knowledge_graph.json")
    ).firstOrNull(File::isFile) ?: error("Missing automotive graph asset")

    private companion object {
        const val VALID_VIN = "KMHCG45C01U123456"
    }
}
