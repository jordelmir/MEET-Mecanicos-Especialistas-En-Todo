package com.elysium369.meet.core.knowledge.graph

import com.elysium369.meet.core.parts.CompatibilityConfidence
import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnosis.DiagnosisContext
import com.elysium369.meet.diagnosis.ProbabilisticDiagnosisEngine
import java.util.Locale

enum class RepairKnowledgeAuthority {
    OBSERVED,
    REVIEWED_GRAPH,
    REVIEW_REQUIRED_GRAPH,
    LEGACY_GENERIC,
    POLICY
}

enum class RepairDtcAuthority { GRAPH, LEGACY_GENERIC }

enum class RepairVisualAuthority {
    L2_GENERIC_ASSEMBLY,
    PROCEDURAL_SCHEMATIC,
    UNAVAILABLE
}

data class RepairObservation(
    val id: String,
    val label: String,
    val value: String,
    val provenance: DiagnosticProvenance
)

data class RepairKnowledgeRequest(
    val vehicle: ActiveVehicleIdentity? = null,
    val dtcs: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
    val observations: List<RepairObservation> = emptyList(),
    val selectedComponentId: String? = null,
    val selectedComponentCanonicalKey: String? = null,
    val evidence: List<VehicleEvidence> = emptyList(),
    val provenance: DiagnosticProvenance
)

data class DtcKnowledge(
    val code: String,
    val nodeId: String?,
    val label: String,
    val authority: RepairDtcAuthority,
    val provenance: DiagnosticProvenance
)

data class KnowledgeCitation(
    val id: String,
    val carrierId: String,
    val carrierKind: String,
    val sourceRef: SourceRef
)

data class RepairSourceClaim(
    val id: String,
    val carrierId: String,
    val statement: String,
    val authority: RepairKnowledgeAuthority,
    val applicability: VehicleApplicabilityState,
    val citationIds: List<String>
)

data class RepairInference(
    val id: String,
    val statement: String,
    val reason: String,
    val citationIds: List<String>
)

data class ConfirmationTest(
    val id: String,
    val nodeId: String?,
    val label: String,
    val sequenceOrder: Int,
    val requiredEvidence: List<String>,
    val completed: Boolean,
    val authority: RepairKnowledgeAuthority,
    val applicability: VehicleApplicabilityState,
    val citationIds: List<String>
)

data class RepairCandidate(
    val nodeId: String,
    val canonicalKey: String,
    val label: String,
    val rank: Int,
    val reason: String,
    val authority: RepairKnowledgeAuthority,
    val applicability: ApplicabilityDecision,
    val citationIds: List<String>
)

data class DoNotReplaceNotice(
    val componentCanonicalKey: String,
    val label: String,
    val reason: String,
    val requiredEvidence: List<String>
)

data class PartEvidenceGate(
    val componentCanonicalKey: String?,
    val replacementAllowed: Boolean,
    val purchaseAllowed: Boolean,
    val purchaseCompatibility: CompatibilityConfidence,
    val requiredTests: List<String>,
    val missingEvidence: List<EvidenceKind>,
    val missingRequirements: List<String>,
    val reason: String
)

data class RepairSafetyNotice(
    val id: String,
    val label: String,
    val authority: RepairKnowledgeAuthority,
    val professionalOnly: Boolean,
    val citationIds: List<String>
)

data class RepairProcedure(
    val id: String,
    val label: String,
    val authority: RepairKnowledgeAuthority,
    val citationIds: List<String>
)

data class RepairToolRequirement(
    val id: String,
    val label: String,
    val authority: RepairKnowledgeAuthority,
    val citationIds: List<String>
)

data class VisualFocusTarget(
    val semanticNodeId: String,
    val componentCanonicalKey: String,
    val label: String,
    val authority: RepairVisualAuthority,
    val reason: String,
    val isDimensionalModel: Boolean,
    val citationIds: List<String>
)

data class GraphBundleIntegrity(
    val status: GraphIntegrityStatus,
    val contentSha256: String?
)

