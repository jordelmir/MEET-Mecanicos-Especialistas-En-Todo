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

/**
 * Backward-compatible entrypoint delegating to [SafetyTripartiteValidationCard].
 */
@Composable
fun SafetyTwoPersonValidationCard(
    modifier: Modifier = Modifier,
    onOpenAccreditation: () -> Unit = {},
) {
    SafetyTripartiteValidationCard(
        modifier = modifier,
        onOpenAccreditation = onOpenAccreditation,
    )
}

/**
 * Interactive Parliamentary & Academic Console for Tripartite Asymmetric Validation.
 *
 * Implements the Epistemic & Institutional Tripartite Architecture:
 * - Grupo A (Prensa & Investigación Documental, TRUST_REVIEWER)
 * - Grupo B (Juristas, Especialistas en Derecho & DDHH, LEGAL_REVIEWER)
 * - Grupo C (Autoridades Públicas & Órganos Jurisdiccionales, AUTHORITY_REVIEWER - Demo / Reserva)
 *
 * Regla de Operación Asimétrica:
 * - Basta 1 de los 3 validadores calificados para que el reporte sea proyectado al mapa público.
 * - El carril C es opcional, demostrativo y no bloqueante (Principio de No-Veto Ciudadano).
 * - En el mapa y en la consola, se muestran claramente qué validadores certificaron el reporte.
 */
