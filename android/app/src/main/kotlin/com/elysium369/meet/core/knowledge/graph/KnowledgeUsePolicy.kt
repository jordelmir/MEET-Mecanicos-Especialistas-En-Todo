package com.elysium369.meet.core.knowledge.graph

enum class KnowledgeUsePurpose {
    EDUCATIONAL, DIAGNOSTIC_HINT, GUIDED_DIAGNOSTIC_TEST,
    PART_COMPATIBILITY, OEM_PROCEDURE, ACTIVE_OPERATION,
}

data class KnowledgeUseDecision(val allowed: Boolean, val reason: String)

/** Applicability is purpose-specific; CONDITIONAL never authorizes exact or active work. */
object KnowledgeUsePolicy {
    fun authorize(
        applicability: KnowledgeConstraintDecision,
        purpose: KnowledgeUsePurpose,
        reviewStates: Set<GraphReviewState>,
        hasCitedSource: Boolean,
        diagnosticAuthority: DiagnosticKnowledgeAuthority,
    ): KnowledgeUseDecision {
        if (applicability.state in setOf(KnowledgeConstraintState.NOT_APPLICABLE, KnowledgeConstraintState.CONFLICTED)) {
            return KnowledgeUseDecision(false, "La evidencia vehicular contradice esta regla.")
        }
        return when (purpose) {
            KnowledgeUsePurpose.EDUCATIONAL -> KnowledgeUseDecision(
                applicability.state != KnowledgeConstraintState.NOT_APPLICABLE,
                "Uso educativo; no constituye especificación OEM.",
            )
            KnowledgeUsePurpose.DIAGNOSTIC_HINT -> KnowledgeUseDecision(
                applicability.state in setOf(
                    KnowledgeConstraintState.CONFIRMED,
                    KnowledgeConstraintState.PROBABLE,
                    KnowledgeConstraintState.CONDITIONAL,
                    KnowledgeConstraintState.GENERIC,
                ),
                "Hipótesis permitida con incertidumbre visible y prueba de confirmación.",
            )
            KnowledgeUsePurpose.GUIDED_DIAGNOSTIC_TEST -> reviewed(
                applicability.state in setOf(KnowledgeConstraintState.CONFIRMED, KnowledgeConstraintState.PROBABLE),
                reviewStates,
                hasCitedSource,
                "La prueba guiada exige aplicabilidad probable o confirmada y fuente revisada.",
            )
            KnowledgeUsePurpose.PART_COMPATIBILITY -> reviewed(
                applicability.state == KnowledgeConstraintState.CONFIRMED &&
                    diagnosticAuthority == DiagnosticKnowledgeAuthority.RAW_IDENTITY_EXACT,
                reviewStates,
                hasCitedSource,
                "Compatibilidad exacta exige identidad raw/ECU y aplicabilidad confirmada; validar VIN/OEM.",
            )
            KnowledgeUsePurpose.OEM_PROCEDURE -> reviewed(
                applicability.state == KnowledgeConstraintState.CONFIRMED,
                reviewStates,
                hasCitedSource,
                "Un procedimiento OEM exige aplicabilidad exacta y autoridad revisada.",
            )
            KnowledgeUsePurpose.ACTIVE_OPERATION -> reviewed(
                applicability.state == KnowledgeConstraintState.CONFIRMED &&
                    diagnosticAuthority == DiagnosticKnowledgeAuthority.RAW_IDENTITY_EXACT,
                reviewStates,
                hasCitedSource,
                "La operación activa requiere además un capability pack firmado; conocimiento genérico no autoriza.",
            )
        }
    }

    private fun reviewed(
        applicable: Boolean,
        reviewStates: Set<GraphReviewState>,
        hasCitedSource: Boolean,
        reason: String,
    ): KnowledgeUseDecision {
        val reviewed = reviewStates.isNotEmpty() && reviewStates.all {
            it in setOf(GraphReviewState.REVIEWED, GraphReviewState.OBSERVED)
        }
        return KnowledgeUseDecision(applicable && reviewed && hasCitedSource, reason)
    }
}
