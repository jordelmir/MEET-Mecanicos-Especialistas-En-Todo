package com.elysium.nexus.fabric.healing

import com.elysium.nexus.fabric.canonical.DeviceId

/**
 * §17 Self-Healing Profiles.
 *
 * If an action fails:
 * ```
 * complete profile ≠ invalid
 * ```
 *
 * Process:
 * ```
 * MUTE fails
 * ↓
 * MUTE = REGRESSION
 * ↓
 * other actions remain active
 * ↓
 * recalibrate only MUTE
 * ```
 *
 * The profile stays functional for all working
 * actions while the failing action enters a
 * revalidation cycle.
 */
class SelfHealingProfileManager {

    private val revalidationQueue = mutableMapOf<String, MutableMap<String, RevalidationState>>()

    /**
     * Mark an action as failed in a profile.
     * The action enters revalidation; other
     * actions remain active.
     */
    fun markActionFailed(
        profileId: String,
        action: String,
        reason: String
    ) {
        val profileActions = revalidationQueue.getOrPut(profileId) { mutableMapOf() }
        val existing = profileActions[action]
        profileActions[action] = RevalidationState(
            status = RevalidationStatus.NeedsRevalidation,
            failureCount = (existing?.failureCount ?: 0) + 1,
            lastFailureReason = reason,
            lastFailureAtMs = System.currentTimeMillis(),
            revalidationAttempt = 0
        )
    }

    /**
     * Mark an action as successfully revalidated.
     */
    fun markActionHealed(profileId: String, action: String) {
        val profileActions = revalidationQueue.getOrPut(profileId) { mutableMapOf() }
        profileActions.remove(action)
    }

    /**
     * Get the healing status for a profile.
     */
    fun profileStatus(profileId: String): ProfileHealingStatus {
        val actions = revalidationQueue[profileId] ?: return ProfileHealingStatus(
            profileId = profileId,
            healthyActions = emptyList(),
            failingActions = emptyList(),
            needsAttention = false
        )

        val failing = actions.entries.map { (action, state) ->
            ActionHealingInfo(
                action = action,
                state = state
            )
        }

        return ProfileHealingStatus(
            profileId = profileId,
            healthyActions = emptyList(), // Filled by caller
            failingActions = failing,
            needsAttention = failing.isNotEmpty()
        )
    }

    /**
     * Get actions that need revalidation.
     */
    fun actionsNeedingRevalidation(profileId: String): List<String> {
        val actions = revalidationQueue[profileId] ?: return emptyList()
        return actions.entries
            .filter { it.value.status == RevalidationStatus.NeedsRevalidation }
            .map { it.key }
    }

    /**
     * Check if a profile is healthy (no failing actions).
     */
    fun isHealthy(profileId: String): Boolean {
        val actions = revalidationQueue[profileId] ?: return true
        return actions.isEmpty()
    }

    /**
     * Get the overall health percentage of a profile.
     */
    fun healthPercentage(profileId: String, totalActions: Int): Double {
        if (totalActions == 0) return 1.0
        val failing = revalidationQueue[profileId]?.size ?: 0
        return ((totalActions - failing).toDouble() / totalActions).coerceIn(0.0, 1.0)
    }

    /**
     * Clear all revalidation state for a profile.
     */
    fun clearProfile(profileId: String) {
        revalidationQueue.remove(profileId)
    }
}

data class RevalidationState(
    val status: RevalidationStatus,
    val failureCount: Int,
    val lastFailureReason: String,
    val lastFailureAtMs: Long,
    val revalidationAttempt: Int
)

enum class RevalidationStatus {
    Healthy,
    NeedsRevalidation,
    Revalidating,
    Healed,
    PermanentlyFailed
}

data class ProfileHealingStatus(
    val profileId: String,
    val healthyActions: List<String>,
    val failingActions: List<ActionHealingInfo>,
    val needsAttention: Boolean
)

data class ActionHealingInfo(
    val action: String,
    val state: RevalidationState
)
