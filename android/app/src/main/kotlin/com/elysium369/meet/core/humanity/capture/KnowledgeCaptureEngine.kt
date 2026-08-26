package com.elysium369.meet.core.humanity.capture

import com.elysium369.meet.core.humanity.CapabilityLevel
import com.elysium369.meet.core.humanity.KnowledgeNode
import com.elysium369.meet.core.humanity.KnowledgeSource
import com.elysium369.meet.core.humanity.SafetyLevel
import com.elysium369.meet.core.humanity.TruthState
import com.elysium369.meet.core.humanity.safety.SafetyKernel

enum class KnowledgeCandidateStatus {
    DRAFT_EXTRACTED,
    PENDING_EXPERT_REVIEW,
    REJECTED_SAFETY_VIOLATION,
    EXPERT_APPROVED,
    PUBLISHED,
}

data class KnowledgeExtractionProposal(
    val caseId: String,
    val dtcCode: String,
    val symptoms: String,
    val solutionText: String,
    val vehicleMake: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val authorMechanicId: String,
)

data class KnowledgeExtractionResult(
    val candidateStatus: KnowledgeCandidateStatus,
    val draftNode: KnowledgeNode?,
    val reviewNotes: List<String>,
    val safetyGatePassed: Boolean,
)

object KnowledgeCaptureEngine {

    /**
     * Extracts a structured draft knowledge node candidate from a verified repair case.
     * Enforces the fundamental rule: Real repair cases are never directly published without human expert review.
     */
    fun proposeKnowledgeNodeFromRepairCase(
        proposal: KnowledgeExtractionProposal,
    ): KnowledgeExtractionResult {
        val reviewNotes = mutableListOf<String>()

        // 1. Safety Gate Evaluation
        val safetyDecision = SafetyKernel.evaluateActionSafety(
            actionDescription = "${proposal.dtcCode} ${proposal.solutionText}",
            nominalSafetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            userLevel = CapabilityLevel.L4_GUIDED_PRACTICE,
        )

        if (!safetyDecision.isAllowed) {
            reviewNotes.add("Bloqueo de seguridad: ${safetyDecision.reason}")
            return KnowledgeExtractionResult(
                candidateStatus = KnowledgeCandidateStatus.REJECTED_SAFETY_VIOLATION,
                draftNode = null,
                reviewNotes = reviewNotes,
                safetyGatePassed = false,
            )
        }

        // 2. Build Structured Candidate
        val source = KnowledgeSource(
            id = "src_case_${proposal.caseId}",
            title = "Caso Real Verificado: ${proposal.dtcCode} en ${proposal.vehicleMake} ${proposal.vehicleModel}",
            authorOrPublisher = "Mecánico Autor: ${proposal.authorMechanicId}",
            sourceType = "OBSERVED_CASE",
            citationNote = "Resolución en taller documentada por el autor.",
        )

        val draftNode = KnowledgeNode(
            id = "node.extracted.${proposal.dtcCode.lowercase()}.${proposal.caseId.take(8)}",
            domainId = "domain.automotive.diagnostics",
            title = "Procedimiento Clínico: ${proposal.dtcCode} (${proposal.vehicleMake} ${proposal.vehicleModel})",
            summary = "Resolución de ${proposal.dtcCode} para ${proposal.vehicleMake} ${proposal.vehicleModel} ${proposal.vehicleYear}. Síntomas: ${proposal.symptoms}. Solución: ${proposal.solutionText}",
            safetyLevel = safetyDecision.effectiveSafetyLevel,
            truthState = TruthState.OBSERVED,
            sources = listOf(source),
        )

        reviewNotes.add("Candidato generado con éxito. Requiere firma de perito antes de publicación oficial.")

        return KnowledgeExtractionResult(
            candidateStatus = KnowledgeCandidateStatus.PENDING_EXPERT_REVIEW,
            draftNode = draftNode,
            reviewNotes = reviewNotes,
            safetyGatePassed = true,
        )
    }
}
