package com.elysium369.meet.core.knowledge.graph

import com.elysium369.meet.core.parts.CompatibilityConfidence
import com.elysium369.meet.core.parts.isValidVin
import java.util.Locale

const val ACCENT_VERNA_2005_REFERENCE_PROFILE_ID =
    "hyundai_accent_verna_2005_1_6_at"

enum class EvidenceKind {
    VIN,
    MARKET,
    ENGINE_CODE,
    TRANSMISSION,
    OEM,
    PHYSICAL_INVENTORY,
    APPROVED_PHYSICAL_MATCH,
    DIAGNOSTIC_CONFIRMATION
}

enum class VehicleEvidenceStatus { VERIFIED, UNVERIFIED }

enum class EvidenceAssertion { PRESENT, ABSENT, MATCHES, PASSED, FAILED, CONFLICTS }

data class ActiveVehicleIdentity(
    val selectedProfileId: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val engine: String? = null,
    val engineCode: String? = null,
    val transmission: String? = null,
    val market: String? = null,
    val vin: String? = null,
    val educationalMode: Boolean = false
)

data class VehicleEvidence(
    val id: String,
    val kind: EvidenceKind,
    val status: VehicleEvidenceStatus,
    val assertion: EvidenceAssertion,
    val subjectCanonicalKey: String? = null,
    val value: String? = null,
    val requirementKey: String? = null
)

enum class ApplicabilityWarningCode {
    CANONICAL_COMPONENT_REQUIRED,
    VEHICLE_IDENTITY_MISSING,
    PROFILE_MISMATCH,
    INVALID_VIN,
    UNVERIFIED_EVIDENCE_IGNORED,
    CONFLICTING_EVIDENCE,
    REVIEW_REQUIRED,
    NEGATIVE_ARCHITECTURE,
    GENERIC_ONLY,
    REPLACEMENT_GATE_MISSING,
    REPLACEMENT_TESTS_REQUIRED,
    COMPONENT_FAILURE_NOT_CONFIRMED,
    EXACT_COMPATIBILITY_REQUIRED
}

data class ApplicabilityWarning(
    val code: ApplicabilityWarningCode,
    val message: String
)

data class VehicleApplicabilityKnowledge(
    val profile: VehicleGraphProfile? = null,
    val rule: VehicleApplicabilityRule? = null,
    val applicabilityEdges: List<KnowledgeEdge> = emptyList(),
    val replacementGateEdges: List<KnowledgeEdge> = emptyList()
)

data class ApplicabilityDecision(
    val state: VehicleApplicabilityState,
    val confidence: GraphConfidence,
    val reason: String,
    val evidenceUsed: List<VehicleEvidence>,
    val missingEvidence: List<EvidenceKind>,
    val missingRequirements: List<String>,
    val warnings: List<ApplicabilityWarning>,
    val educationAllowed: Boolean,
    val diagnosisAllowed: Boolean,
    val replacementAllowed: Boolean,
    val purchaseAllowed: Boolean,
    val purchaseCompatibility: CompatibilityConfidence
)

/**
 * Resolves component applicability without raising graph authority.
 *
 * Text labels and aliases never count as evidence. Vehicle-wide proof must match the active
 * identity, component proof must be bound to the same canonical key, and replacement gates are
 * satisfied only by verified evidence carrying the exact requirement key.
 */
