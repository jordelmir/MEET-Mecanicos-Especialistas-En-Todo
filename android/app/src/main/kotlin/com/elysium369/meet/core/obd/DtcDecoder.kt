package com.elysium369.meet.core.obd

/**
 * DtcDecoder — Professional OBD2 Diagnostic Trouble Code decoder.
 * Handles Modes 03 (Active), 07 (Pending), and 0A (Permanent).
 */
object DtcDecoder {

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
        
        val dataString = fullHex.substring(dataIdx)

        // 4. Iterar en chunks de 4 hex chars (2 bytes por DTC)
        for (i in 0 until dataString.length - 3 step 4) {
            val hexCode = dataString.substring(i, i + 4)
            // 0000 es relleno, no un código real
            if (hexCode == "0000") continue

            codes.add(hexToDtc(hexCode))
        }

        return codes.distinct()
    }

    /**
     * Convierte 2 bytes hex en un código DTC estándar (Pxxxx, Cxxxx, Bxxxx, Uxxxx).
     * Según SAE J2012 / ISO 15031-6.
     */
    fun hexToDtc(hex: String): String {
        return try {
            val b1 = hex.substring(0, 2).toInt(16)
            val b2 = hex.substring(2, 4)
            formatDtc(b1, b2)
        } catch (_: Exception) {
            "P0000"
        }
    }

    fun hexToDtc(b1: Int, b2: Int): String {
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

}

