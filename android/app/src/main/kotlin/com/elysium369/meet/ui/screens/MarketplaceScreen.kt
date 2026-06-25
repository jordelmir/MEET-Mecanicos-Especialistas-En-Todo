package com.elysium369.meet.ui.screens

import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.data.local.entities.ServiceBidEntity
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

    var showCreateDialog by remember { mutableStateOf(false) }
    var problemInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("San José, Costa Rica") }
    var priorityInput by remember { mutableStateOf("MEDIUM") }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "MEET MARKETPLACE\nServicios Automotrices VIP",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    IconButton(onClick = { navController.navigate("workshop_dashboard") }) {
                        Icon(
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
                Icon(Icons.Default.Add, contentDescription = "Nueva Solicitud", tint = MeetColors.neonGreen)
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
                    }
                }
            }

            // ── AI Health Detected Issues ──
            if (predictionEvents.isNotEmpty()) {
                item {
                    Text(
                        text = "ALERTAS DE SALUD AI ACTIVES",
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
                                    Icon(Icons.Default.Warning, "Alerta", tint = Color(0xFFFF4D4D), modifier = Modifier.size(18.dp))
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
                                Icon(Icons.Default.LocationOn, "Ubicación", tint = MeetColors.textMuted, modifier = Modifier.size(14.dp))
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
                                                Icon(Icons.Default.Star, "Rating", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
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
    }
}
