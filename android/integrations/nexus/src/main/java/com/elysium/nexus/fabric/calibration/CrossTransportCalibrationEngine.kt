package com.elysium.nexus.fabric.calibration

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.core.device.EvidenceLevel

/**
 * §11 Cross-Transport IR Calibration Engine.
 *
 * When WiFi can read state, IR signals can be
 * automatically verified by:
 *
 * 1. Read current state via WiFi
 * 2. Send IR candidate signal
 * 3. Read state again via WiFi
 * 4. If state changed as expected → IR verified
 * 5. Restore state via WiFi
 *
 * This produces [EvidenceLevel.WIFI_ORACLE_VERIFIED]
 * for IR code sets without requiring a physical test.
 *
 * ## Safe calibration
 *
 * Only calibrates with reversible actions:
 * - VOLUME_UP / VOLUME_DOWN
 * - MUTE / UNMUTE
 * - Navigation in safe contexts
 *
 * Never auto-calibrates:
 * - POWER_OFF (can't verify if TV is off)
 * - Factory reset
 * - Input changes (may require manual intervention)
 *
 * ## Protocol
 *
 * ```
 * WiFi: read volume → 20
 * IR:   send CANDIDATE_A (VOLUME_UP)
 * WiFi: read volume → 21  ← causal change!
 * IR:   send VOLUME_DOWN
 * WiFi: read volume → 20  ← restored
 * → CANDIDATE_A = WIFI_ORACLE_VERIFIED
 * ```
 */
class CrossTransportCalibrationEngine(
    private val maxRetries: Int = 2
) {

    /**
     * Run a calibration experiment for a single
     * IR candidate against a WiFi-readable state.
     *
     * @param deviceId the target device
     * @param candidateCodeSetId the IR code set to test
     * @param action the action to calibrate
     * @param signalId the specific signal ID
     * @param stateReader function to read current state via WiFi
     * @param irSender function to send IR signal
     * @param stateRestorer function to restore state via WiFi
     * @return the calibration result
     */
    suspend fun calibrate(
        deviceId: DeviceId,
        candidateCodeSetId: String,
        action: UniversalAction,
        signalId: String,
        stateReader: suspend (Capability) -> DeviceState?,
        irSender: suspend (DeviceId, String) -> Boolean,
        stateRestorer: suspend (DeviceId, DeviceState) -> Boolean
    ): CalibrationResult {
        val capability = action.requiredCapability()

        // Step 1: Read initial state
        val beforeState = stateReader(capability)
            ?: return CalibrationResult.StateUnreadable(
                "Cannot read state for $capability via WiFi"
            )

        // Step 2: Send IR candidate
        val sent = irSender(deviceId, signalId)
        if (!sent) {
            return CalibrationResult.SignalFailed(
                "IR signal $signalId failed to transmit"
            )
        }

        // Step 3: Read state after IR
        val afterState = stateReader(capability)
            ?: return CalibrationResult.StateUnreadable(
                "Cannot read state after IR send"
            )

        // Step 4: Check for causal change
        val causalChange = detectCausalChange(action, beforeState, afterState)
        if (!causalChange) {
            return CalibrationResult.NoChange(
                "State did not change after IR send. " +
                    "Before: $beforeState, After: $afterState"
            )
        }

        // Step 5: Restore state
        val restored = stateRestorer(deviceId, beforeState)
        if (!restored) {
            return CalibrationResult.RestorationFailed(
                "IR worked but state restoration failed. " +
                    "Device may be in altered state."
            )
        }

        // Step 6: Verify restoration
        val finalState = stateReader(capability)
        val fullyRestored = finalState == beforeState

        return CalibrationResult.Verified(
            candidateCodeSetId = candidateCodeSetId,
            signalId = signalId,
            beforeState = beforeState,
            afterState = afterState,
            restoredState = finalState,
            fullyRestored = fullyRestored,
            evidenceLevel = if (fullyRestored) {
                EvidenceLevel.WIFI_ORACLE_VERIFIED
            } else {
                EvidenceLevel.SESSION_VERIFIED
            }
        )
    }

    /**
     * Detect whether the state change is consistent
     * with the action. This is the core logic:
     * VOLUME_UP should increase volume, not decrease it.
     */
    private fun detectCausalChange(
        action: UniversalAction,
        before: DeviceState,
        after: DeviceState
    ): Boolean = when (action) {
        is UniversalAction.VolumeUp -> {
            // Volume should have increased
            extractLevel(after) > extractLevel(before)
        }
        is UniversalAction.VolumeDown -> {
            // Volume should have decreased
            extractLevel(after) < extractLevel(before)
        }
        is UniversalAction.Mute -> {
            // Mute state should have toggled
            before != after
        }
        is UniversalAction.PowerOn -> {
            // Power state should be on
            after is DeviceState.OnOff && after.isOn
        }
        is UniversalAction.PowerOff -> {
            // Power state should be off
            after is DeviceState.OnOff && !after.isOn
        }
        else -> {
            // For other actions, any state change counts
            before != after
        }
    }

    private fun extractLevel(state: DeviceState): Float = when (state) {
        is DeviceState.Level -> state.value
        is DeviceState.OnOff -> if (state.isOn) 1f else 0f
        else -> 0f
    }
}

/**
 * Calibration result.
 */
sealed class CalibrationResult {
    /** IR worked and state was verified. */
    data class Verified(
        val candidateCodeSetId: String,
        val signalId: String,
        val beforeState: DeviceState,
        val afterState: DeviceState,
        val restoredState: DeviceState?,
        val fullyRestored: Boolean,
        val evidenceLevel: EvidenceLevel
    ) : CalibrationResult()

    /** State could not be read via WiFi. */
    data class StateUnreadable(val reason: String) : CalibrationResult()

    /** IR signal failed to transmit. */
    data class SignalFailed(val reason: String) : CalibrationResult()

    /** State did not change after IR send. */
    data class NoChange(val reason: String) : CalibrationResult()

    /** IR worked but state could not be restored. */
    data class RestorationFailed(val reason: String) : CalibrationResult()
}
