package com.elysium369.meet.core.obd

/** Production boundary for the expert terminal. It is not a diagnostic authority. */
object DiagnosticRawCommandPolicy {
    data class Decision(
        val allowed: Boolean,
        val normalizedCommand: String,
        val reason: String,
    )

    private val allowedAdapterPrefixes = setOf(
        "ATZ", "ATI", "ATE", "ATL", "ATS", "ATH", "ATSP", "ATDP", "ATDPN",
        "ATRV", "ATAL", "ATCAF", "ATCFC", "ATSH",
    )

    private val readOnlyServices = setOf(
        "01", // current SAE data
        "02", // SAE freeze-frame data
        "06", // monitor observations; never DTC authority
        "09", // vehicle information
        "22", // UDS ReadDataByIdentifier
    )

    fun evaluate(command: String): Decision {
        val normalized = command.uppercase().filter { it.isLetterOrDigit() }
        if (normalized.isBlank()) {
            return Decision(false, normalized, "Comando vacío.")
        }
        if (normalized.startsWith("AT")) {
            val allowed = allowedAdapterPrefixes.any(normalized::startsWith)
            return Decision(
                allowed = allowed,
                normalizedCommand = normalized,
                reason = if (allowed) {
                    "Configuración local de adaptador permitida."
                } else {
                    "Comando AT fuera de la lista de producción revisada."
                },
            )
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
        return Decision(true, normalized, "Servicio explícitamente de solo lectura.")
    }
}
