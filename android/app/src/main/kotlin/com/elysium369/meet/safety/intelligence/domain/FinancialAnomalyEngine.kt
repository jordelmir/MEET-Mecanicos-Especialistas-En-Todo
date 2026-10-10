package com.elysium369.meet.safety.intelligence.domain

import com.elysium369.meet.safety.domain.ProcurementAward
import com.elysium369.meet.safety.domain.ProcurementConcentrationEvaluation
import com.elysium369.meet.safety.domain.ProcurementConcentrationRule

/**
 * Orchestrator for Deterministic Anomaly & Anti-Corruption Analysis.
 *
 * Coordinates:
 * 1. Procurement Concentration (SICOP Ley N.° 9986)
 * 2. Corporate Ownership Cycles & Hidden Control
 * 3. Wealth Non-Criminality Verification Gate
 */
data class MasterAnomalyEvaluationReport(
    val evaluationTimestamp: Long,
    val procurementConcentration: ProcurementConcentrationEvaluation?,
    val corporateOwnershipAssessment: OwnershipAnomalyAssessment?,
    val wealthVerificationAssessment: WealthVerificationAssessment?,
    val totalSignalsDetected: Int,
    val isActionableForHumanReview: Boolean,
    val executiveSummary: String,
)

object FinancialAnomalyEngine {

    fun runFullEvaluation(
        institutionId: String?,
        vendorTaxId: String?,
        awards: List<ProcurementAward>,
        entities: List<EconomicEntity>,
        relationships: List<EntityRelationship>,
        observationText: String? = null,
        sources: List<SourceRecord> = emptyList(),
        hasTaxDiscrepancy: Boolean = false,
    ): MasterAnomalyEvaluationReport {
        var signalsCount = 0

        // 1. Evaluate procurement if data provided
        val procurementEval = if (!institutionId.isNullOrBlank() && !vendorTaxId.isNullOrBlank() && awards.isNotEmpty()) {
            val eval = ProcurementConcentrationRule.evaluate(
                institutionId = institutionId,
                supplierId = vendorTaxId,
                awards = awards,
            )
            if (eval.isConcentrationFlagged) signalsCount++
            eval
        } else {
            null
        }

        // 2. Evaluate corporate ownership graph
        val ownershipEval = if (entities.isNotEmpty() && relationships.isNotEmpty()) {
            val eval = CorporateOwnershipChainRule.evaluate(entities, relationships)
            if (eval.anomalyType != OwnershipAnomalyType.NORMAL_STRUCTURE) signalsCount++
            eval
        } else {
            null
        }

        // 3. Evaluate wealth non-criminality safeguard if observation text provided
        val wealthEval = if (!observationText.isNullOrBlank()) {
            VisibleWealthNonCriminalityRule.evaluate(
                observationText = observationText,
                sourceRecords = sources,
                hasVerifiedAuditOrTaxDiscrepancy = hasTaxDiscrepancy,
            )
        } else {
            null
        }

        val actionable = (procurementEval?.isConcentrationFlagged == true) ||
            (ownershipEval?.requiresHumanAnalystReview == true) ||
            (wealthEval?.eligibleForInstitutionalDossier == true)

        val summary = buildString {
            if (signalsCount == 0) {
                append("No se detectaron anomalías deterministas en el conjunto documental evaluado.")
            } else {
                append("Se detectaron $signalsCount señal(es) determinista(s) que ameritan revisión por analista humano. ")
                if (procurementEval?.isConcentrationFlagged == true) {
                    append("Concentración inusual en contratación pública (${"%.1f".format(procurementEval.concentrationRatio * 100)}%). ")
                }
                if (ownershipEval?.requiresHumanAnalystReview == true) {
                    append("Patrón estructural en el grafo corporativo (${ownershipEval.anomalyType}). ")
                }
                append("Nota: Toda señal va acompañada de explicaciones alternativas legítimas obligatorias.")
            }
        }

        return MasterAnomalyEvaluationReport(
            evaluationTimestamp = System.currentTimeMillis(),
            procurementConcentration = procurementEval,
            corporateOwnershipAssessment = ownershipEval,
            wealthVerificationAssessment = wealthEval,
            totalSignalsDetected = signalsCount,
            isActionableForHumanReview = actionable,
            executiveSummary = summary,
        )
    }
}
