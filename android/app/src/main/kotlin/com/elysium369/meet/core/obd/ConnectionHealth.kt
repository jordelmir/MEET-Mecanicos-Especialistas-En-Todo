package com.elysium369.meet.core.obd

enum class TransportHealth {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DEGRADED,
    FAILED,
}

enum class AdapterHealth {
    UNKNOWN,
    SYNCHRONIZED,
    CLONE_CONSERVATIVE,
    STN_ADVANCED,
    PROMPT_LOST,
}

enum class ProtocolHealth {
    SEARCHING,
    LOCKED,
    UNSUPPORTED,
    TIMED_OUT,
}

enum class EcuHealth {
    UNKNOWN,
    RESPONSIVE,
    NO_DATA,
    UNRESPONSIVE,
}

enum class RecoveryState {
    IDLE,
    FAST_RECONNECTING,
    WARM_RECOVERING,
    FULL_DISCOVERY,
}

data class ConnectionHealth(
    val transport: TransportHealth = TransportHealth.DISCONNECTED,
    val adapter: AdapterHealth = AdapterHealth.UNKNOWN,
    val protocol: ProtocolHealth = ProtocolHealth.SEARCHING,
    val ecu: EcuHealth = EcuHealth.UNKNOWN,
    val lastSuccessfulExchangeMs: Long = 0L,
    val consecutiveTimeouts: Int = 0,
    val rollingErrorRate: Double = 0.0,
    val ewmaLatencyMs: Long = 0L,
    val recoveryState: RecoveryState = RecoveryState.IDLE,
) {
    val isFullyFunctional: Boolean
        get() = transport == TransportHealth.CONNECTED &&
                adapter in setOf(AdapterHealth.SYNCHRONIZED, AdapterHealth.CLONE_CONSERVATIVE, AdapterHealth.STN_ADVANCED) &&
                protocol == ProtocolHealth.LOCKED &&
                ecu == EcuHealth.RESPONSIVE

    val displaySummary: String
        get() = when {
            recoveryState != RecoveryState.IDLE -> "● Recuperando conexión..."
            transport != TransportHealth.CONNECTED -> "● Adaptador desconectado"
            ecu == EcuHealth.UNRESPONSIVE -> "● Adaptador conectado / ECU no responde"
            rollingErrorRate > 0.3 -> "● Conexión degradada"
            ewmaLatencyMs > 300L -> "● Conexión lenta"
            isFullyFunctional -> "● Enlace estable (100%)"
            else -> "● Enlace activo"
        }
}
