package com.elysium369.meet.inspections

import com.elysium369.meet.core.domain.SourceAuthority

enum class InspectionType(val displayName: String) {
    PRE_PURCHASE("Peritaje Pre-Compra Forense"),
    DVIR("Inspección Diaria de Seguridad DVIR"),
    EMISSIONS_READINESS("Verificación de Monitores y Emisiones"),
    POST_REPAIR("Inspección Post-Reparación y Control de Calidad"),
    ACCIDENT_DAMAGE("Evaluación de Daños por Siniestro")
}

data class InspectionCheck(
    val checkId: String,
    val title: String,
    val description: String,
    val isMandatory: Boolean = true,
    val isPassed: Boolean? = null,
    val observationNotes: String? = null,
    val photoProofUris: List<String> = emptyList()
)

data class InspectionProtocol(
    val protocolId: String,
    val type: InspectionType,
    val version: String,
    val authority: SourceAuthority,
    val checks: List<InspectionCheck>
)
