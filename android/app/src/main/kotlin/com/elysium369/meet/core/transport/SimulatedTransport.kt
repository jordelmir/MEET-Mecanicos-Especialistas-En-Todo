package com.elysium369.meet.core.transport

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

class SimulatedTransport : TransportInterface {
    private val TAG = "SimulatedTransport"
    
    private var _isConnected = false
    override val isConnected: Boolean
        get() = _isConnected

    private var currentCommand = ""
    private val startTime = System.currentTimeMillis()

    override suspend fun connect() {
        Log.i(TAG, "Connecting to virtual OBD simulator...")
        delay(800)
        _isConnected = true
        Log.i(TAG, "Virtual OBD simulator connected.")
    }

    override suspend fun disconnect() {
        Log.i(TAG, "Disconnecting virtual OBD simulator...")
        _isConnected = false
        delay(200)
    }

    override suspend fun reconnect() {
        disconnect()
        connect()
    }

    override suspend fun write(data: ByteArray) {
        if (!_isConnected) throw java.io.IOException("Transport is not connected")
        val cmd = String(data).trim().uppercase()
        currentCommand = cmd
        Log.d(TAG, "WRITE: $cmd")
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? {
        if (!_isConnected) throw java.io.IOException("Transport is not connected")
        delay(15) // Simulate latency

        val cmd = currentCommand.replace(" ", "")
        val response = when {
            cmd.startsWith("AT") -> handleAtCommand(cmd)
            cmd.startsWith("01") -> handleMode01Command(cmd)
            cmd.startsWith("09") -> handleMode09Command(cmd)
            cmd.startsWith("06") -> handleMode06Command(cmd)
            cmd == "03" -> "43 02 01 04 04 00" // DTCs: P0104 & P0400
            cmd == "04" -> "44" // Clear DTCs
            else -> "OK"
        }

        val fullResponse = "$response\r\r>"
        Log.d(TAG, "READ: $fullResponse")
        return fullResponse.toByteArray()
    }

    override suspend fun drain() {
        // No-op for simulation
    }

    private fun handleAtCommand(cmd: String): String {
        return when {
            cmd.startsWith("ATZ") || cmd.startsWith("ATI") -> "ELM327 v2.1"
            cmd.startsWith("ATE") || cmd.startsWith("ATL") || cmd.startsWith("ATS") || 
            cmd.startsWith("ATH") || cmd.startsWith("ATSP") || cmd.startsWith("ATSG") ||
            cmd.startsWith("ATCAF") || cmd.startsWith("ATCRA") || cmd.startsWith("ATSH") -> "OK"
            cmd.startsWith("ATDPN") -> "A6"
            cmd.startsWith("ATDP") -> "ISO 15765-4 (CAN 11/500)"
            cmd.startsWith("ATRV") -> {
                val volt = 13.8f + 0.4f * sin((System.currentTimeMillis() - startTime) / 3000.0).toFloat()
                String.format("%.1fV", volt)
            }
            else -> "OK"
        }
    }

    private fun handleMode01Command(cmd: String): String {
        val pidHex = cmd.removePrefix("01").take(2)
        val t = System.currentTimeMillis() - startTime
        
        return when (pidHex) {
            "00" -> "41 00 BE 3E A8 13" // Supported PIDs 01-20
            "20" -> "41 20 80 00 00 00" // Supported PIDs 21-40
            "40" -> "41 40 00 00 00 00" // Supported PIDs 41-60
            "0C" -> { // RPM
                val rpm = 750f + 1400f * (sin(t / 6000.0) + 1.0).toFloat()
                val obdVal = (rpm * 4f).toInt()
                val hexA = String.format("%02X", (obdVal shr 8) and 0xFF)
                val hexB = String.format("%02X", obdVal and 0xFF)
                "41 0C $hexA $hexB"
            }
            "0D" -> { // Speed
                val speed = 40f + 35f * sin(t / 8000.0).toFloat()
                val hex = String.format("%02X", speed.toInt().coerceIn(0, 255))
                "41 0D $hex"
            }
            "05" -> { // Coolant Temp
                val temp = (40f + (t / 15000f) * 45f).coerceAtMost(89f).toInt()
                val hex = String.format("%02X", temp + 40)
                "41 05 $hex"
            }
            "04" -> { // Engine Load
                val load = 15f + 40f * (sin(t / 4000.0) + 1.0).toFloat()
                val obdVal = (load * 2.55f).toInt()
                val hex = String.format("%02X", obdVal.coerceIn(0, 255))
                "41 04 $hex"
            }
            "0B" -> { // MAP
                val map = 98f + 20f * sin(t / 4000.0).toFloat()
                val hex = String.format("%02X", map.toInt().coerceIn(0, 255))
                "41 0B $hex"
            }
            "10" -> { // MAF
                val maf = 8f + 25f * (sin(t / 5000.0) + 1.0).toFloat()
                val obdVal = (maf * 100f).toInt()
                val hexA = String.format("%02X", (obdVal shr 8) and 0xFF)
                val hexB = String.format("%02X", obdVal and 0xFF)
                "41 10 $hexA $hexB"
            }
            "11" -> { // Throttle Pos
                val throttle = 12f + 35f * (sin(t / 4000.0) + 1.0).toFloat()
                val obdVal = (throttle * 2.55f).toInt()
                val hex = String.format("%02X", obdVal.coerceIn(0, 255))
                "41 11 $hex"
            }
            "2F" -> "41 2F 75" // Fuel Level (58%)
            "42" -> { // Control Module Voltage
                val voltVal = (14.1f * 1000f).toInt()
                val hexA = String.format("%02X", (voltVal shr 8) and 0xFF)
                val hexB = String.format("%02X", voltVal and 0xFF)
                "41 42 $hexA $hexB"
            }
            "1F" -> { // Run time
                val runTime = (t / 1000L).toInt()
                val hexA = String.format("%02X", (runTime shr 8) and 0xFF)
                val hexB = String.format("%02X", runTime and 0xFF)
                "41 1F $hexA $hexB"
            }
            else -> "41 $pidHex 00"
        }
    }

    private fun handleMode09Command(cmd: String): String {
        val typeHex = cmd.removePrefix("09").take(2)
        return when (typeHex) {
            "02" -> "49 02 01 31 46 4D 43 55 30 45 31 33 4B 55 43 31 30 35 34 33" // VIN: 1FMCU0E13KUC10543
            "04" -> "49 04 01 43 41 4C 2D 49 44 2D 56 4D 45 45 54" // Calibration ID
            else -> "49 $typeHex 00"
        }
    }

    private fun handleMode06Command(cmd: String): String {
        val midHex = cmd.removePrefix("06").take(2)
        val t = System.currentTimeMillis() - startTime

        return when (midHex) {
            "00" -> "46 00 CC 00 00 00" // MIDs supported: 01, 02, 05, 06
            "20" -> "46 20 80 00 80 00" // MIDs supported: 21, 31
            "A0" -> "46 A0 F0 00 00 00" // MIDs supported: A1, A2, A3, A4
            "01" -> { // O2 Sensor B1S1 (MID 01)
                // TID 01 (Rich-to-Lean threshold), UID 08 (V, scale 0.01)
                // Value 35 (0.35V), Min 15 (0.15V), Max 55 (0.55V)
                "46 01 01 08 00 23 00 0F 00 37"
            }
            "02" -> { // O2 Sensor B1S2
                "46 02 01 08 00 22 00 0F 00 37"
            }
            "05" -> { // O2 Sensor B2S1
                "46 05 01 08 00 1F 00 0F 00 37"
            }
            "06" -> { // O2 Sensor B2S2
                "46 06 01 08 00 21 00 0F 00 37"
            }
            "21" -> { // Catalyst B1 (MID 21)
                // TID 11, UID 1B (ratio, scale 0.1)
                // Value 160 (16.0), Min 64 (6.4), Max 240 (24.0)
                "46 21 11 1B 00 A0 00 40 00 F0"
            }
            "31" -> { // EGR (MID 31)
                // TID 51, UID 01 (cnt, scale 1)
                // Value 21, Min 0, Max 37
                "46 31 51 01 00 15 00 00 00 25"
            }
            "A1" -> { // Misfire Cylinder 1 (MID A1)
                // TID 0B, UID 01 (cnt, scale 1)
                // Value 2, Min 0, Max 16 (passed)
                "46 A1 0B 01 00 02 00 00 00 10"
            }
            "A2" -> { // Misfire Cylinder 2
                "46 A2 0B 01 00 01 00 00 00 10"
            }
            "A3" -> { // Misfire Cylinder 3
                "46 A3 0B 01 00 00 00 00 00 10"
            }
            "A4" -> { // Misfire Cylinder 4
                "46 A4 0B 01 00 00 00 00 00 10"
            }
            else -> "46 $midHex 00 00 00 00 00 00 00 00"
        }
    }
}
