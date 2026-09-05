package com.elysium369.meet.ui.screens.ride

import android.content.Context
import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ride.domain.RideDriverAvailability
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

/**
 * DriverAppScreen — Uber/Didi/inDriver style driver experience.
 *
 * Flow: Go Online → Receive Requests → Accept → Navigate to Pickup → Pickup Passenger → Complete → Earnings
 *
 * Laws:
 * - One-tap go online/offline
 * - Request cards with all info visible
 * - Auto-accept option (configurable)
 * - Turn-by-turn navigation integrated
 * - Earnings visible in real-time
 * - Safety center accessible
 */
@Composable
fun DriverAppScreen(
    navController: androidx.navigation.NavController,
    viewModel: ObdViewModel,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Driver state
    val driverAvailability by viewModel.driverAvailability.collectAsState()
    var isOnline by remember { mutableStateOf(driverAvailability == RideDriverAvailability.AVAILABLE) }
    var activeRideRequest by remember { mutableStateOf<IncomingRideRequest?>(null) }
    var activeRide by remember { mutableStateOf<ActiveDriverRide?>(null) }
    var showEarnings by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSafetyCenter by remember { mutableStateOf(false) }
    var showNavigation by remember { mutableStateOf(false) }

    // Simulated incoming requests (in real app, from Supabase Realtime)
    val incomingRequests = remember {
        mutableStateListOf<IncomingRideRequest>()
    }

    // Toggle online/offline
    val toggleOnline = {
        isOnline = !isOnline
        // In real app: viewModel.setDriverAvailability(isOnline)
        // For demo, simulate requests when online
        if (isOnline) {
            simulateIncomingRequests(incomingRequests)
        } else {
            incomingRequests.clear()
        }
    }

    // Accept ride
    val acceptRide = { request: IncomingRideRequest ->
        activeRideRequest = null
        incomingRequests.remove(request)
        activeRide = ActiveDriverRide(
            rideId = request.rideId,
            passenger = request.passenger,
            pickup = request.pickup,
            dropoff = request.dropoff,
            fare = request.fare,
            state = RideState.ASSIGNED,
            startedAt = System.currentTimeMillis(),
        )
    }

    // Decline ride
    val declineRide = { request: IncomingRideRequest ->
        incomingRequests.remove(request)
        // In real app: viewModel.declineRide(request.rideId)
    }

    // Complete ride
    val completeRide = {
        activeRide = null
        // In real app: viewModel.completeRide()
        showEarnings = true
    }

    val compactHeader = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            // Online/Offline toggle + Safety
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Online/Offline toggle
                    val statusText = if (isOnline) "EN LÍNEA" : "FUERA DE LÍNEA"
                    val statusColor = if (isOnline) MeetColors.neonGreen else MeetColors.textSecondary

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleOnline() }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(statusText, style = MaterialTheme.typography.labelLarge, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ===== EARNINGS SUMMARY =====
                if (!isOnline) {
                    EarningsSummaryCard(
                        todayEarnings = 45600L,
                        weekEarnings = 187500L,
                        monthEarnings = 723400L,
                        tripsToday = 8,
                        onClick = { showEarnings = true },
                    )
                }

                // ===== QUICK ACTIONS (when offline) =====
                if (!isOnline) {
                    QuickActionsGrid(
                        onGoOnline = toggleOnline,
                        onEarnings = { showEarnings = true },
                        onNavigation = { showNavigation = true },
                        onSettings = { showSettings = true },
                    )
                }

                // ===== INCOMING REQUEST =====
                if (activeRideRequest != null) {
                    IncomingRideRequestCard(
                        request = activeRideRequest!!,
                        onAccept = acceptRide,
                        onDecline = declineRide,
                    )
                }

                // ===== ACTIVE RIDE =====
                if (activeRide != null) {
                    ActiveDriverRideCard(
                        ride = activeRide!!,
                        onNavigate = { showNavigation = true },
                        onCall = { /* Call passenger */ },
                        onMessage = { /* Message passenger */ },
                        onArrived = { /* Mark arrived */ },
                        onPickup = { /* Mark passenger onboard */ },
                        onComplete = completeRide,
                        onCancel = { activeRide = null },
                        onSafety = { showSafetyCenter = true },
                    )
                }

                // ===== PENDING REQUESTS LIST =====
                if (isOnline && activeRide == null && incomingRequests.isNotEmpty()) {
                    Text("Solicitudes cercanas", style = MaterialTheme.typography.titleMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        incomingRequests.forEach { request ->
                            if (request != activeRideRequest) {
                                PendingRequestCard(
                                    request = request,
                                    onAccept = acceptRide,
                                    onDecline = declineRide,
                                )
                            }
                        }
                    }
                }

                // ===== EARNINGS TODAY (when online) =====
                if (isOnline && activeRide == null) {
                    LiveEarningsCard(
                        todayEarnings = 45600L,
                        tripsToday = 8,
                        onlineTimeMinutes = 245,
                        onClick = { showEarnings = true },
                    )
                }

                // Spacer for bottom nav
                Spacer(Modifier.height(100.dp))
            }
        }

        // ===== BOTTOM SHEETS =====
        AnimatedVisibility(visible = showEarnings) {
            DriverEarningsBottomSheet(
                onDismiss = { showEarnings = false },
            )
        }

        AnimatedVisibility(visible = showSettings) {
            DriverSettingsBottomSheet(
                isOnline = isOnline,
                onToggleOnline = toggleOnline,
                onDismiss = { showSettings = false },
            )
        }

        AnimatedVisibility(visible = showSafetyCenter) {
            SafetyCenterOverlay(
                ride = activeRide?.let { ActiveRideViewState(
                    rideId = it.rideId,
                    driver = MatchedDriver(
                        driverId = "current_driver",
                        name = "Tú",
                        rating = 4.9,
                        totalTrips = 1247,
                        vehicle = it.pickup.vehicleName ?: "Tu vehículo",
                        plate = "ABC-123",
                        photoUrl = null,
                        etaMinutes = 0,
                        distanceMeters = 0,
                    ),
                    pickup = it.pickup,
                    dropoff = it.dropoff,
                    fareQuote = FareQuote(
                        baseFare = 1500L, distanceFare = 3500L, timeFare = 1200L, totalFare = it.fare,
                        currency = "CRC", estimatedDistanceKm = 8.5, estimatedDurationMin = 18, fareMode = com.elysium369.meet.ride.domain.RideFareMode.METERED
                    ),
                    state = it.state,
                    driverLocation = null,
                    passengerLocation = null,
                    startedAt = it.startedAt,
                ) },
                onDismiss = { showSafetyCenter = false },
                onShareTrip = { /* Share trip */ },
                onSOS = { /* SOS */ },
                onGuardian = { /* Guardian */ },
            )
        }
    }
}