class VehicleApplicabilityResolver {
    fun resolve(
        repository: AutomotiveKnowledgeGraphRepository,
        vehicle: ActiveVehicleIdentity?,
        component: KnowledgeNode,
        evidence: List<VehicleEvidence>
    ): ApplicabilityDecision {
        val candidateProfile = when {
            vehicle == null || vehicle.educationalMode -> null
            !vehicle.selectedProfileId.isNullOrBlank() ->
                repository.profile(vehicle.selectedProfileId)
            else -> repository.profile(ACCENT_VERNA_2005_REFERENCE_PROFILE_ID)
                ?.takeIf { profileMatches(vehicle, it) }
        }
        val canonicalKey = component.canonicalKey
        val knowledge = VehicleApplicabilityKnowledge(
            profile = candidateProfile,
            rule = if (candidateProfile != null && !canonicalKey.isNullOrBlank()) {
                repository.applicabilityRule(candidateProfile.id, canonicalKey)
            } else {
                null
            },
            applicabilityEdges = if (candidateProfile == null) {
                emptyList()
            } else {
                repository.incomingEdges(component.id, setOf(KnowledgeEdgeType.APPLIES_TO))
                    .filter { it.from == candidateProfile.nodeId }
            },
            replacementGateEdges = repository.outgoingEdges(
                component.id,
                setOf(KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE)
            )
        )
        return resolve(vehicle, component, knowledge, evidence)
    }

