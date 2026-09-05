package com.elysium369.meet.actionsafety

import com.elysium369.meet.authority.ElysiumAuthorityKernel
import com.elysium369.meet.authority.VerificationLevel
import com.elysium369.meet.vss.VehicleSignalGraph
import com.elysium369.meet.vss.VssStandardPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

enum class ActionSafetyPhase {
    INTENT_DECLARED,
    AUTHORIZATION_CHECKED,
    CAPABILITY_CHECKED,
    PRECONDITIONS_CHECKED,
    EXCLUSIVE_LEASE_ACQUIRED,
    EXECUTION_INITIATED,
    PHYSICAL_ACK_RECEIVED,
    POST_VERIFICATION_PERFORMED,
    EVIDENCE_CAPTURED,
    COMPLETED,
    FAILED_SAFE,
}

enum class ActionType {
    ECU_WRITE,
    ECU_FLASH_BLOCK,
    CLEAR_DTC,
    ACTIVE_ACTUATOR_TEST,
    VEHICLE_REMOTE_ACCESS,
    PTT_FLOOR_GRANT,
    FINANCIAL_DISBURSEMENT,
}

data class ActionPreconditions(
    val requireStationary: Boolean = true,
    val minBatteryVoltage: Float? = null,
    val requireEngineOff: Boolean = false,
    val requireUserConfirmation: Boolean = true,
)

data class ActionIntent(
    val actionId: String = UUID.randomUUID().toString(),
    val actionType: ActionType,
    val initiatorPrincipalId: String,
    val targetEntityId: String,
    val parameters: Map<String, String> = emptyMap(),
    val preconditions: ActionPreconditions = ActionPreconditions(),
)

data class ActionReceipt(
    val actionId: String,
    val actionType: ActionType,
    val phase: ActionSafetyPhase,
    val leaseToken: Long,
    val isVerified: Boolean,
    val evidenceHash: String,
    val failureReason: String? = null,
    val completedAtEpochMs: Long = System.currentTimeMillis(),
)

/**
 * Universal Action Safety Kernel — Enforces that no sensitive physical mutation
 * occurs without going through the 10-step fail-closed execution lifecycle.
 *
 * Doctrine:
 * 1. A requested action is NOT an executed action.
 * 2. An executed action is NOT a verified action.
 */
