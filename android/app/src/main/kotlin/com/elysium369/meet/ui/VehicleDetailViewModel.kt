package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.data.local.dao.MaintenanceLogDao
import com.elysium369.meet.data.local.dao.RepairHistoryDao
import com.elysium369.meet.data.local.dao.VehicleDao
import com.elysium369.meet.data.local.dao.DtcDao
import com.elysium369.meet.data.local.dao.DtcDefinitionDao
import com.elysium369.meet.data.local.entities.MaintenanceLogEntity
import com.elysium369.meet.data.local.entities.RepairHistoryEntity
import com.elysium369.meet.data.local.entities.VehicleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

import com.elysium369.meet.core.sync.RecallItem
import com.elysium369.meet.core.sync.ElysiumCloudServices

sealed interface NhtsaRecallsState {
    object Idle : NhtsaRecallsState
    object Loading : NhtsaRecallsState
    data class Success(val recalls: List<RecallItem>) : NhtsaRecallsState
    data class Error(val message: String) : NhtsaRecallsState
}

@HiltViewModel
class VehicleDetailViewModel @Inject constructor(
    private val maintenanceLogDao: MaintenanceLogDao,
    private val repairHistoryDao: RepairHistoryDao,
    private val vehicleDao: VehicleDao,
    private val dtcDao: DtcDao,
    private val dtcDefinitionDao: DtcDefinitionDao,
    private val localExpertSystem: com.elysium369.meet.core.obd.LocalExpertSystem
) : ViewModel() {

    private val _maintenanceLogs = MutableStateFlow<List<MaintenanceLogEntity>>(emptyList())
    val maintenanceLogs: StateFlow<List<MaintenanceLogEntity>> = _maintenanceLogs.asStateFlow()

    private val _repairHistory = MutableStateFlow<List<RepairHistoryEntity>>(emptyList())
    val repairHistory: StateFlow<List<RepairHistoryEntity>> = _repairHistory.asStateFlow()

    private val _totalMaintenanceCost = MutableStateFlow(0.0)
    val totalMaintenanceCost: StateFlow<Double> = _totalMaintenanceCost.asStateFlow()

    private val _totalRepairCost = MutableStateFlow(0.0)
    val totalRepairCost: StateFlow<Double> = _totalRepairCost.asStateFlow()

    private val _vehicle = MutableStateFlow<VehicleEntity?>(null)
    val vehicle: StateFlow<VehicleEntity?> = _vehicle.asStateFlow()

    private val _expertProcedures = MutableStateFlow<List<com.elysium369.meet.core.obd.ExpertDiagnosticProcedure>>(emptyList())
    val expertProcedures: StateFlow<List<com.elysium369.meet.core.obd.ExpertDiagnosticProcedure>> = _expertProcedures.asStateFlow()

    private val _activeDtcs = MutableStateFlow<List<String>>(emptyList())
    val activeDtcs: StateFlow<List<String>> = _activeDtcs.asStateFlow()

    private val _dtcDefinitions = MutableStateFlow<Map<String, com.elysium369.meet.data.local.entities.DtcDefinitionEntity>>(emptyMap())
    val dtcDefinitions: StateFlow<Map<String, com.elysium369.meet.data.local.entities.DtcDefinitionEntity>> = _dtcDefinitions.asStateFlow()

    private val _recallsState = MutableStateFlow<NhtsaRecallsState>(NhtsaRecallsState.Idle)
    val recallsState: StateFlow<NhtsaRecallsState> = _recallsState.asStateFlow()

    private var currentVehicleId: String? = null

    fun loadVehicleData(vehicleId: String) {
        currentVehicleId = vehicleId
        viewModelScope.launch {
            val veh = vehicleDao.getVehicleById(vehicleId)
            _vehicle.value = veh
            if (veh != null) {
                fetchNhtsaRecalls(veh.make, veh.model, veh.year)
            }
        }
        viewModelScope.launch {
            maintenanceLogDao.getLogsForVehicle(vehicleId).collect { logs ->
                _maintenanceLogs.value = logs
                _totalMaintenanceCost.value = logs.sumOf { it.cost.toDouble() }
            }
        }
        viewModelScope.launch {
            repairHistoryDao.getRepairsForVehicle(vehicleId).collect { repairs ->
                _repairHistory.value = repairs
                _totalRepairCost.value = repairs.sumOf { it.totalCost.toDouble() }
            }
        }
        viewModelScope.launch {
            val veh = vehicleDao.getVehicleById(vehicleId)
            val make = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(veh?.make)
            dtcDao.getUnresolvedDtcsForVehicle(vehicleId).collect { dtcEvents ->
                val codes = dtcEvents.map { it.code }
                _activeDtcs.value = codes

                val definitionsMap = mutableMapOf<String, com.elysium369.meet.data.local.entities.DtcDefinitionEntity>()
                dtcEvents.forEach { event ->
                    val def = dtcDefinitionDao.getDefinitionForCode(event.code, make)
                    if (def != null) {
                        definitionsMap[event.code] = def
                    } else {
                        definitionsMap[event.code] = com.elysium369.meet.data.local.entities.DtcDefinitionEntity(
                            code = event.code,
                            descriptionEs = event.description,
                            descriptionEn = event.description,
                            system = com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(event.code, isSpanish = true),
                            severity = event.severity,
                            possibleCauses = "Verifique arnés de cableado, conectores y funcionamiento mecánico del componente.",
                            urgency = com.elysium369.meet.ui.components.DtcUtils.getDynamicUrgency(event.code)
                        )
                    }
                }
                _dtcDefinitions.value = definitionsMap

                _expertProcedures.value = localExpertSystem.analyzeLiveTelemetry(
                    liveData = emptyMap(),
                    activeDtcs = codes,
                    dtcDefinitions = definitionsMap
                )
            }
        }
    }

    fun fetchNhtsaRecalls(make: String, model: String, year: Int) {
        _recallsState.value = NhtsaRecallsState.Loading
        viewModelScope.launch {
            try {
                val list = ElysiumCloudServices.fetchNhtsaRecalls(make, model, year)
                _recallsState.value = NhtsaRecallsState.Success(list)
            } catch (e: Exception) {
                _recallsState.value = NhtsaRecallsState.Error(e.localizedMessage ?: "Error al obtener recalls de la NHTSA")
            }
        }
    }

    fun addMaintenanceLog(log: MaintenanceLogEntity) {
        viewModelScope.launch { 
            maintenanceLogDao.insertLog(log) 
        }
    }

    fun copyImageAndSaveMaintenance(context: android.content.Context, uri: android.net.Uri?, logBuilder: (String?) -> MaintenanceLogEntity) {
        viewModelScope.launch {
            val localPath = if (uri != null) {
                com.elysium369.meet.core.utils.FileUtils.copyUriToInternalStorage(context, uri)
            } else null
            val log = logBuilder(localPath)
            maintenanceLogDao.insertLog(log)
        }
    }

    fun addRepairHistory(repair: RepairHistoryEntity) {
        viewModelScope.launch { 
            repairHistoryDao.insertRepair(repair) 
        }
    }

    fun copyImageAndSaveRepair(context: android.content.Context, uri: android.net.Uri?, repairBuilder: (String?) -> RepairHistoryEntity) {
        viewModelScope.launch {
            val localPath = if (uri != null) {
                com.elysium369.meet.core.utils.FileUtils.copyUriToInternalStorage(context, uri)
            } else null
            val repair = repairBuilder(localPath)
            repairHistoryDao.insertRepair(repair)
        }
    }

    fun calculateAverageDailyKm(): Float? {
        val vehicle = _vehicle.value ?: return null
        val logs = _maintenanceLogs.value
        val repairs = _repairHistory.value

        var minDate = vehicle.createdAt
        var minOdo = vehicle.odometerKm
        var maxDate = minDate
        var maxOdo = minOdo

        logs.forEach {
            if (it.datePerformed < minDate) { minDate = it.datePerformed }
            if (it.odometerAtService < minOdo) { minOdo = it.odometerAtService }
            if (it.datePerformed > maxDate) { maxDate = it.datePerformed }
            if (it.odometerAtService > maxOdo) { maxOdo = it.odometerAtService }
        }

        repairs.forEach {
            if (it.datePerformed < minDate) { minDate = it.datePerformed }
            if (it.odometerAtRepair < minOdo) { minOdo = it.odometerAtRepair }
            if (it.datePerformed > maxDate) { maxDate = it.datePerformed }
            if (it.odometerAtRepair > maxOdo) { maxOdo = it.odometerAtRepair }
        }

        val daysDiff = (maxDate - minDate) / (1000f * 60 * 60 * 24)
        val kmDiff = maxOdo - minOdo

        if (daysDiff >= 1f && kmDiff > 0) {
            return (kmDiff / daysDiff).toFloat()
        }
        return null
    }

    fun exportHistoryPdf(context: android.content.Context) {
        val currentVehicle = _vehicle.value ?: return
        val generator = com.elysium369.meet.core.export.ReportGenerator(context)
        val file = generator.generateVehicleHistoryReport(
            vehicle = currentVehicle,
            maintenanceLogs = _maintenanceLogs.value,
            repairs = _repairHistory.value,
            includeExpert = false,
            expertProcedures = emptyList()
        )
        generator.shareReport(file)
    }

    suspend fun generateHistoryPdf(
        context: android.content.Context,
        themeName: String = "ELYSIUM_CYAN",
        includeMaint: Boolean = true,
        includeRepairs: Boolean = true,
        includeSummary: Boolean = true,
        includeBranding: Boolean = true,
        includeExpert: Boolean = false
    ): File? {
        val currentVehicle = _vehicle.value ?: return null
        val generator = com.elysium369.meet.core.export.ReportGenerator(context)
        val procedures = if (includeExpert) _expertProcedures.value else emptyList()
        return withContext(Dispatchers.IO) {
            generator.generateVehicleHistoryReport(
                vehicle = currentVehicle,
                maintenanceLogs = _maintenanceLogs.value,
                repairs = _repairHistory.value,
                themeName = themeName,
                includeMaint = includeMaint,
                includeRepairs = includeRepairs,
                includeSummary = includeSummary,
                includeBranding = includeBranding,
                includeExpert = includeExpert,
                expertProcedures = procedures
            )
        }
    }
}
