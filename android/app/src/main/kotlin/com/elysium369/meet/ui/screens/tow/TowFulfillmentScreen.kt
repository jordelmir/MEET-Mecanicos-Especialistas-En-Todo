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
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun TowFulfillmentScreen(
    viewModel: ObdViewModel,
    towRepository: TowCommandRepository,
    onBack: () -> Unit = {},
) {
    val activeJob by towRepository.activeTowJob.collectAsState()
    val scope = rememberCoroutineScope()

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
                            actorRole = ServiceRole.CUSTOMER,
                            expectedVersion = activeJob!!.serverVersion
                        )
                    }
                    is FulfillmentUiAction.ConfirmCompletion -> {
                        towRepository.executeAction(
                            jobId = activeJob!!.jobId,
                            action = TowAction.CompleteService,
                            actorRole = ServiceRole.CUSTOMER,
                            expectedVersion = activeJob!!.serverVersion
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
                val gps = viewModel.currentGpsLocation.value ?: return@TowRequestConfigScreen
                val originPoint = GeoPoint(gps.latitude, gps.longitude)
                val destPoint: GeoPoint? = null
                val price: Money? = null

                val userId = viewModel.currentUserId ?: return@TowRequestConfigScreen
                val customerId = runCatching {
                    UUID.fromString(userId)
                }.getOrElse { UUID.nameUUIDFromBytes(userId.toByteArray()) }

                val passenger = viewModel.passengerVerification.value
                val selectedVeh = viewModel.selectedVehicle.value ?: return@TowRequestConfigScreen

                scope.launch {
                    towRepository.requestTow(
                        customerId = customerId,
                        customerName = passenger?.fullName ?: "Cliente",
                        customerPhone = passenger?.phone ?: "",
                        vehicleVin = selectedVeh.vin,
                        vehicleSummary = "${selectedVeh.make} ${selectedVeh.model} ${selectedVeh.year}",
                        pickupLocation = originPoint,
                        pickupAddress = originAddr.ifBlank { "Ubicación GPS actual" },
                        destinationLocation = destPoint,
                        destinationAddress = destAddr.takeIf { it.isNotBlank() },
                        requiredCapabilities = capabilities,
                        estimatedPrice = price
                    )
                }
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
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    var pickupAddress by remember(currentGps) {
        mutableStateOf(currentGps?.let { "Lat: %.4f, Lng: %.4f".format(it.latitude, it.longitude) } ?: "")
    }
    var destinationAddress by remember { mutableStateOf("") }
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
            val canRequest = currentGps != null && selectedVeh != null && !viewModel.currentUserId.isNullOrBlank()
            Surface(
                color = MeetColors.cardBackgroundLighter,
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Button(
                    onClick = {
                        onRequestTow(pickupAddress, destinationAddress, selectedCapabilities.toSet())
                    },
                    enabled = canRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = MeetColors.cardBackground,
                        disabledContentColor = MeetColors.textSecondary
                    )
                ) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            currentGps == null -> "SEÑAL DE GPS REQUERIDA"
                            selectedVeh == null -> "SELECCIONA UN VEHÍCULO"
                            viewModel.currentUserId.isNullOrBlank() -> "INICIA SESIÓN"
                            else -> "BUSCAR GRÚA COMPATIBLE"
                        },
                        fontWeight = FontWeight.Black
                    )
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
            if (currentGps == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.electricBlue)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MeetColors.warning, modifier = Modifier.size(20.dp))
                        Text("Ubicación GPS no disponible. Active el GPS para solicitar rescate vial.", style = MaterialTheme.typography.bodySmall, color = MeetColors.textPrimary)
                    }
                }
            }

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
                        selectedVeh?.let { "${it.make} ${it.model} ${it.year}" } ?: "Ningún vehículo seleccionado",
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

            // Pricing Policy Card (Estimación Abierta)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cotización Abierta", fontWeight = FontWeight.Bold, color = MeetColors.neonGreen, fontSize = 16.sp)
                    Text("La tarifa final se cotiza en tiempo real por el operador asignado según el tipo de maniobra, patines de rescate y kilometraje de traslado.", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                }
            }
        }
    }
}