@Composable
fun SafetyTripartiteValidationCard(
    modifier: Modifier = Modifier,
    onOpenAccreditation: () -> Unit = {},
) {
    var isValidatedA by remember { mutableStateOf(false) }
    var isValidatedB by remember { mutableStateOf(false) }
    var isValidatedC by remember { mutableStateOf(false) }
    var isRejected by remember { mutableStateOf(false) }
    var selectedReportIndex by remember { mutableIntStateOf(0) }
    var showSameReviewerError by remember { mutableStateOf(false) }

    val sampleReports = listOf(
        Pair(
            "REP-2026-CR-089",
            "Asalto con arma de fuego y vehículo en fuga en cuadrante San José Central. Testigo presencial aporta video de seguridad externo con hash inmutable.",
        ),
        Pair(
            "REP-2026-CR-094",
            "Presunta inconsistencia en adjudicación de licitación SICOP #2026LN-004. Documento contractual y acta notarial adjuntos con sellos temporales.",
        ),
    )

    val currentReport = sampleReports[selectedReportIndex]
    val hasAnyValidation = !isRejected && (isValidatedA || isValidatedB || isValidatedC)

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
                            "PROTOCOLO DE VALIDACIÓN TRIPARTITA ASIMÉTRICA",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            "Estamentos A (Prensa), B (Juristas) y C (Autoridades)",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
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

            // Banner de Soberanía Ciudadana & Principio de No-Veto
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        tint = MeetColors.cyberCyan,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "PRINCIPIO DE NO-VETO: Con la validación de cualquiera de los 3 estamentos (A, B o C), el caso pasa al mapa territorial. El carril estatal C es opcional y de demostración; la inacción o demora gubernamental nunca censura la alerta ciudadana.",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }

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

            Spacer(Modifier.height(12.dp))

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
                                isValidatedA = false
                                isValidatedB = false
                                isValidatedC = false
                                isRejected = false
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
                        TripartiteStateBadge(
                            isValidatedA = isValidatedA,
                            isValidatedB = isValidatedB,
                            isValidatedC = isValidatedC,
                            isRejected = isRejected,
                        )
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("🛡️ Play Integrity: VÁLIDO", fontSize = 9.sp, color = MeetColors.neonGreen)
                        Text("⏱️ Reloj: SIN DESFASE", fontSize = 9.sp, color = MeetColors.neonGreen)
                        Text("👥 Linaje: 1 FUENTE", fontSize = 9.sp, color = MeetColors.cyberCyan)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ═══════════════════════════════════════════════════════════════
            // PASO 1: REVISOR A (Prensa & Investigación Documental)
            // ═══════════════════════════════════════════════════════════════
            ReviewerStepCard(
                stepNumber = "A",
                roleTitle = "GRUPO A — Periodistas, Medios & Investigadores",
                roleId = "ID: rev_prensa_041 (Colegiatura / Medios Acreditados)",
                description = "Evalúa coherencia espaciotemporal, autenticidad del hash SHA-256 en evidencia audiovisual y corrobora testimonios independientes.",
                isCompleted = isValidatedA,
                isActive = !isValidatedA && !isRejected,
                statusText = when {
                    isRejected -> "❌ Proceso desestimado por inconsistencias"
                    isValidatedA -> "✓ VALIDACIÓN PERIODÍSTICA REGISTRADA (Alerta Temprana en Mapa)"
                    else -> "Pendiente de calificación periodística"
                },
                accentColor = MeetColors.cyberCyan,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            isValidatedA = true
                            isRejected = false
                            showSameReviewerError = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Text("Aprobar Validación A", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            isRejected = true
                            isValidatedA = false
                            isValidatedB = false
                            isValidatedC = false
                            showSameReviewerError = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.error),
                        border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Text("Rechazar (Inconsistente)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ═══════════════════════════════════════════════════════════════
            // PASO 2: REVISOR B (Juristas & Especialistas en Derecho / DDHH)
            // ═══════════════════════════════════════════════════════════════
            ReviewerStepCard(
                stepNumber = "B",
                roleTitle = "GRUPO B — Abogados, Juristas & DDHH",
                roleId = "ID: rev_legal_099 (Colegio de Abogados / Clínicas Jurídicas)",
                description = "Verifica salvaguardas de intimidad (blur residencial 25km), ausencia de imputación calumniosa y tipificación penal formal.",
                isCompleted = isValidatedB,
                isActive = !isValidatedB && !isRejected,
                statusText = when {
                    isRejected -> "❌ Proceso desestimado"
                    isValidatedB -> "✓ VALIDACIÓN JURÍDICA REGISTRADA (Consistencia Legal en Mapa)"
                    else -> "Pendiente de calificación legal independiente"
                },
                accentColor = MeetColors.neonGreen,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                isValidatedB = true
                                isRejected = false
                                showSameReviewerError = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Text("Aprobar Validación B", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                    "BLOQUEO AUTOMÁTICO: Revisor B debe ser una persona distinta a Revisor A. Se prohíbe la auto-autorización unilateral entre estamentos.",
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

            Spacer(Modifier.height(10.dp))

            // ═══════════════════════════════════════════════════════════════
            // PASO 3: REVISOR C (Autoridades Públicas - Demo / Reserva)
            // ═══════════════════════════════════════════════════════════════
            ReviewerStepCard(
                stepNumber = "C",
                roleTitle = "GRUPO C — Autoridades Públicas & Órganos Jurisdiccionales",
                roleId = "CONVENIO: Pendiente de Ratificación (Poder Judicial, OIJ, Fiscalía)",
                description = "Carril institucional reservado para convenios formales de interconexión. Muestra a diputados y jerarcas cómo su participación se integra al ecosistema sin condicionar la alerta civil.",
                isCompleted = isValidatedC,
                isActive = true,
                statusText = when {
                    isValidatedC -> "✓ ADHESIÓN ESTATAL SIMULADA (Validación Oficial en Demostración)"
                    else -> "🔒 EN RESERVA DE CONVENIO MARCO — MODO DEMOSTRACIÓN PARLAMENTARIA"
                },
                accentColor = Color(0xFFFFD54F),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MeetColors.backgroundDeep,
                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🏛️", fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "CANAL DE DEMOSTRACIÓN PARA DIPUTADOS",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFD54F).copy(alpha = 0.2f),
                            ) {
                                Text(
                                    "NO BLOQUEANTE",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Text(
                            "La plataforma nunca espera por trámites burocráticos. Si el Estado no valida, el reporte se proyecta con A o B. Este botón permite mostrar la respuesta estatal en audiencias legislativas.",
                            color = MeetColors.textSecondary,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    isValidatedC = !isValidatedC
                                    if (isValidatedC) isRejected = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isValidatedC) MeetColors.neonGreen else Color(0xFFFFD54F),
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 7.dp),
                            ) {
                                Icon(
                                    if (isValidatedC) Icons.Default.Check else Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isValidatedC) "Revocar Simulación C" else "Simular Adhesión Estatal (C)",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Resultado final de Proyección Territorial
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeetColors.backgroundDeep,
                border = BorderStroke(
                    1.dp,
                    if (hasAnyValidation) MeetColors.neonGreen else MeetColors.borderSubtle,
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
                            Icon(
                                if (hasAnyValidation) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (hasAnyValidation) MeetColors.neonGreen else MeetColors.cyberCyan,
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

                        if (hasAnyValidation) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MeetColors.neonGreen),
                            ) {
                                Text(
                                    "EN MAPA ABIERTO",
                                    color = MeetColors.neonGreen,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        when {
                            isRejected ->
                                "🔴 EN RESERVA PREVENTIVA: Desestimado por no superar criterios de consistencia o evidencia objetiva."
                            hasAnyValidation -> {
                                val activeBadges = mutableListOf<String>()
                                if (isValidatedA) activeBadges.add("[A: Prensa]")
                                if (isValidatedB) activeBadges.add("[B: Jurídico]")
                                if (isValidatedC) activeBadges.add("[C: Autoridad]")
                                "🟢 PROYECCIÓN PÚBLICA ACTIVA: Superó el umbral de validación independiente con sellos: ${activeBadges.joinToString(" + ")}. Visible en Mapa Territorial y Ficha Pública con salvaguarda de intimidad."
                            }
                            else ->
                                "🔒 BÓVEDA PRIVADA: El reporte está asegurado criptográficamente. Requiere al menos 1 validación de A, B o C para ingresar al mapa territorial público."
                        },
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                    )

                    // Sellos interactivos proyectados
                    if (hasAnyValidation) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isValidatedA) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MeetColors.cyberCyan.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, MeetColors.cyberCyan),
                                ) {
                                    Text(
                                        "📰 Sello A: Prensa",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            if (isValidatedB) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MeetColors.neonGreen.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, MeetColors.neonGreen),
                                ) {
                                    Text(
                                        "⚖️ Sello B: Juristas",
                                        color = MeetColors.neonGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            if (isValidatedC) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFD54F).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                                ) {
                                    Text(
                                        "🏛️ Sello C: Autoridad (Demo)",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Botón de reinicio de simulación
            if (isValidatedA || isValidatedB || isValidatedC || isRejected) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        isValidatedA = false
                        isValidatedB = false
                        isValidatedC = false
                        isRejected = false
                        showSameReviewerError = false
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = MeetColors.cyberCyan)
                    Spacer(Modifier.width(4.dp))
                    Text("Reiniciar Simulación Tripartita", fontSize = 11.sp, color = MeetColors.cyberCyan)
                }
            }
        }
    }
}

@Composable
private fun TripartiteStateBadge(
    isValidatedA: Boolean,
    isValidatedB: Boolean,
    isValidatedC: Boolean,
    isRejected: Boolean,
) {
    val (color, text) = when {
        isRejected -> MeetColors.error to "RECHAZADO"
        isValidatedA && isValidatedB && isValidatedC -> Color(0xFFFFD54F) to "TRIPARTITO (A+B+C)"
        isValidatedA && isValidatedB -> MeetColors.neonGreen to "CIVIL PLENO (A+B)"
        isValidatedA && isValidatedC -> Color(0xFF69F0AE) to "PRENSA + ESTADO (A+C)"
        isValidatedB && isValidatedC -> Color(0xFF82B1FF) to "LEGAL + ESTADO (B+C)"
        isValidatedA -> MeetColors.cyberCyan to "PRENSA (A ✓)"
        isValidatedB -> MeetColors.neonGreen to "LEGAL (B ✓)"
        isValidatedC -> Color(0xFFFFD54F) to "ESTATAL (C ✓ DEMO)"
        else -> MeetColors.cyberCyan to "OBSERVED (PRIVADO)"
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
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isCompleted) accentColor else MeetColors.cardBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stepNumber,
                            color = if (isCompleted) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
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
