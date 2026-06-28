package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.DiagnosticSeverity
import com.elysium369.meet.core.obd.PrePurchaseInspection
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay

@Composable
fun UrgencyGauge(
    score: Int,
    verdictColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "gaugeProgress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(180.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 12.dp.toPx()
            val size = this.size

            val startAngle = 135f
            val totalSweep = 270f

            // Arco de fondo (track)
            drawArc(
                color = MeetColors.borderSubtle.copy(alpha = 0.5f),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            // Arco activo con degradado neón
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(
                        verdictColor.copy(alpha = 0.4f),
                        verdictColor
                    ),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f)
                ),
                startAngle = startAngle,
                sweepAngle = totalSweep * animatedProgress,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-8).dp)
        ) {
            val animatedScore by animateIntAsState(
                targetValue = score,
                animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                label = "scoreAnim"
            )
            Text(
                text = "$animatedScore",
                color = verdictColor,
                fontWeight = FontWeight.Black,
                fontSize = 44.sp,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "PUNTAJE CLÍNICO",
                color = MeetColors.textSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun HazardBanner(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stripeWidth = 12.dp.toPx()
        val spacing = 8.dp.toPx()
        val color1 = MeetColors.error
        val color2 = Color.Black

        // Rellenar de negro
        drawRect(color = color2)

        // Dibujar franjas rojas diagonales
        var x = -height
        while (x < width + height) {
            val path = Path().apply {
                moveTo(x, 0f)
                lineTo(x + stripeWidth, 0f)
                lineTo(x + stripeWidth - height, height)
                lineTo(x - height, height)
                close()
            }
            drawPath(path, color = color1)
            x += stripeWidth + spacing
        }
    }
}

@Composable
fun CyberProgressBar(
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "progressAnim"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MeetColors.borderSubtle.copy(alpha = 0.5f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val size = this.size
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        barColor.copy(alpha = 0.6f),
                        barColor
                    )
                ),
                size = size.copy(width = size.width * animatedProgress),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrePurchaseScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val result by viewModel.inspectionResult.collectAsState()
    val isInspecting by viewModel.isInspecting.collectAsState()

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "DIAGNÓSTICO CLÍNICO",
                subtitle = "INSPECCIÓN PRE-COMPRA",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Evaluación automatizada de 8 dimensiones para certificar la salud de un vehículo antes de comprarlo.",
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Welcome/Start Card
            if (result == null && !isInspecting) {
                EliteCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🛡️",
                            fontSize = 56.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "¿Debo comprar este vehículo?",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Elysium Vanguard ejecutará un escaneo profundo de 8 dimensiones mecánicas y electrónicas usando únicamente datos OBD-II reales.",
                            color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Grid of 8 Dimensions (2x4)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val dims = listOf(
                                "🔍 DTCs Activos" to "⚙️ Mezcla Combustible",
                                "🛡️ DTCs Permanentes" to "🌡️ Sistema Térmico",
                                "📡 Monitores Emisiones" to "🔋 Sistema Eléctrico",
                                "⏳ DTCs Pendientes" to "🧪 Pruebas ECU Mode 06"
                            )
                            dims.forEach { (first, second) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(MeetColors.cardBackgroundLighter.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = first,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(MeetColors.cardBackgroundLighter.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = second,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        EliteButton(
                            text = "INICIAR EVALUACIÓN CLÍNICA",
                            onClick = { viewModel.runPrePurchaseInspection() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (isInspecting) {
                Spacer(modifier = Modifier.height(40.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MeetColors.neonGreen,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "CONECTANDO CON SENSORES ECU...",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Recuperando registros clínicos...",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Result Display
            result?.let { r ->
                val verdictColor = when (r.verdict) {
                    PrePurchaseInspection.Verdict.APPROVED -> MeetColors.success
                    PrePurchaseInspection.Verdict.CAUTION -> MeetColors.warning
                    PrePurchaseInspection.Verdict.REJECT -> MeetColors.error
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Verdict Core Card
                EliteCard(
                    borderColor = verdictColor,
                    glowColor = verdictColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UrgencyGauge(
                            score = r.overallScore,
                            verdictColor = verdictColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // High fidelity verdict pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(verdictColor.copy(alpha = 0.15f))
                                .border(1.5.dp, verdictColor, RoundedCornerShape(50))
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = r.verdictText,
                                color = verdictColor,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = r.verdictExplanation,
                            color = MeetColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Red Flags Section
                if (r.redFlags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    EliteCard(
                        backgroundColor = MeetColors.error.copy(alpha = 0.04f),
                        borderColor = MeetColors.error,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            HazardBanner(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            )
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    AnimatedNeonGlyph("🚩", contentDescription = null, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "BANDERAS ROJAS DETECTADAS",
                                        color = MeetColors.error,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                r.redFlags.forEach { flag ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "•",
                                            color = MeetColors.error,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = flag,
                                            color = MeetColors.textPrimary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Breakdowns
                Spacer(modifier = Modifier.height(24.dp))
                PhantomSectionHeader(label = "DESGLOSE DE CATEGORÍAS", accentColor = MeetColors.neonGreen)
                Spacer(modifier = Modifier.height(8.dp))

                r.categories.forEach { cat ->
                    val catColor = when (cat.severity) {
                        DiagnosticSeverity.INFO -> MeetColors.success
                        DiagnosticSeverity.MODERATE -> MeetColors.warning
                        DiagnosticSeverity.HIGH -> Color(0xFFFF6D00)
                        DiagnosticSeverity.CRITICAL -> MeetColors.error
                    }

                    EliteCard(
                        borderColor = catColor.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(catColor.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedNeonGlyph(cat.icon, contentDescription = null, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = cat.name.uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${cat.score}/${cat.maxScore}",
                                    color = catColor,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            CyberProgressBar(
                                progress = cat.score.toFloat() / cat.maxScore.coerceAtLeast(1),
                                barColor = catColor
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            cat.findings.forEach { finding ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡",
                                        color = catColor.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Text(
                                        text = finding,
                                        color = MeetColors.textSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Recommendations Section
                if (r.recommendations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PhantomSectionHeader(label = "RECOMENDACIONES CLÍNICAS", accentColor = MeetColors.cyberCyan)
                    Spacer(modifier = Modifier.height(8.dp))

                    EliteCard(
                        backgroundColor = MeetColors.cyberCyan.copy(alpha = 0.04f),
                        borderColor = MeetColors.cyberCyan,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            r.recommendations.forEachIndexed { i, rec ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${i + 1}.",
                                        color = MeetColors.cyberCyan,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = rec,
                                        color = MeetColors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Actions: Export (PDF) & Re-run
                Spacer(modifier = Modifier.height(32.dp))

                EliteButton(
                    text = "EXPORTAR REPORTE PRE-COMPRA (PDF) 📄",
                    onClick = { viewModel.generatePrePurchasePdf(r) },
                    color = MeetColors.cyberCyan,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                EliteOutlinedButton(
                    text = "REPETIR DIAGNÓSTICO",
                    onClick = { viewModel.runPrePurchaseInspection() },
                    color = MeetColors.neonGreen,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