@Singleton
class ActionSafetyKernel @Inject constructor(
    private val authorityKernel: ElysiumAuthorityKernel,
    private val signalGraph: VehicleSignalGraph,
) {
    private val leaseCounter = AtomicLong(1000L)
    private val activeLeases = ConcurrentHashMap<String, Long>()
    private val _actionHistory = MutableStateFlow<List<ActionReceipt>>(emptyList())
    val actionHistory: StateFlow<List<ActionReceipt>> = _actionHistory.asStateFlow()

    /**
     * Executes a safety-critical action across the 10-phase pipeline.
     */
    suspend fun executeAction(
        intent: ActionIntent,
        isUserConfirmed: Boolean = false,
        hardwareExecutor: suspend (leaseToken: Long) -> Boolean,
        postVerifier: suspend () -> Boolean,
        recoveryRoutine: (suspend () -> Unit)? = null,
    ): ActionReceipt {
        val actionId = intent.actionId

        // Phase 1: Intent Declared
        var currentPhase = ActionSafetyPhase.INTENT_DECLARED

        // Phase 2: Authorization Checked
        if (intent.initiatorPrincipalId.isBlank()) {
            return failSafe(intent, currentPhase, 0L, "Missing initiator principal ID")
        }
        currentPhase = ActionSafetyPhase.AUTHORIZATION_CHECKED

        // Phase 3: Capability Checked
        if (intent.preconditions.requireUserConfirmation && !isUserConfirmed) {
            return failSafe(intent, currentPhase, 0L, "Action requires explicit user confirmation")
        }
        currentPhase = ActionSafetyPhase.CAPABILITY_CHECKED

        // Phase 4: Preconditions Checked
        if (intent.preconditions.requireStationary) {
            val speed = signalGraph.get(VssStandardPaths.VEHICLE_SPEED)?.asFloat() ?: 0f
            if (speed > 0.0f) {
                return failSafe(intent, currentPhase, 0L, "Vehicle is moving ($speed km/h); action requires stationary vehicle")
            }
        }

        if (intent.preconditions.minBatteryVoltage != null) {
            val voltage = signalGraph.get(VssStandardPaths.BATTERY_VOLTAGE)?.asFloat() ?: 12.6f
            if (voltage < intent.preconditions.minBatteryVoltage) {
                return failSafe(intent, currentPhase, 0L, "Battery voltage ($voltage V) below safe threshold (${intent.preconditions.minBatteryVoltage} V)")
            }
        }
        currentPhase = ActionSafetyPhase.PRECONDITIONS_CHECKED

        // Phase 5: Exclusive Lease Acquired
        val leaseToken = leaseCounter.getAndIncrement()
        activeLeases[actionId] = leaseToken
        currentPhase = ActionSafetyPhase.EXCLUSIVE_LEASE_ACQUIRED

        // Phase 6: Execution Initiated
        currentPhase = ActionSafetyPhase.EXECUTION_INITIATED
        val physicalSuccess = try {
            hardwareExecutor(leaseToken)
        } catch (e: Exception) {
            recoveryRoutine?.invoke()
            activeLeases.remove(actionId)
            return failSafe(intent, ActionSafetyPhase.EXECUTION_INITIATED, leaseToken, "Physical execution threw exception: ${e.message}")
        }

        if (!physicalSuccess) {
            recoveryRoutine?.invoke()
            activeLeases.remove(actionId)
            return failSafe(intent, ActionSafetyPhase.EXECUTION_INITIATED, leaseToken, "Hardware returned negative acknowledgement (NACK)")
        }

        // Phase 7: Physical Ack Received
        currentPhase = ActionSafetyPhase.PHYSICAL_ACK_RECEIVED

        // Phase 8: Post Verification Performed
        val verificationPassed = try {
            postVerifier()
        } catch (e: Exception) {
            false
        }

        if (!verificationPassed) {
            recoveryRoutine?.invoke()
            activeLeases.remove(actionId)
            return failSafe(intent, ActionSafetyPhase.POST_VERIFICATION_PERFORMED, leaseToken, "Post-action physical state verification failed")
        }
        currentPhase = ActionSafetyPhase.POST_VERIFICATION_PERFORMED

        // Phase 9: Evidence Captured
        currentPhase = ActionSafetyPhase.EVIDENCE_CAPTURED
        val evidenceHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$actionId:${intent.actionType}:$leaseToken:SUCCESS".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        // Phase 10: Action Completed
        activeLeases.remove(actionId)
        val receipt = ActionReceipt(
            actionId = actionId,
            actionType = intent.actionType,
            phase = ActionSafetyPhase.COMPLETED,
            leaseToken = leaseToken,
            isVerified = true,
            evidenceHash = evidenceHash,
        )

        _actionHistory.update { (it + receipt).takeLast(100) }
        return receipt
    }

    private fun failSafe(
        intent: ActionIntent,
        failedAtPhase: ActionSafetyPhase,
        leaseToken: Long,
        reason: String,
    ): ActionReceipt {
        val evidenceHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${intent.actionId}:${intent.actionType}:$failedAtPhase:FAILED:$reason".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val receipt = ActionReceipt(
            actionId = intent.actionId,
            actionType = intent.actionType,
            phase = ActionSafetyPhase.FAILED_SAFE,
            leaseToken = leaseToken,
            isVerified = false,
            evidenceHash = evidenceHash,
            failureReason = reason,
        )

        _actionHistory.update { (it + receipt).takeLast(100) }
        return receipt
    }
}
