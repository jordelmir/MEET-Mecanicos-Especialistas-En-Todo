package com.elysium369.meet.core.diagnostics

enum class DiagnosticSpatialSystem {
    POWERTRAIN_ENGINE,
    TRANSMISSION,
    CHASSIS,
    BRAKES_STEERING,
    BODY_ELECTRICAL,
    RESTRAINTS,
    COMMUNICATION_NETWORK,
    UNIVERSAL,
}

data class DiagnosticSpatialProjection(
    val primarySystem: DiagnosticSpatialSystem,
    val relatedSystems: Set<DiagnosticSpatialSystem>,
    val candidateComponents: List<SpatialCandidateComponent> = emptyList(),
    val signalPaths: List<String> = emptyList(),
    val electricalPaths: List<String> = emptyList(),
    val communicationPaths: List<String> = emptyList(),
    val fluidPaths: List<String> = emptyList(),
    val mechanicalPaths: List<String> = emptyList(),
    val projectionEvidenceScore: Double = 0.0,
    val explanation: String = "Proyección orientativa sin evidencia suficiente.",
    val relationNotice: String =
        "Relación diagnóstica orientativa: no confirma por sí sola una pieza dañada.",
)

data class SpatialCandidateComponent(
    val componentId: String,
    val relationship: String,
    val projectionEvidenceScore: Double,
    val requiredEvidence: String,
)

data class DiagnosticSpatialFindingContext(
    val stableFindingKey: String,
    val displayCode: String,
    val rawDtcIdentity: String,
    val namespace: String,
    val moduleIdentity: String,
    val moduleName: String,
    val failureType: Int? = null,
    val knowledgeRelations: List<SpatialKnowledgeRelation> = emptyList(),
)

data class SpatialKnowledgeRelation(
    val componentId: String,
    val relationship: String,
    val pathType: String,
    val pathDescription: String,
    val evidenceScore: Double,
    val source: String,
    val requiredEvidence: String,
    val sourceReferences: List<String> = emptyList(),
    val reviewState: String = "REVIEW_REQUIRED",
    val applicability: String = "NOT_DOCUMENTED",
    val vehicleConstraints: List<String> = emptyList(),
)

/**
 * Maps a finding to the vehicle system that should be inspected first.
 * It deliberately does not diagnose a failed part: that requires vehicle,
 * circuit and physical-test evidence from the knowledge graph/procedure.
 */
object DtcSpatialResolver {
    fun resolve(code: String?, moduleName: String? = null): DiagnosticSpatialProjection {
        val normalizedCode = code.orEmpty().trim().uppercase()
        val module = moduleName.orEmpty().trim().uppercase()

        val primary = when {
            module.contains("TCM") || module.contains("TRANSM") -> DiagnosticSpatialSystem.TRANSMISSION
            module.contains("ABS") || module.contains("BRAKE") -> DiagnosticSpatialSystem.BRAKES_STEERING
            module.contains("SRS") || module.contains("AIRBAG") -> DiagnosticSpatialSystem.RESTRAINTS
            module.contains("BCM") || module.contains("BODY") -> DiagnosticSpatialSystem.BODY_ELECTRICAL
            normalizedCode.startsWith("P07") || normalizedCode.startsWith("P17") ->
                DiagnosticSpatialSystem.TRANSMISSION
            normalizedCode.startsWith("C") -> DiagnosticSpatialSystem.CHASSIS
            normalizedCode.startsWith("B") -> DiagnosticSpatialSystem.BODY_ELECTRICAL
            normalizedCode.startsWith("U") -> DiagnosticSpatialSystem.COMMUNICATION_NETWORK
            normalizedCode.startsWith("P") -> DiagnosticSpatialSystem.POWERTRAIN_ENGINE
            else -> DiagnosticSpatialSystem.UNIVERSAL
        }

        val related = when (primary) {
            DiagnosticSpatialSystem.COMMUNICATION_NETWORK -> setOf(
                DiagnosticSpatialSystem.BODY_ELECTRICAL,
                DiagnosticSpatialSystem.POWERTRAIN_ENGINE,
            )
            DiagnosticSpatialSystem.RESTRAINTS -> setOf(DiagnosticSpatialSystem.BODY_ELECTRICAL)
            DiagnosticSpatialSystem.CHASSIS -> setOf(DiagnosticSpatialSystem.BRAKES_STEERING)
            else -> emptySet()
        }
        return DiagnosticSpatialProjection(
            primarySystem = primary,
            relatedSystems = related,
            projectionEvidenceScore = if (module.isNotBlank()) 0.72 else if (normalizedCode.isNotBlank()) 0.48 else 0.0,
            explanation = if (module.isNotBlank()) {
                "Sistema priorizado por evidencia del módulo $module y familia del DTC $normalizedCode."
            } else {
                "Sistema aproximado por familia del DTC; falta identidad de ECU para elevar confianza."
            },
        )
    }

    fun resolve(finding: DiagnosticSpatialFindingContext): DiagnosticSpatialProjection {
        val base = resolve(finding.displayCode, finding.moduleName.ifBlank { finding.moduleIdentity })
        val networkPath = finding.knowledgeRelations.filter { it.pathType == "COMMUNICATION" }
            .map { it.pathDescription }
            .ifEmpty { if (base.primarySystem == DiagnosticSpatialSystem.COMMUNICATION_NETWORK) {
            listOf("${finding.moduleIdentity} → bus de comunicación → módulos relacionados")
        } else {
            emptyList()
        } }
        val graphEvidenceScore = finding.knowledgeRelations.maxOfOrNull { it.evidenceScore }
        return base.copy(
            candidateComponents = finding.knowledgeRelations.map { relation ->
                SpatialCandidateComponent(
                    componentId = relation.componentId,
                    relationship = relation.relationship,
                    projectionEvidenceScore = relation.evidenceScore,
                    requiredEvidence = relation.requiredEvidence,
                )
            },
            signalPaths = finding.knowledgeRelations.filter { it.pathType == "SIGNAL" }.map { it.pathDescription },
            electricalPaths = finding.knowledgeRelations.filter { it.pathType == "ELECTRICAL" }.map { it.pathDescription },
            communicationPaths = networkPath,
            fluidPaths = finding.knowledgeRelations.filter { it.pathType == "FLUID" }.map { it.pathDescription },
            mechanicalPaths = finding.knowledgeRelations.filter { it.pathType == "MECHANICAL" }.map { it.pathDescription },
            projectionEvidenceScore = (graphEvidenceScore ?: (base.projectionEvidenceScore + if (finding.rawDtcIdentity.isNotBlank()) 0.12 else 0.0))
                .coerceAtMost(0.9),
            explanation = if (finding.knowledgeRelations.isNotEmpty()) {
                "Proyección trazada desde ${finding.knowledgeRelations.map { it.source }.distinct().joinToString()} " +
                    "para ${finding.stableFindingKey}; son relaciones candidatas, no piezas confirmadas."
            } else {
                "${base.explanation} Identidad estable ${finding.stableFindingKey}; falta una relación de grafo " +
                    "aplicable al vehículo, por lo que la proyección conserva confianza limitada."
            },
        )
    }
}
