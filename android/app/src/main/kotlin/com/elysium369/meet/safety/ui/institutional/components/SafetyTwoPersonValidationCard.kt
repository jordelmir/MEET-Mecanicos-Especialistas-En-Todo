package com.elysium369.meet.safety.ui.institutional.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

private enum class DemonstrationReviewPhase {
    INGESTED_PRIVATE,   // Estado inicial: OBSERVED, privado en bóveda
    RECOMMENDED_BY_A,   // Calificado por Revisor A (Candidato)
    AUTHORIZED_BY_B,    // Autorizado por Revisor B (Publicado en mapa)
    REJECTED,           // Desestimado por inconsistencias
}

/**
 * Interactive Parliamentary & Academic Console for Two-Person Validation.
 *
 * Demonstrates the Epistemic & Institutional Rule of Elysium Safety:
 * - Reviewer A (Fact & Evidence Analyst, TRUST_REVIEWER) != Reviewer B (Legal & Publication Authority, LEGAL_REVIEWER)
 * - Citizen Report (OBSERVED) never self-elevates to public projection.
 * - Prevents false reports, lynching, defamatory claims, and single-operator bias.
 */
@Composable
fun SafetyTwoPersonValidationCard(
    modifier: Modifier = Modifier,
    onOpenAccreditation: () -> Unit = {},
) {
    var phase by remember { mutableStateOf(DemonstrationReviewPhase.INGESTED_PRIVATE) }
    var selectedReportIndex by remember { mutableIntStateOf(0) }
    var showSameReviewerError by remember { mutableStateOf(false) }

    val sampleReports = listOf(
        Pair(
            "REP-2026-CR-089",
            "Asalto con arma de fuego y vehículo en fuga en cuadrante San José Central. Testigo presencial aporta video de seguridad externo.",
        ),
        Pair(
            "REP-2026-CR-094",
            "Presunta inconsistencia en adjudicación de licitación SICOP #2026LN-004. Documento contractual y acta notarial adjuntos.",
        ),
    )

    val currentReport = sampleReports[selectedReportIndex]

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.5.dp, MeetColors.cyberCyan),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeetColors.cyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "PROTOCOLO DE VALIDACIÓN INDEPENDIENTE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "Regla de Dos Personas (Revisor A ≠ Revisor B)",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Text(
                        "ARTÍCULO #8",
                        color = Color(0xFFFFD54F),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Ningún reporte ciudadano se publica en el mapa abierto por sí mismo. Para evitar denuncias falsas, linchamientos o calumnias, la plataforma exige dos revisiones independientes con roles y firmas separadas.",
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )

            Spacer(Modifier.height(10.dp))

            // Barra de acceso maestro y portal de acreditación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("👑", fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "ACCESO MAESTRO: ACTIVO",
                            color = Color(0xFFFFD54F),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Button(
                    onClick = onOpenAccreditation,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Protocolo de Acreditación", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Selector de reporte en cola
            Text(
                "REPORTE EN BÓVEDA PARA EVALUACIÓN:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MeetColors.textMuted,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sampleReports.forEachIndexed { idx, pair ->
                    val isSelected = selectedReportIndex == idx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.15f) else MeetColors.backgroundDeep,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedReportIndex = idx
                                phase = DemonstrationReviewPhase.INGESTED_PRIVATE
                                showSameReviewerError = false
                            },
                    ) {
                        Text(
                            pair.first,
                            color = if (isSelected) MeetColors.cyberCyan else MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Tarjeta de datos del reporte seleccionado
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            currentReport.first,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        StateBadge(phase)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        currentReport.second,
                        fontSize = 11.sp,
                        color = MeetColors.textPrimary,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("🛡️ Play Integrity: VÁLIDO", fontSize = 9.sp, color = MeetColors.neonGreen)
                        Text("⏱️ Reloj: SIN DESFASE", fontSize = 9.sp, color = MeetColors.neonGreen)
                        Text("👥 Linaje: 1 FUENTE", fontSize = 9.sp, color = MeetColors.cyberCyan)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════════════════
            // PASO 1: REVISOR A (Analista de Hechos / Evidencia)
            // ═══════════════════════════════════════════════════════════════
            ReviewerStepCard(
                stepNumber = "1",
                roleTitle = "REVISOR A — Analista de Hechos y Evidencia",
                roleId = "ID: rev_alfa_041 (Certificado Forense)",
                description = "Evalúa coherencia espaciotemporal, autenticidad del hash SHA-256 en evidencia y descarta contradicciones físicas.",
                isCompleted = phase != DemonstrationReviewPhase.INGESTED_PRIVATE,
                isActive = phase == DemonstrationReviewPhase.INGESTED_PRIVATE,
                statusText = when (phase) {
                    DemonstrationReviewPhase.INGESTED_PRIVATE -> "Pendiente de calificación técnica"
                    DemonstrationReviewPhase.REJECTED -> "❌ Calificado como INCONSISTENTE"
                    else -> "✓ Recomendación FAVORABLE registrada (Candidato a Publicación)"
                },
                accentColor = MeetColors.cyberCyan,
            ) {
                if (phase == DemonstrationReviewPhase.INGESTED_PRIVATE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                phase = DemonstrationReviewPhase.RECOMMENDED_BY_A
                                showSameReviewerError = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Text("Aprobar Candidato", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                phase = DemonstrationReviewPhase.REJECTED
                                showSameReviewerError = false
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.error),
                            border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Text("Rechazar (Duda)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ═══════════════════════════════════════════════════════════════
            // PASO 2: REVISOR B (Autoridad Legal y Publicación)
            // ═══════════════════════════════════════════════════════════════
            ReviewerStepCard(
                stepNumber = "2",
                roleTitle = "REVISOR B — Autoridad Legal y Publicación",
                roleId = "ID: rev_beta_099 (Revisor Legal Independiente)",
                description = "Verifica salvaguardas de intimidad (blur residencial 25km), ausencia de imputación calumniosa y pertinencia legal.",
                isCompleted = phase == DemonstrationReviewPhase.AUTHORIZED_BY_B,
                isActive = phase == DemonstrationReviewPhase.RECOMMENDED_BY_A,
                statusText = when (phase) {
                    DemonstrationReviewPhase.INGESTED_PRIVATE -> "En espera del dictamen del Revisor A"
                    DemonstrationReviewPhase.RECOMMENDED_BY_A -> "Pendiente de autorización legal definitiva"
                    DemonstrationReviewPhase.REJECTED -> "Proceso desestimado"
                    DemonstrationReviewPhase.AUTHORIZED_BY_B -> "✓ PROYECCIÓN PÚBLICA AUTORIZADA (Mapa & Casos)"
                },
                accentColor = MeetColors.neonGreen,
            ) {
                if (phase == DemonstrationReviewPhase.RECOMMENDED_BY_A) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    phase = DemonstrationReviewPhase.AUTHORIZED_BY_B
                                    showSameReviewerError = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                Text("Autorizar Publicación", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // Botón de prueba de seguridad: Intento de auto-aprobación con el mismo revisor
                            OutlinedButton(
                                onClick = {
                                    showSameReviewerError = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD54F)),
                                border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                Text("Simular Revisor A = B", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        AnimatedVisibility(visible = showSameReviewerError) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MeetColors.error.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MeetColors.error),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "BLOQUEO AUTOMÁTICO: Revisor B debe ser una persona distinta a Revisor A. Se prohíbe la auto-autorización unilateral.",
                                        color = MeetColors.error,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Resultado final
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(
                    1.dp,
                    if (phase == DemonstrationReviewPhase.AUTHORIZED_BY_B) MeetColors.neonGreen else MeetColors.borderSubtle,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (phase == DemonstrationReviewPhase.AUTHORIZED_BY_B) Icons.Default.CheckCircle else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (phase == DemonstrationReviewPhase.AUTHORIZED_BY_B) MeetColors.neonGreen else MeetColors.cyberCyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ESTADO DE PROYECCIÓN TERRITORIAL:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textMuted,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (phase) {
                            DemonstrationReviewPhase.INGESTED_PRIVATE ->
                                "🔒 BÓVEDA PRIVADA: El reporte está asegurado criptográficamente. Ningún tercero en el mapa puede verlo todavía."
                            DemonstrationReviewPhase.RECOMMENDED_BY_A ->
                                "🟡 FASE CANDIDATO: Superó la validación técnica del Revisor A. Sigue en reserva hasta el visto bueno legal del Revisor B."
                            DemonstrationReviewPhase.AUTHORIZED_BY_B ->
                                "🟢 PROYECCIÓN PÚBLICA ACTIVA: Aprobado por dos revisores independientes. Visible en Mapa Territorial y Casos Públicos con blur de privacidad."
                            DemonstrationReviewPhase.REJECTED ->
                                "🔴 EN RESERVA PREVENTIVA: Desestimado por no superar criterios de consistencia o evidencia."
                        },
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                    )
                }
            }

            // Botón de reinicio de simulación
            if (phase != DemonstrationReviewPhase.INGESTED_PRIVATE) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        phase = DemonstrationReviewPhase.INGESTED_PRIVATE
                        showSameReviewerError = false
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = MeetColors.cyberCyan)
                    Spacer(Modifier.width(4.dp))
                    Text("Reiniciar Demostración", fontSize = 11.sp, color = MeetColors.cyberCyan)
                }
            }
        }
    }
}

