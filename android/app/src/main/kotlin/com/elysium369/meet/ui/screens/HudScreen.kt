package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val liveData by viewModel.liveData.collectAsState()
    val obdState by viewModel.connectionState.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    
    // States
    var isMirrored by remember { mutableStateOf(true) }
    
    // Fetch live parameters
    val rpm = liveData["010C"]?.toInt() ?: 0
    val speed = liveData["010D"]?.toInt() ?: 0
    val coolantTemp = liveData["0105"]?.toInt() ?: 0
    val throttle = liveData["0111"]?.toInt() ?: 0
    val engineLoad = liveData["0104"]?.toInt() ?: 0
    val voltage = liveData["0142"] ?: liveData["42"] ?: 12.4f

    // Animated Speed and RPM transitions — spring animation ensures the number
    // sweeps through every single integer value like a Waze/premium car speedometer
    val animatedSpeed by animateFloatAsState(
        targetValue = speed.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 35f // Low stiffness = slow, smooth sweep through every number
        ), label = "speed"
    )
    val animatedRpm by animateFloatAsState(
        targetValue = rpm.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 35f
        ), label = "rpm"
    )

    // Pulsing warning if DTCs are present
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val warningAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "warning"
    )

    Scaffold(
        containerColor = Color(0xFF000000), // PURE BLACK background for zero ambient windshield glare
        topBar = {
            // HUD controls are rendered normal (not mirrored) so the driver can easily toggle options
            Column(modifier = Modifier.background(Color(0xFF070B14))) {
                EliteTopAppBar(
                    title = "HUD REFLEJO",
                    subtitle = "Proyección para Parabrisas",
                    onBackClick = { navController.popBackStack() }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMirrored) "MODO ESPEJO: ACTIVADO" else "MODO ESPEJO: DESACTIVADO",
                        color = if (isMirrored) MeetColors.neonGreen else MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { isMirrored = !isMirrored },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMirrored) MeetColors.neonGreen else MeetColors.cardBackgroundLighter,
                            contentColor = if (isMirrored) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("REFLEJAR SCREEN", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Flipped instrument cluster
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = if (isMirrored) -1f else 1f
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                
                // --- Top Status Info ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OBD STATUS",
                            color = MeetColors.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = when (obdState) {
                                ObdState.CONNECTED -> "ONLINE"
                                ObdState.CONNECTING -> "LINKING..."
                                ObdState.NEGOTIATING -> "NEGOTIATING..."
                                else -> "OFFLINE"
                            },
                            color = when (obdState) {
                                ObdState.CONNECTED -> MeetColors.neonGreen
                                ObdState.CONNECTING, ObdState.NEGOTIATING -> MeetColors.warning
                                else -> MeetColors.error
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (activeDtcs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MeetColors.error.copy(alpha = warningAlpha * 0.3f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⚠ DTC ALERT: ${activeDtcs.size}",
                                color = MeetColors.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Text(
                            text = "SYSTEMS OK",
                            color = MeetColors.neonGreen.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // --- Center Gauge (RPM Circular Arc & Digital Speedometer) ---
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Custom Draw RPM Arc
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 14.dp.toPx()
                        val arcRadius = (size.width - strokeWidth) / 2
                        
                        // Background track (semi-circular)
                        drawArc(
                            color = Color(0xFF111111),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        
                        // Filled RPM arc
                        val maxRpm = 8000f
                        val sweepAngle = (animatedRpm / maxRpm).coerceIn(0f, 1f) * 270f
                        
                        // Cyberpunk Neon Gradient
                        val gradient = Brush.sweepGradient(
                            0.0f to MeetColors.neonGreen,
                            0.5f to MeetColors.cyberCyan,
                            0.8f to MeetColors.electricBlue,
                            1.0f to MeetColors.error
                        )
                        
                        drawArc(
                            brush = gradient,
                            startAngle = 135f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Redline indicator tick marks
                        val redlineAngle = 135f + (6000f / maxRpm) * 270f
                        val redlineStart = Offset(
                            center.x + (arcRadius - 10.dp.toPx()) * cos(Math.toRadians(redlineAngle.toDouble())).toFloat(),
                            center.y + (arcRadius - 10.dp.toPx()) * sin(Math.toRadians(redlineAngle.toDouble())).toFloat()
                        )
                        val redlineEnd = Offset(
                            center.x + (arcRadius + 10.dp.toPx()) * cos(Math.toRadians(redlineAngle.toDouble())).toFloat(),
                            center.y + (arcRadius + 10.dp.toPx()) * sin(Math.toRadians(redlineAngle.toDouble())).toFloat()
                        )
                        drawLine(
                            color = MeetColors.error,
                            start = redlineStart,
                            end = redlineEnd,
                            strokeWidth = 3.dp.toPx()
                        )
                    }

                    // Inner Speedometer Layout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = animatedSpeed.toInt().toString(),
                            fontSize = 90.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MeetColors.neonGreen
                        )
                        Text(
                            text = "km/h",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.cyberCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "RPM ${animatedRpm.toInt()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rpm > 6000) MeetColors.error else MeetColors.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // --- Bottom Telemetry Panel (High-Contrast Bars) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Coolant Temperature
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TEMP", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("$coolantTemp°C", color = when {
                            coolantTemp > 105 -> MeetColors.error
                            coolantTemp > 95 -> MeetColors.warning
                            else -> MeetColors.cyberCyan
                        }, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }

                    // Engine Load
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LOAD", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("$engineLoad%", color = MeetColors.neonGreen, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }

                    // Throttle
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TPS", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("$throttle%", color = MeetColors.electricBlue, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }

                    // Battery Voltage
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VOLT", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(String.format("%.1fV", voltage), color = if (voltage < 11.8f) MeetColors.error else MeetColors.warning, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
