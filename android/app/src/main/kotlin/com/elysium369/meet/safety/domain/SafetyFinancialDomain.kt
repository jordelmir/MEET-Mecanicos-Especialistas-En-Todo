package com.elysium369.meet.safety.domain

/**
 * Domain contracts for Public-Interest Financial Intelligence & Anomaly Review.
 *
 * Invariant: Visible personal wealth alone is NEVER crime.
 * An anomaly in public procurement or corporate filing is NEVER proof of guilt.
 */
enum class FinancialObservationReviewResult {
    INSUFFICIENT_EVIDENCE,
    ELIGIBLE_FOR_HUMAN_REVIEW,
    DISMISSED_EXPLAINED,
}

data class FinancialObservationReviewAssessment(
    val decision: FinancialObservationReviewResult,
    val explanation: String,
    val isCriminalReferral: Boolean,
    val isPublicAccusation: Boolean,
    val eligibleForReview: Boolean,
)

object FinancialObservationReviewPolicy {

    /**
     * Evaluates an observation report strictly enforcing that visible lifestyle or wealth
     * (luxury vehicles, jewelry, houses) without verified official documentary discrepancy
     * is INSUFFICIENT_EVIDENCE and can NEVER become a criminal referral or public accusation.
     */
    fun evaluate(
        narrative: String,
        hasIndependentDocumentaryEvidence: Boolean,
        hasPublicRegistryMatch: Boolean,
    ): FinancialObservationReviewAssessment {
        val lower = narrative.lowercase()
        val mentionsVisibleWealth = lower.contains("vehículo") ||
            lower.contains("vehiculo") ||
            lower.contains("carro") ||
            lower.contains("oro") ||
            lower.contains("joya") ||
            lower.contains("propiedad") ||
            lower.contains("casa") ||
            lower.contains("lujo") ||
            lower.contains("riqueza") ||
            lower.contains("dinero")

        if (mentionsVisibleWealth && (!hasIndependentDocumentaryEvidence || !hasPublicRegistryMatch)) {
            return FinancialObservationReviewAssessment(
                decision = FinancialObservationReviewResult.INSUFFICIENT_EVIDENCE,
                explanation = "Riqueza visible o posesión de bienes por sí sola no constituye indicio penal. " +
                    "Se rechaza la apertura de investigación o registro de sospecha sin respaldo documental oficial e independiente.",
                isCriminalReferral = false,
                isPublicAccusation = false,
                eligibleForReview = false,
            )
        }

        if (!hasIndependentDocumentaryEvidence) {
            return FinancialObservationReviewAssessment(
                decision = FinancialObservationReviewResult.INSUFFICIENT_EVIDENCE,
                explanation = "Falta corroboración documental independiente en registros públicos oficiales.",
                isCriminalReferral = false,
                isPublicAccusation = false,
                eligibleForReview = false,
            )
        }

        return FinancialObservationReviewAssessment(
            decision = FinancialObservationReviewResult.ELIGIBLE_FOR_HUMAN_REVIEW,
            explanation = "Material respaldado por fuentes oficiales elegible exclusivamente para revisión humana confidencial.",
            isCriminalReferral = false,
            isPublicAccusation = false,
            eligibleForReview = true,
        )
    }
}

/**
 * Public Procurement Contract Award (e.g. SICOP Costa Rica Ley N.° 9986).
 */
data class ProcurementAward(
    val contractId: String,
    val institutionId: String,
    val supplierId: String,
    val amountMinorUnits: Long,
    val currency: String = "CRC",
    val awardDateIso: String,
    val procedureType: String,
)

data class ProcurementConcentrationEvaluation(
    val institutionId: String,
    val supplierId: String,
    val concentrationRatio: Double,
    val isConcentrationFlagged: Boolean,
    val totalInstitutionAmountMinorUnits: Long,
    val supplierAmountMinorUnits: Long,
    val totalAwardsCount: Int,
    val supplierAwardsCount: Int,
    val alternativeExplanations: List<String>,
    val mathematicalFormula: String,
)

object ProcurementConcentrationRule {

    private val STANDARD_ALTERNATIVE_EXPLANATIONS = listOf(
        "Proveedor exclusivo o fabricante único acreditado formalmente",
        "Contratación efectuada bajo régimen de Emergencia Nacional debidamente decretada",
        "Titularidad de derechos de propiedad intelectual, patente o software especializado incompatible",
        "Distribución geográfica exclusiva o ausencia comprobada de oferentes en concursos previos",
    )

    fun evaluate(
        institutionId: String,
        supplierId: String,
        awards: List<ProcurementAward>,
        concentrationThreshold: Double = 0.65,
        hasEmergencyDecree: Boolean = false,
    ): ProcurementConcentrationEvaluation {
        val institutionAwards = awards.filter {
            it.institutionId.equals(institutionId, ignoreCase = true)
        }

        if (institutionAwards.isEmpty()) {
            return ProcurementConcentrationEvaluation(
                institutionId = institutionId,
                supplierId = supplierId,
                concentrationRatio = 0.0,
                isConcentrationFlagged = false,
                totalInstitutionAmountMinorUnits = 0L,
                supplierAmountMinorUnits = 0L,
                totalAwardsCount = 0,
                supplierAwardsCount = 0,
                alternativeExplanations = emptyList(),
                mathematicalFormula = "Sin registros institucionales para evaluar.",
            )
        }

        val totalInstitutionAmount = institutionAwards.sumOf { it.amountMinorUnits }
        val supplierAwards = institutionAwards.filter {
            it.supplierId.equals(supplierId, ignoreCase = true)
        }
        val supplierAmount = supplierAwards.sumOf { it.amountMinorUnits }

        val ratio = if (totalInstitutionAmount > 0L) {
            supplierAmount.toDouble() / totalInstitutionAmount.toDouble()
        } else {
            0.0
        }

        val flagged = ratio >= concentrationThreshold

        val alternatives = mutableListOf<String>()
        if (flagged) {
            alternatives.addAll(STANDARD_ALTERNATIVE_EXPLANATIONS)
            if (hasEmergencyDecree) {
                alternatives.add(0, "Adjudicación realizada bajo Decreto de Emergencia Nacional vigente")
            }
        }

        val formula = "Ratio = ($supplierAmount / $totalInstitutionAmount) = ${"%.2f".format(ratio * 100)}% " +
            "(Umbral: ${"%.2f".format(concentrationThreshold * 100)}%)"

        return ProcurementConcentrationEvaluation(
            institutionId = institutionId,
            supplierId = supplierId,
            concentrationRatio = ratio,
            isConcentrationFlagged = flagged,
            totalInstitutionAmountMinorUnits = totalInstitutionAmount,
            supplierAmountMinorUnits = supplierAmount,
            totalAwardsCount = institutionAwards.size,
            supplierAwardsCount = supplierAwards.size,
            alternativeExplanations = alternatives,
            mathematicalFormula = formula,
        )
    }
}
