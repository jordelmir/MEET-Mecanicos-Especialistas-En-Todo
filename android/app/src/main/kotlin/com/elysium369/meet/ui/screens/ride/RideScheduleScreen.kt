package com.elysium369.meet.ui.screens.ride

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ride.schedule.ScheduledRide
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.ride.map.RidePlaceSuggestion
import com.elysium369.meet.ride.map.resilientRidePlaceSearchProvider
import com.elysium369.meet.ride.schedule.RecurrenceConfig
import com.elysium369.meet.ride.schedule.RecurrencePattern
import com.elysium369.meet.ride.schedule.RideStop
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

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
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
            onSchedule = { pickup, dropoff, scheduledAt, recurrence, notes ->
                viewModel.scheduleRide(
                    userId = userId,
                    pickup = pickup,
                    dropoff = dropoff,
                    scheduledAtEpochMs = scheduledAt,
                    recurrence = recurrence,
                    notes = notes,
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
                    SimpleDateFormat("EEE d MMM · h:mm a", Locale("es", "CR")).format(Date(ride.scheduledAtEpochMs)),
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
    onSchedule: (pickup: RideStop, dropoff: RideStop, scheduledAt: Long, recurrence: RecurrenceConfig, notes: String) -> Unit,
) {
    val context = LocalContext.current
    val searchProvider = remember {
        resilientRidePlaceSearchProvider(BuildConfig.RIDE_GEOCODER_URL, BuildConfig.RIDE_GEOCODER_FALLBACK_URL)
    }
    var pickupText by remember { mutableStateOf("") }
    var destinationText by remember { mutableStateOf("") }
    var pickup by remember { mutableStateOf<RidePlaceSuggestion?>(null) }
    var destination by remember { mutableStateOf<RidePlaceSuggestion?>(null) }
    var pickupSuggestions by remember { mutableStateOf(emptyList<RidePlaceSuggestion>()) }
    var destinationSuggestions by remember { mutableStateOf(emptyList<RidePlaceSuggestion>()) }
    var recurrence by remember { mutableStateOf(RecurrencePattern.NONE) }
    var notes by remember { mutableStateOf("") }
    val calendar = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) } }
    var scheduledAt by remember { mutableLongStateOf(calendar.timeInMillis) }
    val formatter = remember { SimpleDateFormat("EEEE d 'de' MMMM · h:mm a", Locale("es", "CR")) }

    LaunchedEffect(pickupText, pickup) {
        if (pickup != null || pickupText.trim().length < 3) { pickupSuggestions = emptyList(); return@LaunchedEffect }
        delay(350)
        pickupSuggestions = runCatching { searchProvider.search(pickupText, null, null, 4) }.getOrDefault(emptyList())
    }
    LaunchedEffect(destinationText, destination) {
        if (destination != null || destinationText.trim().length < 3) { destinationSuggestions = emptyList(); return@LaunchedEffect }
        delay(350)
        destinationSuggestions = runCatching { searchProvider.search(destinationText, pickup?.latitude, pickup?.longitude, 4) }.getOrDefault(emptyList())
    }

    fun chooseDate() {
        val selected = Calendar.getInstance().apply { timeInMillis = scheduledAt }
        DatePickerDialog(context, { _, year, month, day ->
            selected.set(year, month, day)
            scheduledAt = selected.timeInMillis
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun chooseTime() {
        val selected = Calendar.getInstance().apply { timeInMillis = scheduledAt }
        TimePickerDialog(context, { _, hour, minute ->
            selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute); selected.set(Calendar.SECOND, 0)
            scheduledAt = selected.timeInMillis
        }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), false).show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MeetColors.backgroundDark) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar") }
                    Column(Modifier.weight(1f)) {
                        Text("PROGRAMAR VIAJE", fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("Define lugares reales, fecha y preferencias", color = MeetColors.cyberCyan)
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Text("1 · RUTA", fontWeight = FontWeight.Bold, color = MeetColors.cyberCyan)
                        SchedulePlaceField("Punto de recogida", pickupText, pickup != null, pickupSuggestions,
                            onTextChange = { pickupText = it; pickup = null },
                            onSelect = { pickup = it; pickupText = it.displayLabel; pickupSuggestions = emptyList() })
                        Spacer(Modifier.height(10.dp))
                        SchedulePlaceField("Destino", destinationText, destination != null, destinationSuggestions,
                            onTextChange = { destinationText = it; destination = null },
                            onSelect = { destination = it; destinationText = it.displayLabel; destinationSuggestions = emptyList() })
                        Text("Solo se aceptan lugares seleccionados del mapa; no se inventan coordenadas.", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                    }
                    item {
                        Text("2 · FECHA Y HORA", fontWeight = FontWeight.Bold, color = MeetColors.cyberCyan)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(formatter.format(Date(scheduledAt)), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = ::chooseDate) { Text("Cambiar fecha") }
                                    OutlinedButton(onClick = ::chooseTime) { Text("Cambiar hora") }
                                }
                            }
                        }
                    }
                    item {
                        Text("3 · FRECUENCIA", fontWeight = FontWeight.Bold, color = MeetColors.cyberCyan)
                        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(RecurrencePattern.NONE to "Una vez", RecurrencePattern.WEEKDAYS to "Lunes a viernes", RecurrencePattern.WEEKLY to "Cada semana").forEach { (value, label) ->
                                FilterChip(selected = recurrence == value, onClick = { recurrence = value }, label = { Text(label) }, leadingIcon = { Icon(Icons.Default.Repeat, null, Modifier.size(16.dp)) })
                            }
                        }
                    }
                    item {
                        OutlinedTextField(value = notes, onValueChange = { notes = it.take(240) }, label = { Text("Indicaciones para la recogida (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Text("Se guardará en este dispositivo como solicitud programada. Un conductor solo queda asignado cuando el servidor lo confirme.", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                    }
                }
                val canSchedule = pickup != null && destination != null && scheduledAt > System.currentTimeMillis()
                Button(
                    onClick = {
                        val start = requireNotNull(pickup); val end = requireNotNull(destination)
                        onSchedule(
                            RideStop("scheduled-pickup-${start.providerId}", start.primaryLabel, start.displayLabel, start.latitude, start.longitude, order = 0, isPickup = true),
                            RideStop("scheduled-destination-${end.providerId}", end.primaryLabel, end.displayLabel, end.latitude, end.longitude, order = 1, isDropoff = true),
                            scheduledAt, RecurrenceConfig(recurrence), notes.trim(),
                        )
                    },
                    enabled = canSchedule,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                ) { Text(if (canSchedule) "GUARDAR VIAJE PROGRAMADO" else "SELECCIONA ORIGEN Y DESTINO") }
            }
        }
    }
}

@Composable
private fun SchedulePlaceField(
    label: String,
    value: String,
    resolved: Boolean,
    suggestions: List<RidePlaceSuggestion>,
    onTextChange: (String) -> Unit,
    onSelect: (RidePlaceSuggestion) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onTextChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
        trailingIcon = { if (resolved) Text("✓", color = MeetColors.neonGreen, fontWeight = FontWeight.Black) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    suggestions.forEach { suggestion ->
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onSelect(suggestion) },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(suggestion.primaryLabel, fontWeight = FontWeight.Bold)
                Text(suggestion.secondaryLabel, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
