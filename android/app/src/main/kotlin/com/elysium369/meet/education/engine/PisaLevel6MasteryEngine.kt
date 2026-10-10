package com.elysium369.meet.education.engine

import com.elysium369.meet.education.domain.PisaCognitiveProcess
import com.elysium369.meet.education.domain.PisaDomain
import com.elysium369.meet.education.domain.PisaProficiencyLevel
import kotlinx.serialization.Serializable

/**
 * 6-Stage PISA Mastery Continuum per subject.
 */
enum class PisaSubjectStage(
    val stageNumber: Int,
    val pisaLevelEquivalent: PisaProficiencyLevel,
    val titleEs: String,
    val descriptionEs: String,
) {
    STAGE_1_ELEMENTARY(
        stageNumber = 1,
        pisaLevelEquivalent = PisaProficiencyLevel.LEVEL_1A,
        titleEs = "Etapa 1: Alfabetización Elemental",
        descriptionEs = "Reconocimiento y ejecución de instrucciones directas en contextos familiares explícitos.",
    ),
    STAGE_2_OECD_BASELINE(
        stageNumber = 2,
        pisaLevelEquivalent = PisaProficiencyLevel.LEVEL_2,
        titleEs = "Etapa 2: Umbral Funcional OCDE",
        descriptionEs = "Umbral mínimo para la participación cívica y económica en el siglo XXI. Inferencias directas.",
    ),
    STAGE_3_OPERATIONAL(
        stageNumber = 3,
        pisaLevelEquivalent = PisaProficiencyLevel.LEVEL_3,
        titleEs = "Etapa 3: Autonomía Operativa",
        descriptionEs = "Ejecución de procedimientos descritos, selección y conexión de diferentes representaciones.",
    ),
    STAGE_4_RELATIONAL_MODELING(
        stageNumber = 4,
        pisaLevelEquivalent = PisaProficiencyLevel.LEVEL_4,
        titleEs = "Etapa 4: Modelado Relacional Complejo",
        descriptionEs = "Construcción y uso de modelos formales en situaciones complejas del mundo real.",
    ),
    STAGE_5_ADVANCED_STRATEGY(
        stageNumber = 5,
        pisaLevelEquivalent = PisaProficiencyLevel.LEVEL_5,
        titleEs = "Etapa 5: Estrategia y Resolución No Rutinaria",
        descriptionEs = "Manejo de restricciones implícitas, supuestos y diseño de estrategias no estándar.",
    ),
    STAGE_6_PERFECT_MASTERY(
        stageNumber = 6,
        pisaLevelEquivalent = PisaProficiencyLevel.LEVEL_6,
        titleEs = "Etapa 6: Perfección y Excelencia de Clase Mundial",
        descriptionEs = "Puntaje PISA Perfecto (800+ pts). Conceptualización, generalización, perspicacia científica y evaluación epistémica.",
    );
}

/**
 * Subject-specific Level 6 Mastery Specification.
 */
@Serializable
data class SubjectPisaProgressionSpec(
    val domain: PisaDomain,
    val subjectTitleEs: String,
    val currentStage: PisaSubjectStage,
    val targetScore: Int = 800, // Perfect score ceiling
    val completedTransferTasksCount: Int,
    val requiredTransferTasksForLevel6: Int,
    val demonstratedProcesses: List<String>,
    val missingCognitiveProcessesForLevel6: List<String>,
    val isLevel6Achieved: Boolean,
    val masteryRoadmapNextStep: String,
)

/**
 * Engine that orchestrates learning progression in each subject until reaching
 * a perfect PISA score (Level 6 / 800+ points).
 */
object PisaLevel6MasteryEngine {

