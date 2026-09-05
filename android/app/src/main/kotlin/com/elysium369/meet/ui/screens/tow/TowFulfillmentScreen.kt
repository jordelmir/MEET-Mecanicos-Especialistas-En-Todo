package com.elysium369.meet.ui.screens.tow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.services.kernel.Money
import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.core.services.tow.*
import com.elysium369.meet.fulfillment.adapters.TowFulfillmentAdapter
import com.elysium369.meet.fulfillment.domain.FulfillmentUiAction
import com.elysium369.meet.fulfillment.ui.FulfillmentScaffold
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.util.UUID

@Composable
fun TowFulfillmentScreen(
    viewModel: ObdViewModel,
    towRepository: TowCommandRepository,
    onBack: () -> Unit = {},
) {
    val activeJob by towRepository.activeTowJob.collectAsState()

    if (activeJob != null) {
        val projection = remember(activeJob) {
            TowFulfillmentAdapter.toFulfillmentProjection(activeJob!!)
        }

        FulfillmentScaffold(
            projection = projection,
            onBack = onBack,
            onAction = { action ->
                when (action) {
                    is FulfillmentUiAction.Cancel -> {
                        towRepository.executeAction(
                            jobId = activeJob!!.jobId,
                            action = TowAction.Cancel("Cancelado por el cliente"),
                            actorRole = ServiceRole.CUSTOMER
                        )
                    }
                    is FulfillmentUiAction.ConfirmCompletion -> {
                        towRepository.executeAction(
                            jobId = activeJob!!.jobId,
                            action = TowAction.CompleteService,
                            actorRole = ServiceRole.CUSTOMER
                        )
                    }
                    else -> Unit
                }
            }
        )
    } else {
        // Tow Request Configuration Screen
        TowRequestConfigScreen(
            viewModel = viewModel,
            onBack = onBack,
            onRequestTow = { originAddr, destAddr, capabilities ->
                val originPoint = GeoPoint(9.9333, -84.0833)
                val destPoint = GeoPoint(9.9939, -84.2088)
                val price = Money.ofCrc(22000L)

                val customerId = runCatching {
                    UUID.fromString(viewModel.currentUserId ?: "")
                }.getOrElse { UUID.randomUUID() }

                val passenger = viewModel.passengerVerification.value
                val selectedVeh = viewModel.selectedVehicle.value

                towRepository.requestTow(
                    customerId = customerId,
                    customerName = passenger?.fullName ?: "Usuario MEET",
                    customerPhone = passenger?.phone ?: "+506 8000-0000",
                    vehicleVin = selectedVeh?.vin,
                    vehicleSummary = selectedVeh?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Registrado",
                    pickupLocation = originPoint,
                    pickupAddress = originAddr,
                    destinationLocation = destPoint,
                    destinationAddress = destAddr,
                    requiredCapabilities = capabilities,
                    estimatedPrice = price
                )
            }
        )
    }
}

@Composable
private fun TowRequestConfigScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    onRequestTow: (String, String, Set<TowCapabilities>) -> Unit,
) {
    val scrollState = rememberScrollState()
    var pickupAddress by remember { mutableStateOf("San José, Costa Rica (GPS Alta Precisión)") }
    var destinationAddress by remember { mutableStateOf("Taller Central Autorizado MEET, La Uruca") }
    val selectedCapabilities = remember { mutableStateListOf(TowCapabilities.FLATBED) }
    val selectedVeh by viewModel.selectedVehicle.collectAsState()

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = MeetColors.textPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Solicitar Grúa y Auxilio Vial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textPrimary
                )
            }
        },
        bottomBar = {
            Surface(
                color = MeetColors.cardBackgroundLighter,
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Button(
                    onClick = {
                        onRequestTow(pickupAddress, destinationAddress, selectedCapabilities.toSet())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("BUSCAR GRÚA COMPATIBLE", fontWeight = FontWeight.Black)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle Context Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "VEHÍCULO A REMOLCAR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textSecondary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        selectedVeh?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Activo Registrado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )
                    Text(
                        "VIN: ${selectedVeh?.vin ?: "No vinculado"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeetColors.textSecondary
                    )
                }
            }

            // Route details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "UBICACIÓN Y DESTINO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textSecondary,
                        letterSpacing = 1.2.sp
                    )

                    OutlinedTextField(
                        value = pickupAddress,
                        onValueChange = { pickupAddress = it },
                        label = { Text("Origen (Vehículo Varado)") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MeetColors.neonGreen) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderSubtle
                        )
                    )

                    OutlinedTextField(
                        value = destinationAddress,
                        onValueChange = { destinationAddress = it },
                        label = { Text("Destino de Remolque") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = MeetColors.electricBlue) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.electricBlue,
                            unfocusedBorderColor = MeetColors.borderSubtle
                        )
                    )
                }
            }

            // Capability Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "TIPO DE EQUIPO / CAPACIDAD REQUERIDA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textSecondary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    TowCapabilities.values().take(6).forEach { cap ->
                        val isSelected = selectedCapabilities.contains(cap)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedCapabilities.remove(cap) else selectedCapabilities.add(cap)
                            },
                            label = { Text(cap.displayName, fontSize = 12.sp) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // Estimated Price Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Estimación inicial", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                        Text("8.7 km aproximados", style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
                    }
                    Text("₡18.000 – ₡24.000", fontWeight = FontWeight.Black, color = MeetColors.neonGreen, fontSize = 18.sp)
                }
            }
        }
    }
}
