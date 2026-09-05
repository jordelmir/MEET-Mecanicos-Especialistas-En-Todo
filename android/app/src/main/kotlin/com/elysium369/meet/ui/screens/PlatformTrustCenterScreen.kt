package com.elysium369.meet.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.share.QrCodeImage
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.observability.TrustCenterObservability
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.TrustQueueSnapshot
import com.elysium369.meet.ride.data.remote.TrustRealtimeSignal
import com.elysium369.meet.ride.data.remote.TrustVerificationApplication
import com.elysium369.meet.ride.domain.PlatformOwnerAccess
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.HolographicBackgroundShared
import com.elysium369.meet.ui.theme.MeetColors
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.mfa.FactorType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TRUST_QUEUE_HEARTBEAT_MS = 30_000L

private enum class TrustRealtimeState { CONNECTING, LIVE, RECOVERING, OFFLINE }

private data class TrustMfaState(
    val isAal2: Boolean = false,
    val verifiedFactorId: String? = null,
)

private data class TrustMfaEnrollment(
    val factorId: String,
    val uri: String,
    val secret: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformTrustCenterScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
) {
    val access by viewModel.platformOwnerAccess.collectAsState()
    var filter by remember { mutableStateOf("PENDING") }
    var snapshot by remember { mutableStateOf(TrustQueueSnapshot()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var realtimeState by remember { mutableStateOf(TrustRealtimeState.CONNECTING) }
    var lastSyncEpochMs by remember { mutableStateOf<Long?>(null) }
    var mfaState by remember { mutableStateOf(TrustMfaState()) }
    var mfaEnrollment by remember { mutableStateOf<TrustMfaEnrollment?>(null) }
    var mfaDialogVisible by remember { mutableStateOf(false) }
    var pendingDecision by remember {
        mutableStateOf<Pair<TrustVerificationApplication, String>?>(null)
    }
    var pendingDecisionAfterMfa by remember {
        mutableStateOf<Pair<TrustVerificationApplication, String>?>(null)
    }
    var evidenceApplication by remember { mutableStateOf<TrustVerificationApplication?>(null) }
    val scope = rememberCoroutineScope()
    val reloadMutex = remember { Mutex() }

    suspend fun reloadNow() {
        if (access != PlatformOwnerAccess.GRANTED) return
        reloadMutex.withLock {
            loading = true
            runCatching { PlatformTrustCenterGateway.loadQueue(filter) }
                .onSuccess {
                    // Enrich applications that have a manifest but no evidence rows
                    val enriched = it.copy(
                        items = it.items.map { app ->
                            if (app.evidence.isEmpty() && app.evidenceManifestSha256 != null) {
                                val fallbackEvidence = runCatching {
                                    PlatformTrustCenterGateway.listEvidenceFromStorage(
                                        applicantUserId = app.applicantUserId,
                                        serviceType = app.serviceType,
                                    )
                                }.getOrDefault(emptyList())
                                if (fallbackEvidence.isNotEmpty()) app.copy(evidence = fallbackEvidence) else app
                            } else app
                        }
                    )
                    snapshot = enriched
                    lastSyncEpochMs = System.currentTimeMillis()
                    if (message?.startsWith("Sincronización") == true) message = null
                }
                .onFailure { error ->
                    message = "Sincronización temporalmente interrumpida; se conserva la última cola y el reintento es automático. Código: ${TrustCenterObservability.failureCode(error)}."
                }
            loading = false
        }
    }

    fun reload() {
        if (access == PlatformOwnerAccess.GRANTED) scope.launch { reloadNow() }
    }

    suspend fun refreshMfaState() {
        val mfa = SupabaseModule.client.auth.mfa
        val factors = mfa.retrieveFactorsForCurrentUser()
        mfaState = TrustMfaState(
            isAal2 = mfa.loggedInUsingMfa,
            verifiedFactorId = factors.firstOrNull { it.isVerified }?.id,
        )
    }

    fun prepareMfa() {
        scope.launch {
            loading = true
            message = null
            runCatching {
                val mfa = SupabaseModule.client.auth.mfa
                val factors = mfa.retrieveFactorsForCurrentUser()
                val verified = factors.firstOrNull { it.isVerified }
                if (verified != null) {
                    mfaState = mfaState.copy(verifiedFactorId = verified.id)
                    mfaEnrollment = null
                    mfaDialogVisible = true
                } else {
                    factors.filterNot { it.isVerified }.forEach { mfa.unenroll(it.id) }
                    val factor = mfa.enroll(factorType = FactorType.TOTP)
                    mfaEnrollment = TrustMfaEnrollment(
                        factorId = factor.id,
                        uri = factor.data.uri,
                        secret = factor.data.secret,
                    )
                    mfaDialogVisible = true
                }
            }.onFailure { error ->
                message = "No se pudo preparar el segundo factor. Código: ${TrustCenterObservability.failureCode(error)}."
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshPlatformOwnerAccess() }
    LaunchedEffect(access) {
        if (access == PlatformOwnerAccess.GRANTED) {
            runCatching { refreshMfaState() }.onFailure {
                message = "No se pudo comprobar el segundo factor. Las decisiones quedan bloqueadas."
            }
        }
    }
    LaunchedEffect(access, filter) {
        if (access != PlatformOwnerAccess.GRANTED) return@LaunchedEffect
        realtimeState = TrustRealtimeState.CONNECTING
        reloadNow()
        launch {
            while (currentCoroutineContext().isActive) {
                delay(TRUST_QUEUE_HEARTBEAT_MS)
                reloadNow()
            }
        }
        PlatformTrustCenterGateway.realtimeWakeUps()
            .retryWhen { _, attempt ->
                realtimeState = TrustRealtimeState.RECOVERING
                TrustCenterObservability.realtime("RECOVERING", attempt + 1)
                delay((2_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(60_000L))
                true
            }
            .collectLatest { signal ->
                realtimeState = TrustRealtimeState.LIVE
                if (signal == TrustRealtimeSignal.CHANGE || signal == TrustRealtimeSignal.SUBSCRIBED) {
                    reloadNow()
                }
            }
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
                                Text(
                                    "Realtime: ${realtimeLabel(realtimeState)} · respaldo REST cada 30 s" +
                                        (lastSyncEpochMs?.let { " · última conciliación ${java.text.DateFormat.getTimeInstance().format(java.util.Date(it))}" } ?: ""),
                                    color = realtimeColor(realtimeState),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                TextButton(onClick = ::reload, enabled = !loading) {
                                    Text("ACTUALIZAR AHORA")
                                }
                            }
                        }
                    }
                    item {
                        EliteCard(
                            glowColor = if (mfaState.isAal2) MeetColors.neonGreen else MeetColors.warning,
                            borderColor = if (mfaState.isAal2) MeetColors.neonGreen.copy(alpha = .35f) else MeetColors.warning.copy(alpha = .35f),
                            backgroundColor = MeetColors.cardBackground,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    if (mfaState.isAal2) "SEGUNDO FACTOR VERIFICADO" else "SEGUNDO FACTOR REQUERIDO PARA DECIDIR",
                                    color = if (mfaState.isAal2) MeetColors.neonGreen else MeetColors.warning,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    if (mfaState.isAal2) {
                                        "La sesión cumple AAL2; aprobar, rechazar o suspender queda habilitado."
                                    } else {
                                        "Puedes revisar la cola, pero el servidor bloqueará cualquier decisión hasta validar una app autenticadora."
                                    },
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                                if (!mfaState.isAal2) {
                                    Button(
                                        onClick = ::prepareMfa,
                                        enabled = !loading,
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) {
                                        Text(if (mfaState.verifiedFactorId == null) "ACTIVAR MFA" else "VALIDAR MFA")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("PENDING", "APPROVED", "REJECTED", "SUSPENDED", "ALL").forEach { status ->
                                FilterChip(
                                    selected = filter == status,
                                    onClick = { filter = status },
                                    label = {
                                        Text(
                                            "${statusLabel(status)} ${queueCount(snapshot, status)}",
                                            fontSize = 11.sp,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    message?.let { item { Text(it, color = MeetColors.warning) } }
                    if (loading) {
                        item { CircularProgressIndicator(color = MeetColors.cyberCyan) }
                    } else if (snapshot.items.isEmpty()) {
                        item {
                            Text(
                                "No hay registros en esta cola.",
                                color = MeetColors.textSecondary,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    } else {
                        items(snapshot.items, key = { it.id }) { application ->
                            TrustApplicationCard(
                                application = application,
                                canDecide = true,
                                onDecision = { decision ->
                                    if (!mfaState.isAal2) {
                                        // MFA not verified — prepare MFA first, then retry decision
                                        pendingDecisionAfterMfa = application to decision
                                        prepareMfa()
                                    } else {
                                        pendingDecision = application to decision
                                    }
                                },
                                onOpenEvidence = { evidenceApplication = application },
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
                    }.onFailure { error ->
                        val code = TrustCenterObservability.failureCode(error)
                        val detail = error.message?.take(120) ?: "Sin detalle"
                        message = when (code) {
                            "AAL2_REQUIRED" ->
                                "El servidor exige segundo factor. Valida MFA y vuelve a intentar."
                            "REVIEWABLE_EVIDENCE_REQUIRED" ->
                                "Faltan archivos de evidencia adjuntos a la solicitud. El servidor los requiere para aprobar."
                            "TIMEOUT", "CONNECTION_FAILED" ->
                                "El servidor no respondió. Verifica tu conexión e intenta de nuevo."
                            "NETWORK_UNAVAILABLE" ->
                                "Sin conexión a internet. Reconecta e intenta de nuevo."
                            "PLATFORM_OWNER_REQUIRED" ->
                                "Se requiere ser propietario de plataforma para esta acción."
                            "APPLICATION_NOT_FOUND" ->
                                "La solicitud no fue encontrada. Puede que ya haya sido procesada."
                            else ->
                                "Error del servidor: $detail"
                        }
                        loading = false
                    }
                }
            },
        )
    }

    evidenceApplication?.let { application ->
        TrustEvidenceDialog(
            application = application,
            onDismiss = { evidenceApplication = null },
        )
    }

    if (mfaDialogVisible) {
        TrustMfaDialog(
            enrollment = mfaEnrollment,
            onDismiss = {
                mfaDialogVisible = false
                mfaEnrollment = null
            },
            onConfirm = { code ->
                val factorId = mfaEnrollment?.factorId ?: mfaState.verifiedFactorId
                if (factorId == null) {
                    message = "No existe un segundo factor disponible. Actívalo nuevamente."
                } else {
                    scope.launch {
                        loading = true
                        runCatching {
                            SupabaseModule.client.auth.mfa.createChallengeAndVerify(
                                factorId = factorId,
                                code = code,
                                saveSession = true,
                            )
                            refreshMfaState()
                    }.onSuccess {
                        mfaDialogVisible = false
                        mfaEnrollment = null
                        message = "Segundo factor verificado. Las decisiones sensibles están habilitadas."
                        reloadNow()
                        // Auto-retry any pending decision that was blocked by MFA
                        pendingDecisionAfterMfa?.let { (app, decision) ->
                            pendingDecisionAfterMfa = null
                            pendingDecision = app to decision
                        }
                    }.onFailure { error ->
                            message = "El código MFA no se validó. Intenta con el código vigente. Código: ${TrustCenterObservability.failureCode(error)}."
                        }
                        loading = false
                    }
                }
            },
        )
    }
}

@Composable
private fun TrustMfaDialog(
    enrollment: TrustMfaEnrollment?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (enrollment == null) "Validar segundo factor" else "Activar segundo factor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (enrollment != null) {
                    Text("Escanea este QR con una app autenticadora. El secreto no debe compartirse.")
                    QrCodeImage(
                        text = enrollment.uri,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    )
                    Text("Entrada manual", fontWeight = FontWeight.Bold)
                    SelectionContainer {
                        Text(enrollment.secret, color = MeetColors.warning)
                    }
                } else {
                    Text("Escribe el código vigente de seis dígitos de tu app autenticadora.")
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("Código de 6 dígitos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = code.length == 6) {
                Text("VERIFICAR")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } },
    )
}

@Composable
private fun TrustApplicationCard(
    application: TrustVerificationApplication,
    canDecide: Boolean,
    onDecision: (String) -> Unit,
    onOpenEvidence: () -> Unit,
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
            Spacer(Modifier.height(8.dp))

            // ── Datos del solicitante ──
            Text("DATOS DEL SOLICITANTE", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            application.businessName?.let { TrustLine("Vehículo / Negocio", it) }
            application.applicantEmail?.let { TrustLine("Cuenta", it) }
            application.phone?.let { TrustLine("Teléfono", it) }
            application.locationLabel?.let { TrustLine("Zona / Detalle", it) }
            application.licenseReference?.let { TrustLine("Placa / Licencia", it) }
            TrustLine("Solicitud", application.submittedAt)
            application.correlationId?.let { TrustLine("Seguimiento", it.take(12)) }

            Spacer(Modifier.height(8.dp))

            // ── Evidencia ──
            Text("EVIDENCIA", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            if (application.evidence.isNotEmpty()) {
                TrustLine("Archivos", "${application.evidence.size} imágenes privadas")
                application.evidenceManifestSha256?.let { TrustLine("Manifiesto", "${it.take(16)}…") }
                OutlinedButton(
                    onClick = onOpenEvidence,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("REVISAR EVIDENCIA (${application.evidence.size})", fontWeight = FontWeight.Bold) }
            } else {
                Surface(
                    color = MeetColors.warning.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        "Sin archivos de evidencia adjuntos. " +
                            (application.evidenceManifestSha256?.let { "Hash del manifiesto: ${it.take(16)}…" }
                                ?: "No se generó manifiesto."),
                        color = MeetColors.warning,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            application.decisionReason?.let {
                Spacer(Modifier.height(8.dp))
                TrustLine("Motivo de decisión", it)
            }

            if (application.status == "PENDING") {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onDecision("APPROVED") },
                        enabled = canDecide,
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                    ) { Text("APROBAR", fontWeight = FontWeight.Black) }
                    OutlinedButton(
                        onClick = { onDecision("REJECTED") },
                        enabled = canDecide,
                    ) { Text("RECHAZAR") }
                    TextButton(
                        onClick = { onDecision("SUSPENDED") },
                        enabled = canDecide,
                    ) { Text("SUSPENDER") }
                }
            } else if (application.status == "APPROVED") {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onDecision("SUSPENDED") },
                    enabled = canDecide,
                ) { Text("SUSPENDER CAPACIDAD") }
            } else if (application.status == "SUSPENDED") {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onDecision("APPROVED") },
                        enabled = canDecide,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.neonGreen,
                            contentColor = Color.Black,
                        ),
                    ) { Text("REACTIVAR") }
                    OutlinedButton(
                        onClick = { onDecision("REJECTED") },
                        enabled = canDecide,
                    ) { Text("RECHAZAR") }
                }
            }
        }
    }
}

@Composable
private fun TrustEvidenceDialog(
    application: TrustVerificationApplication,
    onDismiss: () -> Unit,
) {
    var images by remember(application.id) { mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }
    var error by remember(application.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(application.id) {
        runCatching {
            application.evidence.associate { item ->
                val bytes = PlatformTrustCenterGateway.downloadEvidence(item.storagePath)
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        ?: error("INVALID_IMAGE")
                }
                item.kind to bitmap
            }
        }.onSuccess { images = it }
            .onFailure { error = "No se pudo abrir la evidencia privada. Código: ${TrustCenterObservability.failureCode(it)}" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Evidencia · ${application.displayName}") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (images.size < application.evidence.size && error == null) {
                    item { CircularProgressIndicator(color = MeetColors.cyberCyan) }
                }
                error?.let { item { Text(it, color = MeetColors.error) } }
                items(application.evidence, key = { it.kind }) { item ->
                    Column {
                        Text(item.kind.replace('_', ' ').uppercase(), fontWeight = FontWeight.Bold)
                        images[item.kind]?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Evidencia ${item.kind}",
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                            )
                        }
                        Text(
                            "SHA-256 ${item.contentSha256.take(16)}… · ${item.byteCount} bytes",
                            fontSize = 10.sp,
                            color = MeetColors.textSecondary,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CERRAR") } },
    )
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

private fun serviceLabel(type: String): String = when (type.lowercase()) {
    "passenger" -> "Pasajero"
    "ride_driver" -> "Chofer de viajes"
    "tow_truck", "tow_provider" -> "Grúa / asistencia vial"
    "mechanic", "workshop" -> "Mecánico / taller"
    "parts_store" -> "Repuestera"
    "service_provider" -> "Proveedor de servicios"
    "auto_locksmith" -> "Cerrajería automotriz"
    "lawyer" -> "Profesional legal"
    "notary" -> "Notaría"
    "property_broker" -> "Corredor inmobiliario"
    "property_seller" -> "Vendedor de propiedad"
    "fuel_station_staff" -> "Personal de estación de combustible"
    "fleet_operator" -> "Operador de flota"
    else -> type
}

private fun statusLabel(status: String): String = when (status) {
    "PENDING" -> "Pendientes"
    "APPROVED" -> "Aprobados"
    "REJECTED" -> "Rechazados"
    "SUSPENDED" -> "Suspendidos"
    "ALL" -> "Todos"
    else -> status
}

private fun statusColor(status: String): Color = when (status) {
    "APPROVED" -> MeetColors.neonGreen
    "REJECTED" -> MeetColors.error
    "SUSPENDED" -> MeetColors.warning
    else -> MeetColors.cyberCyan
}

private fun queueCount(snapshot: TrustQueueSnapshot, status: String): Int = when (status) {
    "PENDING" -> snapshot.counts.pending
    "APPROVED" -> snapshot.counts.approved
    "REJECTED" -> snapshot.counts.rejected
    "SUSPENDED" -> snapshot.counts.suspended
    else -> snapshot.counts.all
}

private fun realtimeLabel(state: TrustRealtimeState): String = when (state) {
    TrustRealtimeState.CONNECTING -> "conectando"
    TrustRealtimeState.LIVE -> "en vivo"
    TrustRealtimeState.RECOVERING -> "reconectando"
    TrustRealtimeState.OFFLINE -> "sin conexión"
}

private fun realtimeColor(state: TrustRealtimeState): Color = when (state) {
    TrustRealtimeState.LIVE -> MeetColors.neonGreen
    TrustRealtimeState.CONNECTING -> MeetColors.cyberCyan
    TrustRealtimeState.RECOVERING -> MeetColors.warning
    TrustRealtimeState.OFFLINE -> MeetColors.error
}
