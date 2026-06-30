package com.elysium369.meet.core.obd

/**
 * DtcDecoder — Professional OBD2 Diagnostic Trouble Code decoder.
 * Handles Modes 03 (Active), 07 (Pending), and 0A (Permanent).
 */
object DtcDecoder {

    private val dtcPattern = Regex("[PCBU][0-3][0-9A-F]{3}")
    private val HEX_CLEAN_REGEX = Regex("[^0-9A-Fa-f]")

    /**
     * Decodifica una respuesta cruda (posiblemente multi-frame) en una lista de DTCs.
     * @param mode El modo OBD2 (03, 07, 0A) para validar el prefijo de respuesta.
     */
    fun decode(response: String, mode: String): List<String> {
        // 1. Unificar multi-frame si es necesario
        val fullHex = CanMultiFrameParser.parse(response)
        
        if (fullHex.isEmpty() || fullHex.contains("NODATA") || fullHex.contains("?")) {
            return emptyList()
        }

        val codes = mutableListOf<String>()
        
        // 2. Identificar prefijo de respuesta (Mode + 0x40)
        // Mode 03 -> 43, Mode 07 -> 47, Mode 0A -> 4A
        val expectedPrefix = when(mode.uppercase()) {
            "03" -> "43"
            "07" -> "47"
            "0A" -> "4A"
            else -> mode // Por si se pasa un modo personalizado
        }

        val startIndex = fullHex.indexOf(expectedPrefix)
        if (startIndex < 0) return emptyList()

        // 3. Extraer data real
        // Algunos ECUs insertan un byte con la cantidad de DTCs justo después del prefijo
        var dataIdx = startIndex + 2
        
        // Si el byte siguiente es pequeño (ej: 01-0F) y el resto del mensaje es largo, 
        // podría ser el contador de DTCs. Sin embargo, en OBD2 estándar J1979, 
        // los códigos empiezan inmediatamente en modo 03/07/0A.
        
        var dataString = fullHex.substring(dataIdx).replace(HEX_CLEAN_REGEX, "").uppercase()
        if (dataString.length >= 6 && dataString.length % 4 == 2) {
            val countByte = dataString.substring(0, 2).toIntOrNull(16)
            val remainingCodeSlots = (dataString.length - 2) / 4
            if (countByte != null && countByte in 1..remainingCodeSlots) {
                dataString = dataString.substring(2)
            }
        }

        // 4. Iterar en chunks de 4 hex chars (2 bytes por DTC)
        for (i in 0 until dataString.length - 3 step 4) {
            val hexCode = dataString.substring(i, i + 4)
            // 0000 es relleno, no un código real
            if (hexCode == "0000") continue

            hexToDtcOrNull(hexCode)?.let { codes.add(it) }
        }

        return codes.distinct()
    }

    /**
     * Convierte 2 bytes hex en un código DTC estándar (Pxxxx, Cxxxx, Bxxxx, Uxxxx).
     * Según SAE J2012 / ISO 15031-6.
     */
    fun hexToDtc(hex: String): String {
        return hexToDtcOrNull(hex) ?: "P0000"
    }

    fun hexToDtcOrNull(hex: String): String? {
        val clean = hex.replace(HEX_CLEAN_REGEX, "").uppercase()
        if (clean.length < 4) return null
        val b1 = clean.substring(0, 2).toIntOrNull(16) ?: return null
        val b2 = clean.substring(2, 4)
        return formatDtc(b1, b2).takeIf { it.matches(dtcPattern) && it !in setOf("P0000", "C0000", "B0000", "U0000") }
    }

    fun hexToDtc(b1: Int, b2: Int): String {
        if (b1 !in 0..255 || b2 !in 0..255) return "P0000"
        return formatDtc(b1, String.format("%02X", b2))
    }

    private fun formatDtc(b1: Int, b2Hex: String): String {
        // Los dos bits más significativos del primer byte definen la categoría
        val prefixBits = (b1 shr 6) and 0x03
        val prefix = when (prefixBits) {
            0 -> "P" // Powertrain
            1 -> "C" // Chassis
            2 -> "B" // Body
            3 -> "U" // Network
            else -> "P"
        }

        // Los bits 4 y 5 del primer byte definen el primer dígito (0, 1, 2, 3)
        val digit1 = (b1 shr 4) and 0x03
        
        // Los bits 0-3 del primer byte definen el segundo dígito
        val digit2 = b1 and 0x0F

        return "$prefix$digit1${digit2.toString(16).uppercase()}$b2Hex"
    }


