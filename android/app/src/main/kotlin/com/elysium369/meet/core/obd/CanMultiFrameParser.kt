package com.elysium369.meet.core.obd

/**
 * CanMultiFrameParser — Professional ISO 15765-2 Multi-frame message decoder.
 * Essential for reading VIN, long DTC lists, and proprietary module data.
 */
object CanMultiFrameParser {

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
            val lines = cleanInput.split(Regex("\\s*[0-9]+:\\s*")).filter { it.isNotBlank() }
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
        
        // Professional ISO-TP (ISO 15765-2) Handling
        // Frame Types:
        // 0: Single Frame (SF) - 0[Length] [Data...]
        // 1: First Frame (FF) - 1[Length High] [Length Low] [Data...]
        // 2: Consecutive Frame (CF) - 2[Index] [Data...]
        // 3: Flow Control (FC) - 3[Type] [BS] [STmin]

        val sb = StringBuilder()
        var i = 0
        
        // If it doesn't look like ISO-TP PCI (e.g. standard OBD2 4x yy response from ELM), 
        // but ELM often appends multiple lines.
        
        while (i < hex.length) {
            val byte1Hex = hex.substring(i, i + 2)
            val byte1 = try { byte1Hex.toInt(16) } catch (_: Exception) { 0 }
            val frameType = (byte1 shr 4) and 0x0F
            
            when (frameType) {
                0 -> { // Single Frame
                    val length = byte1 and 0x0F
                    val dataEnd = minOf(i + 2 + (length * 2), hex.length)
                    sb.append(hex.substring(i + 2, dataEnd))
                    i += 16 // Assume standard 8-byte CAN frame spacing
                }
                1 -> { // First Frame
                    // PCI is 2 bytes (4 hex chars)
                    val dataEnd = minOf(i + 16, hex.length)
                    if (i + 4 < dataEnd) {
                        sb.append(hex.substring(i + 4, dataEnd))
                    }
                    i += 16
                }
                2 -> { // Consecutive Frame
                    // PCI is 1 byte (2 hex chars)
                    val dataEnd = minOf(i + 16, hex.length)
                    if (i + 2 < dataEnd) {
                        sb.append(hex.substring(i + 2, dataEnd))
                    }
                    i += 16
                }
                else -> {
                    // Not a standard ISO-TP PCI we want to strip, 
                    // or maybe it's just raw data from an ELM that already stripped it.
                    // If we can't identify it, we take the whole block of 16 (if possible)
                    val end = minOf(i + 16, hex.length)
                    sb.append(hex.substring(i, end))
                    i += 16
                }
            }
        }
        
        return sb.toString()
    }

    /**
     * Decodes a VIN response from hex to ASCII.
     * Typically Mode 09 PID 02.
     */
    fun decodeVin(rawResponse: String): String {
        val fullHex = parse(rawResponse)
        
        // Search for 49 02 response
        val idx = fullHex.uppercase().indexOf("4902")
        if (idx < 0) return "N/A"
        
        // Mode 09 PID 02 data format: 
        // 49 02 [Number of data items (1 byte)] [VIN Data...]
        // Skip 49 02 + 01 (often 01 data item)
        var dataStart = idx + 6
        
        val data = fullHex.substring(minOf(dataStart, fullHex.length))
        val vin = StringBuilder()
        
        for (i in 0 until data.length - 1 step 2) {
            try {
                val b = data.substring(i, i + 2).toInt(16)
                if (b in 32..126) { // ASCII printable
                    vin.append(b.toChar())
                }
            } catch (_: Exception) {}
        }
        
        val result = vin.toString().trim()
        return if (result.length >= 17) result.take(17) else if (result.isNotEmpty()) result else "N/A"
    }
}
