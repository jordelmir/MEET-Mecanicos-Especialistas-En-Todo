package com.elysium369.meet.core.obd

import android.util.Log

/**
 * DtcDecoder — Professional OBD2 Diagnostic Trouble Code decoder.
 * Handles Modes 03 (Active), 07 (Pending), and 0A (Permanent).
 *
 * V2: Sequential prefix scanning (no destructive split), DTC count byte
 * awareness, multi-ECU handling, and robust validation.
 */
object DtcDecoder {

    private const val TAG = "DtcDecoder"

    /**
     * Decodifica una respuesta cruda (posiblemente multi-frame) en una lista de DTCs.
     * @param response La respuesta cruda del adaptador ELM327.
     * @param mode El modo OBD2 ("03", "07", "0A") para validar el prefijo de respuesta.
     */
    private data class PathResult(
        val dtcs: List<String>,
        val score: Int,
        val consumedLength: Int
    )

    init {
        try {
            runSelfTests()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run self-tests at startup", e)
        }
    }

    /**
     * Decodifica una respuesta cruda (posiblemente multi-frame) en una lista de DTCs.
     * @param response La respuesta cruda del adaptador ELM327.
     * @param mode El modo OBD2 ("03", "07", "0A") para validar el prefijo de respuesta.
     */
    fun decode(response: String, mode: String): List<String> {
        val fullHex = CanMultiFrameParser.parse(response)

        Log.d(TAG, "decode(mode=$mode) cleanHex=[$fullHex]")

        if (fullHex.isEmpty() ||
            fullHex.contains("NODATA", ignoreCase = true) ||
            fullHex.contains("ERROR", ignoreCase = true) ||
            fullHex.contains("?")
        ) {
            Log.d(TAG, "No data or error response")
            return emptyList()
        }

        // Clean: remove spaces, ensure uppercase
        val hex = fullHex.replace(" ", "").replace("\r", "").replace("\n", "").uppercase()

        val expectedPrefix = when (mode.uppercase()) {
            "03" -> "43"
            "07" -> "47"
            "0A" -> "4A"
            else -> (mode.uppercase().toIntOrNull(16)?.plus(0x40))
                ?.let { String.format("%02X", it) } ?: mode
        }

        Log.d(TAG, "Looking for prefix '$expectedPrefix' in hex: $hex")

        // Quick check: if the entire string after prefix is only zeroes/F's, return empty
        val hexSanitized = hex.replace(expectedPrefix, "")
        if (hexSanitized.isEmpty() || hexSanitized.all { it == '0' || it == 'F' }) {
            Log.d(TAG, "Response contains only prefix and padding/zeros: $hex")
            return emptyList()
        }

        val codes = mutableSetOf<String>()

        // ═══ STRATEGY 1: Sequential prefix scanning with Dual-Path Scoring ═══
        var searchStart = 0
        while (searchStart < hex.length) {
            val prefixIdx = hex.indexOf(expectedPrefix, searchStart)
            if (prefixIdx < 0) break

            Log.d(TAG, "Found prefix '$expectedPrefix' at index $prefixIdx")

            // remainingHex starts right AFTER the prefix!
            val remainingHex = hex.substring(prefixIdx + 2)

            if (remainingHex.isNotEmpty()) {
                val pathA = scorePath(remainingHex, useCountByte = true, expectedPrefix = expectedPrefix)
                val pathB = scorePath(remainingHex, useCountByte = false, expectedPrefix = expectedPrefix)

                Log.d(TAG, "Path A (With Count): score=${pathA.score}, DTCs=${pathA.dtcs}, consumed=${pathA.consumedLength}")
                Log.d(TAG, "Path B (No Count): score=${pathB.score}, DTCs=${pathB.dtcs}, consumed=${pathB.consumedLength}")

                val winner = if (pathA.score >= pathB.score) {
                    Log.d(TAG, "Path A wins!")
                    pathA
                } else {
                    Log.d(TAG, "Path B wins!")
                    pathB
                }

                codes.addAll(winner.dtcs)
                // Move search past this block
                searchStart = prefixIdx + winner.consumedLength
            } else {
                searchStart = prefixIdx + 2
            }
        }

        // ═══ STRATEGY 2: Prefix-sensitive brute-force fallback ═══
        if (codes.isEmpty() && hex.length >= 4) {
            Log.d(TAG, "Strategy 1 found no DTCs, trying brute-force extraction")
            var pos = 0
            val standardPrefixes = setOf("43", "47", "4A", expectedPrefix)

            while (pos + 4 <= hex.length) {
                var skipped = false
                for (pref in standardPrefixes) {
                    if (hex.substring(pos).startsWith(pref)) {
                        Log.d(TAG, "Strategy 2: Proactively skipping prefix '$pref' at index $pos")
                        pos += 2
                        skipped = true
                        break
                    }
                }
                if (skipped) continue

                val dtcHex = hex.substring(pos, pos + 4)
                if (dtcHex != "0000" && dtcHex != "FFFF") {
                    val dtc = tryHexToDtc(dtcHex)
                    if (dtc != null && isValidDtc(dtc)) {
                        codes.add(dtc)
                        Log.d(TAG, "Brute-force DTC: $dtcHex → $dtc")
                    }
                }
                pos += 4
            }
        }

        Log.d(TAG, "Final DTCs: $codes")
        return codes.toList()
    }

