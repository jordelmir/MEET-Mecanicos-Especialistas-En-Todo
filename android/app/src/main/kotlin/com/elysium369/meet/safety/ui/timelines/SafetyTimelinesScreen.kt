package com.elysium369.meet.safety.ui.timelines

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyTimelinesScreen(
    onBack: () -> Unit = {},
    viewModel: SafetyTimelinesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Línea de Tiempo",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = MeetColors.textPrimary,
                        )
                        Text(
                            "${uiState.entries.size} eventos registrados",
                            fontSize = 11.sp,
                            color = MeetColors.textSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MeetColors.textPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Filled.Timeline,
                            contentDescription = "Actualizar",
                            tint = MeetColors.cyberCyan,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDeep,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.entries.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MeetColors.neonGreen)
                }
            }
            uiState.entries.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Timeline,
                            contentDescription = null,
                            tint = MeetColors.textMuted,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Sin eventos en la línea de tiempo",
                            color = MeetColors.textMuted,
                            fontSize = 14.sp,
                        )
                        Text(
                            "Los reportes aparecerán aquí cronológicamente",
                            color = MeetColors.textMuted.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    item {
                        TimelineCommandDeck(
                            eventCount = uiState.entries.size,
                            sourceCount = uiState.entries.sumOf { it.sourceCount },
                            evidenceCount = uiState.entries.sumOf { it.evidenceCount },
                        )
                    }
                    itemsIndexed(
                        items = uiState.entries,
                        key = { _, entry -> entry.milestoneId },
                    ) { index, entry ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 2 },
                        ) {
                            TimelineNodeCard(
                                entry = entry,
                                isFirst = index == 0,
                                isLast = index == uiState.entries.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineNodeCard(
    entry: TimelineEntry,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val nodeColor = when (entry.caseType.uppercase()) {
        "HOMICIDE" -> Color(0xFFFF4444)
        "VIOLENT_INCIDENT" -> Color(0xFFFF8800)
        "DRUG_SALE_ACTIVITY" -> Color(0xFFAA44FF)
        "THREAT" -> Color(0xFFFFBB00)
        "MISSING_PERSON" -> Color(0xFF44BBFF)
        "INSTITUTIONAL_CONDUCT" -> Color(0xFF00BFA5)
        else -> MeetColors.cyberCyan
    }
    val dateFormatter = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("es", "MX"))
    val dateStr = entry.occurredAt?.let { dateFormatter.format(Date(it)) }
        ?: entry.recordedAt?.let { dateFormatter.format(Date(it)) }
        ?: "Fecha no disponible"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Timeline spine: dot + connecting line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
        ) {
            // Top connector line
            if (!isFirst) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(nodeColor.copy(alpha = 0.4f))
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }

            // Node dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(nodeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isFirst) Icons.Filled.Circle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(8.dp),
                )
            }

            // Bottom connector line
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(nodeColor.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Content card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(
                nodeColor.copy(alpha = 0.48f),
                MeetColors.hotMagenta.copy(alpha = 0.20f),
                MeetColors.borderSubtle,
            ))),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Date label
                Text(
                    dateStr,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = nodeColor,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(6.dp))

                // Case title
                Text(
                    entry.caseTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MeetColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))

                // Summary
                Text(
                    entry.publicSummary,
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatChip(
                        label = "Fuentes",
                        value = entry.sourceCount.toString(),
                        color = MeetColors.cyberCyan,
                    )
                    StatChip(
                        label = "Evidencia",
                        value = entry.evidenceCount.toString(),
                        color = MeetColors.neonGreen,
                    )
                    StatChip(
                        label = "Tipo",
                        value = entry.eventType.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        color = nodeColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column {
        Text(
            label.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = color.copy(alpha = 0.7f),
            letterSpacing = 1.sp,
        )
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}


@Composable
private fun TimelineCommandDeck(eventCount: Int, sourceCount: Int, evidenceCount: Int) {
    val transition = rememberInfiniteTransition(label = "timeline-command")
    val pulse by transition.animateFloat(
        initialValue = 0.30f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "timeline-command-pulse",
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(
            MeetColors.cyberCyan.copy(alpha = pulse),
            MeetColors.electricBlue.copy(alpha = pulse * 0.75f),
            MeetColors.hotMagenta.copy(alpha = pulse * 0.45f),
        ))),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(
            MeetColors.cyberCyan.copy(alpha = 0.08f),
            MeetColors.cardBackground,
            MeetColors.hotMagenta.copy(alpha = 0.06f),
        ))).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TEMPORAL INTELLIGENCE", fontSize = 10.sp, color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, letterSpacing = 1.35.sp)
                    Text("Secuencia de acontecimientos", fontSize = 14.sp, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(9.dp).clip(CircleShape).background(MeetColors.neonGreen.copy(alpha = pulse)))
            }
            Spacer(Modifier.height(10.dp))
            Text("$eventCount hitos · $sourceCount vínculos a fuentes · $evidenceCount vínculos a evidencia", fontSize = 12.sp, color = MeetColors.textSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(
                MeetColors.cyberCyan.copy(alpha = pulse),
                MeetColors.electricBlue.copy(alpha = 0.6f),
                MeetColors.hotMagenta.copy(alpha = 0.25f),
            ))))
        }
    }
}
