package com.elysium369.meet.ui.screens.ride

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.domain.RideStopSnapshot
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ride.map.RideRoadRoute
import com.elysium369.meet.ride.map.resilientRideRoutingProvider
import com.elysium369.meet.ui.screens.RideMapPanel
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RideHistoryDetailDialog(
    ride: RideRequestEntity,
    onDismiss: () -> Unit,
    onOpenSupport: ((RideRequestEntity) -> Unit)? = null,
) {
    val context = LocalContext.current
    val locale = remember { Locale.forLanguageTag("es-CR") }
    val dateTimeFormat = remember(locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    }

    val pickupPoint = remember(ride.pickupLatitude, ride.pickupLongitude) {
        RideGeoPoint(
            latitude = ride.pickupLatitude,
            longitude = ride.pickupLongitude,
            accuracyMeters = ride.pickupAccuracy.takeIf { it > 0f },
            capturedAtEpochMs = ride.createdAt,
        )
    }

    val destPoint = remember(ride.destLatitude, ride.destLongitude) {
        RideGeoPoint(
            latitude = ride.destLatitude,
            longitude = ride.destLongitude,
            accuracyMeters = null,
            capturedAtEpochMs = ride.completedAt ?: ride.createdAt,
        )
    }

    val stopsList = remember(ride.stopsJson) {
        runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<RideStopSnapshot>>(ride.stopsJson)
        }.getOrDefault(emptyList()).filter { it.isResolved }
    }

    val stopGeoPoints = remember(stopsList) {
        stopsList.mapNotNull { stop ->
            if (stop.latitude != null && stop.longitude != null) {
                RideGeoPoint(
                    latitude = stop.latitude,
                    longitude = stop.longitude,
                    accuracyMeters = null,
                    capturedAtEpochMs = ride.createdAt,
                )
            } else null
        }
    }

    val routingProvider = remember {
        resilientRideRoutingProvider(
            primaryEndpoint = BuildConfig.RIDE_ROUTER_URL,
            fallbackEndpoint = BuildConfig.RIDE_ROUTER_FALLBACK_URL,
        )
    }

    var routeGeometry by remember { mutableStateOf<List<RideGeoPoint>?>(null) }
    var routeLoading by remember { mutableStateOf(true) }

    LaunchedEffect(pickupPoint, destPoint, stopGeoPoints) {
        routeLoading = true
        val resolvedRoute = runCatching {
            val allWaypoints = listOf(pickupPoint) + stopGeoPoints + listOf(destPoint)
            routingProvider.route(allWaypoints).geometry
        }.getOrNull()

        routeGeometry = resolvedRoute ?: listOf(pickupPoint) + stopGeoPoints + listOf(destPoint)
        routeLoading = false
    }

    val mapState = remember(pickupPoint, destPoint, stopGeoPoints, routeGeometry) {
        RideMapStateFactory.create(
            pickup = pickupPoint,
            stops = stopGeoPoints,
            destination = destPoint,
            route = routeGeometry,
        )
    }

    val statusColor = when (ride.status.uppercase()) {
        "COMPLETED" -> MeetColors.neonGreen
        "ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED" -> MeetColors.cyberCyan
        "IN_PROGRESS", "PASSENGER_ONBOARD" -> Color(0xFF64FFDA)
        "CANCELLED", "EXPIRED", "VOIDED" -> Color(0xFFEF5350)
        else -> MeetColors.warning
    }

    val statusLabel = when (ride.status.uppercase()) {
        "COMPLETED" -> "VIAJE COMPLETADO ✓"
        "ACCEPTED" -> "CHOFER ASIGNADO"
        "DRIVER_EN_ROUTE" -> "CHOFER EN CAMINO"
        "ARRIVED" -> "CHOFER EN EL PUNTO"
        "IN_PROGRESS" -> "EN TRAYECTO"
        "CANCELLED" -> "VIAJE CANCELADO"
        else -> ride.status
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF060F17),
            border = BorderStroke(1.5.dp, Brush.verticalGradient(listOf(MeetColors.cyberCyan, Color(0xFF1E3A5F)))),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF091827))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = statusColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Route,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "DETALLE DEL VIAJE",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                            )
                            Text(
                                dateTimeFormat.format(Date(ride.createdAt)),
                                color = MeetColors.textMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Map Panel showing exact route
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            RideMapPanel(
                                state = mapState,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(statusColor, CircleShape),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        statusLabel,
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }
                    }

                    // Cancellation Banner (if cancelled)
                    if (ride.status.equals("CANCELLED", ignoreCase = true) ||
                        ride.serverState?.equals("CANCELLED", ignoreCase = true) == true
                    ) {
                        Surface(
                            color = Color(0xFF2C1014),
                            border = BorderStroke(1.dp, Color(0xFFEF5350)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🚫", fontSize = 16.sp)
                                    Text(
                                        text = "VIAJE CANCELADO EN CAMINO",
                                        color = Color(0xFFFF8A80),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = "Registro preservado en viajes finalizados para trazabilidad y auditoría de la plataforma MEET.",
                                    color = Color(0xFFFFCDD2),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // Route Points Card
                    DetailSectionCard(title = "TRAYECTO Y PUNTOS DE CONTROL", icon = Icons.Default.Navigation) {
                        // Origin
                        LocationRow(
                            icon = Icons.Default.LocationOn,
                            iconTint = MeetColors.neonGreen,
                            title = "Punto de recogida",
                            address = ride.pickupAddress,
                            lat = ride.pickupLatitude,
                            lng = ride.pickupLongitude,
                            context = context,
                        )

                        // Intermediate stops if present
                        stopsList.forEachIndexed { idx, stop ->
                            HorizontalDivider(
                                color = MeetColors.borderSubtle.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            LocationRow(
                                icon = Icons.Default.Pin,
                                iconTint = MeetColors.warning,
                                title = "Parada ${idx + 1}",
                                address = stop.label,
                                lat = stop.latitude ?: 0.0,
                                lng = stop.longitude ?: 0.0,
                                context = context,
                            )
                        }

                        HorizontalDivider(
                            color = MeetColors.borderSubtle.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )

                        // Destination
                        LocationRow(
                            icon = Icons.Default.LocationOn,
                            iconTint = MeetColors.cyberCyan,
                            title = "Destino",
                            address = ride.destAddress,
                            lat = ride.destLatitude,
                            lng = ride.destLongitude,
                            context = context,
                        )
                    }

                    // Distance, Time & Speed Card
                    DetailSectionCard(title = "MÉTRICAS DEL RECORRIDO", icon = Icons.Default.Speed) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricItem(
                                label = "Distancia",
                                value = String.format(locale, "%.2f km", ride.estimatedDistanceKm),
                                modifier = Modifier.weight(1f),
                            )
                            MetricItem(
                                label = "Tiempo estimado",
                                value = "${ride.estimatedDurationMin} min",
                                modifier = Modifier.weight(1f),
                            )
                            ride.completedAt?.let { comp ->
                                val diffMin = ((comp - ride.createdAt) / 60000).coerceAtLeast(1)
                                MetricItem(
                                    label = "Duración total",
                                    value = "$diffMin min",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Financial Card
                    DetailSectionCard(title = "TARIFA Y PAGO", icon = Icons.Default.Payments) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Tarifa acordada", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(
                                    "${ride.finalPrice ?: ride.priceOffer} ${ride.currency}",
                                    color = MeetColors.neonGreen,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Método de pago", color = MeetColors.textMuted, fontSize = 11.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0D253A),
                                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        ride.paymentMethod,
                                        color = MeetColors.cyberCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Modo de cálculo", color = MeetColors.textSecondary, fontSize = 11.sp)
                            Text(
                                if (ride.fareMode == "METERED_TIME_DISTANCE") "Taxímetro tiempo-distancia" else "Pon tu precio acordado",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Security, Driver & Passenger Identity
                    DetailSectionCard(title = "SEGURIDAD E IDENTIDAD", icon = Icons.Default.Security) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Conductor", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(
                                    ride.assignedDriverName ?: "Sin conductor asignado",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                ride.assignedDriverVehicle?.let {
                                    Text(it, color = MeetColors.cyberCyan, fontSize = 11.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Pasajero", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(
                                    ride.passengerName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (ride.passengerPhone.isNotBlank()) {
                                    Text(ride.passengerPhone, color = MeetColors.textMuted, fontSize = 11.sp)
                                }
                            }
                        }

                        ride.boardingPin?.let { pin ->
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.3f))
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("PIN de abordaje de seguridad", color = MeetColors.textSecondary, fontSize = 11.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, MeetColors.neonGreen),
                                ) {
                                    Text(
                                        pin,
                                        color = MeetColors.neonGreen,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("ID Viaje", ride.requestId))
                                    Toast.makeText(context, "ID de viaje copiado", Toast.LENGTH_SHORT).show()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("ID único: ${ride.requestId.take(16)}...", color = MeetColors.textMuted, fontSize = 10.sp)
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MeetColors.textMuted, modifier = Modifier.size(14.dp))
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        onOpenSupport?.let { openSupport ->
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    openSupport(ride)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.warning),
                                border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.6f)),
                            ) {
                                Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Soporte", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132A3E), contentColor = Color.White),
                        ) {
                            Text("Cerrar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1624)),
        border = BorderStroke(1.dp, MeetColors.borderSubtle.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                )
            }
            content()
        }
    }
}

@Composable
private fun LocationRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    address: String,
    lat: Double,
    lng: Double,
    context: Context,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(address, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (lat != 0.0 || lng != 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("GPS", "$lat, $lng"))
                            Toast.makeText(context, "Coordenadas copiadas", Toast.LENGTH_SHORT).show()
                        }
                        .padding(top = 2.dp),
                ) {
                    Text(
                        String.format(Locale.ROOT, "%.5f, %.5f", lat, lng),
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar GPS", tint = MeetColors.textMuted, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = MeetColors.textMuted, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}
