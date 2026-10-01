package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.data.local.dao.MaintenanceLogDao
import com.elysium369.meet.data.local.dao.RepairHistoryDao
import com.elysium369.meet.data.local.dao.VehicleDao
import com.elysium369.meet.data.local.dao.DtcDefinitionDao
import com.elysium369.meet.domain.diagnostics.DiagnosticFindingRepository
import com.elysium369.meet.domain.diagnostics.toSummary
import com.elysium369.meet.data.local.entities.MaintenanceLogEntity
import com.elysium369.meet.data.local.entities.RepairHistoryEntity
import com.elysium369.meet.data.local.entities.VehicleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
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
    private val diagnosticFindingRepository: DiagnosticFindingRepository,
    private val dtcDefinitionDao: DtcDefinitionDao,
    private val localExpertSystem: com.elysium369.meet.core.obd.LocalExpertSystem,
    private val principalKernel: com.elysium369.meet.identity.ActivePrincipalKernel,
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

    private var vehicleLoadJob: Job? = null
    private fun clearVehicleData() {
        _vehicle.value=null;_maintenanceLogs.value=emptyList();_repairHistory.value=emptyList()
        _totalMaintenanceCost.value=0.0;_totalRepairCost.value=0.0
        _activeDtcs.value=emptyList();_dtcDefinitions.value=emptyMap();_expertProcedures.value=emptyList()
        _recallsState.value=NhtsaRecallsState.Idle
    }
    fun loadVehicleData(vehicleId: String) {
        currentVehicleId=vehicleId
        vehicleLoadJob?.cancel()
        clearVehicleData()
        vehicleLoadJob=viewModelScope.launch {
            principalKernel.activePrincipal.collectLatest { principal ->
                clearVehicleData()
                val owner=principal.id
                vehicleDao.getVehicleByIdForUser(owner,vehicleId) ?: return@collectLatest
                if(principalKernel.current().id!=owner) return@collectLatest
                coroutineScope {
                    launch {
                        val veh = vehicleDao.getVehicleByIdForUser(owner, vehicleId)
                        if(principalKernel.current().id!=owner) return@launch
                        _vehicle.value = veh
                        if (veh != null) {
                            fetchNhtsaRecalls(veh.make, veh.model, veh.year)
                        }
                    }
                    launch {
                        maintenanceLogDao.getLogsForVehicle(vehicleId).collect { logs ->
                            if(principalKernel.current().id!=owner) return@collect
                            _maintenanceLogs.value = logs
                            _totalMaintenanceCost.value = logs.sumOf { it.cost.toDouble() }
                        }
                    }
                    launch {
                        repairHistoryDao.getRepairsForVehicle(vehicleId).collect { repairs ->
                            if(principalKernel.current().id!=owner) return@collect
                            _repairHistory.value = repairs
                            _totalRepairCost.value = repairs.sumOf { it.totalCost.toDouble() }
                        }
                    }
                    launch {
                        val veh = vehicleDao.getVehicleByIdForUser(owner, vehicleId)
                        val make = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(veh?.make)
                        diagnosticFindingRepository.observeOpenFindings(vehicleId).collect { findings ->
                            if(principalKernel.current().id!=owner) return@collect
                            val summaries = findings.map { it.toSummary() }
                            val codes = summaries.map { it.code }
                            _activeDtcs.value = codes

                            val definitionsMap = mutableMapOf<String, com.elysium369.meet.data.local.entities.DtcDefinitionEntity>()
                            summaries.forEach { finding ->
                                val def = dtcDefinitionDao.getDefinitionForCode(finding.code, make)
                                if (def != null) {
                                    definitionsMap[finding.code] = def
                                } else {
                                    definitionsMap[finding.code] = com.elysium369.meet.data.local.entities.DtcDefinitionEntity(
                                        code = finding.code,
                                        descriptionEs = finding.description,
                                        descriptionEn = "Definition pending vehicle applicability validation",
                                        system = com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(finding.code, isSpanish = true),
                                        severity = finding.severity,
                                        possibleCauses = "Verifique arnés de cableado, conectores y funcionamiento mecánico del componente.",
                                        urgency = com.elysium369.meet.ui.components.DtcUtils.getDynamicUrgency(finding.code)
                                    )
                                }
                            }
                            if(principalKernel.current().id!=owner) return@collect
                            _dtcDefinitions.value = definitionsMap

                            _expertProcedures.value = localExpertSystem.analyzeLiveTelemetry(
                                liveData = emptyMap(),
                                activeDtcs = codes,
                                dtcDefinitions = definitionsMap
                            )
                        }
                    }
                }
            }
        }
    }

    fun fetchNhtsaRecalls(make: String, model: String, year: Int) {
        val owner=principalKernel.current().id
        val vehicleId=_vehicle.value?.takeIf { it.userId==owner }?.id ?: return
        _recallsState.value = NhtsaRecallsState.Loading
        viewModelScope.launch {
            try {
                val list = ElysiumCloudServices.fetchNhtsaRecalls(make, model, year)
                if(principalKernel.current().id==owner && _vehicle.value?.id==vehicleId) _recallsState.value = NhtsaRecallsState.Success(list)
            } catch (e: Exception) {
                if(principalKernel.current().id==owner && _vehicle.value?.id==vehicleId) _recallsState.value = NhtsaRecallsState.Error("No se pudieron consultar recalls")
            }
        }
    }

    fun addMaintenanceLog(log: MaintenanceLogEntity) {
        val owner = principalKernel.current().id
        viewModelScope.launch { 
            if(vehicleDao.getVehicleByIdForUser(owner,log.vehicleId)==null) return@launch
            if (principalKernel.current().id != owner) return@launch
            maintenanceLogDao.insertLog(log) 
        }
    }

    fun copyImageAndSaveMaintenance(context: android.content.Context, uri: android.net.Uri?, logBuilder: (String?) -> MaintenanceLogEntity) {
        val owner = principalKernel.current().id
        viewModelScope.launch {
            val localPath = if (uri != null) {
                com.elysium369.meet.core.utils.FileUtils.copyUriToInternalStorage(context, uri)
            } else null
            val log = logBuilder(localPath)
            if(vehicleDao.getVehicleByIdForUser(owner,log.vehicleId)==null) return@launch
            if (principalKernel.current().id != owner) return@launch
            maintenanceLogDao.insertLog(log)
        }
    }

    fun addRepairHistory(repair: RepairHistoryEntity) {
        val owner = principalKernel.current().id
        viewModelScope.launch { 
            if(vehicleDao.getVehicleByIdForUser(owner,repair.vehicleId)==null) return@launch
            if (principalKernel.current().id != owner) return@launch
            repairHistoryDao.insertRepair(repair) 
        }
    }

    fun copyImageAndSaveRepair(context: android.content.Context, uri: android.net.Uri?, repairBuilder: (String?) -> RepairHistoryEntity) {
        val owner = principalKernel.current().id
        viewModelScope.launch {
            val localPath = if (uri != null) {
                com.elysium369.meet.core.utils.FileUtils.copyUriToInternalStorage(context, uri)
            } else null
            val repair = repairBuilder(localPath)
            if(vehicleDao.getVehicleByIdForUser(owner,repair.vehicleId)==null) return@launch
            if (principalKernel.current().id != owner) return@launch
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
        val currentVehicle = _vehicle.value?.takeIf { it.userId==principalKernel.current().id } ?: return
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
        val currentVehicle = _vehicle.value?.takeIf { it.userId==principalKernel.current().id } ?: return null
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
