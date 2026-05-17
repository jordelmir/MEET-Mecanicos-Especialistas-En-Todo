package com.elysium369.meet.core.obd

import kotlinx.coroutines.delay

/**
 * AdvancedDiagnosticManager — Handles high-level complex diagnostic routines.
 * Examples: ECU Coding, Service Resets, Adaptation.
 */
class AdvancedDiagnosticManager(private val obdSession: ObdSession) {

    /**
     * Executes a block of commands under a specific CAN header and ensures 7DF restoration.
     */
    private suspend fun <T> withHeader(header: String, block: suspend () -> T): T {
        obdSession.sendRawCommand("ATSH$header")
        return try {
            block()
        } finally {
            try { obdSession.sendRawCommand("ATSH7DF") } catch (_: Exception) {}
        }
    }

    /**
     * Resets the Oil Life monitor.
     * Uses manufacturer-specific sequences.
     */
    suspend fun resetOilService(manufacturer: String): Boolean {
        if (!obdSession.verifySafetyForProAction(listOf(SafetyCondition.ENGINE_OFF))) return false

        return when (manufacturer.uppercase()) {
            "VOLKSWAGEN", "AUDI", "SKODA", "SEAT" -> {
                withHeader("7E0") {
                    obdSession.sendRawCommand("1003") // Extended Diagnostic Session
                    delay(200)
                    val resp = obdSession.sendRawCommand("2E000200") 
                    resp.startsWith("6E")
                }
            }
            "FORD", "MAZDA" -> {
                withHeader("7E0") {
                    obdSession.sendRawCommand("1003")
                    delay(100)
                    val resp = obdSession.sendRawCommand("2E053300")
                    resp.startsWith("6E") || !resp.contains("ERROR")
                }
            }
            "BMW", "MINI" -> {
                withHeader("6017F1") {
                    obdSession.sendRawCommand("1003")
                    val resp = obdSession.sendRawCommand("3101FF00")
                    resp.startsWith("71")
                }
            }
            "TOYOTA", "LEXUS" -> {
                withHeader("7E0") {
                    val resp = obdSession.sendRawCommand("2E010100") 
                    resp.startsWith("6E")
                }
            }
            else -> {
                obdSession.sendRawCommand("04")
                true
            }
        }
    }

    /**
     * Registers a new battery to the BMS (Battery Management System).
     */
    suspend fun registerBattery(manufacturer: String, capacityAh: Int): Boolean {
        if (!obdSession.verifySafetyForProAction(listOf(SafetyCondition.ENGINE_OFF))) return false
        
        return when (manufacturer.uppercase()) {
            "BMW" -> {
                withHeader("6B10F1") {
                    obdSession.sendRawCommand("1003")
                    val resp = obdSession.sendRawCommand("3101B001")
                    resp.startsWith("71")
                }
            }
            "VOLKSWAGEN", "AUDI" -> {
                withHeader("7E0") {
                    obdSession.sendRawCommand("1003")
                    val hexCap = Integer.toHexString(capacityAh).padStart(2, '0')
                    val resp = obdSession.sendRawCommand("2E1234$hexCap")
                    resp.startsWith("6E")
                }
            }
            else -> false
        }
    }

    /**
     * Resets the Electronic Parking Brake (EPB) for pad replacement.
     */
    suspend fun resetEPB(manufacturer: String, open: Boolean): Boolean {
        if (!obdSession.verifySafetyForProAction(listOf(SafetyCondition.ENGINE_OFF))) return false
        
        val mode = if (open) "01" else "02"
        return when (manufacturer.uppercase()) {
            "VOLKSWAGEN", "AUDI" -> {
                withHeader("7E0") {
                    obdSession.sendRawCommand("1003")
                    val resp = obdSession.sendRawCommand("3101000$mode")
                    resp.startsWith("71")
                }
            }
            else -> false
        }
    }

