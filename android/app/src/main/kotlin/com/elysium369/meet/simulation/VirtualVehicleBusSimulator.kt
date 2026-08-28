package com.elysium369.meet.simulation

enum class SimulatedFault {
    NONE,
    VOLTAGE_DROP_SHUTDOWN,
    BLUETOOTH_RFCOMM_EOF,
    ECU_SILENCE_TIMEOUT,
    CAN_BUS_OFF_ERROR,
    MALFORMED_ISOTP_FRAME,
    UDS_NEGATIVE_RESPONSE_CODE_22,
}

data class SimulatedVehicleState(
    val vin: String = "1HGCR2F83HA123456",
    val engineRpm: Float = 850f,
    val vehicleSpeedKmh: Float = 0f,
    val coolantTempC: Float = 88f,
    val dtcs: List<String> = listOf("P0300", "P0171"),
    val isSimulated: Boolean = true, // SUPREME INVARIANT: NEVER PHYSICAL EVIDENCE
)

/**
 * VirtualVehicleBusSimulator — Deterministic vehicle bus and ECU simulation engine.
 * Enables repeatable HIL / unit testing with active fault injection without risking physical vehicles.
 */
class VirtualVehicleBusSimulator(
    private val state: SimulatedVehicleState = SimulatedVehicleState(),
    var activeFault: SimulatedFault = SimulatedFault.NONE,
) {

    fun handleAsciiCommand(command: String): String {
        val cmd = command.trim().uppercase()

        if (activeFault == SimulatedFault.VOLTAGE_DROP_SHUTDOWN || activeFault == SimulatedFault.BLUETOOTH_RFCOMM_EOF) {
            return "" // Dead bus / socket closed
        }

        if (activeFault == SimulatedFault.ECU_SILENCE_TIMEOUT) {
            return "NO DATA\r\n>"
        }

        if (activeFault == SimulatedFault.CAN_BUS_OFF_ERROR) {
            return "CAN ERROR\r\n>"
        }

        return when {
            cmd == "ATZ" -> "ELM327 v1.5\r\n>"
            cmd == "ATE0" || cmd == "ATL0" || cmd == "ATH0" || cmd == "ATSP0" || cmd == "ATSP6" -> "OK\r\n>"
            cmd == "ATI" -> "ELM327 v1.5\r\n>"
            cmd == "ATDP" || cmd == "ATDPN" -> "ISO 15765-4 (CAN 11/500)\r\n>"
            cmd == "0100" -> "41 00 BE 3E B8 11\r\n>" // PIDs supported 01-20
            cmd == "010C" -> {
                // RPM formula: ((A*256)+B)/4
                val rawVal = (state.engineRpm * 4).toInt()
                val a = (rawVal shr 8) and 0xFF
                val b = rawVal and 0xFF
                "41 0C %02X %02X\r\n>".format(a, b)
            }
            cmd == "010D" -> "41 0D %02X\r\n>".format(state.vehicleSpeedKmh.toInt())
            cmd == "0105" -> "41 05 %02X\r\n>".format((state.coolantTempC + 40).toInt())
            cmd == "0902" -> "49 02 01 31 48 47 43 52 32 46 38 33 48 41 31 32 33 34 35 36\r\n>" // VIN
            cmd == "03" -> {
                if (state.dtcs.isEmpty()) "43 00\r\n>"
                else "43 02 03 00 01 71\r\n>" // P0300, P0171
            }
            cmd == "04" -> "44\r\n>" // Clear DTCs
            cmd.startsWith("22") -> {
                if (activeFault == SimulatedFault.UDS_NEGATIVE_RESPONSE_CODE_22) {
                    "7F 22 22\r\n>" // Negative Response Code: Conditions Not Correct
                } else {
                    "62 F1 90 31 32 33\r\n>"
                }
            }
            else -> "NO DATA\r\n>"
        }
    }
}
