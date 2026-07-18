package com.elysium369.meet.core.knowledge

import kotlinx.serialization.json.Json
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Imports a KnowledgePack with the 12-phase pipeline required by the spec:
 *
 * 1. read asset
 * 2. validate JSON
 * 3. validate schema (schemaVersion, required fields)
 * 4. validate license (reject H_REJECTED_UNKNOWN_LICENSE)
 * 5. validate nodes (id format, no duplicates, no PII in names)
 * 6. validate edges (from/to must reference existing nodes)
 * 7. validate confidence / source tier distribution
 * 8. reject SourceTier H
 * 9. deduplicate (within-pack and against existing-graph not implemented here;
 *    this layer just produces the validated subset)
 * 10. transactional insert (out of scope; in-memory store for now)
 * 11. FTS update (out of scope here)
 * 12. audit log (out of scope here)
 *
 * The importer returns a PackImportResult describing the outcome.
 * It does not throw for routine validation failures — only for
 * unrecoverable JSON parse errors.
 */
@OptIn(ExperimentalSerializationApi::class)
class KnowledgePackImporter(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }
) {
    fun parse(raw: String): Result<KnowledgePack> = runCatching {
        json.decodeFromString(KnowledgePack.serializer(), raw)
    }

    fun importPack(raw: String): PackImportResult {
        // Phase 2: parse JSON
        val pack = parse(raw).getOrElse { e ->
            return PackImportResult.Rejected(
                packId = "unknown",
                reason = "JSON parse failed: ${e.message}"
            )
        }
        return importPack(pack)
    }

    fun importPack(pack: KnowledgePack): PackImportResult {
        if (pack.schemaVersion < 1) {
            return PackImportResult.Rejected(pack.packId, "schemaVersion < 1")
        }
        if (pack.packId.isBlank() || pack.title.isBlank()) {
            return PackImportResult.Rejected(pack.packId, "Missing required field (packId or title)")
        }

        if (pack.sourcePolicy.tier == SourceTier.H_REJECTED_UNKNOWN_LICENSE) {
            return PackImportResult.Rejected(pack.packId, "Source tier H is rejected")
        }
        if (!pack.sourcePolicy.redistributionAllowed &&
            pack.sourcePolicy.tier != SourceTier.G_EXTERNAL_LINK_ONLY) {
            return PackImportResult.Rejected(
                pack.packId,
                "Non-redistributable content must be marked as external-link-only (tier G)"
            )
        }

        findPromptInjection(pack)?.let { recordId ->
            return PackImportResult.Rejected(
                pack.packId,
                "Prompt-injection content rejected in record $recordId"
            )
        }

        val acceptedNodeIds = HashSet<String>(pack.nodes.size)
        val rejectedNodeIds = mutableListOf<String>()
        for (node in pack.nodes) {
            if (!NODE_ID_REGEX.matches(node.id)) {
                rejectedNodeIds += node.id
                continue
            }
            if (node.type.isBlank() || node.name.isBlank() || node.name.length > 200) {
                rejectedNodeIds += node.id
                continue
            }
            if (containsProhibitedPii(node.name)) {
                rejectedNodeIds += node.id
                continue
            }
            if (node.sourceTier == SourceTier.H_REJECTED_UNKNOWN_LICENSE) {
                rejectedNodeIds += node.id
                continue
            }
            if (node.validationStatus in setOf(
                    ValidationStatus.REJECTED,
                    ValidationStatus.DEPRECATED
                )
            ) {
                rejectedNodeIds += node.id
                continue
            }
            if (!acceptedNodeIds.add(node.id)) {
                rejectedNodeIds += node.id
            }
        }

        val rejectedEdgeIds = mutableListOf<String>()
        val acceptedEdgeIds = HashSet<String>(pack.edges.size)
        var acceptedEdges = 0
        for (edge in pack.edges) {
            if (!NODE_ID_REGEX.matches(edge.id) || !acceptedEdgeIds.add(edge.id)) {
                rejectedEdgeIds += edge.id
                continue
            }
            if (edge.from !in acceptedNodeIds || edge.to !in acceptedNodeIds) {
                rejectedEdgeIds += edge.id
                continue
            }
            if (edge.from == edge.to) {
                rejectedEdgeIds += edge.id
                continue
            }
            if (edge.type.isBlank()) {
                rejectedEdgeIds += edge.id
                continue
            }
            acceptedEdges += 1
        }

        if (acceptedNodeIds.isEmpty() && pack.nodes.isNotEmpty()) {
            return PackImportResult.Rejected(
                pack.packId,
                "No nodes passed structural and validation-status checks"
            )
        }

        validateProfiles(pack.profiles)?.let { reason ->
            return PackImportResult.Rejected(pack.packId, reason)
        }

        val duplicateSourceId = pack.sourceCitations
            .groupingBy { it.sourceId }
            .eachCount()
            .entries
            .firstOrNull { it.key.isBlank() || it.value > 1 }
            ?.key
        if (duplicateSourceId != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Source citation id is blank or duplicated: $duplicateSourceId"
            )
        }
        val invalidSource = pack.sourceCitations.firstOrNull {
            it.title.isBlank() ||
                !SHA256_REGEX.matches(it.contentHash) ||
                it.licenseStatus.isBlank() ||
                it.retrievedAt.isBlank()
        }
        if (invalidSource != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Invalid source citation metadata: ${invalidSource.sourceId}"
            )
        }
        val sourceIds = pack.sourceCitations.map { it.sourceId }.toSet()

        val duplicateVehicleProfileId = pack.vehicleProfiles
            .groupingBy { it.profileId }
            .eachCount()
            .entries
            .firstOrNull { it.key.isBlank() || it.value > 1 }
            ?.key
        if (duplicateVehicleProfileId != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Vehicle profile id is blank or duplicated: $duplicateVehicleProfileId"
            )
        }
        val invalidVehicleProfile = pack.vehicleProfiles.firstOrNull { profile ->
            profile.make.isBlank() ||
                profile.models.isEmpty() ||
                profile.yearStart > profile.yearEnd ||
                profile.sourceCitationIds.any { it !in sourceIds } ||
                (profile.confidence == ConfidenceLevel.VERIFIED && profile.sourceCitationIds.isEmpty())
        }
        if (invalidVehicleProfile != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Invalid vehicle profile: ${invalidVehicleProfile.profileId}"
            )
        }

        val duplicateClaimId = pack.technicalClaims
            .groupingBy { it.claimId }
            .eachCount()
            .entries
            .firstOrNull { it.key.isBlank() || it.value > 1 }
            ?.key
        if (duplicateClaimId != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Technical claim id is blank or duplicated: $duplicateClaimId"
            )
        }
        val invalidClaimReference = pack.technicalClaims.firstOrNull { claim ->
            claim.subjectId !in acceptedNodeIds ||
                (!claim.sourceCitationId.isNullOrBlank() && claim.sourceCitationId !in sourceIds)
        }
        if (invalidClaimReference != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Technical claim has an invalid subject or source reference: ${invalidClaimReference.claimId}"
            )
        }
        val invalidVerifiedClaim = pack.technicalClaims.firstOrNull {
            it.confidence == ConfidenceLevel.VERIFIED &&
                (it.sourceCitationId.isNullOrBlank() || it.sourceCitationId !in sourceIds)
        }
        if (invalidVerifiedClaim != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "VERIFIED claim requires a source citation: ${invalidVerifiedClaim.claimId}"
            )
        }

        val measurementIssue = pack.measurementSpecifications
            .asSequence()
            .flatMap { MeasurementSpecValidator().validate(it).asSequence() }
            .firstOrNull { it.severity == KnowledgeIssueSeverity.BLOCKING }
        if (measurementIssue != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "${measurementIssue.code}: ${measurementIssue.recordId}"
            )
        }
        val claimsById = pack.technicalClaims.associateBy { it.claimId }
        val measurementWithInvalidClaim = pack.measurementSpecifications.firstOrNull { spec ->
            !spec.sourceClaimId.isNullOrBlank() && spec.sourceClaimId !in claimsById
        }
        if (measurementWithInvalidClaim != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Measurement references an unknown source claim: ${measurementWithInvalidClaim.measurementId}"
            )
        }
        val untraceableMeasurement = pack.measurementSpecifications.firstOrNull { spec ->
            spec.verificationStatus == MeasurementVerificationStatus.VERIFIED &&
                claimsById[spec.sourceClaimId]?.confidence != ConfidenceLevel.VERIFIED
        }
        if (untraceableMeasurement != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "VERIFIED measurement requires a VERIFIED source claim: ${untraceableMeasurement.measurementId}"
            )
        }

        val detectedConflicts = KnowledgeConflictDetector().detect(pack.technicalClaims)
        val conflictWithInvalidClaim = pack.knowledgeConflicts.firstOrNull { conflict ->
            conflict.claimIds.isEmpty() || conflict.claimIds.any { it !in claimsById }
        }
        if (conflictWithInvalidClaim != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Knowledge conflict references an unknown claim: ${conflictWithInvalidClaim.conflictId}"
            )
        }
        val declaredConflictClaimSets = pack.knowledgeConflicts.map { it.claimIds.toSet() }.toSet()
        val undeclaredConflict = detectedConflicts.firstOrNull {
            it.claimIds.toSet() !in declaredConflictClaimSets
        }
        if (undeclaredConflict != null) {
            return PackImportResult.Rejected(
                pack.packId,
                "Undeclared knowledge conflict: ${undeclaredConflict.claimIds.joinToString()}"
            )
        }

        return PackImportResult.Success(
            packId = pack.packId,
            nodesAccepted = acceptedNodeIds.size,
            edgesAccepted = acceptedEdges,
            publicationStatus = publicationStatus(pack),
            rejectedNodeIds = rejectedNodeIds,
            rejectedEdgeIds = rejectedEdgeIds
        )
    }

    private fun publicationStatus(pack: KnowledgePack): PackImportResult.PublicationStatus {
        if (pack.sourcePolicy.tier == SourceTier.G_EXTERNAL_LINK_ONLY) {
            return PackImportResult.PublicationStatus.EXTERNAL_ONLY
        }
        val requiresReview =
                pack.sourcePolicy.tier == SourceTier.F_AI_GENERATED_PENDING_REVIEW ||
                pack.nodes.any { it.validationStatus != ValidationStatus.VALIDATED } ||
                (pack.profiles.isNotEmpty() &&
                    pack.nodes.none { it.validationStatus == ValidationStatus.VALIDATED }) ||
                pack.vehicleProfiles.any { it.confidence != ConfidenceLevel.VERIFIED } ||
                pack.sourceCitations.any { it.reviewedBy.isBlank() || it.reviewedAt.isBlank() } ||
                pack.technicalClaims.any { it.confidence != ConfidenceLevel.VERIFIED } ||
                pack.measurementSpecifications.any {
                    it.verificationStatus != MeasurementVerificationStatus.VERIFIED
                } ||
                pack.knowledgeConflicts.any {
                    it.status !in setOf(
                        KnowledgeConflictStatus.RESOLVED,
                        KnowledgeConflictStatus.REJECTED
                    )
                }
        return if (requiresReview) {
            PackImportResult.PublicationStatus.REVIEW_REQUIRED
        } else {
            PackImportResult.PublicationStatus.ACTIVE
        }
    }

    private fun validateProfiles(profiles: List<DtcProfile>): String? {
        val dtcEngine = DtcEngine()
        for (profile in profiles) {
            val normalized = dtcEngine.normalize(profile.code)
            if (normalized !is DtcEngine.NormalizeResult.Valid || normalized.code != profile.code) {
                return "Invalid or non-canonical DTC profile code: ${profile.code}"
            }
            if (profile.rankedCauses.any { it.id.isBlank() || it.probability !in 0.0..1.0 }) {
                return "Invalid ranked cause probability in profile ${profile.code}"
            }
            if (profile.rankedCauses.map { it.id }.distinct().size != profile.rankedCauses.size) {
                return "Duplicate ranked cause id in profile ${profile.code}"
            }
            if (profile.rankedCauses.sumOf { it.probability } > 1.000001) {
                return "Ranked cause probabilities exceed 1.0 in profile ${profile.code}"
            }
        }
        return null
    }

    private fun findPromptInjection(pack: KnowledgePack): String? {
        val textRecords = buildList {
            pack.nodes.forEach { node ->
                add(node.id to node.name)
                add(node.id to node.description)
            }
            pack.profiles.forEach { profile ->
                add(profile.code to profile.description)
                profile.diagnosticSteps.forEach { add(profile.code to it) }
            }
            pack.technicalClaims.forEach { claim ->
                add(claim.claimId to claim.predicate)
                add(claim.claimId to claim.value)
            }
            pack.measurementSpecifications.forEach { spec ->
                add(spec.measurementId to spec.quantityType)
                add(spec.measurementId to spec.measurementCondition)
                add(spec.measurementId to spec.tolerance)
            }
            pack.sourceCitations.forEach { source ->
                add(source.sourceId to source.title)
                add(source.sourceId to source.pageOrSection)
            }
        }
        return textRecords.firstOrNull { (_, text) ->
            PROMPT_INJECTION_PATTERNS.any { it.containsMatchIn(text) }
        }?.first
    }

    private fun containsProhibitedPii(text: String): Boolean =
        EMAIL_REGEX.containsMatchIn(text) ||
            PHONE_REGEX.containsMatchIn(text) ||
            VIN_REGEX.containsMatchIn(text)

    companion object {
        private val NODE_ID_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*$")
        private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
        private val EMAIL_REGEX = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
        private val PHONE_REGEX = Regex("(?<![A-Za-z0-9])(?:\\+?\\d[ .()/-]*){8,15}(?![A-Za-z0-9])")
        private val VIN_REGEX = Regex("(?<![A-Z0-9])(?=[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9]))(?=[A-HJ-NPR-Z0-9]*\\d)[A-HJ-NPR-Z0-9]{17}")
        private val PROMPT_INJECTION_PATTERNS = listOf(
            Regex("ignore\\s+(all\\s+)?(previous|prior|system)\\s+instructions", RegexOption.IGNORE_CASE),
            Regex("reveal\\s+(the\\s+)?system\\s+prompt", RegexOption.IGNORE_CASE),
            Regex("execute\\s+(this\\s+)?(shell|terminal|system)\\s+command", RegexOption.IGNORE_CASE),
            Regex("exfiltrate\\s+(credentials|secrets|data)", RegexOption.IGNORE_CASE),
            Regex("ignora\\s+(todas\\s+)?(las\\s+)?instrucciones", RegexOption.IGNORE_CASE),
            Regex("revela\\s+(el\\s+)?prompt\\s+(del\\s+)?sistema", RegexOption.IGNORE_CASE),
            Regex("ejecuta\\s+(este\\s+)?comando", RegexOption.IGNORE_CASE)
        )
    }
}
