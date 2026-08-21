package com.elysium369.meet.core.evair.domain

import kotlinx.serialization.Serializable

/**
 * VehicleCommand — Sealed interface for all commands that can be sent to the vehicle.
 *
 * The AI PROPOSES commands. The Safety Broker AUTHORIZES them.
 * The AI never executes directly.
 *
 * Safety Levels:
 * - LEVEL 0: READ ONLY (auto-allowed)
 * - LEVEL 1: NON-DESTRUCTIVE DIAGNOSTIC (policy check)
 * - LEVEL 2: ACTIVE TEST (vehicle stopped + user confirmation + timeout)
 * - LEVEL 3: STATE MODIFYING (explicit consent always)
 * - LEVEL 4: SAFETY CRITICAL (DENIED BY DESIGN for autonomous AI)
 */
@Serializable
sealed interface VehicleCommand {
    val requestId: String
    val safetyLevel: SafetyLevel

    /** Level 0 — Read a single PID value */
    @Serializable
    data class ReadPid(
        override val requestId: String,
        val pid: String,
    ) : VehicleCommand {
        override val safetyLevel: SafetyLevel = SafetyLevel.READ_ONLY
    }

    /** Level 0 — Read multiple PID values */
    @Serializable
    data class ReadPids(
        override val requestId: String,
        val pids: Set<String>,
    ) : VehicleCommand {
        override val safetyLevel: SafetyLevel = SafetyLevel.READ_ONLY
    }

    /** Level 0 — Read diagnostic trouble codes */
    @Serializable
    data class ReadDtcs(
        override val requestId: String,
    ) : VehicleCommand {
        override val safetyLevel: SafetyLevel = SafetyLevel.READ_ONLY
    }

    /** Level 0 — Read freeze frame data */
    @Serializable
    data class ReadFreezeFrame(
        override val requestId: String,
    ) : VehicleCommand {
        override val safetyLevel: SafetyLevel = SafetyLevel.READ_ONLY
    }

    /** Level 0 — Read Mode 06 test results */
    @Serializable
    data class ReadMode06(
        override val requestId: String,
    ) : VehicleCommand {
        override val safetyLevel: SafetyLevel = SafetyLevel.READ_ONLY
    }

    /** Level 0 — Read readiness monitors */
    @Serializable
    data class ReadReadiness(
        override val requestId: String,
    ) : VehicleCommand {
        override val safetyLevel: SafetyLevel = SafetyLevel.READ_ONLY
    }

    /** Level 2 — Run a diagnostic test (requires vehicle stopped + confirmation) */
    @Serializable
    data class RunDiagnosticTest(
        override val requestId: String,
        val testId: String,
        val testParameters: Map<String, String> = emptyMap(),
        val timeoutMs: Long = 30_000L,
    ) : VehicleCommand {
        override val safetyLevel: SafetyLevel = SafetyLevel.ACTIVE_TEST
    }

    /** Level 3 — Clear DTCs (requires explicit consent) */
    @Serializable
    data class ClearDtcs(
        override val requestId: String,
    ) : VehicleCommand {
        override val safetyLevel: SafetyLevel = SafetyLevel.STATE_MODIFYING
    }
}

@Serializable
enum class SafetyLevel {
    /** Auto-allowed. Read PIDs, DTCs, freeze frame, Mode 06, readiness */
    READ_ONLY,
    /** May require policy check. Polling config, controlled queries */
    NON_DESTRUCTIVE_DIAGNOSTIC,
    /** Vehicle stopped + user confirmation + timeout + auto-stop */
    ACTIVE_TEST,
    /** Explicit consent always. Clear DTC, reset adaptation */
    STATE_MODIFYING,
    /** DENIED BY DESIGN for autonomous AI. Braking, steering, throttle */
    SAFETY_CRITICAL,
}

/**
 * ProposedVehicleAction — What the AI agent proposes to do.
 *
 * The AI does NOT execute. It PROPOSES. The SafetyBroker decides.
 *
 * Pipeline:
 * Antigravity → ProposedVehicleAction → schema validation →
 * SafetyBroker → authorization → user confirmation if needed →
 * VehicleActionExecutor → ObdSession → evidence capture
 */
@Serializable
data class ProposedVehicleAction(
    val actionId: String,
    val command: VehicleCommand,
    val reason: String,
    val expectedObservation: String,
    val risk: ActionRisk,
    val proposedByAgent: String = "",
)

@Serializable
enum class ActionRisk {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

/**
 * AuthorizationResult — The Safety Broker's verdict.
 */
@Serializable
sealed interface AuthorizationResult {
    @Serializable
    data object Allowed : AuthorizationResult

    @Serializable
    data class RequiresConfirmation(
        val reason: String,
    ) : AuthorizationResult

    @Serializable
    data class Denied(
        val reason: String,
    ) : AuthorizationResult
}
