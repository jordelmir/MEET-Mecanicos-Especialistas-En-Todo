package com.elysium.nexus.fabric.automation

import android.util.Log
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.hedging.MutationSemantics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * §37 Concrete Automation Engine Implementation.
 *
 * Executes scenes with full transaction semantics:
 * - Precondition checking per step
 * - Action dispatch via [ActionDispatcher]
 * - Success condition verification
 * - Rollback on failure (if rollback action provided)
 * - Timeout per step
 * - Execution history tracking
 *
 * ## Scene Execution Flow
 *
 * ```
 * for each step in scene.steps:
 *   1. Check precondition (if provided)
 *   2. Dispatch action
 *   3. Wait for success condition (if provided)
 *   4. On failure:
 *      a. Execute rollback for completed steps (if rollbackOnFailure)
 *      b. Return PartialFailure
 *   5. On timeout:
 *      a. Execute rollback for completed steps
 *      b. Return Timeout
 * ```
 *
 * ## Macro Transaction
 *
 * Same as scene but with [MacroTransaction.rollbackOnFailure]
 * controlling whether failed steps trigger rollback.
 *
 * ## Rule Evaluation
 *
 * Rules are evaluated against current device state.
 * The engine maintains a registry of rules and evaluates
 * them when state changes occur.
 */
