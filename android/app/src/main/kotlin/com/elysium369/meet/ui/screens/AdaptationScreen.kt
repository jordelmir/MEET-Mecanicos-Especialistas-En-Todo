package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════════════════════════════
// ADAPTATION SCREEN — Soft-Coding & Module Adaptation Interface
// ═══════════════════════════════════════════════════════════════

data class AdaptationProcedure(
    val id: String,
    val title: String,
    val description: String,
    val category: AdaptationCategory,
    val difficulty: Difficulty,
    val requiresEngineOff: Boolean = true,
    val capabilityPackId: String? = null,
    val warningMessage: String? = null
)

enum class AdaptationCategory(val label: String, val color: Color) {
    THROTTLE("Cuerpo Aceleración", MeetColors.neonGreen),
    BATTERY("Batería/Voltaje", MeetColors.warning),
    STEERING("Dirección", MeetColors.cyberCyan),
    TRANSMISSION("Transmisión", MeetColors.electricBlue),
    WINDOWS("Ventanas", MeetColors.cyberCyan),
    TPMS("Presión Neumáticos", MeetColors.warning),
    INJECTORS("Inyectores", MeetColors.error)
}

enum class Difficulty(val label: String, val color: Color) {
    BASIC("Básico", MeetColors.neonGreen),
    INTERMEDIATE("Intermedio", MeetColors.warning),
    ADVANCED("Avanzado", MeetColors.error)
}

enum class ProcedureState { IDLE, RUNNING, SUCCESS, ERROR }

