package com.elysium369.meet.ui.screens.ride

import android.content.Context
import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.identity.OnboardingUsageProfile
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.payment.RidePaymentMethod
import com.elysium369.meet.ride.map.RidePlaceSearchProvider
import com.elysium369.meet.ride.map.RideSavedPlace
import com.elysium369.meet.ride.map.RideRouting
import com.elysium369.meet.ride.map.RidePlaceSearchProvider
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * PassengerRideRequestScreen — Uber/Didi/inDriver style ride request flow.
 *
 * Flow: Set Pickup → Set Dropoff → Choose Fare Mode → Confirm → Match Driver → Track → Complete → Rate
 *
 * Laws:
 * - Always show GPS accuracy
 * - Fare preview before confirm
 * - Driver details immediately on match
 * - Real-time tracking with smooth animation
 * - Safety center accessible at all times
 */
@Composable
fun PassengerRideRequestScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Request state
    var pickup by remember { mutableStateOf<RidePlaceInput?>(null) }
    var dropoff by remember { mutableStateOf<RidePlaceInput?>(null) }
    var stops by remember { mutableStateOf<List<RidePlaceInput>>(emptyList()) }
    var fareMode by remember { mutableStateOf<RideFareMode>(RideFareMode.METERED) }
    var paymentMethod by remember { mutableStateOf<RidePaymentMethod>(RidePaymentMethod.CASH) }
    var showFareModeSheet by remember { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showStopsSheet by remember { mutableStateOf(false) }
    var isRequesting by remember { mutableStateOf(false) }
    var matchedDriver by remember { mutableStateOf<MatchedDriver?>(null) }
    var activeRide by remember { mutableStateOf<ActiveRideViewState?>(null) }
    var showSafetyCenter by remember { mutableStateOf(false) }
    var showDriverProfile by remember { mutableStateOf(false) }

    // GPS state
    val currentLocation by viewModel.currentGpsLocation.collectAsState()
    val activeVehicle by viewModel.selectedVehicle.collectAsState()

    // Fare quote
    val fareQuote by remember { mutableStateOf<FareQuote?>(null) }

    // Fetch fare quote when both pickup and dropoff are set
    LaunchedEffect(listOf(pickup, dropoff, fareMode, stops)) {
        if (pickup != null && dropoff != null) {
            // In real implementation, call viewModel.quoteFare()
            // For now, show estimated fare
            fareQuote = FareQuote(
                baseFare = 1500L,
                distanceFare = 3500L,
                timeFare = 1200L,
                totalFare = 6200L,
                currency = "CRC",
                estimatedDistanceKm = 8.5,
                estimatedDurationMin = 18,
                fareMode = fareMode,
            )
        } else {
            fareQuote = null
        }
    }

    // When driver matched, transition to tracking
    LaunchedEffect(matchedDriver) {
        if (matchedDriver != null) {
            activeRide = ActiveRideViewState(
                rideId = "ride_${System.currentTimeMillis()}",
                driver = matchedDriver!!,
                pickup = pickup!!,
                dropoff = dropoff!!,
                fareQuote = fareQuote!!,
                state = RideState.DRIVER_EN_ROUTE,
                driverLocation = null, // Will be updated via realtime
                passengerLocation = currentLocation,
                startedAt = System.currentTimeMillis(),
            )
        }
    }

    val compactHeader = LocalConfiguration.current.screenWidthDp < 600

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            // Safety center button always visible
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .statusBarsPadding(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    shape = CircleShape,
                    color = MeetColors.cardBackground,
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { showSafetyCenter = true }
                ) {
                    EliteButtonIcon(
                        icon = { ElysiumSectionIcon(key = "safety", fallbackGlyph = "🛡️", tint = MeetColors.neonGreen) },
                        onClick = { showSafetyCenter = true },
                        contentDescription = "Centro de Seguridad",
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeRide != null && activeRide!!.state.isActive) {
                // Quick action: Call driver
                AnimatedVisibility(visible = activeRide != null) {
                    FloatingActionButton(
                        onClick = { /* Call driver */ },
                        containerColor = MeetColors.electricBlue,
                        modifier = Modifier.padding(bottom = 100.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Llamar conductor")
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
                // ===== PICKUP / DROPOFF INPUT =====
                RideInputCard(
                    title = "¿De dónde salimos?",
                    subtitle = pickup?.displayName ?: "Ubicación actual",
                    icon = { Icons.Default.MyLocation },
                    iconColor = MeetColors.neonGreen,
                    isActive = pickup == null && dropoff == null,
                    onClick = { /* Open place picker for pickup */ },
                    showCurrentLocation = true,
                    currentLocation = currentLocation,
                )

                // Connecting line
                if (pickup != null || dropoff != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.verticalGradient(
                                        0f to MeetColors.neonGreen,
                                        1f to MeetColors.electricBlue
                                    )
                                )
                        )
                    }
                }

                RideInputCard(
                    title = "¿A dónde vamos?",
                    subtitle = dropoff?.displayName ?: "Seleccionar destino",
                    icon = { Icons.Default.Search },
                    iconColor = MeetColors.electricBlue,
                    isActive = pickup != null && dropoff == null,
                    onClick = { /* Open place picker for dropoff */ },
                )

                // Stops (if any)
                if (stops.isNotEmpty()) {
                    for ((index, stop) in stops.withIndex()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(40.dp)
                                    .background(MeetColors.electricBlue.copy(alpha = 0.5f))
                            )
                        }
                        RideInputCard(
                            title = "Parada ${index + 1}",
                            subtitle = stop.displayName,
                            icon = { Icons.Default.Add },
                            iconColor = MeetColors.electricBlue.copy(alpha = 0.7f),
                            onClick = { /* Edit stop */ },
                        )
                    }
                }

                // Add stop button
                if (stops.size < 3) {
                    OutlinedButton(
                        onClick = { showStopsSheet = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MeetColors.electricBlue,
                            containerColor = MeetColors.backgroundDeep
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MeetColors.electricBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("Añadir parada", style = MaterialTheme.typography.labelLarge, color = MeetColors.electricBlue)
                        }
                    }
                }

                // ===== FARE MODE SELECTOR =====
                if (pickup != null && dropoff != null) {
                    FareModeSelector(
                        selectedMode = fareMode,
                        fareQuote = fareQuote,
                        onModeClick = { showFareModeSheet = true },
                    )

                    // ===== PAYMENT METHOD =====
                    PaymentMethodSelector(
                        selectedMethod = paymentMethod,
                        onClick = { showPaymentSheet = true },
                    )

                    // ===== REQUEST BUTTON =====
                    RequestRideButton(
                        fareQuote = fareQuote!!,
                        isRequesting = isRequesting,
                        onClick = {
                            isRequesting = true
                            // Simulate driver matching
                            kotlinx.coroutines.delay(2000)
                            matchedDriver = MatchedDriver(
                                driverId = "driver_001",
                                name = "Carlos R.",
                                rating = 4.9,
                                totalTrips = 1247,
                                vehicle = "Toyota Corolla 2022",
                                plate = "ABC-123",
                                photoUrl = null,
                                etaMinutes = 3,
                                distanceMeters = 800,
                            )
                            isRequesting = false
                        },
                    )
                } else {
                    // Need both locations
                    InfoCard(
                        icon = { Icons.Default.Info },
                        title = "Ingresa tu destino",
                        message = "Selecciona origen y destino para ver la tarifa estimada",
                        color = MeetColors.textSecondary
                    )
                }

                // Spacer for bottom sheet
                Spacer(Modifier.height(100.dp))
            }
        }

        // ===== BOTTOM SHEETS =====
        AnimatedVisibility(visible = showFareModeSheet) {
            FareModeBottomSheet(
                selectedMode = fareMode,
                fareQuote = fareQuote,
                onSelect = { mode ->
                    fareMode = mode
                    showFareModeSheet = false
                },
                onDismiss = { showFareModeSheet = false }
            )
        }

        AnimatedVisibility(visible = showPaymentSheet) {
            PaymentMethodBottomSheet(
                selectedMethod = paymentMethod,
                onSelect = { method ->
                    paymentMethod = method
                    showPaymentSheet = false
                },
                onDismiss = { showPaymentSheet = false }
            )
        }

        AnimatedVisibility(visible = showStopsSheet) {
            StopsBottomSheet(
                stops = stops,
                onStopsChanged = { newStops ->
                    stops = newStops
                    showStopsSheet = false
                },
                onDismiss = { showStopsSheet = false }
            )
        }

        // ===== DRIVER MATCHED OVERLAY =====
        if (matchedDriver != null && activeRide == null) {
            DriverMatchedOverlay(
                driver = matchedDriver!!,
                onCancel = { matchedDriver = null },
                onViewProfile = { showDriverProfile = true }
            )
        }

        // ===== ACTIVE RIDE TRACKING =====
        if (activeRide != null) {
            ActiveRideTrackingScreen(
                ride = activeRide!!,
                onCancel = { /* Cancel ride */ },
                onSafety = { showSafetyCenter = true },
                onCallDriver = { /* Call driver */ },
                onMessageDriver = { /* Chat with driver */ },
            )
        }

        // ===== SAFETY CENTER =====
        AnimatedVisibility(visible = showSafetyCenter) {
            SafetyCenterOverlay(
                ride = activeRide,
                onDismiss = { showSafetyCenter = false },
                onShareTrip = { /* Share trip link */ },
                onSOS = { /* Emergency SOS */ },
                onGuardian = { /* Guardian signals */ },
            )
        }

        // ===== DRIVER PROFILE =====
        AnimatedVisibility(visible = showDriverProfile) {
            DriverProfileOverlay(
                driver = matchedDriver!!,
                onDismiss = { showDriverProfile = false },
                onCall = { /* Call driver */ },
                onMessage = { /* Message driver */ },
            )
        }
    }
}