    private fun scorePath(remainingHex: String, useCountByte: Boolean, expectedPrefix: String): PathResult {
        val dtcs = mutableListOf<String>()
        var score = 0
        var pos = 0
        var count = 0
        var dtcsFound = 0
        var validNonFillerCount = 0

        if (useCountByte) {
            if (remainingHex.length < 6) {
                return PathResult(emptyList(), -1000, 0)
            }
            val countByteStr = remainingHex.substring(0, 2)
            count = try {
                countByteStr.toInt(16)
            } catch (_: Exception) {
                -1
            }

            if (count !in 1..20) {
                return PathResult(emptyList(), -1000, 0)
            }

            pos = 2
            while (pos + 4 <= remainingHex.length && dtcsFound < count) {
                val dtcHex = remainingHex.substring(pos, pos + 4)
                if (dtcHex == "0000" || dtcHex == "FFFF") {
                    dtcsFound++
                    pos += 4
                    continue
                }

                val dtc = tryHexToDtc(dtcHex)
                if (dtc != null && isValidDtc(dtc)) {
                    dtcs.add(dtc)
                    validNonFillerCount++
                    score += 20
                } else {
                    score -= 30
                }
                dtcsFound++
                pos += 4
            }

            if (dtcsFound < count) {
                score -= 50
            } else {
                if (validNonFillerCount == count) {
                    score += 150 // Perfect Match Bonus
                } else if (validNonFillerCount > 0) {
                    score += 50 // Partial Match Bonus
                }
            }

            // Find where the leftover starts and ends
            var endOfLeftover = remainingHex.length
            var scanPos = pos
            while (scanPos < remainingHex.length) {
                if (remainingHex.substring(scanPos).startsWith(expectedPrefix)) {
                    endOfLeftover = scanPos
                    break
                }
                scanPos += 2
            }

            val leftover = remainingHex.substring(pos, endOfLeftover)
            
            // Check for active DTC in leftover padding area
            var leftoverPos = pos
            var foundActiveInPadding = false
            while (leftoverPos + 4 <= endOfLeftover) {
                val dtcHex = remainingHex.substring(leftoverPos, leftoverPos + 4)
                if (dtcHex != "0000" && dtcHex != "FFFF") {
                    val dtc = tryHexToDtc(dtcHex)
                    if (dtc != null && isValidDtc(dtc)) {
                        foundActiveInPadding = true
                    }
                }
                leftoverPos += 4
            }

            if (foundActiveInPadding) {
                score -= 150 // Leftover Active DTC Penalty
            }

            // Check if leftover is clean
            val isLeftoverPadding = leftover.isEmpty() || leftover.all { it == '0' || it == 'F' }
            if (isLeftoverPadding) {
                score += 20
                if (leftover.length % 4 == 0) {
                    score += 20
                }
            }

            return PathResult(dtcs, score, consumedLength = 2 + endOfLeftover)

        } else {
            if (remainingHex.length < 4) {
                return PathResult(emptyList(), -1000, 0)
            }
            pos = 0
            var hasGhostDtc = false
            var terminatedByFiller = false

            while (pos + 4 <= remainingHex.length) {
                if (remainingHex.substring(pos).startsWith(expectedPrefix)) {
                    break
                }

                val dtcHex = remainingHex.substring(pos, pos + 4)
                if (dtcHex == "0000" || dtcHex == "FFFF") {
                    terminatedByFiller = true
                    break
                }

                if (dtcHex.startsWith(expectedPrefix)) {
                    hasGhostDtc = true
                }

                val dtc = tryHexToDtc(dtcHex)
                if (dtc != null && isValidDtc(dtc)) {
                    dtcs.add(dtc)
                    score += 20
                } else {
                    score -= 30
                }
                pos += 4
            }

            if (dtcs.isNotEmpty()) {
                score += 40
            }
            if (hasGhostDtc) {
                score -= 80 // Ghost DTC Penalty
            }

            // Find leftover boundary
            var endOfLeftover = remainingHex.length
            var scanPos = pos
            while (scanPos < remainingHex.length) {
                if (remainingHex.substring(scanPos).startsWith(expectedPrefix)) {
                    endOfLeftover = scanPos
                    break
                }
                scanPos += 2
            }

            val leftover = remainingHex.substring(pos, endOfLeftover)
            val isLeftoverPadding = leftover.isEmpty() || leftover.all { it == '0' || it == 'F' }

            if (terminatedByFiller && isLeftoverPadding) {
                score += 150 // Legacy Alignment Bonus
            }

            if (remainingHex.substring(pos).startsWith(expectedPrefix)) {
                score += 100 // Next Prefix Alignment Bonus
            }

            return PathResult(dtcs, score, consumedLength = 2 + endOfLeftover)
        }
    }

