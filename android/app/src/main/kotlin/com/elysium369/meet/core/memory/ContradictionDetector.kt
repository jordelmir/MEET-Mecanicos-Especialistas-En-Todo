package com.elysium369.meet.core.memory

/**
 * ASTRA V6 §17 — Automatic Contradiction Detection.
 * Extracted from OpenClaw's wiki_lint pattern.
 *
 * Detects when a new memory entry contradicts an existing one
 * and manages resolution. The system NEVER silently overwrites —
 * contradictions are surfaced for deterministic resolution.
 */
class ContradictionDetector {

    /**
     * Checks if a new entry contradicts any existing entries.
     * Two entries contradict if they share the same key + namespace + memoryClass
     * but have different values.
     */
    fun detectContradictions(
        newEntry: MemoryEntry,
        existingEntries: List<MemoryEntry>,
    ): List<MemoryContradiction> {
        return existingEntries
            .filter { existing ->
                existing.isActive &&
                    existing.id != newEntry.id &&
                    existing.namespace == newEntry.namespace &&
                    existing.memoryClass == newEntry.memoryClass &&
                    existing.key.equals(newEntry.key, ignoreCase = true) &&
                    existing.value != newEntry.value
            }
            .map { existing ->
                MemoryContradiction(
                    id = "contra-${existing.id}-${newEntry.id}",
                    existingEntryId = existing.id,
                    newEntryId = newEntry.id,
                    resolution = ContradictionResolution.PENDING,
                )
            }
    }

    /**
     * Resolves a contradiction using deterministic rules.
     * Resolution strategy:
     * 1. AUTHORITATIVE always wins over non-authoritative
     * 2. If same confidence, newer wins (unless older is USER_EXPLICIT)
     * 3. USER_EXPLICIT provenance beats AI_INFERRED
     * 4. Otherwise → PENDING (requires user input)
     */
    fun autoResolve(
        contradiction: MemoryContradiction,
        existing: MemoryEntry,
        newer: MemoryEntry,
    ): ContradictionResolution {
        // Rule 1: Authoritative always wins
        if (existing.confidence == MemoryConfidence.AUTHORITATIVE &&
            newer.confidence != MemoryConfidence.AUTHORITATIVE
        ) {
            return ContradictionResolution.OLDER_WINS
        }
        if (newer.confidence == MemoryConfidence.AUTHORITATIVE &&
            existing.confidence != MemoryConfidence.AUTHORITATIVE
        ) {
            return ContradictionResolution.NEWER_WINS
        }

        // Rule 2: USER_EXPLICIT beats AI_INFERRED
        if (existing.provenance == MemoryProvenance.USER_EXPLICIT &&
            newer.provenance == MemoryProvenance.AI_INFERRED
        ) {
            return ContradictionResolution.OLDER_WINS
        }
        if (newer.provenance == MemoryProvenance.USER_EXPLICIT &&
            existing.provenance == MemoryProvenance.AI_INFERRED
        ) {
            return ContradictionResolution.NEWER_WINS
        }

        // Rule 3: Same confidence level → newer wins
        if (existing.confidence == newer.confidence) {
            return ContradictionResolution.NEWER_WINS
        }

        // Rule 4: Higher confidence wins
        if (newer.confidence.ordinal < existing.confidence.ordinal) {
            return ContradictionResolution.NEWER_WINS
        }
        if (existing.confidence.ordinal < newer.confidence.ordinal) {
            return ContradictionResolution.OLDER_WINS
        }

        // Fallback: ambiguous, need user input
        return ContradictionResolution.PENDING
    }

    /**
     * Applies a contradiction resolution to produce the surviving entry.
     * Returns the winning entry and the superseded entry ID.
     */
    fun applyResolution(
        resolution: ContradictionResolution,
        existing: MemoryEntry,
        newer: MemoryEntry,
    ): ResolutionOutcome {
        return when (resolution) {
            ContradictionResolution.NEWER_WINS -> ResolutionOutcome(
                winner = newer.copy(
                    version = newer.version + 1,
                    evidenceRefs = (newer.evidenceRefs + existing.evidenceRefs).distinct(),
                ),
                supersededId = existing.id,
            )
            ContradictionResolution.OLDER_WINS -> ResolutionOutcome(
                winner = existing,
                supersededId = newer.id,
            )
            ContradictionResolution.BOTH_VALID_DIFFERENT_CONTEXT -> ResolutionOutcome(
                winner = existing, // both survive
                supersededId = null,
            )
            ContradictionResolution.USER_RESOLVED -> ResolutionOutcome(
                winner = newer, // assume user picked newer
                supersededId = existing.id,
            )
            ContradictionResolution.PENDING -> ResolutionOutcome(
                winner = existing, // keep existing until resolved
                supersededId = null,
            )
        }
    }
}

data class ResolutionOutcome(
    val winner: MemoryEntry,
    val supersededId: String?,
)
