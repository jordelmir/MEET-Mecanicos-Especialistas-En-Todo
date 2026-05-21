package com.elysium369.meet.core.obd

import android.util.Log

/**
 * CanMultiFrameParser — Professional ISO 15765-2 Multi-frame message decoder.
 * Essential for reading VIN, long DTC lists, and proprietary module data.
 *
 * V2: Adaptive frame detection with CAN header stripping, safe fallback,
 * and extensive debug logging for zero-loss DTC extraction.
 */
object CanMultiFrameParser {

    private const val TAG = "CanMultiFrameParser"

    /**
     * Decodes multi-line raw response from ELM327 into a single clean hex string.
     * Handles:
     *  - Headers ON/OFF
     *  - CAN formatting ON/OFF
     *  - ISO-TP (ISO 15765-2) PCI stripping
     *  - Multiple ECU responses (7E8, 7E9, 7EA...)
     *  - ELM327 line numbering ("0: XX XX", "1: XX XX")
     *  - Clone adapters with non-standard formatting
     */
    fun parse(rawResponse: String): String {
        if (rawResponse.isBlank()) return ""

        val cleanInput = rawResponse
            .replace("\r", "\n")
            .replace("\t", " ")
            .trim()

        Log.d(TAG, "RAW INPUT: [$cleanInput]")

        // If it's a simple AT command response return as-is
        val upper = cleanInput.uppercase().replace(" ", "")
        if (upper == "OK" || upper == "ERROR" || upper == "?" ||
            upper.contains("NODATA") || upper.contains("UNABLE") ||
            upper.contains("STOPPED") || upper.contains("CANERROR") ||
            upper.contains("BUSERROR") || upper.contains("FBERROR") ||
            upper.contains("DATAERROR")
        ) {
            return cleanInput
        }

        // Split into individual lines
        val lines = cleanInput.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line ->
                // Filter out echo, prompt, and adapter noise
                val u = line.uppercase().trim()
                !u.startsWith("AT") &&
                !u.startsWith("SEARCHING") &&
                !u.startsWith(">") &&
                u != "OK" &&
                !u.contains("NO DATA") &&
                !u.contains("UNABLE TO CONNECT") &&
                !u.contains("BUS INIT") &&
                !u.contains("STOPPED")
            }

        if (lines.isEmpty()) return ""

        Log.d(TAG, "Filtered lines: $lines")

