package com.elysium369.meet.ui.screens.serviceos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.services.serviceos.*
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun WorkshopProKanbanView(
    workOrders: List<WorkshopWorkOrderSummary>,
    bays: List<BayFacility>,
    onSelectWorkOrder: (WorkshopWorkOrderSummary) -> Unit,
    onRequestChangeOrder: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Kanban Board, 1 = Bahías y Técnicos, 2 = Finanzas

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tab Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeetColors.cardBackground, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("TABLERO KANBAN", "BAHÍAS & AGENDA", "FINANZAS TALLER").forEachIndexed { idx, title ->
                val isSel = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSel) MeetColors.neonGreen.copy(alpha = 0.18f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (isSel) MeetColors.neonGreen else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = idx }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        color = if (isSel) MeetColors.neonGreen else MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (selectedTab == 0) {
            // Horizontal Kanban Columns
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(KanbanStage.values()) { stage ->
                    val ordersInStage = workOrders.filter { it.stage == stage }
                    Column(
                        modifier = Modifier
                            .width(260.dp)
                            .background(Color(0xFF0F1A2A), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(stage.badgeColorHex).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Column Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stage.displayName,
                                color = Color(stage.badgeColorHex),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Surface(
                                color = Color(stage.badgeColorHex).copy(alpha = 0.2f),
                                shape = CircleShape
                            ) {
                                Text(
                                    "${ordersInStage.size}",
                                    color = Color(stage.badgeColorHex),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MeetColors.borderSubtle, thickness = 0.5.dp)

                        if (ordersInStage.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Sin trabajos", color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                        } else {
                            ordersInStage.forEach { order ->
                                EliteCard(
                                    backgroundColor = MeetColors.cardBackground,
                                    borderColor = MeetColors.borderSubtle,
                                    shape = RoundedCornerShape(10.dp),
                                    onClick = { onSelectWorkOrder(order) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(order.vehicleDisplayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Cliente: ${order.customerName}", color = MeetColors.textSecondary, fontSize = 11.sp)
                                        
                                        if (order.assignedBayName != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Build, contentDescription = "Bay", tint = MeetColors.electricBlue, modifier = Modifier.size(11.dp))
                                                Text(order.assignedBayName, color = MeetColors.electricBlue, fontSize = 10.sp)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Autorizado:", color = MeetColors.textMuted, fontSize = 10.sp)
                                            Text(order.authorizedAmount.formatted(), color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (order.pendingChangeOrders.isNotEmpty()) {
                                            Surface(
                                                color = MeetColors.warning.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "⚠️ ${order.pendingChangeOrders.size} cambio(s) pendiente(s)",
                                                    color = MeetColors.warning,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
        } else if (selectedTab == 1) {
            // Bays & Technicians List
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ESTADO DE BAHÍAS DE TRABAJO", color = MeetColors.electricBlue, fontWeight = FontWeight.Black, fontSize = 12.sp)
                bays.forEach { bay ->
                    EliteCard(
                        backgroundColor = MeetColors.cardBackground,
                        borderColor = if (bay.isOccupied) MeetColors.warning.copy(alpha = 0.5f) else MeetColors.neonGreen.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(bay.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (bay.isOccupied) "Ocupada: ${bay.currentVehicleLabel ?: "Vehículo"}" else "Disponible para recepción",
                                    color = if (bay.isOccupied) MeetColors.warning else MeetColors.neonGreen,
                                    fontSize = 11.sp
                                )
                            }
                            if (bay.assignedTechnicianName != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Person, contentDescription = "Tech", tint = MeetColors.textSecondary, modifier = Modifier.size(12.dp))
                                    Text(bay.assignedTechnicianName, color = MeetColors.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Financial Breakdown Tab
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DESGLOSE FINANCIERO Y LIQUIDACIÓN", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
                
                val authorizedSum = workOrders.map { it.authorizedAmount }.fold(Money(0L, CurrencyCode.CRC)) { acc, m -> acc + m }
                
                EliteCard(
                    backgroundColor = MeetColors.cardBackground,
                    borderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Autorizado en Curso:", color = MeetColors.textSecondary, fontSize = 12.sp)
                            Text(authorizedSum.formatted(), color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cobrado / Liquidado:", color = MeetColors.textSecondary, fontSize = 12.sp)
                            Text("₡0", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pendiente por Facturar:", color = MeetColors.textSecondary, fontSize = 12.sp)
                            Text(authorizedSum.formatted(), color = MeetColors.warning, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
