package com.elysium369.meet.education.domain

import kotlinx.serialization.Serializable

enum class CurriculumSourceKind {
    CANONICAL_CURRICULUM,
    YEAR_ORIENTATION,
    SCOPE_SEQUENCE,
    OFFICIAL_MONTHLY_DISTRIBUTION,
    ASSESSMENT_SPEC,
    REINFORCEMENT_ONLY,
    SUPPLEMENTAL,
    INSTITUTIONAL_PLAN,
}

enum class CurriculumGranularity {
    OFFICIAL_MONTHLY,
    OFFICIAL_UNIT_SEQUENCE,
    TEACHER_SEQUENCE_REQUIRED,
    ANNUAL_PROGRAM,
    PLAN_SPECIFIC,
    REINFORCEMENT_ONLY,
}

enum class EducationLevel {
    PREESCOLAR,
    I_CICLO,
    II_CICLO,
    III_CICLO,
    DIVERSIFICADA,
    TECNICA,
}

enum class EducationPlan {
    REGULAR,
    BILINGUE,
    INDIGENA,
    TECNICA,
    ADULTOS,
}

enum class EpistemicTruthState {
    AUTHORITATIVE,
    MEASURED,
    DERIVED,
    SIMULATED,
    REPORTED,
    GENERATED,
    UNKNOWN,
}

enum class CognitiveLevel {
    RECONOCIMIENTO,
    COMPRENSION,
    APLICACION,
    ANALISIS,
    TRANSFERENCIA,
}

enum class StageType {
    ETAPA_I_APRENDIZAJE,
    ETAPA_II_APLICACION_MOVILIZACION,
}

@Serializable
data class CurriculumSourceAnchor(
    val sourceId: String,
    val authorityId: String,
    val canonicalTitle: String,
    val sourceLocator: String,
    val documentHash: String,
    val effectiveYear: Int = 2026,
    val sourceKind: CurriculumSourceKind,
    val sourceGranularity: CurriculumGranularity,
    val isOfficial: Boolean = true,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId cannot be blank" }
        require(documentHash.isNotBlank()) { "documentHash required for provenance" }
        // Rule 9 & Gate 67: If not official, source kind cannot be CANONICAL_CURRICULUM or OFFICIAL_MONTHLY_DISTRIBUTION
        if (!isOfficial) {
            require(sourceKind == CurriculumSourceKind.SUPPLEMENTAL || sourceKind == CurriculumSourceKind.REINFORCEMENT_ONLY) {
                "Unofficial source cannot claim CANONICAL_CURRICULUM or OFFICIAL_MONTHLY_DISTRIBUTION"
            }
        }
    }
}

@Serializable
data class CurriculumConcept(
    val id: String,
    val unitId: String,
    val conceptCode: String,
    val title: String,
    val description: String,
    val truthState: EpistemicTruthState = EpistemicTruthState.AUTHORITATIVE,
    val isOfficial: Boolean = true,
    val sourceAnchor: String,
)

@Serializable
data class CurriculumSkill(
    val id: String,
    val conceptId: String,
    val skillCode: String,
    val title: String,
    val stageType: StageType,
    val cognitiveLevel: CognitiveLevel,
    val minimumEvidenceRequired: Int = 3,
    val sourceAnchor: String,
)

@Serializable
data class CurriculumPrerequisite(
    val conceptId: String,
    val prerequisiteConceptId: String,
    val relationshipType: String = "STRICT_PREREQUISITE",
) {
    init {
        require(conceptId != prerequisiteConceptId) { "Concept cannot be prerequisite of itself" }
    }
}
