package com.elysium369.meet.core.parts

import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeBundle

data class PartRequestPublicationDecision(
    val allowed: Boolean,
    val reasons: List<String>
)

object PartRequestPublicationPolicy {
    fun evaluate(
        partName: String,
        vehiclePresent: Boolean,
        contactPresent: Boolean,
        graphEvidenceRequired: Boolean,
        compatibility: CompatibilityResult?,
        suggestion: PartSuggestion?,
        knowledge: RepairKnowledgeBundle?,
        canonicalReferenceId: String? = null,
    ): PartRequestPublicationDecision {
        val reasons = linkedSetOf<String>()
        if (partName.isBlank()) reasons += "Nombre de pieza requerido."
        if (!vehiclePresent) reasons += "Vehículo activo requerido."
        if (!contactPresent) reasons += "Contacto requerido."

        val graphExact =
            suggestion?.requestAllowed == true &&
                suggestion.evidenceState == PartSuggestionEvidenceState.PURCHASE_VERIFIED &&
                knowledge?.partGate?.purchaseAllowed == true &&
                knowledge.partGate.componentCanonicalKey == suggestion.canonicalKey
        if (graphEvidenceRequired && !graphExact && canonicalReferenceId.isNullOrBlank()) {
            reasons +=
                "La solicitud DTC/3D requiere componente canónico, pruebas, VIN/OEM y confirmación física."
        }
        if (
            compatibility?.warnings?.any { it.severity == WarningSeverity.BLOCK } == true &&
            !graphExact
        ) {
            reasons += "Existe una advertencia de compatibilidad bloqueante."
        }
        return PartRequestPublicationDecision(
            allowed = reasons.isEmpty(),
            reasons = reasons.toList()
        )
    }
}
