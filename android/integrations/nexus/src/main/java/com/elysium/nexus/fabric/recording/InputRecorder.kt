package com.elysium.nexus.fabric.recording

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.evidence.FlightEntry

/**
 * §65-66 Input Recording + Replay Engine.
 *
 * Records:
 * ```
 * UniversalAction
 * timestamp
 * device
 * state
 * ```
 *
 * NOT raw coordinates.
 *
 * Use cases:
 * - Bug reproduction
 * - Macro generation
 * - Testing
 * - Automation
 * - Replay
 */
class InputRecorder(
    private val maxEntries: Int = 10_000
) {
    private val entries = ArrayDeque<RecordedAction>(maxEntries)
    private var isRecording = false

    val size: Int get() = entries.size
    val recording: Boolean get() = isRecording

    /**
     * Start recording.
     */
    fun startRecording() {
        isRecording = true
    }

    /**
     * Stop recording.
     */
    fun stopRecording() {
        isRecording = false
    }

    /**
     * Record an action.
     */
    fun record(
        action: UniversalAction,
        deviceId: DeviceId,
        stateBefore: DeviceState? = null,
        stateAfter: DeviceState? = null
    ) {
        if (!isRecording) return

        val entry = RecordedAction(
            timestampNs = System.nanoTime(),
            wallClockMs = System.currentTimeMillis(),
            action = action,
            deviceId = deviceId,
            stateBefore = stateBefore,
            stateAfter = stateAfter
        )

        if (entries.size >= maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    /**
     * Get all recorded entries.
     */
    fun entries(): List<RecordedAction> = entries.toList()

    /**
     * Get entries for a specific device.
     */
    fun entriesForDevice(deviceId: DeviceId): List<RecordedAction> {
        return entries.filter { it.deviceId == deviceId }
    }

    /**
     * Clear all recordings.
     */
    fun clear() {
        entries.clear()
    }

    /**
     * Export recording as a macro.
     */
    fun exportMacro(name: String): Macro {
        return Macro(
            name = name,
            actions = entries.toList(),
            createdAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Get recording duration in milliseconds.
     */
    fun durationMs(): Long {
        if (entries.size < 2) return 0
        return entries.last().wallClockMs - entries.first().wallClockMs
    }
}

data class RecordedAction(
    val timestampNs: Long,
    val wallClockMs: Long,
    val action: UniversalAction,
    val deviceId: DeviceId,
    val stateBefore: DeviceState? = null,
    val stateAfter: DeviceState? = null
) {
    /**
     * Delay from previous action in milliseconds.
     * Returns 0 for the first action.
     */
    fun delayFromPrevious(previous: RecordedAction?): Long {
        if (previous == null) return 0
        return wallClockMs - previous.wallClockMs
    }
}

/**
 * §66 Replay Engine.
 *
 * Replays recorded actions with:
 * - Simulation mode (dry-run)
 * - Real mode (actually executes)
 *
 * Real mode requires confirmation for
 * destructive actions.
 */
class ReplayEngine(
    private val executor: suspend (UniversalAction, DeviceId) -> Boolean
) {

    /**
     * Replay a macro in simulation mode.
     * Actions are logged but not executed.
     */
    suspend fun simulate(macro: Macro): ReplayResult {
        val results = mutableListOf<ReplayStepResult>()

        for ((index, entry) in macro.actions.withIndex()) {
            val previous = if (index > 0) macro.actions[index - 1] else null
            val delayMs = entry.delayFromPrevious(previous)

            results.add(ReplayStepResult(
                step = index,
                action = entry.action,
                deviceId = entry.deviceId,
                delayMs = delayMs,
                executed = false,
                success = true
            ))
        }

        return ReplayResult(
            macroName = macro.name,
            totalSteps = macro.actions.size,
            executedSteps = 0,
            successfulSteps = results.count { it.success },
            failedSteps = 0,
            mode = ReplayMode.Simulation,
            steps = results
        )
    }

    /**
     * Replay a macro in real mode.
     * Actions are actually executed.
     */
    suspend fun replayReal(
        macro: Macro,
        confirmDestructive: Boolean = true
    ): ReplayResult {
        val results = mutableListOf<ReplayStepResult>()
        var executedCount = 0
        var successCount = 0
        var failedCount = 0

        for ((index, entry) in macro.actions.withIndex()) {
            val previous = if (index > 0) macro.actions[index - 1] else null
            val delayMs = entry.delayFromPrevious(previous)

            // Check for destructive actions
            if (confirmDestructive && isDestructive(entry.action)) {
                results.add(ReplayStepResult(
                    step = index,
                    action = entry.action,
                    deviceId = entry.deviceId,
                    delayMs = delayMs,
                    executed = false,
                    success = false,
                    error = "Destructive action requires confirmation"
                ))
                failedCount++
                continue
            }

            // Execute with delay
            if (delayMs > 0) {
                kotlinx.coroutines.delay(delayMs)
            }

            val success = try {
                executor(entry.action, entry.deviceId)
            } catch (e: Exception) {
                false
            }

            executedCount++
            if (success) successCount++ else failedCount++

            results.add(ReplayStepResult(
                step = index,
                action = entry.action,
                deviceId = entry.deviceId,
                delayMs = delayMs,
                executed = true,
                success = success
            ))
        }

        return ReplayResult(
            macroName = macro.name,
            totalSteps = macro.actions.size,
            executedSteps = executedCount,
            successfulSteps = successCount,
            failedSteps = failedCount,
            mode = ReplayMode.Real,
            steps = results
        )
    }

    /**
     * V06-P18: recording guard delegates to the single [MutationSemantics]
     * classifier (high-consequence axis) — previously a private list here.
     */
    private fun isDestructive(action: UniversalAction): Boolean =
        com.elysium.nexus.fabric.hedging.MutationSemantics.requiresConfirmation(action)
}

data class Macro(
    val name: String,
    val actions: List<RecordedAction>,
    val createdAtMs: Long
)

data class ReplayStepResult(
    val step: Int,
    val action: UniversalAction,
    val deviceId: DeviceId,
    val delayMs: Long,
    val executed: Boolean,
    val success: Boolean,
    val error: String? = null
)

data class ReplayResult(
    val macroName: String,
    val totalSteps: Int,
    val executedSteps: Int,
    val successfulSteps: Int,
    val failedSteps: Int,
    val mode: ReplayMode,
    val steps: List<ReplayStepResult>
) {
    val isComplete: Boolean get() = executedSteps == totalSteps
    val allSuccessful: Boolean get() = failedSteps == 0
}

enum class ReplayMode {
    Simulation,
    Real
}
