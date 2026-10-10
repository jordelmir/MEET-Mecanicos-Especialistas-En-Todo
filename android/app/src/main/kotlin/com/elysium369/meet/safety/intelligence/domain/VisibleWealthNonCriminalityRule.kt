package com.elysium369.meet.safety.intelligence.domain

/**
 * Invariant Rule: Visible Personal Wealth Alone is NEVER Evidence of Illegality.
 *
 * Operational Charter & Ethics:
 * "Que un vecino llegue con cuatro vehículos, oro o una propiedad costosa no demuestra ningún delito.
 * Puede tener ingresos legítimos, patrimonio familiar o una empresa. El problema investigable surge
 * cuando existen discrepancias documentadas, vínculos empresariales relevantes, transacciones sospechosas,
 * contratos públicos irregulares u otras evidencias independientes."
 *
 * The platform:
 * 1. Forbids targeting individuals solely for perceived wealth or lifestyle.
 * 2. Rejects generating suspicious-activity dossiers on neighbors without official documentary discrepancy.
 * 3. Never produces automated criminality scores.
 */
enum class WealthVerificationDisposition {
    REJECTED_LIFESTYLE_SURVEILLANCE_WITHOUT_OFFICIAL_DISCREPANCY,
    INSUFFICIENT_EVIDENCE_FOR_INVESTIGATION,
    LAWFULLY_DOCUMENTED_DISCREPANCY_ELIGIBLE_FOR_HUMAN_AUDIT,
}

data class WealthVerificationAssessment(
    val disposition: WealthVerificationDisposition,
    val explanation: String,
    val isAutomatedSurveillanceRejected: Boolean,
    val eligibleForInstitutionalDossier: Boolean,
)

object VisibleWealthNonCriminalityRule {

    private val WEALTH_INDICATOR_KEYWORDS = listOf(
        "vehículo de lujo",
        "vehiculo de lujo",
        "carro de lujo",
        "carros caros",
        "propiedad costosa",
        "mansion",
        "mansión",
        "cadena de oro",
        "joyas",
        "reloj caro",
        "estilo de vida ostentoso",
        "mucho dinero",
        "dinero en efectivo",
        "parece narco",
        "se viste caro",
    )

    fun evaluate(
        observationText: String,
        sourceRecords: List<SourceRecord>,
        hasVerifiedAuditOrTaxDiscrepancy: Boolean,
    ): WealthVerificationAssessment {
        val lowerText = observationText.lowercase()
        val containsWealthKeywords = WEALTH_INDICATOR_KEYWORDS.any { lowerText.contains(it) }

        val hasOfficialLawfulSources = sourceRecords.any {
            it.isVerified && (
                it.sourceType == SourceRecordType.PUBLIC_PROCUREMENT_SICOP ||
                    it.sourceType == SourceRecordType.NATIONAL_CORPORATE_REGISTRY ||
                    it.sourceType == SourceRecordType.OFFICIAL_AUDIT_REPORT ||
                    it.sourceType == SourceRecordType.JUDICIAL_DECISION ||
                    it.sourceType == SourceRecordType.REGULATORY_SANCTION
                )
        }

        // If it's an observation based on visible wealth/lifestyle without official discrepancy:
        if (containsWealthKeywords && (!hasOfficialLawfulSources || !hasVerifiedAuditOrTaxDiscrepancy)) {
            return WealthVerificationAssessment(
                disposition = WealthVerificationDisposition.REJECTED_LIFESTYLE_SURVEILLANCE_WITHOUT_OFFICIAL_DISCREPANCY,
                explanation = "Principio Constitucional: La tenencia de bienes, vehículos o riqueza visible es lícita " +
                    "y por sí sola no genera sospecha penal ni habilita la creación de perfiles investigativos. " +
                    "El sistema rechaza la vigilancia ciudadana basada en estilos de vida.",
                isAutomatedSurveillanceRejected = true,
                eligibleForInstitutionalDossier = false,
            )
        }

        if (!hasOfficialLawfulSources) {
            return WealthVerificationAssessment(
                disposition = WealthVerificationDisposition.INSUFFICIENT_EVIDENCE_FOR_INVESTIGATION,
                explanation = "Falta respaldo documental en registros oficiales con procedencia jurídica verificable.",
                isAutomatedSurveillanceRejected = false,
                eligibleForInstitutionalDossier = false,
            )
        }

        // Both official sources and documented discrepancy are present:
        return WealthVerificationAssessment(
            disposition = WealthVerificationDisposition.LAWFULLY_DOCUMENTED_DISCREPANCY_ELIGIBLE_FOR_HUMAN_AUDIT,
            explanation = "Discrepancia documentada en registros oficiales independientes. " +
                "Elegible exclusivamente para revisión confidencial por analistas humanos.",
            isAutomatedSurveillanceRejected = false,
            eligibleForInstitutionalDossier = true,
        )
    }
}
