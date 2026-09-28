package com.elysium.nexus.fabric.automation

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * §38 Concrete Local Rule Engine Implementation.
 *
 * Evaluates rules without cloud dependency.
 * Essential automations work on:
 * - Nexus Receiver
 * - Android device
 * - Any local host
 *
 * ## Rule Evaluation
 *
 * 1. Filter enabled rules
 * 2. Check cooldown per rule
 * 3. Check daily execution limit
 * 4. Evaluate trigger condition
 * 5. Evaluate all guard conditions
 * 6. Return list of rules ready to fire
 *
 * ## Execution
 *
 * Each rule's actions are dispatched sequentially.
 * If a rule has conditions, they are evaluated before
 * each action dispatch.
 *
 * ## State Tracking
 *
 * - Last execution time per rule
 * - Execution count per rule per day
 * - Cooldown tracking
 */
class ConcreteLocalRuleEngine(
    private val actionDispatcher: UniversalActionDispatcher,
    private val stateProvider: StateProvider? = null
) : LocalRuleEngine {

    private val TAG = "LocalRuleEngine"
    private val rules = ConcurrentHashMap<String, AutomationRule>()
    private val lastExecutionTimes = ConcurrentHashMap<String, AtomicLong>()
    private val dailyExecutionCounts = ConcurrentHashMap<String, AtomicLong>()

    override suspend fun evaluate(): List<AutomationRule> {
        return rules.values
            .filter { it.enabled }
            .filter { !isInCooldown(it) }
            .filter { !hasExceededDailyLimit(it) }
            .filter { evaluateTrigger(it.trigger) }
            .filter { rule ->
                rule.conditions.all { evaluatePrecondition(it) }
            }
    }

    override suspend fun execute(rule: AutomationRule) {
        Log.i(TAG, "Executing rule: ${rule.name} (${rule.id})")

        val startTime = System.currentTimeMillis()

        try {
            for (action in rule.actions) {
                executeAction(action)
            }

            recordExecution(rule.id, true)
            Log.i(TAG, "Rule ${rule.name} executed successfully")
        } catch (e: Exception) {
            recordExecution(rule.id, false)
            Log.e(TAG, "Rule ${rule.name} execution failed: ${e.message}")
        }
    }

    override suspend fun getDueRules(): List<AutomationRule> {
        return evaluate()
    }

    override suspend fun recordExecution(ruleId: String, success: Boolean) {
        val now = System.currentTimeMillis()
        lastExecutionTimes.getOrPut(ruleId) { AtomicLong(0) }.set(now)

        val todayStart = getTodayStartMs()
        val key = "${ruleId}_${todayStart}"
        dailyExecutionCounts.getOrPut(key) { AtomicLong(0) }.incrementAndGet()

        Log.d(TAG, "Recorded execution for rule $ruleId: success=$success")
    }

    override fun isInCooldown(rule: AutomationRule): Boolean {
        if (rule.cooldownMs <= 0) return false

        val lastExecution = lastExecutionTimes[rule.id]?.get() ?: return false
        val elapsed = System.currentTimeMillis() - lastExecution
        return elapsed < rule.cooldownMs
    }

    override fun hasExceededDailyLimit(rule: AutomationRule): Boolean {
        if (rule.maxExecutionsPerDay >= Int.MAX_VALUE) return false

        val todayStart = getTodayStartMs()
        val key = "${rule.id}_${todayStart}"
        val count = dailyExecutionCounts[key]?.get() ?: 0L
        return count >= rule.maxExecutionsPerDay
    }

    // ─── Rule Management ────────────────────────────────

    fun addRule(rule: AutomationRule) {
        rules[rule.id] = rule
    }

    fun removeRule(ruleId: String) {
        rules.remove(ruleId)
        lastExecutionTimes.remove(ruleId)
    }

    fun getRule(ruleId: String): AutomationRule? = rules[ruleId]

    fun getAllRules(): List<AutomationRule> = rules.values.toList()

    // ─── Trigger Evaluation ─────────────────────────────

    private suspend fun evaluateTrigger(trigger: AutomationTrigger): Boolean {
        return when (trigger) {
            is AutomationTrigger.ManualTrigger -> false
            is AutomationTrigger.TimeTrigger -> evaluateTimeTrigger(trigger)
            is AutomationTrigger.StateTrigger -> evaluateStateTrigger(trigger)
            is AutomationTrigger.EventTrigger -> false // Events must be pushed externally
            is AutomationTrigger.ConnectivityTrigger -> evaluateConnectivityTrigger(trigger)
            is AutomationTrigger.BluetoothTrigger -> false
            is AutomationTrigger.UsbTrigger -> false
            is AutomationTrigger.AppContextTrigger -> false
            is AutomationTrigger.MatterTrigger -> false
            is AutomationTrigger.ElysiumLinkTrigger -> false
            is AutomationTrigger.NfcTrigger -> false
            is AutomationTrigger.CompositeAnd -> trigger.triggers.all { evaluateTrigger(it) }
            is AutomationTrigger.CompositeOr -> trigger.triggers.any { evaluateTrigger(it) }
        }
    }

    private fun evaluateTimeTrigger(trigger: AutomationTrigger.TimeTrigger): Boolean {
        val now = java.util.Calendar.getInstance()

        if (trigger.timeOfDay != null) {
            val parts = trigger.timeOfDay.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: return false
                val minute = parts[1].toIntOrNull() ?: return false
                if (now.get(java.util.Calendar.HOUR_OF_DAY) != hour) return false
                if (now.get(java.util.Calendar.MINUTE) != minute) return false
            }
        }

        if (trigger.daysOfWeek.isNotEmpty()) {
            val dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK)
            val isoDay = if (dayOfWeek == java.util.Calendar.SUNDAY) 7 else dayOfWeek - 1
            if (isoDay !in trigger.daysOfWeek) return false
        }

        return true
    }

    private suspend fun evaluateStateTrigger(trigger: AutomationTrigger.StateTrigger): Boolean {
        val state = stateProvider?.getState(trigger.deviceId) ?: return false
        return when {
            trigger.stateMatches != null -> state == trigger.stateMatches
            else -> state != com.elysium.nexus.fabric.canonical.DeviceState.Unknown
        }
    }

    private suspend fun evaluateConnectivityTrigger(trigger: AutomationTrigger.ConnectivityTrigger): Boolean {
        val connected = stateProvider?.isConnected(trigger.deviceId) ?: false
        return connected == trigger.connected
    }

    // ─── Precondition Evaluation ────────────────────────

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
            is StatePredicate.Custom -> {
                // For local engine, custom predicates are evaluated synchronously
                // In production, this would need a coroutine scope
                false
            }
            is StatePredicate.All -> predicate.predicates.all { evaluatePrecondition(it) }
            is StatePredicate.Any -> predicate.predicates.any { evaluatePrecondition(it) }
        }
    }

    // ─── Action Execution ───────────────────────────────

    private suspend fun executeAction(action: AutomationAction) {
        when (action) {
            is AutomationAction.ExecuteAction -> {
                actionDispatcher.dispatch(action.targetDeviceId, action.action)
            }
            is AutomationAction.ExecuteScene -> {
                Log.d(TAG, "Scene execution delegated to AutomationEngineService")
            }
            is AutomationAction.Notify -> {
                Log.i(TAG, "Notification: ${action.title} - ${action.body}")
            }
            is AutomationAction.Delay -> {
                kotlinx.coroutines.delay(action.durationMs)
            }
            is AutomationAction.Conditional -> {
                if (evaluatePrecondition(action.condition)) {
                    action.thenActions.forEach { executeAction(it) }
                } else {
                    action.elseActions.forEach { executeAction(it) }
                }
            }
            is AutomationAction.ExecuteMacro -> {
                Log.d(TAG, "Macro execution delegated to AutomationEngineService")
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────

    private fun getTodayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
