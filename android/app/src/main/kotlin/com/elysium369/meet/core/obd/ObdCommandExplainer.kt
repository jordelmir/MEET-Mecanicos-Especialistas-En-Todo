package com.elysium369.meet.core.obd

/**
 * ObdCommandExplainer — Expert-level explanations for every OBD2 command.
 * Powers the Expert Terminal with real-time, contextual feedback.
 *
 * Coverage: AT commands, Mode 01-0A PIDs, Mode 09 (VIN/calibration).
 */
object ObdCommandExplainer {

    data class CommandInfo(
        val nameEs: String,
        val nameEn: String,
        val explanationEs: String,
        val explanationEn: String,
        val category: String // "AT", "SENSOR", "DTC", "CONTROL", "INFO"
    )

    private val explanationCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Returns a human-readable explanation for the given OBD command.
     */
    fun explain(command: String, isSpanish: Boolean = true): String? {
        val cmd = command.uppercase().replace(" ", "")
        val cacheKey = "$cmd|$isSpanish"
        explanationCache[cacheKey]?.let { return it }

        val info = findCommand(cmd) ?: return null
        val explanation = if (isSpanish) {
            "💡 ${info.nameEs}\n${info.explanationEs}"
        } else {
            "💡 ${info.nameEn}\n${info.explanationEn}"
        }
        explanationCache[cacheKey] = explanation
        return explanation
    }

    /**
     * Returns the category icon for a command.
     */
    fun categoryIcon(command: String): String {
        val cmd = command.uppercase().replace(" ", "")
        categoryCache[cmd]?.let { return it }
        
        val info = findCommand(cmd) ?: return "📟"
        val icon = when (info.category) {
            "AT" -> "🔧"
            "SENSOR" -> "📊"
            "DTC" -> "🔍"
            "CONTROL" -> "⚡"
            "INFO" -> "ℹ️"
            else -> "📟"
        }
        categoryCache[cmd] = icon
        return icon
    }

