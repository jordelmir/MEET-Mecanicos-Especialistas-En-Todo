package com.elysium.nexus.fabric.infrared

import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus

data class CandidateScore(
    val codeSetId: String,
    val score: Int,
    val rationale: String
)

/**
 * P1-EVIDENCE: Candidate scoring with penalty awareness.
 * Integrates verification status, model match, command completeness,
 * and community penalty data into a single ranking score.
 */
object CandidateScorer {
    /**
     * Score a candidate for ranking in the probe engine.
     * @param candidate The code set to score
     * @param targetModel Optional exact model name for model-match bonus
     * @param penaltyScore Optional penalty score from CandidatePenaltyEntity (higher = worse)
     * @param successCount Optional evidence success count for this code set
     * @param failCount Optional evidence failure count for this code set
     */
    fun scoreCandidate(
        candidate: IrCodeSet,
        targetModel: String? = null,
        penaltyScore: Int = 0,
        successCount: Int = 0,
        failCount: Int = 0
    ): CandidateScore {
        var score = 0
        val reasons = mutableListOf<String>()

        // Model match (highest priority)
        if (!targetModel.isNullOrBlank()) {
            if (candidate.modelPatterns.any { it.equals(targetModel, ignoreCase = true) }) {
                score += 120
                reasons.add("+120 exact model match ($targetModel)")
            } else if (candidate.remoteModels.any { it.equals(targetModel, ignoreCase = true) }) {
                score += 110
                reasons.add("+110 exact remote model match ($targetModel)")
            }
        }

        // Verification status
        when (candidate.verification) {
            VerificationStatus.VERIFIED_LAB -> { score += 50; reasons.add("+50 VERIFIED_LAB") }
            VerificationStatus.VERIFIED_COMMUNITY -> { score += 40; reasons.add("+40 VERIFIED_COMMUNITY") }
            VerificationStatus.SESSION_VERIFIED -> { score += 25; reasons.add("+25 SESSION_VERIFIED") }
            VerificationStatus.PARTIALLY_VERIFIED -> { score += 15; reasons.add("+15 PARTIALLY_VERIFIED") }
            else -> {}
        }

        // Command completeness
        if (candidate.commands.size >= 4) {
            score += 30
            reasons.add("+30 complete remote (${candidate.commands.size} bindings)")
        } else if (candidate.commands.size >= 2) {
            score += 10
            reasons.add("+10 partial remote (${candidate.commands.size} bindings)")
        }

        // P1-EVIDENCE: Community evidence adjustments
        if (successCount > 0) {
            val bonus = (successCount * 15).coerceAtMost(60)
            score += bonus
            reasons.add("+$bonus evidence success×$successCount")
        }
        if (failCount > 0) {
            val penalty = (failCount * 10).coerceAtMost(40)
            score -= penalty
            reasons.add("-$penalty evidence fail×$failCount")
        }

        // P1-PENALTY: Candidate penalty from repeated failures
        if (penaltyScore > 0) {
            val adjusted = penaltyScore.coerceAtMost(80)
            score -= adjusted
            reasons.add("-$adjusted penalty(${penaltyScore})")
        }

        return CandidateScore(candidate.id, score, reasons.joinToString(", "))
    }
}

/**
 * Ranked IR Candidate Probe Engine for physical connection probing.
 *
 * Implements [IrAction.VOLUME_UP] probing to give immediate visual feedback on TV OSD,
 * ranks candidates by [CandidateScorer] scoring (with penalty/evidence awareness),
 * deduplicates candidates by signal fingerprint,
 * and ensures every probe attempt advances to a distinct candidate code set.
 */
