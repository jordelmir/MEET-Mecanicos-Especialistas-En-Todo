package com.elysium369.meet.core.obd

object DtcDecoder {

    /**
     * Decodifica una cadena de respuesta cruda OBD2 (ej: "43 01 33 00 00 00")
     * en una lista de códigos P/B/C/U.
     */
    fun decodeDtcResponse(response: String): List<String> {
        val cleanResponse = response.replace("\\s+".toRegex(), "").replace("\r", "")
        if (cleanResponse.contains("NODATA") || cleanResponse.length < 4) return emptyList()

        val codes = mutableListOf<String>()
        
        // La respuesta típica de Mode 03 es 43 XX XX XX XX
        // Donde 43 es la confirmación de la petición 03, y cada par de bytes forma un código.
        
        // Removemos el echo si existe
        val dataString = if (cleanResponse.startsWith("43")) {
            cleanResponse.substring(2)
        } else {
            cleanResponse
        }

        // Iterar en chunks de 4 hex chars (2 bytes)
        for (i in 0 until dataString.length - 3 step 4) {
            val hexCode = dataString.substring(i, i + 4)
            if (hexCode == "0000") continue

            codes.add(hexToDtc(hexCode))
        }

        return codes.distinct()
    }

    private fun hexToDtc(hex: String): String {
        val b1 = hex.substring(0, 2).toIntOrNull(16) ?: return "P0000"
        val b2 = hex.substring(2, 4)

        val prefixBits = (b1 shr 6) and 0x03
        val prefix = when (prefixBits) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            3 -> "U"
            else -> "P"
        }

        val digit1 = (b1 shr 4) and 0x03
        val digit2 = b1 and 0x0F

        return "$prefix$digit1${digit2.toString(16).uppercase()}$b2"
    }

    // Base de datos fallback offline
    val localDtcDatabase = mapOf(
        "P0300" to "Fallo de encendido aleatorio detectado",
        "P0420" to "Eficiencia del sistema de catalizador por debajo del umbral (Banco 1)",
        "P0171" to "Sistema demasiado pobre (Banco 1)",
        "P0135" to "Circuito del calentador del sensor de O2 (Banco 1, Sensor 1)",
        "P0455" to "Sistema de emisión evaporativa - Fuga grande detectada"
        // Esta base se expandiría a +3000 códigos
    )

    fun getLocalDescription(code: String): String {
        return localDtcDatabase[code] ?: "Descripción no disponible en base local."
    }

    /**
     * Decodifica respuesta de Mode 07 (Pending DTCs).
     * Prefijo de respuesta: 47
     */
    fun decodePendingResponse(response: String): List<String> {
        val cleanResponse = response.replace("\\s+".toRegex(), "").replace("\r", "")
        if (cleanResponse.contains("NODATA") || cleanResponse.length < 4) return emptyList()
        val dataString = if (cleanResponse.startsWith("47")) cleanResponse.substring(2) else cleanResponse
        val codes = mutableListOf<String>()
        for (i in 0 until dataString.length - 3 step 4) {
            val hexCode = dataString.substring(i, i + 4)
            if (hexCode == "0000") continue
            codes.add(hexToDtc(hexCode))
        }
        return codes.distinct()
    }

    /**
     * Decodifica respuesta de Mode 0A (Permanent DTCs).
     * Prefijo de respuesta: 4A
     */
    fun decodePermanentResponse(response: String): List<String> {
        val cleanResponse = response.replace("\\s+".toRegex(), "").replace("\r", "")
        if (cleanResponse.contains("NODATA") || cleanResponse.length < 4) return emptyList()
        val dataString = if (cleanResponse.startsWith("4A")) cleanResponse.substring(2) else cleanResponse
        val codes = mutableListOf<String>()
        for (i in 0 until dataString.length - 3 step 4) {
            val hexCode = dataString.substring(i, i + 4)
            if (hexCode == "0000") continue
            codes.add(hexToDtc(hexCode))
        }
        return codes.distinct()
    }
}
