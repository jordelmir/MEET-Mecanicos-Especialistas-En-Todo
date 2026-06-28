package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.data.local.entities.ServiceBidEntity
import com.elysium369.meet.data.local.entities.PartOfferEntity
import kotlinx.coroutines.flow.Flow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val serviceRequests by viewModel.serviceRequests.collectAsState()
    val predictionEvents by viewModel.predictionEvents.collectAsState() // AI Health warnings
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val partRequests by viewModel.partRequests.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var problemInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("San José, Costa Rica") }
    var priorityInput by remember { mutableStateOf("MEDIUM") }
    var showPartRequestDialog by remember { mutableStateOf(false) }
    var partServiceRequestId by remember { mutableStateOf<String?>(null) }
    var partVehicleId by remember { mutableStateOf<String?>(null) }
    var partDtcCode by remember { mutableStateOf<String?>(null) }
    var partNameInput by remember { mutableStateOf("") }
    var partNumberInput by remember { mutableStateOf("") }
    var partQuantityInput by remember { mutableStateOf("1") }
    var partPreferenceInput by remember { mutableStateOf("ANY") }
    var partUrgencyInput by remember { mutableStateOf("40") }
    var partNotesInput by remember { mutableStateOf("") }
    var partDeliveryInput by remember { mutableStateOf("San José, Costa Rica") }
    var showPartOfferDialog by remember { mutableStateOf<String?>(null) }
    var offerStoreName by remember { mutableStateOf("") }
    var offerBrand by remember { mutableStateOf("") }
    var offerPartNumber by remember { mutableStateOf("") }
    var offerCondition by remember { mutableStateOf("NEW") }
    var offerPrice by remember { mutableStateOf("") }
    var offerDeliveryFee by remember { mutableStateOf("0") }
    var offerEta by remember { mutableStateOf("40") }
    var offerWarranty by remember { mutableStateOf("30") }
    var offerMessage by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "Elysium Vanguard MARKETPLACE\nServicios Automotrices VIP",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    IconButton(onClick = { navController.navigate("workshop_dashboard") }) {
                        AnimatedNeonIcon(
                            Icons.Default.CarRepair,
                            contentDescription = "Taller",
                            tint = MeetColors.cyberCyan
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MeetColors.backgroundDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, MeetColors.neonGreen, RoundedCornerShape(12.dp))
            ) {
                AnimatedNeonIcon(Icons.Default.Add, contentDescription = "Nueva Solicitud", tint = MeetColors.neonGreen)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                EliteCard(
                    glowColor = MeetColors.electricBlue,
                    borderColor = MeetColors.borderSubtle,
                    backgroundColor = MeetColors.backgroundDeep,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "SOLICITUD BIEN ARMADA = OFERTAS MEJORES",
                            color = MeetColors.electricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Describe síntoma, cuándo ocurre, si el vehículo arranca o se mueve, luces presentes, DTCs activos y si necesitas grúa o visita. Los talleres responden mejor cuando el caso llega triageado.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        if (activeDtcs.isNotEmpty()) {
                            Text(
                                "DTCs detectados en esta sesión: ${activeDtcs.take(3).joinToString()}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            EliteButton(
                                text = "🛠️ PEDIR AYUDA A UN MECÁNICO",
                                onClick = { navController.navigate("mechanic_service") },
                                color = MeetColors.neonGreen,
                                modifier = Modifier.fillMaxWidth()
                            )
                            EliteButton(
                                text = "🚛 PEDIR AYUDA A UNA GRÚA",
                                onClick = { navController.navigate("tow_truck_service") },
                                color = MeetColors.warning,
                                modifier = Modifier.fillMaxWidth()
                            )
                            EliteButton(
                                text = "🚗 PEDIR UN VIAJE / RIDE (SUBASTA)",
                                onClick = { navController.navigate("ride_service") },
                                color = MeetColors.electricBlue,
                                modifier = Modifier.fillMaxWidth()
                            )
                            EliteButton(
                                text = "🧩 PEDIR REPUESTOS",
                                onClick = { navController.navigate("part_request") },
                                color = MeetColors.cyberCyan,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── AI Health Detected Issues ──
            if (predictionEvents.isNotEmpty()) {
                item {
                    Text(
                        text = "ALERTAS DE SALUD IA ACTIVAS",
                        color = Color(0xFFFF4D4D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    predictionEvents.take(2).forEach { event ->
                        EliteCard(
                            glowColor = Color(0xFFFF4D4D),
                            borderColor = Color(0xFFFF4D4D).copy(alpha = 0.2f),
                            backgroundColor = MeetColors.cardBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFFF4D4D).copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedNeonIcon(Icons.Default.Warning, "Alerta", tint = Color(0xFFFF4D4D), modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        event.message,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "Prioridad: ${event.severity} | Fallo estimado en ~${event.estimatedDays} días",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                EliteButton(
                                    text = "COTIZAR",
                                    onClick = {
                                        problemInput = "Reemplazo de pastillas / sensor"
                                        descInput = "Alerta AI detectada: ${event.message}"
                                        priorityInput = "HIGH"
                                        showCreateDialog = true
                                    },
                                    color = MeetColors.neonGreen,
                                    modifier = Modifier.width(90.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Service Requests List ──
            item {
                Text(
                    text = "MIS SOLICITUDES DE SERVICIO",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (serviceRequests.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No tienes solicitudes activas.\nPublica una necesidad con síntoma, contexto y prioridad para empezar a recibir cotizaciones útiles.",
                            color = MeetColors.textMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(serviceRequests) { req ->
                    val bidsFlow = remember(req.requestId) { viewModel.getBidsForRequest(req.requestId) }
                    val bids by bidsFlow.collectAsState(initial = emptyList<ServiceBidEntity>())

                    EliteCard(
                        glowColor = MeetColors.cyberCyan,
                        borderColor = MeetColors.borderSubtle,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    req.problem,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            when (req.priority) {
                                                "HIGH" -> Color(0xFFFF4D4D).copy(alpha = 0.15f)
                                                else -> MeetColors.cyberCyan.copy(alpha = 0.15f)
                                            },
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        req.priority,
                                        color = if (req.priority == "HIGH") Color(0xFFFF4D4D) else MeetColors.cyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(req.description, color = MeetColors.textSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedNeonIcon(Icons.Default.LocationOn, "Ubicación", tint = MeetColors.textMuted, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(req.location, color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(14.dp))
                            
                            HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.4f))
                            Spacer(Modifier.height(10.dp))

                            Text(
                                "OFERTAS RECIBIDAS (${bids.size})",
                                color = MeetColors.neonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))

                            if (bids.isEmpty()) {
                                Text(
                                    "Esperando ofertas de talleres cercanos...",
                                    color = MeetColors.textMuted,
                                    fontSize = 12.sp
                                )
                            } else {
                                bids.forEach { bid ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MeetColors.cardBackground)
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(bid.shopName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(Modifier.width(6.dp))
                                                AnimatedNeonIcon(Icons.Default.Star, "Rating", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                                                Text(" ${bid.shopRating}", color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text("Tiempo: ${bid.estimatedHours}h | Garantía: ${bid.warrantyDays} días", color = MeetColors.textSecondary, fontSize = 11.sp)
                                            Text(bid.message, color = MeetColors.textMuted, fontSize = 11.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "¢${String.format("%,.0f", bid.price)}",
                                                color = MeetColors.neonGreen,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 15.sp
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            EliteButton(
                                                text = "ACEPTAR",
                                                onClick = {
                                                    viewModel.acceptBid(req.requestId, bid.bidId)
                                                },
                                                color = MeetColors.neonGreen,
                                                modifier = Modifier.width(80.dp).height(28.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            EliteButton(
                                text = "SOLICITAR REPUESTO PARA ESTE CASO",
                                onClick = {
                                    partServiceRequestId = req.requestId
                                    partVehicleId = req.vehicleId
                                    partDtcCode = req.autoDtcCode ?: activeDtcs.firstOrNull()
                                    partNameInput = suggestPartNameForDtc(req.autoDtcCode ?: activeDtcs.firstOrNull(), req.problem)
                                    partNumberInput = ""
                                    partQuantityInput = "1"
                                    partPreferenceInput = "ANY"
                                    partUrgencyInput = "40"
                                    partDeliveryInput = req.location
                                    partNotesInput = buildString {
                                        append("Caso de servicio: ${req.problem}. ")
                                        if (!req.autoDtcCode.isNullOrBlank()) append("DTC asociado: ${req.autoDtcCode}. ")
                                        append(req.description.take(160))
                                    }
                                    showPartRequestDialog = true
                                },
                                color = MeetColors.cyberCyan,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "SUBASTA DE REPUESTOS Y REPUESTERAS",
                    color = MeetColors.neonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (partRequests.isEmpty()) {
                item {
                    EliteCard(
                        glowColor = MeetColors.cyberCyan,
                        borderColor = MeetColors.borderSubtle,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Aún no hay piezas en subasta",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Cuando el mecánico confirme una pieza, publica la solicitud con vehículo, DTC, número de parte si existe y ubicación. Las repuesteras compiten por precio, ETA y garantía.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                items(partRequests) { partReq ->
                    val offersFlow = remember(partReq.requestId) { viewModel.getPartOffersForRequest(partReq.requestId) }
                    val offers by offersFlow.collectAsState(initial = emptyList<PartOfferEntity>())
                    EliteCard(
                        glowColor = if (partReq.status == "ACCEPTED") MeetColors.neonGreen else MeetColors.electricBlue,
                        borderColor = MeetColors.borderSubtle,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(partReq.partName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    Text(
                                        "${partReq.quantity} und · ${partReq.oemPreference} · ETA objetivo ${partReq.urgencyMinutes} min",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                    if (!partReq.dtcCode.isNullOrBlank()) {
                                        Text("DTC asociado: ${partReq.dtcCode}", color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (partReq.status == "ACCEPTED") MeetColors.neonGreen.copy(alpha = 0.14f) else MeetColors.electricBlue.copy(alpha = 0.14f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(partReq.status, color = if (partReq.status == "ACCEPTED") MeetColors.neonGreen else MeetColors.electricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(partReq.customerNotes, color = MeetColors.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedNeonIcon(Icons.Default.LocationOn, "Entrega", tint = MeetColors.textMuted, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(partReq.deliveryLocation, color = MeetColors.textMuted, fontSize = 11.sp)
                            }

                            HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.4f))
                            Text("OFERTAS DE REPUESTERAS (${offers.size})", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (offers.isEmpty()) {
                                Text("Esperando ofertas reales de repuesteras. También puedes registrar una oferta local desde modo repuestera.", color = MeetColors.textMuted, fontSize = 12.sp)
                            } else {
                                offers.forEach { offer ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MeetColors.cardBackground)
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(offer.storeName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${offer.brand} · ${offer.partNumber} · ${offer.condition}", color = MeetColors.textSecondary, fontSize = 11.sp)
                                            Text("Entrega ${offer.etaMinutes} min · Garantía ${offer.warrantyDays} días", color = MeetColors.textMuted, fontSize = 11.sp)
                                            if (offer.message.isNotBlank()) Text(offer.message, color = MeetColors.textMuted, fontSize = 11.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("¢${String.format("%,.0f", offer.price + offer.deliveryFee)}", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                            Spacer(Modifier.height(4.dp))
                                            if (partReq.status == "OPEN") {
                                                EliteButton(
                                                    text = "ACEPTAR",
                                                    onClick = { viewModel.acceptPartOffer(partReq.requestId, offer.offerId) },
                                                    color = MeetColors.neonGreen,
                                                    modifier = Modifier.width(84.dp)
                                                )
                                            } else if (offer.status == "ACCEPTED") {
                                                Text("ACEPTADA", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            EliteButton(
                                text = "OFERTAR COMO REPUESTERA",
                                onClick = {
                                    showPartOfferDialog = partReq.requestId
                                    offerStoreName = ""
                                    offerBrand = ""
                                    offerPartNumber = partReq.partNumber.orEmpty()
                                    offerCondition = "NEW"
                                    offerPrice = ""
                                    offerDeliveryFee = "0"
                                    offerEta = partReq.urgencyMinutes.toString()
                                    offerWarranty = "30"
                                    offerMessage = ""
                                },
                                color = MeetColors.electricBlue,
                                modifier = Modifier.fillMaxWidth(),
                                isEnabled = partReq.status == "OPEN"
                            )
                        }
                    }
                }
            }
        }

        // ── Create Request Dialog ──
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                containerColor = MeetColors.backgroundDeep,
                title = { Text("Publicar Solicitud de Servicio", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Incluye qué falla, cuándo ocurre, si el auto se puede mover y cualquier DTC o diagnóstico previo.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = problemInput,
                            onValueChange = { problemInput = it },
                            label = { Text("Problema / Necesidad", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        OutlinedTextField(
                            value = descInput,
                            onValueChange = { descInput = it },
                            label = { Text("Descripción Detallada y evidencia", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        OutlinedTextField(
                            value = locationInput,
                            onValueChange = { locationInput = it },
                            label = { Text("Ubicación", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("HIGH" to "Hoy", "MEDIUM" to "Próximo turno", "LOW" to "Programable").forEach { (value, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (priorityInput == value) MeetColors.neonGreen.copy(alpha = 0.14f) else MeetColors.backgroundDark)
                                        .border(1.dp, if (priorityInput == value) MeetColors.neonGreen else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { priorityInput = value }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (priorityInput == value) Color.White else MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (problemInput.isNotBlank() && selectedVehicle != null) {
                                viewModel.createServiceRequest(
                                    vehicleId = selectedVehicle!!.id,
                                    problem = problemInput,
                                    description = descInput,
                                    location = locationInput,
                                    priority = priorityInput
                                )
                                showCreateDialog = false
                                problemInput = ""
                                descInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                    ) {
                        Text("PUBLICAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("CANCELAR", color = MeetColors.textSecondary)
                    }
                }
            )
        }

        if (showPartRequestDialog) {
            AlertDialog(
                onDismissRequest = { showPartRequestDialog = false },
                containerColor = MeetColors.backgroundDeep,
                title = { Text("Solicitar Repuesto a Repuesteras", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Publica pieza, vehículo, urgencia y punto de entrega. Las ofertas se comparan por precio total, tiempo de llegada y garantía.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = partNameInput,
                            onValueChange = { partNameInput = it },
                            label = { Text("Pieza requerida", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                        )
                        OutlinedTextField(
                            value = partNumberInput,
                            onValueChange = { partNumberInput = it },
                            label = { Text("Número de parte / OEM (opcional)", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = partQuantityInput,
                                onValueChange = { partQuantityInput = it },
                                label = { Text("Cantidad", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                            )
                            OutlinedTextField(
                                value = partUrgencyInput,
                                onValueChange = { partUrgencyInput = it },
                                label = { Text("ETA máx. min", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ANY" to "Cualquiera", "OEM" to "OEM", "AFTERMARKET" to "Aftermarket").forEach { (value, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (partPreferenceInput == value) MeetColors.cyberCyan.copy(alpha = 0.14f) else MeetColors.backgroundDark)
                                        .border(1.dp, if (partPreferenceInput == value) MeetColors.cyberCyan else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { partPreferenceInput = value }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (partPreferenceInput == value) Color.White else MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = partDeliveryInput,
                            onValueChange = { partDeliveryInput = it },
                            label = { Text("Entrega en", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                        )
                        OutlinedTextField(
                            value = partNotesInput,
                            onValueChange = { partNotesInput = it },
                            label = { Text("Notas para compatibilidad", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val vehicleId = partVehicleId ?: selectedVehicle?.id
                            if (!vehicleId.isNullOrBlank() && partNameInput.isNotBlank()) {
                                viewModel.createPartRequest(
                                    serviceRequestId = partServiceRequestId,
                                    vehicleId = vehicleId,
                                    dtcCode = partDtcCode ?: activeDtcs.firstOrNull(),
                                    partName = partNameInput,
                                    partNumber = partNumberInput,
                                    quantity = partQuantityInput.toIntOrNull() ?: 1,
                                    oemPreference = partPreferenceInput,
                                    deliveryLocation = partDeliveryInput,
                                    urgencyMinutes = partUrgencyInput.toIntOrNull() ?: 40,
                                    customerNotes = partNotesInput
                                )
                                showPartRequestDialog = false
                                partNameInput = ""
                                partNumberInput = ""
                                partNotesInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan)
                    ) {
                        Text("PUBLICAR PIEZA", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPartRequestDialog = false }) {
                        Text("CANCELAR", color = MeetColors.textSecondary)
                    }
                }
            )
        }

        showPartOfferDialog?.let { requestId ->
            AlertDialog(
                onDismissRequest = { showPartOfferDialog = null },
                containerColor = MeetColors.backgroundDeep,
                title = { Text("Oferta de Repuestera", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Oferta completa: tienda, marca, número de parte, condición, precio, costo de entrega, ETA y garantía.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = offerStoreName,
                            onValueChange = { offerStoreName = it },
                            label = { Text("Nombre de repuestera", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = offerBrand,
                                onValueChange = { offerBrand = it },
                                label = { Text("Marca", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                            OutlinedTextField(
                                value = offerPartNumber,
                                onValueChange = { offerPartNumber = it },
                                label = { Text("Parte", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("NEW" to "Nueva", "OEM" to "OEM", "REMAN" to "Reman").forEach { (value, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (offerCondition == value) MeetColors.neonGreen.copy(alpha = 0.14f) else MeetColors.backgroundDark)
                                        .border(1.dp, if (offerCondition == value) MeetColors.neonGreen else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { offerCondition = value }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (offerCondition == value) Color.White else MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = offerPrice,
                                onValueChange = { offerPrice = it },
                                label = { Text("Precio ¢", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                            OutlinedTextField(
                                value = offerDeliveryFee,
                                onValueChange = { offerDeliveryFee = it },
                                label = { Text("Envío ¢", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = offerEta,
                                onValueChange = { offerEta = it },
                                label = { Text("ETA min", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                            OutlinedTextField(
                                value = offerWarranty,
                                onValueChange = { offerWarranty = it },
                                label = { Text("Garantía días", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                        }
                        OutlinedTextField(
                            value = offerMessage,
                            onValueChange = { offerMessage = it },
                            label = { Text("Mensaje / compatibilidad", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val price = offerPrice.toDoubleOrNull() ?: 0.0
                            if (offerStoreName.isNotBlank() && price > 0.0) {
                                viewModel.placePartOffer(
                                    partRequestId = requestId,
                                    storeName = offerStoreName,
                                    brand = offerBrand,
                                    partNumber = offerPartNumber,
                                    condition = offerCondition,
                                    price = price,
                                    deliveryFee = offerDeliveryFee.toDoubleOrNull() ?: 0.0,
                                    etaMinutes = offerEta.toIntOrNull() ?: 40,
                                    warrantyDays = offerWarranty.toIntOrNull() ?: 30,
                                    message = offerMessage
                                )
                                showPartOfferDialog = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                    ) {
                        Text("ENVIAR OFERTA", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPartOfferDialog = null }) {
                        Text("CANCELAR", color = MeetColors.textSecondary)
                    }
                }
            )
        }
    }
}

private fun suggestPartNameForDtc(dtcCode: String?, problem: String): String {
    val problemText = problem.lowercase()
    return when {
        dtcCode?.startsWith("P030") == true || problemText.contains("buj") || problemText.contains("misfire") ->
            "Bujía / bobina de encendido compatible"
        dtcCode in setOf("P0171", "P0174") || problemText.contains("mezcla") ->
            "Sensor MAF / manguera PCV / filtro combustible"
        dtcCode in setOf("P0420", "P0430") || problemText.contains("catalizador") ->
            "Sensor de oxígeno o catalizador compatible"
        problemText.contains("freno") || problemText.contains("pastilla") ->
            "Pastillas de freno compatibles"
        problemText.contains("alternador") || problemText.contains("bater") ->
            "Alternador / batería compatible"
        else -> "Repuesto requerido según diagnóstico"
    }
}