    /**
     * Convierte 4 hex chars (2 bytes) en un código DTC estándar.
     * Returns null on failure instead of "P0000".
     */
    private fun tryHexToDtc(hex: String): String? {
        if (hex.length < 4) return null
        return try {
            val b1 = hex.substring(0, 2).toInt(16)
            val b2 = hex.substring(2, 4)
            formatDtc(b1, b2)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates that a DTC string is plausible.
     * Rejects P0000 (null code), codes with all zeros, and impossible ranges.
     */
    private fun isValidDtc(dtc: String): Boolean {
        if (dtc.length != 5) return false
        if (dtc == "P0000" || dtc == "C0000" || dtc == "B0000" || dtc == "U0000") return false
        if (!dtc.matches(Regex("[PCBU][0-3][0-9A-F]{3}"))) return false
        return true
    }

    /**
     * Convierte 2 bytes hex en un código DTC estándar (Pxxxx, Cxxxx, Bxxxx, Uxxxx).
     * Según SAE J2012 / ISO 15031-6.
     */
    fun hexToDtc(hex: String): String {
        return tryHexToDtc(hex) ?: "P0000"
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

        return "$prefix$digit1${digit2.toString(16).uppercase()}$b2Hex".uppercase()
    }

    /**
     * Run all self-tests to ensure regression-free behavior.
     * Called at class load (init).
     */
    fun runSelfTests(): Boolean {
        var allPassed = true

        // Test 1: Caso CAN con Conteo
        val r1 = decode("4302010201430000", "03")
        val e1 = listOf("P0102", "P0143")
        if (r1.sorted() == e1.sorted()) {
            Log.i(TAG, "Self-test 1 (CAN with Count) PASSED")
        } else {
            Log.e(TAG, "Self-test 1 FAILED: Expected $e1, got $r1")
            allPassed = false
        }

        // Test 2: Caso Legacy sin Conteo (1 DTC)
        val r2 = decode("43010200000000", "03")
        val e2 = listOf("P0102")
        if (r2.sorted() == e2.sorted()) {
            Log.i(TAG, "Self-test 2 (Legacy 1 DTC) PASSED")
        } else {
            Log.e(TAG, "Self-test 2 FAILED: Expected $e2, got $r2")
            allPassed = false
        }

        // Test 3: Caso Múltiples ECUs
        val r3 = decode("43010200004301430000", "03")
        val e3 = listOf("P0102", "P0143")
        if (r3.sorted() == e3.sorted()) {
            Log.i(TAG, "Self-test 3 (Multiple ECUs) PASSED")
        } else {
            Log.e(TAG, "Self-test 3 FAILED: Expected $e3, got $r3")
            allPassed = false
        }

        // Test 4: Caso Sin Códigos
        val r4 = decode("43000000000000", "03")
        val e4 = emptyList<String>()
        if (r4 == e4) {
            Log.i(TAG, "Self-test 4 (No Codes) PASSED")
        } else {
            Log.e(TAG, "Self-test 4 FAILED: Expected $e4, got $r4")
            allPassed = false
        }

        // Test 5: Caso Fuerza Bruta con Prefijo
        val r5 = decode("4301020000", "99") // Unrecognized mode to force brute force
        val e5 = listOf("P0102")
        if (r5.sorted() == e5.sorted()) {
            Log.i(TAG, "Self-test 5 (Brute Force Prefix Skip) PASSED")
        } else {
            Log.e(TAG, "Self-test 5 FAILED: Expected $e5, got $r5")
            allPassed = false
        }

        Log.i(TAG, "Self-tests finished. Overall status: ${if (allPassed) "SUCCESS" else "FAILURE"}")
        return allPassed
    }

    fun decodeDtcResponse(response: String) = decode(response, "03")
    fun decodePendingResponse(response: String) = decode(response, "07")
    fun decodePermanentResponse(response: String) = decode(response, "0A")

    fun decodeUdsService19(response: String): List<String> {
        val fullHex = CanMultiFrameParser.parse(response)
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .uppercase()
        Log.d(TAG, "decodeUdsService19 cleanHex=[$fullHex]")
        if (fullHex.isEmpty() || fullHex.contains("NODATA") || fullHex.contains("ERROR") || fullHex.contains("?")) {
            return emptyList()
        }

        val idx = fullHex.indexOf("5902")
        if (idx < 0) return emptyList()

        val data = fullHex.substring(idx + 4)
        if (data.length < 2) return emptyList()

        val dtcData = data.substring(2)
        val codes = mutableSetOf<String>()
        var pos = 0
        while (pos + 8 <= dtcData.length) {
            val dtcHex = dtcData.substring(pos, pos + 6)
            try {
                val b1 = dtcHex.substring(0, 2).toInt(16)
                val b2Hex = dtcHex.substring(2, 4)
                val code = formatDtc(b1, b2Hex)
                if (isValidDtc(code)) {
                    codes.add(code)
                }
            } catch (_: Exception) {}
            pos += 8
        }
        return codes.toList()
    }
}
