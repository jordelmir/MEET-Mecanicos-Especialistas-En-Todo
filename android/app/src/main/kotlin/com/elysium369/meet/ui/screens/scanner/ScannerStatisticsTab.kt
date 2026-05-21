package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.data.local.entities.TripEntity
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScannerStatisticsTab(
    viewModel: ObdViewModel, 
    isLandscape: Boolean = false,
    isSpanish: Boolean
) {
    val liveData by viewModel.liveData.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val drivingTime by viewModel.drivingTimeSeconds.collectAsState()
    val standingTime by viewModel.standingTimeSeconds.collectAsState()

    var visibleCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 0..15) {
            delay(50)
            visibleCount = i
        }
    }
    
    // Current session stats from calculated sensors
    val tripDistance = liveData["CALC_TRIP_DISTANCE"] ?: 0f
    val avgSpeed = liveData["CALC_AVG_SPEED"] ?: 0f
    val fuelUsed = liveData["CALC_FUEL_USED"] ?: 0f
    val avgConsumption = liveData["CALC_AVG_CONSUMPTION"] ?: 0f
    val fuelPrice = liveData["CALC_FUEL_PRICE"] ?: 0f
    val totalTime = drivingTime + standingTime

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 4 else 2),
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isSpanish) "Estadísticas de Viaje" else "Trip Telemetry",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                
                EliteCard(
                    backgroundColor = MeetColors.error.copy(alpha = 0.12f),
                    borderColor = MeetColors.error.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        viewModel.resetTrip()
                        viewModel.resetDrivingTime()
                    }
                ) {
                    Text(
                        if (isSpanish) "🗑️ Restablecer" else "🗑️ Reset",
                        color = MeetColors.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Subtitle Indicator
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (isSpanish) "SESIÓN EN CURSO" else "CURRENT SESSION",
                color = MeetColors.cyberCyan,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }

        // Stat Cards grid items
        item {
            AnimatedEntryItem(index = 0, visibleCount = visibleCount) {
                StatCardItem(
                    "🚗", 
                    if (isSpanish) "Distancia" else "Distance", 
                    String.format("%.2f", tripDistance), 
                    "km",
                    MeetColors.cyberCyan
                )
            }
        }
        item {
            AnimatedEntryItem(index = 1, visibleCount = visibleCount) {
                StatCardItem(
                    "⏱", 
                    if (isSpanish) "Velocidad Media" else "Avg. Speed", 
                    String.format("%.1f", avgSpeed), 
                    "km/h",
                    MeetColors.cyberCyan
                )
            }
        }
        item {
            AnimatedEntryItem(index = 2, visibleCount = visibleCount) {
                StatCardItem(
                    "⛽", 
                    if (isSpanish) "Combustible" else "Fuel Used", 
                    String.format("%.3f", fuelUsed), 
                    "L", 
                    MeetColors.electricBlue
                )
            }
        }
        item {
            AnimatedEntryItem(index = 3, visibleCount = visibleCount) {
                StatCardItem(
                    "📊", 
                    if (isSpanish) "Consumo Promedio" else "Avg. Consumption", 
                    String.format("%.1f", avgConsumption), 
                    "L/100 km",
                    MeetColors.electricBlue
                )
            }
        }
        item(span = { GridItemSpan(if (isLandscape) 1 else 2) }) {
            AnimatedEntryItem(index = 4, visibleCount = visibleCount) {
                StatCardItem(
                    "💰", 
                    if (isSpanish) "Costo Estimado" else "Est. Cost", 
                    String.format("%.2f", fuelPrice), 
                    "$", 
                    MeetColors.neonGreen
                )
            }
        }

        // Time Stats row
        item(span = { GridItemSpan(maxLineSpan) }) {
            AnimatedEntryItem(index = 5, visibleCount = visibleCount) {
                EliteCard(
                    backgroundColor = MeetColors.backgroundDeep,
                    shape = RoundedCornerShape(14.dp),
                    borderColor = MeetColors.borderSubtle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimeStatItem("⏱", if (isSpanish) "Tiempo Total" else "Total Time", totalTime)
                        TimeStatItem("▶️", if (isSpanish) "En Movimiento" else "Driving Time", drivingTime)
                        TimeStatItem("⏸️", if (isSpanish) "En Ralentí" else "Idle Time", standingTime)
                    }
                }
            }
        }

        // High-Tech Digital telemetry graph
        item(span = { GridItemSpan(maxLineSpan) }) {
            AnimatedEntryItem(index = 6, visibleCount = visibleCount) {
                Column {
                    Text(
                        if (isSpanish) "EFICIENCIA DE VIAJE (DISTANCIA vs CONSUMO)" else "TRIP EFFICIENCY (DISTANCE vs FUEL)",
                        color = MeetColors.cyberCyan.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    
                    EliteCard(
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(14.dp),
                        borderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                        glowColor = MeetColors.electricBlue.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            ) {
                                DistanceConsumptionChart(
                                    distance = tripDistance,
                                    consumption = avgConsumption
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MeetColors.electricBlue, RoundedCornerShape(50))
                                )
                                Text(
                                    if (isSpanish) "  Distancia (km)  " else "  Distance (km)  ", 
                                    color = MeetColors.textSecondary, 
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MeetColors.error, RoundedCornerShape(50))
                                )
                                Text(
                                    if (isSpanish) "  Consumo (L/100km)  " else "  Consumption (L/100km)  ", 
                                    color = MeetColors.textSecondary, 
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Historial de viajes
        if (trips.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (isSpanish) "Historial de Recorridos" else "Trip History Logs",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            val sortedTrips = trips.sortedByDescending { it.startedAt }.take(15)
            items(
                items = sortedTrips,
                span = { GridItemSpan(if (isLandscape) 2 else maxLineSpan) }
            ) { trip ->
                val itemIndex = 7 + sortedTrips.indexOf(trip)
                AnimatedEntryItem(index = itemIndex, visibleCount = visibleCount) {
                    TripHistoryCard(trip, isSpanish)
                }
            }
        }
    }
}

@Composable
private fun StatCardItem(
    icon: String,
    label: String,
    value: String,
    unit: String,
    glowColor: Color
) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(14.dp),
        borderColor = glowColor.copy(alpha = 0.3f),
        glowColor = glowColor.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, color = MeetColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun TimeStatItem(
    icon: String,
    label: String,
    seconds: Long
) {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    val timeStr = if (hours > 0) {
        String.format("%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }

    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            timeStr,
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun DistanceConsumptionChart(
    distance: Float,
    consumption: Float
) {
    val barColor = MeetColors.electricBlue
    val dotColor = MeetColors.error
    val gridColor = Color.White.copy(alpha = 0.08f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val paddingLeftRight = 20f
        val paddingTopBottom = 20f
        
        val plotWidth = w - paddingLeftRight * 2
        val plotHeight = h - paddingTopBottom * 2

        // Draw background grid lines (CRT instrumentation style)
        for (i in 0..4) {
            val y = paddingTopBottom + plotHeight * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(paddingLeftRight, y),
                end = Offset(w - paddingLeftRight, y),
                strokeWidth = 1.dp.toPx()
            )
            
            val x = paddingLeftRight + plotWidth * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(x, paddingTopBottom),
                end = Offset(x, h - paddingTopBottom),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw distance bar (left half side of chart)
        val maxDist = (distance * 1.3f).coerceAtLeast(1.0f)
        val distHeight = (distance / maxDist) * plotHeight
        val barW = plotWidth * 0.3f
        val barX = paddingLeftRight + plotWidth * 0.15f
        
        drawRoundRect(
            color = barColor.copy(alpha = 0.2f),
            topLeft = Offset(barX, h - paddingTopBottom - distHeight),
            size = Size(barW, distHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = barColor,
            topLeft = Offset(barX, h - paddingTopBottom - distHeight),
            size = Size(barW, 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // Draw consumption bar (right half side of chart)
        val maxCons = (consumption * 1.3f).coerceAtLeast(1.0f)
        val consHeight = (consumption / maxCons) * plotHeight
        val barConsX = paddingLeftRight + plotWidth * 0.55f
        
        drawRoundRect(
            color = dotColor.copy(alpha = 0.2f),
            topLeft = Offset(barConsX, h - paddingTopBottom - consHeight),
            size = Size(barW, consHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = dotColor,
            topLeft = Offset(barConsX, h - paddingTopBottom - consHeight),
            size = Size(barW, 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

@Composable
private fun TripHistoryCard(trip: TripEntity, isSpanish: Boolean) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val endStr = trip.endedAt?.let { sdf.format(Date(it)).substringAfter(", ") } ?: "--:--"
    val dateStr = sdf.format(Date(trip.startedAt)).substringBefore(",")
    val timeRange = "${sdf.format(Date(trip.startedAt)).substringAfter(", ")} - $endStr"

    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(14.dp),
        borderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
        glowColor = MeetColors.electricBlue.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "$dateStr  •  $timeRange",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))
            
            // Statistics values
            TripStatRow(
                "🚗", 
                if (isSpanish) "Distancia Recorrida" else "Distance Traveled", 
                String.format("%.2f", trip.distanceKm), 
                "km"
            )
            TripStatRow(
                "⏱", 
                if (isSpanish) "Velocidad Promedio" else "Average Speed", 
                String.format("%.1f", trip.avgSpeedKmh), 
                "km/h"
            )
            
            if (trip.fuelEfficiency != null && trip.fuelEfficiency > 0f) {
                TripStatRow(
                    "⛽", 
                    if (isSpanish) "Combustible Consumido" else "Fuel Spent", 
                    String.format("%.2f", trip.fuelEfficiency), 
                    "L"
                )
            }
        }
    }
}

@Composable
private fun TripStatRow(icon: String, label: String, value: String, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(unit, color = MeetColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AnimatedEntryItem(
    index: Int,
    visibleCount: Int,
    content: @Composable () -> Unit
) {
    val isVisible = index <= visibleCount
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "entryAlpha$index"
    )
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "entryScale$index"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 30f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "entryOffset$index"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .scale(scale)
            .offset(y = offsetY.dp)
    ) {
        content()
    }
}