// ===== DATA MODELS =====

data class IncomingRideRequest(
    val rideId: String,
    val passenger: PassengerInfo,
    val pickup: RidePlaceInput,
    val dropoff: RidePlaceInput,
    val fare: Long,
    val distanceKm: Double,
    val durationMin: Int,
    val fareMode: com.elysium369.meet.ride.domain.RideFareMode,
    val paymentMethod: com.elysium369.meet.ride.payment.RidePaymentMethod,
    val requestTime: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 30_000, // 30 seconds to accept
)

data class PassengerInfo(
    val passengerId: String,
    val name: String,
    val rating: Double,
    val totalTrips: Int,
    val photoUrl: String?,
)

data class ActiveDriverRide(
    val rideId: String,
    val passenger: PassengerInfo,
    val pickup: RidePlaceInput,
    val dropoff: RidePlaceInput,
    val fare: Long,
    val state: RideState,
    val startedAt: Long,
    var passengerLocation: RideLocationPoint? = null,
)

data class RideLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val speed: Float? = null,
    val heading: Float? = null,
)

// ===== SIMULATION =====

fun simulateIncomingRequests(requests: MutableList<IncomingRideRequest>) {
    // Simulate requests coming in
    kotlinx.coroutines.delay(3000)
    requests.add(IncomingRideRequest(
        rideId = "ride_${System.currentTimeMillis()}",
        passenger = PassengerInfo("pax_001", "María G.", 4.8, 45, null),
        pickup = RidePlaceInput("pickup_1", "Centro Comercial Plaza", "Av. Central 123", 9.9347, -84.0875),
        dropoff = RidePlaceInput("drop_1", "Hotel Presidente", "Paseo Colón", 9.9302, -84.0821),
        fare = 6200L,
        distanceKm = 4.2,
        durationMin = 12,
        fareMode = com.elysium369.meet.ride.domain.RideFareMode.METERED,
        paymentMethod = com.elysium369.meet.ride.payment.RidePaymentMethod.CASH,
    ))

    kotlinx.coroutines.delay(15000)
    requests.add(IncomingRideRequest(
        rideId = "ride_${System.currentTimeMillis()}",
        passenger = PassengerInfo("pax_002", "Carlos M.", 4.9, 112, null),
        pickup = RidePlaceInput("pickup_2", "Aeropuerto Juan Santamaría", "Alajuela", 9.9939, -84.2088),
        dropoff = RidePlaceInput("drop_2", "Zona Franca", "San José", 9.9350, -84.0850),
        fare = 18500L,
        distanceKm = 18.5,
        durationMin = 28,
        fareMode = com.elysium369.meet.ride.domain.RideFareMode.METERED,
        paymentMethod = com.elysium369.meet.ride.payment.RidePaymentMethod.SINPE_MOVIL,
    ))
}