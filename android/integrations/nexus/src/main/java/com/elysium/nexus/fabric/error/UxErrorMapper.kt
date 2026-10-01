package com.elysium.nexus.fabric.error

import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.NexusError
import com.elysium.nexus.fabric.canonical.NexusErrorCode
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.dispatch.DispatchResult

/**
 * V06-P24 — Zero Failure Without Explanation (MASTER_ORDER §79–§81).
 *
 * Every terminal [DispatchResult] maps to a typed [NexusError] so the
 * automation engine reacts to the *category*, telemetry records the *code*
 * and the UI shows the user *what happened and what Elysium did* — in
 * English and Spanish, per the project's bilingual convention.
 *
 * "Cause" = what happened. "Action" = what Elysium already did about it.
 * The message templates on [NexusError.toUserMessage] stay protocol-facing;
 * the UI renders these [Explanation] blocks.
 */
data class Explanation(
    val title: String,
    val cause: String,
    val action: String
)

data class ErrorExplanation(
    val code: NexusErrorCode,
    val en: Explanation,
    val es: Explanation
) {
    fun forLanguage(spanish: Boolean): Explanation = if (spanish) es else en
}

object UxErrorMapper {

    /**
     * Maps every terminal dispatch outcome to a typed, retryable-aware
     * [NexusError]. Returns null only for [DispatchResult.Success].
     */
    fun fromDispatchResult(result: DispatchResult): NexusError? = when (result) {
        is DispatchResult.Success -> null
        is DispatchResult.NoTarget -> NexusError.IdentityNotFound(
            message = "No identity known for device ${result.deviceId.value}",
            deviceId = result.deviceId
        )
        is DispatchResult.NoRoute -> NexusError.DeviceUnreachable(
            message = "No available route for ${result.target.deviceId.value}",
            deviceId = result.target.deviceId
        )
        is DispatchResult.PermissionDenied -> NexusError.PermissionDenied(
            message = "Missing permissions: ${result.missing.joinToString(", ")}",
            missingPermissions = result.missing
        )
        is DispatchResult.TranslationFailed -> NexusError.ProtocolError(
            message = "Translation failed via ${result.route.protocol}",
            protocol = result.route.protocol,
            deviceId = result.action.targetDeviceId
        )
        is DispatchResult.AdapterFailed -> fromAdapterError(
            result.writeError,
            protocol = result.route.protocol,
            deviceId = result.action.targetDeviceId
        )
        is DispatchResult.AllRoutesFailed -> {
            val isBreaker = result.reason.contains("Circuit open")
            if (isBreaker) {
                NexusError.ResourceExhausted(
                    message = result.reason
                )
            } else {
                NexusError.NetworkError(
                    message = result.reason
                )
            }
        }
    }

    /**
     * Maps an adapter-level [WriteResult.Error] to the taxonomy
     * (ErrorCode → NexusErrorCode, category not message).
     */
    fun fromAdapterError(
        error: WriteResult.Error,
        protocol: Protocol? = null,
        deviceId: DeviceId? = null
    ): NexusError = when (error.code) {
        ErrorCode.NotStarted -> NexusError.HardwareUnavailable(
            message = error.message
        )
        ErrorCode.AlreadyStarted -> NexusError.HardwareUnavailable(
            message = error.message
        )
        ErrorCode.DeviceNotFound -> NexusError.DeviceUnreachable(
            message = error.message,
            deviceId = deviceId
        )
        ErrorCode.DeviceOffline -> NexusError.DeviceUnreachable(
            message = error.message,
            deviceId = deviceId
        )
        ErrorCode.AuthFailed -> NexusError.AuthFailed(
            message = error.message,
            protocol = protocol,
            deviceId = deviceId
        )
        ErrorCode.PermissionDenied -> NexusError.PermissionDenied(
            message = error.message
        )
        ErrorCode.UnsupportedOperation -> NexusError.CommandUnsupported(
            message = error.message,
            deviceId = deviceId
        )
        ErrorCode.NetworkError -> NexusError.NetworkError(
            message = error.message,
            protocol = protocol
        )
        ErrorCode.Unknown -> NexusError.ProtocolError(
            message = error.message,
            protocol = protocol,
            deviceId = deviceId
        )
        ErrorCode.Timeout -> NexusError.Timeout(
            message = error.message,
            protocol = protocol,
            deviceId = deviceId
        )
        ErrorCode.HardwareUnavailable -> NexusError.HardwareUnavailable(
            message = error.message
        )
    }

