package com.elysium369.meet.ui.screens.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ride.schedule.ScheduledRide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideScheduleScreen(
    navController: NavController,
    userId: String,
    viewModel: RideScheduleViewModel = hiltViewModel(),
) {
    val upcoming by viewModel.upcomingRides.collectAsState()
    val favorites by viewModel.favoriteRoutes.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    var showNewRideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadUpcoming(userId)
        viewModel.loadFavorites()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viajes Programados") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showNewRideDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Programar viaje")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDark,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Feedback snackbar
            feedback?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(bottom = 8.dp),
                    action = {
                        TextButton(onClick = { viewModel.dismissFeedback() }) {
                            Text("OK")
                        }
                    }
                ) { Text(msg) }
            }

            // Upcoming rides section
            Text(
                "Próximos viajes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            if (upcoming.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No hay viajes programados",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { showNewRideDialog = true }) {
                            Text("Programar uno")
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(upcoming, key = { it.scheduleId }) { ride ->
                        ScheduledRideCard(
                            ride = ride,
                            onCancel = { viewModel.cancelRide(ride.scheduleId, userId) },
                            onTap = { viewModel.loadReminders(ride.scheduleId) },
                        )
                    }
                }
            }

            // Favorites section
            if (favorites.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Rutas favoritas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(favorites, key = { it.routeId }) { fav ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = MeetColors.neonGreen,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fav.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${fav.stops.size} paradas · ${fav.usageCount} usos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New ride dialog
    if (showNewRideDialog) {
        NewRideDialog(
            onDismiss = { showNewRideDialog = false },
            onSchedule = { pickup, dropoff, scheduledAt, fare ->
                viewModel.scheduleRide(
                    userId = userId,
                    pickup = pickup,
                    dropoff = dropoff,
                    scheduledAtEpochMs = scheduledAt,
                    estimatedFare = fare,
                )
                showNewRideDialog = false
            },
        )
    }
}

@Composable
fun ScheduledRideCard(
    ride: ScheduledRide,
    onCancel: () -> Unit,
    onTap: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ride.pickup?.displayName ?: "Origen",
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "→ ${ride.dropoff?.displayName ?: "Destino"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    ride.formattedFare,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                )
                if (ride.isRecurring) {
                    Text(
                        "Recurrente: ${ride.recurrence.pattern.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Cancelar",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun NewRideDialog(
    onDismiss: () -> Unit,
    onSchedule: (pickup: com.elysium369.meet.ride.schedule.RideStop, dropoff: com.elysium369.meet.ride.schedule.RideStop, scheduledAt: Long, fare: Long) -> Unit,
) {
    var pickupText by remember { mutableStateOf("") }
    var dropoffText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Programar viaje") },
        text = {
            Column {
                OutlinedTextField(
                    value = pickupText,
                    onValueChange = { pickupText = it },
                    label = { Text("Origen") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dropoffText,
                    onValueChange = { dropoffText = it },
                    label = { Text("Destino") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pickupText.isNotBlank() && dropoffText.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        val tomorrow = now + 24 * 60 * 60 * 1000L
                        onSchedule(
                            com.elysium369.meet.ride.schedule.RideStop(
                                stopId = "s-pickup",
                                displayName = pickupText,
                                address = pickupText,
                                latitude = 9.93,
                                longitude = -84.08,
                                order = 0,
                                isPickup = true,
                            ),
                            com.elysium369.meet.ride.schedule.RideStop(
                                stopId = "s-dropoff",
                                displayName = dropoffText,
                                address = dropoffText,
                                latitude = 10.0,
                                longitude = -84.21,
                                order = 1,
                                isDropoff = true,
                            ),
                            tomorrow,
                            0L,
                        )
                    }
                },
            ) { Text("Programar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
