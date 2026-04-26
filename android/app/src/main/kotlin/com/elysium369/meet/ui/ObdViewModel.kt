package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.data.supabase.SubscriptionRepository
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.data.supabase.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class ObdViewModel @Inject constructor(
    private val obdSession: ObdSession,
    private val vehicleRepository: VehicleRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val geminiDiagnostic: com.elysium369.meet.core.ai.GeminiDiagnostic,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val connectionState: StateFlow<ObdState> = obdSession.state

    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    private val _activeDtcs = MutableStateFlow<List<String>>(emptyList())
    val activeDtcs: StateFlow<List<String>> = _activeDtcs.asStateFlow()

    val vehicles: StateFlow<List<Vehicle>> = vehicleRepository.getVehiclesForUser()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()

    fun startDiagnosticSession(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        viewModelScope.launch {
            try {
                obdSession.connect()
                // Start Foreground Service
                val serviceIntent = Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java)
                try {
                    context.startService(serviceIntent)
                } catch (e: Exception) {}
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun stopSession() {
        obdSession.disconnect()
        // Stop Foreground Service
        context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
        _selectedVehicle.value = null
        _activeDtcs.value = emptyList()
        _liveData.value = emptyMap()
    }

    fun saveVehicle(make: String, model: String, year: String, vin: String?) {
        viewModelScope.launch {
            val vehicle = Vehicle(
                id = java.util.UUID.randomUUID().toString(),
                user_id = "user_placeholder",
                year = year.toIntOrNull() ?: 2000,
                make = make,
                model = model,
                engine = "Unknown",
                vin = if (vin.isNullOrBlank()) "Sin VIN" else vin,
                plate = "Unknown"
            )
            // Save to real repository (Room DB)
            vehicleRepository.insertVehicle(vehicle)
            _selectedVehicle.value = vehicle
        }
    }

    fun connect(macAddress: String) {
        viewModelScope.launch {
            try {
                obdSession.setTargetAddress(macAddress)
                obdSession.connect() 
                val serviceIntent = Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java)
                try {
                    context.startService(serviceIntent)
                } catch (e: Exception) {}
            } catch (e: Exception) {}
        }
    }

    fun updateLiveData(pidName: String, value: Float) {
        val current = _liveData.value.toMutableMap()
        current[pidName] = value
        _liveData.value = current
    }
    
    suspend fun sendRawCommand(command: String): String {
        return obdSession.sendRawCommand(command)
    }

    suspend fun readVin(): String? {
        val raw = obdSession.sendRawCommand("09 02")
        return parseVinFromResponse(raw)
    }

    private fun parseVinFromResponse(raw: String): String? {
        val hex = raw.replace("49 02", "").replace(" ", "").trim()
        return hex.chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toChar() }
            .filter { it.isLetterOrDigit() }
            .joinToString("")
            .takeIf { it.length == 17 }
    }

    suspend fun readFreezeFrame(dtcCode: String): Map<String, Float> {
        // Modo 02 - Freeze Frame data
        val supportedPids = obdSession.sendRawCommand("0200") // qué PIDs tienen freeze frame
        val result = mutableMapOf<String, Float>()
        // Iterar PIDs disponibles (Simplificado para el demo)
        val pidsToCheck = listOf("0C", "0D", "05", "11") 
        pidsToCheck.forEach { pid ->
            val response = obdSession.sendRawCommand("02 $pid FF")
            if (response.isNotEmpty() && !response.contains("NO DATA")) {
                // Here we would use the pidRegistry formula
                result[pid] = 0f // Placeholder
            }
        }
        return result
    }

    suspend fun consultAi(apiKey: String?, endpointUrl: String?, dtcList: List<String>): String {
        geminiDiagnostic.updateConfig(apiKey, endpointUrl)
        val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
        val liveDataStrings = _liveData.value.mapValues { "%.2f".format(it.value) }
        return geminiDiagnostic.analyzeDtc(dtcList, vehicleInfo, liveDataStrings)
    }

    // ═══════════════════════════════════════════════
    // PROFESSIONAL SCANNER FEATURES
    // ═══════════════════════════════════════════════

    // --- Clear DTCs (Mode 04) ---
    private val _clearDtcResult = MutableStateFlow<String?>(null)
    val clearDtcResult: StateFlow<String?> = _clearDtcResult.asStateFlow()

    suspend fun clearDtcs(): Boolean {
        return try {
            val response = obdSession.sendRawCommand("04")
            val success = !response.contains("ERROR") && !response.contains("UNABLE")
            if (success) {
                _activeDtcs.value = emptyList()
                _pendingDtcs.value = emptyList()
                _clearDtcResult.value = "✅ Códigos borrados exitosamente. MIL apagada."
            } else {
                _clearDtcResult.value = "❌ Error al borrar: $response"
            }
            success
        } catch (e: Exception) {
            _clearDtcResult.value = "❌ Error: ${e.message}"
            false
        }
    }

    // --- Pending DTCs (Mode 07) ---
    private val _pendingDtcs = MutableStateFlow<List<String>>(emptyList())
    val pendingDtcs: StateFlow<List<String>> = _pendingDtcs.asStateFlow()

    suspend fun readPendingDtcs(): List<String> {
        return try {
            val response = obdSession.sendRawCommand("07")
            val decoded = com.elysium369.meet.core.obd.DtcDecoder.decodePendingResponse(response)
            _pendingDtcs.value = decoded
            decoded
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Permanent DTCs (Mode 0A) ---
    private val _permanentDtcs = MutableStateFlow<List<String>>(emptyList())
    val permanentDtcs: StateFlow<List<String>> = _permanentDtcs.asStateFlow()

    suspend fun readPermanentDtcs(): List<String> {
        return try {
            val response = obdSession.sendRawCommand("0A")
            val decoded = com.elysium369.meet.core.obd.DtcDecoder.decodePermanentResponse(response)
            _permanentDtcs.value = decoded
            decoded
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Readiness Monitors (Mode 01 PID 01) ---
    private val _readinessMonitors = MutableStateFlow<ReadinessResult?>(null)
    val readinessMonitors: StateFlow<ReadinessResult?> = _readinessMonitors.asStateFlow()

    suspend fun readReadinessMonitors(): ReadinessResult? {
        return try {
            val response = obdSession.sendRawCommand("0101")
            val result = parseReadinessResponse(response)
            _readinessMonitors.value = result
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun parseReadinessResponse(raw: String): ReadinessResult {
        val clean = raw.replace("\\s+".toRegex(), "").replace("4101", "")
        val milOn = if (clean.length >= 2) {
            val a = clean.substring(0, 2).toIntOrNull(16) ?: 0
            (a and 0x80) != 0
        } else false
        val dtcCount = if (clean.length >= 2) {
            val a = clean.substring(0, 2).toIntOrNull(16) ?: 0
            a and 0x7F
        } else 0
        val b = if (clean.length >= 4) clean.substring(2, 4).toIntOrNull(16) ?: 0 else 0
        val c = if (clean.length >= 6) clean.substring(4, 6).toIntOrNull(16) ?: 0 else 0
        val d = if (clean.length >= 8) clean.substring(6, 8).toIntOrNull(16) ?: 0 else 0
        val monitors = mutableListOf<MonitorStatus>()
        monitors.add(MonitorStatus("Misfire", (b and 0x01) != 0, (b and 0x10) == 0))
        monitors.add(MonitorStatus("Fuel System", (b and 0x02) != 0, (b and 0x20) == 0))
        monitors.add(MonitorStatus("Components", (b and 0x04) != 0, (b and 0x40) == 0))
        monitors.add(MonitorStatus("Catalyst", (c and 0x01) != 0, (d and 0x01) == 0))
        monitors.add(MonitorStatus("Heated Catalyst", (c and 0x02) != 0, (d and 0x02) == 0))
        monitors.add(MonitorStatus("EVAP System", (c and 0x04) != 0, (d and 0x04) == 0))
        monitors.add(MonitorStatus("Secondary Air", (c and 0x08) != 0, (d and 0x08) == 0))
        monitors.add(MonitorStatus("A/C Refrig.", (c and 0x10) != 0, (d and 0x10) == 0))
        monitors.add(MonitorStatus("O2 Sensor", (c and 0x20) != 0, (d and 0x20) == 0))
        monitors.add(MonitorStatus("O2 Heater", (c and 0x40) != 0, (d and 0x40) == 0))
        monitors.add(MonitorStatus("EGR System", (c and 0x80) != 0, (d and 0x80) == 0))
        return ReadinessResult(milOn, dtcCount, monitors.filter { it.available })
    }

    // --- Data Logging ---
    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    private val _dataLog = MutableStateFlow<List<DataLogEntry>>(emptyList())
    val dataLog: StateFlow<List<DataLogEntry>> = _dataLog.asStateFlow()

    private var loggingJob: kotlinx.coroutines.Job? = null

    fun startDataLogging() {
        _isLogging.value = true
        _dataLog.value = emptyList()
        loggingJob?.cancel()
        loggingJob = viewModelScope.launch {
            while (_isLogging.value) {
                val currentData = _liveData.value
                if (currentData.isNotEmpty()) {
                    val entry = DataLogEntry(
                        timestamp = System.currentTimeMillis(),
                        sensorData = currentData.toMap()
                    )
                    _dataLog.value = _dataLog.value + entry
                }
                kotlinx.coroutines.delay(500L) // Capture every 500ms
            }
        }
    }

    fun stopDataLogging() {
        _isLogging.value = false
        loggingJob?.cancel()
        loggingJob = null
    }

    // --- CSV Export ---
    fun exportDataLogCsv(): String {
        val log = _dataLog.value
        if (log.isEmpty()) return ""
        val allKeys = log.flatMap { it.sensorData.keys }.distinct().sorted()
        val sb = StringBuilder()
        sb.appendLine("Timestamp," + allKeys.joinToString(","))
        log.forEach { entry ->
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(entry.timestamp))
            val values = allKeys.map { key -> entry.sensorData[key]?.let { "%.2f".format(it) } ?: "" }
            sb.appendLine("$timestamp," + values.joinToString(","))
        }
        return sb.toString()
    }

    fun saveCsvToFile(): String? {
        val csv = exportDataLogCsv()
        if (csv.isEmpty()) return null
        return try {
            val vehicleName = _selectedVehicle.value?.let { "${it.make}_${it.model}" } ?: "session"
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val fileName = "MEET_${vehicleName}_$timestamp.csv"
            val dir = java.io.File(context.getExternalFilesDir(null), "exports")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.writeText(csv)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

// Data classes for professional features
data class ReadinessResult(
    val milOn: Boolean,
    val dtcCount: Int,
    val monitors: List<MonitorStatus>
)

data class MonitorStatus(
    val name: String,
    val available: Boolean,
    val complete: Boolean
)

data class DataLogEntry(
    val timestamp: Long,
    val sensorData: Map<String, Float>
)

