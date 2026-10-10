package com.elysium369.meet.education.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.education.domain.PisaDomain
import com.elysium369.meet.education.domain.PisaProficiencyLevel
import com.elysium369.meet.education.engine.PisaEvaluationReport
import com.elysium369.meet.education.engine.PisaSubjectStage
import com.elysium369.meet.education.engine.SubjectPisaProgressionSpec

@Composable
fun PisaOecdBenchmarkCard(
    selectedDomain: PisaDomain,
    report: PisaEvaluationReport?,
    progressionSpec: SubjectPisaProgressionSpec?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectDomain: (PisaDomain) -> Unit,
    onOpenTutor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "🌐", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ESTÁNDAR INTERNACIONAL OCDE",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "PISA 2025/2026",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = "Ruta de Excelencia hacia la Prueba Perfecta (Nivel 6 / 800+ pts)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expandir o colapsar",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Domain Selector Chips
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val domains: List<Pair<PisaDomain, String>> = listOf(
                    PisaDomain.MATHEMATICAL_LITERACY to "🧮 Matemática",
                    PisaDomain.READING_LITERACY to "📖 Lectura",
                    PisaDomain.SCIENTIFIC_LITERACY to "🔬 Ciencias",
                    PisaDomain.CREATIVE_THINKING to "💡 Creativo",
                    PisaDomain.LEARNING_IN_DIGITAL_WORLD to "💻 Digital",
                )
                items(domains) { (domain, label) ->
                    val isSelected = domain == selectedDomain
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectDomain(domain) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Score & Level Highlight Banner
                    val score = report?.estimatedPisaScore ?: 520
                    val level = report?.proficiencyLevel ?: PisaProficiencyLevel.LEVEL_3
                    val meetsBaseline = report?.meetsOecdBaseline ?: (score >= 482)

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "PUNTAJE ESTIMADO PISA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.outline,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "$score",
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (score >= 669) Color(0xFFD4AF37) else MaterialTheme.colorScheme.primary,
                                            ),
                                        )
                                        Text(
                                            text = " / 800+ pts",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.outline,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (score >= 669) Color(0xFFD4AF37).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.End,
                                    ) {
                                        Text(
                                            text = level.titleEs,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (score >= 669) Color(0xFFB8860B) else MaterialTheme.colorScheme.primary,
                                            ),
                                        )
                                        Text(
                                            text = "Escala OCDE: 200 - 800",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // Score Gauge Progress Bar
                            val normalizedScore = ((score - 200).coerceIn(0, 600) / 600f)
                            LinearProgressIndicator(
                                progress = { normalizedScore },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (score >= 669) Color(0xFFD4AF37) else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = "200 (Básico)", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                Text(text = "482 (Umbral Nivel 2)", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                Text(text = "669+ (Nivel 6 Perfección)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // OECD Baseline Threshold Status Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (meetsBaseline) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (meetsBaseline) Icons.Default.Check else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (meetsBaseline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (meetsBaseline) {
                                    "✅ Umbral de Base OCDE Superado: Dominio de inferencias y razonamiento autónomo."
                                } else {
                                    "⚠️ Requiere refuerzo: Por debajo del umbral mínimo de competencia OCDE (Nivel 2)."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (meetsBaseline) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            )
                        }
                    }

                    // 6-Stage Mastery Continuum Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "CONTINUO HACIA LA PRUEBA PERFECTA (6 ETAPAS)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val stages = listOf(
                                PisaSubjectStage.STAGE_1_ELEMENTARY,
                                PisaSubjectStage.STAGE_2_OECD_BASELINE,
                                PisaSubjectStage.STAGE_3_OPERATIONAL,
                                PisaSubjectStage.STAGE_4_RELATIONAL_MODELING,
                                PisaSubjectStage.STAGE_5_ADVANCED_STRATEGY,
                                PisaSubjectStage.STAGE_6_PERFECT_MASTERY,
                            )
                            val currentStageNum = progressionSpec?.currentStage?.stageNumber ?: 3

                            stages.forEach { stage ->
                                val isPassed = stage.stageNumber < currentStageNum
                                val isCurrent = stage.stageNumber == currentStageNum
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isPassed -> Color(0xFF2E7D32)
                                                    isCurrent -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "${stage.stageNumber}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stage.titleEs,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            ),
                                        )
                                        if (isCurrent) {
                                            Text(
                                                text = stage.descriptionEs,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.outline,
                                                ),
                                            )
                                        }
                                    }
                                    if (isPassed) {
                                        Text(text = "✓", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Black)
                                    } else if (isCurrent) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        ) {
                                            Text(
                                                text = "EN CURSO",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Transfer Demonstrations Indicator
                    val completedTransfers = progressionSpec?.completedTransferTasksCount ?: 2
                    val requiredTransfers = progressionSpec?.requiredTransferTasksForLevel6 ?: 5
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "🔄 Tareas de Transferencia Inéditas (OCDE)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "$completedTransfers de $requiredTransfers superadas",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Regla Invariante: Sin transferencia comprobada en situaciones no familiares, la maestría se topa en 75%. Para Nivel 6 se exigen 5 desafíos en contextos inéditos.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }

                    // Diagnostic Weakest Process & Next Step
                    report?.weakestProcess?.let { weakProcess ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "🔍 Diagnóstico Cognitivo PISA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Proceso a reforzar: $weakProcess",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    ),
                                )
                                Text(
                                    text = report.recommendedIntervention,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    ),
                                )
                            }
                        }
                    }

                    // Action Button: Open Socratic Tutor with PISA Focus
                    Button(
                        onClick = onOpenTutor,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Entrenar Desafío PISA con Tutor Socrático",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
