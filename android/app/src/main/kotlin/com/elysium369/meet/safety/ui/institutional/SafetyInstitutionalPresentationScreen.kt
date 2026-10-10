package com.elysium369.meet.safety.ui.institutional

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visible institutional overview for Elysium Safety.
 *
 * Existing destinations open the application's current Safety screens.
 * Public records and documentary sources belong in the existing report, case,
 * timeline, observatory, and scientific-research flows. No source is presented
 * as live unless its integration and provenance are actually verified.
 * No synthetic case, source record, or investigative finding is displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyInstitutionalPresentationScreen(
    onBack: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToMyReports: () -> Unit,
    onNavigateToCases: () -> Unit,
    onNavigateToTimelines: () -> Unit,
    onNavigateToObservatory: () -> Unit,
    onNavigateToResearch: () -> Unit,
) {
    val activeGuideStep = rememberSaveable { mutableIntStateOf(0) }
    val guideAction: () -> Unit = when (activeGuideStep.intValue) {
        0, 1 -> onNavigateToReport
        2 -> onNavigateToMap
        3 -> onNavigateToResearch
        else -> onNavigateToCases
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ELYSIUM SAFETY",
                            color = MeetColors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                        )
                        Text(
                            text = "PRESENTACIÓN INSTITUCIONAL",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al centro de seguridad",
                            tint = MeetColors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDeep,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NeonCommandHero() }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NeonPrimaryAction(
                        label = "Crear reporte",
                        icon = Icons.Filled.ReportProblem,
                        color = MeetColors.neonGreen,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToReport,
                    )
                    NeonPrimaryAction(
                        label = "Abrir mapa",
                        icon = Icons.Filled.Map,
                        color = MeetColors.electricBlue,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToMap,
                    )
                }
            }

            item {
                GuideStepperCard(
                    activeIndex = activeGuideStep.intValue,
                    onPrevious = {
                        activeGuideStep.intValue = (activeGuideStep.intValue - 1).coerceAtLeast(0)
                    },
                    onNext = {
                        activeGuideStep.intValue = if (activeGuideStep.intValue == 4) 0
                        else activeGuideStep.intValue + 1
                    },
                    onAction = guideAction,
                )
            }

            item {
                SafetyPipelineCard(
                    onNavigateToReport = onNavigateToReport,
                    onNavigateToMap = onNavigateToMap,
                    onNavigateToResearch = onNavigateToResearch,
                    onNavigateToCases = onNavigateToCases,
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "QUÉ SIGNIFICA CADA ESTADO",
                            color = MeetColors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        StatusLegendRow("REGISTRADO", "Alguien lo reportó; aún no implica confirmación.", MeetColors.electricBlue)
                        StatusLegendRow("CORROBORADO", "Existen fuentes adicionales cuya independencia debe verificarse.", MeetColors.cyberCyan)
                        StatusLegendRow("PENDIENTE", "Falta revisión, datos o una integración autorizada.", MeetColors.warning)
                        StatusLegendRow("REFUTADO / CORREGIDO", "La contradicción queda registrada; no debe borrarse silenciosamente.", MeetColors.hotMagenta)
                    }
                }
            }

            item {
                Text(
                    text = "PANTALLAS DISPONIBLES EN ESTA APLICACIÓN",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                InstitutionalActionCard(
                    title = "Reportar un incidente",
                    description = "Abrir el formulario de Safety para documentar un acontecimiento.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.ReportProblem,
                    iconColor = MeetColors.neonGreen,
                    onClick = onNavigateToReport,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Mapa territorial",
                    description = "Consultar la pantalla de mapa y sus capas disponibles según los datos y permisos configurados.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Map,
                    iconColor = MeetColors.cyberCyan,
                    onClick = onNavigateToMap,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Mis reportes",
                    description = "Consultar los reportes accesibles para la sesión actual.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Assignment,
                    iconColor = MeetColors.electricBlue,
                    onClick = onNavigateToMyReports,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Expedientes de seguridad",
                    description = "Abrir la vista de casos y revisar los registros que estén disponibles para la cuenta.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.FolderOpen,
                    iconColor = MeetColors.cyberCyan,
                    onClick = onNavigateToCases,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Cronología de acontecimientos",
                    description = "Abrir la vista temporal de Safety.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Timeline,
                    iconColor = MeetColors.hotMagenta,
                    onClick = onNavigateToTimelines,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Observatorio de seguridad",
                    description = "Consultar los indicadores disponibles sin interpretar ausencia de datos como ausencia de incidentes.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Analytics,
                    iconColor = MeetColors.electricBlue,
                    onClick = onNavigateToObservatory,
                )
            }
            item {
                InstitutionalActionCard(
                    title = "Investigación científica y cadena de evidencia",
                    description = "Contrastar fuentes periodísticas, registros públicos, expedientes institucionales y material documental; vincular afirmaciones, hipótesis, contradicciones y procedencia sin inferir culpabilidad.",
                    status = "PANTALLA ENLAZADA",
                    icon = Icons.Filled.Analytics,
                    iconColor = MeetColors.hotMagenta,
                    onClick = onNavigateToResearch,
                )
            }

            item {
                Text(
                    text = "GOBERNANZA Y DISPONIBILIDAD",
                    color = MeetColors.warning,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                StatusNoticeCard(
                    title = "Intercambio con instituciones",
                    status = "PENDIENTE DE PILOTO AUTORIZADO",
                    body = "No se afirma que exista una conexión operativa con Fuerza Pública, OIJ, Ministerio Público o Poder Judicial. El intercambio debe acordarse con cada institución, con base jurídica, control de acceso, auditoría y pruebas de extremo a extremo.",
                )
            }
            item {
                StatusNoticeCard(
                    title = "Regla de integridad",
                    status = "EVIDENCIA ≠ CULPABILIDAD",
                    body = "Una huella criptográfica puede ayudar a detectar cambios en determinados bytes; no demuestra por sí sola que el contenido sea verdadero, que la fuente sea independiente ni que haya ocurrido un delito. Las decisiones sensibles requieren revisión humana autorizada.",
                )
            }
        }
    }
}


