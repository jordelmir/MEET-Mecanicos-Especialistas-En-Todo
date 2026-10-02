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

    // Pulsing indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse_alpha",
    )

    val activeColor = if (isMissingPerson) Color(0xFFFF9100) else Color(0xFFFF1744)
    val containerBg = when {
        record.isIntervened -> Color(0xFF071B1E)
        isMissingPerson -> Color(0xFF1E1305)
        else -> Color(0xFF1F0B0E)
    }
    val borderColor = when {
        record.isIntervened -> MeetColors.neonGreen.copy(alpha = 0.6f)
        isMissingPerson -> Color(0xFFFF9100).copy(alpha = 0.8f)
        else -> Color(0xFFFF1744).copy(alpha = 0.8f)
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
                            record.isIntervened && isMissingPerson -> "PERSONA LOCALIZADA (CONFIRMADO POR PRENSA)"
                            record.isIntervened -> "ACCIÓN OFICIAL VERIFICADA"
                            isMissingPerson -> "BÚSQUEDA ACTIVA · PERSONA NO LOCALIZADA"
                            else -> "CRONÓMETRO DE INACCIÓN"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (record.isIntervened) MeetColors.neonGreen else activeColor,
                        letterSpacing = 1.sp,
                    )
                }

                if (record.cycleCount > 1 && !isMissingPerson) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF332005))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "CICLO ${record.cycleCount} · REINCIDENCIA",
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
                        record.isIntervened && isMissingPerson -> "TIEMPO TRANSCURRIDO HASTA LA LOCALIZACIÓN"
                        record.isIntervened -> "TIEMPO TRANSCURRIDO HASTA LA INTERVENCIÓN"
                        isMissingPerson -> "TIEMPO DESDE LA DESAPARICIÓN REPORTADA"
                        else -> "TIEMPO PERMITIDO SIN INTERVENCIÓN EFECTIVA"
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
                    record.isIntervened && isMissingPerson -> {
                        "Persona localizada tras ${formatDurationText(years, months, days, hours, minutes)} de búsqueda. Confirmado oficialmente con cobertura periodística."
                    }
                    record.isIntervened -> {
                        "Las autoridades intervinieron este punto tras ${formatDurationText(years, months, days, hours, minutes)}. Cobertura periodística certificada."
                    }
                    isMissingPerson -> {
                        "Tiempo transcurrido desde la desaparición reportada. Persona aún no localizada. La búsqueda ciudadana continúa activa."
                    }
                    else -> {
                        "Las autoridades han permitido la venta de droga en esta ubicación durante este tiempo desde la creación del reporte, sin intervención efectiva verificada."
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
                                record.mediaName ?: "Medio de Comunicación",
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
                                    if (isMissingPerson) "VER NOTICIA DE LA LOCALIZACIÓN" else "VER NOTICIA DEL OPERATIVO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

            // Action Buttons
            if (!record.isIntervened) {
                Button(
                    onClick = {
                        if (pressProfile.isRegisteredPress) {
                            showInterventionModal = true
                        } else {
                            showCivilianDeniedModal = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMissingPerson) Color(0xFFE65100) else Color(0xFFC62828),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        if (isMissingPerson) Icons.Filled.PersonSearch else Icons.Filled.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isMissingPerson) "DETENER CONTADOR (PERSONA APARECIÓ - PRENSA)" else "DETENER CONTADOR (PRENSA / PERIODISTAS)",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                    )
                }
            } else if (!isMissingPerson) {
                // Drug sales can be reactivated by community
                Button(
                    onClick = { showReactivationModal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE65100),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REPORTAR REINCIDENCIA (VENTA CONTINÚA)", fontWeight = FontWeight.Black, fontSize = 12.sp)
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
                    "Exclusivo para Medios y Periodistas",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isMissingPerson) {
                            "Por seguridad fáctica, solo periodistas y medios de comunicación registrados pueden certificar oficialmente la localización de una persona desaparecida y detener el contador."
                        } else {
                            "Por protocolo de seguridad y rigor de rendición de cuentas, solo periodistas y medios de comunicación registrados pueden certificar la intervención de las autoridades y detener el cronómetro."
                        },
                        fontSize = 13.sp,
                        color = MeetColors.textSecondary,
                        lineHeight = 18.sp,
                    )
                    Text(
                        if (isMissingPerson) {
                            "Esto evita que agresores o terceras personas difundan falsamente que la persona ya apareció para frenar las labores de búsqueda."
                        } else {
                            "Esto impide que los vendedores o terceras personas intenten apagar el contador alegando falsamente que ya no operan."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFFFCDD2),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Si eres reportero o representas un medio de prensa, puedes registrar tu acreditación a continuación.",
                        fontSize = 12.sp,
                        color = MeetColors.cyberCyan,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCivilianDeniedModal = false
                        showPressRegistrationModal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan, contentColor = Color.Black),
                ) {
                    Text("REGISTRARME COMO PRENSA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        "Registra tus datos profesionales para tener autoridad de certificar noticias, intervenciones y resoluciones de casos.",
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
                    Text("GUARDAR Y CONTINUAR", fontWeight = FontWeight.Bold)
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
                    if (isMissingPerson) "Certificar Persona Localizada" else "Certificar Intervención de Autoridades",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Medio: ${pressProfile.mediaOutlet} · Periodista: ${pressProfile.journalistFullName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.cyberCyan,
                    )
                    Text(
                        if (isMissingPerson) {
                            "Al confirmar, el cronómetro de búsqueda se detendrá con la fecha actual y se publicará tu titular confirmando que la persona fue localizada."
                        } else {
                            "Al confirmar, el cronómetro de inacción se detendrá con la fecha actual y se publicará tu titular en este punto."
                        },
                        fontSize = 12.sp,
                        color = MeetColors.textSecondary,
                    )

                    OutlinedTextField(
                        value = headlineInput,
                        onValueChange = { headlineInput = it },
                        label = {
                            Text(
                                if (isMissingPerson) "Titular de la Localización" else "Titular o Resumen del Operativo",
                                fontSize = 12.sp,
                            )
                        },
                        placeholder = {
                            Text(
                                if (isMissingPerson) "Ej. OIJ y familiares confirman localización en buen estado" else "Ej. OIJ y Fuerza Pública allanan búnker y decomisan droga",
                                fontSize = 12.sp,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Enlace a la Noticia / Comunicado Oficial", fontSize = 12.sp) },
                        placeholder = { Text("https://...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (headlineInput.isNotBlank()) {
                            store.certifyIntervention(
                                pointId = pointId,
                                initialReportedAt = initialReportedAt,
                                mediaName = pressProfile.mediaOutlet,
                                mediaUrl = urlInput,
                                headline = headlineInput,
                                journalistName = pressProfile.journalistFullName,
                                clockType = clockType,
                            )
                            showInterventionModal = false
                        }
                    },
                    enabled = headlineInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                ) {
                    Text(if (isMissingPerson) "CERTIFICAR HALLAZGO" else "DETENER CRONÓMETRO", fontWeight = FontWeight.Bold)
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
            title = { Text("Reportar Reincidencia de Venta", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "¿La venta de droga continuó o se reactivó en este punto tras la intervención de las autoridades?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        "Cualquier ciudadano puede reportar reincidencia. El cronómetro se reactivará de inmediato acumulando nuevo tiempo de inacción oficial.",
                        fontSize = 12.sp,
                        color = MeetColors.textSecondary,
                    )

                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Detalle de la Reincidencia (Opcional)", fontSize = 12.sp) },
                        placeholder = { Text("Ej. Reanudaron la venta el mismo día en la tarde con nuevos custodios", fontSize = 12.sp) },
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
                    Text("REACTIVAR CRONÓMETRO", fontWeight = FontWeight.Bold)
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