    fun resolve(
        vehicle: ActiveVehicleIdentity?,
        component: KnowledgeNode,
        knowledge: VehicleApplicabilityKnowledge,
        evidence: List<VehicleEvidence>
    ): ApplicabilityDecision {
        val canonicalKey = component.canonicalKey?.trim().orEmpty()
        if (
            canonicalKey.isBlank() ||
            component.type != KnowledgeNodeType.COMPONENT
        ) {
            return closedDecision(
                reason = "Se requiere un componente canónico; nombres y alias no prueban aplicabilidad.",
                warnings = listOf(
                    warning(ApplicabilityWarningCode.CANONICAL_COMPONENT_REQUIRED)
                )
            )
        }

        val relevantEvidence = evidence.asSequence()
            .filter { it.id.isNotBlank() }
            .filter { isRelevant(it, canonicalKey) }
            .sortedWith(compareBy<VehicleEvidence> { it.kind.ordinal }.thenBy(VehicleEvidence::id))
            .toList()
        val verifiedEvidence = relevantEvidence.filter {
            it.status == VehicleEvidenceStatus.VERIFIED
        }
        val warnings = mutableSetOf<ApplicabilityWarningCode>()
        if (relevantEvidence.any { it.status != VehicleEvidenceStatus.VERIFIED }) {
            warnings += ApplicabilityWarningCode.UNVERIFIED_EVIDENCE_IGNORED
        }

        if (vehicle == null || vehicle.educationalMode) {
            warnings += ApplicabilityWarningCode.VEHICLE_IDENTITY_MISSING
            warnings += ApplicabilityWarningCode.GENERIC_ONLY
            return closedDecision(
                reason = "Sin identidad vehicular activa solo se permite conocimiento educativo genérico.",
                evidenceUsed = verifiedEvidence,
                warnings = warnings.map(::warning)
            )
        }
        if (!vehicle.vin.isNullOrBlank() && !isValidVin(vehicle.vin)) {
            warnings += ApplicabilityWarningCode.INVALID_VIN
        }

        val profile = knowledge.profile
        if (profile == null || !profileMatches(vehicle, profile)) {
            warnings += ApplicabilityWarningCode.PROFILE_MISMATCH
            warnings += ApplicabilityWarningCode.GENERIC_ONLY
            return closedDecision(
                reason = "La identidad activa no cierra un perfil vehicular compatible.",
                evidenceUsed = verifiedEvidence,
                warnings = warnings.map(::warning)
            )
        }

        if (hasMaterialConflict(vehicle, canonicalKey, verifiedEvidence)) {
            warnings += ApplicabilityWarningCode.CONFLICTING_EVIDENCE
            return conflictedDecision(
                reason = "La evidencia verificada contiene afirmaciones incompatibles.",
                evidenceUsed = verifiedEvidence,
                warnings = warnings
            )
        }

        val directPresent = verifiedEvidence.any {
            it.subjectCanonicalKey == canonicalKey &&
                (
                    it.kind == EvidenceKind.PHYSICAL_INVENTORY &&
                        it.assertion == EvidenceAssertion.PRESENT ||
                        it.kind == EvidenceKind.DIAGNOSTIC_CONFIRMATION &&
                        it.assertion in CONFIRMING_ASSERTIONS
                    )
        }
        val directAbsent = verifiedEvidence.any {
            it.subjectCanonicalKey == canonicalKey &&
                it.kind == EvidenceKind.PHYSICAL_INVENTORY &&
                it.assertion == EvidenceAssertion.ABSENT
        }

        val negativeRule = knowledge.rule?.takeIf {
            it.profileId == profile.id &&
                it.canonicalKey == canonicalKey &&
                it.state in NEGATIVE_STATES
        }
        if (negativeRule != null && directPresent) {
            warnings += ApplicabilityWarningCode.CONFLICTING_EVIDENCE
            warnings += ApplicabilityWarningCode.NEGATIVE_ARCHITECTURE
            return conflictedDecision(
                reason = "La presencia observada contradice la arquitectura negativa del perfil.",
                evidenceUsed = verifiedEvidence,
                warnings = warnings
            )
        }
        if (negativeRule != null) {
            warnings += ApplicabilityWarningCode.NEGATIVE_ARCHITECTURE
            if (negativeRule.reviewState != GraphReviewState.REVIEWED) {
                warnings += ApplicabilityWarningCode.REVIEW_REQUIRED
            }
            val state = when {
                directAbsent -> VehicleApplicabilityState.NOT_APPLICABLE
                negativeRule.reviewState == GraphReviewState.REVIEWED -> negativeRule.state
                else -> VehicleApplicabilityState.NOT_DOCUMENTED
            }
            val missing = missingKinds(
                negativeRule.evidenceRequired,
                vehicle,
                canonicalKey,
                verifiedEvidence
            )
            return finalDecision(
                state = state,
                confidence = if (directAbsent) GraphConfidence.HIGH else negativeRule.confidence,
                reason = negativeRule.reason,
                evidenceUsed = verifiedEvidence,
                missingEvidence = missing,
                missingRequirements = emptyList(),
                warnings = warnings,
                replacementAllowed = false,
                purchaseAllowed = false,
                purchaseCompatibility = CompatibilityConfidence.UNKNOWN
            )
        }

        if (directAbsent) {
            warnings += ApplicabilityWarningCode.NEGATIVE_ARCHITECTURE
            return finalDecision(
                state = VehicleApplicabilityState.NOT_APPLICABLE,
                confidence = GraphConfidence.HIGH,
                reason = "Un inventario físico verificado registra el componente como ausente.",
                evidenceUsed = verifiedEvidence,
                missingEvidence = emptyList(),
                missingRequirements = emptyList(),
                warnings = warnings,
                replacementAllowed = false,
                purchaseAllowed = false,
                purchaseCompatibility = CompatibilityConfidence.UNKNOWN
            )
        }

        val matchingApplicabilityEdges = knowledge.applicabilityEdges.asSequence()
            .filter {
                it.type == KnowledgeEdgeType.APPLIES_TO &&
                    it.from == profile.nodeId &&
                    it.to == component.id
            }
            .sortedBy(KnowledgeEdge::id)
            .toList()
        if (
            matchingApplicabilityEdges.map {
                Triple(it.applicability, it.confidence, it.reviewState)
            }.distinct().size > 1
        ) {
            warnings += ApplicabilityWarningCode.CONFLICTING_EVIDENCE
            return conflictedDecision(
                reason = "El grafo contiene relaciones de aplicabilidad incompatibles.",
                evidenceUsed = verifiedEvidence,
                warnings = warnings
            )
        }
        val applicabilityEdge = matchingApplicabilityEdges.firstOrNull()
        var state = when {
            directPresent -> VehicleApplicabilityState.CONFIRMED
            applicabilityEdge != null -> applicabilityEdge.applicability
            else -> VehicleApplicabilityState.GENERIC
        }
        var confidence = when {
            directPresent -> GraphConfidence.HIGH
            applicabilityEdge != null -> applicabilityEdge.confidence
            else -> GraphConfidence.UNASSESSED
        }

        val verifiedVin = hasVerifiedVin(vehicle, verifiedEvidence)
        val verifiedMarket = hasMatchingVehicleEvidence(
            vehicle.market,
            EvidenceKind.MARKET,
            verifiedEvidence
        )
        if (
            !directPresent &&
            state in setOf(
                VehicleApplicabilityState.CONFIRMED,
                VehicleApplicabilityState.PROBABLE
            ) &&
            (!verifiedVin || !verifiedMarket)
        ) {
            state = VehicleApplicabilityState.CONDITIONAL
            confidence = GraphConfidence.MEDIUM
        }
        if (state == VehicleApplicabilityState.GENERIC) {
            warnings += ApplicabilityWarningCode.GENERIC_ONLY
        }

        val graphRequirements = (
            profile.evidenceRequired +
                applicabilityEdge?.evidenceRequired.orEmpty()
            ).distinct()
        val missingEvidence = missingKinds(
            graphRequirements,
            vehicle,
            canonicalKey,
            verifiedEvidence
        ).toMutableSet()
        val gateRequirements = knowledge.replacementGateEdges.asSequence()
            .filter {
                it.type == KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE &&
                    it.from == component.id
            }
            .flatMap(KnowledgeEdge::evidenceRequired)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .toList()
        val satisfiedRequirements = verifiedEvidence.asSequence()
            .filter {
                it.kind == EvidenceKind.DIAGNOSTIC_CONFIRMATION &&
                    it.subjectCanonicalKey == canonicalKey &&
                    it.assertion in CONFIRMING_ASSERTIONS
            }
            .mapNotNull(VehicleEvidence::requirementKey)
            .toSet()
        val missingRequirements = gateRequirements.filterNot(satisfiedRequirements::contains)
            .toMutableList()
        val componentFailureConfirmed = verifiedEvidence.any {
            it.kind == EvidenceKind.DIAGNOSTIC_CONFIRMATION &&
                it.subjectCanonicalKey == canonicalKey &&
                it.assertion in FAILURE_CONFIRMING_ASSERTIONS &&
                it.requirementKey.isNullOrBlank()
        }
        if (gateRequirements.isNotEmpty() && !componentFailureConfirmed) {
            missingRequirements += COMPONENT_FAILURE_CONFIRMATION_REQUIREMENT
        }
        val replacementAllowed =
            state == VehicleApplicabilityState.CONFIRMED &&
                gateRequirements.isNotEmpty() &&
                componentFailureConfirmed &&
                missingRequirements.isEmpty()
        when {
            gateRequirements.isEmpty() ->
                warnings += ApplicabilityWarningCode.REPLACEMENT_GATE_MISSING
            missingRequirements.isNotEmpty() ->
                warnings += ApplicabilityWarningCode.REPLACEMENT_TESTS_REQUIRED
        }
        if (gateRequirements.isNotEmpty() && !componentFailureConfirmed) {
            warnings += ApplicabilityWarningCode.COMPONENT_FAILURE_NOT_CONFIRMED
        }

        val hasBoundOem = verifiedEvidence.any {
            it.kind == EvidenceKind.OEM &&
                it.subjectCanonicalKey == canonicalKey &&
                it.assertion == EvidenceAssertion.MATCHES &&
                !it.value.isNullOrBlank()
        }
        val hasApprovedPhysicalMatch = verifiedEvidence.any {
            it.kind == EvidenceKind.APPROVED_PHYSICAL_MATCH &&
                it.subjectCanonicalKey == canonicalKey &&
                it.assertion == EvidenceAssertion.MATCHES
        }
        val exactCompatibility = hasApprovedPhysicalMatch || verifiedVin && hasBoundOem
        val purchaseAllowed = replacementAllowed && exactCompatibility
        if (!exactCompatibility) {
            warnings += ApplicabilityWarningCode.EXACT_COMPATIBILITY_REQUIRED
            missingEvidence += EvidenceKind.OEM
            if (!verifiedVin) missingEvidence += EvidenceKind.VIN
        } else {
            missingEvidence -= EvidenceKind.OEM
            missingEvidence -= EvidenceKind.VIN
        }
        val purchaseCompatibility = when {
            purchaseAllowed -> CompatibilityConfidence.EXACT
            replacementAllowed && (verifiedVin || hasBoundOem || directPresent) ->
                CompatibilityConfidence.HIGH
            state == VehicleApplicabilityState.CONFIRMED -> CompatibilityConfidence.MEDIUM
            state in setOf(
                VehicleApplicabilityState.PROBABLE,
                VehicleApplicabilityState.CONDITIONAL
            ) -> CompatibilityConfidence.LOW
            else -> CompatibilityConfidence.UNKNOWN
        }

        return finalDecision(
            state = state,
            confidence = confidence,
            reason = applicabilityEdge?.reason
                ?: if (directPresent) {
                    "La presencia del componente está respaldada por evidencia verificada."
                } else {
                    "No existe una relación vehicular específica; el contenido permanece genérico."
                },
            evidenceUsed = verifiedEvidence,
            missingEvidence = missingEvidence,
            missingRequirements = missingRequirements,
            warnings = warnings,
            replacementAllowed = replacementAllowed,
            purchaseAllowed = purchaseAllowed,
            purchaseCompatibility = purchaseCompatibility
        )
    }

