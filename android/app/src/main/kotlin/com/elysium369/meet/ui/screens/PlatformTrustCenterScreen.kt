package com.elysium369.meet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.TrustVerificationApplication
import com.elysium369.meet.ride.domain.PlatformOwnerAccess
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.HolographicBackgroundShared
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformTrustCenterScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
) {
    val access by viewModel.platformOwnerAccess.collectAsState()
    var filter by remember { mutableStateOf("PENDING") }
    var applications by remember { mutableStateOf<List<TrustVerificationApplication>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDecision by remember {
        mutableStateOf<Pair<TrustVerificationApplication, String>?>(null)
    }
    val scope = rememberCoroutineScope()

    fun reload() {
        if (access != PlatformOwnerAccess.GRANTED) return
        scope.launch {
            loading = true
            message = null
            runCatching { PlatformTrustCenterGateway.loadQueue(filter) }
                .onSuccess { applications = it }
                .onFailure {
                    applications = emptyList()
                    message = "No se pudo cargar la cola. La autorización falló cerrada."
                }
            loading = false
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshPlatformOwnerAccess() }
    LaunchedEffect(access, filter) {
        if (access == PlatformOwnerAccess.GRANTED) reload()
    }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Centro de Confianza", color = Color.White, fontWeight = FontWeight.Black)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MeetColors.cyberCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            HolographicBackgroundShared()
            when (access) {
                PlatformOwnerAccess.UNKNOWN -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MeetColors.neonGreen,
                )
                PlatformOwnerAccess.GRANTED -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        EliteCard(
                            glowColor = MeetColors.neonGreen,
                            borderColor = MeetColors.neonGreen.copy(alpha = 0.35f),
                            backgroundColor = MeetColors.cardBackground,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Text("AUTORIDAD DE PLATAFORMA CONFIRMADA", color = MeetColors.neonGreen, fontWeight = FontWeight.Black)
                                Text(
                                    "Acceso concedido por backend. Cada decisión exige motivo y queda auditada.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("PENDING", "APPROVED", "REJECTED", "SUSPENDED").forEach { status ->
                                FilterChip(
                                    selected = filter == status,
                                    onClick = { filter = status },
                                    label = { Text(statusLabel(status), fontSize = 11.sp) },
                                )
                            }
                        }
                    }
                    message?.let { item { Text(it, color = MeetColors.warning) } }
                    if (loading) {
                        item { CircularProgressIndicator(color = MeetColors.cyberCyan) }
                    } else if (applications.isEmpty()) {
                        item {
                            Text(
                                "No hay registros en esta cola.",
                                color = MeetColors.textSecondary,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    } else {
                        items(applications, key = { it.id }) { application ->
                            TrustApplicationCard(
                                application = application,
                                onDecision = { decision -> pendingDecision = application to decision },
                            )
                        }
                    }
                }
                else -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("ACCESO DENEGADO", color = MeetColors.error, fontWeight = FontWeight.Black)
                    Text(
                        "Esta sección solo existe para la cuenta maestra confirmada por el servidor.",
                        color = MeetColors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    pendingDecision?.let { (application, decision) ->
        TrustDecisionDialog(
            application = application,
            decision = decision,
            onDismiss = { pendingDecision = null },
            onConfirm = { reason ->
                pendingDecision = null
                scope.launch {
                    loading = true
                    runCatching {
                        PlatformTrustCenterGateway.decide(application.id, decision, reason)
                    }.onSuccess {
                        message = "Decisión ${statusLabel(decision).lowercase()} registrada y auditada."
                        reload()
                    }.onFailure {
                        message = "La decisión no se guardó. No se modificó el estado del registro."
                        loading = false
                    }
                }
            },
        )
    }
}

@Composable
private fun TrustApplicationCard(
    application: TrustVerificationApplication,
    onDecision: (String) -> Unit,
) {
    EliteCard(
        glowColor = statusColor(application.status),
        borderColor = statusColor(application.status).copy(alpha = 0.35f),
        backgroundColor = MeetColors.cardBackground,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(application.displayName, color = Color.White, fontWeight = FontWeight.Black)
                    Text(serviceLabel(application.serviceType), color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                }
                Text(statusLabel(application.status), color = statusColor(application.status), fontWeight = FontWeight.Black)
            }
            application.businessName?.let { TrustLine("Negocio", it) }
            application.applicantEmail?.let { TrustLine("Cuenta", it) }
            application.phone?.let { TrustLine("Teléfono", it) }
            application.locationLabel?.let { TrustLine("Zona", it) }
            application.licenseReference?.let { TrustLine("Licencia/registro", it) }
            TrustLine("Solicitud", application.submittedAt)
            TrustLine(
                "Evidencia",
                application.evidenceManifestSha256?.let { "Manifiesto SHA-256 ${it.take(12)}…" }
                    ?: "No aportada; no aprobar sin validación suficiente",
            )
            application.decisionReason?.let { TrustLine("Motivo", it) }
            if (application.status == "PENDING") {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onDecision("APPROVED") },
                        enabled = application.evidenceManifestSha256 != null || application.licenseReference != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                    ) { Text("APROBAR", fontWeight = FontWeight.Black) }
                    OutlinedButton(onClick = { onDecision("REJECTED") }) { Text("RECHAZAR") }
                    TextButton(onClick = { onDecision("SUSPENDED") }) { Text("SUSPENDER") }
                }
            }
        }
    }
}

@Composable
private fun TrustLine(label: String, value: String) {
    Text(
        "$label: $value",
        color = MeetColors.textSecondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun TrustDecisionDialog(
    application: TrustVerificationApplication,
    decision: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${statusLabel(decision)} · ${application.displayName}") },
        text = {
            Column {
                Text("El motivo será permanente en el historial de auditoría.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Motivo y evidencia revisada") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.trim().length >= 3,
            ) { Text("CONFIRMAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } },
    )
}

private fun serviceLabel(type: String): String = when (type) {
    "PASSENGER" -> "Pasajero"
    "RIDE_DRIVER" -> "Chofer de viajes"
    "TOW_TRUCK" -> "Grúa / asistencia vial"
    "MECHANIC" -> "Mecánico / taller"
    "PARTS_STORE" -> "Repuestera"
    "SERVICE_PROVIDER" -> "Proveedor de servicios"
    else -> type
}

private fun statusLabel(status: String): String = when (status) {
    "PENDING" -> "Pendientes"
    "APPROVED" -> "Aprobados"
    "REJECTED" -> "Rechazados"
    "SUSPENDED" -> "Suspendidos"
    else -> status
}

private fun statusColor(status: String): Color = when (status) {
    "APPROVED" -> MeetColors.neonGreen
    "REJECTED" -> MeetColors.error
    "SUSPENDED" -> MeetColors.warning
    else -> MeetColors.cyberCyan
}
