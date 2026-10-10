package com.elysium369.meet.education.engine

import com.elysium369.meet.education.domain.PisaCognitiveProcess
import com.elysium369.meet.education.domain.PisaDomain
import com.elysium369.meet.education.domain.PisaProficiencyLevel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * Diagnostic report of an OECD PISA assessment evaluation.
 */
@Serializable
data class PisaEvaluationReport(
    val domain: PisaDomain,
    val estimatedPisaScore: Int, // PISA scale: 200 - 800 (mean 500, SD 100)
    val proficiencyLevel: PisaProficiencyLevel,
    val meetsOecdBaseline: Boolean, // Level >= 2
    val processMastery: Map<String, Double>, // [Process -> Mastery 0.0..1.0]
    val weakestProcess: String?,
    val recommendedIntervention: String,
)

/**
 * OECD PISA Assessment Engine.
 * Translates evidence demonstrations, transfer tasks, and cognitive reasoning into
 * international PISA proficiency scores and diagnostic benchmarks.
 */
object PisaAssessmentEngine {

    const val OECD_BASELINE_SCORE = 482 // Lower bound of Level 2

    /**
     * Estimates international PISA score on the 500-point scale based on:
     * - Fundamental task accuracy (base 0.0..1.0)
     * - Demonstrated transfer task capability (weight 40%)
     * - Multi-source / multi-step reasoning success (weight 30%)
     */
    fun computePisaScore(
        fundamentalMastery: Double,
        transferSuccessRate: Double,
        multiStepReasoningRate: Double,
    ): Int {
        val clampedFund = fundamentalMastery.coerceIn(0.0, 1.0)
        val clampedTrans = transferSuccessRate.coerceIn(0.0, 1.0)
        val clampedMulti = multiStepReasoningRate.coerceIn(0.0, 1.0)

        // Composite proficiency index [0.0 .. 1.0]
        val compositeIndex = (clampedFund * 0.30) + (clampedTrans * 0.45) + (clampedMulti * 0.25)

        // PISA Scale mapping: Index 0.0 -> 250 pts, Index 0.5 -> 500 pts (OECD Mean), Index 1.0 -> 750+ pts (Level 6)
        val rawScore = 250.0 + (compositeIndex * 520.0)
        return min(800, max(200, rawScore.roundToInt()))
    }

    /**
     * Evaluates a learner across PISA cognitive processes and generates an OECD benchmark report.
     */
    fun evaluatePisaProfile(
        domain: PisaDomain,
        processDemonstrations: Map<PisaCognitiveProcess, Pair<Int, Int>>, // [Process -> (successCount, totalAttempts)]
    ): PisaEvaluationReport {
        val processMasteryMap = mutableMapOf<String, Double>()

        var totalSuccess = 0
        var totalAttempts = 0
        var transferSuccess = 0
        var transferAttempts = 0

        processDemonstrations.forEach { (process, stats) ->
            val (success, attempts) = stats
            val rate = if (attempts > 0) success.toDouble() / attempts else 0.0
            processMasteryMap[process.name] = (rate * 1000).roundToInt() / 1000.0

            totalSuccess += success
            totalAttempts += attempts

            // Interpret/Evaluate and Reason count as transfer in PISA
            if (process == PisaCognitiveProcess.MATH_INTERPRET_EVALUATE ||
                process == PisaCognitiveProcess.MATH_REASON ||
                process == PisaCognitiveProcess.READING_EVALUATE_REFLECT ||
                process == PisaCognitiveProcess.SCIENCE_INTERPRET_DATA_EVIDENCE
            ) {
                transferSuccess += success
                transferAttempts += attempts
            }
        }

        val fundamentalRate = if (totalAttempts > 0) totalSuccess.toDouble() / totalAttempts else 0.0
        val transferRate = if (transferAttempts > 0) transferSuccess.toDouble() / transferAttempts else fundamentalRate * 0.70
        val multiStepRate = min(fundamentalRate, transferRate)

        val pisaScore = computePisaScore(fundamentalRate, transferRate, multiStepRate)
        val level = PisaProficiencyLevel.fromScore(pisaScore)
        val meetsBaseline = pisaScore >= OECD_BASELINE_SCORE

        // Find weakest process
        val weakest = processMasteryMap.minByOrNull { it.value }?.key

        val intervention = when {
            level < PisaProficiencyLevel.LEVEL_2 -> {
                "ALERTA OCDE: Por debajo del umbral funcional (Nivel 2). Se requiere intervención socrática intensiva en comprensión directa y eliminación de lagunas en el proceso: $weakest."
            }
            level in PisaProficiencyLevel.LEVEL_2..PisaProficiencyLevel.LEVEL_3 -> {
                "Competencia básica alcanzada. Enfocar práctica en problemas no rutinarios y tareas de formulación matemática/científica en contextos novedosos."
            }
            level in PisaProficiencyLevel.LEVEL_4..PisaProficiencyLevel.LEVEL_5 -> {
                "Alto rendimiento competitivo internacional. Fortalecer modelos abstractos, argumentación crítica y resolución de problemas multi-etapa."
            }
            else -> {
                "Excelencia de Clase Mundial (Nivel 6). Capacidad demostrada para conceptualizar, teorizar e investigar al nivel más alto de la juventud global."
            }
        }

        return PisaEvaluationReport(
            domain = domain,
            estimatedPisaScore = pisaScore,
            proficiencyLevel = level,
            meetsOecdBaseline = meetsBaseline,
            processMastery = processMasteryMap,
            weakestProcess = weakest,
            recommendedIntervention = intervention,
        )
    }
}