data class RepairKnowledgeBundle(
    val observations: List<RepairObservation>,
    val dtcs: List<DtcKnowledge>,
    val invalidDtcInputs: List<String>,
    val sourceClaims: List<RepairSourceClaim>,
    val inferences: List<RepairInference>,
    val candidates: List<RepairCandidate>,
    val nextTests: List<ConfirmationTest>,
    val doNotReplaceYet: List<DoNotReplaceNotice>,
    val procedures: List<RepairProcedure>,
    val tools: List<RepairToolRequirement>,
    val safetyNotices: List<RepairSafetyNotice>,
    val partGate: PartEvidenceGate,
    val visualTargets: List<VisualFocusTarget>,
    val citations: List<KnowledgeCitation>,
    val warnings: List<String>,
    val insufficientDataReasons: List<String>,
    val fallbackUsed: Boolean,
    val graphIntegrity: GraphBundleIntegrity
)

interface RepairKnowledgeSource {
    fun node(id: String): KnowledgeNode?
    fun nodesByCanonicalKey(key: String): List<KnowledgeNode>
    fun dtc(code: String): KnowledgeNode?
    fun profile(id: String): VehicleGraphProfile?
    fun applicabilityRule(profileId: String, canonicalKey: String): VehicleApplicabilityRule?
    fun outgoingEdges(
        id: String,
        types: Set<KnowledgeEdgeType> = emptySet()
    ): List<KnowledgeEdge>
    fun incomingEdges(
        id: String,
        types: Set<KnowledgeEdgeType> = emptySet()
    ): List<KnowledgeEdge>
    fun integrityStatus(): GraphIntegrityStatus
}

private class RepositoryRepairKnowledgeSource(
    private val repository: AutomotiveKnowledgeGraphRepository
) : RepairKnowledgeSource {
    override fun node(id: String) = repository.node(id)
    override fun nodesByCanonicalKey(key: String) = repository.nodesByCanonicalKey(key)
    override fun dtc(code: String) = repository.dtc(code)
    override fun profile(id: String) = repository.profile(id)
    override fun applicabilityRule(profileId: String, canonicalKey: String) =
        repository.applicabilityRule(profileId, canonicalKey)
    override fun outgoingEdges(id: String, types: Set<KnowledgeEdgeType>) =
        repository.outgoingEdges(id, types)
    override fun incomingEdges(id: String, types: Set<KnowledgeEdgeType>) =
        repository.incomingEdges(id, types)
    override fun integrityStatus() = repository.integrityStatus()
}