    private fun finalDecision(
        state: VehicleApplicabilityState,
        confidence: GraphConfidence,
        reason: String,
        evidenceUsed: List<VehicleEvidence>,
        missingEvidence: Collection<EvidenceKind>,
        missingRequirements: Collection<String>,
        warnings: Collection<ApplicabilityWarningCode>,
        replacementAllowed: Boolean,
        purchaseAllowed: Boolean,
        purchaseCompatibility: CompatibilityConfidence
    ): ApplicabilityDecision {
        val diagnosisAllowed = state in DIAGNOSTIC_STATES
        return ApplicabilityDecision(
            state = state,
            confidence = confidence,
            reason = reason,
            evidenceUsed = evidenceUsed.sortedWith(
                compareBy<VehicleEvidence> { it.kind.ordinal }.thenBy(VehicleEvidence::id)
            ),
            missingEvidence = missingEvidence.distinct().sortedBy(EvidenceKind::ordinal),
            missingRequirements = missingRequirements.distinct().sorted(),
            warnings = warnings.distinct().sortedBy(ApplicabilityWarningCode::ordinal).map(::warning),
            educationAllowed = true,
            diagnosisAllowed = diagnosisAllowed,
            replacementAllowed = replacementAllowed && diagnosisAllowed,
            purchaseAllowed = purchaseAllowed && replacementAllowed && diagnosisAllowed,
            purchaseCompatibility = purchaseCompatibility
        )
    }

