package com.elysium369.meet.core.skill

import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §36 — Skill Auto-Generation Engine.
 * Extracted from OpenClaw's procedural memory + skill learning pattern.
 *
 * When the system detects a user performing the same sequence of actions
 * repeatedly, it proposes creating an automated skill (SOP).
 * The user MUST approve — the system never auto-creates skills silently.
 *
 * This bridges OpenClaw's "agent learns recurring execution paths"
 * with ELYSIUM's Learning-to-Earning pipeline.
 */

// ─── Action Sequence Tracking ───

@Serializable
data class ActionRecord(
    val actionId: String,
    val actionType: String,
    val domain: String,
    val parameters: Map<String, String> = emptyMap(),
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val userId: String,
)

@Serializable
data class ActionSequence(
    val sequenceId: String,
    val actions: List<ActionRecord>,
    val occurrenceCount: Int = 1,
    val firstSeenEpochMs: Long = System.currentTimeMillis(),
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
) {
    val length: Int get() = actions.size
}

// ─── Skill Proposal ───

enum class SkillProposalStatus {
    DETECTED,          // Pattern found, not yet proposed to user
    PROPOSED,          // Shown to user for approval
    APPROVED,          // User approved, skill created
    REJECTED,          // User rejected
    AUTO_GENERATED,    // Created from approved proposal
}

@Serializable
data class SkillProposal(
    val proposalId: String,
    val name: String,
    val description: String,
    val triggerPattern: String,
    val steps: List<SkillStep>,
    val sourceSequenceId: String,
    val occurrencesBeforeProposal: Int,
    val status: SkillProposalStatus = SkillProposalStatus.DETECTED,
    val domain: String,
)

@Serializable
data class SkillStep(
    val order: Int,
    val actionType: String,
    val description: String,
    val parameters: Map<String, String> = emptyMap(),
    val isOptional: Boolean = false,
)

// ─── Generated Skill ───

@Serializable
data class GeneratedSkill(
    val skillId: String,
    val name: String,
    val description: String,
    val triggerPattern: String,
    val steps: List<SkillStep>,
    val domain: String,
    val createdFromProposalId: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val executionCount: Int = 0,
    val successRate: Double = 0.0,
)

// ─── Skill Auto-Generation Engine ───

class SkillAutoGenerator {

    private val sequences = mutableListOf<ActionSequence>()
    private val proposals = mutableListOf<SkillProposal>()
    private val skills = mutableListOf<GeneratedSkill>()

    companion object {
        /** Minimum repetitions before proposing a skill */
        const val MIN_OCCURRENCES_FOR_PROPOSAL = 3
        /** Minimum actions in a sequence to be considered a skill */
        const val MIN_SEQUENCE_LENGTH = 2
    }

    /**
     * Records an action and checks for emerging patterns.
     * Returns a SkillProposal if a repeating pattern is detected.
     */
    fun recordAction(action: ActionRecord, recentActions: List<ActionRecord>): SkillProposal? {
        if (recentActions.size < MIN_SEQUENCE_LENGTH) return null

        // Extract action type signature
        val signature = recentActions.map { it.actionType }

        // Check if this signature matches an existing sequence
        val existing = sequences.firstOrNull { seq ->
            seq.actions.map { it.actionType } == signature
        }

        if (existing != null) {
            val updated = existing.copy(
                occurrenceCount = existing.occurrenceCount + 1,
                lastSeenEpochMs = System.currentTimeMillis(),
            )
            sequences.remove(existing)
            sequences.add(updated)

            // Threshold check
            if (updated.occurrenceCount >= MIN_OCCURRENCES_FOR_PROPOSAL) {
                val alreadyProposed = proposals.any { it.sourceSequenceId == updated.sequenceId }
                if (!alreadyProposed) {
                    val proposal = createProposal(updated, recentActions)
                    proposals.add(proposal)
                    return proposal
                }
            }
        } else {
            sequences.add(ActionSequence(
                sequenceId = "seq-${System.currentTimeMillis()}",
                actions = recentActions,
            ))
        }

        return null
    }

    private fun createProposal(
        sequence: ActionSequence,
        actions: List<ActionRecord>,
    ): SkillProposal {
        val steps = actions.mapIndexed { index, action ->
            SkillStep(
                order = index + 1,
                actionType = action.actionType,
                description = "${action.actionType} in ${action.domain}",
                parameters = action.parameters,
            )
        }

        val domain = actions.firstOrNull()?.domain ?: "general"
        val actionNames = actions.map { it.actionType }.joinToString(" → ")

        return SkillProposal(
            proposalId = "proposal-${System.currentTimeMillis()}",
            name = "Auto: $actionNames",
            description = "Detected pattern: $actionNames (seen ${sequence.occurrenceCount} times)",
            triggerPattern = actions.first().actionType,
            steps = steps,
            sourceSequenceId = sequence.sequenceId,
            occurrencesBeforeProposal = sequence.occurrenceCount,
            status = SkillProposalStatus.DETECTED,
            domain = domain,
        )
    }

    /**
     * User approves a proposal → generates a skill.
     */
    fun approveProposal(proposalId: String): GeneratedSkill? {
        val proposal = proposals.firstOrNull { it.proposalId == proposalId } ?: return null

        val updated = proposal.copy(status = SkillProposalStatus.APPROVED)
        proposals.remove(proposal)
        proposals.add(updated)

        val skill = GeneratedSkill(
            skillId = "skill-${System.currentTimeMillis()}",
            name = proposal.name,
            description = proposal.description,
            triggerPattern = proposal.triggerPattern,
            steps = proposal.steps,
            domain = proposal.domain,
            createdFromProposalId = proposalId,
        )
        skills.add(skill)
        return skill
    }

    /**
     * User rejects a proposal.
     */
    fun rejectProposal(proposalId: String): Boolean {
        val proposal = proposals.firstOrNull { it.proposalId == proposalId } ?: return false
        proposals.remove(proposal)
        proposals.add(proposal.copy(status = SkillProposalStatus.REJECTED))
        return true
    }

    val activeProposals: List<SkillProposal>
        get() = proposals.filter { it.status == SkillProposalStatus.DETECTED ||
            it.status == SkillProposalStatus.PROPOSED }

    val generatedSkills: List<GeneratedSkill>
        get() = skills.toList()

    val trackedSequences: List<ActionSequence>
        get() = sequences.toList()
}