    /**
     * Calibrates the Steering Angle Sensor (SAS).
     */
    suspend fun calibrateSAS(manufacturer: String): Boolean {
        if (!obdSession.verifySafetyForProAction(listOf(SafetyCondition.ENGINE_OFF, SafetyCondition.VEHICLE_STATIONARY))) return false
        
        return when (manufacturer.uppercase()) {
            "VOLKSWAGEN", "AUDI", "SKODA", "SEAT" -> {
                withHeader("7E0") { // Note: On some VAG this is 7E0 (ECM handles it) or 760 (ABS)
                    obdSession.sendRawCommand("1003")
                    if (performSecurityAccessVAG("03")) {
                        val resp = obdSession.sendRawCommand("31010001")
                        resp.startsWith("71")
                    } else {
                        obdSession.sendRawCommand("2701")
                        obdSession.sendRawCommand("2702403F")
                        val resp = obdSession.sendRawCommand("31010001")
                        resp.startsWith("71")
                    }
                }
            }
            "TOYOTA" -> {
                withHeader("7E0") {
                    val resp = obdSession.sendRawCommand("30010001")
                    resp.startsWith("70")
                }
            }
            "FORD" -> {
                withHeader("760") {
                    obdSession.sendRawCommand("1003")
                    val resp = obdSession.sendRawCommand("3101FF01") 
                    resp.startsWith("71")
                }
            }
            else -> false
        }
    }

    /**
     * Performs Throttle Body Relearn / Adaptation.
     */
    suspend fun relearnThrottle(manufacturer: String): Boolean {
        if (!obdSession.verifySafetyForProAction(listOf(SafetyCondition.ENGINE_OFF, SafetyCondition.BATTERY_ABOVE_12V))) return false
        
        return when (manufacturer.uppercase()) {
            "VOLKSWAGEN", "AUDI", "SKODA", "SEAT" -> {
                withHeader("7E0") {
                    obdSession.sendRawCommand("1003")
                    val resp = obdSession.sendRawCommand("31010060") 
                    resp.startsWith("71") || resp.contains("OK")
                }
            }
            "GM", "CHEVROLET" -> {
                withHeader("7E0") {
                    obdSession.sendRawCommand("1003")
                    val resp = obdSession.sendRawCommand("3101A002")
                    resp.startsWith("71")
                }
            }
            else -> false
        }
    }

    /**
     * Triggers DPF (Diesel Particulate Filter) Regeneration.
     */
    suspend fun regenerateDPF(manufacturer: String): Boolean {
        if (!obdSession.verifySafetyForProAction(listOf(SafetyCondition.ENGINE_RUNNING, SafetyCondition.BATTERY_ABOVE_12V))) return false
        
        return when (manufacturer.uppercase()) {
            "VOLKSWAGEN", "AUDI", "SKODA", "SEAT" -> {
                withHeader("7E0") {
                    obdSession.sendRawCommand("1003")
                    if (performSecurityAccessVAG("01")) {
                        val resp = obdSession.sendRawCommand("3101000F")
                        resp.startsWith("71")
                    } else false
                }
            }
            "BMW" -> {
                withHeader("6017F1") {
                    obdSession.sendRawCommand("1003")
                    val resp = obdSession.sendRawCommand("3101AB01")
                    resp.startsWith("71")
                }
            }
            else -> false
        }
    }

    private suspend fun performSecurityAccessVAG(moduleId: String): Boolean {
        val seedResp = obdSession.sendRawCommand("2701")
        if (seedResp.startsWith("6701")) {
            val seed = seedResp.substring(4).take(8)
            val key = calculateSecurityKeyVAG(seed, moduleId)
            val authResp = obdSession.sendRawCommand("2702$key")
            return authResp.startsWith("6702")
        }
        return false
    }

    private fun calculateSecurityKeyVAG(seed: String, moduleId: String): String {
        val cleanSeed = seed.replace(" ", "").replace(">", "").take(8)
        if (cleanSeed.isEmpty() || !cleanSeed.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) {
            return "00000000"
        }
        val seedInt = cleanSeed.toLong(16)
        val keyInt = seedInt xor 0x55AA55AA
        return keyInt.toString(16).padStart(8, '0').uppercase().takeLast(8)
    }

    suspend fun performCoding(featureId: String, enable: Boolean): Boolean {
        if (!obdSession.verifySafetyForProAction(listOf(SafetyCondition.ENGINE_OFF))) return false
        
        return when (featureId) {
            "NEEDLE_SWEEP" -> {
                withHeader("7E0") {
                    obdSession.sendRawCommand("1003")
                    val cmd = if (enable) "2E123401" else "2E123400"
                    val resp = obdSession.sendRawCommand(cmd)
                    resp.startsWith("6E")
                }
            }
            else -> false
        }
    }
}

data class CodingFeature(
    val id: String,
    val name: String,
    val description: String,
    val manufacturer: String,
    val isEnabled: Boolean
)
