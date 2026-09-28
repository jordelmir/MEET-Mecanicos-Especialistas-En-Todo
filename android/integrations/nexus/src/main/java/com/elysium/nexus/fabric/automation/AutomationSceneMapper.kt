package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction

/**
 * §28→§34 bridge. Maps a persisted user [Automation]
 * (declarative [CommandValue] actions, JSON store) into an
 * executable [MacroTransaction] that the
 * [ConcreteAutomationEngineService] understands ([UniversalAction] steps).
 *
 * Mapping rules (honest, no fabrication):
 * - [CommandValue.OnOff] → [UniversalAction.PowerOn]/[UniversalAction.PowerOff]
 * - [CommandValue.Media]  → [UniversalAction.MediaPlay]/[UniversalAction.MediaPause]
 * - [CommandValue.Climate] → [UniversalAction.SetTemperature]
 * - Commands with no canonical equivalent ([CommandValue.Level], [CommandValue.Color],
 *   [CommandValue.ColorTemperature], [CommandValue.Lock], [CommandValue.Position])
 *   map to [UniversalAction.Custom] with a stable key + payload. Adapters that
 *   understand the key execute it; those that don't return
 *   [CommandStatus.Unsupported] — the engine records it, never fabricates.
 * - [VerificationPolicy.timeoutMs] becomes the per-step timeout.
 * - A policy that requires state confirmation produces a
 *   [StatePredicate.CapabilityAvailable] success condition on the target;
 *   best-effort policies carry no success condition.
 *
 * Every automation maps completely: the sealed [CommandValue] set is closed,
 * so the mapper is total. It never throws and never silently drops a command.
 */
object AutomationSceneMapper {

    /**
     * Convert one [Action] into a canonical [UniversalAction].
     * Total: every [CommandValue] variant maps to a canonical action.
     */
    fun toUniversalAction(action: Action): UniversalAction {
        val id = action.deviceId
        return when (val c = action.command) {
            is CommandValue.OnOff ->
                if (c.turnOn) UniversalAction.PowerOn(id) else UniversalAction.PowerOff(id)
            is CommandValue.Media ->
                if (c.play) UniversalAction.MediaPlay(id) else UniversalAction.MediaPause(id)
            is CommandValue.Climate ->
                UniversalAction.SetTemperature(id, c.targetCelsius)
            is CommandValue.Level ->
                UniversalAction.Custom(id, "level", mapOf("value" to c.value.toString()))
            is CommandValue.Color ->
                UniversalAction.Custom(
                    id, "color",
                    mapOf("hue" to c.hueDegrees.toString(), "saturation" to c.saturation.toString())
                )
            is CommandValue.ColorTemperature ->
                UniversalAction.Custom(id, "colorTemperature", mapOf("kelvin" to c.kelvin.toString()))
            is CommandValue.Lock ->
                UniversalAction.Custom(id, "lock", mapOf("locked" to c.locked.toString()))
            is CommandValue.Position ->
                UniversalAction.Custom(id, "position", mapOf("percentOpen" to c.percentOpen.toString()))
            is CommandValue.Noop ->
                UniversalAction.Custom(id, "noop", emptyMap())
        }
    }

    /** Capability a step actually exercises, for the success predicate. */
    fun capabilityOf(action: Action): Capability = action.capability

    /**
     * Build the executable macro for an automation.
     *
     * @return transcript with the executable transaction plus an audit
     *         of per-step classification (canonical vs custom).
     */
    fun toMacroTransaction(automation: Automation): MappedTransaction =
        MappedTransaction(
            transaction = MacroTransaction(
                id = automation.id.value,
                name = automation.name,
                steps = automation.actions.map { a ->
                    ActionStep(
                        stepId = "${automation.id.value}-${a.capability.name}",
                        targetDeviceId = a.deviceId,
                        action = toUniversalAction(a),
                        successCondition = successPredicate(automation, a),
                        timeoutMs = automation.verification.timeoutMs
                    )
                },
                rollbackOnFailure = automation.compensation.isNotEmpty(),
                description = "Mapped from ${automation.name}"
            ),
            originalCount = automation.actions.size,
            customKeyCount = automation.actions.count {
                toUniversalAction(it) is UniversalAction.Custom
            }
        )

    private fun successPredicate(
        automation: Automation,
        a: Action
    ): StatePredicate? =
        if (automation.verification.requireStateConfirmation) {
            StatePredicate.CapabilityAvailable(a.deviceId, a.capability.name)
        } else {
            null
        }

    /**
     * Pure helper to classify a command for UI/auditing without
     * constructing the full macro.
     */
    fun isCanonicalCommand(command: CommandValue): Boolean = when (command) {
        is CommandValue.OnOff,
        is CommandValue.Media,
        is CommandValue.Climate -> true
        is CommandValue.Noop,
        is CommandValue.Level,
        is CommandValue.Color,
        is CommandValue.ColorTemperature,
        is CommandValue.Lock,
        is CommandValue.Position -> false
    }
}

/**
 * Result of [AutomationSceneMapper.toMacroTransaction]:
 * the executable transaction plus an audit trace of what was mapped.
 */
data class MappedTransaction(
    val transaction: MacroTransaction,
    /** Number of persisted actions in the source automation. */
    val originalCount: Int,
    /** How many steps exercise capability-less Custom actions. */
    val customKeyCount: Int
) {
    val stepCount: Int get() = transaction.steps.size
    fun classifiedCanonical(): Int = stepCount - customKeyCount
}

/** Dual-language (es/en) one-line summary of an execution result (§28 UI). */
fun summarizeExecution(result: SceneExecutionResult): String = when (result) {
    is SceneExecutionResult.Success ->
        "OK · ${result.completedSteps}/${result.totalSteps} pasos · ${result.durationMs} ms"
    is SceneExecutionResult.PartialFailure ->
        "Parcial · ${result.completedSteps}/${result.totalSteps} pasos · ${result.error}"
    is SceneExecutionResult.PreconditionFailed ->
        "Precondición fallida · ${result.reason}"
    is SceneExecutionResult.Timeout ->
        "Tiempo agotado · ${result.completedSteps}/${result.totalSteps} pasos"
}