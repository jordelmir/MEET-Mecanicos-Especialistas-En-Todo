package com.elysium369.meet.ui.screens.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.elysium369.meet.commerce.data.local.CommerceOrderEntity
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.ride.domain.RideCancellationReason
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.navigation.MeetDestinations
import com.elysium369.meet.ui.navigation.safeNavigate
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E E T   S E R V I C I O S   A C T I V O S   &   F I N A L I Z A D O S
 *  ──────────────────────────────────────────────────────────────
 *  Hub unificado de actividad para Clientes y Prestadores:
 *  - Tab 1: SERVICIOS ACTIVOS (Viajes, Pulperías & Sodas con Despacho
 *           Triangular, Grúas & Mecánica a Domicilio).
 *  - Tab 2: SERVICIOS FINALIZADOS (Historial forense con hash SHA-256,
 *           desglose en ₡ CRC, certificación, y repetición de pedido).
 *  - Modo Dual: Vista Usuario Solicitante 👤 vs Modo Prestador 🛠️🚗.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveAndCompletedServicesHubScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    initialTab: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var isProviderMode by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryFilter by rememberSaveable { mutableStateOf("TODOS") }

    // Reactivos de Viajes
    val userRides by viewModel.rideRequests.collectAsState(initial = emptyList())
    val activeRideRequest by viewModel.activeRideRequest.collectAsState(initial = null)
    val openRideRequests by viewModel.openRideRequests.collectAsState(initial = emptyList())
    val isDriverMode by viewModel.rideDriverMode.collectAsState(initial = false)

    // Reactivos de Servicios Especializados
    val serviceRequests by viewModel.serviceRequests.collectAsState(initial = emptyList())

    // Reactivos de Comercio Local (Pulperías & Sodas)
    val activeCommerceOrders by viewModel.activeCommerceOrders.collectAsState(initial = emptyList())
    val completedCommerceOrders by viewModel.completedCommerceOrders.collectAsState(initial = emptyList())
    val courierMissions by viewModel.activeCourierMissions.collectAsState(initial = emptyList())

    // Dialogs
    var showCommerceOrderDialog by remember { mutableStateOf(false) }
    var commerceOrderTypeSelected by remember { mutableStateOf("PULPERIA") } // "PULPERIA" o "SODA_RESTAURANT"
    var pinVerificationOrder by remember { mutableStateOf<CommerceOrderEntity?>(null) }
    var rideToCancel by remember { mutableStateOf<RideRequestEntity?>(null) }
    var certificateData by remember { mutableStateOf<ServiceCompletionCertificateData?>(null) }

    // Contadores activos
    val activeRidesCount = userRides.count { it.status in listOf("OPEN", "ACCEPTED", "ARRIVED", "IN_PROGRESS") }
    val activeServicesCount = serviceRequests.count { it.status in listOf("OPEN", "ACCEPTED") }
    val activeCommerceCount = activeCommerceOrders.size
    val totalActiveCount = activeRidesCount + activeServicesCount + activeCommerceCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CENTRO DE SERVICIOS MEET",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            text = if (isProviderMode) "Modo Prestador (Chofer / Repartidor / Comercio)" else "Modo Cliente (Mis Solicitudes y Pedidos)",
                            fontSize = 11.sp,
                            color = if (isProviderMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    // Botón para limpiar viajes trabados de emergencia
                    IconButton(onClick = {
                        viewModel.clearAllStuckRides()
                        Toast.makeText(context, "Viajes pendientes limpiados", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Limpiar", tint = Color.LightGray)
                    }

                    // Selector de Modo (Cliente vs Prestador)
                    FilterChip(
                        selected = isProviderMode,
                        onClick = { isProviderMode = !isProviderMode },
                        label = {
                            Text(
                                text = if (isProviderMode) "Prestador 🛠️" else "Cliente 👤",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.25f),
                            selectedLabelColor = MeetColors.neonGreen,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White,
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep)
            )
        },
        bottomBar = {
            // Barra de acceso rápido para crear nuevo servicio
            Surface(
                color = MeetColors.cardBackground,
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { navController.safeNavigate(MeetDestinations.RIDE_HOME) },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("Nuevo Viaje", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            commerceOrderTypeSelected = "PULPERIA"
                            showCommerceOrderDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("🏪 Pulpería", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            commerceOrderTypeSelected = "SODA_RESTAURANT"
                            showCommerceOrderDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("🍳 Soda", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pestañas Principales: ACTIVOS vs FINALIZADOS
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MeetColors.cardBackground,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = if (selectedTab == 0) MeetColors.neonGreen else MeetColors.cyberCyan
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚡ ACTIVOS",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) MeetColors.neonGreen else Color.LightGray
                            )
                            if (totalActiveCount > 0) {
                                Spacer(Modifier.width(6.dp))
                                Badge(containerColor = MeetColors.neonGreen) {
                                    Text(
                                        "$totalActiveCount",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "📜 FINALIZADOS",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) MeetColors.cyberCyan else Color.LightGray
                        )
                    }
                )
            }

            // Chips de filtrado por categoría
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterOptions = listOf(
                    "TODOS" to "Todos",
                    "RIDES" to "🚗 Viajes",
                    "COMMERCE" to "🏪 Pulperías & Sodas",
                    "TECHNICAL" to "🔧 Mecánica & Grúas"
                )
                items(filterOptions) { (key, label) ->
                    FilterChip(
                        selected = selectedCategoryFilter == key,
                        onClick = { selectedCategoryFilter = key },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                            selectedLabelColor = MeetColors.neonGreen,
                            containerColor = MeetColors.cardBackground,
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            // Contenido según pestaña
            if (selectedTab == 0) {
                // ─── PESTAÑA: SERVICIOS ACTIVOS ───
                ActiveServicesList(
                    isProviderMode = isProviderMode,
                    filter = selectedCategoryFilter,
                    userRides = userRides,
                    activeRide = activeRideRequest,
                    openRides = openRideRequests,
                    serviceRequests = serviceRequests,
                    activeCommerceOrders = activeCommerceOrders,
                    courierMissions = courierMissions,
                    onCancelRide = { ride -> rideToCancel = ride },
                    onTrackRide = { navController.safeNavigate(MeetDestinations.RIDE_HOME) },
                    onCancelCommerce = { orderId -> viewModel.cancelCommerceOrder(orderId) },
                    onCancelService = { reqId -> viewModel.cancelServiceRequest(reqId) },
                    onMerchantAdvance = { orderId, status -> viewModel.advanceCommerceMerchantStatus(orderId, status) },
                    onCourierClaim = { orderId ->
                        viewModel.assignCommerceCourier(
                            orderId = orderId,
                            courierId = "courier_${System.currentTimeMillis() % 1000}",
                            courierName = "Conductor Repartidor MEET",
                            courierPhone = "+506 8888-9999",
                            courierVehicle = "Motocicleta Express (Placa MOT-4421)"
                        )
                        Toast.makeText(context, "¡Misión de transporte asignada a ti!", Toast.LENGTH_SHORT).show()
                    },
                    onCourierTransit = { orderId, status -> viewModel.advanceCommerceCourierStatus(orderId, status) },
                    onVerifyPinClick = { order -> pinVerificationOrder = order }
                )
            } else {
                // ─── PESTAÑA: SERVICIOS FINALIZADOS ───
                CompletedServicesList(
                    filter = selectedCategoryFilter,
                    userRides = userRides,
                    serviceRequests = serviceRequests,
                    completedCommerceOrders = completedCommerceOrders,
                    onViewCertificate = { cert -> certificateData = cert },
                    onReorderCommerce = { order ->
                        viewModel.createCommerceOrder(
                            commerceType = order.commerceType,
                            merchantId = order.merchantId,
                            merchantName = order.merchantName,
                            merchantPhone = order.merchantPhone,
                            merchantAddress = order.merchantAddress,
                            merchantLat = order.merchantLat,
                            merchantLng = order.merchantLng,
                            customerName = order.customerName,
                            customerPhone = order.customerPhone,
                            deliveryAddress = order.deliveryAddress,
                            deliveryLat = order.deliveryLat,
                            deliveryLng = order.deliveryLng,
                            itemsJson = order.itemsJson,
                            itemsSubtotalMinor = order.itemsSubtotalMinor,
                            deliveryFeeMinor = order.deliveryFeeMinor,
                            paymentMethod = order.paymentMethod,
                            onCreated = {
                                Toast.makeText(context, "¡Pedido repetido con éxito! Preparando en local.", Toast.LENGTH_LONG).show()
                                selectedTab = 0
                            }
                        )
                    }
                )
            }
        }
    }

    // ── Dialog: Crear Pedido a Pulpería o Soda ──
    if (showCommerceOrderDialog) {
        CommerceStorefrontDialog(
            commerceType = commerceOrderTypeSelected,
            onDismiss = { showCommerceOrderDialog = false },
            onConfirmOrder = { storeName, storePhone, storeAddress, items, subtotal, deliveryFee, address, paymentMethod ->
                viewModel.createCommerceOrder(
                    commerceType = commerceOrderTypeSelected,
                    merchantId = "store_${storeName.hashCode()}",
                    merchantName = storeName,
                    merchantPhone = storePhone,
                    merchantAddress = storeAddress,
                    merchantLat = 9.9325,
                    merchantLng = -84.0512,
                    customerName = "Usuario MEET",
                    customerPhone = "+506 7000-0000",
                    deliveryAddress = address,
                    deliveryLat = 9.9333,
                    deliveryLng = -84.0833,
                    itemsJson = items,
                    itemsSubtotalMinor = subtotal,
                    deliveryFeeMinor = deliveryFee,
                    paymentMethod = paymentMethod,
                    onCreated = {
                        showCommerceOrderDialog = false
                        selectedTab = 0
                        Toast.makeText(context, "¡Pedido enviado a $storeName! Esperando confirmación y chofer.", Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // ── Dialog: Confirmación de Cancelación Segura de Viaje ──
    rideToCancel?.let { ride ->
        AlertDialog(
            onDismissRequest = { rideToCancel = null },
            title = {
                Text("Cancelar Solicitud de Viaje", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "¿Deseas cancelar el viaje hacia ${ride.destAddress}?",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Text(
                        "La unidad y el estado del servicio serán liberados de inmediato sin penalización.",
                        color = MeetColors.neonGreen,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reqId = ride.requestId
                        rideToCancel = null
                        if (ride.serverVersion == 0L || ride.syncState == "LOCAL_ONLY" || ride.status == "PENDING_PUBLICATION") {
                            viewModel.localCancelStuckRide(reqId)
                        } else {
                            viewModel.cancelRide(
                                requestId = reqId,
                                reason = if (isDriverMode) RideCancellationReason.PASSENGER_NO_SHOW else RideCancellationReason.CHANGE_OF_PLANS,
                                detail = "Cancelado por el usuario desde el panel unificado",
                                actorRole = if (isDriverMode) "DRIVER" else "PASSENGER"
                            )
                        }
                        Toast.makeText(context, "Viaje cancelado de forma segura.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Sí, Cancelar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { rideToCancel = null }) {
                    Text("Volver", color = Color.White)
                }
            },
            containerColor = MeetColors.cardBackground
        )
    }

    // ── Dialog: Verificación de PIN de Entrega para Chofer / Repartidor ──
    pinVerificationOrder?.let { order ->
        DeliveryPinVerificationDialog(
            order = order,
            onDismiss = { pinVerificationOrder = null },
            onVerify = { enteredPin, rating, review ->
                viewModel.verifyCommerceDeliveryPin(
                    orderId = order.orderId,
                    enteredPin = enteredPin,
                    rating = rating,
                    review = review,
                    onSuccess = {
                        pinVerificationOrder = null
                        Toast.makeText(context, "¡PIN verificado! Entrega exitosa y fondos de envío liberados.", Toast.LENGTH_LONG).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // ── Dialog: Certificado Forense SHA-256 ──
    certificateData?.let { cert ->
        ServiceCompletionCertificateDialog(
            certificate = cert,
            onDismiss = { certificateData = null }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
//  L I S T A   D E   S E R V I C I O S   A C T I V O S
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun ActiveServicesList(
    isProviderMode: Boolean,
    filter: String,
    userRides: List<RideRequestEntity>,
    activeRide: RideRequestEntity?,
    openRides: List<RideRequestEntity>,
    serviceRequests: List<ServiceRequestEntity>,
    activeCommerceOrders: List<CommerceOrderEntity>,
    courierMissions: List<CommerceOrderEntity>,
    onCancelRide: (RideRequestEntity) -> Unit,
    onTrackRide: () -> Unit,
    onCancelCommerce: (String) -> Unit,
    onCancelService: (String) -> Unit,
    onMerchantAdvance: (String, String) -> Unit,
    onCourierClaim: (String) -> Unit,
    onCourierTransit: (String, String) -> Unit,
    onVerifyPinClick: (CommerceOrderEntity) -> Unit,
) {
    val activeRides = userRides.filter { it.status in listOf("OPEN", "ACCEPTED", "ARRIVED", "IN_PROGRESS", "PENDING_PUBLICATION") }
    val activeServices = serviceRequests.filter { it.status in listOf("OPEN", "ACCEPTED") }

    val hasAny = activeRides.isNotEmpty() || activeServices.isNotEmpty() || activeCommerceOrders.isNotEmpty() || (isProviderMode && courierMissions.isNotEmpty())

    if (!hasAny) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MeetColors.neonGreen.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No tienes servicios activos en este momento",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pide un viaje, abarrotes de pulpería, casados de soda o servicio técnico con los botones inferiores.",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
    ) {
        // ── 1. VIAJES ACTIVOS ──
        if (filter == "TODOS" || filter == "RIDES") {
            if (activeRides.isNotEmpty()) {
                item {
                    Text(
                        "🚗 VIAJES EN CURSO",
                        color = MeetColors.cyberCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
                items(activeRides, key = { "ride_${it.requestId}" }) { ride ->
                    ActiveRideCard(
                        ride = ride,
                        onCancel = { onCancelRide(ride) },
                        onTrack = onTrackRide
                    )
                }
            }
        }

        // ── 2. PULPERÍAS & SODAS (COMMERCE & DELIVERY) ──
        if (filter == "TODOS" || filter == "COMMERCE") {
            if (activeCommerceOrders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🏪 PEDIDOS DE PULPERÍAS & SODAS",
                        color = Color(0xFFFFB74D),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
                items(activeCommerceOrders, key = { "order_${it.orderId}" }) { order ->
                    ActiveCommerceOrderCard(
                        order = order,
                        isProviderMode = isProviderMode,
                        onCancel = { onCancelCommerce(order.orderId) },
                        onMerchantAdvance = { status -> onMerchantAdvance(order.orderId, status) },
                        onCourierTransit = { status -> onCourierTransit(order.orderId, status) },
                        onVerifyPinClick = { onVerifyPinClick(order) }
                    )
                }
            }

            // Misiones para repartidor / chofer en Modo Prestador
            if (isProviderMode && courierMissions.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "📦 MISIONES DISPONIBLES PARA REPARTIDOR / CHOFER",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
                items(courierMissions, key = { "mission_${it.orderId}" }) { mission ->
                    AvailableCourierMissionCard(
                        order = mission,
                        onClaimMission = { onCourierClaim(mission.orderId) }
                    )
                }
            }
        }

        // ── 3. SERVICIOS TÉCNICOS & GRÚAS ──
        if (filter == "TODOS" || filter == "TECHNICAL") {
            if (activeServices.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🔧 SERVICIOS TÉCNICOS & RESCATE",
                        color = MeetColors.electricBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
                items(activeServices, key = { "svc_${it.requestId}" }) { svc ->
                    ActiveTechnicalServiceCard(
                        request = svc,
                        onCancel = { onCancelService(svc.requestId) }
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  T A R J E T A :   V I A J E   A C T I V O
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun ActiveRideCard(
    ride: RideRequestEntity,
    onCancel: () -> Unit,
    onTrack: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚗", fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Viaje en Curso",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }

                val statusLabel = when (ride.status) {
                    "OPEN", "PENDING_PUBLICATION" -> "Buscando conductor..."
                    "ACCEPTED" -> "Conductor asignado"
                    "ARRIVED" -> "Conductor en punto"
                    "IN_PROGRESS" -> "En viaje a destino"
                    else -> ride.status
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.cyberCyan.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan)
                ) {
                    Text(
                        text = statusLabel,
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Ruta
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Origen: ${ride.pickupAddress}",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Destino: ${ride.destAddress}",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))

            // PIN y Conductor si aplica
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tarifa Oficial", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        "₡${String.format("%,.0f", ride.priceOffer)} CRC",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }

                if (!ride.boardingPin.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PIN de Abordaje", color = Color.Gray, fontSize = 11.sp)
                        Surface(
                            color = MeetColors.neonGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MeetColors.neonGreen)
                        ) {
                            Text(
                                ride.boardingPin,
                                color = MeetColors.neonGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Pago", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        if (ride.paymentMethod == "SINPE") "SINPE Móvil" else "Efectivo",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            if (!ride.assignedDriverName.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Conductor: ${ride.assignedDriverName} · ${ride.assignedDriverVehicle ?: "Vehículo Registrado"}",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancelar Viaje", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onTrack,
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Navigation, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ver en Mapa", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  T A R J E T A :   P U L P E R Í A   O   S O D A   A C T I V A
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun ActiveCommerceOrderCard(
    order: CommerceOrderEntity,
    isProviderMode: Boolean,
    onCancel: () -> Unit,
    onMerchantAdvance: (String) -> Unit,
    onCourierTransit: (String) -> Unit,
    onVerifyPinClick: () -> Unit,
) {
    val isSoda = order.commerceType == "SODA_RESTAURANT"
    val icon = if (isSoda) "🍳" else "🏪"
    val typeTitle = if (isSoda) "Soda / Restaurante" else "Pulpería Express"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (isSoda) Color(0xFFFF7043).copy(alpha = 0.6f) else Color(0xFFFFB74D).copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabecera
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(order.merchantName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        Text(typeTitle, color = Color.Gray, fontSize = 11.sp)
                    }
                }

                val statusLabel = when (order.status) {
                    "PLACED" -> "Orden recibida"
                    "CONFIRMED" -> "Confirmada"
                    "PREPARING" -> "En preparación"
                    "READY_FOR_PICKUP" -> "Lista para chofer"
                    "COURIER_ASSIGNED" -> "Chofer en camino a local"
                    "IN_TRANSIT" -> "En camino a tu casa 🛵"
                    "ARRIVED" -> "Chofer en tu puerta 🚪"
                    else -> order.status
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFB74D).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Text(
                        statusLabel,
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Artículos pedidos
            Surface(
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Artículos solicitados:", color = Color.Gray, fontSize = 11.sp)
                    Text(order.itemsJson, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dirección de entrega
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Entrega en: ${order.deliveryAddress}",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))

            // Desglose de Pago y PIN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total (Subtotal + Envío)", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        "₡${String.format("%,d", order.totalAmountMinor)} CRC",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    Text(
                        "Local: ₡${String.format("%,d", order.itemsSubtotalMinor)} · Envío: ₡${String.format("%,d", order.deliveryFeeMinor)}",
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                }

                // PIN de Entrega (Seguridad)
                Column(horizontalAlignment = Alignment.End) {
                    Text("PIN de Entrega", color = Color.Gray, fontSize = 11.sp)
                    Surface(
                        color = MeetColors.neonGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MeetColors.neonGreen)
                    ) {
                        Text(
                            order.deliveryPin,
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text("Dar al chofer al recibir", color = Color.Gray, fontSize = 9.sp)
                }
            }

            // Datos de Chofer asignado si existe
            if (!order.courierName.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeliveryDining, null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Repartidor: ${order.courierName} (${order.courierVehicle ?: "Moto"})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Acciones según Modo
            if (!isProviderMode) {
                // Modo Cliente: Cancelar si aún no está en ruta
                if (order.status in listOf("PLACED", "CONFIRMED", "PREPARING")) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancelar Pedido", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Modo Prestador: Comerciante o Chofer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (order.status) {
                        "PLACED" -> {
                            Button(
                                onClick = { onMerchantAdvance("CONFIRMED") },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Confirmar Pedido (Comercio)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "CONFIRMED" -> {
                            Button(
                                onClick = { onMerchantAdvance("PREPARING") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Empezar Preparación", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "PREPARING" -> {
                            Button(
                                onClick = { onMerchantAdvance("READY_FOR_PICKUP") },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Listo para Chofer MEET", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "COURIER_ASSIGNED" -> {
                            Button(
                                onClick = { onCourierTransit("IN_TRANSIT") },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Recogido y en Ruta 🛵", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "IN_TRANSIT" -> {
                            Button(
                                onClick = { onCourierTransit("ARRIVED") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Llegué al Cliente 🚪", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "ARRIVED" -> {
                            Button(
                                onClick = onVerifyPinClick,
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Verified, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Verificar PIN y Entregar", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  T A R J E T A :   M I S I Ó N   P A R A   R E P A R T I D O R
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun AvailableCourierMissionCard(
    order: CommerceOrderEntity,
    onClaimMission: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📦", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Misión de Entrega",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Text(
                    "Ganancia: ₡${String.format("%,d", order.deliveryFeeMinor)} CRC",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Recoger en: ${order.merchantName} (${order.merchantAddress})",
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Entregar en: ${order.deliveryAddress}",
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClaimMission,
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tomar Misión de Envío", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  T A R J E T A :   S E R V I C I O   T É C N I C O   A C T I V O
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun ActiveTechnicalServiceCard(
    request: ServiceRequestEntity,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔧", fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Servicio Técnico", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.electricBlue.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MeetColors.electricBlue)
                ) {
                    Text(
                        if (request.status == "ACCEPTED") "Especialista asignado" else "Buscando especialista",
                        color = MeetColors.electricBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Requerimiento: ${request.problem}", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text("Ubicación: ${request.location}", color = Color.LightGray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Oferta: ₡${String.format("%,.0f", request.priceOffer)} CRC",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancelar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  L I S T A   D E   S E R V I C I O S   F I N A L I Z A D O S
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun CompletedServicesList(
    filter: String,
    userRides: List<RideRequestEntity>,
    serviceRequests: List<ServiceRequestEntity>,
    completedCommerceOrders: List<CommerceOrderEntity>,
    onViewCertificate: (ServiceCompletionCertificateData) -> Unit,
    onReorderCommerce: (CommerceOrderEntity) -> Unit,
) {
    val completedRides = userRides.filter { it.status in listOf("COMPLETED", "CANCELLED") }
    val completedServices = serviceRequests.filter { it.status in listOf("COMPLETED", "CANCELLED") }

    val hasAny = completedRides.isNotEmpty() || completedServices.isNotEmpty() || completedCommerceOrders.isNotEmpty()

    if (!hasAny) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No hay historial de servicios finalizados",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tus viajes y pedidos completados aparecerán aquí con certificado forense SHA-256.",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
    ) {
        // Pulperías y Sodas finalizadas
        if (filter == "TODOS" || filter == "COMMERCE") {
            items(completedCommerceOrders, key = { "comp_comm_${it.orderId}" }) { order ->
                CompletedCommerceCard(
                    order = order,
                    onViewCertificate = onViewCertificate,
                    onReorder = { onReorderCommerce(order) }
                )
            }
        }

        // Viajes finalizados
        if (filter == "TODOS" || filter == "RIDES") {
            items(completedRides, key = { "comp_ride_${it.requestId}" }) { ride ->
                CompletedRideCard(
                    ride = ride,
                    onViewCertificate = onViewCertificate
                )
            }
        }

        // Servicios técnicos finalizados
        if (filter == "TODOS" || filter == "TECHNICAL") {
            items(completedServices, key = { "comp_svc_${it.requestId}" }) { svc ->
                CompletedServiceCard(
                    request = svc,
                    onViewCertificate = onViewCertificate
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  T A R J E T A :   P U L P E R Í A / S O D A   F I N A L I Z A D A
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun CompletedCommerceCard(
    order: CommerceOrderEntity,
    onViewCertificate: (ServiceCompletionCertificateData) -> Unit,
    onReorder: () -> Unit,
) {
    val isSoda = order.commerceType == "SODA_RESTAURANT"
    val icon = if (isSoda) "🍳" else "🏪"
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "CR")).format(Date(order.completedAt ?: order.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(order.merchantName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = if (order.status == "DELIVERED") "✓ Entregado" else "Cancelado",
                    color = if (order.status == "DELIVERED") MeetColors.neonGreen else Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(dateStr, color = Color.Gray, fontSize = 11.sp)
            Text("Artículos: ${order.itemsJson}", color = Color.LightGray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Entregado en: ${order.deliveryAddress}", color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total: ₡${String.format("%,d", order.totalAmountMinor)} CRC",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (order.status == "DELIVERED") {
                        FilledTonalButton(
                            onClick = {
                                val certId = "cert_comm_${order.orderId.take(8)}"
                                val hash = order.integrityHash ?: HashEngine.sha256Hex("${order.orderId}|${order.totalAmountMinor}|DELIVERED")
                                onViewCertificate(
                                    ServiceCompletionCertificateData(
                                        reportId = certId,
                                        integrityHash = hash,
                                        vehicleId = order.courierVehicle ?: "REPARTIDOR_EXPRESS",
                                        generatedAt = dateStr,
                                        reportType = "ENTREGA DE COMERCIO LOCAL",
                                        verifierUrl = "https://meet.elysium369.cr/verify?id=$certId",
                                        escrowStatus = "FONDOS LIBERADOS",
                                        amountCrc = order.totalAmountMinor.toDouble(),
                                        specialistName = order.merchantName,
                                        problemDescription = order.itemsJson,
                                        ratingStars = order.ratingStars ?: 5,
                                        qrMinimalPayload = "$certId|$hash|${order.orderId}|${order.completedAt}|COMMERCE_DELIVERY"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Certificado 📜", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = onReorder,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Repetir 🔁", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  T A R J E T A :   V I A J E   F I N A L I Z A D O
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun CompletedRideCard(
    ride: RideRequestEntity,
    onViewCertificate: (ServiceCompletionCertificateData) -> Unit,
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "CR")).format(Date(ride.completedAt ?: ride.createdAt))
    val isDone = ride.status == "COMPLETED"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚗", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Viaje Finalizado", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = if (isDone) "✓ Completado" else "Cancelado",
                    color = if (isDone) MeetColors.neonGreen else Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(dateStr, color = Color.Gray, fontSize = 11.sp)
            Text("Destino: ${ride.destAddress}", color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!ride.assignedDriverName.isNullOrBlank()) {
                Text("Chofer: ${ride.assignedDriverName}", color = Color.LightGray, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Monto: ₡${String.format("%,.0f", ride.finalPrice ?: ride.priceOffer)} CRC",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                if (isDone) {
                    FilledTonalButton(
                        onClick = {
                            val certId = "cert_ride_${ride.requestId.take(8)}"
                            val hash = HashEngine.sha256Hex("${ride.requestId}|${ride.priceOffer}|COMPLETED")
                            onViewCertificate(
                                ServiceCompletionCertificateData(
                                    reportId = certId,
                                    integrityHash = hash,
                                    vehicleId = ride.assignedDriverVehicle ?: "VEH_RIDE",
                                    generatedAt = dateStr,
                                    reportType = "VIAJE CERTIFICADO MEET",
                                    verifierUrl = "https://meet.elysium369.cr/verify?id=$certId",
                                    escrowStatus = "TARIFA LIQUIDADA",
                                    amountCrc = ride.finalPrice ?: ride.priceOffer,
                                    specialistName = ride.assignedDriverName ?: "Chofer Certificado",
                                    problemDescription = "Traslado de ${ride.pickupAddress} a ${ride.destAddress}",
                                    ratingStars = ride.passengerRating?.toInt() ?: 5,
                                    qrMinimalPayload = "$certId|$hash|${ride.requestId}|${ride.completedAt}|RIDE_COMPLETION"
                                )
                            )
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Certificado SHA-256 📜", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  T A R J E T A :   S E R V I C I O   T É C N I C O   F I N A L I Z A D O
// ──────────────────────────────────────────────────────────────────────
@Composable
private fun CompletedServiceCard(
    request: ServiceRequestEntity,
    onViewCertificate: (ServiceCompletionCertificateData) -> Unit,
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("es", "CR")).format(Date(request.completedAt ?: request.createdAt))
    val isDone = request.status == "COMPLETED"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔧", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Servicio Técnico", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = if (isDone) "✓ Completado" else "Cancelado",
                    color = if (isDone) MeetColors.neonGreen else Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(dateStr, color = Color.Gray, fontSize = 11.sp)
            Text(request.problem, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total: ₡${String.format("%,.0f", request.priceOffer)} CRC",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                if (isDone) {
                    FilledTonalButton(
                        onClick = {
                            val certId = "cert_svc_${request.requestId.take(8)}"
                            val hash = HashEngine.sha256Hex("${request.requestId}|${request.priceOffer}|COMPLETED")
                            onViewCertificate(
                                ServiceCompletionCertificateData(
                                    reportId = certId,
                                    integrityHash = hash,
                                    vehicleId = request.vehicleId.ifBlank { "VEH_CLIENT" },
                                    generatedAt = dateStr,
                                    reportType = "SERVICIO TÉCNICO FORENSE",
                                    verifierUrl = "https://meet.elysium369.cr/verify?id=$certId",
                                    escrowStatus = "FONDOS LIBERADOS",
                                    amountCrc = request.priceOffer,
                                    specialistName = request.assignedMechanicName ?: "Especialista Certificado",
                                    problemDescription = request.problem,
                                    ratingStars = 5,
                                    qrMinimalPayload = "$certId|$hash|${request.requestId}|${request.completedAt}|TECH_SERVICE"
                                )
                            )
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Certificado SHA-256 📜", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  D I A L O G :   T I E N D A   P U L P E R Í A   /   S O D A
// ──────────────────────────────────────────────────────────────────────
@Composable
fun CommerceStorefrontDialog(
    commerceType: String,
    onDismiss: () -> Unit,
    onConfirmOrder: (
        storeName: String,
        storePhone: String,
        storeAddress: String,
        items: String,
        subtotal: Long,
        deliveryFee: Long,
        address: String,
        paymentMethod: String
    ) -> Unit,
) {
    val isSoda = commerceType == "SODA_RESTAURANT"
    val title = if (isSoda) "Pedir a Soda / Restaurante 🍳" else "Pedir a Pulpería Express 🏪"

    val sampleStores = if (isSoda) {
        listOf(
            Triple("Soda Criolla Doña María", "+506 2283-9090", "Barrio Dent, San José"),
            Triple("Soda El Parque (Comida Casera)", "+506 2253-1234", "Zapote, frente a la Rotonda")
        )
    } else {
        listOf(
            Triple("Pulpería El Sol (Panadería & Abarrotes)", "+506 2225-1020", "San Pedro de Montes de Oca"),
            Triple("Pulpería La Amistad (Canasta Básica)", "+506 2271-4488", "Curridabat Centro")
        )
    }

    var selectedStoreIndex by remember { mutableIntStateOf(0) }
    var customOrderText by remember {
        mutableStateOf(
            if (isSoda) "1 Casado con carne mechada y fresco de mora"
            else "1 bolsa de pan baguette, 1 leche Dos Pinos 1L, 6 huevos"
        )
    }
    var deliveryAddressText by remember { mutableStateOf("Mi Casa (GPS actual, 1.8 km)") }
    var paymentMethod by remember { mutableStateOf("CASH") } // "CASH" o "SINPE"
    val deliveryFee = 1500L
    val estimatedSubtotal = if (isSoda) 4200L else 3800L
    val total = estimatedSubtotal + deliveryFee

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MeetColors.cardBackground,
            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                Spacer(Modifier.height(12.dp))

                Text("Seleccionar comercio cercano:", color = Color.LightGray, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                sampleStores.forEachIndexed { index, store ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStoreIndex = index }
                            .background(
                                if (selectedStoreIndex == index) MeetColors.cyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStoreIndex == index,
                            onClick = { selectedStoreIndex = index },
                            colors = RadioButtonDefaults.colors(selectedColor = MeetColors.cyberCyan)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(store.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(store.third, color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("¿Qué deseas ordenar?", color = Color.LightGray, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customOrderText,
                    onValueChange = { customOrderText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(Modifier.height(10.dp))
                Text("Dirección de entrega:", color = Color.LightGray, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = deliveryAddressText,
                    onValueChange = { deliveryAddressText = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(Modifier.height(10.dp))
                Text("Método de pago:", color = Color.LightGray, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paymentMethod == "SINPE",
                        onClick = { paymentMethod = "SINPE" },
                        label = { Text("SINPE Móvil 📲", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.25f),
                            selectedLabelColor = MeetColors.neonGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = paymentMethod == "CASH",
                        onClick = { paymentMethod = "CASH" },
                        label = { Text("Efectivo 💵", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.25f),
                            selectedLabelColor = MeetColors.neonGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))
                // Resumen
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Subtotal estimado:", color = Color.LightGray, fontSize = 12.sp)
                            Text("₡${String.format("%,d", estimatedSubtotal)} CRC", color = Color.White, fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Envío (Chofer / Repartidor MEET):", color = Color.LightGray, fontSize = 12.sp)
                            Text("₡${String.format("%,d", deliveryFee)} CRC", color = MeetColors.cyberCyan, fontSize = 12.sp)
                        }
                        Divider(Modifier.padding(vertical = 4.dp), color = Color.Gray.copy(alpha = 0.3f))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Total a pagar:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("₡${String.format("%,d", total)} CRC", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val store = sampleStores[selectedStoreIndex]
                            onConfirmOrder(
                                store.first,
                                store.second,
                                store.third,
                                customOrderText,
                                estimatedSubtotal,
                                deliveryFee,
                                deliveryAddressText,
                                paymentMethod
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirmar Pedido", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  D I A L O G :   V E R I F I C A R   P I N   D E   E N T R E G A
// ──────────────────────────────────────────────────────────────────────
@Composable
fun DeliveryPinVerificationDialog(
    order: CommerceOrderEntity,
    onDismiss: () -> Unit,
    onVerify: (pin: String, rating: Int, review: String) -> Unit,
) {
    var enteredPin by remember { mutableStateOf("") }
    var ratingStars by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("Entrega rápida y paquete en perfecto estado.") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MeetColors.cardBackground,
            border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.VerifiedUser, null, tint = MeetColors.neonGreen, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(8.dp))
                Text("Verificación de Entrega", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                Text(
                    "Pide al cliente su código de 4 dígitos para certificar la entrega y liberar la tarifa.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { if (it.length <= 4) enteredPin = it },
                    label = { Text("Código PIN de 4 dígitos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MeetColors.neonGreen,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.neonGreen,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(Modifier.height(12.dp))
                Text("Calificación del servicio:", color = Color.LightGray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { ratingStars = star }) {
                            Icon(
                                if (star <= ratingStars) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (star <= ratingStars) Color(0xFFFFD700) else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = { onVerify(enteredPin, ratingStars, reviewText) },
                        enabled = enteredPin.length == 4,
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Validar PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
