package com.elysium369.meet.ui.screens.scanner

import com.elysium369.meet.ui.components.AnimatedNeonGlyph

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteCard

@Composable
fun DtcStatCard(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDark,
        borderColor = color.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        glowColor = color.copy(alpha = 0.2f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("$count", color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun DtcItemCard(
    code: String,
    type: String,
    color: Color,
    description: String = "Consultando diagnóstico...",
    occurrenceCount: Int = 1,
    lastSeenAt: Long = 0L,
    freezeFrameData: Map<String, String> = emptyMap(),
    onFreezeFrameClick: () -> Unit = {},
    isRefreshingFreezeFrame: Boolean = false,
    onAiConsultClick: () -> Unit = {},
    isConsultingAi: Boolean = false,
    aiAnalysis: String? = null,
    onRepairGuideClick: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val timeStr = if (lastSeenAt > 0) {
        val diff = System.currentTimeMillis() - lastSeenAt
        when {
            diff < 60000 -> "Detectado ahora"
            diff < 3600000 -> "Hace ${diff / 60000} min"
            diff < 86400000 -> "Hace ${diff / 3600000} h"
            else -> "Detectado hace ${diff / 86400000} d"
        }
    } else ""

    // Parse SAE Standard OBD-II Systems
    val (systemName, systemIcon) = when (code.firstOrNull()) {
        'P' -> Pair("Tren Motriz", "⚙️")
        'C' -> Pair("Chasis", "🚗")
        'B' -> Pair("Carrocería", "🛡️")
        'U' -> Pair("Red / Com.", "🌐")
        else -> Pair("Genérico", "🔌")
    }

    EliteCard(
        backgroundColor = MeetColors.backgroundDark,
        borderColor = color.copy(alpha = if (expanded) 0.8f else 0.4f),
        shape = RoundedCornerShape(12.dp),
        glowColor = color.copy(alpha = if (expanded) 0.3f else 0.15f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            code,
                            color = color,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                type.uppercase(),
                                color = color,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (occurrenceCount > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "x$occurrenceCount",
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$systemIcon $systemName",
                            color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (timeStr.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "• $timeStr",
                                color = MeetColors.textSecondary.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                AnimatedNeonIcon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = MeetColors.textSecondary
                )
            }

            val shortDesc = if (description.isBlank() || description.contains("no disponible") || description.contains("no encontrada")) {
                com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(code, isSpanish = true)
            } else {
                description
            }
            val displayDesc = com.elysium369.meet.ui.components.DtcUtils.getDtcParagraphExplanation(code, shortDesc, isSpanish = true)

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                displayDesc,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // Primary Repair Guide Button
                Button(
                    onClick = { onRepairGuideClick() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        contentColor = MeetColors.cyberCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("🛠️ VER GUÍA DE REPARACIÓN COMPLETA", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Consult AI Button
                    val aiButtonColor = MeetColors.neonGreen
                    Button(
                        onClick = { onAiConsultClick() },
                        enabled = !isConsultingAi,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = aiButtonColor.copy(alpha = 0.12f),
                            contentColor = aiButtonColor,
                            disabledContainerColor = aiButtonColor.copy(alpha = 0.05f),
                            disabledContentColor = aiButtonColor.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, aiButtonColor.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (isConsultingAi) {
                            CircularProgressIndicator(
                                color = aiButtonColor,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("🔮 CONSULTAR IA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Freeze Frame Button
                    val ffButtonColor = MeetColors.electricBlue
                    Button(
                        onClick = { onFreezeFrameClick() },
                        enabled = !isRefreshingFreezeFrame,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ffButtonColor.copy(alpha = 0.12f),
                            contentColor = ffButtonColor,
                            disabledContainerColor = ffButtonColor.copy(alpha = 0.05f),
                            disabledContentColor = ffButtonColor.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ffButtonColor.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (isRefreshingFreezeFrame) {
                            CircularProgressIndicator(
                                color = ffButtonColor,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("❄️ FREEZE FRAME", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Search Web Button
                    val searchButtonColor = MeetColors.warning
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=OBD2+code+$code"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = searchButtonColor.copy(alpha = 0.12f),
                            contentColor = searchButtonColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, searchButtonColor.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("🔍 WEB SEARCH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Specific AI Result Display
                if (aiAnalysis != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MeetColors.backgroundDeep.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(MeetColors.neonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "ELYSIUM AI EXPERTO",
                                        color = MeetColors.neonGreen,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                aiAnalysis,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Freeze Frame Table Display
                val dtcFF = freezeFrameData.filterKeys { it.startsWith("$code:") }
                if (dtcFF.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "DATOS DE CUADRO CONGELADO (FREEZE FRAME)",
                        color = MeetColors.electricBlue,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MeetColors.backgroundDeep.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .border(1.5.dp, MeetColors.electricBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    ) {
                        dtcFF.forEach { (fullKey, value) ->
                            val sensorName = fullKey.substringAfter("$code:")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    sensorName,
                                    color = MeetColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    value,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                } else if (isRefreshingFreezeFrame) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Leyendo sensores en el momento de la falla...",
                        color = MeetColors.textMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ToolCard(icon: String, title: String, desc: String, color: Color, onClick: () -> Unit) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDark,
        borderColor = color.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        glowColor = color.copy(alpha = 0.5f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(desc, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun EcuConnector(
    leftConnected: Boolean,
    rightConnected: Boolean,
    leftGlowColor: Color,
    rightGlowColor: Color,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseOffset by if (isScanning) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // 1. Vertical Backbone Line (CAN Bus)
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = androidx.compose.ui.geometry.Offset(cx, 0f),
            end = androidx.compose.ui.geometry.Offset(cx, h),
            strokeWidth = 3.dp.toPx()
        )

        // 2. Left branch horizontal connector
        if (leftConnected) {
            drawLine(
                color = leftGlowColor.copy(alpha = 0.4f),
                start = androidx.compose.ui.geometry.Offset(0f, cy),
                end = androidx.compose.ui.geometry.Offset(cx, cy),
                strokeWidth = 2.dp.toPx()
            )
        }

        // 3. Right branch horizontal connector
        if (rightConnected) {
            drawLine(
                color = rightGlowColor.copy(alpha = 0.4f),
                start = androidx.compose.ui.geometry.Offset(cx, cy),
                end = androidx.compose.ui.geometry.Offset(w, cy),
                strokeWidth = 2.dp.toPx()
            )
        }

        // 4. Central Node Circle
        drawCircle(
            color = if (leftConnected || rightConnected) MeetColors.neonGreen.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.6f),
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )

        // 5. Traveling scanning pulse animation
        if (isScanning) {
            val pulseY = pulseOffset * h
            drawCircle(
                color = MeetColors.neonGreen,
                radius = 5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(cx, pulseY)
            )
        }
    }
}

@Composable
fun EcuModuleCard(
    module: com.elysium369.meet.core.obd.NetworkModule,
    isPreview: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isPreview -> Color.White.copy(alpha = 0.12f)
        module.isAlive -> MeetColors.neonGreen.copy(alpha = 0.6f)
        else -> MeetColors.error.copy(alpha = 0.6f)
    }

    val glowColor = when {
        isPreview -> Color.Transparent
        module.isAlive -> MeetColors.neonGreen
        else -> MeetColors.error
    }

    EliteCard(
        backgroundColor = MeetColors.backgroundDark,
        borderColor = borderColor,
        shape = RoundedCornerShape(10.dp),
        glowColor = glowColor.copy(alpha = if (isPreview) 0f else 0.25f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = module.name,
                color = if (isPreview) Color.White.copy(alpha = 0.4f) else Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isPreview) {
                    Text(
                        "DISPONIBLE",
                        color = Color.White.copy(alpha = 0.25f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                } else if (module.isAlive) {
                    Text(
                        "ONLINE",
                        color = MeetColors.neonGreen,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                    if (module.latencyMs > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "(${module.latencyMs}ms)",
                            color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Text(
                        "OFFLINE",
                        color = MeetColors.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            if (module.dtcs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(MeetColors.error.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, MeetColors.error, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "⚠️ ${module.dtcs.size} DTC",
                        color = MeetColors.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun EcuTopologyMap(
    isScanning: Boolean,
    detectedModules: List<com.elysium369.meet.core.obd.NetworkModule>,
    modifier: Modifier = Modifier
) {
    val previewModules = remember {
        listOf(
            com.elysium369.meet.core.obd.NetworkModule("ECM", "Motor (ECM)", false),
            com.elysium369.meet.core.obd.NetworkModule("TCM", "Transmisión (TCM)", false),
            com.elysium369.meet.core.obd.NetworkModule("ABS", "Frenos (ABS)", false),
            com.elysium369.meet.core.obd.NetworkModule("SRS", "Airbag (SRS)", false),
            com.elysium369.meet.core.obd.NetworkModule("BCM", "Carrocería (BCM)", false),
            com.elysium369.meet.core.obd.NetworkModule("HVAC", "Clima (HVAC)", false)
        )
    }

    val displayModules = if (detectedModules.isEmpty()) previewModules else detectedModules
    val pairCount = (displayModules.size + 1) / 2

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (i in 0 until pairCount) {
            val leftNode = displayModules.getOrNull(2 * i)
            val rightNode = displayModules.getOrNull(2 * i + 1)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Node Card
                if (leftNode != null) {
                    EcuModuleCard(
                        module = leftNode,
                        isPreview = detectedModules.isEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Connecting line
                EcuConnector(
                    leftConnected = leftNode != null && (leftNode.isAlive || detectedModules.isEmpty()),
                    rightConnected = rightNode != null && (rightNode.isAlive || detectedModules.isEmpty()),
                    leftGlowColor = if (leftNode?.isAlive == true) MeetColors.neonGreen else MeetColors.error,
                    rightGlowColor = if (rightNode?.isAlive == true) MeetColors.neonGreen else MeetColors.error,
                    isScanning = isScanning,
                    modifier = Modifier.width(32.dp).height(50.dp)
                )

                // Right Node Card
                if (rightNode != null) {
                    EcuModuleCard(
                        module = rightNode,
                        isPreview = detectedModules.isEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun AiDiagnosisReportCard(
    aiAnalysisResult: String,
    onClose: () -> Unit,
    onGeneratePdf: () -> Unit
) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = MeetColors.neonGreen.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        glowColor = MeetColors.neonGreen,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, MeetColors.neonGreen, RoundedCornerShape(4.dp))
                ) {
                    Text(
                        "ELYSIUM AI MAESTRO",
                        color = MeetColors.neonGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    AnimatedNeonIcon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Close",
                        tint = MeetColors.textMuted,
                        modifier = Modifier.rotate(45f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Format markdown-like sections beautifully
            val paragraphs = aiAnalysisResult.split("\n\n")
            paragraphs.forEach { paragraph ->
                val lines = paragraph.trim().split("\n")
                if (lines.isNotEmpty()) {
                    val firstLine = lines.first().trim()
                    val isHeader = firstLine.startsWith("#") || (firstLine.startsWith("*") && firstLine.endsWith(":")) || (firstLine.contains(":") && firstLine.length < 40)

                    if (isHeader) {
                        val headerText = firstLine.replace(Regex("[#*:]"), "").trim()
                        Text(
                            text = headerText.uppercase(),
                            color = MeetColors.neonGreen,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            letterSpacing = 1.sp
                        )

                        if (lines.size > 1) {
                            val rest = lines.drop(1).joinToString("\n")
                            Text(
                                text = rest.replace("- ", "• ").replace("* ", "• "),
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp),
                                lineHeight = 20.sp
                            )
                        }
                    } else {
                        Text(
                            text = paragraph.replace("- ", "• ").replace("* ", "• "),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                com.elysium369.meet.ui.components.EliteTextButton(
                    text = "GENERAR INFORME PDF",
                    onClick = onGeneratePdf,
                    color = MeetColors.neonGreen
                )
            }
        }
    }
}
