package com.elysium369.meet.core.knowledge.graph

import com.elysium369.meet.core.parts.CompatibilityConfidence
import com.elysium369.meet.diagnostic.DiagnosticProvenance
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairKnowledgeOrchestratorTest {
    private val source = FixtureKnowledgeSource.p0230()
    private val orchestrator = RepairKnowledgeOrchestrator(source)

    @Test
    fun `P0230 produces the cited circuit-first sequence and blocks replacement`() {
        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                dtcs = listOf("p0230"),
                provenance = DiagnosticProvenance.Real
            )
        )

        assertEquals(listOf("P0230"), bundle.dtcs.map(DtcKnowledge::code))
        assertEquals(
            listOf(
                "Guardar DTC, freeze frame y contexto del vehículo",
                "Verificar batería, alimentación y masa bajo carga",
                "Comprobar fusible, feed, corto y sobreconsumo",
                "Separar control y salida de carga del relé",
                "Inspeccionar conector, terminales y arnés",
                "Medir alimentación y caída de masa con el circuito cargado",
                "Medir corriente, presión y retención con criterios revisados",
                "Evaluar PCM o TSB solo como hipótesis final"
            ),
            bundle.nextTests.map(ConfirmationTest::label)
        )
        assertTrue(bundle.citations.isNotEmpty())
        assertTrue(bundle.citations.all { it.sourceRef.isComplete() })
        assertFalse(bundle.partGate.replacementAllowed)
        assertFalse(bundle.partGate.purchaseAllowed)
        assertTrue(bundle.doNotReplaceYet.any { it.componentCanonicalKey == "fuel_pump" })
        assertTrue(bundle.doNotReplaceYet.any { it.componentCanonicalKey == "pcm_driver" })
        assertEquals(
            RepairVisualAuthority.PROCEDURAL_SCHEMATIC,
            bundle.visualTargets.first().authority
        )
        assertEquals("fuel_pump_relay", bundle.visualTargets.first().componentCanonicalKey)
    }

    @Test
    fun `P0230 keeps observations source claims and inferences separated`() {
        val observation = RepairObservation(
            id = "scan-code",
            label = "Código activo",
            value = "P0230",
            provenance = DiagnosticProvenance.Real
        )

        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                dtcs = listOf("P0230"),
                provenance = DiagnosticProvenance.Real,
                observations = listOf(observation)
            )
        )

        assertEquals(listOf(observation), bundle.observations)
        assertTrue(bundle.sourceClaims.isNotEmpty())
        assertTrue(bundle.inferences.isNotEmpty())
        assertTrue(bundle.inferences.all { it.citationIds.isNotEmpty() })
        assertTrue(
            bundle.sourceClaims.none {
                it.statement == observation.value
            }
        )
    }

    @Test
    fun `complete component failure VIN OEM and gate evidence unlocks exact fuel pump request`() {
        val evidence = listOf(
            vehicleEvidence(
                id = "pump-failure",
                kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
                assertion = EvidenceAssertion.MATCHES,
                subject = "fuel_pump"
            ),
            vehicleEvidence(
                id = "loaded-voltage",
                kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
                assertion = EvidenceAssertion.PASSED,
                subject = "fuel_pump",
                requirement = "loaded_voltage_at_pump"
            ),
            vehicleEvidence(
                id = "ground-drop",
                kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
                assertion = EvidenceAssertion.PASSED,
                subject = "fuel_pump",
                requirement = "ground_drop"
            ),
            vehicleEvidence(
                id = "pump-current",
                kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
                assertion = EvidenceAssertion.PASSED,
                subject = "fuel_pump",
                requirement = "pump_current"
            ),
            vehicleEvidence(
                id = "fuel-pressure",
                kind = EvidenceKind.DIAGNOSTIC_CONFIRMATION,
                assertion = EvidenceAssertion.PASSED,
                subject = "fuel_pump",
                requirement = "fuel_pressure_oem_criteria"
            ),
            vehicleEvidence(
                id = "vin",
                kind = EvidenceKind.VIN,
                assertion = EvidenceAssertion.MATCHES,
                value = VALID_VIN
            ),
            vehicleEvidence(
                id = "oem",
                kind = EvidenceKind.OEM,
                assertion = EvidenceAssertion.MATCHES,
                subject = "fuel_pump",
                value = "OWNER-VERIFIED-OEM"
            )
        )

        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(vin = VALID_VIN),
                dtcs = listOf("P0230"),
                provenance = DiagnosticProvenance.Real,
                evidence = evidence
            )
        )

        assertTrue(bundle.partGate.replacementAllowed)
        assertTrue(bundle.partGate.purchaseAllowed)
        assertEquals(CompatibilityConfidence.EXACT, bundle.partGate.purchaseCompatibility)
        assertTrue(bundle.doNotReplaceYet.none { it.componentCanonicalKey == "fuel_pump" })
        assertTrue(bundle.doNotReplaceYet.any { it.componentCanonicalKey == "pcm_driver" })
    }

    @Test
    fun `unknown valid DTC uses bounded generic fallback and cannot suggest a part`() {
        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                dtcs = listOf("P0999"),
                provenance = DiagnosticProvenance.Offline
            )
        )

        assertEquals(RepairDtcAuthority.LEGACY_GENERIC, bundle.dtcs.single().authority)
        assertTrue(bundle.fallbackUsed)
        assertTrue(bundle.candidates.isEmpty())
        assertFalse(bundle.partGate.replacementAllowed)
        assertFalse(bundle.partGate.purchaseAllowed)
        assertEquals(1, bundle.nextTests.size)
        assertEquals(RepairKnowledgeAuthority.POLICY, bundle.nextTests.single().authority)
        assertTrue(bundle.insufficientDataReasons.isNotEmpty())
    }

    @Test
    fun `malformed DTC is rejected instead of normalized into false knowledge`() {
        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                dtcs = listOf("P9999", "bad-code"),
                provenance = DiagnosticProvenance.ManualEntry("owner")
            )
        )

        assertTrue(bundle.dtcs.isEmpty())
        assertTrue(bundle.invalidDtcInputs.containsAll(listOf("P9999", "bad-code")))
        assertTrue(bundle.candidates.isEmpty())
        assertFalse(bundle.partGate.purchaseAllowed)
    }

    @Test
    fun `component-only navigation resolves one cited applicability decision`() {
        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                selectedComponentId = "map_sensor",
                provenance = DiagnosticProvenance.Offline
            )
        )

        assertTrue(bundle.dtcs.isEmpty())
        assertEquals(listOf("map_sensor"), bundle.candidates.map(RepairCandidate::canonicalKey))
        assertEquals(
            VehicleApplicabilityState.CONDITIONAL,
            bundle.candidates.single().applicability.state
        )
        assertTrue(bundle.candidates.single().citationIds.isNotEmpty())
        assertFalse(bundle.partGate.replacementAllowed)
    }

    @Test
    fun `Accent negative MAF rule remains visible and never becomes a part candidate`() {
        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                selectedComponentId = "maf_sensor",
                provenance = DiagnosticProvenance.Offline
            )
        )

        val maf = bundle.candidates.single()
        assertEquals(VehicleApplicabilityState.NOT_DOCUMENTED, maf.applicability.state)
        assertFalse(maf.applicability.diagnosisAllowed)
        assertFalse(bundle.partGate.purchaseAllowed)
        assertTrue(
            bundle.warnings.any {
                it.contains("no documenta", ignoreCase = true) ||
                    it.contains("excluye", ignoreCase = true)
            }
        )
    }

    @Test
    fun `generic non-reference vehicle receives circuit education but no vehicle fact`() {
        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = ActiveVehicleIdentity(
                    make = "Toyota",
                    model = "Corolla",
                    year = 2005,
                    engine = "1.8L",
                    transmission = "Automatic"
                ),
                dtcs = listOf("P0230"),
                provenance = DiagnosticProvenance.Real
            )
        )

        assertEquals(8, bundle.nextTests.size)
        assertTrue(bundle.candidates.all {
            it.applicability.state == VehicleApplicabilityState.GENERIC
        })
        assertFalse(bundle.partGate.replacementAllowed)
        assertFalse(bundle.partGate.purchaseAllowed)
    }

    @Test
    fun `simulated provenance is always marked non-actionable`() {
        val bundle = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                dtcs = listOf("P0230"),
                provenance = DiagnosticProvenance.Simulated
            )
        )

        assertTrue(
            bundle.warnings.any {
                it.contains("simulad", ignoreCase = true)
            }
        )
        assertFalse(bundle.partGate.replacementAllowed)
        assertFalse(bundle.partGate.purchaseAllowed)
    }

    @Test
    fun `input order and duplicate DTCs do not change bundle ordering`() {
        val first = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                dtcs = listOf("P0230", "p0999", "P0230"),
                provenance = DiagnosticProvenance.Offline
            )
        )
        val second = orchestrator.resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                dtcs = listOf("P0999", "p0230"),
                provenance = DiagnosticProvenance.Offline
            )
        )

        assertEquals(first.dtcs, second.dtcs)
        assertEquals(first.nextTests, second.nextTests)
        assertEquals(first.candidates, second.candidates)
        assertEquals(first.citations, second.citations)
    }

    @Test
    fun `real repository resolves P0230 without losing graph integrity`() {
        val repository = AutomotiveKnowledgeGraphRepository {
            graphAsset().readBytes()
        }
        val real = RepairKnowledgeOrchestrator(repository).resolve(
            RepairKnowledgeRequest(
                vehicle = accent(),
                dtcs = listOf("P0230"),
                provenance = DiagnosticProvenance.Real
            )
        )

        assertEquals(GraphIntegrityStatus.VALID, real.graphIntegrity.status)
        assertEquals(EXPECTED_AUTOMOTIVE_GRAPH_CONTENT_SHA256, real.graphIntegrity.contentSha256)
        assertEquals(8, real.nextTests.size)
        assertNotNull(real.candidates.firstOrNull { it.canonicalKey == "fuel_pump" })
        assertTrue(real.citations.isNotEmpty())
    }

    private fun accent(vin: String? = null) = ActiveVehicleIdentity(
        selectedProfileId = ACCENT_VERNA_2005_REFERENCE_PROFILE_ID,
        make = "Hyundai",
        model = "Accent",
        year = 2005,
        engine = "1.6L",
        transmission = "Automatic",
        vin = vin
    )

    private fun vehicleEvidence(
        id: String,
        kind: EvidenceKind,
        assertion: EvidenceAssertion,
        subject: String? = null,
        value: String? = null,
        requirement: String? = null
    ) = VehicleEvidence(
        id = id,
        kind = kind,
        status = VehicleEvidenceStatus.VERIFIED,
        assertion = assertion,
        subjectCanonicalKey = subject,
        value = value,
        requirementKey = requirement
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

private class FixtureKnowledgeSource(
    private val nodes: List<KnowledgeNode>,
    private val edges: List<KnowledgeEdge>,
    private val profiles: List<VehicleGraphProfile>,
    private val rules: List<VehicleApplicabilityRule>
) : RepairKnowledgeSource {
    override fun node(id: String): KnowledgeNode? = nodes.firstOrNull { it.id == id }

    override fun nodesByCanonicalKey(key: String): List<KnowledgeNode> =
        nodes.filter { it.canonicalKey == key }.sortedBy(KnowledgeNode::id)

    override fun dtc(code: String): KnowledgeNode? = nodes.firstOrNull {
        it.type == KnowledgeNodeType.DTC &&
            it.canonicalKey.equals(code.trim(), ignoreCase = true)
    }

    override fun profile(id: String): VehicleGraphProfile? =
        profiles.firstOrNull { it.id == id }

    override fun applicabilityRule(
        profileId: String,
        canonicalKey: String
    ): VehicleApplicabilityRule? = rules.firstOrNull {
        it.profileId == profileId && it.canonicalKey == canonicalKey
    }

    override fun outgoingEdges(
        id: String,
        types: Set<KnowledgeEdgeType>
    ): List<KnowledgeEdge> = edges.filter {
        it.from == id && (types.isEmpty() || it.type in types)
    }.sortedWith(edgeOrder)

    override fun incomingEdges(
        id: String,
        types: Set<KnowledgeEdgeType>
    ): List<KnowledgeEdge> = edges.filter {
        it.to == id && (types.isEmpty() || it.type in types)
    }.sortedWith(edgeOrder)

    override fun integrityStatus(): GraphIntegrityStatus = GraphIntegrityStatus.VALID

    companion object {
        private val edgeOrder = compareBy<KnowledgeEdge> {
            it.sequenceOrder ?: Int.MAX_VALUE
        }.thenBy(KnowledgeEdge::id)

        fun p0230(): FixtureKnowledgeSource {
            val profile = profile()
            val sourceRef = SourceRef(
                sourceDocumentId = "document_17",
                blockId = "block_007074_73dfe71dbb",
                textHash = "73dfe71dbb101f8173f60590f5b67499545f4dd961c81f77537974e1ab75b38f"
            )
            val nodes = mutableListOf(
                node(
                    id = profile.nodeId,
                    type = KnowledgeNodeType.VEHICLE_PROFILE,
                    canonical = profile.id,
                    label = "Hyundai Accent/Verna 2005 1.6 AT",
                    refs = listOf(sourceRef)
                ),
                node(
                    id = "dtc_P0230",
                    type = KnowledgeNodeType.DTC,
                    canonical = "P0230",
                    label = "P0230 — circuito primario de la bomba de combustible"
                ),
                node("fuel_pump", KnowledgeNodeType.COMPONENT, "fuel_pump", "Bomba eléctrica de combustible", listOf(sourceRef)),
                node("pcm_driver", KnowledgeNodeType.COMPONENT, "pcm_driver", "Controlador PCM del relé de bomba"),
                node("map_sensor", KnowledgeNodeType.COMPONENT, "map_sensor", "Sensor MAP", listOf(sourceRef)),
                node("maf_sensor", KnowledgeNodeType.COMPONENT, "maf_sensor", "Sensor MAF", listOf(sourceRef))
            )
            val tests = listOf(
                "test_p0230_capture_context" to "Guardar DTC, freeze frame y contexto del vehículo",
                "test_p0230_power_and_ground" to "Verificar batería, alimentación y masa bajo carga",
                "test_p0230_fuse_and_feed" to "Comprobar fusible, feed, corto y sobreconsumo",
                "test_p0230_relay_control_and_output" to "Separar control y salida de carga del relé",
                "test_p0230_connector_and_harness" to "Inspeccionar conector, terminales y arnés",
                "test_p0230_loaded_voltage_drop" to "Medir alimentación y caída de masa con el circuito cargado",
                "test_p0230_pump_current" to "Medir corriente, presión y retención con criterios revisados",
                "test_p0230_pcm_driver" to "Evaluar PCM o TSB solo como hipótesis final"
            )
            tests.forEachIndexed { index, (id, label) ->
                nodes += node(
                    id = id,
                    type = KnowledgeNodeType.DIAGNOSTIC_TEST,
                    canonical = null,
                    label = label,
                    refs = if (index >= 2) listOf(sourceRef) else emptyList()
                )
            }

            val edges = mutableListOf(
                edge(
                    id = "edge_profile_fuel_pump",
                    from = profile.nodeId,
                    to = "fuel_pump",
                    type = KnowledgeEdgeType.APPLIES_TO,
                    state = VehicleApplicabilityState.PROBABLE,
                    requirements = listOf("vin_fuel_system_confirmation"),
                    refs = listOf(sourceRef)
                ),
                edge(
                    id = "edge_profile_map",
                    from = profile.nodeId,
                    to = "map_sensor",
                    type = KnowledgeEdgeType.APPLIES_TO,
                    state = VehicleApplicabilityState.PROBABLE,
                    requirements = listOf("vin_engine_variant"),
                    refs = listOf(sourceRef)
                ),
                edge(
                    id = "edge_p0230_first_test",
                    from = "dtc_P0230",
                    to = tests.first().first,
                    type = KnowledgeEdgeType.HAS_DIAGNOSTIC_TEST,
                    state = VehicleApplicabilityState.GENERIC,
                    requirements = listOf("dtc_scan_record", "freeze_frame")
                )
            )
            tests.zipWithNext().forEachIndexed { index, (current, next) ->
                edges += edge(
                    id = "edge_p0230_step_${index + 1}",
                    from = current.first,
                    to = next.first,
                    type = KnowledgeEdgeType.NEXT_STEP,
                    state = VehicleApplicabilityState.GENERIC,
                    requirements = listOf("step_${index + 1}_evidence"),
                    refs = if (index >= 1) listOf(sourceRef) else emptyList(),
                    sequenceOrder = index + 1
                )
            }
            edges += edge(
                id = "edge_gate_fuel_pump",
                from = "fuel_pump",
                to = "test_p0230_pump_current",
                type = KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE,
                state = VehicleApplicabilityState.CONDITIONAL,
                requirements = listOf(
                    "loaded_voltage_at_pump",
                    "ground_drop",
                    "pump_current",
                    "fuel_pressure_oem_criteria"
                ),
                refs = listOf(sourceRef),
                reason = "No reemplazar la bomba desde P0230 por sí solo."
            )
            edges += edge(
                id = "edge_gate_pcm",
                from = "pcm_driver",
                to = "test_p0230_pcm_driver",
                type = KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE,
                state = VehicleApplicabilityState.CONDITIONAL,
                requirements = listOf("all_upstream_tests_passed", "oem_pcm_circuit_test", "tsb_check"),
                reason = "PCM permanece como hipótesis final."
            )
            return FixtureKnowledgeSource(
                nodes = nodes,
                edges = edges,
                profiles = listOf(profile),
                rules = listOf(
                    rule("maf_sensor", VehicleApplicabilityState.NOT_DOCUMENTED)
                )
            )
        }

        private fun node(
            id: String,
            type: KnowledgeNodeType,
            canonical: String?,
            label: String,
            refs: List<SourceRef> = emptyList()
        ) = KnowledgeNode(
            id = id,
            type = type,
            label = label,
            canonicalKey = canonical,
            sourceBlockIds = refs.map(SourceRef::blockId),
            sourceRefs = refs,
            curatedSourceIds = listOf("fixture_pack")
        )

        private fun edge(
            id: String,
            from: String,
            to: String,
            type: KnowledgeEdgeType,
            state: VehicleApplicabilityState,
            requirements: List<String>,
            refs: List<SourceRef> = emptyList(),
            sequenceOrder: Int? = null,
            reason: String? = null
        ) = KnowledgeEdge(
            id = id,
            from = from,
            to = to,
            type = type,
            sourceBlockIds = refs.map(SourceRef::blockId),
            sourceRefs = refs,
            curatedSourceIds = listOf("fixture_pack"),
            observedEvidenceIds = emptyList(),
            evidenceRequired = requirements,
            reviewState = GraphReviewState.REVIEW_REQUIRED,
            applicability = state,
            confidence = GraphConfidence.MEDIUM,
            sequenceId = if (type == KnowledgeEdgeType.NEXT_STEP) "p0230_circuit_first" else null,
            sequenceOrder = sequenceOrder,
            reason = reason
        )

        private fun profile() = VehicleGraphProfile(
            id = ACCENT_VERNA_2005_REFERENCE_PROFILE_ID,
            nodeId = ACCENT_VERNA_2005_REFERENCE_PROFILE_ID,
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
            curatedSourceIds = listOf("fixture_pack"),
            evidenceRequired = listOf("vin", "market", "engine_code", "physical_inventory")
        )

        private fun rule(
            canonical: String,
            state: VehicleApplicabilityState
        ) = VehicleApplicabilityRule(
            id = "rule_$canonical",
            profileId = ACCENT_VERNA_2005_REFERENCE_PROFILE_ID,
            canonicalKey = canonical,
            state = state,
            confidence = GraphConfidence.MEDIUM,
            reviewState = GraphReviewState.REVIEW_REQUIRED,
            reason = "El perfil no documenta este componente; confirmar físicamente.",
            sourceBlockIds = emptyList(),
            sourceRefs = emptyList(),
            curatedSourceIds = listOf("fixture_pack"),
            evidenceRequired = listOf("physical_sensor_inventory", "oem_engine_diagram")
        )
    }
}
