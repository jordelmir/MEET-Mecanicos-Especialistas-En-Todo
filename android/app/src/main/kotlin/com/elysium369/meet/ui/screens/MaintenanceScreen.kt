package com.elysium369.meet.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.data.local.entities.MaintenanceAlertEntity
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Calendar

// ═══════════════════════════════════════════════════════
// DATA MODELS FOR EXPENSES AND COMPONENT LIFE
// ═══════════════════════════════════════════════════════

data class ExpenseItem(
    val id: String,
    val category: String, // "Combustible", "Servicio", "Repuesto", "Lavado", "Otros"
    val cost: Double,
    val odometer: Long,
    val date: Long,
    val notes: String
)

data class ComponentWearItem(
    val id: String,
    val name: String,
    val icon: String,
    val intervalKm: Long,
    val description: String,
    val reason: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val alerts by viewModel.maintenanceAlerts.collectAsState()
    val currentOdometer by viewModel.currentOdometer.collectAsState()
    val liveData by viewModel.liveData.collectAsState()
    val activeDtcs by viewModel.activeDtcEvents.collectAsState()
    val pendingDtcs by viewModel.pendingDtcEvents.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()
    val trips by viewModel.trips.collectAsState()

    // Preferences for component life and expenses
    val prefs = remember { context.getSharedPreferences("meet_maintenance_prefs", Context.MODE_PRIVATE) }

    // Tab state
    var selectedTab by remember { mutableStateOf(0) }

    // Dialog state for adding alert
    var showAddAlertModal by remember { mutableStateOf(false) }
    var alertType by remember { mutableStateOf("") }
    var intervalStr by remember { mutableStateOf("") }
    var nextDueStr by remember { mutableStateOf("") }

    // Dialog state for adding expense
    var showAddExpenseModal by remember { mutableStateOf(false) }
    var expenseCategory by remember { mutableStateOf("Combustible") }
    var expenseCostStr by remember { mutableStateOf("") }
    var expenseOdometerStr by remember { mutableStateOf("") }
    var expenseNotes by remember { mutableStateOf("") }

    // Expense list state
    var expenses by remember { mutableStateOf(loadExpenses(context)) }

    // Derived values
    val voltage = liveData["0142"] ?: 12.6f
    val coolantTemp = liveData["0105"] ?: 90f
    val avgEcoScore = if (trips.isNotEmpty()) trips.map { it.ecoScore }.average().toInt() else 85
    val currentOdoLong = currentOdometer.toLong()

    val overdueCount = alerts.count { (it.nextDueKm - currentOdoLong) <= 0 }
    val warningCount = alerts.count { (it.nextDueKm - currentOdoLong) in 1..500 }

    // Component definition list
    val componentItems = remember {
        listOf(
            ComponentWearItem("oil", "Aceite de Motor", "🛢️", 7500L, "Filtro y lubricante del bloque motor.", "Mantiene la viscosidad y arrastra partículas metálicas."),
            ComponentWearItem("air_filter", "Filtro de Aire", "💨", 15000L, "Filtro de admisión del motor.", "Evita que polvo y arena entren en la cámara de combustión."),
            ComponentWearItem("brakes", "Pastillas de Freno", "🛑", 30000L, "Material de fricción delantero/trasero.", "Garantiza una distancia de frenado segura y previene daño al disco."),
            ComponentWearItem("brake_fluid", "Líquido de Frenos", "🧪", 40000L, "Fluido hidráulico higroscópico.", "Evita la fatiga de frenado por ebullición del agua absorbida."),
            ComponentWearItem("spark_plugs", "Bujías de Encendido", "⚡", 40000L, "Electrodos de encendido.", "Previene fallos de cilindro (misfires) y exceso de consumo."),
            ComponentWearItem("coolant", "Líquido Refrigerante", "🌡️", 60000L, "Fluido anticongelante y refrigerante.", "Evita sobrecalentamiento y corrosión galvánica interna."),
            ComponentWearItem("steering", "Fluido de Dirección", "🧪", 50000L, "Fluido hidráulico de asistencia.", "Evita desgaste prematuro en bomba hidráulica y cremallera."),
            ComponentWearItem("transmission", "Aceite de Transmisión", "🔄", 60000L, "Valvulina o ATF de caja de cambios.", "Lubricación y refrigeración de engranajes y embragues hidráulicos."),
            ComponentWearItem("timing_belt", "Correa de Distribución", "⚙️", 80000L, "Correa de sincronización de válvulas.", "CRÍTICO: Rotura causa colisión catastrófica de válvulas y pistones.")
        )
    }

    // Trigger predictive calculation
    LaunchedEffect(currentOdometer, liveData) {
        viewModel.predictMaintenance()
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Mantenimiento Preventivo",
                subtitle = "Ecosistema de Prevención Pro",
                onBackClick = onBack,
                actions = {
                    if (selectedTab == 0) {
                        EliteIconButton(
                            icon = { Text("+", color = MeetColors.neonGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                            onClick = { showAddAlertModal = true },
                            glowColor = MeetColors.neonGreen
                        )
                    } else if (selectedTab == 2) {
                        EliteIconButton(
                            icon = { Text("+", color = MeetColors.cyberCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                            onClick = {
                                expenseOdometerStr = currentOdoLong.toString()
                                showAddExpenseModal = true
                            },
                            glowColor = MeetColors.cyberCyan
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

            // Premium Cyber Scrollable Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeetColors.cardBackground)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    "🔔 ALERTAS",
                    "🔧 VIDA PIEZAS",
                    "💵 BITÁCORA",
                    "📊 ANÁLISIS FODA",
                    "🔍 PIEZAS"
                )
                tabs.forEachIndexed { index, tabName ->
                    val isSelected = selectedTab == index
                    val tabColor = when (index) {
                        0 -> MeetColors.neonGreen
                        1 -> MeetColors.cyberCyan
                        2 -> MeetColors.electricBlue
                        3 -> MeetColors.hotMagenta
                        else -> MeetColors.cyberCyan
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) tabColor else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabName,
                            color = if (isSelected) MeetColors.backgroundDeep else MeetColors.textSecondary,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: Alertas Manuales y Estado de Salud
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
                                            fontSize = 18.sp
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Odómetro actual: $currentOdoLong km",
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
                                            fontSize = 14.sp
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
                                                fontSize = 15.sp,
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
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("INTERVALO", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text("${alert.intervalKm} km", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Column {
                                                Text("ÚLTIMO", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text("${alert.lastDoneKm} km", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Column {
                                                Text("PRÓXIMO", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text("${alert.nextDueKm} km", color = if (statusColor == MeetColors.error) MeetColors.error else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (statusColor != MeetColors.success) {
                                            Spacer(Modifier.height(14.dp))
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

                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }

                1 -> {
                    // TAB 1: Vida de Piezas (Estimaciones y Reseteo)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "VIDA ESTIMADA DE COMPONENTES",
                                color = MeetColors.cyberCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(componentItems) { item ->
                            val lastDoneKey = "last_done_${item.id}"
                            var lastDoneKm by remember { mutableStateOf(prefs.getLong(lastDoneKey, 0L)) }
                            
                            val elapsed = (currentOdoLong - lastDoneKm).coerceAtLeast(0L)
                            val remaining = (item.intervalKm - elapsed).coerceAtLeast(0L)
                            val percentage = if (item.intervalKm > 0) (remaining.toFloat() / item.intervalKm.toFloat()).coerceIn(0f, 1f) else 1.0f
                            
                            val wearColor = when {
                                percentage <= 0.25f -> MeetColors.error
                                percentage <= 0.6f -> MeetColors.warning
                                else -> MeetColors.success
                            }

                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = wearColor.copy(alpha = 0.1f),
                                borderColor = wearColor.copy(alpha = 0.2f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AnimatedNeonGlyph(item.icon, contentDescription = null, fontSize = 22.sp)
                                            Column {
                                                Text(item.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                                Text(item.description, color = MeetColors.textSecondary, fontSize = 10.sp)
                                            }
                                        }

                                        Text(
                                            text = "${(percentage * 100).toInt()}%",
                                            color = wearColor,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Cyber Neon Progress Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x22FFFFFF))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(percentage)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            wearColor.copy(alpha = 0.7f),
                                                            wearColor
                                                        )
                                                    )
                                                )
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Último cambio a los $lastDoneKm km",
                                                color = MeetColors.textMuted,
                                                fontSize = 9.sp
                                            )
                                            Text(
                                                text = if (remaining > 0) "Restan ~${remaining} km" else "¡Vencido por ${elapsed - item.intervalKm} km!",
                                                color = if (remaining > 0) Color.White.copy(alpha = 0.7f) else MeetColors.error,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(wearColor.copy(alpha = 0.15f))
                                                .clickable {
                                                    prefs.edit().putLong(lastDoneKey, currentOdoLong).apply()
                                                    lastDoneKm = currentOdoLong
                                                    Toast.makeText(context, "✅ ${item.name} reiniciado", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "REINICIAR",
                                                color = wearColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }

                2 -> {
                    // TAB 2: Bitácora de Gastos
                    val totalSpent = expenses.sumOf { it.cost }
                    val fuelSpent = expenses.filter { it.category == "Combustible" }.sumOf { it.cost }
                    val servicesSpent = expenses.filter { it.category != "Combustible" }.sumOf { it.cost }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Summary Card
                        item {
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = MeetColors.electricBlue.copy(alpha = 0.1f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "RESUMEN DE GASTOS",
                                        color = MeetColors.textSecondary,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "$${String.format("%.2f", totalSpent)}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 24.sp
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("⛽ COMBUSTIBLE", color = MeetColors.cyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Text("$${String.format("%.2f", fuelSpent)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("🔧 SERVICIOS", color = MeetColors.electricBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Text("$${String.format("%.2f", servicesSpent)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "REGISTROS RECIENTES",
                                    color = MeetColors.electricBlue,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MeetColors.electricBlue.copy(alpha = 0.15f))
                                        .clickable {
                                            expenseOdometerStr = currentOdoLong.toString()
                                            showAddExpenseModal = true
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("AÑADIR GASTO", color = MeetColors.electricBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        if (expenses.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No hay gastos registrados en la bitácora.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            items(expenses) { expense ->
                                val categoryIcon = when (expense.category) {
                                    "Combustible" -> "⛽"
                                    "Servicio Aceite" -> "🛢️"
                                    "Repuesto" -> "⚙️"
                                    "Lavado" -> "🧼"
                                    else -> "📋"
                                }

                                val cal = Calendar.getInstance().apply { timeInMillis = expense.date }
                                val dateStr = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"

                                EliteCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    glowColor = MeetColors.electricBlue.copy(alpha = 0.05f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(categoryIcon, fontSize = 24.sp)
                                            Column {
                                                Text(expense.category, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(
                                                    text = "${expense.notes.ifBlank { "Sin detalles" }} • $dateStr",
                                                    color = MeetColors.textSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1
                                                )
                                                Text("Odómetro: ${expense.odometer} km", color = MeetColors.textMuted, fontSize = 9.sp)
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "-$${String.format("%.2f", expense.cost)}",
                                                color = Color(0xFFFF5252),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp
                                            )

                                            IconButton(
                                                onClick = {
                                                    val updatedList = expenses.filter { it.id != expense.id }
                                                    saveExpenses(context, updatedList)
                                                    expenses = updatedList
                                                    Toast.makeText(context, "🗑️ Registro eliminado", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                                AnimatedNeonGlyph(
                                                    glyph = "✕",
                                                    contentDescription = "Eliminar registro",
                                                    tint = MeetColors.error,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }

                3 -> {
                    // TAB 3: Análisis FODA Vehicular
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
                else -> {
                    // TAB 4: Catálogo de Piezas (Car Parts Encyclopedia)
                    PartsCatalogTabContent(
                        viewModel = viewModel,
                        currentOdoLong = currentOdoLong,
                        prefs = prefs,
                        expenses = expenses,
                        onExpensesChanged = { expenses = it }
                    )
                }
            }
        }
    }

    // Modal para agregar alertas manuales
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
                        fontSize = 18.sp,
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
                                val nextDue = nextDueStr.toLongOrNull() ?: (currentOdoLong + interval)
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

    // Modal para registrar gastos
    if (showAddExpenseModal) {
        Dialog(onDismissRequest = { showAddExpenseModal = false }) {
            Surface(
                color = MeetColors.backgroundDeep.copy(alpha = 0.98f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(MeetColors.electricBlue.copy(alpha = 0.5f), MeetColors.electricBlue.copy(alpha = 0.15f))
                        ),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REGISTRAR GASTO",
                        color = MeetColors.electricBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    // Category chips selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val cats = listOf("Combustible", "Servicio Aceite", "Repuesto", "Lavado", "Otros")
                        cats.forEach { cat ->
                            val isSelected = expenseCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MeetColors.electricBlue else Color(0x15FFFFFF))
                                    .clickable { expenseCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) MeetColors.backgroundDeep else Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = expenseCostStr,
                        onValueChange = { expenseCostStr = it },
                        label = { Text("Costo ($)", color = MeetColors.textSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.electricBlue,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedLabelColor = MeetColors.electricBlue,
                            unfocusedLabelColor = MeetColors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = expenseOdometerStr,
                        onValueChange = { expenseOdometerStr = it },
                        label = { Text("Odómetro (KM)", color = MeetColors.textSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.electricBlue,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedLabelColor = MeetColors.electricBlue,
                            unfocusedLabelColor = MeetColors.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = expenseNotes,
                        onValueChange = { expenseNotes = it },
                        label = { Text("Descripción / Detalles", color = MeetColors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.electricBlue,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedLabelColor = MeetColors.electricBlue,
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
                            onClick = { showAddExpenseModal = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MeetColors.textSecondary.copy(alpha = 0.5f))
                        ) {
                            Text("CANCELAR", color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val cost = expenseCostStr.toDoubleOrNull() ?: 0.0
                                val odo = expenseOdometerStr.toLongOrNull() ?: currentOdoLong
                                if (cost > 0.0) {
                                    val newItem = ExpenseItem(
                                        id = UUID.randomUUID().toString(),
                                        category = expenseCategory,
                                        cost = cost,
                                        odometer = odo,
                                        date = System.currentTimeMillis(),
                                        notes = expenseNotes
                                    )
                                    val updatedList = expenses + newItem
                                    saveExpenses(context, updatedList)
                                    expenses = updatedList
                                    
                                    expenseCostStr = ""
                                    expenseNotes = ""
                                    showAddExpenseModal = false
                                    Toast.makeText(context, "✅ Gasto registrado", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "⚠️ Ingrese un costo válido", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue)
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
        modifier = modifier.size(90.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Background circle track
            drawCircle(
                color = MeetColors.cardBackground,
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth
                )
            )

            // Progress arc
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
                fontSize = 18.sp
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
                        AnimatedNeonGlyph(icon, contentDescription = null, tint = color, fontSize = 16.sp)
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
                    fontSize = 11.sp,
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

// ═══════════════════════════════════════════════════════
// LOCAL STORAGE HELPER FUNCTIONS (JSON SHAPED EXPENSES)
// ═══════════════════════════════════════════════════════

private fun loadExpenses(context: Context): List<ExpenseItem> {
    val prefs = context.getSharedPreferences("meet_maintenance_prefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("expenses_list", "[]") ?: "[]"
    val list = mutableListOf<ExpenseItem>()
    try {
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                ExpenseItem(
                    id = obj.getString("id"),
                    category = obj.getString("category"),
                    cost = obj.getDouble("cost"),
                    odometer = obj.getLong("odometer"),
                    date = obj.getLong("date"),
                    notes = obj.getString("notes")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list.sortedByDescending { it.date }
}

private fun saveExpenses(context: Context, list: List<ExpenseItem>) {
    val prefs = context.getSharedPreferences("meet_maintenance_prefs", Context.MODE_PRIVATE)
    val arr = JSONArray()
    for (item in list) {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("category", item.category)
        obj.put("cost", item.cost)
        obj.put("odometer", item.odometer)
        obj.put("date", item.date)
        obj.put("notes", item.notes)
        arr.put(obj)
    }
    prefs.edit().putString("expenses_list", arr.toString()).apply()
}

// ═══════════════════════════════════════════════════════
// CAR PARTS ENCYCLOPEDIA DATA MODELS AND DATA LIST
// ═══════════════════════════════════════════════════════

data class CarPartDetail(
    val name: String,
    val category: String, // "Motor", "Transmisión", "Suspensión", "Frenos", "Escape", "Eléctrico", "Refrigeración", "Combustible"
    val icon: String,
    val description: String,
    val symptoms: String,
    val averageLife: String
)

val carPartsCatalog = listOf(
    CarPartDetail("Pistones", "Motor", "🛢️", "Componentes móviles que comprimen la mezcla y transmiten la fuerza de la combustión al cigüeñal.", "Humo azul en el escape, pérdida de potencia, consumo elevado de aceite.", "Vida útil del motor (+300,000 km con buen mantenimiento)."),
    CarPartDetail("Árbol de Levas", "Motor", "⚙️", "Controla la apertura y cierre de las válvulas de admisión y escape en el momento preciso.", "Ruido de golpeteo metálico (taqués), fallos de encendido, pérdida de potencia.", "Vida útil del motor (+250,000 km)."),
    CarPartDetail("Cigüeñal", "Motor", "🔄", "Transforma el movimiento lineal de los pistones en movimiento rotativo para mover las ruedas.", "Vibración extrema del motor, ruidos metálicos internos graves, pérdida de presión de aceite.", "Vida útil del motor (+300,000 km)."),
    CarPartDetail("Correa / Cadena de Distribución", "Motor", "⚙️", "Sincroniza la rotación del cigüeñal y el árbol de levas para evitar colisión entre válvulas y pistones.", "Ruido de traqueteo en ralentí (cadena), chirrido (correa), fallo de arranque.", "Correa: 80k-120k km. Cadena: Generalmente de por vida (+250k km)."),
    CarPartDetail("Turbocompresor", "Motor", "🌀", "Sobrealimenta el motor inyectando aire a presión para incrementar la potencia.", "Silbido fuerte tipo ambulancia, humo blanco/azul, aceleración retardada (turbo-lag).", "150,000 - 250,000 km."),
    CarPartDetail("Bujías de Encendido", "Motor", "⚡", "Generan la chispa eléctrica para inflamar la mezcla de aire y gasolina.", "Arranque difícil en frío, ralentí inestable, aumento del consumo de combustible.", "Cobre/Níquel: 30,000 km. Iridio/Platino: 80,000 - 120,000 km."),
    CarPartDetail("Bujías de Precalentamiento", "Motor", "🔌", "Calientan la cámara de combustión en motores diésel para facilitar el arranque en frío.", "Arranque muy difícil en frío, humo blanco tras arrancar, testigo de calentadores encendido.", "80,000 - 120,000 km."),
    CarPartDetail("Soportes de Motor", "Motor", "🔩", "Sujetan el bloque motor al chasis del vehículo y absorben las vibraciones mecánicas.", "Vibraciones fuertes en el habitáculo al ralentí, golpes al acelerar o frenar.", "100,000 - 150,000 km."),
    CarPartDetail("Batería de Alto Voltaje (Híbridos/EV)", "Motor", "🔋", "Almacena la energía eléctrica de tracción para alimentar los motores eléctricos.", "Reducción severa de autonomía, incapacidad de cargar al 100%, fallos del sistema híbrido.", "160,000 - 240,000 km (o 8-10 años)."),
    CarPartDetail("Inversor de Potencia (Híbridos/EV)", "Motor", "⚡", "Convierte la corriente continua (DC) de la batería de tracción en corriente alterna (AC) para el motor.", "Pérdida instantánea de tracción, modo de emergencia activo, sin respuesta al acelerar.", "Vida útil del vehículo (diseñado para durar sin mantenimiento)."),
    CarPartDetail("Convertidor DC-DC (Híbridos/EV)", "Motor", "🔌", "Reduce el voltaje de la batería de tracción para alimentar la red de 12V del carro.", "Batería de 12V descargada frecuentemente, fallos en luces y accesorios electrónicos.", "Vida útil del vehículo."),

    CarPartDetail("Embrague (Clutch)", "Transmisión", "🚗", "Conecta y desconecta la fuerza del motor a la caja de cambios manual.", "El pedal se siente duro, el motor se revoluciona pero el carro no avanza (patinado).", "100,000 - 180,000 km (según hábitos de manejo)."),
    CarPartDetail("Caja de Cambios", "Transmisión", "🔄", "Multiplica la fuerza o velocidad del motor mediante engranajes (manual o automática).", "Dificultad para entrar marchas, ruidos de rascado, fugas de aceite rojo/marrón.", "Manual: +300,000 km. Automática: 200,000 - 300,000 km (requiere cambio de aceite)."),
    CarPartDetail("Convertidor de Par", "Transmisión", "🔄", "Acoplamiento hidráulico que transmite el par del motor a la transmisión automática.", "Tirones en marchas, vibración tipo 'carretera rugosa', sobrecalentamiento de transmisión.", "Vida útil de la transmisión automática."),
    CarPartDetail("Junta Homocinética / Semieje", "Transmisión", "🔩", "Transmite la rotación a las ruedas de dirección permitiendo el movimiento de suspensión.", "Ruido de 'clac-clac' al girar completamente la dirección a baja velocidad, grasa tirada en rines.", "120,000 - 200,000 km (falla rápido si se rompe el cubrepolvos)."),
    CarPartDetail("Diferencial", "Transmisión", "⚙️", "Permite que las ruedas motrices giren a velocidades distintas al tomar curvas.", "Zumbido constante que aumenta con la velocidad del vehículo, fugas de aceite.", "Generalmente de por vida (requiere cambio de valvulina cada 80k-100k km)."),
    CarPartDetail("Árbol de Transmisión", "Transmisión", "🔩", "Eje que conecta la transmisión con el diferencial en vehículos de tracción trasera o 4WD.", "Vibración severa bajo el piso a velocidades de carretera, ruidos metálicos al meter reversa.", "150,000 - 250,000 km."),

    CarPartDetail("Amortiguadores", "Suspensión", "🚗", "Absorben las oscilaciones de los muelles para mantener las llantas pegadas al pavimento.", "Rebote excesivo al pasar baches, el auto se inclina mucho al frenar, fugas de aceite visibles.", "60,000 - 90,000 km."),
    CarPartDetail("Brazos de Control / Horquillas", "Suspensión", "📐", "Guían el movimiento vertical de las ruedas y mantienen la alineación del chasis.", "Ruidos sordos (clonks) en baches, dirección errática o desalineada, desgaste irregular de llantas.", "100,000 - 150,000 km (suelen fallar los bujes de goma)."),
    CarPartDetail("Rótulas de Suspensión", "Suspensión", "🔩", "Pivotes que conectan los brazos de control a las ruedas, permitiendo el giro de dirección.", "Ruido crujiente metálico al pasar baches o girar, juego excesivo en la rueda al levantar el auto.", "80,000 - 120,000 km (CRÍTICO: su rotura puede desprender la rueda)."),
    CarPartDetail("Barra Estabilizadora", "Suspensión", "📐", "Reduce el balanceo de la carrocería en curvas acoplando ambas ruedas del eje.", "Balanceo excesivo en curvas, golpes secos al pasar topes o baches con una sola rueda.", "Generalmente de por vida (las gomas de soporte se cambian cada 50,000 km)."),
    CarPartDetail("Cremallera de Dirección", "Suspensión", "🔄", "Convierte el movimiento circular del volante en movimiento lineal para girar las llantas.", "Dureza extrema al girar, holgura en el volante, fugas de fluido hidráulico por las botas.", "150,000 - 250,000 km."),
    CarPartDetail("Bomba de Dirección Asistida", "Suspensión", "🧪", "Suministra presión hidráulica para suavizar el esfuerzo de giro del conductor.", "Zumbido agudo al girar el volante al máximo, dirección progresivamente dura.", "120,000 - 180,000 km (las eléctricas de hoy duran más)."),
    CarPartDetail("Rodamiento de Rueda", "Suspensión", "🔄", "Permite que la rueda gire libremente sobre el eje con la mínima fricción.", "Zumbido grave que incrementa con la velocidad (similar a avión de hélice), juego en la llanta.", "120,000 - 180,000 km."),

    CarPartDetail("Pastillas de Freno", "Frenos", "🛑", "Material de fricción que presiona contra los discos para detener el vehículo.", "Chirrido metálico al frenar, pedal esponjoso, distancia de frenado prolongada.", "30,000 - 50,000 km (las delantera se desgastan el doble de rápido)."),
    CarPartDetail("Discos de Freno", "Frenos", "💿", "Platos metálicos giratorios sobre los cuales actúan las pastillas de freno.", "Vibración en el volante al frenar a alta velocidad (discos alabeados), ceja marcada de desgaste.", "60,000 - 100,000 km (soportan aprox. 2 o 3 juegos de pastillas)."),
    CarPartDetail("Caliper / Pinza de Freno", "Frenos", "🛑", "Aloja las pastillas y usa pistones hidráulicos para apretarlas contra el disco.", "El carro se jala hacia un lado al frenar o al soltar el freno (caliper atascado), fugas de líquido.", "150,000 - 250,000 km."),
    CarPartDetail("Cilindro Maestro (Bomba)", "Frenos", "🧪", "Genera la presión hidráulica en el circuito de frenos al pisar el pedal.", "El pedal de freno se hunde lentamente hasta el piso sin frenar adecuadamente.", "150,000 - 200,000 km."),
    CarPartDetail("Servofreno / Booster", "Frenos", "💨", "Multiplica la fuerza del pedal de freno utilizando vacío del motor o bomba eléctrica.", "Pedal extremadamente duro, requiere mucha fuerza física para detener el carro.", "150,000 - 250,000 km."),
    CarPartDetail("Módulo ABS", "Frenos", "🔌", "Modula la presión de freno electrónicamente para evitar el bloqueo de ruedas.", "Testigo de ABS encendido, bloqueo de ruedas en frenadas de emergencia, pedal rígido.", "Vida útil del vehículo."),

    CarPartDetail("Convertidor Catalítico", "Escape", "💨", "Reduce la toxicidad de los gases de escape transformando CO y NOx en gases inocuos.", "Humo de escape con olor a huevo podrido (azufre), pérdida de potencia, código P0420.", "150,000 - 200,000 km."),
    CarPartDetail("Filtro de Partículas (DPF)", "Escape", "💨", "Retiene el hollín producido por motores diésel para quemarlo en regeneraciones pasivas.", "Pérdida notable de potencia (modo de emergencia), aumento de nivel de aceite, aviso DPF.", "150,000 - 250,000 km (falla antes por trayectos cortos constantes en ciudad)."),
    CarPartDetail("Válvula EGR", "Escape", "⚙️", "Recircula una parte de los gases de escape a la admisión para reducir emisiones de NOx.", "Ralentí inestable, tirones a bajas revoluciones, humo negro al acelerar.", "80,000 - 120,000 km (se obstruye por carbonilla)."),
    CarPartDetail("Sonda Lambda / Sensor de Oxígeno", "Escape", "🔌", "Mide la cantidad de oxígeno en el escape para ajustar la mezcla aire/combustible.", "Aumento drástico del consumo de gasolina, hollín negro en escape, ralentí inestable.", "100,000 - 160,000 km."),
    CarPartDetail("Silenciador de Escape", "Escape", "🔊", "Amortigua el ruido producido por la salida de gases del motor.", "Ruido de escape deportivo fuerte y molesto, soplidos bajo el carro por roturas.", "80,000 - 150,000 km (se corroe por condensación de agua interna)."),

    CarPartDetail("Alternador", "Eléctrico", "🔌", "Genera energía eléctrica para alimentar los sistemas y recargar la batería mientras el motor gira.", "Luces tenues, parpadeo en tablero, descarga continua de batería, testigo de batería encendido.", "120,000 - 200,000 km."),
    CarPartDetail("Batería de 12V", "Eléctrico", "🔋", "Proporciona la corriente necesaria para encender el motor y alimentar accesorios apagados.", "El motor arranca lento o hace 'clic-clic', pérdida de hora/memorias, sin voltaje en frío.", "3 - 5 años (las AGM para start-stop duran más si se cuidan)."),
    CarPartDetail("Motor de Arranque", "Eléctrico", "⚙️", "Motor eléctrico acoplado al volante de inercia para dar el impulso inicial de encendido.", "Se escucha un golpe seco pero no gira el motor, no hace nada al girar la llave.", "120,000 - 180,000 km."),
    CarPartDetail("Sensor CKP (Cigüeñal)", "Eléctrico", "🔌", "Informa a la ECU la posición y velocidad de giro del motor para coordinar chispa e inyección.", "El motor se apaga repentinamente al calentarse y arranca al enfriarse, no arranca.", "100,000 - 180,000 km."),
    CarPartDetail("Sensor MAF (Flujo de Aire)", "Eléctrico", "💨", "Mide la masa de aire que entra a la admisión para calcular el combustible necesario.", "Tirones en aceleración, humo negro en escape, motor se apaga al ralentí.", "100,000 - 150,000 km (se puede limpiar)."),
    CarPartDetail("Unidad de Control (ECU)", "Eléctrico", "🧠", "Computadora central que procesa datos de sensores y controla actuadores de combustión.", "Falta de chispa o pulso en cilindros específicos, imposibilidad de comunicar con escáner.", "Vida útil del vehículo (suele fallar por cortocircuito o humedad)."),

    CarPartDetail("Radiador", "Refrigeración", "🌡️", "Intercambiador térmico que enfría el anticongelante mediante el aire exterior.", "Pérdida de anticongelante visible, aumento excesivo de temperatura de motor.", "120,000 - 180,000 km (se corroe o agrietan sus tanques plásticos)."),
    CarPartDetail("Termostato", "Refrigeración", "🌡️", "Válvula térmica que regula el flujo de refrigerante hacia el radiador según temperatura.", "El motor tarda mucho en calentar (abierto) o se sobrecalienta en minutos (cerrado).", "80,000 - 120,000 km."),
    CarPartDetail("Bomba de Agua", "Refrigeración", "💧", "Impulsa el refrigerante por todo el circuito interno del motor y radiador.", "Fuga de refrigerante bajo la polea de la bomba, ruido de chillido metálico, sobrecalentamiento.", "80,000 - 150,000 km (se cambia junto a la distribución)."),
    CarPartDetail("Compresor de A/C", "Refrigeración", "❄️", "Comprime el gas refrigerante en el circuito de climatización para enfriar el habitáculo.", "El aire sale a temperatura ambiente, ruido metálico fuerte al encender el clima.", "150,000 - 250,000 km."),

    CarPartDetail("Bomba de Combustible", "Combustible", "⛽", "Succiona el combustible del tanque y lo envía a presión hacia los inyectores.", "Tirones al subir pendientes, el carro tarda mucho en arrancar o no enciende.", "100,000 - 160,000 km (falla antes si se viaja seguido con reserva de gasolina)."),
    CarPartDetail("Inyectores de Combustible", "Combustible", "💉", "Pulverizan el combustible finamente dentro de la admisión o cilindro.", "Ralentí inestable, fallos de combustión (misfires), humo negro, fuerte olor a gasolina.", "150,000 - 250,000 km (mejoran si se les hace limpieza ultrasónica)."),
    CarPartDetail("Filtro de Combustible", "Combustible", "⛽", "Retiene impurezas y agua presentes en el combustible antes de entrar al motor.", "Pérdida de potencia a altas revoluciones, jaloneos, dificultad de arranque.", "20,000 - 40,000 km (en diésel es crítico purgarlo).")
)

@Composable
fun PartsCatalogTabContent(
    viewModel: ObdViewModel,
    currentOdoLong: Long,
    prefs: android.content.SharedPreferences,
    expenses: List<ExpenseItem>,
    onExpensesChanged: (List<ExpenseItem>) -> Unit
) {
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    var selectedPartForDetail by remember { mutableStateOf<CarPartDetail?>(null) }
    var partToRegisterMaintenance by remember { mutableStateOf<CarPartDetail?>(null) }

    // States for registration form dialog:
    var registerDateStr by remember { mutableStateOf("") }
    var registerOdometerStr by remember { mutableStateOf("") }
    var registerIntervalStr by remember { mutableStateOf("") }
    var registerCostStr by remember { mutableStateOf("") }
    var registerNotes by remember { mutableStateOf("") }

    LaunchedEffect(partToRegisterMaintenance) {
        partToRegisterMaintenance?.let { part ->
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            registerDateStr = sdf.format(java.util.Date())
            registerOdometerStr = currentOdoLong.toString()
            registerIntervalStr = parseIntervalKm(part.averageLife).toString()
            registerCostStr = when (part.category) {
                "Motor" -> "250.00"
                "Transmisión" -> "350.00"
                "Suspensión" -> "120.00"
                "Frenos" -> "85.00"
                "Escape" -> "180.00"
                "Eléctrico" -> "95.00"
                "Refrigeración" -> "110.00"
                "Combustible" -> "140.00"
                else -> "100.00"
            }
            registerNotes = "Reemplazo preventivo de ${part.name}."
        }
    }

    val categories = listOf("Todos", "Motor", "Transmisión", "Suspensión", "Frenos", "Escape", "Eléctrico", "Refrigeración", "Combustible")

    val filteredParts = remember(searchQuery, selectedCategory) {
        carPartsCatalog.filter { part ->
            val matchesCategory = selectedCategory == "Todos" || part.category == selectedCategory
            val matchesSearch = part.name.contains(searchQuery, ignoreCase = true) ||
                    part.description.contains(searchQuery, ignoreCase = true) ||
                    part.symptoms.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar pieza (ej: Alternador, Bujía...)", color = MeetColors.textSecondary) },
            leadingIcon = { AnimatedNeonGlyph("🔍", contentDescription = null, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        AnimatedNeonGlyph(
                            glyph = "✕",
                            contentDescription = "Limpiar busqueda",
                            tint = MeetColors.textSecondary,
                            fontSize = 16.sp,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MeetColors.cyberCyan,
                unfocusedBorderColor = MeetColors.borderSubtle,
                focusedContainerColor = MeetColors.cardBackground,
                unfocusedContainerColor = MeetColors.cardBackground
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategory == category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.2f) else MeetColors.cardBackground)
                        .border(1.dp, if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = category.uppercase(),
                        color = if (isSelected) MeetColors.cyberCyan else MeetColors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredParts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeetColors.cardBackground)
                    .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❌ No se encontraron piezas", color = MeetColors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Prueba con otra búsqueda o categoría.", color = MeetColors.textMuted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredParts) { part ->
                    EliteCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPartForDetail = part },
                        borderColor = MeetColors.borderSubtle,
                        glowColor = MeetColors.cyberCyan.copy(alpha = 0.05f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon circular frame
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.cyberCyan.copy(alpha = 0.1f))
                                    .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedNeonGlyph(part.icon, contentDescription = null, fontSize = 20.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = part.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    // Mini Category Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MeetColors.cyberCyan.copy(alpha = 0.08f))
                                            .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = part.category.uppercase(),
                                            color = MeetColors.cyberCyan,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = part.description,
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            AnimatedNeonGlyph(
                                glyph = "➔",
                                contentDescription = "Abrir",
                                tint = MeetColors.cyberCyan,
                                fontSize = 16.sp,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Detail Dialog
    selectedPartForDetail?.let { part ->
        Dialog(onDismissRequest = { selectedPartForDetail = null }) {
            Surface(
                color = MeetColors.backgroundDeep.copy(alpha = 0.98f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(MeetColors.cyberCyan.copy(alpha = 0.5f), MeetColors.cyberCyan.copy(alpha = 0.15f))
                        ),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                            .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedNeonGlyph(part.icon, contentDescription = null, fontSize = 32.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = part.name.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MeetColors.cyberCyan.copy(alpha = 0.1f))
                            .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = part.category.uppercase(),
                            color = MeetColors.cyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Description Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "DESCRIPCIÓN Y FUNCIÓN",
                            color = MeetColors.cyberCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = part.description,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Symptoms Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MeetColors.error.copy(alpha = 0.05f))
                            .border(1.dp, MeetColors.error.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedNeonGlyph("⚠️", contentDescription = null, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SÍNTOMAS DE FALLA COMUNES",
                                color = MeetColors.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = part.symptoms,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Lifespan Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MeetColors.success.copy(alpha = 0.05f))
                            .border(1.dp, MeetColors.success.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏱️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VIDA ÚTIL / INTERVALO",
                                color = MeetColors.success,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = part.averageLife,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EliteOutlinedButton(
                            text = "VOLVER",
                            onClick = { selectedPartForDetail = null },
                            modifier = Modifier.weight(1f)
                        )
                        EliteButton(
                            text = "AÑADIR A MANTENIMIENTO",
                            onClick = {
                                partToRegisterMaintenance = part
                                selectedPartForDetail = null
                            },
                            color = MeetColors.neonGreen,
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }
        }
    }

    // Dialog for registering maintenance
    partToRegisterMaintenance?.let { part ->
        Dialog(onDismissRequest = { partToRegisterMaintenance = null }) {
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
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "REGISTRAR SERVICIO",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    val vehicleName = selectedVehicle?.let { "${it.year} ${it.make} ${it.model}" } ?: "Vehículo Genérico"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.neonGreen.copy(alpha = 0.08f))
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "VEHÍCULO ACTIVO",
                                color = MeetColors.textSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = vehicleName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = part.name,
                        onValueChange = {},
                        label = { Text("Pieza Cambiada", color = MeetColors.textSecondary) },
                        readOnly = true,
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
                        value = registerDateStr,
                        onValueChange = { registerDateStr = it },
                        label = { Text("Fecha (dd/mm/aaaa)", color = MeetColors.textSecondary) },
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
                        value = registerOdometerStr,
                        onValueChange = { registerOdometerStr = it },
                        label = { Text("Odómetro actual (KM)", color = MeetColors.textSecondary) },
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
                        value = registerIntervalStr,
                        onValueChange = { registerIntervalStr = it },
                        label = { Text("Intervalo para próximo cambio (KM)", color = MeetColors.textSecondary) },
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
                        value = registerCostStr,
                        onValueChange = { registerCostStr = it },
                        label = { Text("Precio aproximado / Costo ($)", color = MeetColors.textSecondary) },
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
                        value = registerNotes,
                        onValueChange = { registerNotes = it },
                        label = { Text("Detalles / Notas", color = MeetColors.textSecondary) },
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
                            onClick = { partToRegisterMaintenance = null },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MeetColors.textSecondary.copy(alpha = 0.5f))
                        ) {
                            Text("CANCELAR", color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val odoVal = registerOdometerStr.toLongOrNull() ?: currentOdoLong
                                val intervalVal = registerIntervalStr.toLongOrNull() ?: 20000L
                                val costVal = registerCostStr.toDoubleOrNull() ?: 100.0
                                val nextDueKm = odoVal + intervalVal

                                // 1. Add alert to DB
                                viewModel.addMaintenanceAlert(part.name, intervalVal, nextDueKm)

                                // 2. Add expense item to Preference & list state
                                val newExpense = ExpenseItem(
                                    id = UUID.randomUUID().toString(),
                                    category = "Repuesto",
                                    cost = costVal,
                                    odometer = odoVal,
                                    date = System.currentTimeMillis(),
                                    notes = "Reemplazo de ${part.name}. $registerNotes"
                                )
                                val updatedExpenses = expenses + newExpense
                                saveExpenses(context, updatedExpenses)
                                onExpensesChanged(updatedExpenses)

                                // 3. Reset wear life of corresponding component in SharedPreferences if matched
                                mapPartNameToWearComponentId(part.name)?.let { wearId ->
                                    prefs.edit().putLong("last_done_$wearId", odoVal).apply()
                                }

                                Toast.makeText(context, "Mantenimiento registrado para ${part.name}", Toast.LENGTH_SHORT).show()
                                partToRegisterMaintenance = null
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

private fun mapPartNameToWearComponentId(partName: String): String? {
    val nameLower = partName.lowercase(java.util.Locale.getDefault())
    return when {
        nameLower.contains("aceite") -> "oil"
        nameLower.contains("bujía") || nameLower.contains("bujias") -> "spark_plugs"
        nameLower.contains("filtro de aire") -> "air_filter"
        nameLower.contains("pastilla") || nameLower.contains("freno") || nameLower.contains("caliper") || nameLower.contains("disco") -> {
            if (nameLower.contains("líquido") || nameLower.contains("liquido")) "brake_fluid" else "brakes"
        }
        nameLower.contains("refrigerante") || nameLower.contains("radiador") || nameLower.contains("termostato") || nameLower.contains("bomba de agua") -> "coolant"
        nameLower.contains("dirección") || nameLower.contains("direccion") -> "steering"
        nameLower.contains("transmisión") || nameLower.contains("transmision") || nameLower.contains("diferencial") || nameLower.contains("embrague") || nameLower.contains("clutch") || nameLower.contains("caja de cambios") -> "transmission"
        nameLower.contains("distribución") || nameLower.contains("distribucion") || nameLower.contains("correa") || nameLower.contains("cadena") -> "timing_belt"
        else -> null
    }
}

private fun parseIntervalKm(lifeStr: String): Long {
    val regex = Regex("(\\d{1,3}(,\\d{3})*)")
    val matches = regex.findAll(lifeStr).map { it.value.replace(",", "").toLongOrNull() ?: 0L }.toList()
    return if (matches.isNotEmpty()) {
        matches.firstOrNull { it > 0L } ?: 20000L
    } else {
        20000L
    }
}
