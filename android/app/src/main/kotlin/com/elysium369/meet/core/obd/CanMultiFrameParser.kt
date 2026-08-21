package com.elysium369.meet.core.obd

/**
 * CanMultiFrameParser — Industrial-grade multi-protocol automotive response & VIN decoder.
 *
 * Supports:
 * - SAE J1979 Mode 09 PID 02 (Multi-line KWP2000, ISO 9141-2, J1850 & CAN)
 * - ISO 15765-2 (ISO-TP over CAN with 11-bit / 29-bit headers and First/Consecutive frame PCI)
 * - ELM327 multi-line indexed format (`0: 49 02 01 ...`, `1: ...`, `2: ...`)
 * - UDS (ISO 14229) Service 0x22 DID 0xF190 (VIN)
 * - KWP2000 (ISO 14230) Service 0x1A DID 0x90
 * - Hyundai / Asian specific Enhanced Mode 0x21
 * - Direct ASCII extraction
 */
object CanMultiFrameParser {

    private val ELM_INDEX_REGEX = Regex("^[0-9A-Fa-f]+:\\s*")
    private val CAN_HEADER_REGEX = Regex("^(7E[0-9A-Fa-f]|18DA[0-9A-Fa-f]{4}|18DB[0-9A-Fa-f]{4})\\s+")

    /**
     * Decodes multi-line raw response into normalized clean payload hex.
     */
    fun parse(rawResponse: String): String {
        val lines = rawResponse.split(Regex("[\r\n]+")).map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""

        val sb = StringBuilder()
        for (line in lines) {
            var clean = line.replace(" ", "").trim()
            if (clean.equals("OK", true) || clean.equals("ERROR", true) || clean.equals("?", true) || clean.equals("NODATA", true) || clean.equals("SEARCHING...", true)) {
                continue
            }
            // Strip CAN header if present (e.g., 7E8, 7E9)
            clean = clean.replace(Regex("^(7E[0-9A-Fa-f]|18DA[0-9A-Fa-f]{4}|18DB[0-9A-Fa-f]{4})"), "")
            // Strip ELM index prefix (e.g., 0:, 1:, 2:)
            clean = clean.replace(Regex("^[0-9A-Fa-f]+:"), "")
            sb.append(clean)
        }

        return sb.toString()
    }

