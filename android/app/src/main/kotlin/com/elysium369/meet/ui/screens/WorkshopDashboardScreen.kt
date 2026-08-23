package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.data.local.entities.ServiceBidEntity
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
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
import com.elysium369.meet.core.services.serviceos.*
import com.elysium369.meet.ui.screens.serviceos.WorkshopProKanbanView
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopDashboardScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val serviceRequests by viewModel.serviceRequests.collectAsState()
    val shopBids by viewModel.shopBids.collectAsState()

    var showBidDialog by remember { mutableStateOf<String?>(null) } // requestId to bid on
    var bidPrice by remember { mutableStateOf("") }
    var bidHours by remember { mutableStateOf("") }
    var bidWarranty by remember { mutableStateOf("30") }
    var bidMessage by remember { mutableStateOf("") }

    // Summary statistics
    val wonBidsCount = shopBids.count { it.status == "ACCEPTED" }
    // An accepted offer is authorized commercial intent, not earned or collected revenue.
    val acceptedOfferValue = shopBids.filter { it.status == "ACCEPTED" }.sumOf { it.price }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "WORKSHOP PRO DASHBOARD\nGestión de Ofertas y Agenda",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── B2B Earnings & Won Stats Card ──
            item {
                EliteCard(
                    glowColor = MeetColors.neonGreen,
                    borderColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("VALOR DE OFERTAS ACEPTADAS", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "¢${String.format("%,.0f", acceptedOfferValue)}",
                                color = MeetColors.neonGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TRABAJOS", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("$wonBidsCount", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("OFERTAS", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("${shopBids.size}", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // ── Workshop Operating System (Kanban, Bays & Financials) ──
            item {
                // Offers are not work orders, bays or technician assignments. Until those
                // authoritative records are persisted, render an honest empty operating board.
                val workOrders = remember { emptyList<WorkshopWorkOrderSummary>() }
                val bays = remember { emptyList<BayFacility>() }

                WorkshopProKanbanView(
                    workOrders = workOrders,
                    bays = bays,
                    onSelectWorkOrder = { },
                    onRequestChangeOrder = { }
                )
            }

            item {
                EliteCard(
                    glowColor = MeetColors.cyberCyan,
                    borderColor = MeetColors.borderSubtle,
                    backgroundColor = MeetColors.backgroundDeep,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "CÓMO GANAR OFERTAS SIN ADIVINAR",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Responde con diagnóstico probable, rango realista de horas, qué incluye la garantía y si el precio contempla escaneo, desmontaje o repuestos. Una oferta clara filtra clientes y evita retrabajos.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // ── Quick Toolbar (Schedule / Chats / Statistics) ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val toolbarItems = listOf(
                        Triple("Agenda", Icons.Default.CalendarMonth, MeetColors.electricBlue),
                        Triple("Mensajes", Icons.Default.Chat, MeetColors.cyberCyan),
                        Triple("Métricas", Icons.Default.Timeline, MeetColors.neonGreen)
                    )

                    toolbarItems.forEach { (label, icon, color) ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable { /* future integration */ },
                            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                            shape = RoundedCornerShape(10.dp),
                            border = BoxBorder(1.dp, MeetColors.borderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedNeonIcon(icon, label, tint = color, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Nearby Service Requests List ──
            item {
                Text(
                    text = "SOLICITUDES CERCANAS · RED ELYSIUM",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            val openRequests = serviceRequests.filter { it.status == "OPEN" }

            if (openRequests.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No hay solicitudes activas en tu zona.\nCuando lleguen, prioriza las que traen síntoma claro, DTC y evidencia mínima.",
                            color = MeetColors.textMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(openRequests) { req ->
                    val myBid = shopBids.find { it.requestId == req.requestId }

                    EliteCard(
                        glowColor = if (myBid != null) MeetColors.neonGreen else MeetColors.cyberCyan,
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
                                Text(req.problem, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (req.priority == "HIGH") Color(0xFFFF4D4D).copy(alpha = 0.15f)
                                            else MeetColors.cyberCyan.copy(alpha = 0.15f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        req.priority,
                                        color = if (req.priority == "HIGH") Color(0xFFFF4D4D) else MeetColors.cyberCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(req.description, color = MeetColors.textSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))

                            if (myBid != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MeetColors.neonGreen.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Tu Oferta: ¢${String.format("%,.0f", myBid.price)}", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Estado: ${myBid.status}", color = MeetColors.textSecondary, fontSize = 11.sp)
                                    }
                                    if (myBid.status == "ACCEPTED") {
                                        Box(
                                            modifier = Modifier
                                                .background(MeetColors.neonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text("TRABAJO GANADO", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                EliteButton(
                                    text = "ENVIAR OFERTA",
                                    onClick = { showBidDialog = req.requestId },
                                    color = MeetColors.neonGreen,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Place Bid Dialog ──
        showBidDialog?.let { reqId ->
            AlertDialog(
                onDismissRequest = { showBidDialog = null },
                containerColor = MeetColors.backgroundDeep,
                title = { Text("Enviar Oferta de Servicio", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Entrega una oferta útil: valor, tiempo, garantía y la lógica de diagnóstico que vas a seguir.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = bidPrice,
                            onValueChange = { bidPrice = it },
                            label = { Text("Precio (¢)", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        OutlinedTextField(
                            value = bidHours,
                            onValueChange = { bidHours = it },
                            label = { Text("Horas estimadas", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        OutlinedTextField(
                            value = bidWarranty,
                            onValueChange = { bidWarranty = it },
                            label = { Text("Garantía (días)", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        OutlinedTextField(
                            value = bidMessage,
                            onValueChange = { bidMessage = it },
                            label = { Text("Mensaje / plan de diagnóstico", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val priceVal = bidPrice.toDoubleOrNull() ?: 0.0
                            val hoursVal = bidHours.toDoubleOrNull() ?: 1.0
                            if (priceVal > 0) {
                                viewModel.placeServiceBid(
                                    requestId = reqId,
                                    price = priceVal,
                                    estimatedHours = hoursVal,
                                    warrantyDays = bidWarranty.toIntOrNull() ?: 30,
                                    message = bidMessage
                                )
                                showBidDialog = null
                                bidPrice = ""
                                bidHours = ""
                                bidMessage = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                    ) {
                        Text("ENVIAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBidDialog = null }) {
                        Text("CANCELAR", color = MeetColors.textSecondary)
                    }
                }
            )
        }
    }
}

// Simple BoxBorder workaround for compilation standard cards
@Composable
private fun BoxBorder(width: androidx.compose.ui.unit.Dp, color: Color) = 
    androidx.compose.foundation.BorderStroke(width, color)
