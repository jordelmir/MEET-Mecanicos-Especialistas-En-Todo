package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.data.local.entities.MaintenanceAlertEntity
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit
) {
    val alerts by viewModel.maintenanceAlerts.collectAsState()
    val currentOdometer by viewModel.currentOdometer.collectAsState()
    val liveData by viewModel.liveData.collectAsState()
    val activeDtcs by viewModel.activeDtcEvents.collectAsState()
    val pendingDtcs by viewModel.pendingDtcEvents.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()
    val trips by viewModel.trips.collectAsState()
    
    // Tab state
    var selectedTab by remember { mutableStateOf(0) }
    
    // Dialog state for adding alert
    var showAddAlertModal by remember { mutableStateOf(false) }
    
    // Form fields for new alert
    var alertType by remember { mutableStateOf("") }
    var intervalStr by remember { mutableStateOf("") }
    var nextDueStr by remember { mutableStateOf("") }
    
    // Derived values
    val voltage = liveData["0142"] ?: 12.6f
    val coolantTemp = liveData["0105"] ?: 90f
    val avgEcoScore = if (trips.isNotEmpty()) trips.map { it.ecoScore }.average().toInt() else 85
    val currentOdoLong = currentOdometer.toLong()
    
    val overdueCount = alerts.count { (it.nextDueKm - currentOdoLong) <= 0 }
    val warningCount = alerts.count { (it.nextDueKm - currentOdoLong) in 1..500 }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Mantenimiento",
                subtitle = "Ecosistema de Prevención Pro",
                onBackClick = onBack,
                actions = {
                    if (selectedTab == 0) {
                        EliteIconButton(
                            icon = { Text("+", color = MeetColors.neonGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                            onClick = { showAddAlertModal = true },
                            glowColor = MeetColors.neonGreen
                        )
                    }
                }
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            
            // Premium Cyber Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeetColors.cardBackground)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf("ALERTAS", "ANÁLISIS FODA")
                tabs.forEachIndexed { index, tabName ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MeetColors.neonGreen else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabName,
                            color = if (isSelected) MeetColors.backgroundDeep else MeetColors.textSecondary,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (selectedTab == 0) {
                // TAB 0: Alertas y Salud de Mantenimiento
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        EliteCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = MeetColors.neonGreen.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ESTADO DE SALUD",
                                        color = MeetColors.textSecondary,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (overdueCount > 0) "Acciones Requeridas" else "Todo en Orden",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Odómetro actual: $currentOdometer km",
                                        color = MeetColors.neonGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                MaintenanceGauge(
                                    alerts = alerts,
                                    currentOdometer = currentOdoLong
                                )
                            }
                        }
                    }
                    
                    if (alerts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No hay alertas registradas",
                                        color = MeetColors.textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    EliteOutlinedButton(
                                        text = "CREAR ALERTA",
                                        onClick = { showAddAlertModal = true }
                                    )
                                }
                            }
                        }
                    } else {
                        items(alerts) { alert ->
                            val remaining = alert.nextDueKm - currentOdoLong
                            val (statusColor, statusText) = when {
                                remaining <= 0 -> MeetColors.error to "VENCIDO"
                                remaining <= 500 -> MeetColors.warning to "PRÓXIMO"
                                else -> MeetColors.success to "AL DÍA"
                            }
                            
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = statusColor.copy(alpha = 0.15f),
                                borderColor = statusColor.copy(alpha = 0.25f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = alert.type.uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                        
                                        Surface(
                                            color = statusColor.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(12.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("INTERVALO", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                            Text("${alert.intervalKm} km", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column {
                                            Text("ÚLTIMO", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                            Text("${alert.lastDoneKm} km", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column {
                                            Text("PRÓXIMO", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                            Text("${alert.nextDueKm} km", color = if (statusColor == MeetColors.error) MeetColors.error else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    if (statusColor != MeetColors.success) {
                                        Spacer(Modifier.height(16.dp))
                                        EliteButton(
                                            text = "Marcar como Completado",
                                            onClick = { viewModel.markMaintenanceDone(alert) },
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(Modifier.height(20.dp))
                    }
                }
            } else {
                // TAB 1: Análisis FODA Vehicular
                val strengthsList = remember(voltage, activeDtcs, healthScore) {
                    val list = mutableListOf<String>()
                    if (voltage >= 12.3f) {
                        list.add("Batería saludable (${((voltage * 10).toInt() / 10f)}V)")
                    }
                    if (activeDtcs.isEmpty()) {
                        list.add("Sin fallas de motor activas")
                    }
                    if (healthScore >= 80) {
                        list.add("Salud general óptima (${healthScore}%)")
                    }
                    if (list.isEmpty()) {
                        list.add("Sistemas operativos base OK")
                    }
                    list
                }

                val opportunitiesList = remember(avgEcoScore, alerts) {
                    val list = mutableListOf<String>()
                    list.add("Eco-driving: Mejorar aceleración (+8% km)")
                    list.add("Programar mantenimientos preventivos")
                    list.add("Monitorear voltajes en arranque frío")
                    list
                }

                val weaknessesList = remember(overdueCount, warningCount, avgEcoScore, pendingDtcs) {
                    val list = mutableListOf<String>()
                    if (overdueCount > 0) {
                        list.add("$overdueCount alerta${if (overdueCount > 1) "s" else ""} de mantenimiento vencida${if (overdueCount > 1) "s" else ""}")
                    }
                    if (warningCount > 0) {
                        list.add("$warningCount alerta${if (warningCount > 1) "s" else ""} próxima a vencer")
                    }
                    if (pendingDtcs.isNotEmpty()) {
                        list.add("${pendingDtcs.size} falla${if (pendingDtcs.size > 1) "s" else ""} pendiente${if (pendingDtcs.size > 1) "s" else ""}")
                    }
                    if (avgEcoScore < 75) {
                        list.add("Eco Score bajo (${avgEcoScore}/100)")
                    }
                    if (list.isEmpty()) {
                        list.add("Monitoreo continuo activo")
                    }
                    list
                }

                val threatsList = remember(voltage, activeDtcs, coolantTemp) {
                    val list = mutableListOf<String>()
                    if (activeDtcs.isNotEmpty()) {
                        list.add("Check Engine Encendido (${activeDtcs.size} DTCs)")
                    }
                    if (voltage < 11.8f) {
                        list.add("Batería Crítica (${((voltage * 10).toInt() / 10f)}V)")
                    }
                    if (coolantTemp > 105f) {
                        list.add("Sobrecalentamiento (${coolantTemp.toInt()}°C)")
                    }
                    if (list.isEmpty()) {
                        list.add("Sin amenazas críticas detectadas")
                    }
                    list
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        FodaQuadrant(
                            title = "FORTALEZAS",
                            subtitle = "Sistemas y Estados Óptimos",
                            icon = "✓",
                            color = MeetColors.success,
                            items = strengthsList
                        )
                    }
                    item {
                        FodaQuadrant(
                            title = "OPORTUNIDADES",
                            subtitle = "Consejos y Acciones Preventivas",
                            icon = "★",
                            color = MeetColors.cyberCyan,
                            items = opportunitiesList
                        )
                    }
                    item {
                        FodaQuadrant(
                            title = "DEBILIDADES",
                            subtitle = "Alertas y Fallas no Críticas",
                            icon = "⚠",
                            color = MeetColors.warning,
                            items = weaknessesList
                        )
                    }
                    item {
                        FodaQuadrant(
                            title = "AMENAZAS",
                            subtitle = "Alertas y Riesgos Críticos",
                            icon = "⚡",
                            color = MeetColors.error,
                            items = threatsList
                        )
                    }
                    item {
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    // Modal para agregar alertas
    if (showAddAlertModal) {
        Dialog(onDismissRequest = { showAddAlertModal = false }) {
            Surface(
                color = MeetColors.backgroundDeep.copy(alpha = 0.98f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(MeetColors.neonGreen.copy(alpha = 0.5f), MeetColors.neonGreen.copy(alpha = 0.15f))
                        ),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AGREGAR ALERTA",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = alertType,
                        onValueChange = { alertType = it },
                        label = { Text("Tipo de Mantenimiento", color = MeetColors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedLabelColor = MeetColors.neonGreen,
                            unfocusedLabelColor = MeetColors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = intervalStr,
                        onValueChange = { intervalStr = it },
                        label = { Text("Intervalo (KM)", color = MeetColors.textSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedLabelColor = MeetColors.neonGreen,
                            unfocusedLabelColor = MeetColors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = nextDueStr,
                        onValueChange = { nextDueStr = it },
                        label = { Text("Próximo cambio (KM debido)", color = MeetColors.textSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedLabelColor = MeetColors.neonGreen,
                            unfocusedLabelColor = MeetColors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddAlertModal = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MeetColors.textSecondary.copy(alpha = 0.5f))
                        ) {
                            Text("CANCELAR", color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val interval = intervalStr.toLongOrNull() ?: 5000L
                                val nextDue = nextDueStr.toLongOrNull() ?: (currentOdometer.toLong() + interval)
                                if (alertType.isNotBlank()) {
                                    viewModel.addMaintenanceAlert(alertType, interval, nextDue)
                                    alertType = ""
                                    intervalStr = ""
                                    nextDueStr = ""
                                    showAddAlertModal = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                        ) {
                            Text("GUARDAR", color = MeetColors.backgroundDeep, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MaintenanceGauge(
    alerts: List<MaintenanceAlertEntity>,
    currentOdometer: Long,
    modifier: Modifier = Modifier
) {
    val total = alerts.size
    val activeOk = alerts.count { (it.nextDueKm - currentOdometer) > 0 }
    val percentage = if (total > 0) activeOk.toFloat() / total.toFloat() else 1.0f

    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            // Draw background circle track
            drawCircle(
                color = MeetColors.cardBackground,
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth
                )
            )
            
            // Draw progress arc
            drawArc(
                color = if (percentage > 0.8f) MeetColors.success else if (percentage > 0.5f) MeetColors.warning else MeetColors.error,
                startAngle = -90f,
                sweepAngle = 360f * percentage,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth
                )
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(percentage * 100).toInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
            Text(
                text = "AL DÍA",
                color = MeetColors.textSecondary,
                fontWeight = FontWeight.Black,
                fontSize = 8.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun FodaQuadrant(
    title: String,
    subtitle: String,
    icon: String,
    color: Color,
    items: List<String>,
    modifier: Modifier = Modifier
) {
    EliteCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = color.copy(alpha = 0.2f),
        borderColor = color.copy(alpha = 0.3f),
        backgroundColor = MeetColors.cardBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(32.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(icon, color = color, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
                Column {
                    Text(title, color = color, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                    Text(subtitle, color = MeetColors.textSecondary, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text(
                    "Sin registros en esta categoría",
                    color = MeetColors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("•", color = color, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(item, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
