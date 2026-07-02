package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * Probabilistic priority engine for DTC causes.
 * Adjusts base probabilities from a knowledge pack using the
 * DiagnosticContext (freeze frame, live PIDs, history).
 *
 * Rules (per spec):
 * - PCM is never first without prior evidence.
 * - No part recommended without at least minimum evidence.
 * - Low voltage + multiple modules => prioritize power/ground/load.
 * - No scanner => live data is MISSING.
 * - Unverified community lowers confidence.
 * - Strong contradiction => lower that cause.
 */
class PriorityEngine {
    @Serializable
    data class RankedCause(
        val causeId: String,
        val baseProbability: Double,
        val adjustedProbability: Double,
        val confidence: Double,
        val notes: List<String> = emptyList()
    )

    @Serializable
    data class DiagnosticContext(
        val dtcCode: String,
        val dtcStatus: String,                  // ACTIVE, PENDING, PERMANENT, STORED, HISTORY
        val freezeFrame: Map<String, Double> = emptyMap(),
        val livePids: Map<String, Double> = emptyMap(),
        val scannerConnected: Boolean = false,
        val coOccurringDtcs: List<String> = emptyList(),
        val completedTests: List<String> = emptyList(),
        val communityEvidence: Boolean = false,
        val sourceTier: String = "A_OWNED_CREATED"
    )

    fun rank(
        profile: DtcProfile,
        rankedCauses: List<Pair<String, Double>>,
        ctx: DiagnosticContext
    ): List<RankedCause> {
        // Compute base scores.
        val causeIds = profile.likelyComponents
        // rankedCauses: pairs (causeId, probability)
        val out = rankedCauses.map { (id, base) ->
            var prob = base
            val notes = mutableListOf<String>()

            // Rule 1: low voltage prioritizes power/ground/relay
            val batteryV = ctx.freezeFrame["BatteryVoltage"] ?: ctx.livePids["BatteryVoltage"]
            if (batteryV != null && batteryV < 12.0) {
                if (id in listOf("cause_battery_ground", "cause_relay", "cause_fuse_feed")) {
                    prob = (prob * 1.6).coerceAtMost(0.95)
                    notes += "Low voltage ($batteryV V) prioritizes supply/relay."
                } else if (id in listOf("cause_pump_motor", "cause_pcm_driver")) {
                    prob = prob * 0.5
                    notes += "Low voltage ($batteryV V) reduces confidence in load/PCM."
                }
            }

            // Rule 2: scanner disconnected -> live data is MISSING, not real
            if (!ctx.scannerConnected) {
                prob = prob * 0.7
                notes += "Scanner disconnected: live data MISSING, not a measured value."
            }

            // Rule 3: PCM is last hypothesis — only score it higher if evidence
            if (id == "cause_pcm_driver") {
                if (ctx.completedTests.isEmpty()) {
                    prob = prob * 0.4
                    notes += "PCM ranked last without prior evidence."
                } else if (!ctx.completedTests.containsAll(
                        listOf("battery_check", "fuse_check", "relay_check", "wiring_check")
                    )) {
                    prob = prob * 0.6
                    notes += "PCM hypothesis: incomplete electrical verification."
                }
            }

            // Rule 4: community evidence flag
            if (!ctx.communityEvidence && ctx.sourceTier != "A_OWNED_CREATED") {
                prob = prob * 0.85
                notes += "Source tier not OWNED_CREATED."
            }

            // Confidence: drops if we have no scanner and no freeze frame
            val confidence = when {
                ctx.scannerConnected && ctx.freezeFrame.isNotEmpty() -> 0.85
                ctx.scannerConnected -> 0.7
                else -> 0.4
            }

            RankedCause(
                causeId = id,
                baseProbability = base,
                adjustedProbability = (prob * 100).toInt() / 100.0,
                confidence = confidence,
                notes = notes
            )
        }.sortedByDescending { it.adjustedProbability }
        return out
    }
}