    /**
     * Evaluates a learner's exact status against the OECD PISA Level 6 Perfection Standard.
     */
    fun evaluateSubjectProgression(
        domain: PisaDomain,
        fundamentalMastery: Double,
        transferDemonstrations: Int,
        activeProcesses: Set<PisaCognitiveProcess>,
    ): SubjectPisaProgressionSpec {
        val requiredProcesses = when (domain) {
            PisaDomain.MATHEMATICAL_LITERACY -> setOf(
                PisaCognitiveProcess.MATH_FORMULATE,
                PisaCognitiveProcess.MATH_EMPLOY,
                PisaCognitiveProcess.MATH_INTERPRET_EVALUATE,
                PisaCognitiveProcess.MATH_REASON,
            )
            PisaDomain.READING_LITERACY -> setOf(
                PisaCognitiveProcess.READING_LOCATE_INFORMATION,
                PisaCognitiveProcess.READING_UNDERSTAND_INTEGRATE,
                PisaCognitiveProcess.READING_EVALUATE_REFLECT,
            )
            PisaDomain.SCIENTIFIC_LITERACY -> setOf(
                PisaCognitiveProcess.SCIENCE_EXPLAIN_PHENOMENA,
                PisaCognitiveProcess.SCIENCE_EVALUATE_DESIGN_ENQUIRY,
                PisaCognitiveProcess.SCIENCE_INTERPRET_DATA_EVIDENCE,
            )
            PisaDomain.CREATIVE_THINKING -> setOf(
                PisaCognitiveProcess.MATH_REASON,
                PisaCognitiveProcess.READING_EVALUATE_REFLECT,
            )
            PisaDomain.LEARNING_IN_DIGITAL_WORLD -> setOf(
                PisaCognitiveProcess.READING_EVALUATE_REFLECT,
                PisaCognitiveProcess.SCIENCE_INTERPRET_DATA_EVIDENCE,
            )
        }

        val requiredTransfers = 5 // Minimum 5 verified independent transfer tasks in novel contexts
        val missingProcesses = requiredProcesses.minus(activeProcesses).map { it.titleEs }

        val stage = when {
            fundamentalMastery >= 0.95 && transferDemonstrations >= requiredTransfers && missingProcesses.isEmpty() -> {
                PisaSubjectStage.STAGE_6_PERFECT_MASTERY
            }
            fundamentalMastery >= 0.85 && transferDemonstrations >= 3 -> {
                PisaSubjectStage.STAGE_5_ADVANCED_STRATEGY
            }
            fundamentalMastery >= 0.75 && transferDemonstrations >= 1 -> {
                PisaSubjectStage.STAGE_4_RELATIONAL_MODELING
            }
            fundamentalMastery >= 0.60 -> {
                PisaSubjectStage.STAGE_3_OPERATIONAL
            }
            fundamentalMastery >= 0.45 -> {
                PisaSubjectStage.STAGE_2_OECD_BASELINE
            }
            else -> {
                PisaSubjectStage.STAGE_1_ELEMENTARY
            }
        }

        val isLevel6 = stage == PisaSubjectStage.STAGE_6_PERFECT_MASTERY

        val nextStep = when (stage) {
            PisaSubjectStage.STAGE_1_ELEMENTARY -> "Consolidar conceptos elementales y vocabulario básico."
            PisaSubjectStage.STAGE_2_OECD_BASELINE -> "Superar el umbral funcional de la OCDE mediante inferencias directas."
            PisaSubjectStage.STAGE_3_OPERATIONAL -> "Integrar representaciones múltiples (tablas, gráficos y fórmulas)."
            PisaSubjectStage.STAGE_4_RELATIONAL_MODELING -> "Modelar problemas reales con restricciones explícitas."
            PisaSubjectStage.STAGE_5_ADVANCED_STRATEGY -> "Resolver situaciones complejas no rutinarias y justificar supuestos implícitos."
            PisaSubjectStage.STAGE_6_PERFECT_MASTERY -> "¡MAESTRÍA PERFECTA ALCANZADA! Dominio autónomo al percentil más alto de la OCDE."
        }

        return SubjectPisaProgressionSpec(
            domain = domain,
            subjectTitleEs = domain.titleEs,
            currentStage = stage,
            targetScore = if (isLevel6) 800 else (stage.stageNumber * 120 + 200),
            completedTransferTasksCount = transferDemonstrations,
            requiredTransferTasksForLevel6 = requiredTransfers,
            demonstratedProcesses = activeProcesses.map { it.titleEs },
            missingCognitiveProcessesForLevel6 = missingProcesses,
            isLevel6Achieved = isLevel6,
            masteryRoadmapNextStep = nextStep,
        )
    }
}
