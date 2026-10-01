package com.elysium.nexus.fabric.identity

/**
 * V06-P9: Identity merge policy (audit §10).
 *
 * Answers: do two observations describe the SAME physical device?
 *
 * Hard rules:
 * - NEVER merge because of same IP, same model, or same name.
 * - SAME only when both observations carry strong evidence and at least
 *   one evidence (kind + value) matches.
 * - DIFFERENT only when both carry strong evidence and none matches.
 * - AMBIGUOUS when either side lacks strong evidence (absence of
 *   evidence is not proof of difference) — merges are never invented.
 */
sealed interface IdentityMergeResult {
    data object SamePhysicalDevice : IdentityMergeResult
    data object DifferentPhysicalDevice : IdentityMergeResult
    data object Ambiguous : IdentityMergeResult
}

object IdentityMergeEngine {

    fun merge(a: PeerObservation, b: PeerObservation): IdentityMergeResult {
        val aStrong = a.evidence.filter { it.isStrong }
        val bStrong = b.evidence.filter { it.isStrong }

        // Lack of strong evidence on either side → cannot conclude.
        if (aStrong.isEmpty() || bStrong.isEmpty()) return IdentityMergeResult.Ambiguous

        // Strong evidence on both sides: require kind AND value to match.
        val shared = aStrong.any { ae ->
            bStrong.any { be -> be.kind == ae.kind && be.value == ae.value }
        }
        return if (shared) IdentityMergeResult.SamePhysicalDevice
        else IdentityMergeResult.DifferentPhysicalDevice
    }

    /**
     * Merge a stream of observations against the first identity-bearing
     * observation:
     * - any DIFFERENT pair → the full set is DIFFERENT (contradiction
     *   dominates; a single disagreeing observation must not be hidden),
     * - otherwise any AMBIGUOUS pair → AMBIGUOUS,
     * - every pair SAME → SAME.
     */
    fun mergeAll(observations: List<PeerObservation>): IdentityMergeResult {
        if (observations.size < 2) return IdentityMergeResult.Ambiguous
        val first = observations.first()
        var sawAmbiguous = false
        for (next in observations.drop(1)) {
            when (merge(first, next)) {
                IdentityMergeResult.DifferentPhysicalDevice -> return IdentityMergeResult.DifferentPhysicalDevice
                IdentityMergeResult.Ambiguous -> sawAmbiguous = true
                IdentityMergeResult.SamePhysicalDevice -> Unit
            }
        }
        return if (sawAmbiguous) IdentityMergeResult.Ambiguous else IdentityMergeResult.SamePhysicalDevice
    }
}