    fun decodeDtcResponse(response: String) = decode(response, "03")
    fun decodePendingResponse(response: String) = decode(response, "07")
    fun decodePermanentResponse(response: String) = decode(response, "0A")

    /**
     * Devuelve una descripción genérica o específica si está en el diccionario local.
     */
    fun getLocalDescription(code: String): String {
        return when (code.uppercase()) {
            // SENSOR DE FLUJO DE AIRE Y COMBUSTIBLE (P01xx)
            "P0100" -> "Fallo en el circuito del sensor MAF/VAF"
            "P0101" -> "Problema de rango/rendimiento del circuito MAF"
            "P0102" -> "Entrada baja en el circuito MAF/VAF"
            "P0103" -> "Entrada alta en el circuito MAF/VAF"
            "P0104" -> "Circuito MAF/VAF intermitente"
            "P0113" -> "Entrada alta en el circuito del sensor IAT"
            "P0117" -> "Entrada baja en el circuito del sensor ECT"
            "P0118" -> "Entrada alta en el circuito del sensor ECT"
            "P0121" -> "Rango/Rendimiento del sensor TP/Pedal A"
            "P0122" -> "Entrada baja en el circuito TP/Pedal A"
            "P0123" -> "Entrada alta en el circuito TP/Pedal A"
            "P0125" -> "Temperatura refrigerante insuficiente para control loop cerrado"
            "P0128" -> "Termostato del refrigerante (Temp. por debajo del umbral)"
            "P0130" -> "Fallo en el circuito del sensor de O2 (Banco 1, Sensor 1)"
            "P0131" -> "Voltaje bajo en el circuito del sensor de O2 (Banco 1, Sensor 1)"
            "P0132" -> "Voltaje alto en el circuito del sensor de O2 (Banco 1, Sensor 1)"
            "P0133" -> "Respuesta lenta del circuito del sensor de O2 (Banco 1, Sensor 1)"
            "P0134" -> "Actividad no detectada en el circuito del sensor de O2 (Banco 1, Sensor 1)"
            "P0135" -> "Fallo en el calefactor del sensor de O2 (Banco 1, Sensor 1)"
            "P0171" -> "Sistema demasiado pobre (Banco 1)"
            "P0172" -> "Sistema demasiado rico (Banco 1)"
            "P0174" -> "Sistema demasiado pobre (Banco 2)"
            "P0175" -> "Sistema demasiado rico (Banco 2)"

            // FALLOS DE ENCENDIDO (P03xx)
            "P0300" -> "Fallo de encendido detectado (múltiples cilindros)"
            "P0301" -> "Fallo de encendido - Cilindro 1"
            "P0302" -> "Fallo de encendido - Cilindro 2"
            "P0303" -> "Fallo de encendido - Cilindro 3"
            "P0304" -> "Fallo de encendido - Cilindro 4"
            "P0305" -> "Fallo de encendido - Cilindro 5"
            "P0306" -> "Fallo de encendido - Cilindro 6"
            "P0307" -> "Fallo de encendido - Cilindro 7"
            "P0308" -> "Fallo de encendido - Cilindro 8"
            "P0325" -> "Fallo en el circuito del sensor de detonación (Knock) 1"
            "P0335" -> "Fallo en el circuito del sensor de posición del cigüeñal (CKP)"
            "P0336" -> "Rango/Rendimiento del circuito del sensor CKP"
            "P0340" -> "Fallo en el circuito del sensor de posición del árbol de levas (CMP)"
            "P0341" -> "Rango/Rendimiento del circuito del sensor CMP"

            // EMISIONES (P04xx)
            "P0401" -> "Flujo insuficiente detectado en el sistema EGR"
            "P0402" -> "Flujo excesivo detectado en el sistema EGR"
            "P0420" -> "Eficiencia del sistema catalizador por debajo del umbral (Banco 1)"
            "P0430" -> "Eficiencia del sistema catalizador por debajo del umbral (Banco 2)"
            "P0440" -> "Fallo en el sistema de control de emisiones evaporativas (EVAP)"
            "P0441" -> "Flujo de purga incorrecto en el sistema EVAP"
            "P0442" -> "Fuga pequeña detectada en el sistema EVAP"
            "P0455" -> "Fuga grande detectada en el sistema EVAP"

            // VELOCIDAD E IDLE (P05xx)
            "P0500" -> "Sensor de velocidad del vehículo (VSS) defectuoso"
            "P0505" -> "Fallo en el sistema de control de ralentí (IAC)"
            "P0506" -> "RPM de ralentí más bajas de lo esperado"
            "P0507" -> "RPM de ralentí más altas de lo esperado"

            // COMPUTADORA Y SALIDAS (P06xx)
            "P0600" -> "Fallo en el enlace de comunicación serial"
            "P0601" -> "Error de memoria (Checksum) en el módulo de control interno"
            "P0603" -> "Error de memoria KAM (Keep Alive Memory) interna"
            "P0605" -> "Error de memoria ROM interna"

            // TRANSMISIÓN (P07xx)
            "P0700" -> "Sistema de control de transmisión (Solicitud MIL)"
            "P0705" -> "Fallo en el circuito del sensor de rango de transmisión (PRNDL)"
            "P0706" -> "Rango/Rendimiento del circuito del sensor de rango"
            "P0715" -> "Fallo en el circuito del sensor de velocidad de entrada/turbina"
            "P0720" -> "Fallo en el circuito del sensor de velocidad de salida"
            "P0730" -> "Relación de marchas incorrecta"
            "P0740" -> "Fallo en el circuito del embrague del convertidor de par (TCC)"
            "P0750" -> "Fallo en el solenoide de cambio A"

            // RED Y COMUNICACIÓN (U0xxx)
            "U0001" -> "Bus de comunicación CAN de alta velocidad"
            "U0100" -> "Pérdida de comunicación con el ECM/PCM"
            "U0101" -> "Pérdida de comunicación con el TCM"
            "U0121" -> "Pérdida de comunicación con el módulo del ABS"
            "U0140" -> "Pérdida de comunicación con el módulo Body (BCM)"
            "U0155" -> "Pérdida de comunicación con el panel de instrumentos"

            // INYECCIÓN Y TURBO (P02xx)
            "P0201" -> "Fallo en circuito de inyector cilindro 1"
            "P0202" -> "Fallo en circuito de inyector cilindro 2"
            "P0203" -> "Fallo en circuito de inyector cilindro 3"
            "P0204" -> "Fallo en circuito de inyector cilindro 4"
            "P0219" -> "Condición de sobre-revolución del motor"
            "P0234" -> "Condición de sobrealimentación límite del turbo (Overboost)"
            "P0299" -> "Presión de sobrealimentación del turbo insuficiente (Underboost)"

            // DETONACIÓN Y BOBINAS (P03xx)
            "P0326" -> "Rango/Rendimiento del circuito del sensor de detonación 1 (Knock Sensor)"
            "P0351" -> "Fallo circuito primario/secundario bobina de encendido A"
            "P0352" -> "Fallo circuito primario/secundario bobina de encendido B"
            "P0353" -> "Fallo circuito primario/secundario bobina de encendido C"
            "P0354" -> "Fallo circuito primario/secundario bobina de encendido D"

            // SISTEMAS DE CHASIS Y ABS (Cxxxx)
            "C0035" -> "Sensor velocidad de rueda delantera izquierda ABS defectuoso"
            "C0040" -> "Sensor velocidad de rueda delantera derecha ABS defectuoso"
            "C0550" -> "Fallo en memoria interna de la ECU del ABS / Control de Tracción"

            // RED Y BUS DE CONTROL (Uxxxx)
            "U0073" -> "Bus de comunicación de módulos de control APAGADO"
            "U0104" -> "Pérdida de comunicación con módulo de control de crucero"
            "U0114" -> "Pérdida de comunicación con el módulo de tracción total (AWD)"
            "U0122" -> "Pérdida de comunicación con el módulo del Control de Estabilidad"
            "U0401" -> "Datos no válidos recibidos del módulo del motor (ECM)"

            else -> "Descripción no disponible localmente. El análisis de IA detallará causas y soluciones."
        }
    }
}
