package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ScannerPerformanceTab(
    viewModel: ObdViewModel,
    isLandscape: Boolean
) {
    val performanceSnapshot by viewModel.performanceSnapshot.collectAsState()
    val dragStripResult by viewModel.dragStripResult.collectAsState()
    val liveData by viewModel.liveData.collectAsState()

    val gridState = rememberLazyGridState()
    val cols = if (isLandscape) 2 else 1

    // Local peak values for live dyno fallback
    var peakHpObserved by remember { mutableStateOf(0f) }
    var peakTorqueObserved by remember { mutableStateOf(0f) }

    val currentHp = performanceSnapshot?.horsepowerMAF ?: performanceSnapshot?.horsepowerLoad ?: 0f
    val currentTorque = performanceSnapshot?.torqueNm ?: 0f

    LaunchedEffect(currentHp, currentTorque) {
        if (currentHp > peakHpObserved) peakHpObserved = currentHp
        if (currentTorque > peakTorqueObserved) peakTorqueObserved = currentTorque
    }

    EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().eliteScrollbar(gridState)
        ) {
            // ─── DYNAMOMETER SECTION (Live Power/Torque Gauges) ───
            item(span = { GridItemSpan(1) }) {
                EliteCard(
                    backgroundColor = MeetColors.backgroundDark,
                    borderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.neonGreen.copy(alpha = 0.15f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PhantomSectionHeader(label = "DINO CLINIC - POTENCIA", accentColor = MeetColors.neonGreen)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // HP Gauge
                            LiveCanvasGauge(
                                value = currentHp,
                                maxValue = 400f,
                                label = "CORTEX HP",
                                unit = "HP",
                                peakValue = dragStripResult?.peakHp ?: peakHpObserved,
                                activeColor = MeetColors.neonGreen
                            )

                            // Torque Gauge
                            LiveCanvasGauge(
                                value = currentTorque,
                                maxValue = 500f,
                                label = "TORQUE",
                                unit = "Nm",
                                peakValue = dragStripResult?.peakTorque ?: peakTorqueObserved,
                                activeColor = MeetColors.electricBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Live Readings Footer Table
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("RPM", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${(liveData["010C"] ?: liveData["RPM"] ?: 0f).toInt()}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                            Divider(modifier = Modifier.width(1.dp).height(24.dp).align(Alignment.CenterVertically), color = MeetColors.borderSubtle)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MAF (g/s)", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(String.format("%.1f", liveData["0110"] ?: liveData["MAF"] ?: 0f), color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                            Divider(modifier = Modifier.width(1.dp).height(24.dp).align(Alignment.CenterVertically), color = MeetColors.borderSubtle)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CARGA", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(String.format("%.1f%%", liveData["0104"] ?: liveData["LOAD"] ?: 0f), color = MeetColors.electricBlue, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // ─── G-FORCE & TURBO BOOST / AFR SECTION ───
            item(span = { GridItemSpan(1) }) {
                EliteCard(
                    backgroundColor = MeetColors.backgroundDark,
                    borderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.electricBlue.copy(alpha = 0.15f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PhantomSectionHeader(label = "TELEMETRÍA DE COMPRESIÓN", accentColor = MeetColors.electricBlue)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // G-Force Vector Graph
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ACELERÓMETRO G", color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                LiveGForceVector(
                                    accelX = 0f, // Lateral (OBD can be enhanced later)
                                    accelY = performanceSnapshot?.gForce ?: 0f
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = String.format("Longitudinal: %.2f G", performanceSnapshot?.gForce ?: 0f),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Vertical LED Bars (Boost & AFR)
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val boost = performanceSnapshot?.boostPsiEstimate ?: 0f
                                val afr = performanceSnapshot?.airFuelRatio ?: 14.7f

                                LedVerticalBar(
                                    label = "TURBO BOOST",
                                    value = boost,
                                    maxVal = 30f,
                                    unit = "PSI",
                                    activeColor = MeetColors.cyberCyan
                                )

                                LedVerticalBar(
                                    label = "MEZCLA AIRE/NAFTA (AFR)",
                                    value = afr,
                                    maxVal = 20f,
                                    unit = "AFR",
                                    activeColor = MeetColors.warning
                                )
                            }
                        }
                    }
                }
            }

            // ─── DRAG STRIP INTERACTIVE TESTING (1/4 Mile & 0-60 mph) ───
            item(span = { GridItemSpan(cols) }) {
                EliteCard(
                    backgroundColor = MeetColors.backgroundDark,
                    borderColor = MeetColors.cyberCyan.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.cyberCyan.copy(alpha = 0.15f)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        PhantomSectionHeader(label = "PISTA DE ACELERACIÓN - DRAG STRIP", accentColor = MeetColors.cyberCyan)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls and Christmas Tree row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tree and Active indicator
                            Column(modifier = Modifier.weight(1.2f)) {
                                DragChristmasTree(isRunning = dragStripResult?.isRunning ?: false)
                            }

                            // Action buttons
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val isRunning = dragStripResult?.isRunning ?: false
                                
                                EliteButton(
                                    text = if (isRunning) "PARAR PRUEBA" else "INICIAR ARRASTRE",
                                    onClick = {
                                        if (isRunning) {
                                            viewModel.stopDragStrip()
                                        } else {
                                            viewModel.startDragStrip()
                                        }
                                    },
                                    color = if (isRunning) MeetColors.error else MeetColors.cyberCyan,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = if (isRunning) "● PRUEBA EN PROGRESO" else "STANDBY PARA ARRANQUE",
                                    color = if (isRunning) MeetColors.error else MeetColors.textMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Drag Results Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DragResultItem(
                                modifier = Modifier.weight(1f),
                                label = "0-60 mph (0-96 km/h)",
                                value = dragStripResult?.zeroTo60mph,
                                unit = "s",
                                color = MeetColors.neonGreen
                            )
                            DragResultItem(
                                modifier = Modifier.weight(1f),
                                label = "0-100 km/h",
                                value = dragStripResult?.zeroTo100kph,
                                unit = "s",
                                color = MeetColors.neonGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DragResultItem(
                                modifier = Modifier.weight(1f),
                                label = "Tiempo 1/4 Milla",
                                value = dragStripResult?.quarterMileTime,
                                unit = "s",
                                color = MeetColors.electricBlue
                            )
                            DragResultItem(
                                modifier = Modifier.weight(1f),
                                label = "Velocidad Final 1/4",
                                value = dragStripResult?.quarterMileSpeed,
                                unit = "km/h",
                                color = MeetColors.electricBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DragResultItem(
                                modifier = Modifier.weight(1f),
                                label = "Velocidad Máxima",
                                value = dragStripResult?.topSpeedReached,
                                unit = "km/h",
                                color = MeetColors.cyberCyan
                            )
                            DragResultItem(
                                modifier = Modifier.weight(1f),
                                label = "Caballos Pico (Drag)",
                                value = dragStripResult?.peakHp,
                                unit = "HP",
                                color = MeetColors.cyberCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// LIVE CANVAS GAUGE COMPONENT
// ═══════════════════════════════════════

@Composable
fun LiveCanvasGauge(
    value: Float,
    maxValue: Float,
    label: String,
    unit: String,
    peakValue: Float,
    activeColor: Color
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, maxValue),
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.6f),
        label = "gaugeValue"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(130.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 * 0.85f

            // Inner background dial ring
            drawArc(
                color = Color.DarkGray.copy(alpha = 0.25f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Dynamic Active Dial Arc
            val sweep = (animatedValue / maxValue) * 270f
            drawArc(
                brush = Brush.sweepGradient(
                    0f to activeColor.copy(alpha = 0.2f),
                    0.5f to activeColor.copy(alpha = 0.7f),
                    1f to activeColor
                ),
                startAngle = 135f,
                sweepAngle = sweep.coerceAtLeast(1f),
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Peak Marker (Thin red or white tick at maximum value)
            if (peakValue > 0) {
                val peakAngle = 135f + ((peakValue.coerceIn(0f, maxValue) / maxValue) * 270f)
                val angleRad = (peakAngle * PI / 180f).toFloat()
                val peakStart = Offset(
                    center.x + (radius - 12.dp.toPx()) * cos(angleRad),
                    center.y + (radius - 12.dp.toPx()) * sin(angleRad)
                )
                val peakEnd = Offset(
                    center.x + (radius + 2.dp.toPx()) * cos(angleRad),
                    center.y + (radius + 2.dp.toPx()) * sin(angleRad)
                )
                drawLine(
                    color = MeetColors.error,
                    start = peakStart,
                    end = peakEnd,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Central Dial pointer circle
            drawCircle(
                color = MeetColors.backgroundDeep,
                radius = radius * 0.65f
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = String.format("%.0f", value),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = unit,
                color = activeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = MeetColors.textMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════
// LIVE G-FORCE ACCELEROMETER COMPONENT
// ═══════════════════════════════════════

@Composable
fun LiveGForceVector(
    accelX: Float,
    accelY: Float
) {
    Canvas(modifier = Modifier.size(96.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        // Radar grid circles
        drawCircle(color = MeetColors.borderSubtle.copy(alpha = 0.15f), radius = radius, style = Stroke(0.5.dp.toPx()))
        drawCircle(color = MeetColors.borderSubtle.copy(alpha = 0.15f), radius = radius * 0.66f, style = Stroke(0.5.dp.toPx()))
        drawCircle(color = MeetColors.borderSubtle.copy(alpha = 0.15f), radius = radius * 0.33f, style = Stroke(0.5.dp.toPx()))

        // Axis crosshairs
        drawLine(color = MeetColors.borderSubtle.copy(alpha = 0.2f), start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 0.5.dp.toPx())
        drawLine(color = MeetColors.borderSubtle.copy(alpha = 0.2f), start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 0.5.dp.toPx())

        // Laser vector dot (limits movement to edge boundary)
        val maxG = 1.0f
        val dotX = center.x + (accelX / maxG).coerceIn(-1f, 1f) * radius
        val dotY = center.y - (accelY / maxG).coerceIn(-1f, 1f) * radius

        // Vector line from center
        drawLine(
            color = MeetColors.neonGreen.copy(alpha = 0.4f),
            start = center,
            end = Offset(dotX, dotY),
            strokeWidth = 1.5.dp.toPx()
        )

        // Pulsating radar dot
        drawCircle(
            color = MeetColors.neonGreen,
            radius = 5.dp.toPx(),
            center = Offset(dotX, dotY)
        )
        drawCircle(
            color = MeetColors.neonGreen.copy(alpha = 0.2f),
            radius = 10.dp.toPx(),
            center = Offset(dotX, dotY),
            style = Stroke(1.dp.toPx())
        )
    }
}

// ═══════════════════════════════════════
// VERTICAL LED BAR COMPONENT
// ═══════════════════════════════════════

@Composable
fun LedVerticalBar(
    label: String,
    value: Float,
    maxVal: Float,
    unit: String,
    activeColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MeetColors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.1f %s", value, unit), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .border(0.5.dp, MeetColors.borderSubtle.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            val ratio = (value / maxVal).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(activeColor.copy(alpha = 0.5f), activeColor)
                        ),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

// ═══════════════════════════════════════
// DRAG STRIP CHRISTMAS TREE LIGHTS
// ═══════════════════════════════════════

@Composable
fun DragChristmasTree(isRunning: Boolean) {
    var state by remember { mutableIntStateOf(0) } // 0: Idle, 1: Pre-stage, 2: Yellow, 3: Green

    LaunchedEffect(isRunning) {
        if (isRunning) {
            state = 1
            delay(800L)
            state = 2
            delay(800L)
            state = 3
        } else {
            state = 0
        }
    }

    Row(
        modifier = Modifier.background(Color(0xFF0F0F0F), RoundedCornerShape(10.dp)).border(0.5.dp, Color.DarkGray, RoundedCornerShape(10.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pre-Stage (White)
        TreeLight(isOn = state >= 1, color = Color.White)
        // Stage (Yellow 1)
        TreeLight(isOn = state >= 2, color = MeetColors.warning)
        // Stage (Yellow 2)
        TreeLight(isOn = state >= 2, color = MeetColors.warning)
        // Go (Green)
        TreeLight(isOn = state >= 3, color = MeetColors.neonGreen)
    }
}

@Composable
fun TreeLight(isOn: Boolean, color: Color) {
    val animAlpha by animateFloatAsState(
        targetValue = if (isOn) 1f else 0.15f,
        animationSpec = tween(150),
        label = "lightAlpha"
    )
    val glowSize by animateDpAsState(
        targetValue = if (isOn) 8.dp else 0.dp,
        animationSpec = tween(200),
        label = "lightGlow"
    )

    Box(
        modifier = Modifier
            .size(24.dp)
            .shadow(glowSize, RoundedCornerShape(50), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = animAlpha))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50))
    )
}

// ═══════════════════════════════════════
// DRAG RESULT DISPLAY ITEM
// ═══════════════════════════════════════

@Composable
fun DragResultItem(
    modifier: Modifier = Modifier,
    label: String,
    value: Float?,
    unit: String,
    color: Color
) {
    EliteCard(
        backgroundColor = Color.Black.copy(alpha = 0.25f),
        borderColor = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                color = MeetColors.textMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (value != null) String.format("%.2f", value) else "--.--",
                    color = if (value != null) Color.White else Color.Gray,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                if (value != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