class IrProbeEngine(
    rawCandidates: List<IrCodeSet>,
    targetModel: String? = null,
    /** P1-EVIDENCE: Penalty scores per codeSetId from CandidatePenaltyEntity */
    private val penaltyMap: Map<String, Int> = emptyMap(),
    /** P1-EVIDENCE: Success counts per codeSetId from CompatibilityEvidenceEntity */
    private val successMap: Map<String, Int> = emptyMap(),
    /** P1-EVIDENCE: Failure counts per codeSetId from CompatibilityEvidenceEntity */
    private val failMap: Map<String, Int> = emptyMap()
) : ProbeCursor {
    /**
     * Distinct candidate code sets containing a [IrAction.VOLUME_UP] command,
     * deduplicated by signal fingerprint and ordered by [CandidateScorer] ranking score
     * (incorporating penalty and evidence data).
     */
    val candidates: List<IrCodeSet> = rawCandidates
        .filter { IrAction.VOLUME_UP in it.commands }
        .distinctBy { candidate ->
            val signal = candidate.commands.getValue(IrAction.VOLUME_UP)
            fingerprintSignal(signal)
        }
        .sortedByDescending {
            CandidateScorer.scoreCandidate(
                it,
                targetModel,
                penaltyScore = penaltyMap[it.id] ?: 0,
                successCount = successMap[it.id] ?: 0,
                failCount = failMap[it.id] ?: 0
            ).score
        }

    private var currentIndex = 0

    /** V0.6.3 Phase 2: Engine state machine. */
    private var _state: CursorState = CursorState.UNINITIALIZED
    override val state: CursorState get() = _state

    override val totalCandidates: Int get() = candidates.size
    override val currentProbeNumber: Int get() = (currentIndex + 1).coerceAtMost(totalCandidates)
    override val hasMore: Boolean get() = currentIndex < candidates.size

    /**
     * V0.6.3 Phase 2: Initialize eager engine.
     * After this returns Ready, [currentCandidate] MUST NOT be null.
     */
    override suspend fun initialize(): CursorInitResult {
        if (_state == CursorState.READY) return CursorInitResult.Ready(this)
        if (_state == CursorState.EXHAUSTED) return CursorInitResult.NoCandidates

        currentIndex = 0
        if (candidates.isEmpty()) {
            _state = CursorState.EXHAUSTED
            return CursorInitResult.NoCandidates
        }

        _state = CursorState.READY
        return CursorInitResult.Ready(this)
    }

    /** Get the currently selected candidate code set, or null if exhausted. */
    override fun currentCandidate(): IrCodeSet? = candidates.getOrNull(currentIndex)

    /** Advance to the next candidate code set. Returns the candidate consumed or null if exhausted. */
    override suspend fun nextCandidate(): IrCodeSet? {
        if (currentIndex >= candidates.size) {
            _state = CursorState.EXHAUSTED
            return null
        }
        val current = candidates.getOrNull(currentIndex)
        currentIndex++
        if (currentIndex >= candidates.size) {
            _state = CursorState.EXHAUSTED
        }
        return current
    }

    /** Reset state back to beginning. */
    override fun reset() {
        _state = CursorState.UNINITIALIZED
        currentIndex = 0
    }

    /**
     * Re-position the probe on a specific candidate by ID.
     * Used by the auto-sweep: the user confirms the LAST transmitted
     * candidate, so the engine must point at it before verification.
     * Returns false if no candidate matches (position unchanged).
     */
    override fun selectById(candidateId: String): Boolean {
        val idx = candidates.indexOfFirst { it.id == candidateId }
        if (idx < 0) return false
        currentIndex = idx
        return true
    }

    companion object {
        fun fingerprintSignal(signal: IrSignal): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            when (signal) {
                is IrSignal.Raw -> {
                    digest.update("RAW:v1".toByteArray(Charsets.UTF_8))
                    buffer.clear(); buffer.putInt(signal.carrierHz); digest.update(buffer.array())
                    for (us in signal.patternUs) {
                        buffer.clear(); buffer.putInt(us); digest.update(buffer.array())
                    }
                }
                is IrSignal.Encoded -> {
                    digest.update("ENCODED:v1".toByteArray(Charsets.UTF_8))
                    digest.update(signal.protocol.name.toByteArray(Charsets.UTF_8))
                    buffer.clear(); buffer.putInt(signal.carrierHz); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.address); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.subDevice ?: -1); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.command); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.repeats); digest.update(buffer.array())
                    buffer.clear(); buffer.putInt(signal.toggle); digest.update(buffer.array())
                }
            }
            val hex = StringBuilder()
            for (b in digest.digest()) {
                hex.append(String.format("%02x", b))
            }
            return hex.toString()
        }
    }
}
