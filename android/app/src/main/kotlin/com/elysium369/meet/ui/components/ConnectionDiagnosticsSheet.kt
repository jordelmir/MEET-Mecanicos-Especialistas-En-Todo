package com.elysium369.meet.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.*
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDiagnosticsSheet(
    viewModel: ObdViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val truth by viewModel.connectionTruth.collectAsState()
    val rawTrace = remember(truth) { viewModel.getConnectionTrace() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MeetColors.backgroundDark,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MeetColors.textMuted.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DIAGNÓSTICO FORENSE DE ENLACE V3",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Núcleo de intención humana + verdad física",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp
                    )
                }

                val intentColor = if (truth.intent == ConnectionIntent.CONNECT_REQUESTED) MeetColors.neonGreen else MeetColors.error
                Surface(
                    color = intentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, intentColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = truth.intent.name,
                        color = intentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Scrollable Diagnostics Content ──
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Orthogonal Layers Breakdown
                item {
                    Text(
                        text = "ESTADOS ORTOGONALES POR CAPA",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Layer 3: Physical Transport
                item {
                    DiagnosticLayerCard(
                        layerName = "L3 · Transporte Físico",
                        status = when (val t = truth.transportState) {
                            is TransportLinkState.Connected -> "CONECTADO"
                            is TransportLinkState.Connecting -> "CONECTANDO..."
                            is TransportLinkState.RemoteClosed -> "CIERRE REMOTO (EOF)"
                            is TransportLinkState.IoFailure -> "FALLO I/O"
                            is TransportLinkState.Closing -> "CERRANDO"
                            is TransportLinkState.Disconnected -> "DESCONECTADO"
                        },
                        statusColor = when (truth.transportState) {
                            is TransportLinkState.Connected -> MeetColors.neonGreen
                            is TransportLinkState.Connecting -> MeetColors.warning
                            else -> MeetColors.error
                        },
                        details = listOf(
                            "Adaptador" to (truth.adapterName ?: truth.adapterAddress ?: "No seleccionado"),
                            "Dirección" to (truth.adapterAddress ?: "N/A"),
                            "Última actividad RX" to (truth.transportLastRxMonotonicMs?.let { "${it}ms" } ?: "—"),
                            "Última actividad TX" to (truth.transportLastTxMonotonicMs?.let { "${it}ms" } ?: "—")
                        )
                    )
                }

                // Layer 1: ELM Adapter
                item {
                    DiagnosticLayerCard(
                        layerName = "L1 · Adaptador ELM327",
                        status = truth.elmState.name,
                        statusColor = when (truth.elmState) {
                            ElmLinkState.READY, ElmLinkState.NOT_APPLICABLE -> MeetColors.neonGreen
                            ElmLinkState.SYNCING -> MeetColors.warning
                            else -> MeetColors.textMuted
                        },
                        details = listOf(
                            "Identidad" to (truth.elmIdentity ?: "No identificada"),
                            "Última respuesta válida" to (truth.elmLastProofMonotonicMs?.let { "${it}ms" } ?: "—")
                        )
                    )
                }

                // Layer 2: Protocol & Bus
                item {
                    DiagnosticLayerCard(
                        layerName = "L2 · Protocolo & Bus del Vehículo",
                        status = truth.protocolState.name,
                        statusColor = when (truth.protocolState) {
                            ProtocolLinkState.ACTIVE -> MeetColors.neonGreen
                            ProtocolLinkState.NEGOTIATING -> MeetColors.warning
                            else -> MeetColors.textMuted
                        },
                        details = listOf(
                            "Protocolo activo" to (truth.protocol?.displayName ?: "Auto-detect / Desconocido"),
                            "Código ATSP" to (truth.protocol?.atspCode ?: "—")
                        )
                    )
                }

                // Layer 0: ECU & Telemetry
                item {
                    DiagnosticLayerCard(
                        layerName = "L0 · ECU & Telemetría",
                        status = truth.ecuState.name,
                        statusColor = when (truth.ecuState) {
                            EcuLinkState.RESPONSIVE -> MeetColors.neonGreen
                            EcuLinkState.NO_RESPONSE -> MeetColors.warning
                            else -> MeetColors.textMuted
                        },
                        details = listOf(
                            "Estado Telemetría" to truth.telemetryState.name,
                            "Última muestra" to (truth.telemetryLastSampleMonotonicMs?.let { "${it}ms" } ?: "—"),
                            "Sesión Lista (Semántica)" to if (truth.isSessionReady) "SÍ (Verificada)" else "NO"
                        )
                    )
                }

                // 2. Recovery & Statistics
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "MÉTRICAS DE RECUPERACIÓN & EVENTOS",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MeetColors.cardBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MeetColors.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricRow("Recuperaciones L1 (Sync ELM)", "${truth.softRecoveryCount}")
                            MetricRow("Recuperaciones L2 (Protocolo)", "${truth.protocolRecoveryCount}")
                            MetricRow("Pérdidas físicas L3 (Remote Closed)", "${truth.physicalLinkLossCount}")
                            MetricRow("Causa última desconexión", truth.disconnectReason?.name ?: "Ninguna")
                            MetricRow("Attempt ID / Generación", "${truth.attemptId?.take(8) ?: "—"} / gen ${truth.attemptGeneration}")
                        }
                    }
                }

                // 3. Raw Forensic Trace
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRAZA MONOTÓNICA REDACTADA",
                            color = MeetColors.textMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${rawTrace.size} eventos",
                            color = MeetColors.textSecondary,
                            fontSize = 9.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MeetColors.borderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            if (rawTrace.isEmpty()) {
                                Text(
                                    "No hay eventos registrados en la sesión actual.",
                                    color = MeetColors.textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                rawTrace.takeLast(35).forEach { line ->
                                    Text(
                                        text = line,
                                        color = if (line.startsWith("METRICS")) MeetColors.hotMagenta else if (line.contains("READY") || line.contains("OK")) MeetColors.neonGreen else if (line.contains("LOST") || line.contains("ERROR") || line.contains("CANCEL")) MeetColors.warning else Color.LightGray,
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Bottom Action Button ──
            Button(
                onClick = {
                    val fullTraceText = rawTrace.joinToString("\n")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("MEET OBD Trace", fullTraceText)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Traza de enlace copiada al portapapeles", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "COPIAR REGISTRO FORENSE COMPLETO",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DiagnosticLayerCard(
    layerName: String,
    status: String,
    statusColor: Color,
    details: List<Pair<String, String>>
) {
    Surface(
        color = MeetColors.cardBackground,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MeetColors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(layerName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = status,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            details.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = MeetColors.textSecondary, fontSize = 9.sp)
                    Text(value, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MeetColors.textSecondary, fontSize = 9.sp)
        Text(value, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