@Composable
private fun StateBadge(phase: DemonstrationReviewPhase) {
    val (color, text) = when (phase) {
        DemonstrationReviewPhase.INGESTED_PRIVATE -> MeetColors.cyberCyan to "OBSERVED (PRIVADO)"
        DemonstrationReviewPhase.RECOMMENDED_BY_A -> Color(0xFFFFD54F) to "CANDIDATE (REVISOR A ✓)"
        DemonstrationReviewPhase.AUTHORIZED_BY_B -> MeetColors.neonGreen to "PUBLISHED (AUTORIZADO)"
        DemonstrationReviewPhase.REJECTED -> MeetColors.error to "RECHAZADO"
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ReviewerStepCard(
    stepNumber: String,
    roleTitle: String,
    roleId: String,
    description: String,
    isCompleted: Boolean,
    isActive: Boolean,
    statusText: String,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MeetColors.backgroundDeep,
        border = BorderStroke(
            1.dp,
            if (isCompleted) accentColor else if (isActive) accentColor.copy(alpha = 0.5f) else MeetColors.borderSubtle,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isCompleted) accentColor else MeetColors.cardBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stepNumber,
                            color = if (isCompleted) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            roleTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            roleId,
                            fontSize = 9.sp,
                            color = MeetColors.textMuted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                description,
                fontSize = 10.sp,
                color = MeetColors.textSecondary,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                statusText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCompleted) accentColor else MeetColors.textMuted,
            )

            if (isActive) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}
