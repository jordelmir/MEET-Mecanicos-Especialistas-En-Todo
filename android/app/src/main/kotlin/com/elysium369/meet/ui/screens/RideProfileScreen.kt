package com.elysium369.meet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.domain.RideProfileAnalytics
import com.elysium369.meet.ride.domain.RideProfileSummary
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
    onBack: () -> Unit,
) {
    val rides by viewModel.rideRequests.collectAsState()
    val driver by viewModel.driverVerification.collectAsState()
    val passenger by viewModel.passengerVerification.collectAsState()
    val id = if (isDriver) {
        driver?.driverId ?: viewModel.currentRideActorId
    } else {
        passenger?.passengerId ?: viewModel.currentRideActorId
    }
    val name = if (isDriver) driver?.fullName else passenger?.fullName
    val roleRides = remember(rides, id, isDriver) {
        if (id == null) emptyList() else if (isDriver) {
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
    var tab by remember { mutableIntStateOf(0) }

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
                0 -> RideProfileOverview(name, isDriver, summary)
                1 -> RideHistoryPanel(roleRides)
                else -> RideSupportPanel(summary, roleRides)
            }
        }
    }
}

@Composable
private fun RideProfileOverview(name: String?, isDriver: Boolean, summary: RideProfileSummary) {
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
                            "★ ${String.format(Locale.getDefault(), "%.2f", it)} · ${summary.capturedRatings} calificaciones"
                        } ?: "Calificación: dato no capturado",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        item { RideMetricGrid(summary, isDriver) }
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
private fun RideMetricGrid(data: RideProfileSummary, isDriver: Boolean) {
    val money = NumberFormat.getCurrencyInstance(Locale("es", "CR"))
    ProfileSection(if (isDriver) "CENTRO DE INGRESOS" else "RESUMEN ANUAL") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileMetric("Viajes", data.completedTrips.toString(), Modifier.weight(1f))
            ProfileMetric("Distancia", "${data.totalDistanceKm.toInt()} km", Modifier.weight(1f))
            ProfileMetric("Cancelados", data.cancelledTrips.toString(), Modifier.weight(1f))
        }
        if (isDriver) {
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
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ride.createdAt)), color = MeetColors.textMuted, fontSize = 10.sp)
                    }
                    Text(ride.pickupAddress, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Text("→ ${ride.destAddress}", color = MeetColors.cyberCyan, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    Text(
                        "${ride.finalPrice ?: ride.priceOffer} ${ride.currency} · ${String.format(Locale.getDefault(), "%.1f km", ride.estimatedDistanceKm)}",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RideSupportPanel(summary: RideProfileSummary?, rides: List<RideRequestEntity>) {
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
                            "Cancelación · ${DateFormat.getDateInstance().format(Date(ride.createdAt))}",
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
