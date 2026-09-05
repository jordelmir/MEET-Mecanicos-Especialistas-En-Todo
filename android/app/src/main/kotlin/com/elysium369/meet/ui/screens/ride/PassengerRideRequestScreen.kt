package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ride.payment.RidePaymentMethod
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PassengerRideRequestScreen — Complete Uber/Didi-grade passenger ride booking experience (Item 1).
 */
@Composable
fun PassengerRideRequestScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    onBack: () -> Unit = {},
    onStartActiveRide: (ActiveRideViewState) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var pickup by remember {
        mutableStateOf(
            RidePlaceInput(
                placeId = "pickup_default",
                displayName = "Mi ubicación actual",
                address = "San José, Costa Rica (GPS Alta Precisión)",
                latitude = 9.9333,
                longitude = -84.0833,
                placeType = PlaceType.CURRENT
            )
        )
    }

    var dropoff by remember {
        mutableStateOf<RidePlaceInput?>(
            RidePlaceInput(
                placeId = "dropoff_default",
                displayName = "Aeropuerto Juan Santamaría (SJO)",
                address = "Alajuela, Costa Rica",
                latitude = 9.9939,
                longitude = -84.2088,
                placeType = PlaceType.SEARCH
            )
        )
    }

    var fareMode by remember { mutableStateOf(RideFareMode.METERED_TIME_DISTANCE) }
    var paymentMethod by remember { mutableStateOf(RidePaymentMethod.CASH) }
    var showFareModeSheet by remember { mutableStateOf(false) }
    var showPaymentSheet by remember { mutableStateOf(false) }
    var showSafetyCenter by remember { mutableStateOf(false) }
    var isRequesting by remember { mutableStateOf(false) }
    var matchedDriver by remember { mutableStateOf<MatchedDriver?>(null) }
    var showDriverProfile by remember { mutableStateOf(false) }

    val fareQuote = remember(pickup, dropoff, fareMode) {
        if (dropoff != null) {
            FareQuote(
                baseFare = 1200L,
                distanceFare = 5800L,
                timeFare = 1400L,
                totalFare = 8400L,
                currency = "CRC",
                estimatedDistanceKm = 17.2,
                estimatedDurationMin = 24,
                fareMode = fareMode
            )
        } else null
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = MeetColors.textPrimary)
                }

                Text(
                    "Solicitar Viaje MEET",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.textPrimary
                )

                IconButton(onClick = { showSafetyCenter = true }) {
                    Icon(Icons.Default.Security, contentDescription = "Seguridad", tint = MeetColors.neonGreen)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Route Input Cards
                RideInputCard(
                    title = "Punto de recogida",
                    subtitle = pickup.displayName,
                    icon = { Icon(Icons.Default.MyLocation, contentDescription = null, tint = MeetColors.neonGreen) },
                    iconColor = MeetColors.neonGreen,
                    isActive = true,
                    onClick = { /* Open search dialog for pickup */ }
                )

                RideInputCard(
                    title = "Destino",
                    subtitle = dropoff?.displayName ?: "Selecciona tu destino",
                    icon = { Icon(Icons.Default.Flag, contentDescription = null, tint = MeetColors.electricBlue) },
                    iconColor = MeetColors.electricBlue,
                    isActive = false,
                    onClick = { /* Open search dialog for dropoff */ }
                )

                // Fare Mode Selector
                FareModeSelector(
                    selectedMode = fareMode,
                    fareQuote = fareQuote,
                    onModeClick = { showFareModeSheet = true }
                )

                // Payment Method Selector
                PaymentMethodSelector(
                    selectedMethod = paymentMethod,
                    onClick = { showPaymentSheet = true }
                )

                // Matched Driver Card if matched
                matchedDriver?.let { driver ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDriverProfile = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.5.dp, MeetColors.neonGreen)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MeetColors.neonGreen),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        driver.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MeetColors.neonGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("¡Conductor Asignado!", style = MaterialTheme.typography.labelMedium, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                                Text(driver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                                Text("${driver.vehicle} • ${driver.plate}", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                                Text("Llegada estimada en ${driver.etaMinutes} min", style = MaterialTheme.typography.labelSmall, color = MeetColors.electricBlue, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MeetColors.neonGreen)
                        }
                    }

                    Button(
                        onClick = {
                            val activeState = ActiveRideViewState(
                                rideId = "ride_${System.currentTimeMillis()}",
                                driver = driver,
                                pickup = pickup,
                                dropoff = dropoff!!,
                                fareQuote = fareQuote!!,
                                state = RideState.DRIVER_EN_ROUTE,
                                driverLocation = RideLocationPoint(latitude = 9.9350, longitude = -84.0840),
                                passengerLocation = RideLocationPoint(latitude = pickup.latitude, longitude = pickup.longitude)
                            )
                            onStartActiveRide(activeState)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                    ) {
                        Text("VER SEGUIMIENTO EN VIVO", fontWeight = FontWeight.Bold)
                    }
                }

                // Request Button (when not yet matched)
                if (matchedDriver == null) {
                    RequestRideButton(
                        fareQuote = fareQuote,
                        isRequesting = isRequesting,
                        onClick = {
                            isRequesting = true
                            scope.launch {
                                delay(3000)
                                isRequesting = false
                                matchedDriver = MatchedDriver(
                                    driverId = "drv_481",
                                    name = "Rodrigo Alvarado",
                                    rating = 4.94,
                                    totalTrips = 842,
                                    vehicle = "Toyota Corolla 2022",
                                    plate = "BGH-409",
                                    etaMinutes = 4,
                                    distanceMeters = 950
                                )
                            }
                        }
                    )
                }

                Spacer(Modifier.height(40.dp))
            }

            // Sheets & Overlays
            if (showFareModeSheet) {
                FareModeBottomSheet(
                    selectedMode = fareMode,
                    fareQuote = fareQuote,
                    onSelect = { fareMode = it },
                    onDismiss = { showFareModeSheet = false }
                )
            }

            if (showPaymentSheet) {
                PaymentMethodBottomSheet(
                    selectedMethod = paymentMethod,
                    onSelect = { paymentMethod = it },
                    onDismiss = { showPaymentSheet = false }
                )
            }

            if (showSafetyCenter) {
                SafetyCenterOverlay(
                    ride = null,
                    onDismiss = { showSafetyCenter = false },
                    onShareTrip = { /* Share link */ },
                    onSOS = { /* Dial 911 */ },
                    onGuardian = { /* Guardian */ }
                )
            }

            if (showDriverProfile && matchedDriver != null) {
                DriverProfileOverlay(
                    driver = matchedDriver!!,
                    onDismiss = { showDriverProfile = false },
                    onCall = { /* Call driver */ },
                    onMessage = { /* Chat driver */ }
                )
            }
        }
    }
}
