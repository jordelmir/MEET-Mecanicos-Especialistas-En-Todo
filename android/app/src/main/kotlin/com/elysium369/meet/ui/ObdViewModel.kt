package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.obd.*
import com.elysium369.meet.data.supabase.SubscriptionRepository
import io.github.jan.supabase.gotrue.auth
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.data.supabase.VehicleRepository
import com.elysium369.meet.data.supabase.SessionLogRepository
import com.elysium369.meet.data.supabase.DiagnosticSession
import com.elysium369.meet.data.local.dao.TripDao
import com.elysium369.meet.data.local.dao.MaintenanceAlertDao
import com.elysium369.meet.data.local.dao.CustomPidDao
import com.elysium369.meet.data.local.entities.TripEntity
import com.elysium369.meet.data.local.entities.MaintenanceAlertEntity
import com.elysium369.meet.data.local.entities.CustomPidEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@HiltViewModel
class ObdViewModel @Inject constructor(
    private val obdSession: ObdSession,
    private val vehicleRepository: VehicleRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val sessionLogRepository: SessionLogRepository,
    private val geminiDiagnostic: com.elysium369.meet.core.ai.GeminiDiagnostic,
    private val tripManager: com.elysium369.meet.core.trips.TripManager,
    private val tripDao: TripDao,
    private val maintenanceAlertDao: MaintenanceAlertDao,
    private val customPidDao: CustomPidDao,
    @ApplicationContext private val context: Context,
    private val reportGenerator: com.elysium369.meet.core.export.ReportGenerator,
    private val diagnosticManager: com.elysium369.meet.core.obd.AdvancedDiagnosticManager
) : ViewModel() {

    // --- State Flows ---
    val connectionState: StateFlow<ObdState> = obdSession.state
    val statusMessage: StateFlow<String> = obdSession.statusMessage
    val isAdapterPro: StateFlow<Boolean> = obdSession.isAdapterPro
    
    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()

    private val _activeDtcs = MutableStateFlow<List<String>>(emptyList())
    val activeDtcs: StateFlow<List<String>> = _activeDtcs.asStateFlow()

    private val _pendingDtcs = MutableStateFlow<List<String>>(emptyList())
    val pendingDtcs: StateFlow<List<String>> = _pendingDtcs.asStateFlow()

    private val _permanentDtcs = MutableStateFlow<List<String>>(emptyList())
    val permanentDtcs: StateFlow<List<String>> = _permanentDtcs.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private val _freezeFrameData = MutableStateFlow<Map<String, String>>(emptyMap())
    val freezeFrameData: StateFlow<Map<String, String>> = _freezeFrameData.asStateFlow()

    private val _manufacturer = MutableStateFlow<String>("GENERIC")
    val manufacturer: StateFlow<String> = _manufacturer.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _currentOdometer = MutableStateFlow(0f)
    val currentOdometer: StateFlow<Float> = _currentOdometer.asStateFlow()

    private val _cloudSyncState = MutableStateFlow("")
    val cloudSyncState: StateFlow<String> = _cloudSyncState.asStateFlow()

    private val _qosMetrics = MutableStateFlow(QosMetrics())
    val qosMetrics: StateFlow<QosMetrics> = _qosMetrics.asStateFlow()
    
    // Telemetry History for Graphs
    private val _telemetryHistory = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val telemetryHistory: StateFlow<Map<String, List<Float>>> = _telemetryHistory.asStateFlow()

    val highSpeedMode: StateFlow<Boolean> = obdSession.highSpeedMode
    val pinnedPids: StateFlow<Set<String>> = obdSession.pinnedPids

    val activeTestStatus: StateFlow<ActiveTestStatus> = obdSession.activeTestStatus
    val availableActiveTests: List<ActiveTest> = PidRegistry.ACTIVE_TESTS

    // --- Reactive Data from Room ---
    val trips: StateFlow<List<TripEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { tripDao.getTripsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val maintenanceAlerts: StateFlow<List<MaintenanceAlertEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { maintenanceAlertDao.getAlertsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customPids: StateFlow<List<CustomPidEntity>> = customPidDao.getAllCustomPids()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val vehicles: StateFlow<List<Vehicle>> = vehicleRepository.getVehiclesForUser()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // IMPORTANT: Use Dispatchers.Main (not .immediate) to force async dispatch.
        // This guarantees all MutableStateFlow fields are fully initialized
        // before any collector lambda runs, preventing NullPointerException.

        // Collect live data from session
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            obdSession.liveData.collect { data -> 
                _liveData.value = data
                updateTelemetryHistory(data)
            }
        }

        // Collect VIN and detect manufacturer
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            obdSession.vin.collect { v -> 
                _vin.value = v
                v?.let { detectManufacturer(it) }
                updateHealthScore()
            }
        }
        
        // Auto-refresh diagnostics on connection
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            obdSession.state.collect { state ->
                if (state == ObdState.CONNECTED) {
                    refreshDiagnostics()
                    obdSession.fetchVin()
                    _currentOdometer.value = obdSession.readOdometer()
                }
            }
        }

        // Sync custom PIDs to session
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            customPids.collect { pids ->
                obdSession.setCustomPids(pids)
            }
        }

        // Check subscription
        viewModelScope.launch {
            _isPremium.value = try { subscriptionRepository.isPremium() } catch (_: Exception) { false }
        }

        // Collect QoS from session
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            obdSession.qosMetrics.collect { metrics -> _qosMetrics.value = metrics }
        }

        // Auto-start AI monitoring if enabled
        viewModelScope.launch {
            isAiMonitoring.collect { enabled ->
                if (enabled) startAiMonitoring() else aiMonitorJob?.cancel()
            }
        }

        // --- Reactive Health Score ---
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            combine(
                _activeDtcs,
                _pendingDtcs,
                _anomalousPids,
                _liveData
            ) { active, pending, anomalies, live ->
                calculateHealthScore(active, pending, anomalies, live)
            }.collect { score ->
                _healthScore.value = score
            }
        }
    }

    // --- Actions ---

    /** Connect to an OBD2 adapter by MAC address or IP */
    fun connect(address: String) {
        obdSession.setTargetAddress(address)
        viewModelScope.launch {
            obdSession.connect()
        }
    }

    fun startDiagnosticSession(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        viewModelScope.launch {
            obdSession.connect()
            if (obdSession.state.value == ObdState.CONNECTED) {
                startForegroundService(vehicle.id)
            }
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            saveSessionResults()
            obdSession.disconnect()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
            _selectedVehicle.value = null
            clearState()
        }
    }

    private suspend fun saveSessionResults() {
        val vehicle = _selectedVehicle.value ?: return
        val currentDtcs = _activeDtcs.value
        val snapshot = _liveData.value
        
        val session = DiagnosticSession(
            id = UUID.randomUUID().toString(),
            user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
            vehicle_vin = vehicle.vin,
            vehicle_make = vehicle.make,
            vehicle_model = vehicle.model,
            vehicle_year = vehicle.year,
            dtcs_found = Json.encodeToString(currentDtcs),
            severity = if (currentDtcs.isNotEmpty()) "high" else "low",
            live_data_snapshot = Json.encodeToString(snapshot.mapValues { it.value.toString() })
        )
        
        sessionLogRepository.saveSession(session)
    }

    private fun clearState() {
        _activeDtcs.value = emptyList()
        _pendingDtcs.value = emptyList()
        _permanentDtcs.value = emptyList()
        _readinessMonitors.value = null
        _liveData.value = emptyMap()
    }

    suspend fun refreshDiagnostics() {
        _activeDtcs.value = obdSession.readActiveDtcs()
        _pendingDtcs.value = obdSession.readPendingDtcs()
        _permanentDtcs.value = obdSession.readPermanentDtcs()
        _readinessMonitors.value = obdSession.readReadinessMonitors()
        updateHealthScore()
    }

    suspend fun clearDtcs(): Boolean {
        val success = obdSession.clearDtcs()
        if (success) {
            _activeDtcs.value = emptyList()
            _pendingDtcs.value = emptyList()
            _freezeFrameData.value = emptyMap()
        }
        return success
    }

    /**
     * Executes a "Smart Scan" — A comprehensive health check of the vehicle.
     */
    suspend fun runSmartScan() {
        if (connectionState.value != ObdState.CONNECTED) return
        
        _cloudSyncState.value = "Iniciando Escaneo Inteligente Elite..."
        
        // 1. Scan DTCs
        _cloudSyncState.value = "Buscando códigos de falla (DTCs)..."
        refreshDiagnostics()
        
        // 2. If DTCs found, fetch Freeze Frame for the first one
        if (_activeDtcs.value.isNotEmpty()) {
            _cloudSyncState.value = "Capturando Cuadro Congelado Histórico..."
            val ff = obdSession.readFreezeFrame(_activeDtcs.value.first())
            _freezeFrameData.value = ff
        }
        
        // 3. Check Battery Voltage & Alternator health
        _cloudSyncState.value = "Analizando sistema eléctrico..."
        val voltage = obdSession.readBatteryVoltage()
        val batteryHealth = if (voltage > 12.4f) "Excelente" else if (voltage > 11.8f) "Normal" else "Baja (Cargar)"
        
        // 4. Update status with detailed report
        _cloudSyncState.value = "Escaneo completado. Batería: $batteryHealth (${voltage}V)"
        
        // 5. Auto-save session to cloud
        saveSessionResults()
    }

    private fun detectManufacturer(vin: String) {
        val wmi = if (vin.length >= 3) vin.substring(0, 3) else ""
        
        // Professional VIN Decoding (Simplified for demo, but extensible)
        val mfr = when {
            vin.startsWith("1FM") || vin.startsWith("1FT") -> "FORD"
            vin.startsWith("JTD") || vin.startsWith("JT1") -> "TOYOTA"
            vin.startsWith("1GC") || vin.startsWith("1G1") || vin.startsWith("1G6") -> "GM"
            vin.startsWith("WBA") || vin.startsWith("WBS") -> "BMW"
            vin.startsWith("WVW") || vin.startsWith("WVW") -> "VOLKSWAGEN"
            vin.startsWith("VF3") || vin.startsWith("VF7") -> "PEUGEOT"
            vin.startsWith("ZFA") -> "FIAT"
            vin.startsWith("SAL") -> "LAND_ROVER"
            else -> "GENERIC"
        }
        
        _manufacturer.value = mfr
        // Automatically inject OEM PIDs if they exist in the registry
        obdSession.enableOemPids(mfr)
    }

    suspend fun refreshFreezeFrame(dtc: String) {
        _cloudSyncState.value = "Refrescando Cuadro Congelado..."
        val ff = obdSession.readFreezeFrame(dtc)
        _freezeFrameData.value = ff
        _cloudSyncState.value = "Cuadro Congelado actualizado."
    }

    suspend fun readVin(): String? {
        return obdSession.fetchVin()
    }

    suspend fun setProtocol(protocol: String): Boolean {
        return obdSession.setProtocol(protocol)
    }

    suspend fun scanModules() = obdSession.scanModules()

    suspend fun sendRawCommand(cmd: String): String {
        return obdSession.sendRawCommand(cmd)
    }

    /**
     * Executes a professional-grade diagnostic routine or active test.
     * Performs safety checks (voltage, adapter quality) before sending commands.
     */
    suspend fun runDiagnosticCommand(command: com.elysium369.meet.core.obd.ObdCommandDef): String {
        // 1. Safety Guard
        if (!obdSession.verifySafetyForProAction()) {
            return "SAFETY_ERROR"
        }
        
        // 2. Execution
        val response = obdSession.sendRawCommand(command.command)
        
        // 3. Validation
        val isSuccess = response.contains(command.expectedResponse) || 
                        response.contains("OK") || 
                        response.contains("61") || // ISO 14230-4 response prefix
                        (command.command.startsWith("31") && response.startsWith("71")) // Routine control response
        
        return if (isSuccess) "SUCCESS" else response
    }

    suspend fun consultAi(apiKey: String?, endpointUrl: String?, dtcList: List<String>): String {
        geminiDiagnostic.updateConfig(apiKey, endpointUrl)
        val info = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
        val result = geminiDiagnostic.analyzeDtc(
            dtcList, 
            info, 
            _liveData.value.mapValues { "%.2f".format(it.value) },
            _telemetryHistory.value
        )
        
        // Update the UI with detected anomalies (as generic HealthAnomaly for now)
        _anomalousPids.value = result.anomalousPids.map { 
            com.elysium369.meet.core.ai.HealthAnomaly(it, "Anomalía detectada en diagnóstico profundo")
        }
        
        return result.analysisText
    }

    fun generateFullReport(aiAnalysis: String?) {
        val currentTrip = tripManager.currentTrip ?: return
        val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
        val dtcs = _activeDtcs.value
        val history = _telemetryHistory.value
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tripData = com.elysium369.meet.data.supabase.Trip(
                id = currentTrip.id,
                started_at = currentTrip.startTime,
                max_speed_kmh = 120, // Mocked or fetched from stats
                max_rpm = 4500,
                max_temp_c = 92
            )
            
            val healthScore = _healthScore.value
            val alerts = maintenanceAlerts.value
            
            val file = reportGenerator.generatePdfReport(
                trip = tripData,
                dtcs = dtcs,
                aiAnalysis = aiAnalysis,
                vehicleDetails = vehicleInfo,
                telemetryHistory = history,
                anomalies = _anomalousPids.value,
                healthScore = healthScore,
                maintenanceAlerts = alerts
            )
            
            reportGenerator.shareReport(file)
        }
    }

    fun markMaintenanceDone(alert: MaintenanceAlertEntity) {
        viewModelScope.launch {
            // Fetch real odometer from ECU if available
            val currentOdo = obdSession.readOdometer()
            val nextDue = if (currentOdo > 0) currentOdo + alert.intervalKm else alert.nextDueKm + alert.intervalKm
            
            val updatedAlert = alert.copy(
                lastDoneKm = if (currentOdo > 0) currentOdo.toInt() else alert.nextDueKm,
                nextDueKm = nextDue.toInt()
            )
            maintenanceAlertDao.insertAlert(updatedAlert)
        }
    }

    fun addCustomPid(pid: CustomPidEntity) {
        viewModelScope.launch {
            customPidDao.insertCustomPid(pid)
        }
    }

    fun deleteCustomPid(pid: CustomPidEntity) {
        viewModelScope.launch {
            customPidDao.deleteCustomPid(pid)
        }
    }

    fun setHighSpeedMode(enabled: Boolean) {
        obdSession.setHighSpeedMode(enabled)
    }

    fun pinPid(pid: String) {
        obdSession.pinPid(pid)
    }

    fun unpinPid(pid: String) {
        obdSession.unpinPid(pid)
        // Optionally clear history for unpinned PID
        val current = _telemetryHistory.value.toMutableMap()
        current.remove(pid)
        _telemetryHistory.value = current
    }

    private fun updateTelemetryHistory(newData: Map<String, Float>) {
        val pinned = pinnedPids.value
        if (pinned.isEmpty()) return

        val currentHistory = _telemetryHistory.value
        val newHistory = currentHistory.toMutableMap()
        val maxPoints = 200 

        pinned.forEach { pid ->
            val value = newData[pid] ?: return@forEach
            val list = currentHistory[pid]?.toMutableList() ?: mutableListOf()
            list.add(value)
            if (list.size > maxPoints) {
                list.removeAt(0)
            }
            newHistory[pid] = list
        }
        _telemetryHistory.value = newHistory
    }

    // --- Active Testing (Bidirectional) ---
    fun runActiveTest(test: ActiveTest) {
        obdSession.runActiveTest(test)
    }

    fun stopActiveTest() {
        obdSession.stopActiveTest()
    }

    suspend fun resetOilService(): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.resetOilService(mfr)
    }

    suspend fun registerBattery(capacityAh: Int): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.registerBattery(mfr, capacityAh)
    }

    suspend fun resetEPB(open: Boolean): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.resetEPB(mfr, open)
    }

    suspend fun calibrateSAS(): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.calibrateSAS(mfr)
    }

    suspend fun relearnThrottle(): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.relearnThrottle(mfr)
    }

    suspend fun regenerateDPF(): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.regenerateDPF(mfr)
    }


    fun exportTripToPdf(trip: TripEntity) {
        viewModelScope.launch {
            val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo Desconocido"
            
            // Convert Entity to Domain model for ReportGenerator
            val domainTrip = com.elysium369.meet.data.supabase.Trip(
                id = trip.id,
                user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                vehicle_id = trip.vehicleId,
                session_id = trip.sessionId,
                started_at = trip.startedAt,
                ended_at = trip.endedAt,
                distance_km = trip.distanceKm,
                duration_seconds = trip.durationSeconds,
                avg_speed_kmh = trip.avgSpeedKmh,
                max_speed_kmh = trip.maxSpeedKmh,
                max_rpm = trip.maxRpm,
                avg_rpm = trip.avgRpm,
                max_temp_c = trip.maxTempC,
                fuel_efficiency = trip.fuelEfficiency,
                eco_score = trip.ecoScore,
                gps_track_json = trip.gpsTrackJson
            )

            // In a real scenario, we might want to fetch DTCs for this trip
            // For now, use active DTCs if it's the current session, or empty
            val dtcs = if (trip.endedAt == null) _activeDtcs.value else emptyList()
            
            val file = reportGenerator.generatePdfReport(
                trip = domainTrip,
                dtcs = dtcs,
                aiAnalysis = null, // Could be fetched from a saved analysis
                vehicleDetails = vehicleInfo
            )
            reportGenerator.shareReport(file)
        }
    }

    suspend fun runAdapterCloneTest(): List<com.elysium369.meet.ui.screens.TestResult> {
        val rawResults = obdSession.runAdapterTests()
        return rawResults.map { (name, value) ->
            val color = when {
                value.contains("ERROR", true) || value.contains("?") -> android.graphics.Color.RED
                value.contains("ms") && (value.replace(" ms", "").toIntOrNull() ?: 0) > 300 -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.GREEN
            }
            com.elysium369.meet.ui.screens.TestResult(name, value, color.toComposeColor())
        }
    }

    private fun Int.toComposeColor() = androidx.compose.ui.graphics.Color(this)

    // --- Helpers ---
    private fun startForegroundService(vehicleId: String) {
        val intent = Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java).apply {
            putExtra("vehicle_id", vehicleId)
        }
        try { context.startService(intent) } catch (_: Exception) {}
    }

    fun saveVehicle(make: String, model: String, year: String, vin: String?) {
        viewModelScope.launch {
            val vehicle = Vehicle(
                id = UUID.randomUUID().toString(),
                user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                year = year.toIntOrNull() ?: 2024,
                make = make,
                model = model,
                engine = "N/A",
                vin = vin ?: "N/A",
                plate = "N/A"
            )
            vehicleRepository.insertVehicle(vehicle)
            _selectedVehicle.value = vehicle
        }
    }

    // --- Data Logging ---
    private val _anomalousPids = MutableStateFlow<List<com.elysium369.meet.core.ai.HealthAnomaly>>(emptyList())
    val anomalousPids: StateFlow<List<com.elysium369.meet.core.ai.HealthAnomaly>> = _anomalousPids.asStateFlow()

    private val _isAiMonitoring = MutableStateFlow(false)
    val isAiMonitoring: StateFlow<Boolean> = _isAiMonitoring.asStateFlow()
    private var aiMonitorJob: kotlinx.coroutines.Job? = null

    fun toggleAiMonitoring(enabled: Boolean) {
        _isAiMonitoring.value = enabled
        if (enabled) {
            startAiMonitoring()
        } else {
            aiMonitorJob?.cancel()
        }
    }

    private fun startAiMonitoring() {
        aiMonitorJob?.cancel()
        aiMonitorJob = viewModelScope.launch {
            while (_isAiMonitoring.value) {
                if (connectionState.value == ObdState.CONNECTED && telemetryHistory.value.isNotEmpty()) {
                    val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model}" } ?: "Generic Vehicle"
                    val anomalies = geminiDiagnostic.checkHealth(vehicleInfo, telemetryHistory.value)
                    _anomalousPids.value = anomalies
                    updateHealthScore()
                }
                kotlinx.coroutines.delay(30000) // Check every 30 seconds
            }
        }
    }

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
                val current = _liveData.value
                if (current.isNotEmpty()) {
                    _dataLog.value = _dataLog.value + DataLogEntry(System.currentTimeMillis(), current)
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun stopDataLogging() {
        _isLogging.value = false
        loggingJob?.cancel()
    }

    fun saveCsvToFile() {
        val data = _dataLog.value
        if (data.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = "MEET_Log_${System.currentTimeMillis()}.csv"
                val file = java.io.File(context.getExternalFilesDir(null), fileName)
                java.io.FileWriter(file).use { writer ->
                    // Header
                    val pids = data.first().values.keys.toList()
                    writer.write("Timestamp," + pids.joinToString(",") + "\n")
                    
                    // Data
                    data.forEach { entry ->
                        val row = mutableListOf<String>()
                        row.add(entry.timestamp.toString())
                        pids.forEach { pid ->
                            row.add(entry.values[pid]?.toString() ?: "")
                        }
                        writer.write(row.joinToString(",") + "\n")
                    }
                }
                
                // Share file
                shareFile(file)
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    private fun shareFile(file: java.io.File) {
        val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartir Log de MEET").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private val _healthScore = MutableStateFlow(100)
    val healthScore: StateFlow<Int> = _healthScore.asStateFlow()

    private fun updateHealthScore() {
        _healthScore.value = calculateHealthScore()
    }

    private fun calculateHealthScore(
        active: List<String> = _activeDtcs.value,
        pending: List<String> = _pendingDtcs.value,
        anomalies: List<com.elysium369.meet.core.ai.HealthAnomaly> = _anomalousPids.value,
        live: Map<String, Float> = _liveData.value
    ): Int {
        var score = 100
        
        // Subtract for DTCs (High priority)
        score -= (active.size * 25)
        score -= (pending.size * 10)
        
        // Subtract for AI Anomalies (Medium priority)
        score -= (anomalies.size * 15)
        
        // Critical Sensor Thresholds
        // 1. Engine Coolant Temperature (PID 0105)
        val temp = live["0105"]
        if (temp != null) {
            if (temp > 115f) score -= 30 // Overheating
            else if (temp > 105f) score -= 10 // Warning
        }

        // 2. Control Module Voltage (PID 0142) or Battery Voltage
        val voltage = live["0142"] ?: live["AT RV"]
        if (voltage != null) {
            if (voltage < 11.5f) score -= 20 // Low battery/alternator
            else if (voltage < 12.2f && (live["010C"] ?: 0f) < 100f) score -= 5 // Weak battery at rest
        }
        
        // 3. Fuel Trims (PID 0106, 0107) - Long term trim
        val ltft = live["0107"] ?: live["0109"]
        if (ltft != null && (ltft > 15f || ltft < -15f)) {
            score -= 10 // Fuel system richness/leanness
        }

        // 4. Misfires (Count detected or erratic RPM)
        // (Placeholder for more complex logic if misfire PIDs are available)
        
        return score.coerceIn(5, 100)
    }
}

data class DataLogEntry(
    val timestamp: Long,
    val values: Map<String, Float>
)