class ConcreteAutomationEngineService(
    private val actionDispatcher: UniversalActionDispatcher,
    private val stateProvider: StateProvider? = null
) : AutomationEngineService {

    private val TAG = "AutomationEngine"

    private val rules = ConcurrentHashMap<String, AutomationRule>()
    private val scenes = ConcurrentHashMap<String, Scene>()
    private val history = MutableStateFlow<List<AutomationExecution>>(emptyList())
    private val _rulesFlow = MutableStateFlow<List<AutomationRule>>(emptyList())

    // ─── Scene Execution ────────────────────────────────

    override suspend fun executeScene(scene: Scene): SceneExecutionResult {
        val startTime = System.currentTimeMillis()
        val rollbackOnFailure = scene.steps.any { it.rollbackAction != null }
        val result = runSteps(scene.steps, rollbackOnFailure)
        recordExecution(kind = "SCENE", name = scene.name, result = result, startedAtMs = startTime)
        return result
    }

    // ─── Macro Execution ────────────────────────────────

    override suspend fun executeMacro(macro: MacroTransaction): SceneExecutionResult {
        val startTime = System.currentTimeMillis()
        val result = runSteps(macro.steps, rollbackOnFailure = macro.rollbackOnFailure)
        recordExecution(kind = "MACRO", name = macro.name, result = result, startedAtMs = startTime)
        return result
    }

    /**
     * V06-P19: per-step transaction semantics — the ONE implementation for
     * scenes and macros. Every step is:
     *
     * 1. Precondition checked (fail → rollback completed, then abort)
     * 2. Action dispatched — blind retries are gated by
     *    [MutationSemantics]: only IDEMPOTENT_SAFE steps may repeat
     *    (a NON_IDEMPOTENT / DESTRUCTIVE step is dispatched exactly once;
     *    a blind repeat would be a user-visible double-execution)
     * 3. Success condition verified within [ActionStep.timeoutMs]
     * 4. On any failure: rollback of completed steps (reversed)
     *    when [rollbackOnFailure]
     */
    private suspend fun runSteps(
        steps: List<ActionStep>,
        rollbackOnFailure: Boolean
    ): SceneExecutionResult {
        val startTime = System.currentTimeMillis()
        val completedSteps = mutableListOf<ActionStep>()

        for (step in steps) {
            // 1. Precondition
            if (step.precondition != null) {
                val preconditionMet = evaluatePrecondition(step.precondition)
                if (!preconditionMet) {
                    if (rollbackOnFailure) rollbackSteps(completedSteps)
                    return SceneExecutionResult.PreconditionFailed(
                        failedStep = step,
                        reason = "Precondition not met: ${step.precondition}"
                    )
                }
            }

            // 2. Execute with policy-gated retries
            var lastError: String? = null
            var success = false

            val attempts =
                if (MutationSemantics.canRepeatWithoutConfirmation(step.action)) step.retryCount + 1
                else 1

            for (attempt in 0 until attempts) {
                if (attempt > 0) {
                    kotlinx.coroutines.delay(step.retryDelayMs)
                }

                val result = actionDispatcher.dispatch(step.targetDeviceId, step.action)
                if (result) {
                    success = true
                    break
                } else {
                    lastError = "Action dispatch failed"
                }
            }

            if (!success) {
                if (rollbackOnFailure) rollbackSteps(completedSteps)
                return SceneExecutionResult.PartialFailure(
                    completedSteps = completedSteps.size,
                    totalSteps = steps.size,
                    failedStep = step,
                    error = lastError ?: "Unknown error",
                    rolledBack = rollbackOnFailure,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // 3. Verify success condition
            if (step.successCondition != null) {
                val verified = verifySuccessCondition(step.successCondition, step.timeoutMs)
                if (!verified) {
                    // The step itself was dispatched but never confirmed:
                    // roll it back together with completed steps.
                    if (rollbackOnFailure) rollbackSteps(completedSteps + step)
                    return SceneExecutionResult.Timeout(
                        completedSteps = completedSteps.size,
                        totalSteps = steps.size,
                        timedOutStep = step,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            completedSteps.add(step)
        }

        return SceneExecutionResult.Success(
            completedSteps = completedSteps.size,
            totalSteps = steps.size,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    // ─── Rule Evaluation ────────────────────────────────

    override suspend fun evaluateRules(): List<AutomationRule> {
        return rules.values
            .filter { it.enabled }
            .filter { rule ->
                // Check cooldown
                if (rule.cooldownMs > 0) {
                    val lastExecution = history.value
                        .filter { it.ruleId == rule.id }
                        .maxByOrNull { it.startedAtMs }
                    if (lastExecution != null) {
                        val elapsed = System.currentTimeMillis() - lastExecution.startedAtMs
                        if (elapsed < rule.cooldownMs) {
                            return@filter false
                        }
                    }
                }

                // Check daily limit
                val todayStart = getTodayStartMs()
                val todayExecutions = history.value.count {
                    it.ruleId == rule.id && it.startedAtMs >= todayStart
                }
                if (todayExecutions >= rule.maxExecutionsPerDay) {
                    return@filter false
                }

                // Evaluate trigger
                evaluateTrigger(rule.trigger)
            }
            .filter { rule ->
                // Evaluate all conditions
                rule.conditions.all { evaluatePrecondition(it) }
            }
    }

    override suspend fun addRule(rule: AutomationRule) {
        rules[rule.id] = rule
        _rulesFlow.value = rules.values.toList()
    }

    override suspend fun removeRule(ruleId: String) {
        rules.remove(ruleId)
        _rulesFlow.value = rules.values.toList()
    }

    override fun observeRules(): Flow<List<AutomationRule>> = _rulesFlow

    override fun observeHistory(): Flow<List<AutomationExecution>> = history

    // ─── Precondition / Trigger Evaluation ──────────────

    private suspend fun evaluatePrecondition(predicate: StatePredicate): Boolean {
        return when (predicate) {
            is StatePredicate.DeviceState -> {
                val currentState = stateProvider?.getState(predicate.deviceId)
                currentState == predicate.expectedState
            }
            is StatePredicate.CapabilityAvailable -> {
                val capabilities = stateProvider?.getCapabilities(predicate.deviceId)
                capabilities?.any { it.name == predicate.capability } ?: false
            }
            is StatePredicate.DeviceReachable -> {
                val lastSeen = stateProvider?.getLastSeen(predicate.deviceId) ?: 0L
                val age = System.nanoTime() - lastSeen
                age < predicate.maxAgeMs * 1_000_000L
            }
            is StatePredicate.Custom -> predicate.evaluate()
            is StatePredicate.All -> predicate.predicates.all { evaluatePrecondition(it) }
            is StatePredicate.Any -> predicate.predicates.any { evaluatePrecondition(it) }
        }
    }

    private suspend fun verifySuccessCondition(condition: StatePredicate, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (evaluatePrecondition(condition)) return true
            kotlinx.coroutines.delay(100)
        }
        return false
    }

    private suspend fun evaluateTrigger(trigger: AutomationTrigger): Boolean {
        return when (trigger) {
            is AutomationTrigger.ManualTrigger -> false // Manual triggers don't auto-fire
            is AutomationTrigger.TimeTrigger -> evaluateTimeTrigger(trigger)
            is AutomationTrigger.StateTrigger -> {
                val state = stateProvider?.getState(trigger.deviceId)
                state != null
            }
            is AutomationTrigger.EventTrigger -> false // Events must be pushed externally
            is AutomationTrigger.ConnectivityTrigger -> {
                val connected = stateProvider?.isConnected(trigger.deviceId) ?: false
                connected == trigger.connected
            }
            is AutomationTrigger.BluetoothTrigger -> false // Requires BT subsystem
            is AutomationTrigger.UsbTrigger -> false // Requires USB subsystem
            is AutomationTrigger.AppContextTrigger -> false // Requires host agent
            is AutomationTrigger.MatterTrigger -> false // Requires Matter subsystem
            is AutomationTrigger.ElysiumLinkTrigger -> false // Requires Elysium Link
            is AutomationTrigger.NfcTrigger -> false // Requires NFC subsystem
            is AutomationTrigger.CompositeAnd -> trigger.triggers.all { evaluateTrigger(it) }
            is AutomationTrigger.CompositeOr -> trigger.triggers.any { evaluateTrigger(it) }
        }
    }

    private fun evaluateTimeTrigger(trigger: AutomationTrigger.TimeTrigger): Boolean {
        // Simple time-based trigger evaluation
        if (trigger.timeOfDay != null) {
            val now = java.util.Calendar.getInstance()
            val parts = trigger.timeOfDay.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: return false
                val minute = parts[1].toIntOrNull() ?: return false
                if (now.get(java.util.Calendar.HOUR_OF_DAY) != hour) return false
                if (now.get(java.util.Calendar.MINUTE) != minute) return false
            }
        }

        if (trigger.daysOfWeek.isNotEmpty()) {
            val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
            // Convert Java Calendar days (1=Sun) to ISO days (1=Mon)
            val isoDay = if (dayOfWeek == java.util.Calendar.SUNDAY) 7 else dayOfWeek - 1
            if (isoDay !in trigger.daysOfWeek) return false
        }

        return true
    }

    // ─── Rollback ───────────────────────────────────────

    private suspend fun rollbackSteps(steps: List<ActionStep>) {
        for (step in steps.reversed()) {
            if (step.rollbackAction != null) {
                try {
                    actionDispatcher.dispatch(step.targetDeviceId, step.rollbackAction)
                } catch (e: Exception) {
                    Log.e(TAG, "Rollback failed for step ${step.stepId}: ${e.message}")
                }
            }
        }
    }

    // ─── History ────────────────────────────────────────

    /**
     * V06-P19: every scene/macro run is recorded — the observable history
     * is a real audit trail, not an always-empty flow.
     */
    private fun recordExecution(
        kind: String,
        name: String,
        result: SceneExecutionResult,
        startedAtMs: Long
    ) {
        val completedSteps = when (result) {
            is SceneExecutionResult.Success -> result.completedSteps
            is SceneExecutionResult.PartialFailure -> result.completedSteps
            is SceneExecutionResult.Timeout -> result.completedSteps
            is SceneExecutionResult.PreconditionFailed -> 0
        }
        val error = when (result) {
            is SceneExecutionResult.PartialFailure -> result.error
            is SceneExecutionResult.Timeout -> "Timeout at step ${result.timedOutStep.stepId}"
            is SceneExecutionResult.PreconditionFailed -> result.reason
            else -> null
        }
        val execution = AutomationExecution(
            ruleId = "$kind:$name",
            ruleName = name,
            triggerType = kind,
            actionsExecuted = completedSteps,
            success = result is SceneExecutionResult.Success,
            error = error,
            startedAtMs = startedAtMs
        )
        history.value = (history.value + execution).takeLast(1000)
    }

    private fun getTodayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

// ─── Action Dispatcher Interface ─────────────────────────────────────────────

/**
 * Dispatches a [UniversalAction] to the target device.
 * Implementations translate the canonical action to
 * protocol-specific commands.
 */
fun interface UniversalActionDispatcher {
    /**
     * Dispatch an action to a device.
     * Returns true if the action was delivered successfully.
     */
    suspend fun dispatch(deviceId: DeviceId, action: UniversalAction): Boolean
}

// ─── State Provider Interface ────────────────────────────────────────────────

/**
 * Provides current state for devices.
 * Used by the automation engine for precondition
 * and trigger evaluation.
 */
interface StateProvider {
    suspend fun getState(deviceId: DeviceId): DeviceState?
    suspend fun getCapabilities(deviceId: DeviceId): Set<com.elysium.nexus.fabric.canonical.Capability>?
    suspend fun getLastSeen(deviceId: DeviceId): Long
    suspend fun isConnected(deviceId: DeviceId): Boolean
}
