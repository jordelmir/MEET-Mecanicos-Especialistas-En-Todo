package com.elysium369.meet.core.obd

/** Production boundary for the expert terminal. It is not a diagnostic authority. */
object DiagnosticRawCommandPolicy {
    data class Decision(
        val allowed: Boolean,
        val normalizedCommand: String,
        val reason: String,
    )

    private val readOnlyAdapterCommands = setOf(
        "ATI", "ATDP", "ATDPN", "ATRV",
    )

    private val readOnlyServices = setOf(
        "01", // current SAE data
        "02", // SAE freeze-frame data
        "06", // monitor observations; never DTC authority
        "09", // vehicle information
        "22", // UDS ReadDataByIdentifier
    )

    fun evaluate(command: String): Decision {
        val raw = command.trim().uppercase()
        if (raw.isBlank()) {
            return Decision(false, "", "Comando vacío.")
        }
        if (raw.startsWith("AT")) {
            if (!raw.matches(Regex("^AT ?[A-Z]{1,5}(?: ?[0-9A-F]{1,8})?$"))) {
                return Decision(false, "", "Sintaxis AT inválida; no se normalizó ni ejecutó.")
            }
            val normalized = raw.replace(" ", "")
            val allowed = normalized in readOnlyAdapterCommands
            return Decision(
                allowed = allowed,
                normalizedCommand = normalized,
                reason = if (allowed) {
                    "Consulta de estado del adaptador permitida."
                } else {
                    "Comando AT bloqueado: el terminal normal no modifica estado del adaptador."
                },
            )
        }

        if (!raw.matches(Regex("^[0-9A-F]{2}(?: ?[0-9A-F]{2})*$"))) {
            return Decision(false, "", "Sintaxis hexadecimal inválida; no se normalizó ni ejecutó.")
        }
        val normalized = raw.replace(" ", "")
        if (normalized.length > MAX_COMMAND_HEX_LENGTH) {
            return Decision(false, "", "Comando excede el límite de lectura revisado.")
        }

        val service = normalized.take(2)
        if (service in setOf("03", "07", "0A", "19")) {
            return Decision(
                false,
                normalized,
                "Los DTC solo se adquieren mediante DiagnosticAcquisitionEngine con evidencia y atribución ECU.",
            )
        }
        if (service !in readOnlyServices) {
            return Decision(
                false,
                normalized,
                "Servicio bloqueado: puede cambiar estado, memoria, sesión, seguridad o actuadores del vehículo.",
            )
        }
        val minimumLength = if (service == "22") 6 else 4
        if (normalized.length < minimumLength) {
            return Decision(false, "", "Comando incompleto para el servicio $service.")
        }
        return Decision(true, normalized, "Servicio explícitamente de solo lectura.")
    }

    private const val MAX_COMMAND_HEX_LENGTH = 64
}