    /**
     * Extracts and validates the 17-character vehicle VIN from any raw ECU response.
     */
    fun decodeVin(rawResponse: String): String {
        if (rawResponse.isBlank()) return "N/A"

        // 1. Direct plain ASCII match
        val plainMatches = Regex("[A-HJ-NPR-Z0-9]{17}", RegexOption.IGNORE_CASE).findAll(rawResponse)
        for (match in plainMatches) {
            val candidate = match.value.uppercase()
            if (isValidVinCandidate(candidate)) {
                return candidate
            }
        }

        val lines = rawResponse.split(Regex("[\r\n]+")).map { it.trim() }.filter { it.isNotBlank() }

        // 2. Multi-line SAE J1979 Mode 09 PID 02 extraction (e.g. 49 02 01 ... / 49 02 02 ...)
        val mode09Lines = mutableMapOf<Int, String>()
        for (line in lines) {
            val clean = line.replace(Regex("^[0-9A-Fa-f]+:\\s*"), "") // strip 0:, 1:
                .replace(Regex("^(7E[0-9A-Fa-f]|18DA[0-9A-Fa-f]{4}|18DB[0-9A-Fa-f]{4})\\s*"), "") // strip headers
                .replace(" ", "")
                .uppercase()

            val idx4902 = clean.indexOf("4902")
            if (idx4902 >= 0 && idx4902 + 6 <= clean.length) {
                val itemHex = clean.substring(idx4902 + 4, idx4902 + 6)
                val itemNum = itemHex.toIntOrNull(16) ?: 0
                val dataPayload = clean.substring(idx4902 + 6)
                if (dataPayload.isNotEmpty()) {
                    mode09Lines[itemNum] = dataPayload
                }
            }
        }

        if (mode09Lines.isNotEmpty()) {
            val reconstructedHex = mode09Lines.toSortedMap().values.joinToString("")
            val decoded = hexToAsciiVin(reconstructedHex)
            if (decoded.length >= 17) {
                return decoded.take(17)
            }
        }

        // 3. ELM327 Indexed Multi-frame ISO-TP CAN Response (e.g. 0: 49 02 01 KMH..., 1: ..., 2: ...)
        val indexedFrames = mutableMapOf<Int, String>()
        for (line in lines) {
            val match = Regex("^([0-9A-Fa-f]+):\\s*(.*)$").find(line)
            if (match != null) {
                val frameIndex = match.groupValues[1].toInt(16)
                val frameData = match.groupValues[2].replace(" ", "").uppercase()
                indexedFrames[frameIndex] = frameData
            }
        }

        if (indexedFrames.isNotEmpty()) {
            val fullStream = indexedFrames.toSortedMap().values.joinToString("")
            // Find service prefix in stream
            val prefixIndex = listOf("4902", "62F190", "5A90", "6100", "6101").mapNotNull { p ->
                val pos = fullStream.indexOf(p)
                if (pos >= 0) pos to p else null
            }.minByOrNull { it.first }

            if (prefixIndex != null) {
                val (pos, p) = prefixIndex
                val skip = if (p == "4902") pos + 6 else pos + p.length
                val payload = fullStream.substring(minOf(skip, fullStream.length))
                val decoded = hexToAsciiVin(payload)
                if (decoded.length >= 17) {
                    return decoded.take(17)
                }
            }
        }

        // 4. ISO-TP Raw Multi-frame (PCI Bytes 10 XX, 21, 22, ...)
        val fullHex = parse(rawResponse).uppercase()
        val udsIdx = fullHex.indexOf("62F190")
        if (udsIdx >= 0) {
            val decoded = hexToAsciiVin(fullHex.substring(udsIdx + 6))
            if (decoded.length >= 17) return decoded.take(17)
        }

        val kwpIdx = fullHex.indexOf("5A90")
        if (kwpIdx >= 0) {
            val decoded = hexToAsciiVin(fullHex.substring(kwpIdx + 4))
            if (decoded.length >= 17) return decoded.take(17)
        }

        val obdIdx = fullHex.indexOf("4902")
        if (obdIdx >= 0) {
            val decoded = hexToAsciiVin(fullHex.substring(obdIdx + 6))
            if (decoded.length >= 17) return decoded.take(17)
        }

        // 5. Fallback: Parse entire hex payload
        val globalDecoded = hexToAsciiVin(fullHex)
        if (globalDecoded.length >= 17) {
            // Find any 17-char slice with valid VIN structure
            for (i in 0..globalDecoded.length - 17) {
                val candidate = globalDecoded.substring(i, i + 17)
                if (isValidVinCandidate(candidate)) {
                    return candidate
                }
            }
            return globalDecoded.take(17)
        }

        return if (globalDecoded.length in 11..16) globalDecoded else "N/A"
    }

    private fun hexToAsciiVin(hex: String): String {
        val sb = StringBuilder()
        for (i in 0 until hex.length - 1 step 2) {
            val byteStr = hex.substring(i, i + 2)
            // Skip NULL padding bytes or 0xFF
            if (byteStr.equals("00", true) || byteStr.equals("FF", true)) {
                continue
            }
            try {
                val code = byteStr.toInt(16)
                val c = code.toChar()
                // Valid characters in ISO 3779 VIN: A-Z (excluding I, O, Q) and 0-9
                if ((c in '0'..'9') || (c in 'A'..'Z' && c !in listOf('I', 'O', 'Q')) || (c in 'a'..'z' && c.uppercaseChar() !in listOf('I', 'O', 'Q'))) {
                    sb.append(c.uppercaseChar())
                }
            } catch (_: Exception) {}
        }
        return sb.toString()
    }

    private fun isValidVinCandidate(vin: String): Boolean {
        if (vin.length != 17) return false
        // Disallow forbidden characters I, O, Q
        if (vin.any { it in listOf('I', 'O', 'Q') }) return false
        // Must contain both letters and digits or standard structure
        val hasLetter = vin.any { it.isLetter() }
        val hasDigit = vin.any { it.isDigit() }
        return hasLetter && hasDigit
    }
}
