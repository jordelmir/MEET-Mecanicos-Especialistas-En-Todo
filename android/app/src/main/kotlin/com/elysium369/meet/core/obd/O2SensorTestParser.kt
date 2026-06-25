package com.elysium369.meet.core.obd

import android.util.Log

/**
 * O2SensorTestParser — Mode $05 O2 Sensor Monitoring Test Results
 *
 * Parses responses from OBD2 Mode $05 which provides O2 sensor test data
 * including voltage thresholds, switch times, and min/max values.
 *
 * ⚠️ Mode $05 is only supported on pre-CAN vehicles (generally pre-2008).
 * CAN-based vehicles use Mode $06 for equivalent data.
 *
 * Command format: 05 [TID] [SensorID]
 * Response format: 45 [TID] [SensorID] [DataA] [DataB]
 *
 * Standard Test IDs (TIDs):
 *   $01: Rich-to-Lean Sensor Threshold Voltage
 *   $02: Lean-to-Rich Sensor Threshold Voltage
 *   $03: Low Sensor Voltage for Switch Time Calculation
 *   $04: High Sensor Voltage for Switch Time Calculation
 *   $05: Rich-to-Lean Sensor Switch Time
 *   $06: Lean-to-Rich Sensor Switch Time
 *   $07: Minimum Sensor Voltage for Test Cycle
 *   $08: Maximum Sensor Voltage for Test Cycle
 *   $09: Time Between Sensor Transitions
 */
class O2SensorTestParser {

    private val TAG = "O2SensorTestParser"

    /** TID definitions with scaling factors and unit types */
    data class TidDefinition(
        val id: Int,
        val nameEn: String,
        val nameEs: String,
        val scaleFactor: Double,
        val unit: String,
        val isVoltage: Boolean = true
    )

    private val tidDefinitions = mapOf(
        0x01 to TidDefinition(0x01, "Rich-to-Lean Threshold Voltage", "Voltaje Umbral Rico-a-Pobre", 0.005, "V"),
        0x02 to TidDefinition(0x02, "Lean-to-Rich Threshold Voltage", "Voltaje Umbral Pobre-a-Rico", 0.005, "V"),
        0x03 to TidDefinition(0x03, "Low Voltage for Switch Time", "Voltaje Bajo para Tiempo de Cambio", 0.005, "V"),
        0x04 to TidDefinition(0x04, "High Voltage for Switch Time", "Voltaje Alto para Tiempo de Cambio", 0.005, "V"),
        0x05 to TidDefinition(0x05, "Rich-to-Lean Switch Time", "Tiempo de Cambio Rico-a-Pobre", 0.004, "s", false),
        0x06 to TidDefinition(0x06, "Lean-to-Rich Switch Time", "Tiempo de Cambio Pobre-a-Rico", 0.004, "s", false),
        0x07 to TidDefinition(0x07, "Minimum Sensor Voltage", "Voltaje Mínimo del Sensor", 0.005, "V"),
        0x08 to TidDefinition(0x08, "Maximum Sensor Voltage", "Voltaje Máximo del Sensor", 0.005, "V"),
        0x09 to TidDefinition(0x09, "Time Between Transitions", "Tiempo Entre Transiciones", 0.04, "s", false)
    )

    /** Sensor ID to bank/number mapping */
    private fun sensorIdToLabel(sensorId: Int): Triple<String, Int, Int> {
        return when (sensorId) {
            0x01 -> Triple("B1S1", 1, 1)
            0x02 -> Triple("B1S2", 1, 2)
            0x03 -> Triple("B1S3", 1, 3)
            0x04 -> Triple("B1S4", 1, 4)
            0x05 -> Triple("B2S1", 2, 1)
            0x06 -> Triple("B2S2", 2, 2)
            0x07 -> Triple("B2S3", 2, 3)
            0x08 -> Triple("B2S4", 2, 4)
            else -> Triple("B?S?", 0, 0)
        }
    }

    /**
     * Nominal pass/fail thresholds per TID.
     * These are standard SAE J1979 recommended ranges.
     */
    private fun getThresholds(tid: Int): Pair<Float?, Float?> {
        return when (tid) {
            0x01 -> 0.300f to 0.600f    // Rich-to-Lean threshold: 0.3V - 0.6V
            0x02 -> 0.300f to 0.600f    // Lean-to-Rich threshold: 0.3V - 0.6V
            0x03 -> 0.000f to 0.200f    // Low voltage: 0V - 0.2V
            0x04 -> 0.600f to 1.000f    // High voltage: 0.6V - 1.0V
            0x05 -> null to 0.100f      // Rich-to-Lean switch: < 100ms
            0x06 -> null to 0.100f      // Lean-to-Rich switch: < 100ms
            0x07 -> 0.000f to 0.250f    // Min voltage: 0V - 0.25V
            0x08 -> 0.650f to 1.100f    // Max voltage: 0.65V - 1.1V
            0x09 -> null to 2.000f      // Transition time: < 2s
            else -> null to null
        }
    }