// ===== DATA MODELS =====

data class RidePlaceInput(
    val placeId: String,
    val displayName: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val placeType: PlaceType = PlaceType.SEARCH,
) {
    companion object {
        fun fromCurrentLocation(lat: Double, lng: Double): RidePlaceInput {
            return RidePlaceInput(
                placeId = "current_${System.currentTimeMillis()}",
                displayName = "Mi ubicación actual",
                address = "Lat: ${lat}, Lng: ${lng}",
                latitude = lat,
                longitude = lng,
                placeType = PlaceType.CURRENT,
            )
        }
    }
}

enum class PlaceType { CURRENT, SEARCH, SAVED, HOME, WORK }

data class FareQuote(
    val baseFare: Long,
    val distanceFare: Long,
    val timeFare: Long,
    val totalFare: Long,
    val currency: String,
    val estimatedDistanceKm: Double,
    val estimatedDurationMin: Int,
    val fareMode: RideFareMode,
) {
    val formattedTotal: String
        get() = "₡${totalFare.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "\$1,")}"

    val formattedDistance: String
        get() = "%.1f km".format(estimatedDistanceKm)

    val formattedDuration: String
        get() = "${estimatedDurationMin} min"
}

data class MatchedDriver(
    val driverId: String,
    val name: String,
    val rating: Double,
    val totalTrips: Int,
    val vehicle: String,
    val plate: String,
    val photoUrl: String?,
    val etaMinutes: Int,
    val distanceMeters: Int,
)

data class ActiveRideViewState(
    val rideId: String,
    val driver: MatchedDriver,
    val pickup: RidePlaceInput,
    val dropoff: RidePlaceInput,
    val fareQuote: FareQuote,
    val state: RideState,
    val driverLocation: RideLocationPoint?,
    val passengerLocation: RideLocationPoint?,
    val startedAt: Long,
)

data class RideLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val speed: Float? = null,
    val heading: Float? = null,
)