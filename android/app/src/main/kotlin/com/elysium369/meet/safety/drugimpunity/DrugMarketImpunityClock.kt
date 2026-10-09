package com.elysium369.meet.safety.drugimpunity

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay

@Composable
fun DrugMarketImpunityClockCard(
    pointId: String,
    initialReportedAt: Long,
    store: DrugMarketImpunityStore,
    modifier: Modifier = Modifier,
    clockType: ImpunityClockType = ImpunityClockType.DRUG_SALE,
) {
    val context = LocalContext.current
    val allRecords by store.recordsFlow.collectAsState()
    val pressProfile by store.pressProfile.collectAsState()

    val record = allRecords[pointId] ?: remember(pointId, initialReportedAt) {
        store.getRecord(pointId, initialReportedAt, clockType)
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Live clock ticker every second when running
    LaunchedEffect(record.isIntervened) {
        while (!record.isIntervened) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    var showInterventionModal by remember { mutableStateOf(false) }
    var showPressRegistrationModal by remember { mutableStateOf(false) }
    var showCivilianDeniedModal by remember { mutableStateOf(false) }
    var showReactivationModal by remember { mutableStateOf(false) }

    val startTime = if (record.isReactivated && record.reactivatedAt != null) {
        record.reactivatedAt
    } else {
        record.initialReportedAt.coerceAtLeast(1)
    }

    val endTime = if (record.isIntervened && record.intervenedAt != null) {
        record.intervenedAt
    } else {
        nowMs
    }

    val totalMs = (endTime - startTime).coerceAtLeast(0L)
    val totalSeconds = totalMs / 1000
    val totalMinutes = totalSeconds / 60
    val totalHours = totalMinutes / 60
    val totalDays = totalHours / 24

    val years = totalDays / 365
    val months = (totalDays % 365) / 30
    val days = (totalDays % 365) % 30
    val hours = totalHours % 24
    val minutes = totalMinutes % 60
    val seconds = totalSeconds % 60

    val isMissingPerson = clockType == ImpunityClockType.MISSING_PERSON
    val isHomicide = clockType == ImpunityClockType.HOMICIDE

    // Pulsing indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_alpha",
    )

    val activeColor = when {
        isHomicide -> Color(0xFFFF1744)
        isMissingPerson -> Color(0xFFFF9100)
        else -> Color(0xFFFF5252)
    }
    val containerBg = when {
        record.isIntervened -> Color(0xFF071B1E)
        isHomicide -> Color(0xFF220508)
        isMissingPerson -> Color(0xFF1E1305)
        else -> Color(0xFF1F0B0E)
    }
    val borderColor = when {
        record.isIntervened -> MeetColors.neonGreen.copy(alpha = 0.6f)
        isHomicide -> Color(0xFFFF1744).copy(alpha = 0.85f)
        isMissingPerson -> Color(0xFFFF9100).copy(alpha = 0.8f)
        else -> Color(0xFFFF5252).copy(alpha = 0.8f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = BorderStroke(width = 1.5.dp, color = borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .alpha(if (record.isIntervened) 1f else pulseAlpha)
                            .background(if (record.isIntervened) MeetColors.neonGreen else activeColor),
                    )
                    Text(
                        text = when {
                            record.isIntervened -> "ACTUALIZACIÓN APORTADA · NO VERIFICADA"
                            record.isReactivated -> "NUEVA OBSERVACIÓN APORTADA · NO VERIFICADA"
                            isHomicide -> "REPORTE DE HOMICIDIO"
                            isMissingPerson -> "REPORTE DE PERSONA DESAPARECIDA"
                            else -> "REPORTE DE ACTIVIDAD RELACIONADA CON DROGAS"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (record.isIntervened) MeetColors.neonGreen else activeColor,
                        letterSpacing = 1.sp,
                    )
                }

                if (record.cycleCount > 1 && !isMissingPerson && !isHomicide) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF332005))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "SEGUIMIENTO ${record.cycleCount} · ACTUALIZACIONES",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.warning,
                        )
                    }
                }
            }

            // Big Clock Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF080D16))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = buildString {
                        if (years > 0) append("$years AÑO${if (years > 1) "S" else ""} · ")
                        if (months > 0 || years > 0) append("$months MES${if (months != 1L) "ES" else ""} · ")
                        append("$days DÍA${if (days != 1L) "S" else ""}")
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (record.isIntervened) MeetColors.cyberCyan else if (isMissingPerson) Color(0xFFFFCC80) else Color(0xFFFFAB91),
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if (record.isIntervened) MeetColors.neonGreen else activeColor,
                    letterSpacing = 2.sp,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = when {
                        record.isIntervened -> "TIEMPO HASTA EL REGISTRO DE LA ACTUALIZACIÓN APORTADA"
                        else -> "TIEMPO DESDE LA FECHA DE REGISTRO DEL REPORTE"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textSecondary,
                    letterSpacing = 0.5.sp,
                )
            }

            // Mandatory Legend
            Text(
                text = when {
                    record.isIntervened -> {
                        "Se registró una actualización aportada por un usuario tras ${formatDurationText(years, months, days, hours, minutes)}. El título y el enlace son declaraciones externas no verificadas por Elysium."
                    }
                    record.isReactivated -> {
                        "Se registró una nueva observación aportada por un usuario. Esta entrada no valida la observación inicial ni confirma que el hecho continúe."
                    }
                    else -> {
                        "Este contador mide el tiempo desde la fecha de registro del reporte. No confirma que el incidente esté corroborado, que una persona siga desaparecida ni que una autoridad haya actuado u omitido actuar."
                    }
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = if (record.isIntervened) MeetColors.textPrimary else if (isMissingPerson) Color(0xFFFFE0B2) else Color(0xFFFFCDD2),
                fontWeight = FontWeight.Medium,
            )

            // News / Location Details Card (if intervened)
            if (record.isIntervened) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2624)),
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.35f)),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Newspaper, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                record.mediaName ?: "Fuente aportada por usuario",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.cyberCyan,
                            )
                        }

                        if (!record.interventionHeadline.isNullOrBlank()) {
                            Text(
                                record.interventionHeadline,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }

                        if (!record.mediaUrl.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(record.mediaUrl))
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.neonGreen),
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when {
                                        isHomicide -> "ABRIR FUENTE APORTADA · NO VERIFICADA"
                                        isMissingPerson -> "ABRIR FUENTE APORTADA · NO VERIFICADA"
                                        else -> "ABRIR FUENTE APORTADA · NO VERIFICADA"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

            Button(
                onClick = { showCivilianDeniedModal = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.cyberCyan,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(Icons.Filled.Newspaper, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("APORTAR ACTUALIZACIÓN", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }

            if (record.isIntervened && !isMissingPerson && !isHomicide) {
                OutlinedButton(
                    onClick = { showReactivationModal = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.warning),
                    border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("APORTAR NUEVA OBSERVACIÓN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }

    // Modal: Civilian Denied / Explanation dialog
    if (showCivilianDeniedModal) {
        AlertDialog(
            onDismissRequest = { showCivilianDeniedModal = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MeetColors.warning, modifier = Modifier.size(32.dp)) },
            title = {
                Text(
                    "Antes de aportar una actualización",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "El cronómetro parte de la fecha registrada del reporte. Por sí solo no prueba que el hecho haya ocurrido como fue descrito ni que una autoridad haya actuado u omitido actuar.",
                        fontSize = 13.sp,
                        color = MeetColors.textSecondary,
                        lineHeight = 18.sp,
                    )
                    Text(
                        "La actualización y su enlace son datos aportados por el usuario. El sistema no autentica automáticamente la fuente ni certifica una decisión oficial.",
                        fontSize = 12.sp,
                        color = Color(0xFFFFCDD2),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Puedes aportar una referencia externa. El formato válido no comprueba el origen ni el contenido de ese enlace.",
                        fontSize = 12.sp,
                        color = MeetColors.cyberCyan,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCivilianDeniedModal = false
                        showInterventionModal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan, contentColor = Color.Black),
                ) {
                    Text("CONTINUAR AL FORMULARIO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCivilianDeniedModal = false }) {
                    Text("ENTENDIDO", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.cardBackground,
        )
    }

    // Modal: Press Registration
    if (showPressRegistrationModal) {
        var mediaOutletInput by remember { mutableStateOf("") }
        var journalistNameInput by remember { mutableStateOf("") }
        var cardIdInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPressRegistrationModal = false },
            icon = { Icon(Icons.Filled.Newspaper, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(32.dp)) },
            title = { Text("Registro de Prensa / Periodista", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Los datos de medio, nombre y acreditación son autodeclarados. Guardarlos no verifica tu identidad ni concede autoridad para certificar hechos.",
                        fontSize = 12.sp,
                        color = MeetColors.textSecondary,
                    )

                    OutlinedTextField(
                        value = mediaOutletInput,
                        onValueChange = { mediaOutletInput = it },
                        label = { Text("Medio de Comunicación / Agencia", fontSize = 12.sp) },
                        placeholder = { Text("Ej. Teletica, CRHoy, Diario Extra, Medio Local", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = journalistNameInput,
                        onValueChange = { journalistNameInput = it },
                        label = { Text("Nombre del Periodista / Corresponsal", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = cardIdInput,
                        onValueChange = { cardIdInput = it },
                        label = { Text("Carné / Acreditación de Prensa (Opcional)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (mediaOutletInput.isNotBlank() && journalistNameInput.isNotBlank()) {
                            store.registerPressProfile(mediaOutletInput, journalistNameInput, cardIdInput)
                            showPressRegistrationModal = false
                            showInterventionModal = true
                        }
                    },
                    enabled = mediaOutletInput.isNotBlank() && journalistNameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                ) {
                    Text("GUARDAR PERFIL DECLARADO", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPressRegistrationModal = false }) {
                    Text("CANCELAR", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.cardBackground,
        )
    }

    // Modal: Journalist Certifies Intervention or Location
    if (showInterventionModal) {
        var headlineInput by remember { mutableStateOf("") }
        var urlInput by remember { mutableStateOf("") }
        var sourceError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showInterventionModal = false },
            icon = {
                Icon(
                    if (isMissingPerson) Icons.Filled.PersonSearch else Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MeetColors.neonGreen,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    when {
                        isHomicide -> "Registrar una actualización aportada"
                        isMissingPerson -> "Registrar una actualización aportada"
                        else -> "Registrar una actualización aportada"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Perfil declarado: ${pressProfile.mediaOutlet.ifBlank { "usuario" }} · ${pressProfile.journalistFullName.ifBlank { "sin nombre declarado" }} (no verificado)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.cyberCyan,
                    )
                    Text(
                        "Al guardar, el seguimiento se pausará en la fecha de esta entrada. Elysium registra el título y enlace aportados, pero no verifica automáticamente su origen ni declara confirmado el acontecimiento.",
                        fontSize = 12.sp,
                        color = MeetColors.textSecondary,
                    )

                    OutlinedTextField(
                        value = headlineInput,
                        onValueChange = { headlineInput = it },
                        label = { Text("Resumen de la actualización aportada", fontSize = 12.sp) },
                        placeholder = { Text("Ej. Se publicó una nota relacionada con este reporte", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Enlace externo de referencia (requerido)", fontSize = 12.sp) },
                        placeholder = { Text("https://sitio.example/noticia", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (urlInput.isNotBlank() && !isValidSafetyUpdateSourceUrl(urlInput)) {
                        Text(
                            "El enlace debe ser una URL HTTP(S) absoluta y no puede contener credenciales.",
                            fontSize = 11.sp,
                            color = MeetColors.error,
                        )
                    }
                    Text(
                        "Un formato válido no verifica el origen ni el contenido del enlace.",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MeetColors.textSecondary,
                    )
                    sourceError?.let { error -> Text(error, fontSize = 11.sp, color = MeetColors.error) }
                    TextButton(onClick = {
                        showInterventionModal = false
                        showPressRegistrationModal = true
                    }) {
                        Text("Editar perfil declarado", color = MeetColors.cyberCyan)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (headlineInput.isNotBlank()) {
                            normalizeSafetyUpdateSourceUrl(urlInput).fold(
                                onSuccess = { safeUrl ->
                                    val declaredSource = pressProfile.mediaOutlet.trim()
                                        .takeIf { it.isNotEmpty() }
                                        ?.let { "Fuente autodeclarada: $it" }
                                        ?: "Fuente aportada por usuario"
                                    store.recordExternalUpdate(
                                        pointId = pointId,
                                        initialReportedAt = initialReportedAt,
                                        mediaName = declaredSource,
                                        mediaUrl = safeUrl,
                                        headline = headlineInput.trim(),
                                        journalistName = pressProfile.journalistFullName.trim(),
                                        clockType = clockType,
                                    ).onSuccess {
                                        showInterventionModal = false
                                    }.onFailure { error ->
                                        sourceError = error.message ?: "No se pudo registrar la actualización."
                                    }
                                },
                                onFailure = { error ->
                                    sourceError = error.message ?: "El enlace no es válido."
                                },
                            )
                        }
                    },
                    enabled = headlineInput.isNotBlank() && isValidSafetyUpdateSourceUrl(urlInput),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                ) {
                    Text("GUARDAR ACTUALIZACIÓN APORTADA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInterventionModal = false }) {
                    Text("CANCELAR", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.cardBackground,
        )
    }

    // Modal: Citizen Reports Reactivation
    if (showReactivationModal && !isMissingPerson) {
        var reasonInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showReactivationModal = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MeetColors.warning, modifier = Modifier.size(32.dp)) },
            title = { Text("Aportar nueva observación de seguimiento", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "¿Tienes una nueva observación o información de seguimiento relacionada con este reporte?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        "Al registrarla comenzará un nuevo tramo de seguimiento desde la fecha de esta observación. La entrada no confirma la actividad inicial, su continuidad ni la actuación de una institución.",
                        fontSize = 12.sp,
                        color = MeetColors.textSecondary,
                    )

                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Detalle de la nueva observación (opcional)", fontSize = 12.sp) },
                        placeholder = { Text("Describe únicamente lo que observaste o la información documental que aportas", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        store.reportReactivation(
                            pointId = pointId,
                            initialReportedAt = initialReportedAt,
                            reason = reasonInput,
                        )
                        showReactivationModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100), contentColor = Color.White),
                ) {
                    Text("REGISTRAR NUEVA OBSERVACIÓN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReactivationModal = false }) {
                    Text("CANCELAR", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.cardBackground,
        )
    }
}

private fun formatDurationText(years: Long, months: Long, days: Long, hours: Long, minutes: Long): String {
    val parts = mutableListOf<String>()
    if (years > 0) parts.add("$years año${if (years > 1) "s" else ""}")
    if (months > 0) parts.add("$months mes${if (months > 1) "es" else ""}")
    if (days > 0) parts.add("$days día${if (days > 1) "s" else ""}")
    if (hours > 0 && years == 0L) parts.add("$hours hora${if (hours > 1) "s" else ""}")
    if (minutes > 0 && days == 0L && years == 0L) parts.add("$minutes minuto${if (minutes > 1) "s" else ""}")
    return if (parts.isEmpty()) "pocos minutos" else parts.joinToString(", ")
}
