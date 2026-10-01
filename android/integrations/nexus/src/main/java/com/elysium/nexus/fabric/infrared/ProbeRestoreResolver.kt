package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrCodeSet

/**
 * V06-P3: Outcome of restoring a probe engine to a saved position after process death.
 */
sealed interface ProbeRestoreDecision {
    /** Engine repositioned (or no saved position) — probing can resume safely. */
    data class Ready(val candidate: IrCodeSet?) : ProbeRestoreDecision

    /**
     * The candidate identity at the saved index does not match the saved
     * candidateId — the catalog changed between sessions. Recovery is
     * required; never silently select candidate 0.
     */
    data class RecoveryRequired(
        val expectedId: String?,
        val foundId: String?
    ) : ProbeRestoreDecision
}

/**
 * V06-P3: Pure process-death restore policy.
 *
 * Mirrors the verify-before-resume contract of IrProbeViewModel:
 * 1. Prefer `selectById(savedId)` — exact identity restore.
 * 2. If the ID is gone (catalog changed), fall back to index-based
 *    repositioning, then VERIFY the candidate at that index.
 * 3. Any mismatch → RecoveryRequired (never a silent candidate-0 resume).
 *
 * JVM-testable (no Android deps).
 */
object ProbeRestoreResolver {

    suspend fun resolve(
        engine: ProbeCursor,
        restoreCandidateIndex: Int,
        restoreCandidateId: String?
    ): ProbeRestoreDecision {
        if (restoreCandidateIndex > 0 && restoreCandidateId != null) {
            if (engine.selectById(restoreCandidateId)) {
                return ProbeRestoreDecision.Ready(engine.currentCandidate())
            }
            // ID gone — index fallback
            repeat(restoreCandidateIndex.coerceAtMost(engine.totalCandidates - 1)) {
                engine.nextCandidate()
            }
            val current = engine.currentCandidate()
            if (current?.id != restoreCandidateId) {
                return ProbeRestoreDecision.RecoveryRequired(
                    expectedId = restoreCandidateId,
                    foundId = current?.id
                )
            }
        }
        return ProbeRestoreDecision.Ready(engine.currentCandidate())
    }
}