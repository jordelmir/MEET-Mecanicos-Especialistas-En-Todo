package com.elysium.nexus.fabric.confidence

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.core.device.EvidenceLevel

/**
 * §16 Confidence Per Action.
 *
 * Not: "This remote works."
 *
 * But:
 * ```
 * POWER          99.8%
 * VOLUME_UP      99.9%
 * VOLUME_DOWN    99.9%
 * MUTE           99.7%
 * HOME           93.1%
 * INPUT          81.4%
 * NETFLIX        51.0%
 * ```
 *
 * The profile must NOT hide low-confidence actions.
 * Confidence is computed from:
 * - Evidence level of the signal
 * - Number of successful uses
 * - Number of failures
 * - Time since last verification
 * - Whether the action has been calibrated
 * - Community evidence
 */
class ActionConfidenceTracker {

    private val records = mutableMapOf<ActionConfidenceKey, ActionConfidenceRecord>()

    /**
     * Record a successful action execution.
     */
    fun recordSuccess(
        deviceId: DeviceId,
        action: UniversalAction,
        evidenceLevel: EvidenceLevel,
        latencyMs: Long
    ) {
        val key = key(deviceId, action)
        val existing = records[key] ?: ActionConfidenceRecord()
        records[key] = existing.copy(
            successCount = existing.successCount + 1,
            lastSuccessAtMs = System.currentTimeMillis(),
            lastEvidenceLevel = evidenceLevel,
            averageLatencyMs = if (existing.successCount == 0L) {
                latencyMs.toDouble()
            } else {
                (existing.averageLatencyMs * existing.successCount + latencyMs) / (existing.successCount + 1)
            }
        )
    }

    /**
     * Record a failed action execution.
     */
    fun recordFailure(
        deviceId: DeviceId,
        action: UniversalAction,
        reason: String
    ) {
        val key = key(deviceId, action)
        val existing = records[key] ?: ActionConfidenceRecord()
        records[key] = existing.copy(
            failureCount = existing.failureCount + 1,
            lastFailureAtMs = System.currentTimeMillis(),
            lastFailureReason = reason
        )
    }

    /**
     * Get confidence for a specific action on a device.
     * Returns a value in [0.0, 1.0].
     */
    fun confidence(
        deviceId: DeviceId,
        action: UniversalAction
    ): Double {
        val key = key(deviceId, action)
        val record = records[key] ?: return 0.0
        return record.confidence()
    }

    /**
     * Get confidence for all actions on a device.
     */
    fun confidenceMap(deviceId: DeviceId): Map<String, Double> {
        return records.entries
            .filter { it.key.deviceId == deviceId }
            .associate { (key, record) ->
                key.actionName to record.confidence()
            }
    }

    /**
     * Get the confidence summary for display.
     */
    fun summary(deviceId: DeviceId): ConfidenceSummary {
        val map = confidenceMap(deviceId)
        if (map.isEmpty()) return ConfidenceSummary(deviceId, emptyMap(), 0.0)

        val overall = map.values.average()
        return ConfidenceSummary(
            deviceId = deviceId,
            actionConfidences = map,
            overallConfidence = overall
        )
    }

    /**
     * Mark an action as needing revalidation.
     */
    fun markNeedsRevalidation(
        deviceId: DeviceId,
        action: UniversalAction
    ) {
        val key = key(deviceId, action)
        val existing = records[key] ?: ActionConfidenceRecord()
        records[key] = existing.copy(needsRevalidation = true)
    }

    /**
     * Clear all records for a device.
     */
    fun clearDevice(deviceId: DeviceId) {
        records.keys.removeAll { it.deviceId == deviceId }
    }

    private fun key(deviceId: DeviceId, action: UniversalAction): ActionConfidenceKey {
        return ActionConfidenceKey(
            deviceId = deviceId,
            actionName = action::class.simpleName ?: "Unknown"
        )
    }
}

data class ActionConfidenceKey(
    val deviceId: DeviceId,
    val actionName: String
)

data class ActionConfidenceRecord(
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val lastSuccessAtMs: Long = 0L,
    val lastFailureAtMs: Long = 0L,
    val lastEvidenceLevel: EvidenceLevel = EvidenceLevel.INTERNAL_UNVERIFIED,
    val lastFailureReason: String? = null,
    val averageLatencyMs: Double = 0.0,
    val needsRevalidation: Boolean = false
) {
    fun confidence(): Double {
        if (successCount == 0L && failureCount == 0L) return 0.0
        val total = successCount + failureCount
        val baseRate = successCount.toDouble() / total

        // Evidence level bonus (0.0 to 0.3)
        val evidenceBonus = lastEvidenceLevel.tier.toDouble() / 30.0

        // Recency penalty
        val hoursSinceLastSuccess = (System.currentTimeMillis() - lastSuccessAtMs) / 3_600_000.0
        val recencyPenalty = when {
            hoursSinceLastSuccess < 24 -> 0.0
            hoursSinceLastSuccess < 168 -> 0.05
            hoursSinceLastSuccess < 720 -> 0.1
            else -> 0.2
        }

        // Failure streak penalty
        val failurePenalty = if (failureCount > successCount) 0.2 else 0.0

        // Revalidation penalty
        val revalPenalty = if (needsRevalidation) 0.1 else 0.0

        return (baseRate + evidenceBonus - recencyPenalty - failurePenalty - revalPenalty)
            .coerceIn(0.0, 1.0)
    }
}

data class ConfidenceSummary(
    val deviceId: DeviceId,
    val actionConfidences: Map<String, Double>,
    val overallConfidence: Double
) {
    /**
     * Actions with confidence below threshold.
     */
    fun lowConfidenceActions(threshold: Double = 0.7): List<Pair<String, Double>> {
        return actionConfidences.entries
            .filter { it.value < threshold }
            .sortedBy { it.value }
            .map { it.key to it.value }
    }
}
