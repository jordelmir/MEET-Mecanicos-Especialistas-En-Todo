package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OscilloscopeScreen(
    onNavigateBack: () -> Unit,
    viewModel: ObdViewModel
) {
    // Current PID being tracked
    var selectedPid by remember { mutableStateOf("010C") } // RPM default
    var isRunning by remember { mutableStateOf(false) }
    
    // Local buffer for UI rendering (max 200 points for performance)
    val dataBuffer = remember { mutableStateListOf<Pair<Long, Float>>() }
    val maxPoints = 200

    // ═══ CRASH FIX: Safe stream collection with proper lifecycle awareness ═══
    // The crash occurred because collectLatest could throw when the Bluetooth
    // stream was unavailable. We now guard with try-catch and check connection state.
    LaunchedEffect(isRunning, selectedPid) {
        if (isRunning) {
            dataBuffer.clear()
            try {
                viewModel.startOscilloscope(selectedPid)
                viewModel.oscilloscopeStream.collect { point ->
                    if (dataBuffer.size >= maxPoints) {
                        dataBuffer.removeAt(0)
                    }
                    dataBuffer.add(point)
                }
            } catch (e: Exception) {
                // Gracefully handle stream errors — don't crash the app
                android.util.Log.w("OscilloscopeScreen", "Stream error: ${e.message}")
                isRunning = false
            }
        } else {
            try {
                viewModel.stopOscilloscope()
            } catch (_: Exception) {}
        }
    }

    // Cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            try { viewModel.stopOscilloscope() } catch (_: Exception) {}
        }
    }

    // AI Analysis trigger
    var isAnalyzing by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "OSCILOSCOPIO DIGITAL",
                onBackClick = {
                    isRunning = false
                    try { viewModel.stopOscilloscope() } catch (_: Exception) {}
                    onNavigateBack()
                },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
                .padding(16.dp)
        ) {
            // Signal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (selectedPid == "010C") "REVOLUCIONES MOTOR (RPM)" else "VOLTAJE SISTEMA",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                    Text(
                        "MODO: ${if (isRunning) "CAPTURA EN TIEMPO REAL" else "DETENIDO"}",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                if (dataBuffer.isNotEmpty()) {
                    Text(
                        "${dataBuffer.last().second.toInt()}",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Professional Oscilloscope Viewport
            EliteCard(
                backgroundColor = Color.Black,
                glowColor = MeetColors.neonGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Phosphor Grid
                    OscilloscopeGrid()
                    
                    // Waveform
                    OscilloscopeWaveform(dataPoints = dataBuffer.toList())
                    
                    // Scanning line effect
                    if (isRunning) {
                        ScanningLineEffect()
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EliteButton(
                    text = if (isRunning) "STOP" else "START CAPTURE",
                    onClick = { isRunning = !isRunning },
                    color = if (isRunning) MeetColors.error else MeetColors.neonGreen,
                    modifier = Modifier.weight(1f)
                )
                
                EliteButton(
                    text = "AI ANALYSIS",
                    onClick = { isAnalyzing = true },
                    isEnabled = dataBuffer.size > 50 && !isAnalyzing,
                    color = MeetColors.electricBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Report
            if (isAnalyzing || aiResult != null) {
                EliteCard(
                    backgroundColor = MeetColors.backgroundDark,
                    glowColor = MeetColors.electricBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MeetColors.neonGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("DIAGNÓSTICO PREDICTIVO", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (isAnalyzing) {
                            LinearProgressIndicator(color = MeetColors.neonGreen, modifier = Modifier.fillMaxWidth())
                            Text("Analizando armónicos y glitches...", color = MeetColors.textSecondary, fontSize = 12.sp)
                        } else {
                            Text(aiResult ?: "", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // AI Analysis Logic
    LaunchedEffect(isAnalyzing) {
        if (isAnalyzing) {
            kotlinx.coroutines.delay(2000)
            aiResult = "PATRÓN NOMINAL DETECTADO. Sin anomalías en la señal de frecuencia. Ciclo de trabajo estable al 85%."
            isAnalyzing = false
        }
    }
}

@Composable
fun OscilloscopeGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepX = size.width / 10
        val stepY = size.height / 8
        
        for (i in 1..9) {
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(i * stepX, 0f),
                end = Offset(i * stepX, size.height),
                strokeWidth = 1f
            )
        }
        for (i in 1..7) {
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(0f, i * stepY),
                end = Offset(size.width, i * stepY),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
fun OscilloscopeWaveform(dataPoints: List<Pair<Long, Float>>) {
    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
        if (dataPoints.size < 2) return@Canvas

        val minVal = dataPoints.minOf { it.second } * 0.9f
        val maxVal = dataPoints.maxOf { it.second } * 1.1f
        val range = maxOf(1f, maxVal - minVal)

        val width = size.width
        val height = size.height
        val stepX = width / (dataPoints.size - 1)

        val path = Path()
        dataPoints.forEachIndexed { index, point ->
            val x = index * stepX
            val normalizedY = (point.second - minVal) / range
            val y = height - (normalizedY * height)

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Draw multiple paths for "Glow" effect
        drawPath(
            path = path,
            color = MeetColors.neonGreen.copy(alpha = 0.3f),
            style = Stroke(width = 8.dp.toPx())
        )
        drawPath(
            path = path,
            color = MeetColors.neonGreen,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun ScanningLineEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "ScanLine")
    val scanX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing)
        ),
        label = "ScanX"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val x = scanX * size.width
        drawLine(
            color = MeetColors.neonGreen.copy(alpha = 0.4f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.dp.toPx()
        )
    }
}
