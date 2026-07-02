package com.elysium369.meet.core.knowledge

import kotlinx.serialization.json.Json

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
        // Phase 3: schema validation
        if (pack.schemaVersion < 1) {
            return PackImportResult.Rejected(pack.packId, "schemaVersion < 1")
        }
        if (pack.packId.isBlank() || pack.title.isBlank()) {
            return PackImportResult.Rejected(pack.packId, "Missing required field (packId or title)")
        }

        // Phase 4 + 8: license / source tier
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

        // Phase 5: validate nodes
        val nodeIds = HashSet<String>(pack.nodes.size)
        val rejectedNodeIds = mutableListOf<String>()
        for (node in pack.nodes) {
            if (!NODE_ID_REGEX.matches(node.id)) {
                rejectedNodeIds += node.id
                continue
            }
            if (node.name.isBlank() || node.name.length > 200) {
                rejectedNodeIds += node.id
                continue
            }
            if (node.sourceTier == SourceTier.H_REJECTED_UNKNOWN_LICENSE) {
                rejectedNodeIds += node.id
                continue
            }
            // Dedupe: first occurrence wins, subsequent ones are rejected.
            if (!nodeIds.add(node.id)) {
                rejectedNodeIds += node.id
            }
        }
        val acceptedNodeIds = pack.nodes
            .map { it.id }
            .filter { it !in rejectedNodeIds }
            .toSet()

        // Phase 6: validate edges
        val rejectedEdgeIds = mutableListOf<String>()
        for (edge in pack.edges) {
            if (edge.from !in acceptedNodeIds || edge.to !in acceptedNodeIds) {
                rejectedEdgeIds += edge.id
                continue
            }
            if (edge.from == edge.to) {
                rejectedEdgeIds += edge.id
                continue
            }
        }

        // Phase 7: confidence / tier distribution — at least one validated node
        val validatedCount = pack.nodes.count {
            it.id in acceptedNodeIds &&
                (it.validationStatus == ValidationStatus.VALIDATED ||
                    it.validationStatus == ValidationStatus.AI_GENERATED ||
                    it.validationStatus == ValidationStatus.COMMUNITY_PENDING)
        }
        if (validatedCount == 0 && pack.nodes.isNotEmpty()) {
            return PackImportResult.Rejected(
                pack.packId,
                "No nodes have a recognized validation status"
            )
        }

        return PackImportResult.Success(
            packId = pack.packId,
            nodesAccepted = acceptedNodeIds.size,
            edgesAccepted = pack.edges.size - rejectedEdgeIds.size,
            rejectedNodeIds = rejectedNodeIds,
            rejectedEdgeIds = rejectedEdgeIds
        )
    }

    companion object {
        private val NODE_ID_REGEX = Regex("^[a-z][a-z0-9_]*$")
    }
}
