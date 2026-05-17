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
        val fullHex = CanMultiFrameParser.parse(response)
        
        if (fullHex.isEmpty() || fullHex.contains("NODATA") || fullHex.contains("?")) {
            return emptyList()
        }

        val codes = mutableListOf<String>()
        val expectedPrefix = when(mode.uppercase()) {
            "03" -> "43"
            "07" -> "47"
            "0A" -> "4A"
            else -> mode
        }

        // Split by the expected prefix. This handles multiple ECU responses cleanly.
        // Example: "430300000000430000000000" splits to ["", "0300000000", "0000000000"]
        val chunks = fullHex.split(expectedPrefix)
        
        for (chunk in chunks) {
            if (chunk.isBlank()) continue
            
            // Iterate in chunks of 4 hex chars (2 bytes per DTC)
            for (i in 0 until chunk.length - 3 step 4) {
                val hexCode = chunk.substring(i, i + 4)
                if (hexCode == "0000") continue
                codes.add(hexToDtc(hexCode))
            }
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