    /** Telemetry + automation code for a terminal outcome (null = success). */
    fun codeFor(result: DispatchResult): NexusErrorCode? =
        fromDispatchResult(result)?.code

    /**
     * Bilingual explanation dictionary. Completeness is test-enforced:
     * every [NexusErrorCode] must have both `en` and `es` entries with a
     * title, a cause ("what happened") and an action ("what Elysium did").
     */
    fun explanation(code: NexusErrorCode): ErrorExplanation? = all().firstOrNull { it.code == code }

    fun all(): List<ErrorExplanation> = listOf(
        ErrorExplanation(
            code = NexusErrorCode.DiscoveryFailed,
            en = Explanation(
                title = "Device discovery failed",
                cause = "No devices answered on the network.",
                action = "Elysium stopped the scan and will retry."
            ),
            es = Explanation(
                title = "No se pudo descubrir el dispositivo",
                cause = "Ningún dispositivo respondió en la red.",
                action = "Elysium detuvo el escaneo y reintentará."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.DeviceUnreachable,
            en = Explanation(
                title = "Device unreachable",
                cause = "The device did not respond on any route.",
                action = "Elysium checked every transport and stopped."
            ),
            es = Explanation(
                title = "Dispositivo inalcanzable",
                cause = "El dispositivo no respondió por ninguna vía.",
                action = "Elysium comprobó todos los transportes y se detuvo."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.IdentityNotFound,
            en = Explanation(
                title = "Device identity unknown",
                cause = "This device has no verified identity.",
                action = "Elysium did not dispatch to an unknown device."
            ),
            es = Explanation(
                title = "Identidad de dispositivo desconocida",
                cause = "Este dispositivo no tiene una identidad verificada.",
                action = "Elysium no envió comandos a un dispositivo desconocido."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.SignatureVerificationFailed,
            en = Explanation(
                title = "Security check failed",
                cause = "The device signature did not verify.",
                action = "Elysium refused the peer to protect you."
            ),
            es = Explanation(
                title = "Fallo de verificación de seguridad",
                cause = "La firma del dispositivo no se verificó.",
                action = "Elysium rechazó al dispositivo para protegerte."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.PairingFailed,
            en = Explanation(
                title = "Pairing failed",
                cause = "The device rejected the pairing attempt.",
                action = "Elysium stopped pairing; try again."
            ),
            es = Explanation(
                title = "El emparejamiento falló",
                cause = "El dispositivo rechazó el intento de emparejamiento.",
                action = "Elysium detuvo el emparejamiento; inténtalo de nuevo."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.PairingTimeout,
            en = Explanation(
                title = "Pairing timed out",
                cause = "The device never answered the pairing request.",
                action = "Elysium cancelled and will let you retry."
            ),
            es = Explanation(
                title = "El emparejamiento expiró",
                cause = "El dispositivo nunca respondió a la solicitud.",
                action = "Elysium canceló y te permitirá reintentar."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.AuthFailed,
            en = Explanation(
                title = "Authentication failed",
                cause = "The credentials were rejected.",
                action = "Elysium did not repeat the request."
            ),
            es = Explanation(
                title = "Fallo de autenticación",
                cause = "Las credenciales fueron rechazadas.",
                action = "Elysium no repitió la solicitud."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.TokenExpired,
            en = Explanation(
                title = "Session expired",
                cause = "The stored token is no longer valid.",
                action = "Elysium is refreshing the session."
            ),
            es = Explanation(
                title = "Sesión caducada",
                cause = "El token guardado ya no es válido.",
                action = "Elysium está renovando la sesión."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.NetworkError,
            en = Explanation(
                title = "Network error",
                cause = "The transport link failed.",
                action = "Elysium retried on the same route."
            ),
            es = Explanation(
                title = "Error de red",
                cause = "El enlace de transporte falló.",
                action = "Elysium reintentó por la misma vía."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.ProtocolError,
            en = Explanation(
                title = "Protocol error",
                cause = "The protocol rejected the operation.",
                action = "Elysium tried an alternate route."
            ),
            es = Explanation(
                title = "Error de protocolo",
                cause = "El protocolo rechazó la operación.",
                action = "Elysium probó una ruta alternativa."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.Timeout,
            en = Explanation(
                title = "Operation timed out",
                cause = "The device did not answer in time.",
                action = "Elysium stopped waiting and reported the failure."
            ),
            es = Explanation(
                title = "La operación expiró",
                cause = "El dispositivo no respondió a tiempo.",
                action = "Elysium dejó de esperar y reportó el fallo."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.CommandRejected,
            en = Explanation(
                title = "Command rejected",
                cause = "The device refused the command.",
                action = "Elysium did not repeat it."
            ),
            es = Explanation(
                title = "Comando rechazado",
                cause = "El dispositivo se negó a ejecutar el comando.",
                action = "Elysium no lo repitió."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.CommandUnsupported,
            en = Explanation(
                title = "Command not supported",
                cause = "This device cannot perform the action.",
                action = "Elysium marks the capability as unavailable here."
            ),
            es = Explanation(
                title = "Comando no soportado",
                cause = "Este dispositivo no puede realizar la acción.",
                action = "Elysium marca la capacidad como no disponible aquí."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.StateReconciliationFailed,
            en = Explanation(
                title = "State check failed",
                cause = "The device state did not match the expectation.",
                action = "Elysium retried the observation."
            ),
            es = Explanation(
                title = "Fallo en la verificación de estado",
                cause = "El estado del dispositivo no coincidió con lo esperado.",
                action = "Elysium reintentó la observación."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.StateUnknown,
            en = Explanation(
                title = "Device state unknown",
                cause = "No state is observable for this device.",
                action = "Elysium keeps the action unconfirmed."
            ),
            es = Explanation(
                title = "Estado del dispositivo desconocido",
                cause = "No se puede observar el estado de este dispositivo.",
                action = "Elysium mantiene la acción sin confirmar."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.PermissionDenied,
            en = Explanation(
                title = "Permission required",
                cause = "Android/OS permission is missing for this transport.",
                action = "Elysium shows the exact permission to grant."
            ),
            es = Explanation(
                title = "Permiso requerido",
                cause = "Falta un permiso del sistema para este transporte.",
                action = "Elysium muestra el permiso exacto que debes otorgar."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.HardwareUnavailable,
            en = Explanation(
                title = "Hardware unavailable",
                cause = "The required hardware is missing or busy.",
                action = "Elysium routed around it or stopped."
            ),
            es = Explanation(
                title = "Hardware no disponible",
                cause = "El hardware necesario falta o está ocupado.",
                action = "Elysium buscó otra vía o se detuvo."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.ResourceExhausted,
            en = Explanation(
                title = "Circuit open",
                cause = "Repeated failures opened the protection circuit.",
                action = "Elysium is cooling down before trying again."
            ),
            es = Explanation(
                title = "Circuito abierto",
                cause = "Los fallos repetidos abrieron el circuito de protección.",
                action = "Elysium está en enfriamiento antes de reintentar."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.InvalidConfig,
            en = Explanation(
                title = "Configuration error",
                cause = "The stored configuration is invalid.",
                action = "Elysium ignored it and asks you to check settings."
            ),
            es = Explanation(
                title = "Error de configuración",
                cause = "La configuración guardada no es válida.",
                action = "Elysium la ignoró y te pide revisar los ajustes."
            )
        ),
        ErrorExplanation(
            code = NexusErrorCode.SchemaVersionMismatch,
            en = Explanation(
                title = "Data format mismatch",
                cause = "A stored record belongs to an older format.",
                action = "Elysium handles the migration or ignores the record."
            ),
            es = Explanation(
                title = "Formato de datos incompatible",
                cause = "Un registro guardado pertenece a un formato antiguo.",
                action = "Elysium gestiona la migración o ignora el registro."
            )
        )
    )
}