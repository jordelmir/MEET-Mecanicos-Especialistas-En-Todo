package com.elysium369.meet.safety.science.domain

/**
 * Deterministic, Explainable Anomaly Detection Rule: Procurement Concentration.
 *
 * Evaluates whether a disproportionate percentage of tenders or direct contracts
 * from a specific public institution are concentrated in a single vendor entity.
 *
 * Invariants:
 * 1. Anomaly != Crime. A high concentration percentage is never proof of corruption.
 * 2. Mandatory Alternative Hypotheses must accompany every detected signal.
 * 3. Incomplete datasets must return INSUFFICIENT_DATA rather than raising false alarms.
 */
enum class AnomalySignalDisposition {
    CONCENTRATION_SIGNAL_DETECTED,
    NORMAL_DISTRIBUTION,
    INSUFFICIENT_DATA,
}

data class ProcurementConcentrationAssessment(
    val disposition: AnomalySignalDisposition,
    val buyerInstitution: String,
    val vendorTaxId: String,
    val concentrationRatioPercent: Double,
    val vendorAwardsCount: Int,
    val totalInstitutionAwardsCount: Int,
    val totalVendorAmountMinorUnits: Long,
    val deterministicFormulaExplanation: String,
    val mandatoryAlternativeHypotheses: List<String>,
    val eligibleForHumanReview: Boolean,
)

object ProcurementConcentrationRule {

    const val RULE_ID = "RULE_CR_PROCUREMENT_CONCENTRATION_v1"
    const val DEFAULT_CONCENTRATION_THRESHOLD_PERCENT = 70.0
    const val MINIMUM_AWARDS_SAMPLE_THRESHOLD = 3

    private val standardAlternativeHypotheses = listOf(
        "Proveedor exclusivo o fabricante único debidamente acreditado ante la institución",
        "Contratación efectuada al amparo de régimen de emergencia nacional o urgencia calificada",
        "Titularidad de derechos de propiedad intelectual, patente o software propietario incompatible con otros oferentes",
        "Distribución geográfica exclusiva o ausencia de otros oferentes en licitaciones públicas previas",
    )

    /**
     * Assesses award concentration deterministically.
     */
    fun evaluate(
        buyerInstitution: String,
        vendorTaxId: String,
        institutionAwards: List<NormalizedProcurementRecord>,
        thresholdPercent: Double = DEFAULT_CONCENTRATION_THRESHOLD_PERCENT,
    ): ProcurementConcentrationAssessment {
        val trimmedBuyer = buyerInstitution.trim()
        val trimmedVendor = vendorTaxId.trim()

        val relevantAwards = institutionAwards.filter {
            it.rawRecord.buyerInstitution.equals(trimmedBuyer, ignoreCase = true)
        }

        if (relevantAwards.size < MINIMUM_AWARDS_SAMPLE_THRESHOLD) {
            return ProcurementConcentrationAssessment(
                disposition = AnomalySignalDisposition.INSUFFICIENT_DATA,
                buyerInstitution = trimmedBuyer,
                vendorTaxId = trimmedVendor,
                concentrationRatioPercent = 0.0,
                vendorAwardsCount = 0,
                totalInstitutionAwardsCount = relevantAwards.size,
                totalVendorAmountMinorUnits = 0L,
                deterministicFormulaExplanation = "Sample size (${relevantAwards.size}) is below minimum threshold ($MINIMUM_AWARDS_SAMPLE_THRESHOLD)",
                mandatoryAlternativeHypotheses = emptyList(),
                eligibleForHumanReview = false,
            )
        }

        val vendorAwards = relevantAwards.filter {
            it.rawRecord.vendorTaxId.equals(trimmedVendor, ignoreCase = true)
        }

        val totalAmount = vendorAwards.sumOf { it.rawRecord.amountMinorUnits }
        val concentrationRatio = (vendorAwards.size.toDouble() / relevantAwards.size.toDouble()) * 100.0

        val explanation = buildString {
            append("Formula: (vendorAwards / totalBuyerAwards) * 100. ")
            append("Observed: (${vendorAwards.size} / ${relevantAwards.size}) * 100 = ")
            append("%.2f%% against threshold %.2f%%.".format(concentrationRatio, thresholdPercent))
        }

        return if (concentrationRatio >= thresholdPercent) {
            ProcurementConcentrationAssessment(
                disposition = AnomalySignalDisposition.CONCENTRATION_SIGNAL_DETECTED,
                buyerInstitution = trimmedBuyer,
                vendorTaxId = trimmedVendor,
                concentrationRatioPercent = concentrationRatio,
                vendorAwardsCount = vendorAwards.size,
                totalInstitutionAwardsCount = relevantAwards.size,
                totalVendorAmountMinorUnits = totalAmount,
                deterministicFormulaExplanation = explanation,
                mandatoryAlternativeHypotheses = standardAlternativeHypotheses,
                eligibleForHumanReview = true,
            )
        } else {
            ProcurementConcentrationAssessment(
                disposition = AnomalySignalDisposition.NORMAL_DISTRIBUTION,
                buyerInstitution = trimmedBuyer,
                vendorTaxId = trimmedVendor,
                concentrationRatioPercent = concentrationRatio,
                vendorAwardsCount = vendorAwards.size,
                totalInstitutionAwardsCount = relevantAwards.size,
                totalVendorAmountMinorUnits = totalAmount,
                deterministicFormulaExplanation = explanation,
                mandatoryAlternativeHypotheses = emptyList(),
                eligibleForHumanReview = false,
            )
        }
    }
}
