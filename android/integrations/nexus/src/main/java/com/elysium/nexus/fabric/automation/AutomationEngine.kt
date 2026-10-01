package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

// ─── §37 Universal Automation Engine ─────────────────────────────────────────

/**
 * The [AutomationEngine] executes rules, scenes, and macros.
 *
 * It is the central coordinator for all automated actions:
 * - Rule triggers → action execution
 * - Scene execution → step-by-step with preconditions
 * - Macro transactions → with rollback on failure
 * - Time-based scheduling
 * - State-based triggers
 * - Manual triggers
 *
 * The engine operates locally. Cloud is optional for sync/remote access.
 */
interface AutomationEngineService {

    /**
     * Execute a scene with full transaction semantics.
     *
     * Each step is:
     * 1. Precondition checked
     * 2. Action dispatched
     * 3. Success condition verified (if provided)
     * 4. Rollback executed on failure (if provided)
     */
    suspend fun executeScene(scene: Scene): SceneExecutionResult

    /**
     * Execute a macro transaction.
     * If [MacroTransaction.rollbackOnFailure] is true,
     * failed steps trigger rollback of completed steps.
     */
    suspend fun executeMacro(macro: MacroTransaction): SceneExecutionResult

    /**
     * Evaluate all rules against current state.
     * Returns rules that should fire.
     */
    suspend fun evaluateRules(): List<AutomationRule>

    /**
     * Add a rule to the engine.
     */
    suspend fun addRule(rule: AutomationRule)

    /**
     * Remove a rule.
     */
    suspend fun removeRule(ruleId: String)

    /**
     * Get all active rules.
     */
    fun observeRules(): Flow<List<AutomationRule>>

    /**
     * Get execution history.
     */
    fun observeHistory(): Flow<List<AutomationExecution>>
}

// ─── Automation Rules ────────────────────────────────────────────────────────

/**
 * An [AutomationRule] defines: WHEN trigger THEN action(s).
 *
 * Rules are the simplest form of automation:
 * - Time-based: every day at 8 AM
 * - State-based: when TV turns on
 * - Event-based: when Bluetooth connects
 * - Manual: user presses a button
 */
data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val trigger: AutomationTrigger,
    val conditions: List<StatePredicate> = emptyList(),
    val actions: List<AutomationAction>,
    val enabled: Boolean = true,
    val cooldownMs: Long = 0L,
    val maxExecutionsPerDay: Int = Int.MAX_VALUE,
    val metadata: Map<String, String> = emptyMap()
)

// ─── Triggers ────────────────────────────────────────────────────────────────

sealed class AutomationTrigger {

    /** Fire at a specific time. */
    data class TimeTrigger(
        val cronExpression: String? = null,
        val timeOfDay: String? = null,  // "08:00"
        val daysOfWeek: Set<Int> = emptySet()  // 1=Mon..7=Sun
    ) : AutomationTrigger()

    /** Fire when a device state changes. */
    data class StateTrigger(
        val deviceId: DeviceId,
        val capability: String,
        val stateMatches: com.elysium.nexus.fabric.canonical.DeviceState? = null
    ) : AutomationTrigger()

    /** Fire on a device event. */
    data class EventTrigger(
        val deviceId: DeviceId,
        val eventType: String
    ) : AutomationTrigger()

    /** Fire when connectivity changes. */
    data class ConnectivityTrigger(
        val deviceId: DeviceId,
        val connected: Boolean
    ) : AutomationTrigger()

    /** Fire when a Bluetooth device connects. */
    data class BluetoothTrigger(
        val deviceAddress: String? = null,
        val connected: Boolean = true
    ) : AutomationTrigger()

    /** Fire when a USB device is plugged in. */
    data class UsbTrigger(
        val vendorId: Int? = null,
        val productId: Int? = null,
        val connected: Boolean = true
    ) : AutomationTrigger()

    /** Fire when an app becomes active (host agent context). */
    data class AppContextTrigger(
        val appId: String
    ) : AutomationTrigger()

    /** Fire on Matter event. */
    data class MatterTrigger(
        val nodeId: String,
        val clusterId: String,
        val attributeId: String? = null
    ) : AutomationTrigger()

    /** Fire on Elysium Link event. */
    data class ElysiumLinkTrigger(
        val eventType: String
    ) : AutomationTrigger()

