package com.elysium369.meet.ui.screens.vehicleaccess

import com.elysium369.meet.ui.navigation.backOrHome

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.vehicleaccess.application.VehicleAccessManager
import com.elysium369.meet.core.vehicleaccess.domain.*
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleAccessDashboardScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val accessManager = viewModel.vehicleAccessManager
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val phoneCaps by accessManager.phoneCapabilities.collectAsState()
    val vehicleCaps by accessManager.vehicleCapabilities.collectAsState()
    val credentials by accessManager.credentials.collectAsState()
    val grants by accessManager.grants.collectAsState()
    val auditEvents by accessManager.auditTimeline.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showNewGrantDialog by remember { mutableStateOf(false) }
    var newGrantName by remember { mutableStateOf("") }
    var newGrantRole by remember { mutableStateOf("Familiar") }

    LaunchedEffect(selectedVehicle) {
        val vehicle = selectedVehicle
        if (vehicle != null) {
            accessManager.initializeForVehicle(
                vehicleId = vehicle.id,
                make = vehicle.make,
                model = vehicle.model,
                year = vehicle.year,
                vin = vehicle.vin
            )
        }
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "VANGUARD ACCESS & IMMO",
                onBackClick = { navController.backOrHome() },
                actions = {
                    IconButton(onClick = { navController.navigate("messages?serviceVertical=vehicle_access") }) {
                        Icon(Icons.Default.Chat, "Mensajes del servicio", tint = MeetColors.cyberCyan)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── 1. VEHICLE ACCESS IDENTITY CARD ──
            item {
                EliteCard(
                    backgroundColor = MeetColors.backgroundDark,
                    borderColor = if (selectedVehicle != null) MeetColors.neonGreen.copy(alpha = 0.4f) else MeetColors.textMuted.copy(alpha = 0.3f),
                    glowColor = if (selectedVehicle != null) MeetColors.neonGreen else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🗝️", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (selectedVehicle != null) "${selectedVehicle?.make} ${selectedVehicle?.model} (${selectedVehicle?.year})" else "Ningún vehículo seleccionado",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "VIN: ${selectedVehicle?.vin?.ifBlank { "PENDIENTE POR OBD" } ?: "NO ASIGNADO"}",
                                    color = MeetColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (selectedVehicle != null) MeetColors.neonGreen.copy(alpha = 0.15f) else MeetColors.textMuted.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selectedVehicle != null) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.textMuted.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (selectedVehicle != null) "INICIALIZADO" else "SIN VEHÍCULO",
                                    color = if (selectedVehicle != null) MeetColors.neonGreen else MeetColors.textMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MeetColors.textMuted.copy(alpha = 0.2f))
                        Spacer(Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Protocolo IMMO:", color = MeetColors.textMuted, fontSize = 12.sp)
                            Text(vehicleCaps?.immoProtocol ?: "No detectado / Requiere OBD", color = MeetColors.cyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── 2. PHONE VS VEHICLE CAPABILITY MATRIX ──
            item {
                EliteCard(
                    backgroundColor = MeetColors.backgroundDark,
                    borderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "MATRIZ DE CAPACIDADES DE HARDWARE (HONEST FAIL-CLOSED)",
                            color = MeetColors.electricBlue,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(12.dp))

                        CapabilityRow("NFC / HCE Tap-to-Unlock", phoneCaps.hasNfc && phoneCaps.hasHce, vehicleCaps?.nfcSupport ?: CapabilityState.UNSUPPORTED)
                        CapabilityRow("Bluetooth LE Proximity", phoneCaps.hasBle && phoneCaps.canAdvertiseBle, vehicleCaps?.bleSupport ?: CapabilityState.UNSUPPORTED)
                        CapabilityRow("UWB Passive Entry (Ultra-Wideband)", phoneCaps.hasUwb, vehicleCaps?.uwbSupport ?: CapabilityState.UNSUPPORTED)
                        CapabilityRow("Google Wallet Car Key Provisioning", phoneCaps.walletAvailability, vehicleCaps?.walletProvisioningSupport ?: CapabilityState.CONDITIONAL)
                        CapabilityRow("Bloqueo Seguro de Pantalla (Biometría)", phoneCaps.hasSecureScreenLock, CapabilityState.SUPPORTED)
                    }
                }
            }

            // ── 3. DIGITAL TWIN OF REGISTERED KEYS ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("INVENTARIO & GEMELO DIGITAL DE LLAVES", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text("${credentials.size} REGISTRADAS", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }

            items(credentials, key = { it.credentialId }) { cred ->
                EliteCard(
                    backgroundColor = MeetColors.backgroundDark,
                    borderColor = if (cred.status == CredentialStatus.LOST) MeetColors.error else MeetColors.neonGreen.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (cred.type == CredentialType.SMART_KEY) "📟" else "🔑", fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cred.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${cred.type.displayName} · ${cred.authority.displayName}",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background((if (cred.status == CredentialStatus.LOST) MeetColors.error else MeetColors.neonGreen).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    cred.status.displayName.uppercase(),
                                    color = if (cred.status == CredentialStatus.LOST) MeetColors.error else MeetColors.neonGreen,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Transponder: ${cred.transponderFamily ?: "N/A"}", color = MeetColors.textMuted, fontSize = 11.sp)
                            if (cred.batteryHealthPercent != null) {
                                Text("Pila Mando: ${cred.batteryHealthPercent}%", color = if (cred.batteryHealthPercent > 30) MeetColors.neonGreen else MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (cred.status != CredentialStatus.LOST) {
                            Spacer(Modifier.height(10.dp))
                            EliteOutlinedButton(
                                text = "REPORTAR COMO EXTRAVIADA / BLOQUEAR",
                                onClick = {
                                    accessManager.markKeyLost(cred.credentialId)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Llave marcada como extraviada en MEET. El inmovilizador no fue reprogramado.")
                                    }
                                },
                                color = MeetColors.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── 4. ACCESS SHARING & TEMPORARY GRANTS ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ACCESOS COMPARTIDOS (VALET / TALLER / FLOTA)", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    EliteTextButton(
                        text = "+ NUEVO ACCESO",
                        onClick = { showNewGrantDialog = true },
                        color = MeetColors.cyberCyan
                    )
                }
            }

            if (grants.isEmpty()) {
                item {
                    EliteCard(
                        backgroundColor = MeetColors.backgroundDark,
                        borderColor = MeetColors.textMuted.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No hay accesos temporales activos. Puedes emitir un permiso seguro para un familiar, valet parking o taller mecánico con tiempo de expiración.",
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(grants, key = { it.grantId }) { grant ->
                    EliteCard(
                        backgroundColor = MeetColors.backgroundDark,
                        borderColor = if (grant.status == CredentialStatus.REVOKED) MeetColors.textMuted.copy(alpha = 0.2f) else MeetColors.cyberCyan.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👤", fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(grant.recipientName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Rol: ${grant.recipientRole} · ${if (grant.isVehicleEnforced) "Enforced by OEM" else "MEET Policy"}", color = MeetColors.textSecondary, fontSize = 11.sp)
                                }
                                if (grant.status != CredentialStatus.REVOKED) {
                                    EliteTextButton(
                                        text = "REVOCAR",
                                        onClick = {
                                            accessManager.revokeGrant(grant.grantId, "Revocado por propietario")
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Acceso revocado en MEET; confirma aparte la revocación OEM si aplica.")
                                            }
                                        },
                                        color = MeetColors.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 5. SECURE QUICK COMMANDS (1-TAP) ──
            item {
                Text("COMANDOS SEGUROS DE ACCESO", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EliteButton(
                        text = "🔒 BLOQUEAR",
                        onClick = {
                            accessManager.executeQuickAccessCommand("BLOQUEO TOTAL PUERTAS") { success, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        color = MeetColors.error,
                        modifier = Modifier.weight(1f)
                    )
                    EliteButton(
                        text = "🔓 DESBLOQUEAR",
                        onClick = {
                            accessManager.executeQuickAccessCommand("APERTURA DE ACCESO") { success, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        color = MeetColors.neonGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 6. IMMUTABLE SECURITY AUDIT TIMELINE ──
            item {
                Text("HISTORIAL DE AUDITORÍA & CADENA DE CUSTODIA (SHA-256)", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }

            items(auditEvents.take(5), key = { it.eventId }) { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeetColors.backgroundDark, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (event.outcome == "AUTORIZADO") MeetColors.neonGreen else MeetColors.warning))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(event.action, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Actor: ${event.actor} · Hash: ${event.evidenceHash.take(12)}...", color = MeetColors.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(event.outcome, color = if (event.outcome == "AUTORIZADO") MeetColors.neonGreen else MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }

    // New Grant Dialog
    if (showNewGrantDialog) {
        AlertDialog(
            onDismissRequest = { showNewGrantDialog = false },
            title = { Text("Emitir Acceso Temporal", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newGrantName,
                        onValueChange = { newGrantName = it },
                        label = { Text("Nombre del Destinatario") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.neonGreen,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = newGrantRole,
                        onValueChange = { newGrantRole = it },
                        label = { Text("Rol / Propósito (Familiar, Valet, Taller)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.neonGreen,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                EliteButton(
                    text = "EMITIR ACCESO",
                    onClick = {
                        if (newGrantName.isNotBlank()) {
                            accessManager.addGrant(
                                AccessGrant(
                                    vehicleId = selectedVehicle?.id ?: "V-DEMO-01",
                                    recipientName = newGrantName,
                                    recipientRole = newGrantRole,
                                    permissions = setOf(AccessPermission.ENTRY, AccessPermission.DRIVE),
                                    validFromEpochMs = System.currentTimeMillis(),
                                    validUntilEpochMs = System.currentTimeMillis() + 86400000L, // 24 hours
                                    isVehicleEnforced = false
                                )
                            )
                            showNewGrantDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Acceso emitido con validez de 24 horas para $newGrantName.")
                            }
                        }
                    },
                    color = MeetColors.neonGreen
                )
            },
            dismissButton = {
                EliteTextButton(text = "CANCELAR", onClick = { showNewGrantDialog = false }, color = MeetColors.textSecondary)
            },
            containerColor = MeetColors.backgroundDark
        )
    }
}

@Composable
private fun CapabilityRow(name: String, phoneSupported: Boolean, vehicleState: CapabilityState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Teléfono: ${if (phoneSupported) "COMPATIBLE ✓" else "NO EQUIPADO ✕"}",
                color = if (phoneSupported) MeetColors.neonGreen else MeetColors.textMuted,
                fontSize = 10.sp
            )
        }
        val (stateColor, stateText) = when (vehicleState) {
            CapabilityState.SUPPORTED -> MeetColors.neonGreen to "SOPORTADO"
            CapabilityState.CONDITIONAL -> MeetColors.warning to "CONDICIONAL"
            CapabilityState.UNSUPPORTED -> MeetColors.textMuted to "NO SOPORTADO"
            CapabilityState.UNKNOWN -> MeetColors.textMuted to "DESCONOCIDO"
            CapabilityState.BLOCKED -> MeetColors.error to "BLOQUEADO"
        }
        Box(
            modifier = Modifier
                .background(stateColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .border(1.dp, stateColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(stateText, color = stateColor, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}
