package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.authority.VerificationLevel
import com.elysium369.meet.data.local.dao.DiagnosticFindingDao
import com.elysium369.meet.data.local.dao.DiagnosticEvidenceDao
import com.elysium369.meet.evidence.causal.CausalEvidenceGraph
import com.elysium369.meet.evidence.causal.CausalEvidenceNode
import com.elysium369.meet.evidence.causal.CausalNodeType
import com.elysium369.meet.evidence.causal.CausalRelationType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 *  V E H I C L E   E V I D E N C E   F A B R I C
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §7
 *
 *  Single unified facade consolidating automotive truth:
 *  - VehicleEvidenceGraph: Deterministic vehicle truth projection.
 *  - CausalEvidenceGraph: Append-only cryptographic causal provenance DAG.
 * ══════════════════════════════════════════════════════════════════════
 */

data class AppendEvidenceCommand(
    val entityId: String,
    val nodeType: CausalNodeType,
    val payloadJson: String,
    val relationToParent: CausalRelationType? = null,
    val parentIds: List<String> = emptyList(),
    val verificationLevel: VerificationLevel = VerificationLevel.PHYSICALLY_VERIFIED,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)

sealed interface EvidenceVerificationResult {
    data class Verified(val proofHash: String, val chainDepth: Int) : EvidenceVerificationResult
    data class Corrupted(val failedNodeId: String, val reason: String) : EvidenceVerificationResult
    data object NodeNotFound : EvidenceVerificationResult
}

interface VehicleEvidenceProjectionProvider {
    suspend fun load(vehicleId: String, bindingId: String): VehicleEvidenceGraphProjectionInput
}

interface VehicleEvidenceFabric {
    suspend fun project(vehicleId: String, bindingId: String): VehicleEvidenceGraph
    suspend fun appendCausalEvidence(command: AppendEvidenceCommand): CausalEvidenceNode
    suspend fun verify(nodeId: String): EvidenceVerificationResult
}

@Singleton
class DefaultVehicleEvidenceFabric @Inject constructor(
    private val projectionProvider: VehicleEvidenceProjectionProvider,
    private val graphRepository: VehicleEvidenceGraphRepository,
    private val causalGraph: CausalEvidenceGraph,
) : VehicleEvidenceFabric {

    override suspend fun project(vehicleId: String, bindingId: String): VehicleEvidenceGraph {
        val input = projectionProvider.load(vehicleId, bindingId)
        return graphRepository.rebuild(input)
    }

    override suspend fun appendCausalEvidence(command: AppendEvidenceCommand): CausalEvidenceNode {
        return causalGraph.appendNode(
            entityId = command.entityId,
            nodeType = command.nodeType,
            payloadJson = command.payloadJson,
            relationToParent = command.relationToParent,
            parentIds = command.parentIds,
            verificationLevel = command.verificationLevel,
            timestampEpochMs = command.timestampEpochMs,
        )
    }

    override suspend fun verify(nodeId: String): EvidenceVerificationResult {
        val node = causalGraph.getNode(nodeId) ?: return EvidenceVerificationResult.NodeNotFound
        val intact = causalGraph.verifyChainIntegrity(nodeId)
        return if (intact) {
            val trace = causalGraph.traceRootCauses(nodeId)
            EvidenceVerificationResult.Verified(
                proofHash = node.proofHash,
                chainDepth = trace.size,
            )
        } else {
            EvidenceVerificationResult.Corrupted(
                failedNodeId = nodeId,
                reason = "Cryptographic proof hash mismatch in ancestral chain",
            )
        }
    }
}
