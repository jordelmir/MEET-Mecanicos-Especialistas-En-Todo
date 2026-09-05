package com.elysium369.meet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.services.dekra.*
import com.elysium369.meet.ui.DekraConciergeSubmissionState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.AnimatedNeonIcon
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DekraConciergeScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenTow: () -> Unit,
    onOpenMessages: () -> Unit = {},
) {
    val vehicle by viewModel.selectedVehicle.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val submission by viewModel.dekraConciergeSubmission.collectAsState()
    val uriHandler = LocalUriHandler.current

    var step by rememberSaveable { mutableIntStateOf(0) }
    var appointmentModeName by rememberSaveable { mutableStateOf(DekraAppointmentMode.CONFIRMED.name) }
    var station by rememberSaveable { mutableStateOf("") }
    var appointmentDateTime by rememberSaveable { mutableStateOf("") }
    var reservationCode by rememberSaveable { mutableStateOf("") }
    var pickupZone by rememberSaveable { mutableStateOf("") }
    var contactPhone by rememberSaveable { mutableStateOf("") }
    var conditionName by rememberSaveable { mutableStateOf(DekraVehicleCondition.NORMAL.name) }
    var notes by rememberSaveable { mutableStateOf("") }
    var precheckAuthorized by rememberSaveable { mutableStateOf(false) }
    var custodyAuthorized by rememberSaveable { mutableStateOf(false) }
    var independentResultAcknowledged by rememberSaveable { mutableStateOf(false) }
    var officialFeeAcknowledged by rememberSaveable { mutableStateOf(false) }
    var stationRulesAcknowledged by rememberSaveable { mutableStateOf(false) }
    var lastRequest by remember { mutableStateOf<DekraConciergeRequest?>(null) }

    val appointmentMode = DekraAppointmentMode.valueOf(appointmentModeName)
    val vehicleCondition = DekraVehicleCondition.valueOf(conditionName)
    val transportPlan = DekraConciergePolicy.transportPlanFor(vehicleCondition)
    val vehicleReady = vehicle != null && !vehicle?.plate.isNullOrBlank()
    val appointmentReady = station.isNotBlank() && appointmentDateTime.isNotBlank()
    val pickupReady = pickupZone.isNotBlank() && contactPhone.count(Char::isDigit) >= 8
    val consentsReady = precheckAuthorized && custodyAuthorized &&
        independentResultAcknowledged && officialFeeAcknowledged && stationRulesAcknowledged

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "MEET → DEKRA\nConcierge de Inspección",
                onBackClick = onBack,
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    IconButton(onClick = onOpenMessages) {
                        Icon(Icons.Default.Chat, "Mensajes del servicio", tint = MeetColors.cyberCyan)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DekraProgress(step = step)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    DekraHeroCard(
                        vehicleLabel = vehicle?.let { "${it.make} ${it.model} ${it.year}" },
                        maskedPlate = DekraConciergePolicy.maskPlate(vehicle?.plate),
                        activeDtcCount = activeDtcs.size,
                    )
                }

                if (!vehicleReady) {
                    item {
                        BlockingNotice(
                            text = if (vehicle == null) {
                                "Selecciona un vehículo en Garage antes de solicitar el servicio."
                            } else {
                                "El vehículo necesita una placa registrada para vincular la cita DEKRA."
                            },
                            button = "ABRIR GARAGE",
                            onClick = onOpenGarage,
                        )
                    }
                }

                when (step) {
                    0 -> item {
                        ServicePromiseCard(
                            onOpenBooking = { uriHandler.openUri(DekraInspectionKnowledge.OFFICIAL_BOOKING_URL) },
                            onOpenPolicy = { uriHandler.openUri(DekraInspectionKnowledge.OFFICIAL_APPOINTMENT_POLICY_URL) },
                        )
                    }
                    1 -> item {
                        AppointmentAndPickupCard(
                            appointmentMode = appointmentMode,
                            onAppointmentMode = { appointmentModeName = it.name },
                            station = station,
                            onStation = { station = it },
                            appointmentDateTime = appointmentDateTime,
                            onAppointmentDateTime = { appointmentDateTime = it },
                            reservationCode = reservationCode,
                            onReservationCode = { reservationCode = it },
                            pickupZone = pickupZone,
                            onPickupZone = { pickupZone = it },
                            contactPhone = contactPhone,
                            onContactPhone = { contactPhone = it },
                            vehicleCondition = vehicleCondition,
                            onVehicleCondition = { conditionName = it.name },
                            transportPlan = transportPlan,
                            notes = notes,
                            onNotes = { notes = it },
                            onOpenBooking = { uriHandler.openUri(DekraInspectionKnowledge.OFFICIAL_BOOKING_URL) },
                            onOpenTow = onOpenTow,
                        )
                    }
                    2 -> item {
                        InspectionEducationCard(
                            onOpenManual = { uriHandler.openUri(DekraInspectionKnowledge.OFFICIAL_MANUAL_URL) },
                            onOpenFaq = { uriHandler.openUri(DekraInspectionKnowledge.OFFICIAL_FAQ_URL) },
                        )
                    }
                    else -> item {
                        AuthorizationCard(
                            vehicleLabel = vehicle?.let { "${it.make} ${it.model} ${it.year}" }.orEmpty(),
                            station = station,
                            appointmentDateTime = appointmentDateTime,
                            pickupZone = pickupZone,
                            transportPlan = transportPlan,
                            precheckAuthorized = precheckAuthorized,
                            onPrecheckAuthorized = { precheckAuthorized = it },
                            custodyAuthorized = custodyAuthorized,
                            onCustodyAuthorized = { custodyAuthorized = it },
                            independentResultAcknowledged = independentResultAcknowledged,
                            onIndependentResultAcknowledged = { independentResultAcknowledged = it },
                            officialFeeAcknowledged = officialFeeAcknowledged,
                            onOfficialFeeAcknowledged = { officialFeeAcknowledged = it },
                            stationRulesAcknowledged = stationRulesAcknowledged,
                            onStationRulesAcknowledged = { stationRulesAcknowledged = it },
                            onOpenFees = { uriHandler.openUri(DekraInspectionKnowledge.OFFICIAL_FEES_URL) },
                        )
                    }
                }
            }

            Surface(color = MeetColors.backgroundDeep, tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, MeetColors.textMuted),
                        ) {
                            Text("ANTERIOR", color = MeetColors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (step < 3) {
                        EliteButton(
                            text = "CONTINUAR",
                            onClick = { step++ },
                            color = MeetColors.cyberCyan,
                            modifier = Modifier.weight(1f),
                            isEnabled = vehicleReady && when (step) {
                                1 -> appointmentReady && pickupReady
                                else -> true
                            },
                        )
                    } else {
                        EliteButton(
                            text = if (submission is DekraConciergeSubmissionState.Publishing) "GUARDANDO…" else "SOLICITAR SERVICIO",
                            onClick = {
                                val selected = vehicle ?: return@EliteButton
                                val request = DekraConciergeRequest(
                                    vehicleId = selected.id,
                                    vehicleDisplayName = "${selected.make} ${selected.model} ${selected.year}",
                                    maskedVin = DekraConciergePolicy.maskVin(selected.vin),
                                    maskedPlate = DekraConciergePolicy.maskPlate(selected.plate),
                                    appointmentMode = appointmentMode,
                                    station = station.trim(),
                                    appointmentDateTime = appointmentDateTime.trim(),
                                    reservationCode = reservationCode.trim().takeIf(String::isNotBlank),
                                    pickupZone = pickupZone.trim(),
                                    contactPhone = contactPhone.trim(),
                                    vehicleCondition = vehicleCondition,
                                    transportPlan = transportPlan,
                                    activeDtcs = activeDtcs.distinct(),
                                    notes = notes.trim(),
                                    precheckAuthorized = precheckAuthorized,
                                    custodyAuthorized = custodyAuthorized,
                                    independentResultAcknowledged = independentResultAcknowledged,
                                    officialFeeAcknowledged = officialFeeAcknowledged,
                                    stationRulesAcknowledged = stationRulesAcknowledged,
                                )
                                lastRequest = request
                                viewModel.publishDekraConciergeRequest(request)
                            },
                            color = MeetColors.neonGreen,
                            modifier = Modifier.weight(1f),
                            isEnabled = vehicleReady && appointmentReady && pickupReady && consentsReady &&
                                submission !is DekraConciergeSubmissionState.Publishing,
                        )
                    }
                }
            }
        }
    }

    when (val state = submission) {
        is DekraConciergeSubmissionState.Saved -> AlertDialog(
            onDismissRequest = {},
            containerColor = MeetColors.backgroundDeep,
            title = {
                Text(
                    if (state.cloudPublished) "Solicitud publicada" else "Solicitud guardada localmente",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    if (state.cloudPublished) {
                        "La solicitud quedó abierta en la Red de Reparación. Esto todavía no asigna conductor ni autoriza entregar llaves: revisa y acepta una oferta verificada antes del servicio."
                    } else {
                        "La red no confirmó la publicación. La solicitud está protegida en este dispositivo; vuelve a intentar cuando tengas conexión."
                    },
                    color = MeetColors.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (state.cloudPublished) onBack() else lastRequest?.let(viewModel::publishDekraConciergeRequest)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                ) {
                    Text(if (state.cloudPublished) "VER RED" else "REINTENTAR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = if (!state.cloudPublished) {
                {
                    TextButton(onClick = onBack) {
                        Text("VOLVER A LA RED", color = MeetColors.textSecondary)
                    }
                }
            } else null,
        )
        is DekraConciergeSubmissionState.Failed -> AlertDialog(
            onDismissRequest = viewModel::resetDekraConciergeSubmission,
            containerColor = MeetColors.backgroundDeep,
            title = { Text("No se pudo crear la solicitud", color = Color.White) },
            text = { Text(state.message, color = MeetColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = viewModel::resetDekraConciergeSubmission) {
                    Text("REVISAR", color = MeetColors.warning)
                }
            },
        )
        else -> Unit
    }
}

@Composable
private fun DekraProgress(step: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeetColors.backgroundDeep)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("Servicio", "Cita", "Revisión", "Autorizar").forEachIndexed { index, label ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            if (index <= step) MeetColors.cyberCyan else MeetColors.borderSubtle,
                            RoundedCornerShape(99.dp),
                        ),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    label,
                    color = if (index <= step) Color.White else MeetColors.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DekraHeroCard(vehicleLabel: String?, maskedPlate: String, activeDtcCount: Int) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.cyberCyan,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedNeonIcon(Icons.AutoMirrored.Filled.FactCheck, null, tint = MeetColors.cyberCyan)
                Column {
                    Text("DEKRA CONCIERGE 360", color = MeetColors.cyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("Tu carro, preparado y acompañado", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(
                vehicleLabel ?: "Vehículo pendiente de selección",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            if (maskedPlate.isNotBlank()) Text("Placa protegida: $maskedPlate", color = MeetColors.textSecondary, fontSize = 11.sp)
            if (activeDtcCount > 0) {
                Text(
                    "$activeDtcCount DTC activo(s) se incluirán como contexto del prechequeo; no equivalen al resultado DEKRA.",
                    color = MeetColors.warning,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun BlockingNotice(text: String, button: String, onClick: () -> Unit) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.warning,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, color = MeetColors.warning, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClick, border = BorderStroke(1.dp, MeetColors.warning)) {
                Text(button, color = MeetColors.warning, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ServicePromiseCard(onOpenBooking: () -> Unit, onOpenPolicy: () -> Unit) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.neonGreen,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("UN SERVICIO DE PRINCIPIO A FIN", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
            listOf(
                "1" to "Confirmamos la cita oficial, el vehículo y la logística de retiro.",
                "2" to "Documentamos entrega, llaves, kilometraje, combustible y estado exterior.",
                "3" to "Antes de la cita hacemos un prechequeo técnico explícito y trazable.",
                "4" to "Si no es seguro conducir, el plan cambia a grúa; no se improvisa.",
                "5" to "Acompañamos la inspección, conservamos el resultado y devolvemos el vehículo.",
                "6" to "Si hay defectos, explicamos el reporte y preparamos reparación/reinspección sin prometer aprobación.",
            ).forEach { (number, text) -> NumberedPromise(number, text) }
            HorizontalDivider(color = MeetColors.borderSubtle)
            Text(
                "MEET no es DEKRA y no puede influir ni garantizar el resultado. El servicio es de preparación, custodia y traslado.",
                color = MeetColors.warning,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenBooking, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, MeetColors.cyberCyan)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("CITA OFICIAL", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onOpenPolicy, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, MeetColors.textMuted)) {
                    Text("POLÍTICA DEKRA", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Información oficial verificada: ${DekraInspectionKnowledge.VERIFIED_ON}", color = MeetColors.textMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun NumberedPromise(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MeetColors.neonGreen.copy(alpha = 0.14f), RoundedCornerShape(7.dp))
                .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        Text(text, color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AppointmentAndPickupCard(
    appointmentMode: DekraAppointmentMode,
    onAppointmentMode: (DekraAppointmentMode) -> Unit,
    station: String,
    onStation: (String) -> Unit,
    appointmentDateTime: String,
    onAppointmentDateTime: (String) -> Unit,
    reservationCode: String,
    onReservationCode: (String) -> Unit,
    pickupZone: String,
    onPickupZone: (String) -> Unit,
    contactPhone: String,
    onContactPhone: (String) -> Unit,
    vehicleCondition: DekraVehicleCondition,
    onVehicleCondition: (DekraVehicleCondition) -> Unit,
    transportPlan: DekraTransportPlan,
    notes: String,
    onNotes: (String) -> Unit,
    onOpenBooking: () -> Unit,
    onOpenTow: () -> Unit,
) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.electricBlue,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CITA + RETIRO SEGURO", color = MeetColors.electricBlue, fontWeight = FontWeight.Black, fontSize = 12.sp)
            ChoiceRow(
                options = DekraAppointmentMode.entries,
                selected = appointmentMode,
                label = { it.displayName },
                onSelect = onAppointmentMode,
            )
            if (appointmentMode == DekraAppointmentMode.NEEDS_COORDINATION) {
                Text(
                    "MEET puede ayudarte a preparar los datos, pero la cita se confirma únicamente por los canales oficiales de DEKRA.",
                    color = MeetColors.warning,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                OutlinedButton(onClick = onOpenBooking, border = BorderStroke(1.dp, MeetColors.cyberCyan)) {
                    Text("ABRIR RESERVA OFICIAL", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                }
            }
            DekraTextField(station, onStation, "Estación DEKRA elegida o preferida")
            DekraTextField(appointmentDateTime, onAppointmentDateTime, "Fecha y hora confirmada o preferida")
            DekraTextField(reservationCode, onReservationCode, "Código de reservación (opcional)")
            DekraTextField(pickupZone, onPickupZone, "Zona de retiro aproximada")
            DekraTextField(contactPhone, onContactPhone, "Teléfono de coordinación")

            Text("CONDICIÓN ACTUAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            ChoiceRow(
                options = DekraVehicleCondition.entries,
                selected = vehicleCondition,
                label = { it.displayName },
                onSelect = onVehicleCondition,
            )
            Surface(
                color = if (transportPlan == DekraTransportPlan.TOW_ONLY) MeetColors.warning.copy(alpha = 0.12f) else MeetColors.neonGreen.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (transportPlan == DekraTransportPlan.TOW_ONLY) MeetColors.warning else MeetColors.neonGreen.copy(alpha = 0.5f)),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (transportPlan == DekraTransportPlan.TOW_ONLY) Icons.Default.LocalShipping else Icons.Default.DirectionsCar,
                        null,
                        tint = if (transportPlan == DekraTransportPlan.TOW_ONLY) MeetColors.warning else MeetColors.neonGreen,
                    )
                    Text(transportPlan.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
            if (transportPlan == DekraTransportPlan.TOW_ONLY) {
                OutlinedButton(onClick = onOpenTow, border = BorderStroke(1.dp, MeetColors.warning)) {
                    Text("VER SERVICIO DE GRÚA", color = MeetColors.warning, fontWeight = FontWeight.Bold)
                }
            }
            DekraTextField(notes, onNotes, "Notas, testigos, fugas o condiciones especiales", minLines = 3)
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { option ->
            val active = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (active) MeetColors.electricBlue.copy(alpha = 0.12f) else MeetColors.cardBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, if (active) MeetColors.electricBlue else MeetColors.borderSubtle, RoundedCornerShape(10.dp))
                    .clickable { onSelect(option) }
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = active, onClick = { onSelect(option) })
                Text(label(option), color = if (active) Color.White else MeetColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DekraTextField(value: String, onValue: (String) -> Unit, label: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, color = MeetColors.textSecondary, fontSize = 11.sp) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = MeetColors.electricBlue,
            unfocusedBorderColor = MeetColors.borderSubtle,
        ),
    )
}

@Composable
private fun InspectionEducationCard(onOpenManual: () -> Unit, onOpenFaq: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ChecklistCard(
            eyebrow = "ANTES DE LA CITA · MEET",
            title = "Prechequeo técnico documentado",
            intro = "Se realiza antes de ir a DEKRA. Detecta riesgos visibles y puntos que merecen atención; no sustituye la inspección oficial ni garantiza aprobación.",
            sections = DekraInspectionKnowledge.precheckSections,
            accent = MeetColors.neonGreen,
        )
        ChecklistCard(
            eyebrow = "EL DÍA DE LA PRUEBA · DEKRA",
            title = "Qué revisa la inspección oficial",
            intro = "DEKRA aplica el manual regulado por COSEVI mediante inspección visual y equipos. No desmonta piezas y puede aplicar controles adicionales según el tipo de vehículo.",
            sections = DekraInspectionKnowledge.officialInspectionSections,
            accent = MeetColors.cyberCyan,
        )
        EliteCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = MeetColors.warning,
            backgroundColor = MeetColors.backgroundDeep,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("REGLAS PRÁCTICAS DE LA CITA", color = MeetColors.warning, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text("• La persona que conduce debe portar licencia vigente para ese tipo de vehículo.\n• La cita está vinculada a la placa o DUA.\n• DEKRA concede 15 minutos posteriores a la hora; luego puede requerir reprogramación.\n• No se usan dispositivos electrónicos durante la inspección.\n• No deben ir mascotas ni menores sin el dispositivo de seguridad correspondiente.\n• El pago oficial se hace en la estación, según la tarifa vigente.", color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                Surface(
                    color = MeetColors.warning.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SI EL VEHÍCULO NO APRUEBA", color = MeetColors.warning, fontWeight = FontWeight.Black, fontSize = 10.sp)
                        Text(
                            "Con un único defecto grave, DEKRA indica reinspección del defecto dentro de un mes. Con más de un defecto grave corresponde inspección completa. MEET conserva el resultado, explica prioridades y puede enlazar reparación + nueva cita.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenManual, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, MeetColors.cyberCyan)) {
                        Text("MANUAL COSEVI", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onOpenFaq, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, MeetColors.textMuted)) {
                        Text("FAQ DEKRA", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Fuentes oficiales verificadas: ${DekraInspectionKnowledge.VERIFIED_ON}", color = MeetColors.textMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ChecklistCard(
    eyebrow: String,
    title: String,
    intro: String,
    sections: List<DekraChecklistSection>,
    accent: Color,
) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = accent,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(eyebrow, color = accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(intro, color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            sections.forEach { section ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(17.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(section.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(section.summary, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                        if (section.items.isNotEmpty()) {
                            Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                section.items.forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("•", color = accent.copy(alpha = 0.7f), fontSize = 10.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(item, color = MeetColors.textSecondary.copy(alpha = 0.9f), fontSize = 10.sp, lineHeight = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorizationCard(
    vehicleLabel: String,
    station: String,
    appointmentDateTime: String,
    pickupZone: String,
    transportPlan: DekraTransportPlan,
    precheckAuthorized: Boolean,
    onPrecheckAuthorized: (Boolean) -> Unit,
    custodyAuthorized: Boolean,
    onCustodyAuthorized: (Boolean) -> Unit,
    independentResultAcknowledged: Boolean,
    onIndependentResultAcknowledged: (Boolean) -> Unit,
    officialFeeAcknowledged: Boolean,
    onOfficialFeeAcknowledged: (Boolean) -> Unit,
    stationRulesAcknowledged: Boolean,
    onStationRulesAcknowledged: (Boolean) -> Unit,
    onOpenFees: () -> Unit,
) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.neonGreen,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedNeonIcon(Icons.Default.GppGood, null, tint = MeetColors.neonGreen)
                Text("AUTORIZACIÓN INFORMADA", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            Surface(color = MeetColors.cardBackground, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vehicleLabel, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("$station · $appointmentDateTime", color = MeetColors.textSecondary, fontSize = 11.sp)
                    Text("Retiro: $pickupZone", color = MeetColors.textSecondary, fontSize = 11.sp)
                    Text("Plan: ${transportPlan.displayName}", color = if (transportPlan == DekraTransportPlan.TOW_ONLY) MeetColors.warning else MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            ConsentRow(precheckAuthorized, onPrecheckAuthorized, "Autorizo el prechequeo técnico previo a la cita y su evidencia. Entiendo que cualquier reparación requiere una autorización separada.")
            ConsentRow(custodyAuthorized, onCustodyAuthorized, "Autorizo la recepción de llaves, custodia y traslado conforme al plan seguro confirmado; entregaré el vehículo sin armas, mascotas, menores ni carga restringida.")
            ConsentRow(independentResultAcknowledged, onIndependentResultAcknowledged, "Entiendo que DEKRA es independiente y que MEET no promete, negocia ni garantiza un resultado favorable.")
            ConsentRow(officialFeeAcknowledged, onOfficialFeeAcknowledged, "Entiendo que la tarifa oficial DEKRA se paga por separado en la estación y que el precio del concierge se acepta únicamente mediante una oferta MEET.")
            ConsentRow(stationRulesAcknowledged, onStationRulesAcknowledged, "Confirmo que habrá licencia vigente para el tipo de vehículo y que acepto las reglas oficiales de ingreso y comportamiento.")
            OutlinedButton(onClick = onOpenFees, border = BorderStroke(1.dp, MeetColors.cyberCyan)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("VER TARIFAS OFICIALES VIGENTES", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Publicar una solicitud no asigna proveedor ni autoriza entregar las llaves. La asignación ocurre solo después de revisar y aceptar una oferta.",
                color = MeetColors.warning,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ConsentRow(checked: Boolean, onChecked: (Boolean) -> Unit, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(text, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp).weight(1f))
    }
}