private data class InstitutionalGuideStep(
    val code: String,
    val title: String,
    val body: String,
    val actionLabel: String,
    val tip: String,
)

private val institutionalGuideSteps = listOf(
    InstitutionalGuideStep(
        "01",
        "Registra el acontecimiento",
        "Describe qué ocurrió, cuándo y dónde. Separa lo que observaste directamente de lo que otra persona te contó. Si no sabes un dato, déjalo como desconocido en lugar de adivinar.",
        "ABRIR FORMULARIO DE REPORTE",
        "Este botón abre la pantalla existente de reporte. No se envía nada hasta que completes y confirmes la acción allí.",
    ),
    InstitutionalGuideStep(
        "02",
        "Conserva el material original",
        "Adjunta únicamente material que estés autorizado a compartir. Conserva el original, registra el contexto por separado y evita revelar domicilios, identidades vulnerables o metadatos innecesarios.",
        "VOLVER AL FORMULARIO DE REPORTE",
        "La guía no adjunta archivos por sí sola; te dirige al formulario de Safety.",
    ),
    InstitutionalGuideStep(
        "03",
        "Contextualiza tiempo y territorio",
        "Usa el mapa y la cronología para ordenar los registros. Una coincidencia temporal o geográfica no demuestra causalidad ni identifica automáticamente a una persona.",
        "ABRIR MAPA TERRITORIAL",
        "El mapa muestra las capas y los registros disponibles para tu sesión; no inventa puntos cuando faltan datos.",
    ),
    InstitutionalGuideStep(
        "04",
        "Contrasta fuentes y alternativas",
        "Vincula cada afirmación con sus fuentes. Distingue documentos originales de copias y republicaciones; conserva contradicciones y explicaciones alternativas. Un hash no garantiza que un contenido sea verdadero.",
        "ABRIR INVESTIGACIÓN CIENTÍFICA",
        "La pantalla científica abre las herramientas existentes y no convierte automáticamente una hipótesis en hecho.",
    ),
    InstitutionalGuideStep(
        "05",
        "Prepara una revisión responsable",
        "Comprueba qué está documentado, qué sigue incierto y quién está autorizado para revisar o recibir el material. Una presentación no constituye una remisión formal ni crea conexiones institucionales.",
        "VER EXPEDIENTES DISPONIBLES",
        "Abre los expedientes accesibles para la cuenta. El intercambio institucional requiere un piloto autorizado.",
    ),
)

