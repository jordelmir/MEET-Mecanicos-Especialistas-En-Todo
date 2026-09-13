package com.elysium369.meet.ui.screens.ride

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.data.remote.RideDispatchGateway
import com.elysium369.meet.ride.domain.RideDispatchExpiryPolicy
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideStopSnapshot
import com.elysium369.meet.ui.screens.calculateDistance
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private enum class RideCenterFilter(val label: String) {
    ALL("Todos"),
    OPEN_BID("Pon tu precio"),
    METERED("Tiempo+Distancia"),
    CASH("Efectivo"),
    SINPE("SINPE"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideCenterScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    onSelectRide: (RideRequestEntity) -> Unit,
) {
    val openRides by viewModel.openRideRequests.collectAsState()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val driverVer by viewModel.driverVerification.collectAsState()
    val myDriverId = viewModel.currentUserId ?: driverVer?.driverId
    val context = LocalContext.current
    val driverPrefs = remember(context, myDriverId) {
        context.getSharedPreferences(
            "elysium_ride_driver_ops_${myDriverId ?: "signed_out"}",
            Context.MODE_PRIVATE,
        )
    }
    val dispatchScope = rememberCoroutineScope()
    val voicePreferences = remember(context) {
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
    }

    var activeFilter by remember { mutableStateOf(RideCenterFilter.ALL) }
    var voiceEnabled by remember {
        mutableStateOf(voicePreferences.getBoolean("voice_feedback_enabled", true))
    }
    var hiddenRideIds by remember(myDriverId) {
        mutableStateOf(
            driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty()
                .mapNotNull { it.substringBefore('|').takeIf(String::isNotBlank) }
                .toSet(),
        )
    }
    var dispatchMessage by remember(myDriverId) { mutableStateOf<String?>(null) }
    var clockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun queueDriverDecisions(rides: List<RideRequestEntity>, action: String) {
        if (rides.isEmpty()) return
        val queuedEntries = rides.map { "${it.requestId}|$action|${UUID.randomUUID()}" }
        hiddenRideIds = hiddenRideIds + rides.map(RideRequestEntity::requestId)
        val persisted = driverPrefs.getStringSet("pending_driver_decisions", emptySet())
            .orEmpty()
            .toMutableSet()
            .apply { addAll(queuedEntries) }
        driverPrefs.edit { putStringSet("pending_driver_decisions", persisted) }
        dispatchMessage = if (action == "REJECT") {
            "Oferta rechazada aquí. El pasajero será avisado y su solicitud seguirá activa."
        } else {
            "Solicitud eliminada de tu Centro de viajes."
        }
        dispatchScope.launch {
            queuedEntries.forEach { encoded ->
                val parts = encoded.split('|')
                runCatching { RideDispatchGateway.decideRequest(parts[0], parts[1], parts[2]) }
                    .onSuccess {
                        val remaining = driverPrefs
                            .getStringSet("pending_driver_decisions", emptySet())
                            .orEmpty()
                            .toMutableSet()
                            .apply { remove(encoded) }
                        driverPrefs.edit { putStringSet("pending_driver_decisions", remaining) }
                    }
            }
        }
    }

    LaunchedEffect(myDriverId) {
        if (myDriverId == null) return@LaunchedEffect
        viewModel.startRideProjectionSync()
        while (true) {
            clockMillis = System.currentTimeMillis()
            runCatching { RideDispatchGateway.expireStaleRequests() }
            viewModel.refreshRideProjectionNow()
            runCatching { RideDispatchGateway.decisions() }
                .onSuccess { decisions -> hiddenRideIds = hiddenRideIds + decisions.map { it.tripId } }
            val pending = driverPrefs.getStringSet("pending_driver_decisions", emptySet()).orEmpty().toSet()
            pending.forEach { encoded ->
                val parts = encoded.split('|')
                if (parts.size == 3) {
                    runCatching { RideDispatchGateway.decideRequest(parts[0], parts[1], parts[2]) }
                        .onSuccess {
                            val remaining = driverPrefs
                                .getStringSet("pending_driver_decisions", emptySet())
                                .orEmpty()
                                .toMutableSet()
                                .apply { remove(encoded) }
                            driverPrefs.edit { putStringSet("pending_driver_decisions", remaining) }
                        }
                }
            }
            delay(15_000L)
        }
    }

    val eligibleRides = remember(openRides, myDriverId, hiddenRideIds, clockMillis) {
        openRides.filter {
            it.passengerId != myDriverId &&
                it.requestId !in hiddenRideIds &&
                RideDispatchExpiryPolicy.remainsVisible(it.createdAt, clockMillis)
        }
    }

    val filteredRides = remember(eligibleRides, activeFilter) {
        when (activeFilter) {
            RideCenterFilter.ALL -> eligibleRides
            RideCenterFilter.OPEN_BID -> eligibleRides.filter {
                it.fareMode == RideFareMode.OPEN_BID.name
            }
            RideCenterFilter.METERED -> eligibleRides.filter {
                it.fareMode == RideFareMode.METERED_TIME_DISTANCE.name
            }
            RideCenterFilter.CASH -> eligibleRides.filter {
                it.paymentMethod == "CASH"
            }
            RideCenterFilter.SINPE -> eligibleRides.filter {
                it.paymentMethod == "SINPE_MOVIL" || it.paymentMethod == "SINPE"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Centro de viajes",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        voiceEnabled = !voiceEnabled
                        viewModel.voiceFeedbackManager.setEnabled(voiceEnabled)
                        if (voiceEnabled) {
                            viewModel.voiceFeedbackManager.speak(es = "Guía de voz activada.")
                        }
                    }) {
                        Icon(
                            if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            if (voiceEnabled) "Silenciar toda la guía de voz" else "Activar guía de voz",
                            tint = if (voiceEnabled) MeetColors.neonGreen else MeetColors.textMuted,
                        )
                    }
                    IconButton(onClick = { viewModel.refreshRideProjectionNow() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sincronizar solicitudes con Supabase",
                            tint = MeetColors.cyberCyan,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDark,
                ),
            )
        },
        containerColor = MeetColors.backgroundDark,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Subtitle + Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Elige un viaje",
                    color = MeetColors.textSecondary,
                    fontSize = 14.sp,
                )
                Text(
                    "${filteredRides.size} disponible(s)",
                    color = MeetColors.cyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (eligibleRides.isNotEmpty()) {
                OutlinedButton(
                    onClick = { queueDriverDecisions(eligibleRides, "DISMISS") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    border = BorderStroke(1.dp, MeetColors.textMuted),
                ) {
                    Icon(Icons.Default.ClearAll, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("LIMPIAR TODAS DE MI CENTRO")
                }
            }

            dispatchMessage?.let { message ->
                Text(
                    message,
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // ── Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RideCenterFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = {
                            Text(
                                filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (activeFilter == filter) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeetColors.cyberCyan.copy(alpha = 0.2f),
                            selectedLabelColor = MeetColors.cyberCyan,
                            containerColor = MeetColors.cardBackground,
                            labelColor = MeetColors.textSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MeetColors.borderSubtle,
                            selectedBorderColor = MeetColors.cyberCyan,
                            enabled = true,
                            selected = activeFilter == filter,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Ride list
            if (filteredRides.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MeetColors.textMuted,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No hay viajes disponibles",
                            color = MeetColors.textMuted,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Las solicitudes de pasajeros aparecerán aquí",
                            color = MeetColors.textMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(filteredRides, key = { it.requestId }) { ride ->
                        RideCenterCard(
                            ride = ride,
                            currentGps = currentGps,
                            onSelect = { onSelectRide(ride) },
                            onDismiss = { queueDriverDecisions(listOf(ride), "DISMISS") },
                            onReject = { queueDriverDecisions(listOf(ride), "REJECT") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RideCenterCard(
    ride: RideRequestEntity,
    currentGps: ObdViewModel.GpsLocationInfo?,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
    onReject: () -> Unit,
) {
    val orderedStops = remember(ride.stopsJson) {
        runCatching { Json.decodeFromString<List<RideStopSnapshot>>(ride.stopsJson) }
            .getOrDefault(emptyList())
            .sortedBy(RideStopSnapshot::order)
    }

    val isOpenBid = ride.fareMode == RideFareMode.OPEN_BID.name
    val fareModeLabel = if (isOpenBid) "Poné Tu Precio" else "Tiempo + Distancia"
    val fareModeBadgeColor = if (isOpenBid) Color(0xFFFF8C00) else MeetColors.cyberCyan

    val distanceToPickup = remember(currentGps, ride) {
        currentGps?.let {
            calculateDistance(it.latitude, it.longitude, ride.pickupLatitude, ride.pickupLongitude)
        }
    }

    val elapsedMs = System.currentTimeMillis() - ride.createdAt
    val elapsedMins = (elapsedMs / (1_000 * 60)).toInt()

    val paymentLabel = when (ride.paymentMethod) {
        "CASH" -> "Efectivo"
        "SINPE_MOVIL", "SINPE" -> "SINPE"
        else -> "Por definir"
    }
    val paymentIcon = when (ride.paymentMethod) {
        "CASH" -> "\uD83D\uDCB5"
        "SINPE_MOVIL", "SINPE" -> "\uD83D\uDCF1"
        else -> "\u2753"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // ── Row 1: Badge + Payment badge + permanent owner-scoped dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Fare mode badge
                Surface(
                    color = fareModeBadgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = fareModeLabel,
                        color = fareModeBadgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MeetColors.cardBackground,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "$paymentIcon $paymentLabel",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Quitar solicitud de mi Centro de viajes",
                            tint = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Row 2: Price (large, centered)
            Text(
                text = "${ride.currency} ${ride.priceOffer.toInt()}",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Row 3: Rating + trips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Pasajero verificado",
                    color = MeetColors.textSecondary,
                    fontSize = 12.sp,
                )
                if (elapsedMins > 0) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Hace $elapsedMins min",
                        color = MeetColors.textMuted,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Row 4: Route info
            // Pickup
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MeetColors.neonGreen),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ride.pickupAddress,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (distanceToPickup != null) {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", distanceToPickup)} km · ~${(distanceToPickup / 30 * 60).toInt()} min",
                            color = MeetColors.neonGreen,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Stops
            orderedStops.forEach { stop ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MeetColors.warning),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stop.label,
                        color = MeetColors.warning,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Destination
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ride.destAddress,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ride.estimatedDistanceKm > 0) {
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", ride.estimatedDistanceKm)} km · ~${ride.estimatedDurationMin} min",
                            color = Color(0xFFFF1744),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Row 5: Quick price chips (for OPEN_BID)
            if (isOpenBid && ride.priceOffer > 0) {
                val base = ride.priceOffer.toInt()
                val quickPrices = listOf(
                    base,
                    (base * 1.05).toInt(),
                    (base * 1.10).toInt(),
                    (base * 1.15).toInt(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickPrices.forEachIndexed { index, price ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelect() },
                            color = if (index == 0) Color(0xFFFF8C00).copy(alpha = 0.15f) else MeetColors.cardBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (index == 0) Color(0xFFFF8C00) else MeetColors.borderSubtle,
                            ),
                        ) {
                            Text(
                                text = "${ride.currency} $price",
                                color = if (index == 0) Color(0xFFFF8C00) else MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Row 6: CTA Button
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOpenBid) Color(0xFFFF8C00) else MeetColors.cyberCyan,
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = if (isOpenBid) {
                        "Aceptar por ${ride.currency} ${ride.priceOffer.toInt()}"
                    } else {
                        "Enviar oferta"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFFFF5252)),
            ) {
                Text(
                    "RECHAZAR OFERTA · AVISAR AL PASAJERO",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                )
            }

            // ── Stale indicator
            if (elapsedMins > 5) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Solicitud hace $elapsedMins min — el pasajero podría haber encontrado otro conductor",
                    color = MeetColors.warning.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