class RepairKnowledgeOrchestrator(
    private val source: RepairKnowledgeSource,
    private val applicabilityResolver: VehicleApplicabilityResolver =
        VehicleApplicabilityResolver(),
    private val legacyDiagnosisEngine: ProbabilisticDiagnosisEngine =
        ProbabilisticDiagnosisEngine()
) {
    constructor(
        repository: AutomotiveKnowledgeGraphRepository,
        applicabilityResolver: VehicleApplicabilityResolver =
            VehicleApplicabilityResolver(),
        legacyDiagnosisEngine: ProbabilisticDiagnosisEngine =
            ProbabilisticDiagnosisEngine()
    ) : this(
        RepositoryRepairKnowledgeSource(repository),
        applicabilityResolver,
        legacyDiagnosisEngine
    )

    fun resolve(request: RepairKnowledgeRequest): RepairKnowledgeBundle {
        val integrity = source.integrityStatus()
        if (integrity != GraphIntegrityStatus.VALID) {
            return invalidGraphBundle(request, integrity)
        }

        val citations = CitationAccumulator()
        val claims = mutableListOf<RepairSourceClaim>()
        val warnings = linkedSetOf<String>()
        val insufficient = linkedSetOf<String>()
        val dtcInputs = request.dtcs.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        val validDtcs = dtcInputs.asSequence()
            .map { it.uppercase(Locale.ROOT) }
            .filter(DTC_PATTERN::matches)
            .distinct()
            .sorted()
            .toList()
        val invalidDtcs = dtcInputs
            .filterNot { DTC_PATTERN.matches(it.uppercase(Locale.ROOT)) }
            .sorted()
        if (invalidDtcs.isNotEmpty()) {
            warnings += "Se ignoraron DTC con formato inválido; no se normalizaron como conocimiento real."
        }

        val profile = source.profile(
            request.vehicle?.selectedProfileId ?: ACCENT_VERNA_2005_REFERENCE_PROFILE_ID
        )
        val sequenceNodes = linkedMapOf<String, List<KnowledgeNode>>()
        val tests = mutableListOf<ConfirmationTest>()
        var fallbackUsed = false
        val dtcKnowledge = validDtcs.map { code ->
            val node = source.dtc(code)
            if (node == null) {
                fallbackUsed = true
                insufficient +=
                    "$code no tiene un nodo citado en el grafo; la orientación permanece genérica."
                tests += legacyFallbackTest(code, request)
                DtcKnowledge(
                    code = code,
                    nodeId = null,
                    label = "$code — conocimiento específico no disponible",
                    authority = RepairDtcAuthority.LEGACY_GENERIC,
                    provenance = request.provenance
                )
            } else {
                val result = resolveTestSequence(
                    dtcNode = node,
                    request = request,
                    citations = citations,
                    claims = claims,
                    insufficient = insufficient
                )
                tests += result.tests
                sequenceNodes[code] = result.nodes
                DtcKnowledge(
                    code = code,
                    nodeId = node.id,
                    label = node.label,
                    authority = RepairDtcAuthority.GRAPH,
                    provenance = request.provenance
                )
            }
        }

        val selected = resolveSelectedComponent(request)
        val seeds = collectCandidateSeeds(
            dtcKnowledge = dtcKnowledge,
            sequenceNodes = sequenceNodes,
            selected = selected
        )
        val candidates = seeds
            .groupBy { requireNotNull(it.node.canonicalKey) }
            .map { (_, sameCanonical) ->
                sameCanonical.minWith(
                    compareBy<CandidateSeed>(CandidateSeed::priority)
                        .thenBy { it.node.id }
                )
            }
            .sortedWith(compareBy<CandidateSeed>(CandidateSeed::priority).thenBy { it.node.id })
            .mapIndexed { index, seed ->
                val canonical = requireNotNull(seed.node.canonicalKey)
                val decision = applicabilityResolver.resolve(
                    vehicle = request.vehicle,
                    component = seed.node,
                    knowledge = applicabilityKnowledge(profile, seed.node),
                    evidence = request.evidence
                )
                val citationIds = citations.add(
                    carrierId = seed.node.id,
                    carrierKind = "COMPONENT",
                    refs = seed.node.sourceRefs + seed.supportingRefs
                )
                if (citationIds.isNotEmpty()) {
                    claims += RepairSourceClaim(
                        id = "claim_component_${seed.node.id}",
                        carrierId = seed.node.id,
                        statement = seed.reason,
                        authority = seed.authority,
                        applicability = decision.state,
                        citationIds = citationIds
                    )
                }
                RepairCandidate(
                    nodeId = seed.node.id,
                    canonicalKey = canonical,
                    label = seed.node.label,
                    rank = index + 1,
                    reason = seed.reason,
                    authority = seed.authority,
                    applicability = decision,
                    citationIds = citationIds
                )
            }

        val inferences = candidates.filter { it.citationIds.isNotEmpty() }.map {
            RepairInference(
                id = "inference_${it.nodeId}",
                statement = "${it.label} es un candidato que requiere verificación; no confirma una falla.",
                reason = it.reason,
                citationIds = it.citationIds
            )
        }
        val gateNotices = buildDoNotReplaceNotices(
            request = request,
            profile = profile,
            sequenceNodes = sequenceNodes
        )
        val primary = candidates.firstOrNull {
            it.nodeId == selected?.id
        } ?: candidates.firstOrNull {
            it.canonicalKey == "fuel_pump" && dtcKnowledge.any { dtc -> dtc.code == "P0230" }
        } ?: candidates.firstOrNull()
        val partGate = buildPartGate(primary, request)
        val nonActionableProvenance =
            request.provenance is DiagnosticProvenance.Simulated ||
                request.provenance is DiagnosticProvenance.SinEnlace ||
                request.provenance is DiagnosticProvenance.NoSoportado ||
                request.provenance is DiagnosticProvenance.ManualEntry
        val effectivePartGate = if (nonActionableProvenance) {
            partGate.copy(
                replacementAllowed = false,
                purchaseAllowed = false,
                purchaseCompatibility = CompatibilityConfidence.UNKNOWN,
                reason = "La procedencia del DTC no autoriza acciones de reemplazo o compra."
            )
        } else {
            partGate
        }

        candidates.flatMap { it.applicability.warnings }.forEach {
            warnings += it.message
        }
        if (dtcKnowledge.isNotEmpty()) {
            warnings += "Un DTC identifica un circuito o sistema; no confirma por sí solo una pieza dañada."
        }
        when (request.provenance) {
            is DiagnosticProvenance.Simulated ->
                warnings += "Datos simulados: solo entrenamiento, no usar para una reparación real."
            is DiagnosticProvenance.SinEnlace ->
                warnings += "OBD no disponible: la orientación no está confirmada por el vehículo."
            is DiagnosticProvenance.ManualEntry ->
                warnings += "DTC manual: confirmar con escaneo real antes de reparar."
            is DiagnosticProvenance.NoSoportado ->
                warnings += "Dato no soportado por el adaptador o vehículo."
            is DiagnosticProvenance.RequiereHardware ->
                warnings += "La confirmación requiere ${request.provenance.toolName}."
            is DiagnosticProvenance.Inferred ->
                warnings += "Dato inferido: no se promueve a observación física."
            is DiagnosticProvenance.Offline ->
                warnings += "Dato offline: confirmar en el vehículo antes de actuar."
            is DiagnosticProvenance.Real -> Unit
        }

        val visualTargets = buildVisualTargets(
            dtcs = dtcKnowledge,
            sequenceNodes = sequenceNodes,
            selected = selected,
            citations = citations
        )
        val relatedNodes = (sequenceNodes.values.flatten() + listOfNotNull(selected) +
            candidates.mapNotNull { source.node(it.nodeId) }).distinctBy(KnowledgeNode::id)
        val procedures = resolveRelatedNodes(
            relatedNodes,
            KnowledgeEdgeType.HAS_PROCEDURE,
            KnowledgeNodeType.PROCEDURE,
            citations
        ).map {
            RepairProcedure(it.node.id, it.node.label, it.authority, it.citationIds)
        }
        val tools = resolveRelatedNodes(
            relatedNodes,
            KnowledgeEdgeType.USES_TOOL,
            KnowledgeNodeType.TOOL,
            citations
        ).map {
            RepairToolRequirement(it.node.id, it.node.label, it.authority, it.citationIds)
        }
        val safety = resolveRelatedNodes(
            relatedNodes,
            KnowledgeEdgeType.HAS_WARNING,
            KnowledgeNodeType.SAFETY_WARNING,
            citations
        ).map {
            RepairSafetyNotice(
                id = it.node.id,
                label = it.node.label,
                authority = it.authority,
                professionalOnly = true,
                citationIds = it.citationIds
            )
        }.toMutableList()
        if (dtcKnowledge.any { it.code == "P0230" }) {
            safety += RepairSafetyNotice(
                id = "policy_fuel_circuit_safety",
                label = "Trabajar sin fuentes de ignición, controlar vapores y seguir el procedimiento OEM.",
                authority = RepairKnowledgeAuthority.POLICY,
                professionalOnly = true,
                citationIds = emptyList()
            )
        }

        if (dtcKnowledge.isEmpty() && selected == null) {
            insufficient += "Se requiere un DTC válido o un componente canónico identificado."
        }
        if (procedures.isEmpty()) {
            insufficient += "Procedimiento detallado no capturado para este contexto."
        }

        return RepairKnowledgeBundle(
            observations = request.observations.distinctBy(RepairObservation::id)
                .sortedBy(RepairObservation::id),
            dtcs = dtcKnowledge,
            invalidDtcInputs = invalidDtcs,
            sourceClaims = claims.distinctBy(RepairSourceClaim::id).sortedBy(RepairSourceClaim::id),
            inferences = inferences.sortedBy(RepairInference::id),
            candidates = candidates,
            nextTests = tests.distinctBy(ConfirmationTest::id)
                .sortedWith(compareBy<ConfirmationTest>(ConfirmationTest::sequenceOrder).thenBy { it.id }),
            doNotReplaceYet = gateNotices.filterNot {
                it.componentCanonicalKey == effectivePartGate.componentCanonicalKey &&
                    effectivePartGate.replacementAllowed
            }.distinctBy(DoNotReplaceNotice::componentCanonicalKey)
                .sortedBy(DoNotReplaceNotice::componentCanonicalKey),
            procedures = procedures.distinctBy(RepairProcedure::id).sortedBy(RepairProcedure::id),
            tools = tools.distinctBy(RepairToolRequirement::id).sortedBy(RepairToolRequirement::id),
            safetyNotices = safety.distinctBy(RepairSafetyNotice::id).sortedBy(RepairSafetyNotice::id),
            partGate = effectivePartGate,
            visualTargets = visualTargets.distinctBy(VisualFocusTarget::semanticNodeId)
                .sortedBy(VisualFocusTarget::semanticNodeId),
            citations = citations.values(),
            warnings = warnings.sorted(),
            insufficientDataReasons = insufficient.sorted(),
            fallbackUsed = fallbackUsed,
            graphIntegrity = GraphBundleIntegrity(
                status = integrity,
                contentSha256 = EXPECTED_AUTOMOTIVE_GRAPH_CONTENT_SHA256
            )
        )
    }

    private fun resolveSelectedComponent(request: RepairKnowledgeRequest): KnowledgeNode? {
        val byId = request.selectedComponentId?.let(source::node)
        if (byId?.type == KnowledgeNodeType.COMPONENT) return byId
        return request.selectedComponentCanonicalKey
            ?.let(source::nodesByCanonicalKey)
            ?.filter { it.type == KnowledgeNodeType.COMPONENT }
            ?.sortedBy(KnowledgeNode::id)
            ?.firstOrNull()
    }

    private fun resolveTestSequence(
        dtcNode: KnowledgeNode,
        request: RepairKnowledgeRequest,
        citations: CitationAccumulator,
        claims: MutableList<RepairSourceClaim>,
        insufficient: MutableSet<String>
    ): SequenceResolution {
        val firstEdges = source.outgoingEdges(
            dtcNode.id,
            setOf(KnowledgeEdgeType.HAS_DIAGNOSTIC_TEST)
        )
        if (firstEdges.isEmpty()) {
            insufficient += "${dtcNode.canonicalKey} no tiene una secuencia diagnóstica capturada."
            return SequenceResolution(emptyList(), emptyList())
        }
        if (firstEdges.size > 1) {
            insufficient += "${dtcNode.canonicalKey} tiene rutas iniciales ambiguas; se conserva la primera por ID."
        }
        val tests = mutableListOf<ConfirmationTest>()
        val nodes = mutableListOf<KnowledgeNode>()
        val visited = mutableSetOf<String>()
        var connectingEdge = firstEdges.sortedBy(KnowledgeEdge::id).first()
        var current = source.node(connectingEdge.to)
        var order = 0
        while (current != null && current.type == KnowledgeNodeType.DIAGNOSTIC_TEST) {
            if (!visited.add(current.id)) {
                insufficient += "Se detectó un ciclo en la secuencia ${dtcNode.canonicalKey}."
                break
            }
            nodes += current
            val refs = current.sourceRefs + connectingEdge.sourceRefs
            val citationIds = citations.add(current.id, "DIAGNOSTIC_TEST", refs)
            val authority = authority(connectingEdge.reviewState)
            tests += ConfirmationTest(
                id = "test_${dtcNode.canonicalKey}_$order",
                nodeId = current.id,
                label = current.label,
                sequenceOrder = order,
                requiredEvidence = connectingEdge.evidenceRequired.sorted(),
                completed = requirementsCompleted(connectingEdge.evidenceRequired, request.evidence),
                authority = authority,
                applicability = connectingEdge.applicability,
                citationIds = citationIds
            )
            if (citationIds.isNotEmpty()) {
                claims += RepairSourceClaim(
                    id = "claim_test_${current.id}",
                    carrierId = current.id,
                    statement = current.label,
                    authority = authority,
                    applicability = connectingEdge.applicability,
                    citationIds = citationIds
                )
            }
            val next = source.outgoingEdges(current.id, setOf(KnowledgeEdgeType.NEXT_STEP))
            if (next.isEmpty()) break
            if (next.size > 1) {
                insufficient += "La secuencia ${dtcNode.canonicalKey} tiene una bifurcación no resuelta."
            }
            connectingEdge = next.sortedWith(
                compareBy<KnowledgeEdge> { it.sequenceOrder ?: Int.MAX_VALUE }
                    .thenBy(KnowledgeEdge::id)
            ).first()
            current = source.node(connectingEdge.to)
            order += 1
        }
        return SequenceResolution(tests, nodes)
    }

    private fun legacyFallbackTest(
        code: String,
        request: RepairKnowledgeRequest
    ): ConfirmationTest {
        legacyDiagnosisEngine.diagnose(
            DiagnosisContext(
                dtcCode = code,
                vehicleMake = request.vehicle?.make ?: "Generic",
                vehicleModel = request.vehicle?.model ?: "Generic",
                vehicleYear = request.vehicle?.year,
                reportedSymptoms = request.symptoms.sorted(),
                provenance = request.provenance
            )
        )
        return ConfirmationTest(
            id = "policy_fallback_$code",
            nodeId = null,
            label = "Capturar DTC, freeze frame y confirmar el código con un escáner compatible.",
            sequenceOrder = 10_000 + code.hashCode().and(0x7fff),
            requiredEvidence = listOf("dtc_scan_record", "freeze_frame", "vehicle_identity"),
            completed = false,
            authority = RepairKnowledgeAuthority.POLICY,
            applicability = VehicleApplicabilityState.GENERIC,
            citationIds = emptyList()
        )
    }

    private fun collectCandidateSeeds(
        dtcKnowledge: List<DtcKnowledge>,
        sequenceNodes: Map<String, List<KnowledgeNode>>,
        selected: KnowledgeNode?
    ): List<CandidateSeed> {
        val seeds = mutableListOf<CandidateSeed>()
        if (selected != null) {
            seeds += CandidateSeed(
                node = selected,
                priority = 0,
                reason = "Componente seleccionado explícitamente.",
                authority = RepairKnowledgeAuthority.REVIEW_REQUIRED_GRAPH,
                supportingRefs = emptyList()
            )
        }
        dtcKnowledge.filter { it.nodeId != null }.forEach { dtc ->
            val nodeId = requireNotNull(dtc.nodeId)
            source.outgoingEdges(
                nodeId,
                setOf(
                    KnowledgeEdgeType.MAY_CAUSE,
                    KnowledgeEdgeType.AFFECTS,
                    KnowledgeEdgeType.SUGGESTS_PART_CANDIDATE
                )
            ).forEach { edge ->
                source.node(edge.to)?.takeIf { it.type == KnowledgeNodeType.COMPONENT }?.let {
                    seeds += seed(it, edge, 10)
                }
            }
            source.incomingEdges(nodeId, setOf(KnowledgeEdgeType.MAY_SET_DTC)).forEach { edge ->
                source.node(edge.from)?.takeIf { it.type == KnowledgeNodeType.COMPONENT }?.let {
                    seeds += seed(it, edge, 20)
                }
            }
            sequenceNodes[dtc.code].orEmpty().forEachIndexed { index, test ->
                source.incomingEdges(
                    test.id,
                    setOf(KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE)
                ).forEach { gate ->
                    source.node(gate.from)?.takeIf { it.type == KnowledgeNodeType.COMPONENT }?.let {
                        if (it.sourceRefs.isNotEmpty() || gate.sourceRefs.isNotEmpty()) {
                            seeds += seed(it, gate, 100 + index)
                        }
                    }
                }
            }
        }
        return seeds.filter {
            !it.node.canonicalKey.isNullOrBlank()
        }
    }

    private fun seed(
        node: KnowledgeNode,
        edge: KnowledgeEdge,
        priority: Int
    ) = CandidateSeed(
        node = node,
        priority = priority,
        reason = edge.reason
            ?: "Relación ${edge.type.name.lowercase(Locale.ROOT)} pendiente de prueba.",
        authority = authority(edge.reviewState),
        supportingRefs = edge.sourceRefs
    )

    private fun applicabilityKnowledge(
        profile: VehicleGraphProfile?,
        component: KnowledgeNode
    ) = VehicleApplicabilityKnowledge(
        profile = profile,
        rule = if (profile != null && component.canonicalKey != null) {
            source.applicabilityRule(profile.id, component.canonicalKey)
        } else {
            null
        },
        applicabilityEdges = if (profile == null) {
            emptyList()
        } else {
            source.incomingEdges(component.id, setOf(KnowledgeEdgeType.APPLIES_TO))
                .filter { it.from == profile.nodeId }
        },
        replacementGateEdges = source.outgoingEdges(
            component.id,
            setOf(KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE)
        )
    )

    private fun buildPartGate(
        candidate: RepairCandidate?,
        request: RepairKnowledgeRequest
    ): PartEvidenceGate {
        if (candidate == null) {
            return PartEvidenceGate(
                componentCanonicalKey = null,
                replacementAllowed = false,
                purchaseAllowed = false,
                purchaseCompatibility = CompatibilityConfidence.UNKNOWN,
                requiredTests = emptyList(),
                missingEvidence = emptyList(),
                missingRequirements = emptyList(),
                reason = "No existe un componente canónico citado para solicitar un repuesto."
            )
        }
        val required = source.outgoingEdges(
            candidate.nodeId,
            setOf(KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE)
        ).flatMap(KnowledgeEdge::evidenceRequired).distinct().sorted()
        val decision = candidate.applicability
        return PartEvidenceGate(
            componentCanonicalKey = candidate.canonicalKey,
            replacementAllowed = decision.replacementAllowed,
            purchaseAllowed = decision.purchaseAllowed,
            purchaseCompatibility = decision.purchaseCompatibility,
            requiredTests = required,
            missingEvidence = decision.missingEvidence,
            missingRequirements = decision.missingRequirements,
            reason = decision.reason
        )
    }

    private fun buildDoNotReplaceNotices(
        request: RepairKnowledgeRequest,
        profile: VehicleGraphProfile?,
        sequenceNodes: Map<String, List<KnowledgeNode>>
    ): List<DoNotReplaceNotice> = sequenceNodes.values.flatten().flatMap { test ->
        source.incomingEdges(
            test.id,
            setOf(KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE)
        ).mapNotNull { edge ->
            val component = source.node(edge.from)
                ?.takeIf { it.type == KnowledgeNodeType.COMPONENT }
                ?: return@mapNotNull null
            val decision = applicabilityResolver.resolve(
                request.vehicle,
                component,
                applicabilityKnowledge(profile, component),
                request.evidence
            )
            if (decision.replacementAllowed) return@mapNotNull null
            DoNotReplaceNotice(
                componentCanonicalKey = component.canonicalKey ?: component.id,
                label = component.label,
                reason = edge.reason ?: "La pieza requiere pruebas antes de reemplazar.",
                requiredEvidence = edge.evidenceRequired.sorted()
            )
        }
    }

    private fun buildVisualTargets(
        dtcs: List<DtcKnowledge>,
        sequenceNodes: Map<String, List<KnowledgeNode>>,
        selected: KnowledgeNode?,
        citations: CitationAccumulator
    ): List<VisualFocusTarget> {
        val targets = mutableListOf<VisualFocusTarget>()
        if (dtcs.any { it.code == "P0230" }) {
            sequenceNodes["P0230"].orEmpty()
                .firstOrNull { it.id == "test_p0230_relay_control_and_output" }
                ?.let { relayTest ->
                    targets += VisualFocusTarget(
                        semanticNodeId = relayTest.id,
                        componentCanonicalKey = "fuel_pump_relay",
                        label = "Circuito de control y salida del relé de bomba",
                        authority = RepairVisualAuthority.PROCEDURAL_SCHEMATIC,
                        reason = "P0230 requiere separar control y carga antes de evaluar la bomba.",
                        isDimensionalModel = false,
                        citationIds = citations.add(
                            relayTest.id,
                            "VISUAL_TARGET",
                            relayTest.sourceRefs
                        )
                    )
                }
        }
        if (targets.isEmpty() && selected?.canonicalKey != null) {
            targets += VisualFocusTarget(
                semanticNodeId = selected.id,
                componentCanonicalKey = selected.canonicalKey,
                label = selected.label,
                authority = RepairVisualAuthority.UNAVAILABLE,
                reason = "La selección semántica existe; la autoridad visual se resolverá por contrato 3D.",
                isDimensionalModel = false,
                citationIds = citations.add(selected.id, "VISUAL_TARGET", selected.sourceRefs)
            )
        }
        return targets
    }

    private fun resolveRelatedNodes(
        carriers: List<KnowledgeNode>,
        edgeType: KnowledgeEdgeType,
        nodeType: KnowledgeNodeType,
        citations: CitationAccumulator
    ): List<RelatedNode> = carriers.flatMap { carrier ->
        source.outgoingEdges(carrier.id, setOf(edgeType)).mapNotNull { edge ->
            val related = source.node(edge.to)?.takeIf { it.type == nodeType }
                ?: return@mapNotNull null
            RelatedNode(
                node = related,
                authority = authority(edge.reviewState),
                citationIds = citations.add(
                    related.id,
                    nodeType.name,
                    related.sourceRefs + edge.sourceRefs
                )
            )
        }
    }

    private fun requirementsCompleted(
        requirements: List<String>,
        evidence: List<VehicleEvidence>
    ): Boolean {
        if (requirements.isEmpty()) return false
        val completed = evidence.asSequence()
            .filter {
                it.status == VehicleEvidenceStatus.VERIFIED &&
                    it.kind == EvidenceKind.DIAGNOSTIC_CONFIRMATION &&
                    it.assertion in setOf(
                        EvidenceAssertion.PASSED,
                        EvidenceAssertion.MATCHES,
                        EvidenceAssertion.PRESENT
                    )
            }
            .mapNotNull(VehicleEvidence::requirementKey)
            .toSet()
        return requirements.all(completed::contains)
    }

    private fun authority(state: GraphReviewState): RepairKnowledgeAuthority = when (state) {
        GraphReviewState.REVIEWED,
        GraphReviewState.OBSERVED -> RepairKnowledgeAuthority.REVIEWED_GRAPH
        GraphReviewState.REVIEW_REQUIRED,
        GraphReviewState.CONFLICTED -> RepairKnowledgeAuthority.REVIEW_REQUIRED_GRAPH
    }

    private fun invalidGraphBundle(
        request: RepairKnowledgeRequest,
        status: GraphIntegrityStatus
    ) = RepairKnowledgeBundle(
        observations = request.observations.distinctBy(RepairObservation::id)
            .sortedBy(RepairObservation::id),
        dtcs = emptyList(),
        invalidDtcInputs = request.dtcs.distinct().sorted(),
        sourceClaims = emptyList(),
        inferences = emptyList(),
        candidates = emptyList(),
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
            reason = "El grafo automotriz no superó la validación de integridad."
        ),
        visualTargets = emptyList(),
        citations = emptyList(),
        warnings = listOf("Conocimiento estructurado no disponible; se bloquean acciones materiales."),
        insufficientDataReasons = listOf("Integridad del grafo: $status."),
        fallbackUsed = false,
        graphIntegrity = GraphBundleIntegrity(status, null)
    )

    private data class SequenceResolution(
        val tests: List<ConfirmationTest>,
        val nodes: List<KnowledgeNode>
    )

    private data class CandidateSeed(
        val node: KnowledgeNode,
        val priority: Int,
        val reason: String,
        val authority: RepairKnowledgeAuthority,
        val supportingRefs: List<SourceRef>
    )

    private data class RelatedNode(
        val node: KnowledgeNode,
        val authority: RepairKnowledgeAuthority,
        val citationIds: List<String>
    )

    private class CitationAccumulator {
        private val citations = linkedMapOf<String, KnowledgeCitation>()

        fun add(
            carrierId: String,
            carrierKind: String,
            refs: List<SourceRef>
        ): List<String> = refs.distinct().sortedWith(
            compareBy<SourceRef>(SourceRef::sourceDocumentId)
                .thenBy(SourceRef::blockId)
                .thenBy(SourceRef::textHash)
        ).map { ref ->
            val id = "$carrierId|${ref.sourceDocumentId}|${ref.blockId}|${ref.textHash}"
            citations.putIfAbsent(
                id,
                KnowledgeCitation(id, carrierId, carrierKind, ref)
            )
            id
        }

        fun values(): List<KnowledgeCitation> = citations.values.sortedBy(KnowledgeCitation::id)
    }

    private companion object {
        val DTC_PATTERN = Regex("^[PBCU][0-3][0-9A-F]{3}$")
    }
}