    private fun closedDecision(
        reason: String,
        evidenceUsed: List<VehicleEvidence> = emptyList(),
        warnings: List<ApplicabilityWarning>
    ) = ApplicabilityDecision(
        state = VehicleApplicabilityState.GENERIC,
        confidence = GraphConfidence.UNASSESSED,
        reason = reason,
        evidenceUsed = evidenceUsed,
        missingEvidence = emptyList(),
        missingRequirements = emptyList(),
        warnings = warnings.sortedBy { it.code.ordinal },
        educationAllowed = true,
        diagnosisAllowed = false,
        replacementAllowed = false,
        purchaseAllowed = false,
        purchaseCompatibility = CompatibilityConfidence.UNKNOWN
    )

    private fun conflictedDecision(
        reason: String,
        evidenceUsed: List<VehicleEvidence>,
        warnings: Collection<ApplicabilityWarningCode>
    ) = finalDecision(
        state = VehicleApplicabilityState.CONFLICTED,
        confidence = GraphConfidence.LOW,
        reason = reason,
        evidenceUsed = evidenceUsed,
        missingEvidence = emptyList(),
        missingRequirements = emptyList(),
        warnings = warnings,
        replacementAllowed = false,
        purchaseAllowed = false,
        purchaseCompatibility = CompatibilityConfidence.UNKNOWN
    )