    /** Fire on NFC tap. */
    data class NfcTrigger(
        val tagId: String? = null
    ) : AutomationTrigger()

    /** Manual trigger (user pressed a button). */
    data object ManualTrigger : AutomationTrigger()

    /** Composite: all sub-triggers must fire. */
    data class CompositeAnd(
        val triggers: List<AutomationTrigger>
    ) : AutomationTrigger()

    /** Composite: any sub-trigger fires. */
    data class CompositeOr(
        val triggers: List<AutomationTrigger>
    ) : AutomationTrigger()
}

// ─── Actions ─────────────────────────────────────────────────────────────────

sealed class AutomationAction {

    /** Execute a universal action on a device. */
    data class ExecuteAction(
        val targetDeviceId: DeviceId,
        val action: UniversalAction
    ) : AutomationAction()

    /** Execute a scene. */
    data class ExecuteScene(
        val sceneId: String
    ) : AutomationAction()

    /** Send a notification. */
    data class Notify(
        val title: String,
        val body: String,
        val priority: NotificationPriority = NotificationPriority.DEFAULT
    ) : AutomationAction()

    /** Delay before next action. */
    data class Delay(
        val durationMs: Long
    ) : AutomationAction()

    /** Conditional action. */
    data class Conditional(
        val condition: StatePredicate,
        val thenActions: List<AutomationAction>,
        val elseActions: List<AutomationAction> = emptyList()
    ) : AutomationAction()

    /** Execute a macro transaction. */
    data class ExecuteMacro(
        val macroId: String
    ) : AutomationAction()
}

enum class NotificationPriority {
    LOW, DEFAULT, HIGH, URGENT
}

// ─── Rule Execution History ──────────────────────────────────────────────────

data class AutomationExecution(
    val id: String = UUID.randomUUID().toString(),
    val ruleId: String,
    val ruleName: String,
    val triggerType: String,
    val actionsExecuted: Int,
    val success: Boolean,
    val error: String? = null,
    val startedAtMs: Long,
    val completedAtMs: Long = System.currentTimeMillis()
) {
    val durationMs: Long
        get() = completedAtMs - startedAtMs
}

// ─── §38 Local Rule Engine ───────────────────────────────────────────────────

/**
 * The [LocalRuleEngine] evaluates rules without cloud dependency.
 *
 * Essential automations must work on:
 * - Nexus Receiver
 * - Android device
 * - Any local host
 *
 * No cloud required for:
 * - Time-based triggers
 * - State-based triggers
 * - Local device events
 * - Manual triggers
 */
interface LocalRuleEngine {

    /**
     * Evaluate all rules against current state.
     * Returns rules whose triggers and conditions are met.
     */
    suspend fun evaluate(): List<AutomationRule>

    /**
     * Execute a rule's actions.
     */
    suspend fun execute(rule: AutomationRule)

    /**
     * Get rules that are due to fire.
     */
    suspend fun getDueRules(): List<AutomationRule>

    /**
     * Update the last execution time for a rule.
     */
    suspend fun recordExecution(ruleId: String, success: Boolean)

    /**
     * Check if a rule is in cooldown.
     */
    fun isInCooldown(rule: AutomationRule): Boolean

    /**
     * Check if a rule has exceeded max executions today.
     */
    fun hasExceededDailyLimit(rule: AutomationRule): Boolean
}

// ─── Scene Registry ──────────────────────────────────────────────────────────

/**
 * The [SceneRegistry] manages scene definitions.
 * Scenes are portable, serializable, and testable.
 */
interface SceneRegistry {

    /**
     * Get a scene by ID.
     */
    suspend fun getScene(sceneId: String): Scene?

    /**
     * Get all scenes.
     */
    fun observeScenes(): Flow<List<Scene>>

    /**
     * Save a scene.
     */
    suspend fun saveScene(scene: Scene)

    /**
     * Delete a scene.
     */
    suspend fun deleteScene(sceneId: String)

    /**
     * Import a scene from declarative YAML/JSON.
     */
    suspend fun importScene(definition: SceneDefinition): Scene

    /**
     * Export a scene to declarative format.
     */
    suspend fun exportScene(sceneId: String): SceneDefinition?

    /**
     * Get scenes by tag.
     */
    suspend fun getScenesByTag(tag: String): List<Scene>
}
