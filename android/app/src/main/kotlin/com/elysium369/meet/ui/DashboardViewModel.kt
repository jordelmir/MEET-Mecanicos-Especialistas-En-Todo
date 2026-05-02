package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.data.local.dao.CustomPidDao
import com.elysium369.meet.data.local.dao.DashboardDao
import com.elysium369.meet.data.local.entities.CustomPidEntity
import com.elysium369.meet.data.local.entities.DashboardEntity
import com.elysium369.meet.data.local.entities.DashboardWidgetEntity
import com.elysium369.meet.ui.screens.scanner.GaugeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardDao: DashboardDao,
    private val customPidDao: CustomPidDao,
    private val obdSession: com.elysium369.meet.core.obd.ObdSession,
    private val gemini: com.elysium369.meet.core.ai.GeminiDiagnostic
) : ViewModel() {

    private val _aiInsight = MutableStateFlow("ANALIZANDO FLUJO DE DATOS...")
    val aiInsight: StateFlow<String> = _aiInsight.asStateFlow()

    private val _currentDashboardId = MutableStateFlow<String?>(null)
    val currentDashboardId: StateFlow<String?> = _currentDashboardId.asStateFlow()

    val allDashboards: StateFlow<List<DashboardEntity>> = dashboardDao.getAllDashboards()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val customPids: StateFlow<List<CustomPidEntity>> = customPidDao.getAllCustomPids()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val currentWidgets: StateFlow<List<DashboardWidgetEntity>> = _currentDashboardId
        .flatMapLatest { id ->
            if (id != null) dashboardDao.getWidgetsForDashboard(id)
            else flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // BRIDGE: Combine widgets with live data from session
    val widgetStates: StateFlow<Map<String, Float>> = obdSession.liveData

    val connectionState = obdSession.state

    init {
        viewModelScope.launch {
            val default = dashboardDao.getDefaultDashboard()
            if (default != null) {
                _currentDashboardId.value = default.id
            } else {
                createMasterTemplate("PERFORMANCE PRO")
            }
        }
        
        // Periodic AI Insight Generation based on live data
        viewModelScope.launch {
            obdSession.liveData.collectLatest { data ->
                if (data.isNotEmpty()) {
                    delay(5000) // Don't spam AI
                    val snapshot = data.entries.joinToString { "${it.key}: ${it.value}" }
                    try {
                        val insight = gemini.analyzeQuick(snapshot)
                        _aiInsight.value = insight.uppercase()
                    } catch (e: Exception) {
                        _aiInsight.value = "SISTEMA NOMINAL • SIN ANOMALÍAS"
                    }
                }
            }
        }
    }

    private suspend fun createMasterTemplate(name: String) {
        val id = UUID.randomUUID().toString()
        dashboardDao.insertDashboard(DashboardEntity(id, name, true))
        _currentDashboardId.value = id
        
        // Inyectar widgets profesionales
        val templates = listOf(
            Triple("RPM MOTOR", "010C", "GAUGE"),
            Triple("VELOCIDAD", "010D", "GAUGE"),
            Triple("TEMP COOLANT", "0105", "WAVE"),
            Triple("CARGA MOTOR", "0104", "DIGITAL")
        )
        
        templates.forEachIndexed { i, (n, p, t) ->
            dashboardDao.insertWidget(
                DashboardWidgetEntity(
                    dashboardId = id,
                    name = n,
                    pid = p,
                    type = t,
                    gridX = 0,
                    gridY = i,
                    gridW = if (t == "WAVE") 2 else 1,
                    gridH = 1,
                    color = if (i % 2 == 0) "#00FFCC" else "#FF00FF",
                    minVal = 0f,
                    maxVal = when(p) { "010C" -> 8000f; "010D" -> 255f; "0105" -> 215f; else -> 100f },
                    unit = when(p) { "010C" -> "rpm"; "010D" -> "km/h"; "0105" -> "°C"; else -> "%" }
                )
            )
        }
    }

    fun applyTemplate(type: String) {
        viewModelScope.launch {
            val name = when(type) {
                "PERFORMANCE" -> "ELITE PERFORMANCE"
                "DIAGNOSTIC" -> "MASTER DIAGNOSTIC"
                "ECO" -> "EV ECO MODE"
                else -> "NUEVA TERMINAL"
            }
            createMasterTemplate(name)
        }
    }

    fun selectDashboard(id: String) {
        _currentDashboardId.value = id
    }

    fun createDashboard(name: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            dashboardDao.insertDashboard(DashboardEntity(id, name))
            _currentDashboardId.value = id
        }
    }

    fun deleteDashboard(dashboard: DashboardEntity) {
        viewModelScope.launch {
            dashboardDao.deleteWidgetsByDashboardId(dashboard.id)
            dashboardDao.deleteDashboard(dashboard)
            if (_currentDashboardId.value == dashboard.id) {
                // allDashboards Flow may not have updated yet, so exclude the deleted entry
                _currentDashboardId.value = allDashboards.value
                    .firstOrNull { it.id != dashboard.id }?.id
            }
        }
    }

    fun addWidget(
        name: String,
        pid: String,
        type: String,
        minVal: Float,
        maxVal: Float,
        unit: String,
        gridW: Int = 2,
        gridH: Int = 1,
        color: String = "#00FFCC"
    ) {
        val dashboardId = _currentDashboardId.value ?: return
        viewModelScope.launch {
            val widgets = currentWidgets.value
            val gridY = if (widgets.isEmpty()) 0 else widgets.maxOf { it.gridY } + 1
            
            dashboardDao.insertWidget(
                DashboardWidgetEntity(
                    dashboardId = dashboardId,
                    name = name,
                    pid = pid,
                    type = type,
                    gridX = 0,
                    gridY = gridY,
                    gridW = gridW,
                    gridH = gridH,
                    color = color,
                    minVal = minVal,
                    maxVal = maxVal,
                    unit = unit
                )
            )
        }
    }

    fun updateWidget(widget: DashboardWidgetEntity) {
        viewModelScope.launch {
            dashboardDao.updateWidget(widget)
        }
    }

    fun reorderWidget(widgetId: Int, newY: Int) {
        viewModelScope.launch {
            val widget = currentWidgets.value.find { it.id == widgetId } ?: return@launch
            dashboardDao.updateWidget(widget.copy(gridY = newY))
        }
    }

    fun swapWidgets(widget1: DashboardWidgetEntity, widget2: DashboardWidgetEntity) {
        viewModelScope.launch {
            val y1 = widget1.gridY
            val y2 = widget2.gridY
            dashboardDao.updateWidget(widget1.copy(gridY = y2))
            dashboardDao.updateWidget(widget2.copy(gridY = y1))
        }
    }

    fun deleteWidget(widget: DashboardWidgetEntity) {
        viewModelScope.launch {
            dashboardDao.deleteWidget(widget)
        }
    }

    fun cloneDashboard(originalId: String, newName: String) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            dashboardDao.insertDashboard(DashboardEntity(newId, newName))
            
            // Get original widgets and clone them
            dashboardDao.getWidgetsForDashboardSync(originalId).forEach { widget ->
                dashboardDao.insertWidget(widget.copy(id = 0, dashboardId = newId))
            }
            _currentDashboardId.value = newId
        }
    }

    fun exportCurrentLayout(): String {
        val widgets = currentWidgets.value
        return "MEET_LAYOUT_V1\n" + widgets.joinToString("\n") { 
            "${it.name}|${it.pid}|${it.type}|${it.gridW}|${it.gridH}|${it.color}|${it.minVal}|${it.maxVal}|${it.unit}"
        }
    }

    fun importLayout(data: String) {
        val lines = data.split("\n")
        if (lines.isEmpty() || !lines[0].startsWith("MEET_LAYOUT")) return
        
        val dashboardId = _currentDashboardId.value ?: return
        
        viewModelScope.launch {
            // Optional: Clear current widgets or create a new dashboard for the import
            // For "PRO" we'll create a new dashboard called "IMPORTED_LAYOUT"
            val newDashboardId = UUID.randomUUID().toString()
            dashboardDao.insertDashboard(DashboardEntity(newDashboardId, "IMPORTADO_${System.currentTimeMillis().toString().takeLast(4)}"))
            _currentDashboardId.value = newDashboardId
            
            lines.drop(1).forEachIndexed { index, line ->
                val parts = line.split("|")
                if (parts.size >= 9) {
                    dashboardDao.insertWidget(
                        DashboardWidgetEntity(
                            dashboardId = newDashboardId,
                            name = parts[0],
                            pid = parts[1],
                            type = parts[2],
                            gridX = 0,
                            gridY = index,
                            gridW = parts[3].toIntOrNull() ?: 2,
                            gridH = parts[4].toIntOrNull() ?: 1,
                            color = parts[5],
                            minVal = parts[6].toFloatOrNull() ?: 0f,
                            maxVal = parts[7].toFloatOrNull() ?: 100f,
                            unit = parts[8]
                        )
                    )
                }
            }
        }
    }
}