    private fun profileMatches(
        vehicle: ActiveVehicleIdentity,
        profile: VehicleGraphProfile
    ): Boolean {
        if (
            !vehicle.selectedProfileId.isNullOrBlank() &&
            vehicle.selectedProfileId != profile.id
        ) {
            return false
        }
        if (!equalsNormalized(vehicle.make, profile.make)) return false
        if (profile.models.none { equalsNormalized(vehicle.model, it) }) return false
        if (vehicle.year != profile.year) return false
        if (!engineMatches(vehicle.engine, profile.engine)) return false
        return transmissionMatches(vehicle.transmission, profile.transmission)
    }

    private fun engineMatches(active: String?, documented: String): Boolean {
        val normalizedActive = normalize(active)
        if (normalizedActive.isBlank()) return false
        val displacement = ENGINE_DISPLACEMENT.find(documented)?.value
            ?.lowercase(Locale.ROOT)
            ?.replace(",", ".")
            ?.replace(" ", "")
            ?.removeSuffix("l")
            ?: return false
        return normalizedActive.replace(" ", "").contains(displacement)
    }

    private fun transmissionMatches(active: String?, documented: String): Boolean {
        val left = normalize(active)
        val right = normalize(documented)
        if (left.isBlank()) return false
        val leftAutomatic = left == "at" || "auto" in left
        val rightAutomatic = right == "at" || "auto" in right
        return leftAutomatic && rightAutomatic || left == right
    }

    private fun hasMaterialConflict(
        vehicle: ActiveVehicleIdentity,
        canonicalKey: String,
        evidence: List<VehicleEvidence>
    ): Boolean {
        if (evidence.map(VehicleEvidence::id).distinct().size != evidence.size) return true
        if (evidence.any { it.assertion == EvidenceAssertion.CONFLICTS }) return true
        val physical = evidence.filter {
            it.kind == EvidenceKind.PHYSICAL_INVENTORY &&
                it.subjectCanonicalKey == canonicalKey
        }.map(VehicleEvidence::assertion).toSet()
        if (
            EvidenceAssertion.PRESENT in physical &&
            EvidenceAssertion.ABSENT in physical
        ) {
            return true
        }
        val identityKinds = listOf(
            EvidenceKind.VIN,
            EvidenceKind.MARKET,
            EvidenceKind.ENGINE_CODE,
            EvidenceKind.TRANSMISSION,
            EvidenceKind.OEM
        )
        if (identityKinds.any { kind ->
                evidence.filter { it.kind == kind }
                    .mapNotNull(VehicleEvidence::value)
                    .map(::normalize)
                    .filter(String::isNotBlank)
                    .distinct()
                    .size > 1
            }
        ) {
            return true
        }
        val vinEvidence = evidence.filter {
            it.kind == EvidenceKind.VIN &&
                it.assertion == EvidenceAssertion.MATCHES &&
                !it.value.isNullOrBlank()
        }
        if (vinEvidence.any {
            normalize(it.value) != normalize(vehicle.vin)
        }) {
            return true
        }
        val activeIdentityValues = listOf(
            EvidenceKind.MARKET to vehicle.market,
            EvidenceKind.ENGINE_CODE to vehicle.engineCode,
            EvidenceKind.TRANSMISSION to vehicle.transmission
        )
        return activeIdentityValues.any { (kind, activeValue) ->
            val normalizedActive = normalize(activeValue)
            normalizedActive.isNotBlank() &&
                evidence.any {
                    it.kind == kind &&
                        it.assertion == EvidenceAssertion.MATCHES &&
                        !it.value.isNullOrBlank() &&
                        normalize(it.value) != normalizedActive
                }
        }
    }

