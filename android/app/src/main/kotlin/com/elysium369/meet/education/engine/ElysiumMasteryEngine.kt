package com.elysium369.meet.education.engine

import com.elysium369.meet.education.domain.ConceptKnowledgeState
import com.elysium369.meet.education.domain.CurriculumConcept
import com.elysium369.meet.education.domain.CurriculumPrerequisite
import com.elysium369.meet.education.domain.FrontierConcept
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object ElysiumMasteryEngine {

    /**
     * Updates the learner's longitudinal concept mastery based on task demonstration.
     * Enforces the constitutional invariant:
     * - Answering correctly without transfer tasks caps mastery at 0.750 max.
     * - Full mastery (> 0.850) strictly requires successful transfer across different contexts.
     */
    fun recordEvidence(
        current: ConceptKnowledgeState,
        isCorrect: Boolean,
        isTransferTask: Boolean,
        misconceptionCode: String? = null,
        todayEpochDay: Long = 0L,
    ): ConceptKnowledgeState {
        val newEvidenceCount = current.evidenceCount + 1
        val updatedMisconceptions = if (misconceptionCode != null && !current.misconceptionCodes.contains(misconceptionCode)) {
            current.misconceptionCodes + misconceptionCode
        } else {
            current.misconceptionCodes
        }

        return if (!isCorrect) {
            val penalizedMastery = max(0.0, current.masteryEstimate - 0.25)
            current.copy(
                masteryEstimate = (penalizedMastery * 1000).toLong() / 1000.0,
                evidenceCount = newEvidenceCount,
                misconceptionCodes = updatedMisconceptions,
                nextReviewEpochDay = todayEpochDay + 1,
            )
        } else {
            val newTransferCount = if (isTransferTask) current.successfulTransferCount + 1 else current.successfulTransferCount
            val newConfidence = min(0.98, current.confidence + 0.08)

            // Mastery increment: capped at 0.75 without transfer
            val candidateMastery = if (isTransferTask) {
                min(1.0, current.masteryEstimate + 0.20)
            } else {
                min(0.75, current.masteryEstimate + 0.12)
            }

            // Spaced interval calculation (days)
            val intervalDays = max(2L, (newEvidenceCount * 2.5).roundToLong())

            current.copy(
                masteryEstimate = (candidateMastery * 1000).toLong() / 1000.0,
                confidence = (newConfidence * 1000).toLong() / 1000.0,
                evidenceCount = newEvidenceCount,
                successfulTransferCount = newTransferCount,
                misconceptionCodes = updatedMisconceptions,
                nextReviewEpochDay = todayEpochDay + intervalDays,
            )
        }
    }

    /**
     * Resolves the Personal Learning Frontier (Target Knowledge Graph - Mastered Concepts)
     * respecting strict prerequisite dependencies.
     */
    fun computeLearningFrontier(
        targetConcepts: List<CurriculumConcept>,
        prerequisites: List<CurriculumPrerequisite>,
        states: Map<String, ConceptKnowledgeState>,
        conceptMonthMap: Map<String, Int> = emptyMap(),
        maxFrontierSize: Int = 5,
    ): List<FrontierConcept> {
        val prereqMap = prerequisites
            .filter { it.relationshipType == "STRICT_PREREQUISITE" }
            .groupBy({ it.conceptId }, { it.prerequisiteConceptId })

        return targetConcepts
            .asSequence()
            .map { concept ->
                val state = states[concept.id] ?: ConceptKnowledgeState(
                    learnerId = "unknown",
                    conceptId = concept.id,
                )
                val targetMonth = conceptMonthMap[concept.id] ?: 1
                val prereqs = prereqMap[concept.id] ?: emptyList()
                val prereqsSatisfied = prereqs.all { prereqId ->
                    val prereqState = states[prereqId]
                    prereqState != null && prereqState.masteryEstimate >= 0.70
                }

                Triple(concept, state, Pair(targetMonth, prereqsSatisfied))
            }
            .filter { (_, state, meta) ->
                val (_, prereqsSatisfied) = meta
                // Only include if not yet mastered and all prerequisites are satisfied
                state.masteryEstimate < 0.85 && prereqsSatisfied
            }
            .sortedWith(
                compareBy<Triple<CurriculumConcept, ConceptKnowledgeState, Pair<Int, Boolean>>> { it.third.first }
                    .thenBy { it.first.id }
            )
            .take(maxFrontierSize)
            .map { (concept, state, meta) ->
                val (targetMonth, _) = meta
                FrontierConcept(
                    conceptId = concept.id,
                    conceptCode = concept.conceptCode,
                    title = concept.title,
                    unitTitle = "Mes $targetMonth",
                    targetMonth = targetMonth,
                    currentMastery = state.masteryEstimate,
                    confidence = state.confidence,
                    evidenceCount = state.evidenceCount,
                    needsTransfer = state.isTransferReady,
                )
            }
            .toList()
    }
}
