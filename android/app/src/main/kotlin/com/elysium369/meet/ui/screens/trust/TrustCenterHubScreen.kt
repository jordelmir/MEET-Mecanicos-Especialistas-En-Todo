package com.elysium369.meet.ui.screens.trust

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.identity.ScopeType
import com.elysium369.meet.core.identity.TrustScope
import com.elysium369.meet.core.trust.engine.ComplianceStatus
import com.elysium369.meet.core.trust.engine.DocumentRecord
import com.elysium369.meet.core.trust.engine.TrustCenterEngine
import com.elysium369.meet.ui.theme.MeetColors

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E E T   T R U S T   C E N T E R   H U B
 *  ──────────────────────────────────────────────────────────────
 *  Answers: "¿Puedo confiar y permitir operar a este actor?"
 *  - Identity, compliance, document validity, and transparent risk scoring.
 *  - Bidirectional link to Command Center.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustCenterHubScreen(
    principalId: String,
    organizationId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCommandCenter: () -> Unit,
) {
    val engine = remember { TrustCenterEngine() }
    val scope = remember(principalId, organizationId) {
        TrustScope(
            principalId = principalId,
            organizationId = organizationId,
            scopeType = if (organizationId != null) ScopeType.FLEET else ScopeType.DRIVER,
            canManageTrust = false,
        )
    }

    val profile = remember(scope, principalId, organizationId) {
        engine.getProfile(scope, principalId, organizationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "CENTRO DE CONFIANZA",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            "Identity, Compliance & Safety Verification",
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

            // ── Section 1: Trust Score Card ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1924)),
                    border = BorderStroke(1.5.dp, MeetColors.neonGreen.copy(alpha = 0.8f)),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "NIVEL DE CONFIANZA Y SEGURIDAD",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "ESTADO: OPERACIÓN HABILITADA",
                                    color = MeetColors.neonGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(MeetColors.neonGreen.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${profile.riskScore.score}",
                                    color = MeetColors.neonGreen,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Divider(color = MeetColors.borderSubtle.copy(alpha = 0.5f))
                        Spacer(Modifier.height(14.dp))

                        Text("Evidencia y Justificación Transparente:", color = MeetColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        for (evidence in profile.riskScore.evidenceList) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("✓", color = MeetColors.neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.width(8.dp))
                                Text(evidence, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ── Section 2: Document Compliance List ──
            item {
                Text(
                    "EXPEDIENTE Y DOCUMENTACIÓN OFICIAL",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            items(profile.documents) { doc ->
                DocumentComplianceCard(doc = doc)
            }

            // ── Section 3: Bidirectional Link to Command Center ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCommandCenter() },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A28)),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("EXPLORAR EN COMMAND CENTER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Ver telemetría, facturación económica y rendimiento operacional.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MeetColors.textMuted)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DocumentComplianceCard(doc: DocumentRecord) {
    val statusColor = when (doc.status) {
        ComplianceStatus.VALID -> MeetColors.neonGreen
        ComplianceStatus.EXPIRING_SOON -> MeetColors.warning
        ComplianceStatus.EXPIRED, ComplianceStatus.REVOKED -> Color(0xFFFF3B30)
        ComplianceStatus.PENDING_REVIEW -> MeetColors.cyberCyan
    }

    val statusLabel = when (doc.status) {
        ComplianceStatus.VALID -> "VIGENTE"
        ComplianceStatus.EXPIRING_SOON -> "VENCE EN ${doc.daysUntilExpiration} DÍAS"
        ComplianceStatus.EXPIRED -> "VENCIDO"
        ComplianceStatus.REVOKED -> "REVOCADO"
        ComplianceStatus.PENDING_REVIEW -> "EN REVISIÓN"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(doc.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Surface(shape = RoundedCornerShape(4.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        statusLabel,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Emisor: ${doc.issuer}", color = MeetColors.textMuted, fontSize = 11.sp)
        }
    }
}