    private fun hasVerifiedVin(
        vehicle: ActiveVehicleIdentity,
        evidence: List<VehicleEvidence>
    ): Boolean =
        isValidVin(vehicle.vin) &&
            evidence.any {
                it.kind == EvidenceKind.VIN &&
                    it.assertion == EvidenceAssertion.MATCHES &&
                    isValidVin(it.value) &&
                    normalize(it.value) == normalize(vehicle.vin)
            }

    private fun hasMatchingVehicleEvidence(
        activeValue: String?,
        kind: EvidenceKind,
        evidence: List<VehicleEvidence>
    ): Boolean {
        val normalizedActive = normalize(activeValue)
        return normalizedActive.isNotBlank() && evidence.any {
            it.kind == kind &&
                it.assertion == EvidenceAssertion.MATCHES &&
                normalize(it.value) == normalizedActive
        }
    }

    private fun missingKinds(
        requirements: Collection<String>,
        vehicle: ActiveVehicleIdentity,
        canonicalKey: String,
        evidence: List<VehicleEvidence>
    ): Set<EvidenceKind> = requirements.asSequence()
        .flatMap { kindsForRequirement(it).asSequence() }
        .filterNot { kind -> hasKind(kind, vehicle, canonicalKey, evidence) }
        .toSortedSet(compareBy(EvidenceKind::ordinal))

    private fun hasKind(
        kind: EvidenceKind,
        vehicle: ActiveVehicleIdentity,
        canonicalKey: String,
        evidence: List<VehicleEvidence>
    ): Boolean = when (kind) {
        EvidenceKind.VIN -> hasVerifiedVin(vehicle, evidence)
        EvidenceKind.MARKET ->
            hasMatchingVehicleEvidence(vehicle.market, EvidenceKind.MARKET, evidence)
        EvidenceKind.ENGINE_CODE ->
            hasMatchingVehicleEvidence(
                vehicle.engineCode,
                EvidenceKind.ENGINE_CODE,
                evidence
            )
        EvidenceKind.TRANSMISSION ->
            hasMatchingVehicleEvidence(
                vehicle.transmission,
                EvidenceKind.TRANSMISSION,
                evidence
            )
        EvidenceKind.OEM -> evidence.any {
            it.kind == kind &&
                it.subjectCanonicalKey == canonicalKey &&
                it.assertion == EvidenceAssertion.MATCHES &&
                !it.value.isNullOrBlank()
        }
        EvidenceKind.PHYSICAL_INVENTORY -> evidence.any {
            it.kind == kind &&
                it.subjectCanonicalKey == canonicalKey &&
                it.assertion in setOf(EvidenceAssertion.PRESENT, EvidenceAssertion.ABSENT)
        }
        EvidenceKind.APPROVED_PHYSICAL_MATCH -> evidence.any {
            it.kind == kind &&
                it.subjectCanonicalKey == canonicalKey &&
                it.assertion == EvidenceAssertion.MATCHES
        }
        EvidenceKind.DIAGNOSTIC_CONFIRMATION -> evidence.any {
            it.kind == kind &&
                it.subjectCanonicalKey == canonicalKey &&
                it.assertion in CONFIRMING_ASSERTIONS
        }
    }

    private fun kindsForRequirement(requirement: String): Set<EvidenceKind> {
        val normalized = normalize(requirement)
        return buildSet {
            if ("vin" in normalized) add(EvidenceKind.VIN)
            if ("market" in normalized) add(EvidenceKind.MARKET)
            if ("oem" in normalized) add(EvidenceKind.OEM)
            if (
                "engine_code" in normalized ||
                "engine_variant" in normalized ||
                normalized == "engine"
            ) {
                add(EvidenceKind.ENGINE_CODE)
            }
            if ("transmission" in normalized) add(EvidenceKind.TRANSMISSION)
            if (listOf(
                "physical",
                "inventory",
                "visual",
                "photo",
                "connector",
                "dimension",
                "throttle"
            ).any(normalized::contains)
            ) {
                add(EvidenceKind.PHYSICAL_INVENTORY)
            }
        }
    }

