package com.elysium369.meet.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ActiveTest
import com.elysium369.meet.core.obd.ActiveTestStatus
import com.elysium369.meet.core.obd.PidRegistry
import com.elysium369.meet.core.obd.SafetyCondition
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun ActiveTestsScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val status by viewModel.activeTestStatus.collectAsState()
    val availableTests = viewModel.availableActiveTests
    
    var expandedTestId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "PRUEBAS ACTIVAS ELITE",
                subtitle = "CONTROL BIDIRECCIONAL & TELEMETRÍA",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            // Current Active Test / Final Report Card
            if (status.isActive || status.testId != null) {
                ActiveTestProgressCard(
                    status = status,
                    viewModel = viewModel,
                    onStop = { viewModel.stopActiveTest() },
                    onClose = { viewModel.clearActiveTestStatus() }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            PhantomSectionHeader(label = "Controles Disponibles")
            Text(
                "Active actuadores mecánicos directamente desde el bus OBD para verificar su correcto funcionamiento físico.",
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(availableTests) { test ->
                    ActiveTestItem(
                        test = test,
                        isEnabled = !status.isActive,
                        isExpanded = expandedTestId == test.id,
                        onExpandClick = {
                            expandedTestId = if (expandedTestId == test.id) null else test.id
                        },
                        onStartClick = {
                            viewModel.runActiveTest(test)
                            expandedTestId = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveTestItem(
    test: ActiveTest,
    isEnabled: Boolean,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onStartClick: () -> Unit
) {
    val borderColor = if (isExpanded) MeetColors.neonGreen else MeetColors.borderSubtle.copy(alpha = 0.2f)
    val glow = if (isExpanded) MeetColors.neonGreen else null
    
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = borderColor,
        glowColor = glow,
        shape = RoundedCornerShape(14.dp),
        onClick = onExpandClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(test.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(test.description, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    if (test.manufacturer != null) {
                        Text(
                            "Específico: ${test.manufacturer}",
                            color = MeetColors.neonGreen,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Text(
                    text = if (isExpanded) "▼" else "▶", 
                    color = if (isEnabled) MeetColors.neonGreen else MeetColors.borderSubtle
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MeetColors.borderSubtle.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Safety checklist
                Text(
                    "REQUISITOS DE SEGURIDAD:",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (test.safetyConditions.isEmpty()) {
                    Text("• Ninguno detectado (Prueba de uso general)", color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    test.safetyConditions.forEach { cond ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text("✓ ", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                            Text(getSafetyConditionName(cond), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Technical Workshop Guide
                Text(
                    "GUÍA TÉCNICA DE TALLER:",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = getTechnicalGuide(test.id),
                    color = MeetColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Confirmation button
                if (isEnabled) {
                    EliteButton(
                        text = "CONFIRMAR Y EJECUTAR",
                        onClick = onStartClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    EliteButton(
                        text = "CONFIRMAR Y EJECUTAR",
                        onClick = {},
                        isEnabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Hay otra prueba activa en progreso.",
                        color = MeetColors.error,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveTestProgressCard(
    status: ActiveTestStatus,
    viewModel: com.elysium369.meet.ui.ObdViewModel,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val aiResult by viewModel.aiActiveTestResult.collectAsState()
    val isAiLoading by viewModel.isAiActiveTestLoading.collectAsState()

    val accentColor = if (status.isActive) MeetColors.neonGreen else MeetColors.cyberCyan
    val glowColor = if (status.isActive) MeetColors.neonGreen else MeetColors.cyberCyan
    
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = accentColor,
        glowColor = glowColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header block
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status.isActive) {
                    CircularProgressIndicator(
                        progress = status.progress,
                        color = accentColor,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Text("✓", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = status.message,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (status.isActive) {
                    EliteIconButton(
                        icon = { Text("⏹", color = MeetColors.error, fontWeight = FontWeight.Bold) },
                        onClick = onStop,
                        glowColor = MeetColors.error,
                        modifier = Modifier.background(MeetColors.error.copy(alpha = 0.1f), CircleShape)
                    )
                } else {
                    EliteIconButton(
                        icon = { Text("✕", color = MeetColors.textSecondary, fontWeight = FontWeight.Bold) },
                        onClick = onClose,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actuator Visualizer (Canvas animations)
            status.testId?.let { testId ->
                ActuatorVisualizer(testId = testId, progress = status.progress, values = status.currentValues)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Monitored PIDs
            if (status.currentValues.isNotEmpty()) {
                Text(
                    "TELEMETRÍA EN VIVO:",
                    color = MeetColors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    status.currentValues.forEach { (name, value) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .border(1.dp, MeetColors.borderSubtle.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(name, color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = String.format("%.1f", value),
                                color = accentColor,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Linear Progress Bar
            LinearProgressIndicator(
                progress = status.progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.05f)
            )

            // AI Diagnostics Section
            if (!status.isActive && status.testId != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MeetColors.borderSubtle.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                if (aiResult == null && !isAiLoading) {
                    EliteButton(
                        text = "🧠 ANALIZAR RESULTADO CON IA",
                        color = MeetColors.cyberCyan,
                        onClick = {
                            val testName = PidRegistry.ACTIVE_TESTS.find { it.id == status.testId }?.name ?: status.testId
                            viewModel.runActiveTestAiDiagnostic(testName, status.testId, status.currentValues)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (isAiLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MeetColors.cyberCyan, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Evaluando telemetría del actuador con Elysium AI...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    aiResult?.let { result ->
                        Text(
                            "DIAGNÓSTICO ELYSIUM VANGUARD AI:",
                            color = MeetColors.cyberCyan,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        StyledMarkdownText(text = result, accentColor = MeetColors.cyberCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun ActuatorVisualizer(testId: String, progress: Float, values: Map<String, Float>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .border(1.dp, MeetColors.borderSubtle.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (testId) {
            "FUEL_PUMP", "INJECTOR_BALANCE" -> FuelPressureVisualizer(values)
            "COOLING_FAN_LOW", "COOLING_FAN_HIGH" -> CoolingFanVisualizer(testId, progress)
            "EGR_VALVE", "EVAP_VENT", "EVAP_PURGE", "SECONDARY_AIR", "TCC_SOLENOID", "TURBO_WASTEGATE" -> SolenoidVisualizer(testId, progress, values)
            "THROTTLE_BODY", "IDLE_SPEED_UP" -> ThrottleBodyVisualizer(values)
            "AC_COMPRESSOR" -> AcCompressorVisualizer(values)
            "GLOW_PLUGS" -> GlowPlugVisualizer(progress)
            else -> GenericActuatorVisualizer(progress)
        }
    }
}

@Composable
fun FuelPressureVisualizer(values: Map<String, Float>) {
    val pressure = values["Presión Comb."] ?: 0f
    val animatedPressure by animateFloatAsState(
        targetValue = pressure,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pressure_needle"
    )
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val center = Offset(size.width / 2, size.height * 0.85f)
            val radius = size.height * 0.7f
            val startAngle = 150f
            val sweepAngle = 240f
            
            // Draw dial arc
            drawArc(
                color = Color.DarkGray,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            
            drawArc(
                color = MeetColors.cyberCyan.copy(alpha = 0.3f),
                startAngle = startAngle,
                sweepAngle = sweepAngle * (animatedPressure / 500f).coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Needle angle
            val needleAngle = startAngle + sweepAngle * (animatedPressure / 500f).coerceIn(0f, 1f)
            val angleRad = Math.toRadians(needleAngle.toDouble())
            val needleLength = radius * 0.95f
            val needleEnd = Offset(
                (center.x + needleLength * Math.cos(angleRad)).toFloat(),
                (center.y + needleLength * Math.sin(angleRad)).toFloat()
            )
            
            // Ticks
            val numTicks = 6
            for (i in 0 until numTicks) {
                val tickAngle = startAngle + (sweepAngle / (numTicks - 1)) * i
                val tickRad = Math.toRadians(tickAngle.toDouble())
                val tickStart = Offset(
                    (center.x + (radius * 0.85f) * Math.cos(tickRad)).toFloat(),
                    (center.y + (radius * 0.85f) * Math.sin(tickRad)).toFloat()
                )
                val tickEnd = Offset(
                    (center.x + radius * Math.cos(tickRad)).toFloat(),
                    (center.y + radius * Math.sin(tickRad)).toFloat()
                )
                drawLine(
                    color = Color.Gray,
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Draw central hub
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = center
            )
            
            // Draw needle
            drawLine(
                color = MeetColors.error,
                start = center,
                end = needleEnd,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) {
            Text(
                text = "${pressure.toInt()} kPa",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
            Text(
                text = String.format("%.1f PSI", pressure * 0.145038f),
                color = MeetColors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun CoolingFanVisualizer(testId: String, progress: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "fan_rotation")
    val speedDuration = if (testId == "COOLING_FAN_HIGH") 500 else 1000
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(speedDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fan_angle"
    )
    
    Canvas(modifier = Modifier.size(110.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2
        
        // Outer ring
        drawCircle(
            color = Color.White.copy(alpha = 0.1f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        
        drawCircle(
            color = MeetColors.neonGreen.copy(alpha = 0.15f),
            radius = radius - 6.dp.toPx(),
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Draw blades
        rotate(rotationAngle, pivot = center) {
            val numBlades = 5
            for (i in 0 until numBlades) {
                val bladeAngle = (360f / numBlades) * i
                rotate(bladeAngle, pivot = center) {
                    drawOval(
                        color = MeetColors.neonGreen.copy(alpha = 0.7f),
                        topLeft = Offset(center.x - 10.dp.toPx(), center.y - radius + 6.dp.toPx()),
                        size = Size(20.dp.toPx(), radius * 0.85f)
                    )
                }
            }
        }
        
        // Center hub
        drawCircle(
            color = Color.Black,
            radius = 14.dp.toPx(),
            center = center
        )
        drawCircle(
            color = MeetColors.neonGreen,
            radius = 6.dp.toPx(),
            center = center
        )
    }
}

@Composable
fun SolenoidVisualizer(testId: String, progress: Float, values: Map<String, Float>) {
    val transition = rememberInfiniteTransition(label = "solenoid_cycle")
    val pistonOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "piston"
    )
    
    val activeColor = MeetColors.electricBlue
    
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        // Draw Coil windings background
        val coilLeft = width * 0.25f
        val coilRight = width * 0.65f
        val coilWidth = coilRight - coilLeft
        val coilHeight = height * 0.7f
        val coilTop = centerY - coilHeight / 2
        
        drawRoundRect(
            color = Color.DarkGray.copy(alpha = 0.4f),
            topLeft = Offset(coilLeft, coilTop),
            size = Size(coilWidth, coilHeight),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )
        
        // Windings (simulated copper loops)
        val numWindings = 10
        for (i in 0 until numWindings) {
            val windingX = coilLeft + (coilWidth / numWindings) * i
            drawLine(
                color = activeColor.copy(alpha = 0.2f + 0.8f * pistonOffset),
                start = Offset(windingX, coilTop),
                end = Offset(windingX, coilTop + coilHeight),
                strokeWidth = 3.dp.toPx()
            )
        }
        
        // Plunger rod
        val plungerLength = width * 0.35f
        val plungerHeight = height * 0.25f
        val plungerTop = centerY - plungerHeight / 2
        val plungerLeft = coilLeft + (width * 0.05f) + (width * 0.18f) * pistonOffset
        
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(plungerLeft, plungerTop),
            size = Size(plungerLength, plungerHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        
        // Flux lines (electromagnetic field arcs)
        if (pistonOffset > 0.4f) {
            val fluxRadius = height * 0.45f
            drawArc(
                color = activeColor.copy(alpha = (pistonOffset - 0.4f) * 0.8f),
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(coilRight + 12.dp.toPx(), centerY - fluxRadius),
                size = Size(fluxRadius, fluxRadius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun ThrottleBodyVisualizer(values: Map<String, Float>) {
    val throttlePos = values["Pos. Mariposa"] ?: 0f
    val openFraction = (throttlePos / 25f).coerceIn(0f, 1f)
    
    val animatedOpen by animateFloatAsState(
        targetValue = openFraction,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "throttle_open"
    )
    
    val flowTransition = rememberInfiniteTransition(label = "air_flow")
    val flowOffset by flowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow"
    )

    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val boreRadius = size.height * 0.45f
        
        // Outer Bore
        drawCircle(
            color = Color.DarkGray,
            radius = boreRadius,
            center = center,
            style = Stroke(width = 6.dp.toPx())
        )
        
        drawCircle(
            color = MeetColors.cyberCyan.copy(alpha = 0.15f),
            radius = boreRadius - 3.dp.toPx(),
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        
        // Butterfly plate (3D rotating disc)
        val plateWidth = boreRadius * 1.75f
        val plateHeight = boreRadius * 1.75f * (1f - animatedOpen * 0.92f)
        
        drawOval(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(center.x - plateWidth / 2, center.y - plateHeight / 2),
            size = Size(plateWidth, plateHeight)
        )
        
        // Shaft pin
        drawLine(
            color = Color.Gray,
            start = Offset(center.x - boreRadius, center.y),
            end = Offset(center.x + boreRadius, center.y),
            strokeWidth = 4.dp.toPx()
        )
        
        // Air particles (flow indication)
        if (animatedOpen > 0.05f) {
            val flowAlpha = animatedOpen * 0.7f
            val numParticles = 8
            for (i in 0 until numParticles) {
                val startX = (size.width / numParticles) * i + (flowOffset * (size.width / numParticles))
                val particleY = center.y - boreRadius * 0.6f + (i * 24f) % (boreRadius * 1.2f)
                
                val dx = startX - center.x
                val dy = particleY - center.y
                if (dx * dx + dy * dy < (boreRadius - 6.dp.toPx()) * (boreRadius - 6.dp.toPx())) {
                    drawCircle(
                        color = MeetColors.cyberCyan.copy(alpha = flowAlpha),
                        radius = 2.5f.dp.toPx(),
                        center = Offset(startX, particleY)
                    )
                }
            }
        }
    }
}

@Composable
fun AcCompressorVisualizer(values: Map<String, Float>) {
    val isEngaged = values.containsKey("Carga Motor")
    
    val transition = rememberInfiniteTransition(label = "ac_spin")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulley_spin"
    )

    Canvas(modifier = Modifier.size(110.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val pulleyRadius = size.width * 0.45f
        val clutchRadius = pulleyRadius * 0.68f
        
        // Pulley wheel
        rotate(spinAngle, pivot = center) {
            drawCircle(
                color = Color.DarkGray,
                radius = pulleyRadius,
                center = center,
                style = Stroke(width = 5.dp.toPx())
            )
            // Rib markings
            for (i in 0 until 4) {
                rotate(i * 90f, pivot = center) {
                    drawLine(
                        color = Color.Gray,
                        start = center,
                        end = Offset(center.x, center.y - pulleyRadius),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
        
        // Inner magnetic clutch plate
        val clutchColor = if (isEngaged) MeetColors.neonGreen else Color.White.copy(alpha = 0.25f)
        val rotationAngle = if (isEngaged) spinAngle else 0f
        
        rotate(rotationAngle, pivot = center) {
            drawCircle(
                color = clutchColor.copy(alpha = 0.2f),
                radius = clutchRadius,
                center = center
            )
            
            drawCircle(
                color = clutchColor,
                radius = clutchRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            
            // 3 Triangular coupling bolts
            for (i in 0 until 3) {
                rotate(i * 120f, pivot = center) {
                    drawCircle(
                        color = clutchColor,
                        radius = 5.dp.toPx(),
                        center = Offset(center.x, center.y - clutchRadius * 0.55f)
                    )
                    drawLine(
                        color = clutchColor,
                        start = center,
                        end = Offset(center.x, center.y - clutchRadius * 0.55f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
fun GlowPlugVisualizer(progress: Float) {
    val heatColor = animateColorAsState(
        targetValue = if (progress < 0.25f) Color.DarkGray
        else if (progress < 0.65f) Color(0xFFFF6D00)
        else Color(0xFFFF1744),
        animationSpec = tween(1200),
        label = "heat"
    )
    
    val pulseTransition = rememberInfiniteTransition(label = "heat_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val tipWidth = 8.dp.toPx()
        val tipHeight = 45.dp.toPx()
        val tipTop = center.y - 12.dp.toPx()
        
        // Glow plug body
        drawRect(
            color = Color.DarkGray,
            topLeft = Offset(center.x - 10.dp.toPx(), center.y - 50.dp.toPx()),
            size = Size(20.dp.toPx(), 35.dp.toPx())
        )
        
        // Thread rings
        for (i in 0 until 3) {
            val y = center.y - 42.dp.toPx() + i * 8.dp.toPx()
            drawLine(
                color = Color.Gray,
                start = Offset(center.x - 12.dp.toPx(), y),
                end = Offset(center.x + 12.dp.toPx(), y),
                strokeWidth = 1.5f.dp.toPx()
            )
        }
        
        // Glow tip
        drawRoundRect(
            color = heatColor.value,
            topLeft = Offset(center.x - tipWidth / 2, tipTop),
            size = Size(tipWidth, tipHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        
        // Radiating heat wave rings
        if (progress > 0.4f) {
            val waveAlpha = (progress - 0.4f) * 0.45f
            drawCircle(
                color = heatColor.value.copy(alpha = waveAlpha * 0.25f),
                radius = tipHeight * 0.75f * pulseScale,
                center = Offset(center.x, tipTop + tipHeight / 2)
            )
        }
    }
}

@Composable
fun GenericActuatorVisualizer(progress: Float) {
    val transition = rememberInfiniteTransition(label = "generic")
    val pulse by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.height * 0.32f
        
        drawCircle(
            color = MeetColors.neonGreen.copy(alpha = 0.08f),
            radius = radius * (1f + pulse * 0.35f),
            center = center
        )
        
        drawCircle(
            color = MeetColors.neonGreen,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        
        drawCircle(
            color = MeetColors.neonGreen.copy(alpha = pulse),
            radius = radius * 0.45f,
            center = center
        )
    }
}

@Composable
fun StyledMarkdownText(text: String, accentColor: Color = MeetColors.cyberCyan) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        val lines = text.split("\n")
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            
            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.substring(2).uppercase(),
                        color = accentColor,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.substring(3).uppercase(),
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("- **") || trimmed.startsWith("* **") -> {
                    val cleanLine = trimmed.removePrefix("- ").removePrefix("* ")
                    val parts = cleanLine.split("**")
                    if (parts.size >= 3) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("• ", color = accentColor, fontWeight = FontWeight.Bold)
                            Text(
                                text = parts[1] + ": ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = parts.subList(2, parts.size).joinToString(""),
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("• ", color = accentColor, fontWeight = FontWeight.Bold)
                            Text(
                                text = cleanLine.replace("**", ""),
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("• ", color = accentColor, fontWeight = FontWeight.Bold)
                        Text(
                            text = trimmed.substring(2),
                            color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    val parts = trimmed.split("**")
                    if (parts.size > 1) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            parts.forEachIndexed { index, part ->
                                val isBold = index % 2 == 1
                                Text(
                                    text = part,
                                    color = if (isBold) Color.White else MeetColors.textSecondary,
                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        Text(
                            text = trimmed,
                            color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

fun getSafetyConditionName(condition: SafetyCondition): String {
    return when (condition) {
        SafetyCondition.ENGINE_OFF -> "Motor APAGADO (Ignición en ON)"
        SafetyCondition.ENGINE_RUNNING -> "Motor ENCENDIDO (En marcha mínima/Ralentí)"
        SafetyCondition.VEHICLE_STATIONARY -> "Vehículo inmóvil y freno de mano aplicado"
        SafetyCondition.BATTERY_ABOVE_12V -> "Batería arriba de 12.0 Voltios"
        SafetyCondition.TRANS_IN_PARK -> "Transmisión en PARK (P) o NEUTRO (N)"
    }
}

fun getTechnicalGuide(testId: String): String {
    return when (testId) {
        "FUEL_PUMP" -> """
            1. Conecte un manómetro mecánico en el puerto de servicio del riel de combustible.
            2. Active la bomba usando este control bidireccional.
            3. Verifique que la presión suba a las especificaciones del fabricante (nominal: ~45-55 PSI).
            4. Desactive la prueba y verifique que la línea sostenga la presión durante al menos 5 minutos (máxima caída tolerada: 5 PSI).
        """.trimIndent()
        "INJECTOR_BALANCE" -> """
            1. Conecte el manómetro mecánico al riel y abra switch (Ignición ON).
            2. MEET pulsará de forma individual cada inyector seleccionado.
            3. Registre la caída de presión exacta tras el pulso (ej. de 50 PSI a 36 PSI).
            4. Todas las caídas deben ser similares dentro de ±1.5 PSI. Caídas desiguales sugieren inyector tapado o goteo constante.
        """.trimIndent()
        "EVAP_VENT", "EVAP_PURGE" -> """
            1. Utilice una máquina de humo conectada al puerto de llenado de combustible o puerto EVAP.
            2. Active el solenoide con MEET para sellar o liberar la línea.
            3. Si sella el Vent, verifique con manómetro que el sistema alcance estanqueidad al vacío.
            4. Si abre el Purge, compruebe que succione vacío del múltiple de admisión.
        """.trimIndent()
        "EGR_VALVE" -> """
            1. Mantenga el motor encendido en ralentí.
            2. Abra gradualmente el ciclo de la válvula EGR (0% -> 50% -> 100%).
            3. Las RPM deben bajar y el motor debe titubear o apagarse al aproximarse al 100%.
            4. Si el motor no reacciona, sospeche de ductos obstruidos por carbonilla o solenoide atascado.
        """.trimIndent()
        "COOLING_FAN_LOW", "COOLING_FAN_HIGH" -> """
            1. Asegúrese de estar alejado del ventilador y que no haya herramientas u objetos cerca.
            2. Ordene la velocidad requerida. Deberá escuchar el accionamiento del relevador en la caja de fusibles.
            3. Si no arranca pero el relevador suena, verifique voltaje y masa directamente en el conector del ventilador.
        """.trimIndent()
        "IDLE_SPEED_UP" -> """
            1. Con motor encendido, active la velocidad de ralentí acelerado.
            2. Confirme que las RPM del motor se incrementen en el rango configurado (normalmente sube a 1000 - 1200 RPM).
            3. Si la respuesta es inestable, revise el sensor TPS y limpie el cuerpo de aceleración.
        """.trimIndent()
        "THROTTLE_BODY" -> """
            1. Desmonte la manguera de admisión de aire para exponer visualmente la mariposa.
            2. Con ignición en ON y motor apagado, inicie la prueba.
            3. Observe la mariposa abrir y cerrar. Debe moverse de forma continua y fluida, sin saltos ni ruidos ásperos de engranes.
        """.trimIndent()
        "AC_COMPRESSOR" -> """
            1. Con motor encendido, inicie el acoplamiento del embrague del compresor de A/C.
            2. Verifique visualmente que el embrague magnético acople ("click" seco) y empiece a girar con la polea.
            3. Si no acopla, verifique el fusible del A/C, relevador y el sensor de presión de gas refrigerante.
        """.trimIndent()
        "ABS_PUMP" -> """
            1. Ubique el módulo de control hidráulico de ABS en el vano motor.
            2. Inicie la activación bidireccional. Escuchará un zumbido eléctrico agudo de la bomba de recirculación de líquido.
            3. Si no suena, verifique el fusible de alimentación dedicada de alta corriente (30A a 40A).
        """.trimIndent()
        else -> """
            1. Compruebe los fusibles y relevadores del circuito asociado al actuador.
            2. Use un multímetro para medir el voltaje de activación en las terminales físicas del conector.
            3. Realice pruebas físicas y acústicas complementarias para descartar bloqueos mecánicos del actuador.
        """.trimIndent()
    }
}