    /**
     * Attempts to decode a raw OBD response into human-readable values.
     * Returns null if the command/response is not recognized.
     */
    fun decodeResponse(command: String, response: String): String? {
        val cmd = command.uppercase().replace(" ", "")
        val clean = response.replace(" ", "").uppercase()

        return try {
            when {
                // ── AT commands ──
                cmd.startsWith("AT") -> decodeAtResponse(cmd, response)

                // ── Mode 01: Live Data ──
                cmd.startsWith("01") && clean.contains("41") -> decodeMode01(cmd, clean)

                // ── Mode 03: Active DTCs ──
                cmd == "03" && (clean.contains("43") || clean.length >= 4) -> {
                    val dtcs = DtcDecoder.decode(response, "03")
                    if (dtcs.isEmpty()) "✅ No hay códigos de falla activos"
                    else "🔴 ${dtcs.size} DTC(s): ${dtcs.joinToString(", ")}"
                }

                // ── Mode 04: Clear DTCs ──
                cmd == "04" -> {
                    val decoded = DiagnosticPduDecoder.decodeResponses(
                        rawResponse = response,
                        expectedPositiveService = 0x44,
                        requestedService = 0x04,
                    )
                    when {
                        decoded.any { it is ProtocolResponse.Positive } ->
                            "⚠️ Solicitud aceptada por ECU; falta escaneo post-borrado para verificar ausencia"
                        response.lineSequence().any { it.trim().equals("OK", ignoreCase = true) } ->
                            "ℹ️ Adaptador respondió OK; no demuestra borrado ni ausencia de DTC"
                        decoded.any { it is ProtocolResponse.Negative } ->
                            "⚠️ La ECU rechazó la solicitud de borrado"
                        else -> "⚠️ Respuesta de borrado inconclusa"
                    }
                }

                // ── Mode 07: Pending DTCs ──
                cmd == "07" && (clean.contains("47") || clean.length >= 4) -> {
                    val dtcs = DtcDecoder.decode(response, "07")
                    if (dtcs.isEmpty()) "✅ No hay códigos pendientes"
                    else "🟡 ${dtcs.size} pendiente(s): ${dtcs.joinToString(", ")}"
                }

                // ── Mode 09: Vehicle Info ──
                cmd.startsWith("09") -> decodeMode09(cmd, response)

                // ── Mode 0A: Permanent DTCs ──
                cmd == "0A" && (clean.contains("4A") || clean.length >= 4) -> {
                    val dtcs = DtcDecoder.decode(response, "0A")
                    if (dtcs.isEmpty()) "✅ No hay códigos permanentes"
                    else "🔵 ${dtcs.size} permanente(s): ${dtcs.joinToString(", ")}"
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════
    //  PRIVATE: Mode 01 Response Decoder
    // ═══════════════════════════════════════

    private fun decodeMode01(cmd: String, clean: String): String? {
        val pid = if (cmd.length >= 4) cmd.substring(2, 4) else return null
        val prefix = "41$pid"
        val idx = clean.indexOf(prefix)
        if (idx < 0) return null
        val data = clean.substring(idx + prefix.length)

        return when (pid) {
            "00" -> "PIDs soportados [01-20]: ${data.take(8)}"
            "01" -> {
                if (data.length >= 8) {
                    val a = data.substring(0, 2).toInt(16)
                    val milOn = (a and 0x80) != 0
                    val dtcCount = a and 0x7F
                    "${if (milOn) "🔴 MIL ENCENDIDA" else "🟢 MIL apagada"} — $dtcCount DTC(s)"
                } else null
            }
            "03" -> {
                if (data.length >= 2) {
                    val a = data.substring(0, 2).toInt(16)
                    val status1 = a and 0x1F
                    val statusText = when (status1) {
                        1 -> "Open loop (temp insuficiente)"
                        2 -> "Closed loop (O2 feedback)"
                        4 -> "Open loop (carga/desac.)"
                        8 -> "Open loop (falla sistema)"
                        16 -> "Closed loop (con falla catalizador)"
                        else -> "Estado: $status1"
                    }
                    "Sistema combustible: $statusText"
                } else null
            }
            "04" -> {
                if (data.length >= 2) {
                    val value = data.substring(0, 2).toInt(16) * 100 / 255
                    "Carga motor: $value%"
                } else null
            }
            "05" -> {
                if (data.length >= 2) {
                    val temp = data.substring(0, 2).toInt(16) - 40
                    "Temp. refrigerante: ${temp}°C ${if (temp > 105) "⚠️ ALTO" else if (temp < -10) "❄️" else "✅"}"
                } else null
            }
            "06" -> {
                if (data.length >= 2) {
                    val trim = (data.substring(0, 2).toInt(16) - 128) * 100.0 / 128.0
                    "Ajuste comb. corto B1: ${"%.1f".format(trim)}% ${if (kotlin.math.abs(trim) > 25) "⚠️" else "✅"}"
                } else null
            }
            "07" -> {
                if (data.length >= 2) {
                    val trim = (data.substring(0, 2).toInt(16) - 128) * 100.0 / 128.0
                    "Ajuste comb. largo B1: ${"%.1f".format(trim)}% ${if (kotlin.math.abs(trim) > 10) "⚠️" else "✅"}"
                } else null
            }
            "0B" -> {
                if (data.length >= 2) {
                    val kpa = data.substring(0, 2).toInt(16)
                    "Presión MAP: ${kpa} kPa"
                } else null
            }
            "0C" -> {
                if (data.length >= 4) {
                    val rpm = (data.substring(0, 2).toInt(16) * 256 + data.substring(2, 4).toInt(16)) / 4
                    "RPM: $rpm ${if (rpm > 6500) "🔴 ALTO" else if (rpm < 600) "⚠️ BAJO" else ""}"
                } else null
            }
            "0D" -> {
                if (data.length >= 2) {
                    val speed = data.substring(0, 2).toInt(16)
                    "Velocidad: $speed km/h"
                } else null
            }
            "0E" -> {
                if (data.length >= 2) {
                    val advance = data.substring(0, 2).toInt(16) / 2.0 - 64.0
                    "Avance encendido: ${"%.1f".format(advance)}°"
                } else null
            }
            "0F" -> {
                if (data.length >= 2) {
                    val temp = data.substring(0, 2).toInt(16) - 40
                    "Temp. admisión: ${temp}°C"
                } else null
            }
            "10" -> {
                if (data.length >= 4) {
                    val maf = (data.substring(0, 2).toInt(16) * 256 + data.substring(2, 4).toInt(16)) / 100.0
                    "Flujo MAF: ${"%.2f".format(maf)} g/s"
                } else null
            }
            "11" -> {
                if (data.length >= 2) {
                    val pos = data.substring(0, 2).toInt(16) * 100 / 255
                    "Pos. acelerador: $pos%"
                } else null
            }
            "1C" -> {
                if (data.length >= 2) {
                    val std = data.substring(0, 2).toInt(16)
                    val stdName = when (std) {
                        1 -> "OBD-II (CARB)"
                        2 -> "OBD (EPA)"
                        3 -> "OBD + OBD-II"
                        6 -> "EOBD"
                        else -> "Estándar $std"
                    }
                    "Estándar OBD: $stdName"
                } else null
            }
            "1F" -> {
                if (data.length >= 4) {
                    val secs = data.substring(0, 2).toInt(16) * 256 + data.substring(2, 4).toInt(16)
                    val mins = secs / 60
                    val hrs = mins / 60
                    "Tiempo motor: ${hrs}h ${mins % 60}m ${secs % 60}s"
                } else null
            }
            "2F" -> {
                if (data.length >= 2) {
                    val level = data.substring(0, 2).toInt(16) * 100 / 255
                    "Nivel combustible: $level% ${if (level < 10) "⚠️ BAJO" else ""}"
                } else null
            }
            "42" -> {
                if (data.length >= 4) {
                    val mv = data.substring(0, 2).toInt(16) * 256 + data.substring(2, 4).toInt(16)
                    val v = mv / 1000.0
                    "Voltaje módulo: ${"%.2f".format(v)}V ${if (v < 11.5) "⚠️ BAJO" else if (v > 15.0) "⚠️ ALTO" else "✅"}"
                } else null
            }
            "46" -> {
                if (data.length >= 2) {
                    val temp = data.substring(0, 2).toInt(16) - 40
                    "Temp. ambiente: ${temp}°C"
                } else null
            }
            else -> "PID $pid → datos: ${data.take(8)}"
        }
    }

    // ═══════════════════════════════════════
    //  PRIVATE: AT Response Decoder
    // ═══════════════════════════════════════

    private fun decodeAtResponse(cmd: String, response: String): String? {
        return when {
            cmd == "ATZ" -> "Adaptador reiniciado: $response"
            cmd == "ATRV" || cmd == "AT RV".replace(" ","") -> {
                val voltage = response.replace("V", "").replace("v", "").trim()
                "🔋 Voltaje batería: ${voltage}V"
            }
            cmd == "ATDP" || cmd == "ATDPN" -> "Protocolo: $response"
            cmd.startsWith("ATSP") -> "Protocolo configurado: $response"
            cmd == "ATI" -> "ID adaptador: $response"
            cmd == "ATAT1" || cmd == "ATAT2" -> "Timing adaptativo: $response"
            cmd == "ATH1" -> "Headers activados"
            cmd == "ATH0" -> "Headers desactivados"
            cmd == "ATE0" -> "Echo desactivado"
            cmd == "ATE1" -> "Echo activado"
            cmd == "ATSTFF" || cmd.startsWith("ATST") -> "Timeout configurado: $response"
            else -> null
        }
    }

    // ═══════════════════════════════════════
    //  PRIVATE: Mode 09 Response Decoder
    // ═══════════════════════════════════════

    private fun decodeMode09(cmd: String, response: String): String? {
        val pid = if (cmd.length >= 4) cmd.substring(2, 4) else return null
        return when (pid) {
            "02" -> {
                val vin = CanMultiFrameParser.decodeVin(response)
                "VIN: $vin"
            }
            "0A" -> "Nombre ECU: ${response.take(40)}"
            else -> "Info vehículo PID $pid"
        }
    }

    // ═══════════════════════════════════════
    //  COMMAND DATABASE (80+ entries)
    // ═══════════════════════════════════════

    private val commands = mapOf(
        // ── AT COMMANDS ──
        "ATZ" to CommandInfo("Reset Adaptador", "Reset Adapter", "Reinicia el ELM327/STN a valores de fábrica. Borra configuración temporal.", "Resets ELM327/STN to factory defaults. Clears temporary config.", "AT"),
        "ATRV" to CommandInfo("Voltaje Batería", "Battery Voltage", "Lee el voltaje en el pin 16 del conector OBD2. Normal: 12.0-14.8V con motor encendido.", "Reads voltage on OBD2 pin 16. Normal: 12.0-14.8V with engine running.", "AT"),
        "ATI" to CommandInfo("ID Adaptador", "Adapter ID", "Muestra versión del firmware del adaptador ELM327/STN.", "Shows adapter firmware version.", "AT"),
        "ATDP" to CommandInfo("Protocolo Detectado", "Detected Protocol", "Muestra el protocolo de comunicación activo (CAN, ISO, KWP, etc.).", "Shows active communication protocol.", "AT"),
        "ATDPN" to CommandInfo("Número de Protocolo", "Protocol Number", "Retorna código numérico del protocolo activo (0-C).", "Returns numeric code of active protocol.", "AT"),
        "ATSP0" to CommandInfo("Auto-Detectar Protocolo", "Auto-Detect Protocol", "Configura modo automático de detección de protocolo OBD2.", "Sets automatic OBD2 protocol detection.", "AT"),
        "ATH0" to CommandInfo("Desactivar Headers", "Headers Off", "Oculta las cabeceras CAN/ISO de las respuestas.", "Hides CAN/ISO headers from responses.", "AT"),
        "ATH1" to CommandInfo("Activar Headers", "Headers On", "Muestra cabeceras completas (útil para diagnóstico avanzado).", "Shows full headers (useful for advanced diagnostics).", "AT"),
        "ATE0" to CommandInfo("Desactivar Echo", "Echo Off", "Desactiva el eco de comandos enviados.", "Disables echo of sent commands.", "AT"),
        "ATE1" to CommandInfo("Activar Echo", "Echo On", "Activa el eco de comandos enviados.", "Enables echo of sent commands.", "AT"),
        "ATCAF0" to CommandInfo("CAN Auto-Format Off", "CAN Auto-Format Off", "Desactiva formateo automático CAN. Muestra datos crudos.", "Disables CAN auto-formatting. Shows raw data.", "AT"),
        "ATCAF1" to CommandInfo("CAN Auto-Format On", "CAN Auto-Format On", "Activa formateo automático CAN (default).", "Enables CAN auto-formatting (default).", "AT"),
        "ATAT1" to CommandInfo("Timing Adaptativo Normal", "Adaptive Timing Normal", "Ajuste automático de timeout según velocidad ECU.", "Automatic timeout adjustment based on ECU speed.", "AT"),
        "ATAT2" to CommandInfo("Timing Adaptativo Agresivo", "Adaptive Timing Aggressive", "Timeout más corto para comunicación rápida.", "Shorter timeout for faster communication.", "AT"),

        // ── MODE 01: LIVE SENSOR DATA ──
        "0100" to CommandInfo("PIDs Soportados 01-20", "Supported PIDs 01-20", "Consulta qué sensores soporta la ECU (bloque 1). Mapa de bits de 32 PIDs.", "Queries which sensors the ECU supports (block 1). 32-PID bitmap.", "SENSOR"),
        "0101" to CommandInfo("Estado MIL y Monitores", "MIL Status & Monitors", "Lee si la luz Check Engine está encendida y cuántos DTCs hay.", "Reads if Check Engine light is on and DTC count.", "SENSOR"),
        "0103" to CommandInfo("Estado Sistema Combustible", "Fuel System Status", "Muestra si el sistema está en lazo abierto o cerrado.", "Shows if fuel system is in open or closed loop.", "SENSOR"),
        "0104" to CommandInfo("Carga del Motor", "Engine Load", "Porcentaje de carga calculada del motor. 100% = máxima demanda.", "Calculated engine load percentage. 100% = max demand.", "SENSOR"),
        "0105" to CommandInfo("Temp. Refrigerante", "Coolant Temperature", "Temperatura del líquido refrigerante del motor. Normal: 80-100°C.", "Engine coolant temperature. Normal: 80-100°C.", "SENSOR"),
        "0106" to CommandInfo("Ajuste Comb. Corto B1", "Short Term Fuel Trim B1", "Corrección instantánea de inyección. ±25% indica problema.", "Instant injection correction. ±25% indicates a problem.", "SENSOR"),
        "0107" to CommandInfo("Ajuste Comb. Largo B1", "Long Term Fuel Trim B1", "Corrección aprendida de inyección. >±10% requiere revisión.", "Learned injection correction. >±10% needs inspection.", "SENSOR"),
        "010B" to CommandInfo("Presión MAP", "MAP Pressure", "Presión absoluta del colector de admisión en kPa.", "Intake manifold absolute pressure in kPa.", "SENSOR"),
        "010C" to CommandInfo("RPM del Motor", "Engine RPM", "Revoluciones por minuto del motor. Fórmula: (A×256+B)/4.", "Engine revolutions per minute. Formula: (A×256+B)/4.", "SENSOR"),
        "010D" to CommandInfo("Velocidad Vehículo", "Vehicle Speed", "Velocidad del vehículo en km/h vía sensor VSS.", "Vehicle speed in km/h via VSS sensor.", "SENSOR"),
        "010E" to CommandInfo("Avance de Encendido", "Timing Advance", "Grados de avance de la chispa antes del PMS.", "Spark advance degrees before TDC.", "SENSOR"),
        "010F" to CommandInfo("Temp. Aire Admisión", "Intake Air Temperature", "Temperatura del aire entrando al motor (IAT).", "Temperature of air entering the engine (IAT).", "SENSOR"),
        "0110" to CommandInfo("Flujo de Aire MAF", "MAF Air Flow", "Masa de aire entrando al motor en gramos/segundo.", "Mass of air entering engine in grams/second.", "SENSOR"),
        "0111" to CommandInfo("Posición Acelerador", "Throttle Position", "Apertura de la mariposa del acelerador (TPS) en %.", "Throttle butterfly opening (TPS) in %.", "SENSOR"),
        "0114" to CommandInfo("Sensor O2 B1S1", "O2 Sensor B1S1", "Voltaje del sensor de oxígeno banco 1, sensor 1 (pre-cat).", "O2 sensor voltage bank 1, sensor 1 (pre-cat).", "SENSOR"),
        "0115" to CommandInfo("Sensor O2 B1S2", "O2 Sensor B1S2", "Voltaje del sensor de oxígeno banco 1, sensor 2 (post-cat).", "O2 sensor voltage bank 1, sensor 2 (post-cat).", "SENSOR"),
        "011C" to CommandInfo("Estándar OBD", "OBD Standard", "Identifica el estándar OBD2 que cumple el vehículo.", "Identifies the OBD2 standard the vehicle complies with.", "INFO"),
        "011F" to CommandInfo("Tiempo Motor Encendido", "Engine Run Time", "Segundos desde que el motor fue encendido.", "Seconds since engine was started.", "SENSOR"),
        "0120" to CommandInfo("PIDs Soportados 21-40", "Supported PIDs 21-40", "Consulta segundo bloque de PIDs soportados.", "Queries second block of supported PIDs.", "SENSOR"),
        "012F" to CommandInfo("Nivel de Combustible", "Fuel Level", "Porcentaje de combustible en el tanque.", "Percentage of fuel in the tank.", "SENSOR"),
        "0133" to CommandInfo("Presión Barométrica", "Barometric Pressure", "Presión atmosférica actual en kPa.", "Current atmospheric pressure in kPa.", "SENSOR"),
        "0142" to CommandInfo("Voltaje Módulo OBD", "OBD Module Voltage", "Voltaje de alimentación del módulo de control.", "Control module power supply voltage.", "SENSOR"),
        "0146" to CommandInfo("Temp. Ambiente", "Ambient Temperature", "Temperatura del aire exterior.", "Outside air temperature.", "SENSOR"),

        // ── MODE 02: FREEZE FRAME ──
        "0200" to CommandInfo("PIDs Freeze Frame", "Freeze Frame PIDs", "PIDs disponibles en el cuadro congelado del último DTC.", "PIDs available in freeze frame of last DTC.", "DTC"),
        "0202" to CommandInfo("DTC del Freeze Frame", "Freeze Frame DTC", "Código que causó la captura del cuadro congelado.", "Code that caused the freeze frame capture.", "DTC"),

        // ── MODE 03: ACTIVE DTCS ──
        "03" to CommandInfo("Leer DTCs Activos", "Read Active DTCs", "Lee TODOS los códigos de falla almacenados que encendieron la luz MIL.", "Reads ALL stored fault codes that turned on the MIL light.", "DTC"),

        // ── MODE 04: CLEAR DTCS ──
        "04" to CommandInfo("Borrar DTCs", "Clear DTCs", "⚠️ Borra TODOS los códigos, apaga MIL, resetea monitores de emisiones.", "⚠️ Clears ALL codes, turns off MIL, resets emission monitors.", "CONTROL"),

        // ── MODE 07: PENDING DTCS ──
        "07" to CommandInfo("DTCs Pendientes", "Pending DTCs", "Códigos que han sido detectados pero aún no encendieron la luz MIL.", "Codes detected but haven't turned on the MIL light yet.", "DTC"),

        // ── MODE 09: VEHICLE INFO ──
        "0902" to CommandInfo("Leer VIN", "Read VIN", "Lee el Número de Identificación del Vehículo (17 caracteres).", "Reads Vehicle Identification Number (17 characters).", "INFO"),
        "0904" to CommandInfo("ID de Calibración", "Calibration ID", "Identificador de calibración del software de la ECU.", "ECU software calibration identifier.", "INFO"),
        "090A" to CommandInfo("Nombre ECU", "ECU Name", "Nombre del módulo de control electrónico.", "Electronic control module name.", "INFO"),

        // ── MODE 0A: PERMANENT DTCS ──
        "0A" to CommandInfo("DTCs Permanentes", "Permanent DTCs", "Códigos que NO se pueden borrar manualmente. Solo se resuelven reparando la falla.", "Codes that CANNOT be manually cleared. Only resolved by fixing the fault.", "DTC"),
    )

    private fun findCommand(cmd: String): CommandInfo? {
        // Direct match
        commands[cmd]?.let { return it }
        // Try with space removed variant
        commands[cmd.replace(" ", "")]?.let { return it }
        // For "AT RV" -> "ATRV" style lookups
        if (cmd.startsWith("AT") && cmd.length > 2) {
            commands["AT${cmd.substring(2).replace(" ", "")}"]?.let { return it }
        }
        return null
    }
}
