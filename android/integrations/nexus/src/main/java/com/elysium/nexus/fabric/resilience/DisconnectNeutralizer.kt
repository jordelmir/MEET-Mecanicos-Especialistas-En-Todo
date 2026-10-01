package com.elysium.nexus.fabric.resilience

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction

/**
 * The §38 disconnect neutralizer.
 *
 * When a control session disconnects (gracefully or
 * abruptly), the neutralizer ensures no input remains
 * "stuck". For every action dispatched during the
 * session, the neutralizer tracks the inflight state
 * and generates the inverse (neutral) commands on
 * disconnect.
 *
 * ## Test #38 compliance
 *
 * If any input is stuck after an abrupt disconnect,
 * the change is rejected. The neutralizer is the
 * mechanism that prevents this.
 *
 * ## How it works
 *
 * 1. Every dispatched action is recorded via [trackAction].
 * 2. On disconnect, [neutralize] returns the list of
 *    inverse actions needed to zero-out inflight state.
 * 3. The [ActionDispatcher] sends those neutral actions
 *    through the adapter before releasing the session.
 *
 * ## What "neutral" means per action type
 *
 * - Power: no inverse (power state is a toggle; forcing
 *   an inverse could be worse than leaving it).
 * - Volume: no inverse (volume change is transient).
 * - Media transport: send MediaStop if MediaPlay was
 *   the last media action (release the play lock).
 * - Navigation: no inverse (d-pad presses are momentary).
 * - Climate: no inverse (temperature/mode persist on
 *   the device; inverting could be dangerous).
 * - Custom: no inverse (unknown semantics).
 *
 * The neutralizer is intentionally conservative:
 * it only generates inverses for actions that can
 * leave a continuous effect (held keys, streaming).
 */
class DisconnectNeutralizer {

    /**
     * Inflight state per device. Tracks which actions
     * are "active" (could leave a stuck effect).
     */
    private val inflightState = mutableMapOf<DeviceId, MutableSet<InflightEntry>>()

    /**
     * Track an action as dispatched. The neutralizer
     * records it as inflight if it's a "holdable"
     * action type.
     */
    fun trackAction(action: UniversalAction) {
        val entry = toInflightEntry(action) ?: return
        inflightState
            .getOrPut(action.targetDeviceId) { mutableSetOf() }
            .add(entry)
    }

    /**
     * Clear tracking for a completed action. Called
     * when the natural end of the action occurs
     * (e.g., key released, media stopped by user).
     */
    fun clearAction(action: UniversalAction) {
        val entry = toInflightEntry(action) ?: return
        inflightState[action.targetDeviceId]?.remove(entry)
    }

    /**
     * Generate neutral actions for all inflight state
     * on [deviceId]. Called on disconnect.
     *
     * @return list of actions to dispatch to neutralize
     *   all stuck inputs. Empty if nothing is inflight.
     */
    fun neutralize(deviceId: DeviceId): List<UniversalAction> {
        val entries = inflightState.remove(deviceId) ?: return emptyList()
        return entries.mapNotNull { entry -> entry.toNeutralAction(deviceId) }
    }

    /**
     * Neutralize ALL devices. Called on app shutdown
     * or global disconnect.
     *
     * @return map of device → neutral actions.
     */
    fun neutralizeAll(): Map<DeviceId, List<UniversalAction>> {
        val result = mutableMapOf<DeviceId, List<UniversalAction>>()
        val deviceIds = inflightState.keys.toList()
        for (deviceId in deviceIds) {
            val actions = neutralize(deviceId)
            if (actions.isNotEmpty()) {
                result[deviceId] = actions
            }
        }
        return result
    }

    /**
     * @return the number of inflight entries for [deviceId].
     */
    fun inflightCount(deviceId: DeviceId): Int =
        inflightState[deviceId]?.size ?: 0

    /**
     * @return true if any device has inflight state.
     */
    fun hasInflight(): Boolean =
        inflightState.any { it.value.isNotEmpty() }

    /**
     * Clear all tracking state. Use in tests or
     * after a full neutralization cycle.
     */
    fun reset() {
        inflightState.clear()
    }

    companion object {
        /**
         * Convert a [UniversalAction] to an [InflightEntry]
         * if it's a holdable action. Returns null for
         * momentary actions (d-pad, ok, back, etc.).
         */
        internal fun toInflightEntry(action: UniversalAction): InflightEntry? = when (action) {
            is UniversalAction.MediaPlay -> InflightEntry.MediaPlaying(action.correlationId)
            is UniversalAction.Mute -> InflightEntry.MuteActive(action.correlationId)
            else -> null // Momentary actions don't need neutralization
        }
    }
}

/**
 * Represents an active (holdable) input effect that
 * must be neutralized on disconnect.
 */
sealed class InflightEntry {
    abstract val correlationId: String

    data class MediaPlaying(override val correlationId: String) : InflightEntry() {
        override fun toNeutralAction(deviceId: DeviceId): UniversalAction =
            UniversalAction.MediaStop(targetDeviceId = deviceId)
    }

    data class MuteActive(override val correlationId: String) : InflightEntry() {
        override fun toNeutralAction(deviceId: DeviceId): UniversalAction =
            UniversalAction.Mute(targetDeviceId = deviceId)
    }

    /**
     * Generate the neutral (inverse) action for this
     * inflight entry.
     */
    abstract fun toNeutralAction(deviceId: DeviceId): UniversalAction
}