    /**
     * Parse all raw Mode $05 responses into structured results.
     *
     * @param rawResponses List of hex response strings from the ELM327
     * @return List of O2SensorTestResult objects
     */
    fun parse(rawResponses: List<String>): List<O2SensorTestResult> {
        val results = mutableListOf<O2SensorTestResult>()

        for (raw in rawResponses) {
            try {
                val parsed = parseSingleResponse(raw)
                if (parsed != null) results.add(parsed)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse Mode 05 response: $raw", e)
            }
        }

        return results.sortedWith(compareBy({ it.bank }, { it.sensorNumber }, { it.testId }))
    }

    /**
     * Parse a single Mode $05 response line.
     * Expected format after ELM327 cleanup: "45 TT SS AA BB"
     * Where TT=TID, SS=SensorID, AA BB=16-bit data value
     */
    private fun parseSingleResponse(raw: String): O2SensorTestResult? {
        // Clean and extract hex bytes
        val clean = raw.replace(Regex("[\\s\\r\\n>]+"), " ").trim().uppercase()
        val bytes = clean.split(" ").filter { it.length == 2 && it.all { c -> c.isDigit() || c in 'A'..'F' } }

        // Find the 45 response header
        val headerIdx = bytes.indexOf("45")
        if (headerIdx < 0 || headerIdx + 4 >= bytes.size) return null

        val tidByte = bytes[headerIdx + 1].toIntOrNull(16) ?: return null
        val sensorByte = bytes[headerIdx + 2].toIntOrNull(16) ?: return null
        val dataA = bytes[headerIdx + 3].toIntOrNull(16) ?: return null
        val dataB = bytes[headerIdx + 4].toIntOrNull(16) ?: return null

        val tidDef = tidDefinitions[tidByte] ?: return null
        val (sensorLabel, bank, sensorNum) = sensorIdToLabel(sensorByte)
        if (bank == 0) return null

        // Calculate the 16-bit value
        val rawValue = (dataA shl 8) or dataB
        val scaledValue = (rawValue * tidDef.scaleFactor).toFloat()

        // Get thresholds for pass/fail determination
        val (minLimit, maxLimit) = getThresholds(tidByte)
        val passed = when {
            minLimit != null && maxLimit != null -> scaledValue in minLimit..maxLimit
            minLimit != null -> scaledValue >= minLimit
            maxLimit != null -> scaledValue <= maxLimit
            else -> true
        }

        return O2SensorTestResult(
            sensorId = sensorLabel,
            bank = bank,
            sensorNumber = sensorNum,
            testId = tidByte,
            testDescription = tidDef.nameEn,
            testDescriptionEs = tidDef.nameEs,
            value = scaledValue,
            minLimit = minLimit,
            maxLimit = maxLimit,
            unit = tidDef.unit,
            passed = passed
        )
    }

    /**
     * Generate the list of Mode $05 commands to send.
     * Queries all TIDs (01-09) for all potentially available sensors (01-08).
     * The ECU will respond with "NO DATA" for unsupported combinations.
     */
    fun generateCommands(): List<String> {
        val commands = mutableListOf<String>()
        // Query supported TIDs first
        commands.add("0500") // PID $00 — Supported O2 sensor test TIDs
        // Then each TID for common sensors
        for (tid in 0x01..0x09) {
            for (sensor in 0x01..0x08) {
                commands.add(String.format("05%02X%02X", tid, sensor))
            }
        }
        return commands
    }

    /**
     * Generate a compact subset of commands for the most common sensors only.
     * Optimized for ELM327 communication speed.
     */
    fun generateQuickCommands(): List<String> {
        val commands = mutableListOf<String>()
        // Only B1S1 (01), B1S2 (02), B2S1 (05), B2S2 (06) — most common
        val sensors = listOf(0x01, 0x02, 0x05, 0x06)
        for (tid in 0x01..0x09) {
            for (sensor in sensors) {
                commands.add(String.format("05%02X%02X", tid, sensor))
            }
        }
        return commands
    }
}
