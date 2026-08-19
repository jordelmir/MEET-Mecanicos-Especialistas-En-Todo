package com.elysium369.meet.core.obd.handshake

/**
 * Pid00HandshakeDecoder — Strict cryptographic/semantic PDU decoder for Mode 01 PID 00.
 * Eliminates false protocol locks caused by stale delayed responses (e.g. 410C, 4105).
 */
object Pid00HandshakeDecoder {

    /**
     * Decodes and strictly verifies if the raw response to "0100" is a positive Mode 01 PID 00 PDU.
     * @param response Raw string from ELM327 adapter
     * @return true if and only if valid Service 0x41 PID 0x00 bitmask is present
     */
    fun isPositivePid00Response(response: String?): Boolean {
        if (response.isNullOrBlank()) return false

        val rawUpper = response.uppercase()

        // Reject immediate error tokens
        if (rawUpper.contains("NO DATA") ||
            rawUpper.contains("NODATA") ||
            rawUpper.contains("UNABLE") ||
            rawUpper.contains("CAN ERROR") ||
            rawUpper.contains("BUS INIT: ERROR") ||
            rawUpper.contains("STOPPED") ||
            rawUpper.contains("BUS ERROR") ||
            rawUpper.contains("BUFFER FULL") ||
            rawUpper.contains("FB ERROR") ||
            rawUpper.trim() == "?"
        ) {
            return false
        }

        // Split into lines to cleanly handle multiline ISO-TP CAN and single-line K-Line responses
        val lines = response.split(Regex("[\\r\\n]+"))
            .map { it.replace(">", "").trim() }
            .filter { it.isNotBlank() && !it.startsWith("SEARCHING", ignoreCase = true) }

        for (line in lines) {
            // Strip CAN multi-frame sequence prefix e.g. "0: ", "1: ", "018"
            val stripped = line.replace(Regex("^[0-9A-F]:\\s*"), "")
                .replace(Regex("[\\s]+"), "")
                .uppercase()

            val idx = stripped.indexOf("4100")
            if (idx != -1) {
                val dataAfter = stripped.substring(idx + 4)
                // Mode 01 PID 00 requires at least 4 bytes (8 hex characters) of bitmask data
                if (dataAfter.length >= 8) {
                    val bitmaskHex = dataAfter.substring(0, 8)
                    if (bitmaskHex.all { it.isDigit() || it in 'A'..'F' }) {
                        return true
                    }
                }
            }
        }

        return false
    }
}
