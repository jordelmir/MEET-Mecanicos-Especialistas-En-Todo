package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import com.elysium369.meet.core.obd.ObdState

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MeetDnaScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val dnaResult by viewModel.dnaResult.collectAsState()
    val isTraining by viewModel.isTrainingDna.collectAsState()
    val twinAnomalies by viewModel.twinAnomalies.collectAsState()

    val context = LocalContext.current

    // Trigger initial evaluation on enter
    LaunchedEffect(activeVehicle) {
        if (activeVehicle != null) {
            viewModel.evaluateDnaInference()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Header ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "MEET DNA",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Firma Matemática de Comportamiento del Vehículo",
                        color = MeetColors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            if (activeVehicle == null) {
                // No selected vehicle state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            "SELECCIONA UN VEHÍCULO",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Necesitas seleccionar un vehículo de tu Garage para ver su firma digital MEET DNA.",
                            color = MeetColors.textMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { navController.navigate("garage") },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                        ) {
                            Text("IR AL GARAGE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Vehicle Info Header Card
                    item {
                        EliteCard(
                            glowColor = if (dnaResult.isAnomalous) Color(0xFFFF4D4D) else Color(0xFF00FFCC),
                            borderColor = (if (dnaResult.isAnomalous) Color(0xFFFF4D4D) else Color(0xFF00FFCC)).copy(alpha = 0.15f),
                            backgroundColor = MeetColors.cardBackground,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${activeVehicle?.make} ${activeVehicle?.model}".uppercase(),
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "VIN: ${activeVehicle?.vin ?: "DESCONOCIDO"}",
                                        color = MeetColors.textMuted,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                // Connection Status badge
                                val isConnected = connectionState == ObdState.CONNECTED
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isConnected) MeetColors.neonGreen.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isConnected) MeetColors.neonGreen.copy(alpha = 0.3f) else Color.Red.copy(alpha = 0.3f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (isConnected) "CONECTADO OBD2" else "DESCONECTADO",
                                        color = if (isConnected) MeetColors.neonGreen else Color.Red,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // DNA Helix Animation Card
                    item {
                        EliteCard(
                            glowColor = if (dnaResult.isAnomalous) Color(0xFFFF4D4D) else Color(0xFF00FFCC),
                            borderColor = Color.White.copy(alpha = 0.05f),
                            backgroundColor = MeetColors.cardBackground,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "FIRMA MATEMÁTICA EN TIEMPO REAL",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                DnaHelixAnimation(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    isAnomalous = dnaResult.isAnomalous,
                                    healthScore = dnaResult.healthScore
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Health Score and Anomaly Level
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "HEALTH SCORE",
                                            color = MeetColors.textMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            if (dnaResult.isCalibrated) "${dnaResult.healthScore}%" else "--",
                                            color = when {
                                                !dnaResult.isCalibrated -> MeetColors.textMuted
                                                dnaResult.healthScore >= 90 -> MeetColors.neonGreen
                                                dnaResult.healthScore >= 80 -> MeetColors.cyberCyan
                                                dnaResult.healthScore >= 60 -> Color(0xFFFFB300)
                                                else -> Color(0xFFFF4D4D)
                                            },
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "ANOMALY SCORE",
                                            color = MeetColors.textMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            if (dnaResult.isCalibrated) String.format("%.2f", dnaResult.anomalyScore) else "--",
                                            color = if (dnaResult.isAnomalous) Color(0xFFFF4D4D) else Color(0xFF00FFCC),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "CALIBRACIÓN",
                                            color = MeetColors.textMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            if (dnaResult.isCalibrated) "${dnaResult.confidence.toInt()}%" else "NO APTO",
                                            color = if (dnaResult.isCalibrated) MeetColors.cyberCyan else Color.Red,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Anomaly Alert Box if applicable
                    if (dnaResult.isCalibrated && dnaResult.isAnomalous) {
                        item {
                            val pulseTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseAlpha by pulseTransition.animateFloat(
                                initialValue = 0.1f,
                                targetValue = 0.35f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Red.copy(alpha = pulseAlpha), RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.Red.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Alerta",
                                        tint = Color.Red,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            "ALERTA PREVENTIVA DNA",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            dnaResult.message,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Calibration Action Card
                    item {
                        EliteCard(
                            glowColor = MeetColors.cyberCyan,
                            borderColor = Color.White.copy(alpha = 0.05f),
                            backgroundColor = MeetColors.cardBackground,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (!dnaResult.isCalibrated) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Calibrar",
                                        tint = MeetColors.cyberCyan,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "FIRMA NO CALIBRADA",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Se requieren registrar al menos 50 lecturas normales de sensores OBD2 (RPM, temperatura, voltajes, etc.) durante la conducción para generar la firma única de este vehículo.",
                                        color = MeetColors.textMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        "FIRMA BASAL REGISTRADA",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "El bosque de aislamiento local y el perfil Z-Score están entrenados y evaluando la telemetría del motor en tiempo real. Confianza: ${String.format("%.1f", dnaResult.confidence)}%.",
                                        color = MeetColors.textMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                if (isTraining) {
                                    CircularProgressIndicator(color = MeetColors.cyberCyan, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Entrenando bosque de aislamiento local...", color = MeetColors.textMuted, fontSize = 11.sp)
                                } else {
                                    Button(
                                        onClick = { viewModel.trainVehicleDna() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (dnaResult.isCalibrated) Color.DarkGray else MeetColors.cyberCyan
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Entrenar",
                                            tint = if (dnaResult.isCalibrated) Color.White else Color.Black
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (dnaResult.isCalibrated) "RE-ENTRENAR FIRMA DNA" else "CALIBRAR FIRMA DNA",
                                            color = if (dnaResult.isCalibrated) Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // DNA Sensors Header
                    item {
                        Text(
                            "7 SENSORES CLÍNICOS DNA",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // 7 Sensors Detail list
                    if (!dnaResult.isCalibrated || dnaResult.sensorStates.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MeetColors.cardBackground, RoundedCornerShape(14.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Esperando calibración o conexión OBD2 activa para mostrar desviaciones estadísticas.",
                                    color = MeetColors.textMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(dnaResult.sensorStates) { state ->
                            val unit = when (state.pid) {
                                "0105", "010F" -> "°C"
                                "0142" -> "V"
                                "010C" -> "RPM"
                                "0107" -> "%"
                                "010B" -> "kPa"
                                "0110" -> "g/s"
                                else -> ""
                            }
                            
                            val isSensorAnomalous = abs(state.zScore) > 3.0f
                            val isSensorWarning = abs(state.zScore) > 2.0f

                            EliteCard(
                                glowColor = when {
                                    isSensorAnomalous -> Color(0xFFFF4D4D)
                                    isSensorWarning -> Color(0xFFFFB300)
                                    else -> MeetColors.neonGreen
                                },
                                borderColor = Color.White.copy(alpha = 0.05f),
                                backgroundColor = MeetColors.cardBackground,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            state.label.uppercase(),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "PID: ${state.pid} | Basal: ${String.format("%.1f", state.baselineMean)} $unit",
                                            color = MeetColors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            "${String.format("%.1f", state.currentValue)} $unit",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Z-Score: ${if (state.zScore >= 0) "+" else ""}${String.format("%.2f", state.zScore)} SD",
                                            color = when {
                                                isSensorAnomalous -> Color(0xFFFF4D4D)
                                                isSensorWarning -> Color(0xFFFFB300)
                                                else -> MeetColors.neonGreen
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Digital Twin Anomaly Timeline Header
                    if (twinAnomalies.isNotEmpty()) {
                        item {
                            Text(
                                "HISTORIAL DE ANOMALÍAS DEL GEMELO DIGITAL",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        items(twinAnomalies) { anomaly ->
                            EliteCard(
                                glowColor = if (anomaly.severity == "HIGH") Color(0xFFFF4D4D) else Color(0xFFFFB300),
                                borderColor = Color.White.copy(alpha = 0.05f),
                                backgroundColor = MeetColors.cardBackground,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(anomaly.parameter.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(anomaly.severity, color = if (anomaly.severity == "HIGH") Color(0xFFFF4D4D) else Color(0xFFFFB300), fontWeight = FontWeight.Black, fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Esperado: ${String.format("%.1f", anomaly.expectedValue)} | Actual: ${String.format("%.1f", anomaly.actualValue)} | Desviación: ${String.format("%.1f", anomaly.deviation)}",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(anomaly.timestamp))
                                    Text("Detectado: $formattedTime | Confianza: ${anomaly.confidence.toInt()}%", color = MeetColors.textMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Early Warning Predictions
                    val alternatorDrift = twinAnomalies.any { it.parameter == "Battery Voltage" && it.severity == "HIGH" }
                    val coolantRise = twinAnomalies.any { it.parameter == "Coolant Temperature" && it.severity == "HIGH" }

                    if (alternatorDrift || coolantRise) {
                        item {
                            Text(
                                "PREDICCIONES DE ALERTA TEMPRANA",
                                color = Color(0xFFFFB300),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        if (alternatorDrift) {
                            item {
                                EliteCard(
                                    glowColor = Color(0xFFFFB300),
                                    backgroundColor = MeetColors.cardBackground,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = "Alerta", tint = Color(0xFFFFB300))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("DEGRADACIÓN DEL ALTERNADOR / BATERÍA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("El gemelo digital detecta una tendencia descendente en voltaje regulado. Posible falla de alternador en 15-30 días.", color = MeetColors.textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        if (coolantRise) {
                            item {
                                EliteCard(
                                    glowColor = Color(0xFFFFB300),
                                    backgroundColor = MeetColors.cardBackground,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = "Alerta", tint = Color(0xFFFFB300))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("SISTEMA DE ENFRIAMIENTO COMPROMETIDO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Incremento inusual y desviado en la temperatura. Revise posibles fugas de refrigerante o termostato trabado.", color = MeetColors.textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DnaHelixAnimation(
    modifier: Modifier = Modifier,
    isAnomalous: Boolean,
    healthScore: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dna")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val colorHelix = if (isAnomalous) Color(0xFFFF4D4D) else Color(0xFF00FFCC)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val amplitude = 24.dp.toPx()
        val wavelength = 120.dp.toPx()
        val numPoints = 20

        for (i in 0..numPoints) {
            val x = (i.toFloat() / numPoints) * width
            val angle1 = (x / wavelength) * 2f * Math.PI.toFloat() + phase
            val angle2 = angle1 + Math.PI.toFloat()

            val y1 = centerY + sin(angle1) * amplitude
            val y2 = centerY + sin(angle2) * amplitude

            // Draw rung connecting the two strands
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(x, y1),
                end = Offset(x, y2),
                strokeWidth = 2.dp.toPx()
            )

            // Draw strand 1 node
            drawCircle(
                color = colorHelix,
                radius = 5.dp.toPx(),
                center = Offset(x, y1)
            )

            // Draw strand 2 node
            drawCircle(
                color = colorHelix.copy(alpha = 0.6f),
                radius = 4.dp.toPx(),
                center = Offset(x, y2)
            )
        }
    }
}
