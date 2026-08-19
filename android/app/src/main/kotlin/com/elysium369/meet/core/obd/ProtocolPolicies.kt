package com.elysium369.meet.core.obd

enum class ObdOperationType {
    LIVE_PID_FAST,
    DTC_READ,
    FREEZE_FRAME,
    MODE06_MONITORS,
    UDS_SESSION_CONTROL,
    DTC_CLEAR_COMMAND,
    DTC_CLEAR_VERIFICATION,
}

object ProtocolTimeoutPolicy {
    fun getTimeoutMs(protocol: ObdProtocol, operation: ObdOperationType, isClone: Boolean): Long {
        val isCan = protocol.name.contains("CAN") || protocol.displayName.contains("CAN", ignoreCase = true)
        val isLegacy = protocol in setOf(
            ObdProtocol.ISO9141,
            ObdProtocol.KWP2000,
            ObdProtocol.KWP2000_FAST,
            ObdProtocol.J1850_PWM,
            ObdProtocol.J1850_VPW
        )

        return when (operation) {
            ObdOperationType.LIVE_PID_FAST -> when {
                isCan && !isClone -> 200L
                isCan -> 350L
                isLegacy -> 600L
                else -> 400L
            }
            ObdOperationType.DTC_READ -> when {
                isCan -> 800L
                isLegacy -> 1800L
                else -> 1200L
            }
            ObdOperationType.FREEZE_FRAME -> when {
                isCan -> 1000L
                isLegacy -> 2200L
                else -> 1500L
            }
            ObdOperationType.MODE06_MONITORS -> when {
                isCan -> 1200L
                isLegacy -> 3000L
                else -> 2000L
            }
            ObdOperationType.UDS_SESSION_CONTROL -> 1000L
            ObdOperationType.DTC_CLEAR_COMMAND -> 2000L
            ObdOperationType.DTC_CLEAR_VERIFICATION -> when {
                isCan -> 600L
                isLegacy -> 1500L
                else -> 1000L
            }
        }
    }

    fun getElmTimeoutCommand(protocol: ObdProtocol, operation: ObdOperationType, isClone: Boolean): String {
        val isCan = protocol.name.contains("CAN") || protocol.displayName.contains("CAN", ignoreCase = true)
        return when (operation) {
            ObdOperationType.LIVE_PID_FAST -> if (isCan && !isClone) "ATST20" else "ATST32"
            ObdOperationType.DTC_CLEAR_COMMAND -> "ATST64"
            ObdOperationType.DTC_CLEAR_VERIFICATION -> if (isCan) "ATST32" else "ATST64"
            else -> if (isCan) "ATST40" else "ATST96"
        }
    }
}

object ProtocolKeepAlivePolicy {
    fun shouldSendKeepAlive(
        protocol: ObdProtocol,
        isUdsSessionActive: Boolean,
        idleDurationMs: Long,
    ): Boolean = when {
        isUdsSessionActive -> idleDurationMs >= 1500L
        protocol in setOf(ObdProtocol.ISO9141, ObdProtocol.KWP2000, ObdProtocol.KWP2000_FAST) -> idleDurationMs >= 2500L
        else -> false // Generic CAN with continuous polling does not require artificial heartbeats
    }

    fun getKeepAliveCommand(isUdsSessionActive: Boolean): String =
        if (isUdsSessionActive) "3E80\r" else "0100\r"
}
