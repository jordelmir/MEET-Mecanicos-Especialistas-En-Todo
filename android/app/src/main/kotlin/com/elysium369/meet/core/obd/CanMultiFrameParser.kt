package com.elysium369.meet.core.obd

/**
 * CanMultiFrameParser — Professional ISO 15765-2 Multi-frame message decoder.
 * Essential for reading VIN, long DTC lists, and proprietary module data.
 */
object CanMultiFrameParser {

    private val ELM_LINE_SPLIT_REGEX = Regex("\\s*[0-9]+:\\s*")

    /**
     * Decodes multi-line raw response from ELM327 into a single hex string.
     * Handles various ELM327 formatting styles (Headers ON/OFF, CAN formatting ON/OFF).
     */
    /**
     * Decodes multi-line raw response from ELM327 into a single hex string.
     * Handles ISO 15765-2 (ISO-TP) protocol control information (PCI).
     */
    fun parse(rawResponse: String): String {
        val cleanInput = rawResponse.replace("\r", " ").replace("\n", " ").trim()
        if (cleanInput.isBlank()) return ""
        
        // If it's a simple AT command response (OK, ERROR, etc) return as is
        if (cleanInput == "OK" || cleanInput == "ERROR" || cleanInput == "?") return cleanInput

        val result = StringBuilder()
        
        // 1. ELM327 Formatting (X: XX XX XX ...)
        if (cleanInput.contains(":")) {
            val lines = cleanInput.split(ELM_LINE_SPLIT_REGEX).filter { it.isNotBlank() }
            for (line in lines) {
                val data = line.replace(" ", "").trim()
                // In ISO-TP over CAN, the first byte of a frame is often PCI
                // But ELM327 usually hides this when ATCAF1 is ON.
                // If it's NOT hidden, we need to handle FF, CF.
                result.append(data)
            }
            return stripIsoTpPci(result.toString())
        }

        // 2. Raw formatting (Multiple lines of hex)
        val spaceCleaned = cleanInput.replace(" ", "")
        return stripIsoTpPci(spaceCleaned)
    }

    /**
     * Strips ISO-TP Protocol Control Information bytes from a reconstructed hex string.
     * FF (First Frame) starts with '1' + 3 hex digits for length.
     * CF (Consecutive Frame) starts with '2' + 1 hex digit for sequence.
     */
    private fun stripIsoTpPci(hex: String): String {
        if (hex.length < 4) return hex
        
        // If the hex starts with '1' (FF) and we have multiple segments
        if (hex.startsWith("1") && hex.length > 32) {
            val sb = StringBuilder()
            // The length is in the first 3 nibbles after '1'
            // byte 0: [1][Length High], byte 1: [Length Low]
            // We usually want to skip these 2 bytes (4 hex chars)
            
            // Note: ELM327 with ATCAF1 usually already handled this, 
            // but for raw mode or STN custom frames we do it manually.
            
            var i = 0
            while (i < hex.length) {
                val type = hex[i]
                when (type) {
                    '1' -> { // First Frame
                        // Skip PCI (4 hex chars)
                        if (i + 4 <= hex.length) {
                            sb.append(hex.substring(i + 4, minOf(i + 16, hex.length)))
                            i += 16 // Standard CAN frame is 8 bytes = 16 hex
                        } else break
                    }
                    '2' -> { // Consecutive Frame
                        // Skip PCI (2 hex chars: '2' + index)
                        if (i + 2 <= hex.length) {
                            sb.append(hex.substring(i + 2, minOf(i + 16, hex.length)))
                            i += 16
                        } else break
                    }
                    else -> {
                        // Not standard ISO-TP PCI or already stripped by ELM
                        return hex 
                    }
                }
            }
            return sb.toString()
        }
        
        return hex
    }

    fun decodeVin(rawResponse: String): String {
        if (rawResponse.isBlank()) return "N/A"

        // 1. Direct ASCII extraction if 17-char VIN is present in raw response
        val asciiMatch = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b", RegexOption.IGNORE_CASE).find(rawResponse)
        if (asciiMatch != null && VinDecoder.validateCheckDigit(asciiMatch.value.uppercase())) {
            return asciiMatch.value.uppercase()
        }

        val fullHex = parse(rawResponse).uppercase()

        // Search for known VIN response service prefixes
        // 4902 (Mode 09 PID 02), 62F190 (UDS Read DID F190), 5A90 (KWP2000 1A 90), 6100 (Mode 21 00)
        val prefixes = listOf("4902", "62F190", "5A90", "6100")
        var dataStart = -1

        for (prefix in prefixes) {
            val idx = fullHex.indexOf(prefix)
            if (idx >= 0) {
                dataStart = if (prefix == "4902") idx + 6 else idx + prefix.length
                break
            }
        }

        if (dataStart < 0) {
            // Check if full string itself is hex-encoded ASCII
            if (fullHex.length >= 34) {
                dataStart = 0
            } else {
                return "N/A"
            }
        }

        val data = fullHex.substring(minOf(dataStart, fullHex.length))
        val vin = StringBuilder()

        for (i in 0 until data.length - 1 step 2) {
            try {
                val hexChar = data.substring(i, i + 2)
                if (hexChar.equals("AA", ignoreCase = true) ||
                    hexChar.equals("55", ignoreCase = true) ||
                    hexChar.equals("FF", ignoreCase = true) ||
                    hexChar.equals("00", ignoreCase = true)
                ) {
                    continue
                }
                val b = hexChar.toInt(16)
                val c = b.toChar()
                if (c.isLetterOrDigit() && c.uppercaseChar() !in listOf('I', 'O', 'Q')) {
                    vin.append(c.uppercaseChar())
                }
            } catch (_: Exception) {}
        }

        val result = vin.toString().trim()
        return if (result.length >= 17) result.take(17) else if (result.length in 11..16) result else "N/A"
    }
}
