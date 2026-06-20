package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.DiagnosticSeverity
import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

@Composable
fun ScannerMonitorsTab(
    viewModel: ObdViewModel,
    isSpanish: Boolean,
    snackbarHostState: SnackbarHostState? = null,
    navController: NavController? = null
) {
    val mode06Results by viewModel.mode06Results.collectAsState()
    val isReading by viewModel.isReadingMode06.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == ObdState.CONNECTED
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark)
    ) {
        // Header with Premium Action
        HeaderSection(
            isConnected = isConnected,
            isReading = isReading,
            isSpanish = isSpanish,
            onRead = { viewModel.readMode06() },
            onNotConnectedClick = {
                scope.launch {
                    val result = snackbarHostState?.showSnackbar(
                        message = if (isSpanish) "OBD Desconectado. Conéctate a tu adaptador primero." else "OBD Disconnected. Connect your adapter first.",
                        actionLabel = if (isSpanish) "CONECTAR" else "CONNECT",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController?.navigate("connect")
                    }
                }
            }
        )

        // Health Summary Index
        if (mode06Results.isNotEmpty()) {
            HealthSummarySection(mode06Results, isSpanish)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Results list
        if (mode06Results.isEmpty() && !isReading) {
            EmptyMonitorsState(isConnected, isSpanish)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(mode06Results) { result ->
                    ExpertMonitorCard(result, isSpanish)
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    isConnected: Boolean, 
    isReading: Boolean, 
    isSpanish: Boolean, 
    onRead: () -> Unit,
    onNotConnectedClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isSpanish) "Módulos No Continuos" else "Non-Continuous Monitors",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (isSpanish) "Diagnóstico de Ingeniería Avanzado (Mode $06)" else "Advanced Engineering Diagnostics (Mode $06)",
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        EliteCard(
            onClick = if (isReading) null else {
                if (isConnected) onRead else onNotConnectedClick
            },
            backgroundColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.1f) else MeetColors.borderBlue.copy(alpha = 0.15f),
            borderColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.textSecondary.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            glowColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.3f) else Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MeetColors.neonGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isSpanish) "ANALIZANDO..." else "READING...", 
                        color = MeetColors.neonGreen, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Text(
                        if (isSpanish) "ESCANEAR" else "RUN TEST", 
                        color = if (isConnected) MeetColors.neonGreen else MeetColors.textSecondary, 
                        fontWeight = FontWeight.Black, 
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthSummarySection(results: List<Mode06TestResult>, isSpanish: Boolean) {
    val total = results.size
    val passed = results.count { it.passed }
    val failed = results.count { !it.passed }
    val healthIndex = if (total > 0) (passed.toFloat() / total.toFloat() * 100).toInt() else 100
    
    val healthColor = when {
        healthIndex > 90 -> MeetColors.neonGreen
        healthIndex > 70 -> MeetColors.warning
        else -> MeetColors.error
    }

    EliteCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = healthColor.copy(alpha = 0.4f),
        glowColor = healthColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = healthIndex / 100f,
                    color = healthColor,
                    strokeWidth = 6.dp,
                    trackColor = healthColor.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxSize()
                )
                Text("$healthIndex%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isSpanish) "Índice de Salud" else "Health Index", 
                    color = MeetColors.textSecondary, 
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (failed == 0) {
                        if (isSpanish) "SISTEMAS EN RANGO" else "SYSTEMS IN RANGE"
                    } else {
                        if (isSpanish) "FALLOS DETECTADOS" else "FAILURES DETECTED"
                    },
                    color = healthColor,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    if (isSpanish) "Evaluados $total parámetros de monitoreo." else "Evaluated $total monitoring parameters.",
                    color = MeetColors.textSecondary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                SummaryStat(passed.toString(), "OK", MeetColors.neonGreen)
                Spacer(modifier = Modifier.width(12.dp))
                SummaryStat(failed.toString(), "FAIL", if (failed > 0) MeetColors.error else MeetColors.textMuted)
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
    }
}

@Composable
private fun ExpertMonitorCard(result: Mode06TestResult, isSpanish: Boolean) {
    val statusColor = if (result.passed) MeetColors.neonGreen else MeetColors.error
    val cardGlow = if (!result.passed) statusColor.copy(alpha = 0.2f) else Color.Transparent

    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = statusColor.copy(alpha = if (result.passed) 0.2f else 0.5f),
        glowColor = cardGlow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Component & Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    result.componentName.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (result.passed) "PASSED" else "FAILED",
                        color = statusColor,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                result.testName,
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Values Table Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ValueItem(
                    if (isSpanish) "VALOR LEÍDO" else "CURRENT VALUE", 
                    String.format("%.3f %s", result.value, result.unit), 
                    if (result.passed) Color.White else statusColor
                )
                ValueItem(
                    if (isSpanish) "LÍM. MIN" else "MIN LIMIT", 
                    result.minLimit?.let { String.format("%.3f", it) } ?: "---", 
                    MeetColors.textSecondary
                )
                ValueItem(
                    if (isSpanish) "LÍM. MAX" else "MAX LIMIT", 
                    result.maxLimit?.let { String.format("%.3f", it) } ?: "---", 
                    MeetColors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Range Slider representing relative margin to limit
            Mode06VisualRange(
                value = result.value,
                minLimit = result.minLimit,
                maxLimit = result.maxLimit,
                passed = result.passed,
                unit = result.unit,
                isSpanish = isSpanish
            )

            // Pro Tip Section (Expert advice if failure detected)
            if (!result.passed && result.proTip != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(statusColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info, 
                                contentDescription = null, 
                                tint = statusColor, 
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isSpanish) "CONSEJO TÉCNICO EXPERTO" else "EXPERT ADVISE SERVICE", 
                                color = statusColor, 
                                fontWeight = FontWeight.Black, 
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            result.proTip ?: "",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MeetColors.textSecondary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Mode06VisualRange(
    value: Float,
    minLimit: Float?,
    maxLimit: Float?,
    passed: Boolean,
    unit: String,
    isSpanish: Boolean
) {
    val statusColor = if (passed) MeetColors.neonGreen else MeetColors.error
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                
                // Draw Background Track
                drawRoundRect(
                    color = Color(0xFF161616),
                    size = androidx.compose.ui.geometry.Size(w, 6.dp.toPx()),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, cy - 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                
                // Draw allowed zone and value cursor
                if (minLimit != null && maxLimit != null) {
                    val min = minLimit
                    val max = maxLimit
                    val valF = value
                    
                    val range = max - min
                    val pad = if (range > 0f) range * 0.15f else 1f
                    val displayMin = min - pad
                    val displayMax = max + pad
                    val displayRange = displayMax - displayMin
                    
                    val minX = ((min - displayMin) / displayRange) * w
                    val maxX = ((max - displayMin) / displayRange) * w
                    val valX = (((valF - displayMin) / displayRange) * w).coerceIn(0f, w)
                    
                    // Draw green range
                    drawRect(
                        color = MeetColors.neonGreen.copy(alpha = 0.12f),
                        topLeft = androidx.compose.ui.geometry.Offset(minX, cy - 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(maxX - minX, 6.dp.toPx())
                    )
                    
                    // Min boundary line
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(minX, cy - 6.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(minX, cy + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    // Max boundary line
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(maxX, cy - 6.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(maxX, cy + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    // Cursor circle
                    drawCircle(
                        color = statusColor,
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(valX, cy)
                    )
                    drawCircle(
                        color = statusColor.copy(alpha = 0.3f),
                        radius = 10.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(valX, cy)
                    )
                } else if (maxLimit != null) {
                    val max = maxLimit
                    val valF = value
                    val displayMax = max * 1.2f
                    val maxX = (max / displayMax) * w
                    val valX = ((valF / displayMax) * w).coerceIn(0f, w)
                    
                    drawRect(
                        color = MeetColors.neonGreen.copy(alpha = 0.12f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, cy - 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(maxX, 6.dp.toPx())
                    )
                    
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(maxX, cy - 6.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(maxX, cy + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    drawCircle(
                        color = statusColor,
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(valX, cy)
                    )
                    drawCircle(
                        color = statusColor.copy(alpha = 0.3f),
                        radius = 10.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(valX, cy)
                    )
                } else if (minLimit != null) {
                    val min = minLimit
                    val valF = value
                    val displayMin = min * 0.8f
                    val displayMax = if (valF > min) valF * 1.2f else min * 1.5f
                    val range = displayMax - displayMin
                    val minX = ((min - displayMin) / range) * w
                    val valX = (((valF - displayMin) / range) * w).coerceIn(0f, w)
                    
                    drawRect(
                        color = MeetColors.neonGreen.copy(alpha = 0.12f),
                        topLeft = androidx.compose.ui.geometry.Offset(minX, cy - 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(w - minX, 6.dp.toPx())
                    )
                    
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(minX, cy - 6.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(minX, cy + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    
                    drawCircle(
                        color = statusColor,
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(valX, cy)
                    )
                    drawCircle(
                        color = statusColor.copy(alpha = 0.3f),
                        radius = 10.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(valX, cy)
                    )
                } else {
                    drawCircle(
                        color = statusColor,
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(w / 2f, cy)
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val minText = minLimit?.let { String.format("%.3f %s", it, unit) } ?: "---"
            val maxText = maxLimit?.let { String.format("%.3f %s", it, unit) } ?: "---"
            
            Text(
                text = "${if (isSpanish) "Lím. Mín: " else "Min Limit: "}$minText",
                color = MeetColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
            
            Text(
                text = "${if (isSpanish) "Lím. Máx: " else "Max Limit: "}$maxText",
                color = MeetColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun EmptyMonitorsState(isConnected: Boolean, isSpanish: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("🔬", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (isConnected) {
                    if (isSpanish) "Sistemas Listos para Diagnóstico" else "Monitors Ready for Scanning"
                } else {
                    if (isSpanish) "Escáner Desconectado" else "Scanner Disconnected"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isConnected) {
                    if (isSpanish) "El Mode \$06 permite evaluar los resultados de las auto-pruebas internas de la ECU. Pulsa ESCANEAR para iniciar."
                    else "Mode \$06 allows you to review the ECU's self-test results. Press RUN TEST to start the scanning cycle."
                } else {
                    if (isSpanish) "Conecta el adaptador OBD2 para interrogar la ECU y ver los datos internos de fallas."
                    else "Connect the OBD2 adapter to run system scans and pull deep engineering logs."
                },
                color = MeetColors.textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