@Composable
fun AdaptationScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val procedures = remember { buildAdaptationCatalog() }
    var selectedProcedure by remember { mutableStateOf<AdaptationProcedure?>(null) }
    var procedureState by remember { mutableStateOf(ProcedureState.IDLE) }
    var statusMessage by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AdaptationCategory?>(null) }
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == com.elysium369.meet.core.obd.ObdState.CONNECTED
    val logLines = remember { mutableStateListOf<String>() }

    val filtered = remember(selectedCategory) {
        if (selectedCategory == null) procedures
        else procedures.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.backOrHome() }) {
                AnimatedNeonIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Adaptaciones & Codificación", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Soft-coding de módulos ECU", color = MeetColors.textSecondary, fontSize = 12.sp)
            }
            AnimatedNeonIcon(Icons.Default.Build, "Tools", tint = MeetColors.electricBlue, modifier = Modifier.size(24.dp))
        }

        // ── Warning Banner ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MeetColors.warning.copy(alpha = 0.1f))
                .border(1.dp, MeetColors.warning.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedNeonIcon(Icons.Default.Warning, "Warning", tint = MeetColors.warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Catálogo educativo. No se transmite ninguna adaptación sin paquete OEM revisado para el vehículo y ECU exactos.",
                color = MeetColors.warning, fontSize = 11.sp, lineHeight = 15.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Category Filter ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AdaptationCategory.entries.take(4).forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) cat.color.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (isSelected) cat.color else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable { selectedCategory = if (isSelected) null else cat }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(cat.label, color = if (isSelected) cat.color else MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AdaptationCategory.entries.drop(4).forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) cat.color.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (isSelected) cat.color else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable { selectedCategory = if (isSelected) null else cat }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(cat.label, color = if (isSelected) cat.color else MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Procedure List ──
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filtered, key = { it.id }) { proc ->
                val isExpanded = selectedProcedure?.id == proc.id

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isExpanded) proc.category.color.copy(alpha = 0.06f) else MeetColors.cardBackground)
                        .border(1.dp, if (isExpanded) proc.category.color.copy(alpha = 0.5f) else MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                        .clickable {
                            selectedProcedure = if (isExpanded) null else proc
                            procedureState = ProcedureState.IDLE
                            statusMessage = ""
                        }
                        .padding(12.dp)
                ) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(proc.category.color, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(proc.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        // Difficulty badge
                        Text(
                            proc.difficulty.label, color = proc.difficulty.color, fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(proc.difficulty.color.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Expanded detail
                    AnimatedVisibility(visible = isExpanded) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text(proc.description, color = MeetColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)

                            if (proc.requiresEngineOff) {
                                Spacer(Modifier.height(6.dp))
                                Text("⚠️ Requiere motor APAGADO, llave en ON", color = MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }

                            proc.warningMessage?.let {
                                Spacer(Modifier.height(4.dp))
                                Text("🔴 $it", color = MeetColors.error, fontSize = 11.sp)
                            }

                            Spacer(Modifier.height(12.dp))

                            // Status indicator
                            when (procedureState) {
                                ProcedureState.RUNNING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = proc.category.color,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(statusMessage, color = proc.category.color, fontSize = 13.sp)
                                    }
                                }
                                ProcedureState.SUCCESS -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AnimatedNeonIcon(Icons.Default.CheckCircle, "OK", tint = MeetColors.success, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(statusMessage, color = MeetColors.success, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                ProcedureState.ERROR -> {
                                    Text("❌ $statusMessage", color = MeetColors.error, fontSize = 13.sp)
                                }
                                else -> {}
                            }

                            // Console Terminal Log (UDS/OBD)
                            if (logLines.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.9f))
                                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        "UDS ADAPTATION TERMINAL LOG",
                                        color = MeetColors.textSecondary.copy(alpha = 0.6f),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    val consoleState = rememberLazyListState()
                                    LaunchedEffect(logLines.size) {
                                        if (logLines.size > 0) {
                                            consoleState.scrollToItem(logLines.size - 1)
                                        }
                                    }
                                    
                                    LazyColumn(
                                        state = consoleState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        items(logLines) { line ->
                                            val color = when {
                                                line.contains("TX:") -> MeetColors.electricBlue
                                                line.contains("RX:") -> MeetColors.cyberCyan
                                                line.contains("ERR:") || line.contains("ERROR:") -> MeetColors.error
                                                line.contains("ÉXITO") || line.contains("COMPLETADO") -> MeetColors.neonGreen
                                                else -> MeetColors.textSecondary
                                            }
                                            Text(
                                                text = line,
                                                color = color,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (procedureState != ProcedureState.RUNNING) {
                                Spacer(Modifier.height(8.dp))
                                EliteButton(
                                    text = when {
                                        !isConnected -> "Conecta OBD para evaluar"
                                        else -> "Requiere paquete OEM"
                                    },
                                    onClick = {
                                        procedureState = ProcedureState.ERROR
                                        statusMessage = "No transmitido: falta capability pack OEM aplicable y revisado."
                                        logLines.clear()
                                        logLines.add("BLOQUEADO ANTES DEL BUS: no existe autoridad OEM para esta rutina.")
                                    },
                                    color = proc.category.color,
                                    isEnabled = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Adaptation Catalog ──

private fun buildAdaptationCatalog(): List<AdaptationProcedure> = listOf(
    AdaptationProcedure(
        id = "throttle_relearn",
        title = "Reaprendizaje Cuerpo de Aceleración",
        description = "Reinicia los valores de adaptación del cuerpo de aceleración electrónico (ETC). Necesario después de limpiar la mariposa, cambiar el cuerpo de aceleración, o desconectar la batería. El ECU recalibra la posición mínima y máxima de la mariposa.",
        category = AdaptationCategory.THROTTLE,
        difficulty = Difficulty.BASIC,
        warningMessage = "No toque el pedal del acelerador durante el proceso"
    ),
    AdaptationProcedure(
        id = "idle_relearn",
        title = "Reaprendizaje de Ralentí",
        description = "Fuerza a la PCM a recalcular las RPM de ralentí base. Realice después de limpiar el cuerpo de aceleración, reparar fugas de vacío, o cuando el ralentí sea errático.",
        category = AdaptationCategory.THROTTLE,
        difficulty = Difficulty.BASIC,
    ),
    AdaptationProcedure(
        id = "battery_register",
        title = "Registro de Batería Nueva",
        description = "Informa al módulo de gestión de energía (BMS/IBS) que se instaló una batería nueva. Sin este registro, el alternador seguirá cargando como batería vieja, reduciendo la vida útil de la nueva.",
        category = AdaptationCategory.BATTERY,
        difficulty = Difficulty.INTERMEDIATE,
        warningMessage = "Solo realice después de instalar una batería nueva"
    ),
    AdaptationProcedure(
        id = "voltage_reset",
        title = "Reset Sensor Voltaje (IBS)",
        description = "Reinicia el sensor inteligente de batería. Necesario cuando el sensor IBS pierde sincronización y reporta voltajes incorrectos, causando modo de protección.",
        category = AdaptationCategory.BATTERY,
        difficulty = Difficulty.INTERMEDIATE,
    ),
    AdaptationProcedure(
        id = "steering_center",
        title = "Calibración Centro de Dirección",
        description = "Recalibra la posición central del sensor del ángulo de dirección (SAS). Obligatorio después de alineación, cambio de cremallera, o rotación de llantas.",
        category = AdaptationCategory.STEERING,
        difficulty = Difficulty.BASIC,
        warningMessage = "Coloque el volante perfectamente centrado antes de iniciar"
    ),
    AdaptationProcedure(
        id = "trans_adapt_reset",
        title = "Reset Adaptación Transmisión",
        description = "Borra los valores de presión y tiempos de cambio aprendidos por el TCM. Necesario después de cambiar aceite de transmisión, reparaciones internas, o cuando los cambios son bruscos.",
        category = AdaptationCategory.TRANSMISSION,
        difficulty = Difficulty.ADVANCED,
        warningMessage = "Después del reset, conduzca 20-30 min variando velocidades para reaprender"
    ),
    AdaptationProcedure(
        id = "window_init",
        title = "Inicialización Ventanas Eléctricas",
        description = "Recalibra los límites superior e inferior de las ventanas eléctricas con función auto-up/down. Requerido después de desconectar la batería o cambiar el regulador.",
        category = AdaptationCategory.WINDOWS,
        difficulty = Difficulty.BASIC,
    ),
    AdaptationProcedure(
        id = "tpms_relearn",
        title = "Reaprendizaje TPMS",
        description = "Asocia los sensores de presión de neumáticos con las posiciones del vehículo. Necesario después de rotación de llantas o instalación de sensores nuevos.",
        category = AdaptationCategory.TPMS,
        difficulty = Difficulty.INTERMEDIATE,
        warningMessage = "Infle todos los neumáticos a la presión recomendada antes de iniciar"
    ),
    AdaptationProcedure(
        id = "injector_coding",
        title = "Codificación de Inyectores",
        description = "Registra los códigos IMA/IQA de inyectores nuevos en la PCM. Cada inyector tiene una calibración individual grabada en su cuerpo. Sin esta codificación, el motor tendrá ralentí inestable y mayor emisión.",
        category = AdaptationCategory.INJECTORS,
        difficulty = Difficulty.ADVANCED,
        warningMessage = "Ingrese los códigos exactos del inyector. Códigos incorrectos causan daño al motor"
    ),
    AdaptationProcedure(
        id = "dpf_regen",
        title = "Regeneración Forzada DPF",
        description = "Inicia una regeneración estática del filtro de partículas diésel (DPF). Quema el hollín acumulado elevando la temperatura del escape a ~600°C. Solo para motores diésel.",
        category = AdaptationCategory.THROTTLE,
        difficulty = Difficulty.ADVANCED,
        warningMessage = "El escape alcanzará 600°C+. Mantenga distancia. No realice en espacios cerrados"
    )
)
