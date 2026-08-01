package com.elysium369.meet.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.domain.RideProfileAnalytics
import com.elysium369.meet.ride.domain.RideProfileSummary
import com.elysium369.meet.ride.domain.RideDriverVehicleSummary
import com.elysium369.meet.ride.domain.RideSupportCategory
import com.elysium369.meet.ride.domain.RideSupportPolicy
import com.elysium369.meet.ride.map.RideDriverAvatar
import com.elysium369.meet.ride.map.RideMapAvatarRenderer
import com.elysium369.meet.ride.map.RideMapAvatarSelection
import com.elysium369.meet.ride.map.RideMapAvatarStore
import com.elysium369.meet.ride.map.RideMarkerRole
import com.elysium369.meet.ride.map.RidePassengerAvatar
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideProfileScreen(
    viewModel: ObdViewModel,
    isDriver: Boolean,
    initialTab: Int = 0,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val rides by viewModel.rideRequests.collectAsState()
    val driver by viewModel.driverVerification.collectAsState()
    val passenger by viewModel.passengerVerification.collectAsState()
    val fleetVehicles by viewModel.rideDriverVehicles.collectAsState()
    val id = if (isDriver) {
        driver?.driverId ?: viewModel.currentRideActorId
    } else {
        passenger?.passengerId ?: viewModel.currentRideActorId
    }
    val name = if (isDriver) driver?.fullName else passenger?.fullName
    val driverVehicleSummary = driver?.let {
        listOf(it.vehicleMake, it.vehicleModel, it.vehicleYear.toString(), it.vehicleColor)
            .filter(String::isNotBlank)
            .joinToString(" ")
    }
    val driverPlate = driver?.vehiclePlate
    val roleRides = remember(rides, id, isDriver) {
        if (isDriver) {
            rides.filter { it.assignedDriverId == id }
        } else {
            rides.filter { it.passengerId == id }
        }
    }
    val summary = remember(roleRides, id, isDriver) {
        if (isDriver) {
            RideProfileAnalytics.driver(roleRides, id)
        } else {
            RideProfileAnalytics.passenger(roleRides, id)
        }
    }
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var supportRide by remember { mutableStateOf<RideRequestEntity?>(null) }
    var showAddVehicle by remember { mutableStateOf(false) }

    LaunchedEffect(isDriver) {
        if (isDriver) viewModel.refreshRideDriverVehicles()
    }

    LaunchedEffect(viewModel) {
        viewModel.rideSupportFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    supportRide?.let { ride ->
        RideSupportCaseDialog(
            ride = ride,
            onDismiss = { supportRide = null },
            onSubmit = { category, summary ->
                viewModel.openRideSupportCase(ride.requestId, category, summary)
                supportRide = null
            },
        )
    }
    if (showAddVehicle) {
        RideAddVehicleDialog(
            onDismiss = { showAddVehicle = false },
            onSubmit = { make, model, year, color, plate, fleet ->
                viewModel.addRideDriverVehicle(make, model, year, color, plate, fleet)
                showAddVehicle = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PERFIL ELYSIUM", color = Color.White, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDark),
            )
        },
        containerColor = MeetColors.backgroundDark,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color(0xFF07131E),
                contentColor = MeetColors.cyberCyan,
            ) {
                listOf(
                    Icons.Default.Person to "Perfil",
                    Icons.Default.History to "Historial",
                    Icons.Default.SupportAgent to "Soporte",
                    Icons.Default.LocationOn to "Iconos",
                ).forEachIndexed { index, item ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.first, null) },
                        text = { Text(item.second, fontSize = 11.sp) },
                    )
                }
            }
            when (tab) {
                0 -> RideProfileOverview(
                    name = name,
                    isDriver = isDriver,
                    summary = summary,
                    driverVehicleSummary = driverVehicleSummary,
                    driverPlate = driverPlate,
                    fleetVehicles = fleetVehicles,
                    onAddVehicle = { showAddVehicle = true },
                    onActivateVehicle = viewModel::activateRideDriverVehicle,
                )
                1 -> RideHistoryPanel(roleRides)
                2 -> RideSupportPanel(
                    summary = summary,
                    rides = roleRides,
                    onOpenCase = { supportRide = it },
                )
                else -> RideMapAvatarPanel(isDriver = isDriver)
            }
        }
    }
}

