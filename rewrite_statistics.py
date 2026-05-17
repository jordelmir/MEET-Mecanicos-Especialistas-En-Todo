import sys

content = """package com.elysium369.meet.ui.screens.scanner

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
import com.elysium369.meet.ui.components.neonGlow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScannerStatisticsTab(viewModel: ObdViewModel, isLandscape: Boolean = false) {
    val liveData by viewModel.liveData.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val drivingTime by viewModel.drivingTimeSeconds.collectAsState()
    val standingTime by viewModel.standingTimeSeconds.collectAsState()

    var visibleCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 0..15) {
            delay(100)
            visibleCount = i
        }
    }
    
    // Current session stats from calculated sensors
    val tripDistance = liveData["CALC_TRIP_DISTANCE"] ?: 0f
    val totalDistance = liveData["CALC_TOTAL_DISTANCE"] ?: 0f
    val avgSpeed = liveData["CALC_AVG_SPEED"] ?: 0f
    val fuelUsed = liveData["CALC_FUEL_USED"] ?: 0f
    val avgConsumption = liveData["CALC_AVG_CONSUMPTION"] ?: 0f
    val fuelPrice = liveData["CALC_FUEL_PRICE"] ?: 0f
    val totalTime = drivingTime + standingTime

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 4 else 2),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A)),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ═══════ HEADER ═══════
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Statistics",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                // Reset buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFF39FF14).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .border(1.dp, Color(0xFF39FF14).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable {
                                viewModel.resetTrip()
                                viewModel.resetDrivingTime()
                            }
                    ) {
                        Text(
                            "🗑️ Reset",
                            color = Color(0xFF39FF14),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ═══════ PERIOD INDICATOR ═══════
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Current Session",
                color = Color(0xFF3B82F6),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // ═══════ STAT CARDS ═══════
        item {
            AnimatedEntryItem(index = 0, visibleCount = visibleCount) {
                StatCardItem("🚗", "Distance", String.format("%.2f", tripDistance), "km")
            }
        }
        item {
            AnimatedEntryItem(index = 1, visibleCount = visibleCount) {
                StatCardItem("⏱", "Avg. speed", String.format("%.1f", avgSpeed), "km/h")
            }
        }
        item {
            AnimatedEntryItem(index = 2, visibleCount = visibleCount) {
                StatCardItem("⛽", "Fuel", String.format("%.3f", fuelUsed), "L", Color(0xFF3B82F6))
            }
        }
        item {
            AnimatedEntryItem(index = 3, visibleCount = visibleCount) {
                StatCardItem("📊", "Consumption", String.format("%.2f", avgConsumption), "L/100 km")
            }
        }
        item(span = { GridItemSpan(if (isLandscape) 1 else 2) }) {
            AnimatedEntryItem(index = 4, visibleCount = visibleCount) {
                StatCardItem("💰", "Costs", String.format("%.2f", fuelPrice), "$", Color(0xFF39FF14))
            }
        }

        // ═══════ TIME CARD ═══════
        item(span = { GridItemSpan(maxLineSpan) }) {
            AnimatedEntryItem(index = 5, visibleCount = visibleCount) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().neonGlow(Color(0xFF94A3B8), minAlpha = 0.05f, maxAlpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimeStatItem("⏱", "Total", totalTime, Color(0xFF94A3B8))
                        TimeStatItem("▶️", "Driving", drivingTime, Color(0xFF39FF14))
                        TimeStatItem("⏸️", "Standing", standingTime, Color(0xFFF59E0B))
                    }
                }
            }
        }

        // ═══════ DISTANCE + AVG CONSUMPTION CHART ═══════
        item(span = { GridItemSpan(maxLineSpan) }) {
            AnimatedEntryItem(index = 6, visibleCount = visibleCount) {
                Column {
                    Text(
                        "Distance+Avg.consumption",
                        color = Color(0xFF3B82F6),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().neonGlow(Color(0xFF3B82F6), minAlpha = 0.05f, maxAlpha = 0.2f)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp).padding(16.dp)) {
                            DistanceConsumptionChart(
                                distance = tripDistance,
                                consumption = avgConsumption
                            )
                        }
                        // Legend
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF3B82F6), RoundedCornerShape(50))
                            )
                            Text("  Distance  ", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFFEF4444), RoundedCornerShape(50))
                            )
                            Text("  Fuel", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // ═══════ TRIP HISTORY FROM DATABASE ═══════
        if (trips.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Trip History",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            val sortedTrips = trips.sortedByDescending { it.startedAt }.take(20)
            items(
                items = sortedTrips,
                span = { GridItemSpan(if (isLandscape) 2 else maxLineSpan) }
            ) { trip ->
                val itemIndex = 7 + sortedTrips.indexOf(trip)
                AnimatedEntryItem(index = itemIndex, visibleCount = visibleCount) {
                    TripHistoryCard(trip)
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
    iconColor: Color = Color(0xFFEF4444)
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .neonGlow(iconColor, minAlpha = 0.05f, maxAlpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun TimeStatItem(
    icon: String,
    label: String,
    seconds: Long,
    iconColor: Color = Color.Gray
) {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val timeStr = String.format("%d:%02d", hours, mins)

    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            timeStr,
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun DistanceConsumptionChart(
    distance: Float,
    consumption: Float
) {
    val barColor = Color(0xFF3B82F6)
    val dotColor = Color(0xFFEF4444)
    val gridColor = Color.Gray.copy(alpha = 0.2f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val chartWidth = size.width
        val chartHeight = size.height
        val padding = 40f

        // Grid lines
        for (i in 0..4) {
            val y = padding + (chartHeight - padding * 2) * (1 - i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(chartWidth - padding, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )
        }

        // Bar for distance (blue filled rectangle)
        val barWidth = (chartWidth - padding * 2) * 0.7f
        val maxDistance = (distance * 1.5f).coerceAtLeast(0.3f)
        val barHeight = ((distance / maxDistance) * (chartHeight - padding * 2)).coerceAtLeast(4f)
        
        drawRect(
            color = barColor,
            topLeft = Offset(padding + (chartWidth - padding * 2 - barWidth) / 2, chartHeight - padding - barHeight),
            size = Size(barWidth, barHeight)
        )

        // Dot for fuel consumption
        val maxConsumption = (consumption * 1.5f).coerceAtLeast(2.5f)
        val dotY = chartHeight - padding - ((consumption / maxConsumption) * (chartHeight - padding * 2)).coerceAtLeast(2f)
        drawCircle(
            color = dotColor,
            radius = 6f,
            center = Offset(chartWidth / 2f, dotY)
        )
    }
}

@Composable
private fun TripHistoryCard(trip: TripEntity) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val startStr = sdf.format(Date(trip.startedAt))
    val endStr = trip.endedAt?.let { sdf.format(Date(it)).substringAfter(", ") } ?: "--:--"
    val dateStr = sdf.format(Date(trip.startedAt)).substringBefore(",")
    val timeRange = "${sdf.format(Date(trip.startedAt)).substringAfter(", ")} - $endStr"

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2332)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .neonGlow(Color(0xFF3B82F6), minAlpha = 0.02f, maxAlpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "$dateStr, $timeRange",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats rows
            TripStatRow("🚗", "Distance", String.format("%.2f", trip.distanceKm), "km")
            TripStatRow("⏱", "Avg. speed", String.format("%.2f", trip.avgSpeedKmh), "km/h")
            
            if (trip.fuelEfficiency != null && trip.fuelEfficiency > 0) {
                TripStatRow("⛽", "Fuel", String.format("%.3f", trip.fuelEfficiency), "L")
                TripStatRow("📊", "Consumption", "0", "L/100 km")
            }
            
            TripStatRow("💰", "Costs", "0", "$")
        }
    }
}

@Composable
private fun TripStatRow(icon: String, label: String, value: String, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(unit, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}

// ═══════════════════════════════════════
// ANIMATED ENTRY WRAPPER
// ═══════════════════════════════════════

@Composable
private fun AnimatedEntryItem(
    index: Int,
    visibleCount: Int,
    content: @Composable () -> Unit
) {
    val isVisible = index <= visibleCount
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "entryAlpha$index"
    )
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "entryScale$index"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 40f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
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
"""

with open("android/app/src/main/kotlin/com/elysium369/meet/ui/screens/scanner/ScannerStatisticsTab.kt", "w") as f:
    f.write(content)