@Composable
private fun StatusLegendRow(
    label: String,
    description: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun NeonCommandHero() {
    val transition = rememberInfiniteTransition(label = "safety-command-hud")
    val orbitPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit-phase",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "core-pulse",
    )
    val tilt by transition.animateFloat(
        initialValue = -2.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hologram-tilt",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(244.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MeetColors.backgroundDark,
                        MeetColors.cardBackground,
                        Color(0xFF160B2C),
                        MeetColors.backgroundDeep,
                    ),
                ),
            )
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            MeetColors.electricBlue.copy(alpha = 0.95f),
                            MeetColors.hotMagenta.copy(alpha = 0.72f),
                            MeetColors.neonGreen.copy(alpha = 0.52f),
                        ),
                    ),
                ),
                RoundedCornerShape(26.dp),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cyan = MeetColors.electricBlue
            val magenta = MeetColors.hotMagenta
            for (index in 0..8) {
                val x = size.width * index / 8f
                drawLine(
                    color = cyan.copy(alpha = 0.075f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            for (index in 0..5) {
                val y = size.height * index / 5f
                drawLine(
                    color = magenta.copy(alpha = 0.055f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val center = Offset(size.width * 0.79f, size.height * 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cyan.copy(alpha = 0.20f), magenta.copy(alpha = 0.07f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.78f,
                ),
                radius = size.minDimension * 0.78f,
                center = center,
            )
            val scanY = (orbitPhase / 360f) * size.height
            drawLine(
                color = cyan.copy(alpha = 0.16f),
                start = Offset(0f, scanY),
                end = Offset(size.width, scanY),
                strokeWidth = 1.1.dp.toPx(),
            )
            val horizon = size.height * 0.78f
            for (index in 0..9) {
                val startX = size.width * index / 9f
                val endX = size.width * 0.5f + (startX - size.width * 0.5f) * 0.13f
                drawLine(
                    color = cyan.copy(alpha = 0.10f),
                    start = Offset(startX, size.height),
                    end = Offset(endX, horizon),
                    strokeWidth = 0.8.dp.toPx(),
                )
            }
        }

        HolographicCore(
            phase = orbitPhase,
            pulse = pulse,
            tilt = tilt,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .size(136.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.64f)
                .padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MeetColors.neonGreen),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "NEURAL SAFETY INTERFACE",
                    color = MeetColors.electricBlue,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp,
                    letterSpacing = 1.05.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Inteligencia con evidencia.",
                color = MeetColors.textPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                lineHeight = 28.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Reportes · Territorio · Trazabilidad · Revisión humana",
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = MeetColors.backgroundDeep.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.36f)),
            ) {
                Text(
                    text = "3D HOLOGRAPHIC COMMAND DECK",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HolographicCore(
    phase: Float,
    pulse: Float,
    tilt: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.graphicsLayer {
            rotationX = tilt * 2.1f
            rotationY = tilt * 3.2f
            rotationZ = tilt * 0.4f
            cameraDistance = 28f * density
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.29f * pulse
            val cyan = MeetColors.electricBlue
            val green = MeetColors.neonGreen
            val magenta = MeetColors.hotMagenta

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(cyan.copy(alpha = 0.30f), magenta.copy(alpha = 0.09f), Color.Transparent),
                    center = center,
                    radius = radius * 2.5f,
                ),
                radius = radius * 2.5f,
                center = center,
            )
            drawCircle(
                color = cyan.copy(alpha = 0.65f),
                radius = radius * 1.18f,
                center = center,
                style = Stroke(width = 1.1.dp.toPx()),
            )
            drawCircle(
                color = magenta.copy(alpha = 0.62f),
                radius = radius * 0.86f,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.82f), cyan.copy(alpha = 0.78f), Color(0xFF123E76).copy(alpha = 0.28f)),
                    center = Offset(center.x - radius * 0.16f, center.y - radius * 0.20f),
                    radius = radius * 0.78f,
                ),
                radius = radius * 0.62f,
                center = center,
            )

            val ellipseRadiusX = radius * 1.62f
            val ellipseRadiusY = radius * 0.60f
            drawArc(
                color = cyan.copy(alpha = 0.88f),
                startAngle = phase,
                sweepAngle = 215f,
                useCenter = false,
                topLeft = Offset(center.x - ellipseRadiusX, center.y - ellipseRadiusY),
                size = Size(ellipseRadiusX * 2f, ellipseRadiusY * 2f),
                style = Stroke(width = 2.3.dp.toPx()),
            )
            drawArc(
                color = magenta.copy(alpha = 0.92f),
                startAngle = -phase * 1.35f + 125f,
                sweepAngle = 155f,
                useCenter = false,
                topLeft = Offset(center.x - ellipseRadiusX * 0.78f, center.y - ellipseRadiusY * 1.68f),
                size = Size(ellipseRadiusX * 1.56f, ellipseRadiusY * 3.36f),
                style = Stroke(width = 1.65.dp.toPx()),
            )

            for (index in 0 until 12) {
                val angle = phase * (PI / 180.0) + (index * PI / 6.0)
                val x = center.x + cos(angle).toFloat() * radius * 1.46f
                val y = center.y + sin(angle).toFloat() * radius * 1.46f
                drawCircle(
                    color = if (index % 3 == 0) green.copy(alpha = 0.95f) else cyan.copy(alpha = 0.78f),
                    radius = if (index % 3 == 0) 2.7.dp.toPx() else 1.45.dp.toPx(),
                    center = Offset(x, y),
                )
            }
            drawLine(
                color = green.copy(alpha = 0.35f),
                start = Offset(center.x - radius * 1.6f, center.y),
                end = Offset(center.x + radius * 1.6f, center.y),
                strokeWidth = 0.75.dp.toPx(),
            )
            drawLine(
                color = cyan.copy(alpha = 0.24f),
                start = Offset(center.x, center.y - radius * 1.55f),
                end = Offset(center.x, center.y + radius * 1.55f),
                strokeWidth = 0.75.dp.toPx(),
            )
        }
    }
}

