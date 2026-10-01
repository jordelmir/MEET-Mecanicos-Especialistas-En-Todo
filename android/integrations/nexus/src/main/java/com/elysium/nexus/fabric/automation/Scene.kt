package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import java.util.UUID

// ─── §34 Multi-Device Scenes ─────────────────────────────────────────────────

/**
 * A [Scene] is a declarative, multi-device action sequence.
 *
 * Scenes are the primary way users express compound intent:
 * ```
 * MOVIE MODE
 *   TV → ON
 *   Receiver → ON
 *   TV → HDMI2
 *   Lights → 20%
 * ```
 *
 * Each step has:
 * - a precondition (optional)
 * - an action
 * - a success condition (optional)
 * - a timeout
 * - a rollback action (optional)
 *
 * Scenes are portable, serializable, and testable.
 */
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val steps: List<ActionStep>,
    val tags: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(steps.isNotEmpty()) { "Scene must have at least one step." }
    }
}

// ─── §35 Macro Transactions ──────────────────────────────────────────────────

/**
 * An [ActionStep] is one atomic unit within a [Scene] or [MacroTransaction].
 *
 * Unlike naive `delay(1000); click()`, an [ActionStep] is:
 * - idempotent when possible
 * - precondition-gated
 * - state-confirmable
 * - rollbackable
 * - timeout-bounded
 */
data class ActionStep(
    val stepId: String = UUID.randomUUID().toString(),
    val targetDeviceId: DeviceId,
    val action: UniversalAction,
    val precondition: StatePredicate? = null,
    val successCondition: StatePredicate? = null,
    val timeoutMs: Long = 5_000L,
    val rollbackAction: UniversalAction? = null,
    val description: String = "",
    val optional: Boolean = false,
    val retryCount: Int = 0,
    val retryDelayMs: Long = 1_000L
) {
    init {
        require(timeoutMs > 0) { "timeoutMs must be positive." }
        require(retryCount >= 0) { "retryCount must be non-negative." }
    }
}

/**
 * A [MacroTransaction] is a scene with explicit transaction semantics:
 * preconditions, confirmations, timeouts, and rollbacks for every step.
 *
 * This is NOT a simple delay-based macro. Every step is verified.
 */
data class MacroTransaction(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<ActionStep>,
    val rollbackOnFailure: Boolean = true,
    val description: String = ""
) {
    init {
        require(steps.isNotEmpty()) { "MacroTransaction must have at least one step." }
    }
}

// ─── State Predicates ────────────────────────────────────────────────────────

/**
 * A [StatePredicate] describes a condition that must be true
 * before or after an [ActionStep].
 *
 * Used for:
 * - Precondition checking (device must be ON before sending INPUT)
 * - Success verification (volume changed from 20 to 21)
 * - Rollback verification (volume restored to 20)
 */
sealed class StatePredicate {

    /** Device must be in a specific state. */
    data class DeviceState(
        val deviceId: DeviceId,
        val expectedState: com.elysium.nexus.fabric.canonical.DeviceState
    ) : StatePredicate()

    /** A capability must be available on the device. */
    data class CapabilityAvailable(
        val deviceId: DeviceId,
        val capability: String
    ) : StatePredicate()

    /** Device must be reachable (ping/pong or last-seen). */
    data class DeviceReachable(
        val deviceId: DeviceId,
        val maxAgeMs: Long = 30_000L
    ) : StatePredicate()

    /** Custom predicate evaluated by a lambda. */
    data class Custom(
        val description: String,
        val evaluate: suspend () -> Boolean
    ) : StatePredicate()

    /** All sub-predicates must be true. */
    data class All(
        val predicates: List<StatePredicate>
    ) : StatePredicate()

    /** At least one sub-predicate must be true. */
    data class Any(
        val predicates: List<StatePredicate>
    ) : StatePredicate()
}

// ─── §36 Scenes as Code ──────────────────────────────────────────────────────

/**
 * Declarative scene definition.
 *
 * This is the DSL representation of a [Scene].
 * It can be serialized to/from YAML or JSON and
 * is portable across devices.
 *
 * ```yaml
 * scene: movie
 * description: "Living room movie mode"
 * steps:
 *   - device: tv-living
 *     action: power_on
 *     precondition: {type: device_reachable, device: tv-living}
 *     timeout: 5000
 *   - device: soundbar
 *     action: power_on
 *     timeout: 3000
 *   - device: tv-living
 *     action: input_select
 *     inputId: HDMI2
 *     timeout: 2000
 *   - device: lights
 *     action: set_level
 *     level: 0.20
 *     timeout: 1000
 * ```
 */
data class SceneDefinition(
    val scene: String,
    val description: String = "",
    val steps: List<StepDefinition>,
    val tags: Set<String> = emptySet()
)

data class StepDefinition(
    val device: String,
    val action: String,
    val inputId: String? = null,
    val level: Float? = null,
    val direction: String? = null,
    val targetCelsius: Float? = null,
    val mode: String? = null,
    val key: String? = null,
    val precondition: PredicateDefinition? = null,
    val successCondition: PredicateDefinition? = null,
    val timeout: Long = 5_000L,
    val rollback: StepDefinition? = null,
    val optional: Boolean = false,
    val retryCount: Int = 0,
    val retryDelayMs: Long = 1_000L,
    val description: String = ""
)

data class PredicateDefinition(
    val type: String,
    val device: String? = null,
    val state: String? = null,
    val capability: String? = null,
    val maxAgeMs: Long? = null,
    val predicates: List<PredicateDefinition>? = null
)

// ─── Scene Execution Result ──────────────────────────────────────────────────

sealed class SceneExecutionResult {
    data class Success(
        val completedSteps: Int,
        val totalSteps: Int,
        val durationMs: Long
    ) : SceneExecutionResult()

    data class PartialFailure(
        val completedSteps: Int,
        val totalSteps: Int,
        val failedStep: ActionStep,
        val error: String,
        val rolledBack: Boolean,
        val durationMs: Long
    ) : SceneExecutionResult()

    data class PreconditionFailed(
        val failedStep: ActionStep,
        val reason: String
    ) : SceneExecutionResult()

    data class Timeout(
        val completedSteps: Int,
        val totalSteps: Int,
        val timedOutStep: ActionStep,
        val durationMs: Long
    ) : SceneExecutionResult()
}

// ─── Scene Status ────────────────────────────────────────────────────────────

enum class SceneStepStatus {
    Pending,
    PreconditionChecking,
    Executing,
    Confirming,
    Succeeded,
    Failed,
    RolledBack,
    Skipped,
    TimedOut
}

data class SceneExecutionState(
    val sceneId: String,
    val stepStates: Map<String, SceneStepStatus>,
    val currentStepIndex: Int,
    val isComplete: Boolean,
    val startTimeMs: Long,
    val currentTimeMs: Long
) {
    val progress: Float
        get() = if (stepStates.isEmpty()) 0f
        else stepStates.values.count { it == SceneStepStatus.Succeeded }.toFloat() / stepStates.size

    val elapsedMs: Long
        get() = currentTimeMs - startTimeMs
}
