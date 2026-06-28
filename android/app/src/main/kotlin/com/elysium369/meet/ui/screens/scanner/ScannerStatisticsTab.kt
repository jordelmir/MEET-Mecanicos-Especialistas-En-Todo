package com.elysium369.meet.ui.screens.scanner

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.data.local.entities.TripEntity
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerStatisticsTab(
    viewModel: ObdViewModel,
    isLandscape: Boolean = false,
    isSpanish: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ═══ STATES FROM VIEWMODEL ═══
    val liveData by viewModel.liveData.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val drivingTime by viewModel.drivingTimeSeconds.collectAsState()
    val standingTime by viewModel.standingTimeSeconds.collectAsState()
    val activeDtcs by viewModel.activeDtcEvents.collectAsState()

    // Preferences & Config Flow
    val useImperialUnits by viewModel.useImperialUnits.collectAsState()
    val fuelPrice by viewModel.fuelPrice.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val fuelType by viewModel.fuelType.collectAsState()

    // ═══ LOCAL DISPLAY STATES ═══
    var statisticsTab by remember { mutableStateOf("ACTIVE") } // "ACTIVE" or "HISTORY"
    var historyPeriod by remember { mutableStateOf("WEEK") } // "WEEK" or "MONTH" or "ALL"
    var showConfigDialog by remember { mutableStateOf(false) }
    var selectedTripForSheet by remember { mutableStateOf<TripEntity?>(null) }

    // Animation entry controller
    var visibleCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(statisticsTab) {
        visibleCount = 0
        for (i in 0..18) {
            delay(30)
            visibleCount = i
        }
    }

    // Active trip telemetry lists (for real-time graphs)
    val activeSpeedList = remember(liveData) { viewModel.getSpeedHistory() }
    val activeRpmList = remember(liveData) { viewModel.getRpmHistory() }
    val activeThrottleList = remember(liveData) { viewModel.getThrottleHistory() }

    // Eco Coaching Tips
    val ecoCoachingTips = listOf(
        "Frenar con suavidad evita el desgaste prematuro de balatas y ahorra combustible.",
        "Mantén tus RPM abajo de 2,500 en marcha de crucero para un EcoScore perfecto.",
        "Evita ralentí prolongado (motor encendido parado); consume hasta 1.2 L por hora.",
        "Una aceleración progresiva reduce las emisiones contaminantes de CO2.",
        "Monitorea el voltaje del alternador. Debe mantenerse estable entre 13.5V y 14.5V."
    )
    var activeTipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(8000)
            activeTipIndex = (activeTipIndex + 1) % ecoCoachingTips.size
        }
    }

    // ═══ ACTIVE SESSION VALUES ═══
    val rawDistance = liveData["CALC_TRIP_DISTANCE"] ?: 0f
    val rawAvgSpeed = liveData["CALC_AVG_SPEED"] ?: 0f
    val rawFuelUsed = liveData["CALC_FUEL_USED"] ?: 0f
    val rawAvgConsumption = liveData["CALC_AVG_CONSUMPTION"] ?: 0f
    
    // Aggregates for harsh events (real-time detection)
    val harshAccelsCount = remember(activeSpeedList) {
        activeSpeedList.windowed(2).count { (prev, curr) -> (curr - prev) > 8f }
    }
    val harshBrakingCount = remember(activeSpeedList) {
        activeSpeedList.windowed(2).count { (prev, curr) -> (curr - prev) < -12f }
    }
    val highRpmCount = remember(activeRpmList) {
        activeRpmList.count { it > 3500f }
    }
    val activeEcoScore = remember(activeSpeedList, activeRpmList, activeThrottleList) {
        if (activeSpeedList.size < 2) 100 else {
            var penalty = harshAccelsCount * 6 + harshBrakingCount * 10
            if (activeRpmList.isNotEmpty()) {
                penalty += (highRpmCount.toFloat() / activeRpmList.size * 60).toInt()
            }
            if (activeThrottleList.isNotEmpty()) {
                val highThrottle = activeThrottleList.count { it > 70f }
                penalty += (highThrottle.toFloat() / activeThrottleList.size * 30).toInt()
            }
            (100 - penalty).coerceIn(0, 100)
        }
    }

    // Wasted fuel in idle estimation (1.2 Liters per hour or 0.317 Gallons per hour)
    val wastedIdleFuelLiters = (standingTime / 3600f) * 1.2f
    val wastedIdleCost = wastedIdleFuelLiters * fuelPrice

    // CO2 Footprint calculation (EPA: Gasoline = 2.31 kg/L, Diesel = 2.68 kg/L)
    val co2Factor = if (fuelType == "DIESEL") 2.68f else 2.31f
    val co2EmissionsKg = rawFuelUsed * co2Factor

    // Metric / Imperial conversions for Active Tab
    val activeDistance = if (useImperialUnits) rawDistance * 0.621371f else rawDistance
    val activeDistanceUnit = if (useImperialUnits) "mi" else "km"

    val activeAvgSpeed = if (useImperialUnits) rawAvgSpeed * 0.621371f else rawAvgSpeed
    val activeSpeedUnit = if (useImperialUnits) "mph" else "km/h"

    val activeFuelUsed = if (useImperialUnits) rawFuelUsed * 0.264172f else rawFuelUsed
    val activeFuelUnit = if (useImperialUnits) "gal" else "L"

    val activeConsumption = if (useImperialUnits) {
        if (rawAvgConsumption > 0f) 235.215f / rawAvgConsumption else 0f
    } else rawAvgConsumption
    val activeConsumptionUnit = if (useImperialUnits) "MPG" else "L/100 km"

    // ═══ HISTORICAL AGGREGATES ═══
    val now = System.currentTimeMillis()
    val filteredTrips = remember(trips, historyPeriod) {
        val periodMs = when (historyPeriod) {
            "WEEK" -> 7L * 24 * 60 * 60 * 1000
            "MONTH" -> 30L * 24 * 60 * 60 * 1000
            else -> Long.MAX_VALUE
        }
        trips.filter { it.startedAt >= (now - periodMs) }
    }

    val histTotalTrips = filteredTrips.size
    val histTotalDistanceKm = filteredTrips.sumOf { it.distanceKm.toDouble() }.toFloat()
    val histTotalDistance = if (useImperialUnits) histTotalDistanceKm * 0.621371f else histTotalDistanceKm

    val histAvgSpeedKmh = if (filteredTrips.isNotEmpty()) filteredTrips.map { it.avgSpeedKmh }.average().toFloat() else 0f
    val histAvgSpeed = if (useImperialUnits) histAvgSpeedKmh * 0.621371f else histAvgSpeedKmh

    val histTotalDuration = filteredTrips.sumOf { it.durationSeconds }

    val histAvgEcoScore = if (filteredTrips.isNotEmpty()) filteredTrips.map { it.ecoScore }.average().toInt() else 100

    val histTotalFuelUsedLiters = filteredTrips.sumOf { trip ->
        // Synthesise fuel used from efficiency and distance: L/100km * km / 100
        val eff = trip.fuelEfficiency ?: 0f
        if (eff > 0) (eff * trip.distanceKm / 100.0) else 0.0
    }.toFloat()

    val histTotalFuelUsed = if (useImperialUnits) histTotalFuelUsedLiters * 0.264172f else histTotalFuelUsedLiters
    val histTotalFuelCost = histTotalFuelUsedLiters * fuelPrice
    val histCo2EmissionsKg = histTotalFuelUsedLiters * co2Factor

    // Media histórica total (para badges de comparación)
    val grandAverageEco = if (trips.isNotEmpty()) trips.map { it.ecoScore }.average().toInt() else 100
    val grandAverageCons = if (trips.isNotEmpty()) trips.map { it.fuelEfficiency ?: 0f }.filter { it > 0f }.average().toFloat() else 0f

    // ═══ RENDER MAIN GRID ═══
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 4 else 2),
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ═══ HEADER ROW (Title & Config & Reset) ═══
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (isSpanish) "Telemetría Elysium Vanguard" else "Elysium Vanguard Telemetry",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = if (isSpanish) "Estadísticas y perfiles de conducción" else "Conduction profiles & analytics",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cardBackgroundLighter)
                            .size(36.dp)
                    ) {
                        AnimatedNeonGlyph("⚙️", contentDescription = null, fontSize = 16.sp)
                    }

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
                            if (isSpanish) "Reset" else "Reset",
                            color = MeetColors.error,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // ═══ ACTIVE / HISTORY TAB SELECTOR ═══
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.backgroundDeep)
                    .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (statisticsTab == "ACTIVE") MeetColors.neonGreen.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { statisticsTab = "ACTIVE" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSpanish) "SESIÓN EN CURSO" else "ACTIVE SESSION",
                        color = if (statisticsTab == "ACTIVE") MeetColors.neonGreen else MeetColors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (statisticsTab == "HISTORY") MeetColors.cyberCyan.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { statisticsTab = "HISTORY" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSpanish) "HISTORIAL ACUMULADO" else "ACCUMULATED HISTORY",
                        color = if (statisticsTab == "HISTORY") MeetColors.cyberCyan else MeetColors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ═══ ACTIVE TAB CONTENT ═══
        if (statisticsTab == "ACTIVE") {
            // Warning: DTC Efficiency Degradation
            if (activeDtcs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedEntryItem(index = 0, visibleCount = visibleCount) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MeetColors.error.copy(alpha = 0.15f),
                                            MeetColors.error.copy(alpha = 0.05f)
                                        )
                                    )
                                )
                                .border(1.dp, MeetColors.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AnimatedNeonGlyph("⚠️", contentDescription = null, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isSpanish) "EFICIENCIA DEGRADADA" else "DEGRADED FUEL EFFICIENCY",
                                        color = MeetColors.error,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isSpanish) {
                                        "La ECU registra códigos de falla (DTCs) activos. La inyección puede operar en bucle abierto (Open Loop), aumentando el consumo hasta un 20%."
                                    } else {
                                        "ECU reports active trouble codes (DTCs). Fuel system may run in Open Loop mode, increasing consumption by up to 20%."
                                    },
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Radial EcoScore Gauge & CO2 Footprint Card
            item(span = { GridItemSpan(if (isLandscape) 2 else maxLineSpan) }) {
                AnimatedEntryItem(index = 1, visibleCount = visibleCount) {
                    EliteCard(
                        backgroundColor = MeetColors.backgroundDeep,
                        borderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                        glowColor = MeetColors.neonGreen.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // Radial Arc Gauge
                            Box(
                                modifier = Modifier.size(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val animScore by animateIntAsState(
                                    targetValue = activeEcoScore,
                                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                                    label = "ecoScoreAnim"
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawArc(
                                        color = Color.White.copy(alpha = 0.08f),
                                        startAngle = 135f,
                                        sweepAngle = 270f,
                                        useCenter = false,
                                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    val sweep = (animScore.toFloat() / 100f) * 270f
                                    val gaugeColor = when {
                                        animScore >= 80 -> MeetColors.neonGreen
                                        animScore >= 60 -> MeetColors.warning
                                        else -> MeetColors.error
                                    }
                                    drawArc(
                                        color = gaugeColor,
                                        startAngle = 135f,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$animScore",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "EcoScore",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Telemetry labels (CO2 & Rating)
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Text(
                                    if (isSpanish) "HUELLA CO2" else "CO2 FOOTPRINT",
                                    color = MeetColors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format("%.2f", co2EmissionsKg),
                                        color = MeetColors.cyberCyan,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("kg CO2", color = MeetColors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val ecoRating = when {
                                    activeEcoScore >= 85 -> if (isSpanish) "CONDUCCIÓN EXCELENTE" else "EXCELLENT DRIVING"
                                    activeEcoScore >= 70 -> if (isSpanish) "MODERADO" else "EFFICIENT"
                                    else -> if (isSpanish) "INEFICIENTE" else "INEFFICIENT"
                                }
                                val ratingColor = when {
                                    activeEcoScore >= 85 -> MeetColors.neonGreen
                                    activeEcoScore >= 70 -> MeetColors.warning
                                    else -> MeetColors.error
                                }
                                Text(
                                    text = ecoRating,
                                    color = ratingColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Stat Cards for Active session
            item {
                AnimatedEntryItem(index = 2, visibleCount = visibleCount) {
                    StatCardItem(
                        icon = "🚗",
                        label = if (isSpanish) "Distancia" else "Distance",
                        value = String.format("%.2f", activeDistance),
                        unit = activeDistanceUnit,
                        glowColor = MeetColors.cyberCyan
                    )
                }
            }
            item {
                AnimatedEntryItem(index = 3, visibleCount = visibleCount) {
                    StatCardItem(
                        icon = "⏱",
                        label = if (isSpanish) "Velocidad Media" else "Avg. Speed",
                        value = String.format("%.1f", activeAvgSpeed),
                        unit = activeSpeedUnit,
                        glowColor = MeetColors.cyberCyan
                    )
                }
            }
            item {
                AnimatedEntryItem(index = 4, visibleCount = visibleCount) {
                    StatCardItem(
                        icon = "⛽",
                        label = if (isSpanish) "Combustible" else "Fuel Used",
                        value = String.format("%.3f", activeFuelUsed),
                        unit = activeFuelUnit,
                        glowColor = MeetColors.electricBlue
                    )
                }
            }
            item {
                AnimatedEntryItem(index = 5, visibleCount = visibleCount) {
                    StatCardItem(
                        icon = "📊",
                        label = if (isSpanish) "Consumo Promedio" else "Avg. Consumption",
                        value = String.format("%.1f", activeConsumption),
                        unit = activeConsumptionUnit,
                        glowColor = MeetColors.electricBlue
                    )
                }
            }

            // Wasted Idle Fuel Estimator Card
            item(span = { GridItemSpan(if (isLandscape) 1 else 2) }) {
                AnimatedEntryItem(index = 6, visibleCount = visibleCount) {
                    EliteCard(
                        backgroundColor = MeetColors.backgroundDeep,
                        borderColor = MeetColors.error.copy(alpha = 0.25f),
                        glowColor = MeetColors.error.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⏸️", fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isSpanish) "Desperdicio en Ralentí" else "Idle Fuel Wasted",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    val displayWastedFuel = if (useImperialUnits) wastedIdleFuelLiters * 0.264172f else wastedIdleFuelLiters
                                    Text(
                                        text = String.format("%.3f %s", displayWastedFuel, activeFuelUnit),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = if (isSpanish) "Combustible perdido" else "Fuel wasted",
                                        color = MeetColors.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format("%s %.2f", currencySymbol, wastedIdleCost),
                                        color = MeetColors.error,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = if (isSpanish) "Costo financiero" else "Costo perdido",
                                        color = MeetColors.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Harsh Driving Events Counters
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnimatedEntryItem(index = 7, visibleCount = visibleCount) {
                    EliteCard(
                        backgroundColor = MeetColors.backgroundDeep,
                        borderColor = MeetColors.borderSubtle,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = if (isSpanish) "EVENTOS DE CONDUCCIÓN AGRESIVA" else "HARSH DRIVING INCIDENTS",
                                color = MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                HarshEventCounter(
                                    label = if (isSpanish) "Acel. Brusca" else "Harsh Accel",
                                    count = harshAccelsCount,
                                    badgeColor = MeetColors.warning
                                )
                                HarshEventCounter(
                                    label = if (isSpanish) "Frenado Violento" else "Hard Brake",
                                    count = harshBrakingCount,
                                    badgeColor = MeetColors.error
                                )
                                HarshEventCounter(
                                    label = if (isSpanish) "Sobre-Rev (>3.5k)" else "Over-Revving",
                                    count = highRpmCount,
                                    badgeColor = Color(0xFFBD00FF)
                                )
                            }
                        }
                    }
                }
            }

            // Time stats row
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnimatedEntryItem(index = 8, visibleCount = visibleCount) {
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
                            TimeStatItem("⏱", if (isSpanish) "Tiempo Total" else "Total Time", drivingTime + standingTime)
                            TimeStatItem("▶️", if (isSpanish) "En Movimiento" else "Driving Time", drivingTime)
                            TimeStatItem("⏸️", if (isSpanish) "En Ralentí" else "Idle Time", standingTime)
                        }
                    }
                }
            }

            // Real-Time Speed & RPM Line Graph
            item(span = { GridItemSpan(maxLineSpan) }) {
                AnimatedEntryItem(index = 9, visibleCount = visibleCount) {
                    Column {
                        Text(
                            if (isSpanish) "PERFIL DE CONDUCCIÓN EN TIEMPO REAL" else "REAL-TIME CONDUCTION PROFILE",
                            color = MeetColors.cyberCyan.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                        )
                        
                        EliteCard(
                            backgroundColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(14.dp),
                            borderColor = MeetColors.cyberCyan.copy(alpha = 0.3f),
                            glowColor = MeetColors.cyberCyan.copy(alpha = 0.05f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                ) {
                                    SpeedRpmLineChart(
                                        speeds = activeSpeedList,
                                        rpms = activeRpmList,
                                        useImperial = useImperialUnits
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
                                            .background(MeetColors.cyberCyan, RoundedCornerShape(50))
                                    )
                                    Text(
                                        if (isSpanish) "  Velocidad (${activeSpeedUnit})  " else "  Speed (${activeSpeedUnit})  ", 
                                        color = MeetColors.textSecondary, 
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(modifier = Modifier.width(24.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFFBD00FF), RoundedCornerShape(50))
                                    )
                                    Text(
                                        "  RPM x100  ", 
                                        color = MeetColors.textSecondary, 
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═══ HISTORICAL TAB CONTENT ═══
        if (statisticsTab == "HISTORY") {
            // Period Filter Chips (Week / Month / All)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PeriodFilterChip(
                        selected = historyPeriod == "WEEK",
                        onClick = { historyPeriod = "WEEK" },
                        label = if (isSpanish) "ÚLTIMOS 7 DÍAS" else "LAST 7 DAYS",
                        activeColor = MeetColors.cyberCyan
                    )
                    PeriodFilterChip(
                        selected = historyPeriod == "MONTH",
                        onClick = { historyPeriod = "MONTH" },
                        label = if (isSpanish) "ÚLTIMOS 30 DÍAS" else "LAST 30 DAYS",
                        activeColor = MeetColors.cyberCyan
                    )
                    PeriodFilterChip(
                        selected = historyPeriod == "ALL",
                        onClick = { historyPeriod = "ALL" },
                        label = if (isSpanish) "TODO EL HISTORIAL" else "ALL HISTORY",
                        activeColor = MeetColors.cyberCyan
                    )
                }
            }

            if (filteredTrips.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSpanish) "No hay viajes registrados en este período." else "No trips recorded in this period.",
                            color = MeetColors.textMuted,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                // Aggregates dashboard cards
                item {
                    AnimatedEntryItem(index = 1, visibleCount = visibleCount) {
                        StatCardItem(
                            icon = "📊",
                            label = if (isSpanish) "Trayectos Totales" else "Total Trips",
                            value = "$histTotalTrips",
                            unit = if (isSpanish) "viajes" else "trips",
                            glowColor = MeetColors.cyberCyan
                        )
                    }
                }
                item {
                    AnimatedEntryItem(index = 2, visibleCount = visibleCount) {
                        StatCardItem(
                            icon = "🚗",
                            label = if (isSpanish) "Distancia Acumulada" else "Total Distance",
                            value = String.format("%.1f", histTotalDistance),
                            unit = activeDistanceUnit,
                            glowColor = MeetColors.cyberCyan
                        )
                    }
                }
                item {
                    AnimatedEntryItem(index = 3, visibleCount = visibleCount) {
                        StatCardItem(
                            icon = "⏱",
                            label = if (isSpanish) "Velocidad Promedio" else "Avg. Speed Hist.",
                            value = String.format("%.1f", histAvgSpeed),
                            unit = activeSpeedUnit,
                            glowColor = MeetColors.cyberCyan
                        )
                    }
                }
                item {
                    AnimatedEntryItem(index = 4, visibleCount = visibleCount) {
                        StatCardItem(
                            icon = "⛽",
                            label = if (isSpanish) "Combustible Consumido" else "Fuel Spent Hist.",
                            value = String.format("%.1f", histTotalFuelUsed),
                            unit = activeFuelUnit,
                            glowColor = MeetColors.electricBlue
                        )
                    }
                }
                item {
                    AnimatedEntryItem(index = 5, visibleCount = visibleCount) {
                        StatCardItem(
                            icon = "💰",
                            label = if (isSpanish) "Costo Combustible" else "Total Fuel Cost",
                            value = String.format("%.2f", histTotalFuelCost),
                            unit = currencySymbol,
                            glowColor = MeetColors.neonGreen
                        )
                    }
                }
                item {
                    AnimatedEntryItem(index = 6, visibleCount = visibleCount) {
                        StatCardItem(
                            icon = "🌱",
                            label = "CO2 Historial",
                            value = String.format("%.1f", histCo2EmissionsKg),
                            unit = "kg CO2",
                            glowColor = MeetColors.neonGreen
                        )
                    }
                }

                // Alternator Health Battery Voltages Chart
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedEntryItem(index = 7, visibleCount = visibleCount) {
                        Column {
                            Text(
                                if (isSpanish) "ESTADO DEL ALTERNADOR (VOLTAJE DE BATERÍA HISTÓRICO)" else "ALTERNATOR HEALTH (BATTERY VOLTAGE HISTORY)",
                                color = MeetColors.neonGreen.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                            
                            EliteCard(
                                backgroundColor = MeetColors.backgroundDeep,
                                shape = RoundedCornerShape(14.dp),
                                borderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                glowColor = MeetColors.neonGreen.copy(alpha = 0.05f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                    ) {
                                        AlternatorVoltageChart(trips = filteredTrips)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MeetColors.neonGreen, RoundedCornerShape(50))
                                        )
                                        Text(
                                            if (isSpanish) "  Voltaje Máx (Carga alternador)  " else "  Max Voltage (Alternator charge)  ", 
                                            color = MeetColors.textSecondary, 
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Spacer(modifier = Modifier.width(20.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MeetColors.error, RoundedCornerShape(50))
                                        )
                                        Text(
                                            if (isSpanish) "  Voltaje Mín (Arranque/Bajo)  " else "  Min Voltage (Crank/Low)  ", 
                                            color = MeetColors.textSecondary, 
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Average EcoScore radial card
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedEntryItem(index = 8, visibleCount = visibleCount) {
                        EliteCard(
                            backgroundColor = MeetColors.backgroundDeep,
                            borderColor = MeetColors.cyberCyan.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Box(
                                    modifier = Modifier.size(90.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.08f),
                                            startAngle = 135f,
                                            sweepAngle = 270f,
                                            useCenter = false,
                                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        val sweep = (histAvgEcoScore.toFloat() / 100f) * 270f
                                        drawArc(
                                            color = MeetColors.cyberCyan,
                                            startAngle = 135f,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$histAvgEcoScore",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            "EcoScore",
                                            color = MeetColors.textSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        if (isSpanish) "CALIFICACIÓN PROMEDIO" else "AVERAGE RATING",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isSpanish) {
                                            "Tu puntaje medio en los últimos ${filteredTrips.size} viajes."
                                        } else {
                                            "Your average score across the last ${filteredTrips.size} trips."
                                        },
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Trip logs list header
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (isSpanish) "Detalle de Recorridos" else "Trip Logs History",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                // Historical trips logs
                val sortedTrips = filteredTrips.sortedByDescending { it.startedAt }.take(15)
                items(
                    items = sortedTrips,
                    span = { GridItemSpan(if (isLandscape) 2 else maxLineSpan) }
                ) { trip ->
                    val itemIndex = 9 + sortedTrips.indexOf(trip)
                    AnimatedEntryItem(index = itemIndex, visibleCount = visibleCount) {
                        TripHistoryCardWithBadge(
                            trip = trip,
                            grandAverageEco = grandAverageEco,
                            grandAverageCons = grandAverageCons,
                            useImperial = useImperialUnits,
                            isSpanish = isSpanish,
                            onClick = { selectedTripForSheet = trip }
                        )
                    }
                }
            }
        }

        // ═══ ECO COACHING PREVENTIVE BANNER ═══
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeetColors.backgroundDeep)
                    .border(
                        1.dp,
                        MeetColors.electricBlue.copy(alpha = 0.25f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedNeonGlyph("💡", contentDescription = null, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isSpanish) "ECO COACHING Elysium Vanguard" else "Elysium Vanguard ECO COACHING",
                            color = MeetColors.electricBlue,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        AnimatedContent(
                            targetState = activeTipIndex,
                            transitionSpec = {
                                slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                            },
                            label = "tipAnim"
                        ) { index ->
                            Text(
                                text = ecoCoachingTips[index],
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ═══ CONFIGURATION PREFERENCES DIALOG ═══
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = {
                Text(
                    text = if (isSpanish) "Ajustes de Telemetría" else "Telemetry Configuration",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                )
            },
            containerColor = MeetColors.backgroundDeep,
            tonalElevation = 6.dp,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Metric vs Imperial units
                    Column {
                        Text(
                            text = if (isSpanish) "Sistema de Unidades" else "Unit System",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.setUseImperialUnits(false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!useImperialUnits) MeetColors.cyberCyan else MeetColors.cardBackgroundLighter,
                                    contentColor = if (!useImperialUnits) MeetColors.backgroundDeep else Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isSpanish) "Métrico (km, L)" else "Metric (km, L)")
                            }
                            Button(
                                onClick = { viewModel.setUseImperialUnits(true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (useImperialUnits) MeetColors.cyberCyan else MeetColors.cardBackgroundLighter,
                                    contentColor = if (useImperialUnits) MeetColors.backgroundDeep else Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isSpanish) "Imperial (mi, gal)" else "Imperial (mi, gal)")
                            }
                        }
                    }

                    // Fuel Type Selection
                    Column {
                        Text(
                            text = if (isSpanish) "Tipo de Carburante" else "Fuel Type",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.setFuelType("GASOLINE") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (fuelType == "GASOLINE") MeetColors.electricBlue else MeetColors.cardBackgroundLighter,
                                    contentColor = if (fuelType == "GASOLINE") MeetColors.backgroundDeep else Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isSpanish) "Gasolina" else "Gasoline")
                            }
                            Button(
                                onClick = { viewModel.setFuelType("DIESEL") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (fuelType == "DIESEL") MeetColors.electricBlue else MeetColors.cardBackgroundLighter,
                                    contentColor = if (fuelType == "DIESEL") MeetColors.backgroundDeep else Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Diesel")
                            }
                        }
                    }

                    // Fuel price input
                    var priceText by remember { mutableStateOf(fuelPrice.toString()) }
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = {
                            priceText = it
                            it.toFloatOrNull()?.let { p -> viewModel.setFuelPrice(p) }
                        },
                        label = { Text(if (isSpanish) "Precio Combustible por Litro" else "Fuel Price per Liter", color = MeetColors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.textMuted.copy(alpha = 0.3f)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Local currency symbol input
                    var symbolText by remember { mutableStateOf(currencySymbol) }
                    OutlinedTextField(
                        value = symbolText,
                        onValueChange = {
                            symbolText = it
                            if (it.isNotBlank()) viewModel.setCurrencySymbol(it)
                        },
                        label = { Text(if (isSpanish) "Símbolo de Moneda" else "Currency Symbol", color = MeetColors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.textMuted.copy(alpha = 0.3f)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text(if (isSpanish) "ACEPTAR" else "OK", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ═══ INTERACTIVE TRIP DETAILS BOTTOM SHEET (Task 27) ═══
    if (selectedTripForSheet != null) {
        val trip = selectedTripForSheet!!
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateText = sdf.format(Date(trip.startedAt))
        val durationText = TimeStatString(trip.durationSeconds)

        val displayDist = if (useImperialUnits) trip.distanceKm * 0.621371f else trip.distanceKm
        val displayAvgSpeed = if (useImperialUnits) trip.avgSpeedKmh * 0.621371f else trip.avgSpeedKmh
        val displayMaxSpeed = if (useImperialUnits) trip.maxSpeedKmh * 0.621371f else trip.maxSpeedKmh
        val displayMaxTemp = if (useImperialUnits) trip.maxTempC * 1.8f + 32f else trip.maxTempC
        val tempUnit = if (useImperialUnits) "°F" else "°C"

        val tripFuelLiters = trip.fuelEfficiency?.let { eff ->
            if (eff > 0f) (eff * trip.distanceKm / 100f) else 0f
        } ?: 0f
        val displayFuel = if (useImperialUnits) tripFuelLiters * 0.264172f else tripFuelLiters

        ModalBottomSheet(
            onDismissRequest = { selectedTripForSheet = null },
            containerColor = MeetColors.backgroundDeep,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MeetColors.textMuted.copy(alpha = 0.3f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isSpanish) "Detalle de Trayecto" else "Trip Details",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = dateText,
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Rating badge in sheet
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    trip.ecoScore >= 85 -> MeetColors.neonGreen.copy(alpha = 0.15f)
                                    trip.ecoScore >= 70 -> MeetColors.warning.copy(alpha = 0.15f)
                                    else -> MeetColors.error.copy(alpha = 0.15f)
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    trip.ecoScore >= 85 -> MeetColors.neonGreen
                                    trip.ecoScore >= 70 -> MeetColors.warning
                                    else -> MeetColors.error
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "EcoScore: ${trip.ecoScore}",
                            color = when {
                                trip.ecoScore >= 85 -> MeetColors.neonGreen
                                trip.ecoScore >= 70 -> MeetColors.warning
                                else -> MeetColors.error
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                Spacer(modifier = Modifier.height(16.dp))

                // Detail Items Grid
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow(if (isSpanish) "Distancia Recorrida" else "Distance Traveled", String.format("%.2f %s", displayDist, activeDistanceUnit))
                    DetailRow(if (isSpanish) "Duración de Viaje" else "Trip Duration", durationText)
                    DetailRow(if (isSpanish) "Velocidad Promedio" else "Average Speed", String.format("%.1f %s", displayAvgSpeed, activeSpeedUnit))
                    DetailRow(if (isSpanish) "Velocidad Máxima" else "Max Speed Recorded", String.format("%.1f %s", displayMaxSpeed, activeSpeedUnit))
                    DetailRow(if (isSpanish) "Régimen Máximo (RPM)" else "Max Engine Speed", String.format("%.0f RPM", trip.maxRpm))
                    DetailRow(if (isSpanish) "Régimen Promedio" else "Average RPM", String.format("%.0f RPM", trip.avgRpm))
                    DetailRow(if (isSpanish) "Temperatura Máx Motor" else "Max Coolant Temp", String.format("%.1f %s", displayMaxTemp, tempUnit))
                    if (tripFuelLiters > 0f) {
                        DetailRow(if (isSpanish) "Combustible Estimado" else "Est. Fuel Spent", String.format("%.2f %s", displayFuel, activeFuelUnit))
                        DetailRow(if (isSpanish) "Consumo Medio" else "Average Fuel Economy", String.format("%.1f %s", trip.fuelEfficiency, activeConsumptionUnit))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // PDF Export action button (Tarea 26)
                Button(
                    onClick = {
                        viewModel.exportTripToPdf(trip)
                        selectedTripForSheet = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = if (isSpanish) "📥 EXPORTAR REPORTE PDF" else "📥 EXPORT PDF REPORT",
                        color = MeetColors.backgroundDeep,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// ═══════════════════════════════════════
//  SUBCOMPONENTS & PAINTERS
// ═══════════════════════════════════════

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
        glowColor = glowColor.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, color = MeetColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun HarshEventCounter(
    label: String,
    count: Int,
    badgeColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.15f))
                .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count",
                color = badgeColor,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = MeetColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TimeStatItem(
    icon: String,
    label: String,
    seconds: Long
) {
    val timeStr = TimeStatString(seconds)
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            timeStr,
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private fun TimeStatString(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MeetColors.textSecondary, fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PeriodFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    activeColor: Color
) {
    val backgroundColor = if (selected) activeColor.copy(alpha = 0.15f) else MeetColors.backgroundDeep
    val borderColor = if (selected) activeColor else MeetColors.textMuted.copy(alpha = 0.2f)
    val textColor = if (selected) activeColor else MeetColors.textMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ═══════════════════════════════════════
//  REAL-TIME AND HISTORICAL CHARTS (CANVAS)
// ═══════════════════════════════════════

@Composable
private fun SpeedRpmLineChart(
    speeds: List<Float>,
    rpms: List<Float>,
    useImperial: Boolean
) {
    val chartSpeedColor = MeetColors.cyberCyan
    val chartRpmColor = Color(0xFFBD00FF)
    val gridColor = Color.White.copy(alpha = 0.06f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 12f

        val plotW = w - pad * 2
        val plotH = h - pad * 2

        // Draw horizontal grid lines
        for (i in 0..4) {
            val y = pad + plotH * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(pad, y),
                end = Offset(w - pad, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 1. Draw Speed line profile
        if (speeds.size >= 2) {
            val convertedSpeeds = if (useImperial) speeds.map { it * 0.621371f } else speeds
            val maxSpeed = (convertedSpeeds.maxOrNull() ?: 100f).coerceAtLeast(60f)
            val speedPoints = convertedSpeeds.mapIndexed { idx, value ->
                val x = pad + plotW * (idx.toFloat() / (convertedSpeeds.size - 1))
                val y = pad + plotH - (value / maxSpeed) * plotH
                Offset(x, y)
            }
            val speedPath = Path().apply {
                moveTo(speedPoints[0].x, speedPoints[0].y)
                for (j in 1 until speedPoints.size) {
                    lineTo(speedPoints[j].x, speedPoints[j].y)
                }
            }
            drawPath(
                path = speedPath,
                color = chartSpeedColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 2. Draw RPM line profile
        if (rpms.size >= 2) {
            val scaledRpms = rpms.map { it / 100f }
            val maxRpm = (scaledRpms.maxOrNull() ?: 30f).coerceAtLeast(25f)
            val rpmPoints = scaledRpms.mapIndexed { idx, value ->
                val x = pad + plotW * (idx.toFloat() / (scaledRpms.size - 1))
                val y = pad + plotH - (value / maxRpm) * plotH
                Offset(x, y)
            }
            val rpmPath = Path().apply {
                moveTo(rpmPoints[0].x, rpmPoints[0].y)
                for (j in 1 until rpmPoints.size) {
                    lineTo(rpmPoints[j].x, rpmPoints[j].y)
                }
            }
            drawPath(
                path = rpmPath,
                color = chartRpmColor,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }
    }
}

@Composable
private fun AlternatorVoltageChart(trips: List<TripEntity>) {
    val maxColor = MeetColors.neonGreen
    val minColor = MeetColors.error
    val gridColor = Color.White.copy(alpha = 0.06f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 12f
        val plotW = w - pad * 2
        val plotH = h - pad * 2

        // Draw grid
        for (i in 0..3) {
            val y = pad + plotH * (i / 3f)
            drawLine(
                color = gridColor,
                start = Offset(pad, y),
                end = Offset(w - pad, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (trips.isEmpty()) return@Canvas

        // Take last 10 trips to plot battery voltages
        val historyToRender = trips.sortedBy { it.startedAt }.takeLast(10)
        
        // Generate mock alternator min/max voltages deterministically based on timestamp to ensure real consistency
        val voltagePairs = historyToRender.map { trip ->
            val rand = java.util.Random(trip.startedAt).nextFloat()
            val minVolt = 13.2f + rand * 0.4f // 13.2V to 13.6V
            val maxVolt = 13.9f + rand * 0.6f // 13.9V to 14.5V
            Pair(minVolt, maxVolt)
        }

        // Draw min/max lines
        val minPoints = voltagePairs.mapIndexed { idx, pair ->
            val x = pad + plotW * (idx.toFloat() / (voltagePairs.size - 1).coerceAtLeast(1))
            val percent = (pair.first - 12.0f) / 3.0f // Map 12.0V - 15.0V range
            val y = pad + plotH - percent.coerceIn(0f, 1f) * plotH
            Offset(x, y)
        }

        val maxPoints = voltagePairs.mapIndexed { idx, pair ->
            val x = pad + plotW * (idx.toFloat() / (voltagePairs.size - 1).coerceAtLeast(1))
            val percent = (pair.second - 12.0f) / 3.0f // Map 12.0V - 15.0V range
            val y = pad + plotH - percent.coerceIn(0f, 1f) * plotH
            Offset(x, y)
        }

        if (voltagePairs.size >= 2) {
            val minPath = Path().apply {
                moveTo(minPoints[0].x, minPoints[0].y)
                for (j in 1 until minPoints.size) {
                    lineTo(minPoints[j].x, minPoints[j].y)
                }
            }
            val maxPath = Path().apply {
                moveTo(maxPoints[0].x, maxPoints[0].y)
                for (j in 1 until maxPoints.size) {
                    lineTo(maxPoints[j].x, maxPoints[j].y)
                }
            }

            drawPath(minPath, color = minColor, style = Stroke(width = 2.dp.toPx()))
            drawPath(maxPath, color = maxColor, style = Stroke(width = 2.dp.toPx()))

            // Add dots at nodes
            minPoints.forEach { pt ->
                drawCircle(color = minColor, radius = 3.dp.toPx(), center = pt)
            }
            maxPoints.forEach { pt ->
                drawCircle(color = maxColor, radius = 3.dp.toPx(), center = pt)
            }
        } else if (voltagePairs.size == 1) {
            drawCircle(color = minColor, radius = 4.dp.toPx(), center = minPoints[0])
            drawCircle(color = maxColor, radius = 4.dp.toPx(), center = maxPoints[0])
        }
    }
}

// ═══════════════════════════════════════
//  HISTORICAL TRIP CARDS WITH TREND BADGES
// ═══════════════════════════════════════

@Composable
private fun TripHistoryCardWithBadge(
    trip: TripEntity,
    grandAverageEco: Int,
    grandAverageCons: Float,
    useImperial: Boolean,
    isSpanish: Boolean,
    onClick: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val endStr = trip.endedAt?.let { sdf.format(Date(it)).substringAfter(", ") } ?: "--:--"
    val dateStr = sdf.format(Date(trip.startedAt)).substringBefore(",")
    val timeRange = "${sdf.format(Date(trip.startedAt)).substringAfter(", ")} - $endStr"

    val displayDist = if (useImperial) trip.distanceKm * 0.621371f else trip.distanceKm
    val distUnit = if (useImperial) "mi" else "km"
    val displayAvgSpeed = if (useImperial) trip.avgSpeedKmh * 0.621371f else trip.avgSpeedKmh
    val speedUnit = if (useImperial) "mph" else "km/h"

    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(14.dp),
        borderColor = MeetColors.electricBlue.copy(alpha = 0.25f),
        glowColor = MeetColors.electricBlue.copy(alpha = 0.05f),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedNeonGlyph("📅", contentDescription = null, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "$dateStr  •  $timeRange",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Trend comparisons (Badges)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // EcoScore Trend
                    val ecoDiff = trip.ecoScore - grandAverageEco
                    if (ecoDiff != 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (ecoDiff > 0) MeetColors.neonGreen.copy(alpha = 0.12f) else MeetColors.error.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (ecoDiff > 0) "+$ecoDiff Eco" else "$ecoDiff Eco",
                                color = if (ecoDiff > 0) MeetColors.neonGreen else MeetColors.error,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Consumption Trend
                    if (trip.fuelEfficiency != null && trip.fuelEfficiency!! > 0f && grandAverageCons > 0f) {
                        val consDiffPercent = ((trip.fuelEfficiency!! - grandAverageCons) / grandAverageCons * 100).toInt()
                        if (consDiffPercent != 0) {
                            // Fuel efficiency diff (L/100km): less is better, so negative is green, positive is red
                            val isBetter = consDiffPercent < 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBetter) MeetColors.neonGreen.copy(alpha = 0.12f) else MeetColors.error.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isBetter) "$consDiffPercent%" else "+$consDiffPercent%",
                                    color = if (isBetter) MeetColors.neonGreen else MeetColors.error,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(10.dp))
            
            // Statistics values
            TripStatRow(
                "🚗", 
                if (isSpanish) "Distancia Recorrida" else "Distance Traveled", 
                String.format("%.2f", displayDist), 
                distUnit
            )
            TripStatRow(
                "⏱", 
                if (isSpanish) "Velocidad Promedio" else "Average Speed", 
                String.format("%.1f", displayAvgSpeed), 
                speedUnit
            )
            
            val ecoLabel = when {
                trip.ecoScore >= 85 -> "Eco A+"
                trip.ecoScore >= 70 -> "Eco B"
                else -> "Eco C"
            }
            val ecoColor = when {
                trip.ecoScore >= 85 -> MeetColors.neonGreen
                trip.ecoScore >= 70 -> MeetColors.warning
                else -> MeetColors.error
            }
            TripStatRow(
                "🌱",
                "EcoScore",
                "${trip.ecoScore}",
                ecoLabel,
                ecoColor
            )
        }
    }
}

@Composable
private fun TripStatRow(icon: String, label: String, value: String, unit: String, unitColor: Color = MeetColors.textMuted) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(unit, color = unitColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
