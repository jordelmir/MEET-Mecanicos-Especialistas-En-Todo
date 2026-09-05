package com.elysium369.meet.ecu.agent

/**
 * Section 19, 20 & 108: Elysium Programming Agent Local IPC Contract.
 *
 * DOCTRINE:
 * The Programming Agent runs out-of-process to isolate vendor J2534 DLLs,
 * 32/64-bit cross-bitness bridges, and native SocketCAN drivers.
 * A driver crash or memory corruption in the vendor library NEVER corrupts the core app.
 */

enum class AgentCommandType {
    ATTACH_ADAPTER,
    ACQUIRE_ECU_IDENTITY,
    EXECUTE_READ_BACKUP,
    VALIDATE_PREFLIGHT,
    DISPATCH_PROGRAMMING_SESSION,
    ABORT_SESSION,
    DISPATCH_RECOVERY_KERNEL,
    DETACH_ADAPTER,
}

data class AgentIpcRequest(
    val requestId: String,
    val commandType: AgentCommandType,
    val targetEcuAddress: String,
    val payloadHex: String? = null,
    val leaseToken: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class AgentResponseStatus {
    SUCCESS,
    PREFLIGHT_REJECTED,
    TIMEOUT,
    NEGATIVE_RESPONSE,
    POWER_UNSTABLE,
    DRIVER_CRASH_ISOLATED,
    RECOVERY_MANDATED,
    FAILED,
}

data class AgentIpcResponse(
    val requestId: String,
    val status: AgentResponseStatus,
    val statusCode: Int = 0,
    val responsePayloadHex: String? = null,
    val diagnosticNrc: Int? = null,
    val observedVoltage: Double? = null,
    val errorMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val isSuccess: Boolean
        get() = status == AgentResponseStatus.SUCCESS
}