        // Detect format and process accordingly
        return when {
            // Format 1: ELM327 numbered lines "0: XX XX XX ..." (multi-frame with ATCAF0)
            lines.any { it.matches(Regex("^\\d+:\\s*.*")) } -> parseNumberedLines(lines)

            // Format 2: Lines with CAN headers "7E8 06 43 02 01 43 00 00"
            lines.any { looksLikeCanHeader(it) } -> parseCanHeaderLines(lines)

            // Format 3: Simple hex data (single or multi-line, no headers)
            else -> parseSimpleHex(lines)
        }
    }

    /**
     * Format 1: ELM327 numbered lines.
     * "0: 49 02 01 57 44 42"
     * "1: 52 46 38 43 58 34 36"
     * "2: 30 4D 42 35 36 39 32"
     */
    private fun parseNumberedLines(lines: List<String>): String {
        val sb = StringBuilder()
        for (line in lines) {
            // Remove line number prefix "N: "
            val data = line.replace(Regex("^\\d+:\\s*"), "").replace(" ", "").trim()
            sb.append(data)
        }
        val result = sb.toString().uppercase()
        Log.d(TAG, "Numbered parse result: $result")

        // Check if ISO-TP PCI is embedded (first nibble of concatenated data)
        return stripIsoTpPciSafe(result)
    }

    /**
     * Format 2: Lines with CAN headers.
     * "7E8 06 43 02 01 43 00 00"
     * "7E9 06 43 01 03 00 00 00"
     */
    private fun parseCanHeaderLines(lines: List<String>): String {
        val ecuResponses = mutableMapOf<String, MutableList<String>>()

        for (line in lines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue

            // Identify and strip the CAN header (3 hex chars like 7E8, or 8 hex chars for 29-bit)
            val header = parts[0].uppercase()
            val isCanHeader = header.matches(Regex("[0-9A-F]{3,8}"))

            if (isCanHeader) {
                val dataBytes = parts.drop(1).joinToString("").uppercase()
                ecuResponses.getOrPut(header) { mutableListOf() }.add(dataBytes)
            } else {
                // No header detected, treat entire line as data
                val data = parts.joinToString("").uppercase()
                ecuResponses.getOrPut("NONE") { mutableListOf() }.add(data)
            }
        }

        Log.d(TAG, "CAN header ECU responses: $ecuResponses")

        // Reconstruct from all ECUs
        val sb = StringBuilder()
        for ((header, dataLines) in ecuResponses) {
            for (data in dataLines) {
                // The first byte after header is often the PCI byte (length/frame type)
                // We need to strip ISO-TP PCI from each frame
                val stripped = stripSingleFramePci(data)
                sb.append(stripped)
            }
        }

        val result = sb.toString()
        Log.d(TAG, "CAN header parse result: $result")
        return result
    }

    /**
     * Format 3: Simple hex data without headers.
     * "43 02 01 43 00 00" or "4302014300000000"
     */
    private fun parseSimpleHex(lines: List<String>): String {
        val sb = StringBuilder()
        for (line in lines) {
            sb.append(line.replace(" ", "").uppercase())
        }
        val result = sb.toString()
        Log.d(TAG, "Simple hex result: $result")

        // Only strip ISO-TP PCI if it clearly looks like ISO-TP framing
        return stripIsoTpPciSafe(result)
    }

    /**
     * Strips PCI from a single CAN frame's data bytes.
     * Input is the data portion AFTER the CAN header has been removed.
     * E.g., "0643020143000000" → "43020143000000" (stripped SF PCI "06")
     */
    private fun stripSingleFramePci(data: String): String {
        if (data.length < 2) return data

        val firstByte = try { data.substring(0, 2).toInt(16) } catch (_: Exception) { return data }
        val frameType = (firstByte shr 4) and 0x0F

        return when (frameType) {
            0 -> { // Single Frame — PCI is 1 byte (the length)
                val dataLength = firstByte and 0x0F
                val dataEnd = minOf(2 + dataLength * 2, data.length)
                data.substring(2, dataEnd)
            }
            1 -> { // First Frame — PCI is 2 bytes (length spread across 2 bytes)
                if (data.length < 4) return data
                data.substring(4, data.length)
            }
            2 -> { // Consecutive Frame — PCI is 1 byte (sequence number)
                data.substring(2, data.length)
            }
            3 -> { // Flow Control — skip entirely
                ""
            }
            else -> {
                // Not ISO-TP PCI. This is likely already raw service data (4x, 6x, etc.)
                // Return as-is. This is the critical fallback that prevents data loss.
                data
            }
        }
    }

    /**
     * Safe ISO-TP PCI stripping for concatenated hex strings.
     * Only strips if we can confidently identify ISO-TP framing.
     * Falls back to returning input unchanged if unsure.
     */
    private fun stripIsoTpPciSafe(hex: String): String {
        if (hex.length < 4) return hex

        val firstByte = try { hex.substring(0, 2).toInt(16) } catch (_: Exception) { return hex }
        val frameType = (firstByte shr 4) and 0x0F

        // Only strip if the first byte looks like a valid ISO-TP PCI
        // AND the data after stripping starts with a valid OBD service response (4x)
        return when (frameType) {
            0 -> { // Single Frame
                val length = firstByte and 0x0F
                if (length in 1..7) {
                    val stripped = hex.substring(2, minOf(2 + length * 2, hex.length))
                    // Verify: does the stripped data start with a valid OBD response?
                    if (isValidObdResponse(stripped)) {
                        Log.d(TAG, "Stripped SF PCI, result: $stripped")
                        stripped
                    } else {
                        Log.d(TAG, "SF PCI strip produced invalid OBD response, keeping original")
                        hex
                    }
                } else hex
            }
            1 -> { // First Frame — multi-frame response
                if (hex.length >= 4) {
                    // For multi-frame, we need to process each 16-char chunk
                    stripMultiFramePci(hex)
                } else hex
            }
            else -> {
                // If the first byte is already an OBD response (41, 43, 47, 49, 4A, 46, etc.)
                // then there's no PCI to strip — the data is already clean
                if (isValidObdResponse(hex)) {
                    Log.d(TAG, "Data already clean (starts with OBD response), no stripping needed")
                    hex
                } else {
                    Log.d(TAG, "Unknown framing, returning as-is: ${hex.take(20)}...")
                    hex
                }
            }
        }
    }

    /**
     * Multi-frame PCI stripping with variable chunk sizes.
     */
    private fun stripMultiFramePci(hex: String): String {
        val sb = StringBuilder()
        var i = 0
        var isFirstFrame = true

        while (i < hex.length) {
            if (i + 2 > hex.length) break
            val byte1 = try { hex.substring(i, i + 2).toInt(16) } catch (_: Exception) { break }
            val frameType = (byte1 shr 4) and 0x0F

            // Determine chunk size based on remaining data
            val remainingChars = hex.length - i
            // Standard CAN frame is 8 bytes = 16 hex chars, but some adapters vary
            val chunkSize = if (remainingChars >= 16) 16 else remainingChars

            when (frameType) {
                0 -> { // Single Frame
                    val length = byte1 and 0x0F
                    val dataEnd = minOf(i + 2 + length * 2, hex.length)
                    sb.append(hex.substring(i + 2, dataEnd))
                    i += chunkSize
                }
                1 -> { // First Frame — skip 4 hex chars (2 bytes PCI)
                    val dataEnd = minOf(i + chunkSize, hex.length)
                    if (i + 4 < dataEnd) {
                        sb.append(hex.substring(i + 4, dataEnd))
                    }
                    i += chunkSize
                    isFirstFrame = false
                }
                2 -> { // Consecutive Frame — skip 2 hex chars (1 byte PCI)
                    val dataEnd = minOf(i + chunkSize, hex.length)
                    if (i + 2 < dataEnd) {
                        sb.append(hex.substring(i + 2, dataEnd))
                    }
                    i += chunkSize
                }
                3 -> { // Flow Control — skip entirely
                    i += chunkSize
                }
                else -> {
                    // Unknown — append as-is and advance
                    val dataEnd = minOf(i + chunkSize, hex.length)
                    sb.append(hex.substring(i, dataEnd))
                    i += chunkSize
                }
            }
        }

        val result = sb.toString()
        Log.d(TAG, "Multi-frame strip result: $result")
        return result
    }

    /**
     * Checks if a hex string starts with a valid OBD-II service response byte.
     * Mode responses: 41 (Mode 01), 42 (Mode 02), 43 (Mode 03), 44 (Mode 04),
     * 45 (Mode 05), 46 (Mode 06), 47 (Mode 07), 48 (Mode 08), 49 (Mode 09), 4A (Mode 0A)
     */
    private fun isValidObdResponse(hex: String): Boolean {
        if (hex.length < 2) return false
        val firstByte = hex.substring(0, 2).uppercase()
        return firstByte in listOf("41", "42", "43", "44", "45", "46", "47", "48", "49", "4A")
    }

    /**
     * Heuristic: does this line start with something that looks like a CAN header?
     * CAN 11-bit IDs in hex are 3 chars (e.g., 7E8, 7E9, 7EA, 18DAF110)
     */
    private fun looksLikeCanHeader(line: String): Boolean {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 2) return false
        val first = parts[0].uppercase()
        // Standard OBD CAN response IDs: 7E8-7EF, or extended 18DAxxxx
        return first.matches(Regex("7E[0-9A-F]")) ||
                first.matches(Regex("7[0-9A-F]{2}")) ||
                first.matches(Regex("18[0-9A-F]{6}")) ||
                (first.length == 3 && first.all { it in "0123456789ABCDEF" })
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