    private fun isRelevant(evidence: VehicleEvidence, canonicalKey: String): Boolean =
        when (evidence.kind) {
            EvidenceKind.VIN,
            EvidenceKind.MARKET,
            EvidenceKind.ENGINE_CODE,
            EvidenceKind.TRANSMISSION ->
                evidence.subjectCanonicalKey.isNullOrBlank() ||
                    evidence.subjectCanonicalKey == canonicalKey
            else -> evidence.subjectCanonicalKey == canonicalKey
        }

    private fun warning(code: ApplicabilityWarningCode) = ApplicabilityWarning(
        code = code,
        message = when (code) {
            ApplicabilityWarningCode.CANONICAL_COMPONENT_REQUIRED ->
                "Seleccione un componente canónico; un nombre o alias no es evidencia."
            ApplicabilityWarningCode.VEHICLE_IDENTITY_MISSING ->
                "No hay identidad vehicular activa y verificada."
            ApplicabilityWarningCode.PROFILE_MISMATCH ->
                "El perfil seleccionado no coincide con marca, modelo, año, motor y transmisión."
            ApplicabilityWarningCode.INVALID_VIN ->
                "El VIN activo no tiene una estructura válida de 17 caracteres."
            ApplicabilityWarningCode.UNVERIFIED_EVIDENCE_IGNORED ->
                "La evidencia no verificada no elevó la aplicabilidad."
            ApplicabilityWarningCode.CONFLICTING_EVIDENCE ->
                "La evidencia material entra en conflicto; diagnóstico y compra quedan bloqueados."
            ApplicabilityWarningCode.REVIEW_REQUIRED ->
                "La afirmación del perfil requiere revisión o evidencia física adicional."
            ApplicabilityWarningCode.NEGATIVE_ARCHITECTURE ->
                "El perfil no documenta o excluye este componente para el vehículo objetivo."
            ApplicabilityWarningCode.GENERIC_ONLY ->
                "El contenido se muestra solo con autoridad educativa genérica."
            ApplicabilityWarningCode.REPLACEMENT_GATE_MISSING ->
                "No existe una compuerta de pruebas suficiente para autorizar reemplazo."
            ApplicabilityWarningCode.REPLACEMENT_TESTS_REQUIRED ->
                "Faltan pruebas de confirmación antes de reemplazar."
            ApplicabilityWarningCode.COMPONENT_FAILURE_NOT_CONFIRMED ->
                "Aplicabilidad confirmada no significa pieza dañada; falta confirmar la falla."
            ApplicabilityWarningCode.EXACT_COMPATIBILITY_REQUIRED ->
                "La compra exige VIN más OEM vinculados al componente o coincidencia física aprobada."
        }
    )

    private fun equalsNormalized(left: String?, right: String?): Boolean =
        normalize(left) == normalize(right) && normalize(left).isNotBlank()

    private fun normalize(value: String?): String =
        value?.trim()?.lowercase(Locale.ROOT).orEmpty()

    private companion object {
        val ENGINE_DISPLACEMENT = Regex("""\d+(?:[.,]\d+)?\s*[lL]?""")
        val NEGATIVE_STATES = setOf(
            VehicleApplicabilityState.NOT_DOCUMENTED,
            VehicleApplicabilityState.NOT_APPLICABLE
        )
        val DIAGNOSTIC_STATES = setOf(
            VehicleApplicabilityState.CONFIRMED,
            VehicleApplicabilityState.PROBABLE,
            VehicleApplicabilityState.CONDITIONAL
        )
        val CONFIRMING_ASSERTIONS = setOf(
            EvidenceAssertion.PRESENT,
            EvidenceAssertion.MATCHES,
            EvidenceAssertion.PASSED
        )
        val FAILURE_CONFIRMING_ASSERTIONS = setOf(
            EvidenceAssertion.MATCHES,
            EvidenceAssertion.FAILED
        )
        const val COMPONENT_FAILURE_CONFIRMATION_REQUIREMENT =
            "component_failure_confirmation"
    }
}
