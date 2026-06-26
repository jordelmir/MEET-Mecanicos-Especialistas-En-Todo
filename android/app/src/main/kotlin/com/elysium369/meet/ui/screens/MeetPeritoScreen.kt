package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.elysium369.meet.core.obd.VehicleInspectionReport
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MeetPeritoScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val isInspecting by viewModel.isInspectingPerito.collectAsState()
    val consoleLogs by viewModel.peritoConsoleLogs.collectAsState()
    val activeReport by viewModel.activePeritoReport.collectAsState()
    val history by viewModel.peritoHistory.collectAsState()
    val currentStep by viewModel.currentPeritoStep.collectAsState()
    val currentOdometer by viewModel.currentOdometer.collectAsState()

    val context = LocalContext.current

    // Load history when entering the screen
    LaunchedEffect(activeVehicle) {
        if (activeVehicle != null) {
            viewModel.loadPeritoHistory()
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
                        "MEET PERITO",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Certificador Clínico de Vehículos Usados",
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
                            "Necesitas seleccionar un vehículo de tu Garage para iniciar el peritaje clínico MEET Perito.",
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
                            glowColor = MeetColors.neonGreen,
                            borderColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                            backgroundColor = MeetColors.cardBackground,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${activeVehicle?.make} ${activeVehicle?.model}",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        "VIN: ${activeVehicle?.vin} | ODO: ${currentOdometer.toInt()} KM",
                                        color = MeetColors.textMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                if (!isInspecting) {
                                    Button(
                                        onClick = { viewModel.runMeetPeritoInspection() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("INICIAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (isInspecting) {
                        // Diagnostic console view
                        item {
                            EliteCard(
                                glowColor = MeetColors.neonGreen,
                                borderColor = MeetColors.neonGreen.copy(alpha = 0.4f),
                                backgroundColor = Color.Black,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "CONSOLE CLINIC LINK",
                                            color = MeetColors.neonGreen,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        CircularProgressIndicator(
                                            progress = { currentStep / 10f },
                                            color = MeetColors.neonGreen,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .background(Color.Black)
                                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(8.dp)
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            items(consoleLogs) { log ->
                                                Text(
                                                    log,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Analizando dimensión $currentStep de 10...",
                                        color = MeetColors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (activeReport != null && !isInspecting) {
                        // Display inspection result
                        val report = activeReport!!

                        item {
                            EliteCard(
                                glowColor = when (report.score0to100) {
                                    in 90..100 -> MeetColors.neonGreen
                                    in 80..89 -> MeetColors.cyberCyan
                                    in 60..79 -> MeetColors.warning
                                    else -> MeetColors.error
                                },
                                borderColor = Color.White.copy(alpha = 0.15f),
                                backgroundColor = MeetColors.cardBackground,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "RESULTADO DE EVALUACIÓN",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Score Clock/Tachometer Gauge
                                    Box(
                                        modifier = Modifier.size(160.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ScoreGaugeCanvas(score = report.score0to100)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "${report.score0to100}",
                                                color = Color.White,
                                                fontSize = 42.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                report.category.uppercase(),
                                                color = when (report.score0to100) {
                                                    in 90..100 -> MeetColors.neonGreen
                                                    in 80..89 -> MeetColors.cyberCyan
                                                    in 60..79 -> MeetColors.warning
                                                    else -> MeetColors.error
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // Repair Cost Alert Card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (report.estimatedRepairCost > 0) MeetColors.error.copy(alpha = 0.1f)
                                                else MeetColors.neonGreen.copy(alpha = 0.1f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (report.estimatedRepairCost > 0) MeetColors.error.copy(alpha = 0.2f)
                                                else MeetColors.neonGreen.copy(alpha = 0.2f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Costo Estimado de Reparación:",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "$${report.estimatedRepairCost} USD",
                                            color = if (report.estimatedRepairCost > 0) MeetColors.error else MeetColors.neonGreen,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        report.recommendation,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // Export and share actions
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { viewModel.generatePeritoPdf() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Compartir", tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("COMPARTIR REPORT PDF", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 10 Dimensions details checklist
                        item {
                            EliteCard(
                                glowColor = null,
                                borderColor = Color.White.copy(alpha = 0.1f),
                                backgroundColor = MeetColors.cardBackground,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "DETALLES POR DIMENSIÓN",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    val dims = report.dimensionsDetails.toList()
                                    dims.forEachIndexed { idx, (key, value) ->
                                        val formattedName = when (key) {
                                            "VIN" -> "1. Identificación VIN"
                                            "DTC_ACTIVOS" -> "2. Códigos DTC Activos"
                                            "DTC_PENDIENTES" -> "3. Códigos DTC Pendientes"
                                            "FREEZE_FRAME" -> "4. Registro Freeze Frame"
                                            "FUEL_TRIMS" -> "5. Ajustes de Mezcla (FT)"
                                            "TEMPERATURA" -> "6. Sistema Térmico (ECT)"
                                            "VOLTAJE" -> "7. Voltaje Alternador"
                                            "SENSORES" -> "8. Sensores Críticos"
                                            "KILOMETRAJE" -> "9. Consistencia Km OBD"
                                            "ESTADO_GENERAL" -> "10. Preparación OBD2"
                                            else -> key
                                        }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                        ) {
                                            Text(
                                                formattedName,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                value,
                                                color = MeetColors.textMuted,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                            if (idx < dims.lastIndex) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Divider(color = Color.White.copy(alpha = 0.05f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Certificacion incluida mientras la APK opera con acceso completo temporal.
                        item {
                            EliteCard(
                                glowColor = MeetColors.electricBlue,
                                borderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                                backgroundColor = MeetColors.backgroundDeep,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("✓", color = MeetColors.electricBlue, fontWeight = FontWeight.Black)
                                        Text(
                                            "CERTIFICACIÓN MEET PERITO HABILITADA",
                                            color = MeetColors.electricBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "El sello digital, el PDF imprimible en alta definición y el código QR de verificación quedan incluidos en esta APK sin cobro ni suscripción.",
                                        color = MeetColors.textMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        color = MeetColors.electricBlue.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            "INCLUIDO EN ACCESO COMPLETO",
                                            color = MeetColors.electricBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Historic Log Section
                    if (history.isNotEmpty()) {
                        item {
                            Text(
                                "HISTORIAL DE INSPECCIONES",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                        
                        items(history) { rep ->
                            val sdfHistory = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            val dateStrHistory = sdfHistory.format(Date(rep.createdAt))
                            
                            EliteCard(
                                glowColor = null,
                                borderColor = Color.White.copy(alpha = 0.1f),
                                backgroundColor = MeetColors.cardBackground,
                                shape = RoundedCornerShape(10.dp),
                                onClick = { viewModel.selectPeritoReport(rep) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Reporte #${rep.inspectionId.take(8).uppercase()}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Fecha: $dateStrHistory | Costo: $${rep.estimatedRepairCost} USD",
                                            color = MeetColors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when (rep.score0to100) {
                                                    in 90..100 -> MeetColors.neonGreen.copy(alpha = 0.2f)
                                                    in 80..89 -> MeetColors.cyberCyan.copy(alpha = 0.2f)
                                                    in 60..79 -> MeetColors.warning.copy(alpha = 0.2f)
                                                    else -> MeetColors.error.copy(alpha = 0.2f)
                                                },
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            "${rep.score0to100}",
                                            color = when (rep.score0to100) {
                                                in 90..100 -> MeetColors.neonGreen
                                                in 80..89 -> MeetColors.cyberCyan
                                                in 60..79 -> MeetColors.warning
                                                else -> MeetColors.error
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
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
private fun ScoreGaugeCanvas(score: Int) {
    val excelColor = MeetColors.neonGreen
    val goodColor = MeetColors.cyberCyan
    val attentionColor = MeetColors.warning
    val riskColor = MeetColors.error

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 12.dp.toPx()

        // Draw track base
        drawArc(
            color = Color.White.copy(alpha = 0.1f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw active sweep colored by score range
        val activeColor = when (score) {
            in 90..100 -> excelColor
            in 80..89 -> goodColor
            in 60..79 -> attentionColor
            else -> riskColor
        }

        drawArc(
            color = activeColor,
            startAngle = 180f,
            sweepAngle = (score / 100f) * 180f,
            useCenter = false,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw ticks / calibrations
        val tickCount = 10
        val tickLength = 6.dp.toPx()
        for (i in 0..tickCount) {
            val angleDeg = 180f + (i * 180f / tickCount)
            val angleRad = Math.toRadians(angleDeg.toDouble())
            
            val startX = center.x + (radius - tickLength) * cos(angleRad).toFloat()
            val startY = center.y + (radius - tickLength) * sin(angleRad).toFloat()
            
            val endX = center.x + radius * cos(angleRad).toFloat()
            val endY = center.y + radius * sin(angleRad).toFloat()
            
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // Draw needle
        val sweepAngle = (score / 100f) * 180f
        val needleRad = Math.toRadians((180f + sweepAngle).toDouble())
        val needleLen = radius - 12.dp.toPx()
        val needleEndX = center.x + needleLen * cos(needleRad).toFloat()
        val needleEndY = center.y + needleLen * sin(needleRad).toFloat()

        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = center,
            end = Offset(needleEndX, needleEndY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = center
        )
    }
}