@Composable
private fun NeonPrimaryAction(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = MeetColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun GuideStepperCard(
    activeIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAction: () -> Unit,
) {
    val accent = when (activeIndex) {
        0 -> MeetColors.neonGreen
        1 -> MeetColors.electricBlue
        2 -> MeetColors.cyberCyan
        3 -> MeetColors.hotMagenta
        else -> MeetColors.warning
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(accent.copy(alpha = 0.85f), MeetColors.borderBlue, MeetColors.hotMagenta.copy(alpha = 0.4f)),
            ),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "GUÍA INTERACTIVA · 5 ETAPAS",
                        color = MeetColors.electricBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.05.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Un proceso claro. Sin saltos de evidencia.",
                        color = MeetColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
                ) {
                    Text(
                        text = (activeIndex + 1).toString() + " / " + institutionalGuideSteps.size.toString(),
                        color = accent,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                institutionalGuideSteps.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(if (index <= activeIndex) accent else MeetColors.borderBlue),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = activeIndex,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(240)) +
                        slideInVertically(animationSpec = tween(240)) { height -> height / 5 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(150)) +
                                slideOutVertically(animationSpec = tween(150)) { height -> -height / 7 },
                        )
                },
                label = "safety-guide-step-transition",
            ) { stepIndex ->
                val step = institutionalGuideSteps[stepIndex]
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(accent.copy(alpha = 0.24f), MeetColors.backgroundDeep, MeetColors.hotMagenta.copy(alpha = 0.12f)),
                                    ),
                                )
                                .border(1.dp, accent.copy(alpha = 0.56f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(step.code, color = accent, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = step.title,
                                color = MeetColors.textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = step.body,
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MeetColors.backgroundDeep,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
                    ) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(step.tip, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.copy(alpha = 0.16f),
                            contentColor = accent,
                        ),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.75f)),
                    ) {
                        Text(step.actionLabel, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.4.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = activeIndex > 0,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 11.dp),
                ) {
                    Text("Anterior")
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.backgroundDeep,
                        contentColor = MeetColors.electricBlue,
                    ),
                    border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.52f)),
                ) {
                    Text(
                        text = if (activeIndex == institutionalGuideSteps.lastIndex) "Reiniciar guía" else "Siguiente etapa",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstitutionalActionCard(
    title: String,
    description: String,
    status: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = iconColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MeetColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    color = MeetColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status,
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.7.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Abrir $title",
                tint = MeetColors.cyberCyan,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatusNoticeCard(
    title: String,
    status: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = MeetColors.textPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = status,
                color = MeetColors.warning,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 0.65.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = body,
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


/**
 * Interactive map of the information lifecycle. Every action reuses an existing
 * Safety destination; none of these cards implies a live institutional integration.
 */
@Composable
private fun SafetyPipelineCard(
    onNavigateToReport: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToResearch: () -> Unit,
    onNavigateToCases: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    MeetColors.cyberCyan.copy(alpha = 0.72f),
                    MeetColors.electricBlue.copy(alpha = 0.48f),
                    MeetColors.hotMagenta.copy(alpha = 0.46f),
                ),
            ),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MeetColors.electricBlue.copy(alpha = 0.10f),
                            MeetColors.cardBackground,
                            MeetColors.hotMagenta.copy(alpha = 0.07f),
                        ),
                    ),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(
                    "FLUJO DE INFORMACIÓN · 6 ETAPAS",
                    color = MeetColors.cyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.25.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Del reporte a un expediente revisable",
                    color = MeetColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Cada etapa abre herramientas existentes; la disponibilidad de datos y los permisos siguen siendo los reales de la sesión.",
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SafetyPipelineStage(
                    number = "01",
                    title = "CAPTURAR",
                    detail = "Reporte, relato y fecha",
                    icon = Icons.Filled.ReportProblem,
                    accent = MeetColors.neonGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToReport,
                )
                SafetyPipelineStage(
                    number = "02",
                    title = "PRESERVAR",
                    detail = "Adjuntos y estado de custodia",
                    icon = Icons.Filled.CheckCircle,
                    accent = MeetColors.electricBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToReport,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SafetyPipelineStage(
                    number = "03",
                    title = "UBICAR",
                    detail = "Tiempo, territorio y filtros",
                    icon = Icons.Filled.Map,
                    accent = MeetColors.cyberCyan,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMap,
                )
                SafetyPipelineStage(
                    number = "04",
                    title = "RELACIONAR",
                    detail = "Fuentes, afirmaciones e hipótesis",
                    icon = Icons.Filled.Timeline,
                    accent = MeetColors.hotMagenta,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToResearch,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SafetyPipelineStage(
                    number = "05",
                    title = "CONTRASTAR",
                    detail = "Procedencia y contradicciones",
                    icon = Icons.Filled.Analytics,
                    accent = MeetColors.electricBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToResearch,
                )
                SafetyPipelineStage(
                    number = "06",
                    title = "REVISAR",
                    detail = "Expedientes disponibles",
                    icon = Icons.Filled.FolderOpen,
                    accent = MeetColors.warning,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToCases,
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Text(
                    "Un reporte no es una confirmación. Un hash no demuestra la verdad del contenido. Compartir con una institución requiere autorización y una integración validada.",
                    color = MeetColors.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun SafetyPipelineStage(
    number: String,
    title: String,
    detail: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(94.dp),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
        border = BorderStroke(0.8.dp, accent.copy(alpha = 0.62f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text(number, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Column {
                Text(title, color = MeetColors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(detail, color = MeetColors.textSecondary, fontSize = 9.sp, lineHeight = 11.sp)
            }
        }
    }
}