@Composable
private fun RideMapAvatarPanel(isDriver: Boolean) {
    val context = LocalContext.current
    val store = remember(context) { RideMapAvatarStore(context) }
    var selection by remember(context) { mutableStateOf(store.load()) }
    val role = if (isDriver) RideMarkerRole.DRIVER else RideMarkerRole.PASSENGER_GPS
    val preview = remember(context, selection, role) {
        RideMapAvatarRenderer.render(context, role, selection, sizeDp = 112)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = .5f)),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF071A28), Color(0xFF160B2B), Color(0xFF071019)),
                            ),
                        )
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "IDENTIDAD EN EL MAPA",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.size(132.dp),
                        shape = CircleShape,
                        color = Color(0xFF02080E).copy(alpha = .72f),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = .5f)),
                    ) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Vista previa del icono seleccionado",
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (isDriver) selection.driver.displayName else selection.passenger.displayName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Se usa en el mapa activo de Elysium Vanguard y no cambia datos de seguridad ni ubicación.",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        item {
            Text(
                if (isDriver) "ELIGE TU EMBLEMA DE CONDUCTOR" else "ELIGE TU AVATAR DE PASAJERO",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.1.sp,
            )
        }
        if (isDriver) {
            items(RideDriverAvatar.entries, key = { it.storageId }) { avatar ->
                val candidate = selection.copy(driver = avatar)
                RideAvatarChoice(
                    title = avatar.displayName,
                    description = avatar.description,
                    selected = selection.driver == avatar,
                    preview = remember(context, avatar) {
                        RideMapAvatarRenderer.render(context, RideMarkerRole.DRIVER, candidate, sizeDp = 72)
                    },
                    onClick = {
                        selection = candidate
                        store.save(candidate)
                    },
                )
            }
        } else {
            items(RidePassengerAvatar.entries, key = { it.storageId }) { avatar ->
                val candidate = selection.copy(passenger = avatar)
                RideAvatarChoice(
                    title = avatar.displayName,
                    description = avatar.description,
                    selected = selection.passenger == avatar,
                    preview = remember(context, avatar) {
                        RideMapAvatarRenderer.render(context, RideMarkerRole.PASSENGER_GPS, candidate, sizeDp = 72)
                    },
                    onClick = {
                        selection = candidate
                        store.save(candidate)
                    },
                )
            }
        }
        item {
            Text(
                "Todos los diseños son originales de Elysium Vanguard. El catálogo queda preparado para añadir nuevas colecciones sin alterar el motor del mapa.",
                color = MeetColors.textMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun RideAvatarChoice(
    title: String,
    description: String,
    selected: Boolean,
    preview: android.graphics.Bitmap,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) Color(0xFF0B2630) else Color(0xFF08141F),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MeetColors.neonGreen else MeetColors.borderSubtle,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Color(0xFF02080E),
            ) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(description, color = MeetColors.textSecondary, fontSize = 10.sp)
            }
            Text(
                if (selected) "ACTIVO" else "ELEGIR",
                color = if (selected) MeetColors.neonGreen else MeetColors.cyberCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun RideProfileOverview(
    name: String?,
    isDriver: Boolean,
    summary: RideProfileSummary,
    driverVehicleSummary: String?,
    driverPlate: String?,
    fleetVehicles: List<RideDriverVehicleSummary>,
    onAddVehicle: () -> Unit,
    onActivateVehicle: (String) -> Unit,
) {
    val currentLocale = rememberRideJavaLocale()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF071A28), Color(0xFF101026), Color(0xFF071019)),
                            ),
                        )
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        color = MeetColors.cyberCyan.copy(alpha = 0.16f),
                        border = BorderStroke(2.dp, MeetColors.cyberCyan),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                name?.trim()?.firstOrNull()?.uppercase() ?: "EV",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(name ?: "Perfil pendiente", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (isDriver) "CONDUCTOR ELYSIUM" else "PASAJERO ELYSIUM",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                    )
                    Text(
                        summary.averageRating?.let {
                            "★ ${String.format(currentLocale, "%.2f", it)} · ${summary.capturedRatings} calificaciones"
                        } ?: "Calificación: dato no capturado",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item { RideMetricGrid(summary, isDriver) }
        if (isDriver) {
            item {
                ProfileSection("AUTOS Y FLOTILLAS") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MeetColors.neonGreen.copy(alpha = 0.14f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("◆", color = MeetColors.neonGreen, fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                driverVehicleSummary ?: "Sin vehículo activo confirmado",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                driverPlate?.takeIf(String::isNotBlank)?.let { "Placa: $it · ACTIVO" }
                                    ?: "Placa: dato no capturado",
                                color = MeetColors.textMuted,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    Text(
                        "La cuenta admite flotillas; la autoridad garantiza que sólo un vehículo quede activo por conductor.",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                    )
                    Text(
                        "Marca, modelo, año, color, placa y revisión documental se validan antes de recibir viajes.",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                    )
                    fleetVehicles.forEach { vehicle ->
                        Surface(
                            color = Color(0xFF0B1722),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (vehicle.active) MeetColors.neonGreen else MeetColors.borderSubtle,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(vehicle.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(
                                        listOfNotNull(vehicle.plateMasked, vehicle.fleetName, vehicle.verificationStatus)
                                            .joinToString(" · "),
                                        color = MeetColors.textMuted,
                                        fontSize = 9.sp,
                                    )
                                }
                                if (!vehicle.active) {
                                    TextButton(
                                        onClick = { onActivateVehicle(vehicle.id) },
                                        enabled = vehicle.verificationStatus == "VERIFIED",
                                    ) { Text("ACTIVAR") }
                                } else {
                                    Text("ACTIVO", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = onAddVehicle, modifier = Modifier.fillMaxWidth()) {
                        Text("AGREGAR VEHÍCULO A LA FLOTILLA")
                    }
                }
            }
        }
        item { RatingDistribution(summary) }
        item {
            ProfileSection("RECONOCIMIENTOS") {
                if (summary.recognitions.isEmpty()) {
                    Text("Aún no hay reconocimientos desbloqueados.", color = MeetColors.textMuted, fontSize = 12.sp)
                } else {
                    summary.recognitions.forEach {
                        Text("◆ $it", color = MeetColors.neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (name == null) {
            item {
                Text(
                    "La verificación mejora la confianza y habilita solicitudes, pero tu tablero e historial permanecen accesibles.",
                    color = MeetColors.warning,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun RideAddVehicleDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, String, String, String?) -> Unit,
) {
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var fleet by remember { mutableStateOf("") }
    val valid = make.isNotBlank() && model.isNotBlank() &&
        year.toIntOrNull()?.let { it in 1900..2200 } == true &&
        color.isNotBlank() && plate.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF071019),
        title = { Text("AGREGAR AUTO", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(make, { make = it.take(60) }, label = { Text("Marca") })
                OutlinedTextField(model, { model = it.take(60) }, label = { Text("Modelo") })
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Año") })
                OutlinedTextField(color, { color = it.take(40) }, label = { Text("Color") })
                OutlinedTextField(plate, { plate = it.take(20) }, label = { Text("Placa") })
                OutlinedTextField(fleet, { fleet = it.take(80) }, label = { Text("Nombre de flotilla (opcional)") })
                Text(
                    "El auto se agrega como pendiente. No podrá activarse ni recibir viajes hasta completar revisión.",
                    color = MeetColors.warning,
                    fontSize = 10.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(make, model, requireNotNull(year.toIntOrNull()), color, plate, fleet.ifBlank { null }) },
                enabled = valid,
            ) { Text("GUARDAR PARA REVISIÓN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } },
    )
}

@Composable
private fun RideMetricGrid(data: RideProfileSummary, isDriver: Boolean) {
    val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CR"))
    ProfileSection(if (isDriver) "CENTRO DE INGRESOS" else "RESUMEN ANUAL") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileMetric("Viajes", data.completedTrips.toString(), Modifier.weight(1f))
            ProfileMetric("Distancia", "${data.totalDistanceKm.toInt()} km", Modifier.weight(1f))
            ProfileMetric("Cancelados", data.cancelledTrips.toString(), Modifier.weight(1f))
        }
        if (isDriver) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMetric(
                    "Aceptación",
                    data.acceptanceRatePercent?.let { String.format(Locale.ROOT, "%.0f%%", it) }
                        ?: "Pendiente servidor",
                    Modifier.weight(1f),
                )
                ProfileMetric(
                    "Viajes finalizados",
                    data.completionRatePercent?.let { String.format(Locale.ROOT, "%.0f%%", it) }
                        ?: "Sin base suficiente",
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMetric("Hoy", money.format(data.money.today), Modifier.weight(1f))
                ProfileMetric("Semana", money.format(data.money.week), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMetric("Mes", money.format(data.money.month), Modifier.weight(1f))
                ProfileMetric("Año", money.format(data.money.year), Modifier.weight(1f))
            }
            Text(
                "Histórico móvil de 3 años: ${money.format(data.money.rollingThreeYears)}. No altera saldos ni pagos.",
                color = MeetColors.textMuted,
                fontSize = 10.sp,
            )
        } else {
            ProfileMetric("Gastado en viajes este año", money.format(data.money.year), Modifier.fillMaxWidth())
            Text("El contador anual cambia automáticamente al iniciar un nuevo año.", color = MeetColors.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color(0xFF0B1722), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(label, color = MeetColors.textMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun RatingDistribution(data: RideProfileSummary) {
    ProfileSection("REPUTACIÓN VERIFICADA") {
        (5 downTo 1).forEach { stars ->
            val count = data.ratingDistribution[stars].orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$stars ★", color = Color.White, modifier = Modifier.width(38.dp), fontSize = 11.sp)
                LinearProgressIndicator(
                    progress = { if (data.capturedRatings == 0) 0f else count.toFloat() / data.capturedRatings },
                    modifier = Modifier.weight(1f).height(7.dp),
                    color = MeetColors.neonGreen,
                    trackColor = MeetColors.borderSubtle,
                )
                Text(count.toString(), color = MeetColors.textSecondary, modifier = Modifier.width(30.dp), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RideHistoryPanel(rides: List<RideRequestEntity>) {
    val currentLocale = rememberRideJavaLocale()
    val dateTimeFormat = remember(currentLocale) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, currentLocale)
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (rides.isEmpty()) {
            item { Text("No hay viajes capturados todavía.", color = MeetColors.textMuted) }
        }
        items(rides.sortedByDescending { it.createdAt }) { ride ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF08141F)),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ride.status, color = statusColor(ride.status), fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(dateTimeFormat.format(Date(ride.createdAt)), color = MeetColors.textMuted, fontSize = 10.sp)
                    }
                    Text(ride.pickupAddress, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Text("→ ${ride.destAddress}", color = MeetColors.cyberCyan, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Text(
                        "${ride.finalPrice ?: ride.priceOffer} ${ride.currency} · ${String.format(currentLocale, "%.1f km", ride.estimatedDistanceKm)}",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RideSupportPanel(
    summary: RideProfileSummary?,
    rides: List<RideRequestEntity>,
    onOpenCase: (RideRequestEntity) -> Unit,
) {
    val context = LocalContext.current
    val currentLocale = rememberRideJavaLocale()
    val dateFormat = remember(currentLocale) {
        DateFormat.getDateInstance(DateFormat.DEFAULT, currentLocale)
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ProfileSection("CENTRO DE SOPORTE ELYSIUM") {
                Text("Emergencia o riesgo inmediato: usa primero los servicios oficiales de emergencia de tu localidad.", color = MeetColors.warning, fontSize = 11.sp)
                Text("Viajes activos: abre el viaje para reportar seguridad, identidad, vehículo, ruta o incidente vial.", color = Color.White, fontSize = 12.sp)
                Text("Privacidad: las ubicaciones exactas y la telemetría solo se comparten con autorización voluntaria.", color = MeetColors.textSecondary, fontSize = 11.sp)
            }
        }
        item {
            ProfileSection("ACCIONES RÁPIDAS") {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:".toUri()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error),
                ) {
                    Text("EMERGENCIA · ABRIR MARCADOR", fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = { rides.maxByOrNull(RideRequestEntity::createdAt)?.let(onOpenCase) },
                    enabled = rides.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("OBJETO PERDIDO / COBRO / CONDUCTA")
                }
                Text(
                    "1. Selecciona el viaje. 2. Elige la categoría. 3. Describe hechos verificables. 4. Conserva el número del caso.",
                    color = MeetColors.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        item {
            ProfileSection("HISTORIAL DE INCONVENIENTES") {
                Text(
                    "${summary?.cancelledTrips ?: 0} cancelaciones registradas en este perfil.",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Los detalles sensibles permanecen en el historial del viaje; no se inventan casos ni resoluciones.",
                    color = MeetColors.textMuted,
                    fontSize = 10.sp,
                )
            }
        }
        if (rides.isNotEmpty()) {
            item {
                Text(
                    "ABRIR CASO POR VIAJE",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                )
            }
            items(rides.sortedByDescending { it.createdAt }.take(10)) { ride ->
                OutlinedButton(
                    onClick = { onOpenCase(ride) },
                    enabled = ride.serverVersion > 0L,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            "${dateFormat.format(Date(ride.createdAt))} · ${ride.status}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                        Text(
                            if (ride.serverVersion > 0L) {
                                "${ride.pickupAddress} → ${ride.destAddress}"
                            } else {
                                "Pendiente de confirmación remota; soporte no disponible todavía."
                            },
                            color = MeetColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
        val cancelled = rides.filter { it.status == "CANCELLED" }.sortedByDescending { it.createdAt }
        if (cancelled.isNotEmpty()) {
            item {
                Text(
                    "CASOS REGISTRADOS",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                )
            }
            items(cancelled) { ride ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF08141F)),
                    border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(
                            "Cancelación · ${dateFormat.format(Date(ride.createdAt))}",
                            color = MeetColors.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                        Text(
                            "${ride.pickupAddress} → ${ride.destAddress}",
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                        )
                        Text(
                            "Abre el viaje desde Historial para revisar la evidencia y conversación capturadas.",
                            color = MeetColors.textMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RideSupportCaseDialog(
    ride: RideRequestEntity,
    onDismiss: () -> Unit,
    onSubmit: (RideSupportCategory, String) -> Unit,
) {
    var category by remember { mutableStateOf<RideSupportCategory?>(null) }
    var summary by remember { mutableStateOf("") }
    val valid = category != null && RideSupportPolicy.isValidSummary(summary)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF071019),
        title = {
            Column {
                Text(
                    "NUEVO CASO DE SOPORTE",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Viaje ${ride.requestId.take(8)} · ${ride.status}",
                    color = MeetColors.textMuted,
                    fontSize = 10.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .height(460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Elige la categoría real. Un caso financiero podrá referenciar un ajuste compensatorio aprobado, pero soporte nunca edita el ledger.",
                    color = MeetColors.warning,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
                RideSupportCategory.entries.forEach { item ->
                    OutlinedButton(
                        onClick = { category = item },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        border = BorderStroke(
                            1.dp,
                            if (category == item) MeetColors.cyberCyan else MeetColors.borderSubtle,
                        ),
                    ) {
                        Text(
                            item.supportLabel(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it.take(RideSupportPolicy.MAX_SUMMARY_LENGTH) },
                    label = { Text("Describe lo ocurrido") },
                    supportingText = {
                        Text(
                            "${summary.trim().length}/${RideSupportPolicy.MAX_SUMMARY_LENGTH} · mínimo ${RideSupportPolicy.MIN_SUMMARY_LENGTH}",
                        )
                    },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    category?.let { onSubmit(it, summary.trim()) }
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
            ) {
                Text("ENVIAR CASO", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("VOLVER")
            }
        },
    )
}

private fun RideSupportCategory.supportLabel(): String = when (this) {
    RideSupportCategory.LOST_ITEM -> "Objeto perdido"
    RideSupportCategory.WRONG_CHARGE -> "Cobro incorrecto"
    RideSupportCategory.WRONG_DRIVER -> "Conductor no coincide"
    RideSupportCategory.WRONG_PASSENGER -> "Pasajero no coincide"
    RideSupportCategory.ROUTE_ISSUE -> "Problema con la ruta"
    RideSupportCategory.ACCIDENT -> "Accidente"
    RideSupportCategory.CANCELLATION -> "Cancelación"
    RideSupportCategory.PAYMENT -> "Pago"
    RideSupportCategory.COMMISSION -> "Comisión"
    RideSupportCategory.DOCUMENT -> "Documento"
    RideSupportCategory.BEHAVIOR -> "Comportamiento"
    RideSupportCategory.OTHER -> "Otro"
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF07131E)),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            content()
        }
    }
}

private fun statusColor(status: String): Color = when (status) {
    "COMPLETED" -> MeetColors.neonGreen
    "CANCELLED" -> MeetColors.error
    "OPEN" -> MeetColors.warning
    else -> MeetColors.cyberCyan
}

private fun Int?.orEmpty(): Int = this ?: 0
