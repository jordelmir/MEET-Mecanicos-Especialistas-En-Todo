package com.elysium369.meet.ui.screens.intelligence

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.identity.ActorCapability
import com.elysium369.meet.core.identity.AnalyticsScope
import com.elysium369.meet.core.identity.ScopeType
import com.elysium369.meet.core.intelligence.engine.AnomalyLevel
import com.elysium369.meet.core.intelligence.engine.IntelligenceEngine
import com.elysium369.meet.core.intelligence.engine.OperationalAnomaly
import com.elysium369.meet.core.operations.AutonomousOperationsEngine
import com.elysium369.meet.core.operations.CaseSeverity
import com.elysium369.meet.core.operations.OperationCase
import com.elysium369.meet.core.owner.domain.DataFreshness
import com.elysium369.meet.ui.theme.MeetColors

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E E T   E X E C U T I V E   C O M M A N D   C E N T E R
 *  ──────────────────────────────────────────────────────────────
 *  Answers: "¿Qué está ocurriendo económica y operacionalmente en Elysium?"
 *  Exclusive to platform owner and executive administrators.
 *  - GMV ≠ Revenue ≠ Profit strictly displayed.
 *  - Zero synthetic data: adheres to Rule 1 ("Never invent data").
 *  - EAOS Solo-Operator Inbox: review real exceptions, SRE/Finance incidents.
 *  - Conversational Owner Agent: query system health and pending cases.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetExecutiveCommandCenterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrustCenter: (String?) -> Unit,
    onNavigateToAgentStore: () -> Unit = {},
) {
    val engine = remember { IntelligenceEngine() }
    val opsEngine = remember { AutonomousOperationsEngine() }

    val scope = remember {
        AnalyticsScope(
            principalId = "platform-owner",
            scopeType = ScopeType.EXECUTIVE,
            capabilities = setOf(ActorCapability.VIEW_PLATFORM_INTELLIGENCE, ActorCapability.MANAGE_TRUST),
        )
    }

    val projection = remember(scope) { engine.projectExecutive(scope) }

    var operatorQueryText by remember { mutableStateOf("") }
    var operatorResponseText by remember { mutableStateOf<String?>(null) }
    var pendingCases by remember { mutableStateOf(opsEngine.listCasesRequiringOwner()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Elysium COMMAND CENTER",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "EAOS Autonomous Operations & Platform Truth",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { onNavigateToTrustCenter(null) },
                        color = MeetColors.warning.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MeetColors.warning),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MeetColors.warning, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Trust Center (${projection.trustAlertCount})",
                                color = MeetColors.warning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
        containerColor = MeetColors.backgroundDeep,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ── Section 1: Financial Truth Hero Card ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF071424)),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(MeetColors.cyberCyan, MeetColors.neonGreen))),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "VOLUMEN ECONÓMICO GLOBAL (GMV)",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                            )
                            val badgeText = when (projection.freshness) {
                                DataFreshness.LIVE -> "VERDAD AUTORITATIVA (LIVE)"
                                DataFreshness.STALE -> "DATOS EN CACHÉ"
                                DataFreshness.DEGRADED -> "TELEMETRÍA DEGRADADA"
                                DataFreshness.UNAVAILABLE -> "TELEMETRÍA NO CONECTADA"
                            }
                            val badgeColor = when (projection.freshness) {
                                DataFreshness.LIVE -> MeetColors.neonGreen
                                DataFreshness.STALE -> MeetColors.cyberCyan
                                DataFreshness.DEGRADED -> MeetColors.warning
                                DataFreshness.UNAVAILABLE -> MeetColors.textSecondary
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = badgeColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    badgeText,
                                    color = badgeColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = projection.totalGmv.formatted(),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))

                        // Revenue vs Net Breakdown
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Platform Revenue (Fee)", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(projection.platformRevenue.formatted(), color = MeetColors.cyberCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Net Revenue (Post-Refunds)", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(projection.netRevenue.formatted(), color = MeetColors.neonGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Ecosystem Vital Signs ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VitalStatBox(modifier = Modifier.weight(1f), title = "Usuarios Activos", value = "${projection.activeUsers}", icon = Icons.Default.People, tint = MeetColors.cyberCyan)
                    VitalStatBox(modifier = Modifier.weight(1f), title = "Proveedores", value = "${projection.activeProviders}", icon = Icons.Default.Engineering, tint = MeetColors.neonGreen)
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VitalStatBox(modifier = Modifier.weight(1f), title = "Viajes", value = "${projection.completedTrips}", icon = Icons.Default.DirectionsCar, tint = Color(0xFFFFB300))
                    VitalStatBox(modifier = Modifier.weight(1f), title = "Reparaciones", value = "${projection.completedServices}", icon = Icons.Default.Build, tint = Color(0xFF00E5FF))
                    VitalStatBox(modifier = Modifier.weight(1f), title = "Grúas", value = "${projection.completedTowCalls}", icon = Icons.Default.LocalShipping, tint = Color(0xFFFF5252))
                }
            }

            // ── Section 3: EAOS Solo-Operator Exception Inbox ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "INBOX DE EXCEPCIONES OPERACIONALES (EAOS)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${pendingCases.size} pendientes",
                        color = if (pendingCases.isEmpty()) MeetColors.neonGreen else MeetColors.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (pendingCases.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Cero incidentes requieren atención humana. EAOS opera con autonomía total y resiliencia SRE.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                items(pendingCases, key = { it.id }) { case ->
                    OperatorCaseCard(
                        case = case,
                        onApprove = {
                            opsEngine.resolveCaseByOwner(case.id, true, "Aprobado por el Operador")
                            pendingCases = opsEngine.listCasesRequiringOwner()
                        },
                        onReject = {
                            opsEngine.resolveCaseByOwner(case.id, false, "Rechazado por el Operador")
                            pendingCases = opsEngine.listCasesRequiringOwner()
                        }
                    )
                }
            }

            // ── Section 4: Operational Anomalies ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "ANOMALÍAS Y ALERTAS OPERACIONALES",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${projection.anomalies.size} detectadas",
                        color = MeetColors.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            items(projection.anomalies) { anomaly ->
                AnomalyCard(anomaly = anomaly)
            }

            // ── Section 5: Trust Center Integration (Truth-Verified) ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTrustCenter(null) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF101C2C)),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MeetColors.cyberCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MeetColors.cyberCyan)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AUDITORÍA EN CENTRO DE CONFIANZA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val auditSummary = if (projection.expiringDocumentsCount == 0 && projection.trustAlertCount == 0) {
                                "Documentos y alertas de riesgo al día."
                            } else {
                                "${projection.expiringDocumentsCount} documentos por vencer y ${projection.trustAlertCount} alertas de riesgo pendientes de validación."
                            }
                            Text(
                                auditSummary,
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MeetColors.textMuted)
                    }
                }
            }

            // ── Section 6: Conversational Solo-Operator Assistant ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF081525)),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "ASISTENTE CONVERSACIONAL DE OPERACIONES",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        // Quick queries chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("¿Qué necesita mi atención?", "¿Hay dinero descuadrado?", "¿Salud del sistema?").forEach { queryPrompt ->
                                Surface(
                                    modifier = Modifier.clickable {
                                        operatorQueryText = queryPrompt
                                        operatorResponseText = opsEngine.answerOwnerQuery(queryPrompt, null)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MeetColors.backgroundDark,
                                    border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
                                ) {
                                    Text(
                                        text = queryPrompt,
                                        color = MeetColors.cyberCyan,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Custom Query input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = operatorQueryText,
                                onValueChange = { operatorQueryText = it },
                                placeholder = { Text("Consultar a EAOS...", color = MeetColors.textMuted, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.cyberCyan,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (operatorQueryText.isNotBlank()) {
                                        operatorResponseText = opsEngine.answerOwnerQuery(operatorQueryText, null)
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MeetColors.cyberCyan.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = MeetColors.cyberCyan)
                            }
                        }

                        if (operatorResponseText != null) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(0.5.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = operatorResponseText!!,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun OperatorCaseCard(
    case: OperationCase,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val severityColor = when (case.severity) {
        CaseSeverity.P0 -> Color(0xFFFF3B30)
        CaseSeverity.P1 -> Color(0xFFFF9500)
        CaseSeverity.P2 -> Color(0xFFFFCC00)
        CaseSeverity.P3 -> MeetColors.cyberCyan
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = severityColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, severityColor)
                ) {
                    Text(
                        text = "${case.severity.name} • ${case.domain}",
                        color = severityColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "Requiere Decisión",
                    color = MeetColors.warning,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(case.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(case.whatHappened, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)

            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MeetColors.backgroundDeep,
            ) {
                Text(
                    text = "Acción: ${case.requestedOwnerAction}",
                    color = MeetColors.cyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("Aprobar", color = MeetColors.backgroundDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30)),
                    border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("Rechazar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VitalStatBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AnomalyCard(anomaly: OperationalAnomaly) {
    val borderColor = when (anomaly.level) {
        AnomalyLevel.CRITICAL -> Color(0xFFFF3B30)
        AnomalyLevel.HIGH -> Color(0xFFFF9500)
        AnomalyLevel.WARNING -> Color(0xFFFFCC00)
        AnomalyLevel.INFO -> MeetColors.cyberCyan
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    anomaly.domain.uppercase(),
                    color = borderColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    anomaly.level.name,
                    color = borderColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(anomaly.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(anomaly.message, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
