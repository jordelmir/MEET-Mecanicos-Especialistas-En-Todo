package com.elysium369.meet.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.data.local.entities.TripEntity
import com.elysium369.meet.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    trips: List<TripEntity>,
    isPremium: Boolean,
    onExportPdf: (TripEntity) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Viajes Eco",
                subtitle = "Telemetría y Eficiencia"
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Premium Cyber Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeetColors.cardBackground)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf("HISTORIAL", "ANÁLISIS FODA")
                tabs.forEachIndexed { index, tabName ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MeetColors.neonGreen else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabName,
                            color = if (isSelected) MeetColors.backgroundDeep else MeetColors.textSecondary,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            if (trips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = MeetColors.borderBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MeetColors.borderBlue.copy(alpha = 0.3f)),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AnimatedNeonGlyph("🚗", contentDescription = null, fontSize = 36.sp)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "Sin Viajes Reales Registrados",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Los viajes se registran automáticamente cuando el adaptador OBD2 detecta motor encendido, vehículo en marcha y una sesión real activa.",
                            color = MeetColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                        EliteCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = MeetColors.borderBlue.copy(alpha = 0.12f),
                            borderColor = MeetColors.borderBlue.copy(alpha = 0.28f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "CAPTURA REAL",
                                    color = MeetColors.borderBlue,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "1. Conecta el adaptador OBD.\n2. Enciende el vehículo.\n3. Conduce con la sesión activa.\n4. Elysium Vanguard guardará el trayecto automáticamente.",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            } else {
                if (selectedTab == 0) {
                    // TAB 0: Historial de Viajes
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header dashboard showing averages
                        item {
                            val avgScore = trips.map { it.ecoScore }.average().toInt()
                            val totalKm = trips.map { it.distanceKm }.sum()
                            val totalDurationMin = trips.map { it.durationSeconds }.sum() / 60
                            
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = MeetColors.neonGreen.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "DESEMPEÑO PROMEDIO",
                                            color = MeetColors.textSecondary,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Conducción Eficiente",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 20.sp
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column {
                                                Text("TOTAL KM", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text("${String.format(Locale.US, "%.1f", totalKm)} km", color = MeetColors.neonGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Column {
                                                Text("TIEMPO", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text("${totalDurationMin} min", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    
                                    EcoScoreGauge(avgScore)
                                }
                            }
                        }
                        
                        if (!isPremium) {
                            item {
                                EliteCard(
                                    glowColor = MeetColors.borderBlue.copy(alpha = 0.15f),
                                    borderColor = MeetColors.borderBlue.copy(alpha = 0.25f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = MeetColors.borderBlue.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(32.dp),
                                            border = BorderStroke(1.dp, MeetColors.borderBlue.copy(alpha = 0.3f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                AnimatedNeonGlyph("⭐", contentDescription = null, fontSize = 16.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = "Funciones Pro Bloqueadas",
                                                color = MeetColors.borderBlue,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "La exportación de reportes PDF detallados, el consumo L/100km y el desglose de velocidad máxima requieren suscripción.", 
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        items(trips.sortedByDescending { it.startedAt }) { trip ->
                            TripCard(trip, isPremium, onExportPdf)
                        }
                        
                        item {
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                } else {
                    // TAB 1: Análisis FODA de Conducción Ecológica
                    val avgScore = trips.map { it.ecoScore }.average().toInt()
                    val maxSpeed = trips.map { it.maxSpeedKmh }.maxOrNull() ?: 0f
                    val maxRpm = trips.map { it.maxRpm }.maxOrNull() ?: 0f
                    val avgRpm = trips.map { it.avgRpm }.average().toFloat()
                    val maxTemp = trips.map { it.maxTempC }.maxOrNull() ?: 0f
                    val avgFuel = trips.mapNotNull { it.fuelEfficiency }.average().toFloat()

                    val strengthsList = remember(avgScore, maxSpeed, avgRpm, maxTemp, avgFuel) {
                        val list = mutableListOf<String>()
                        if (avgScore >= 80) {
                            list.add("Eficiencia ecológica destacada (Promedio: $avgScore/100)")
                        }
                        if (maxSpeed <= 110f && maxSpeed > 0f) {
                            list.add("Control aerodinámico: Velocidades por debajo de 110 km/h")
                        }
                        if (avgRpm < 2400f && avgRpm > 0f) {
                            list.add("Transiciones eficientes: Revoluciones bajas (<2400 RPM)")
                        }
                        if (maxTemp in 80.0f..98.0f) {
                            list.add("Termodinámica del motor estable (${maxTemp.toInt()}°C)")
                        }
                        if (avgFuel > 0f && avgFuel <= 7.0f) {
                            list.add("Excelente rendimiento: ${String.format(Locale.US, "%.1f", avgFuel)} L/100km prom.")
                        }
                        if (list.isEmpty()) {
                            list.add("Estabilidad base en el patrón de manejo")
                        }
                        list
                    }

                    val opportunitiesList = remember(avgScore, avgFuel) {
                        val list = mutableListOf<String>()
                        if (avgScore < 90) {
                            list.add("Freno de motor: Soltar acelerador anticipadamente para aprovechar inercia")
                        }
                        list.add("Control de crucero: Usar en autopistas planas para mantener RPM constantes")
                        list.add("Presión de llantas: Verificar semanalmente (ahorra hasta 4% de combustible)")
                        if (avgFuel > 8.0f) {
                            list.add("Acelerar gradualmente en arranques desde semáforos")
                        }
                        list
                    }

                    val weaknessesList = remember(avgScore, maxRpm, avgFuel) {
                        val list = mutableListOf<String>()
                        if (avgScore < 70) {
                            list.add("Puntaje Eco crítico ($avgScore/100): Hábitos de conducción muy bruscos")
                        }
                        if (maxRpm > 3800f) {
                            list.add("Revoluciones excesivas detectadas (${maxRpm.toInt()} RPM Máx)")
                        }
                        if (avgFuel > 9.0f) {
                            list.add("Consumo elevado de combustible (${String.format(Locale.US, "%.1f", avgFuel)} L/100km)")
                        }
                        if (list.isEmpty()) {
                            list.add("No se detectan debilidades severas en los viajes actuales")
                        }
                        list
                    }

                    val threatsList = remember(maxSpeed, maxTemp) {
                        val list = mutableListOf<String>()
                        if (maxSpeed > 120f) {
                            list.add("Exceso de velocidad (${maxSpeed.toInt()} km/h): Incrementa exponencialmente el consumo")
                        }
                        if (maxTemp > 102f) {
                            list.add("Estrés térmico de motor (${maxTemp.toInt()}°C): Riesgo de sobrecalentamiento prematuro")
                        }
                        if (list.isEmpty()) {
                            list.add("Parámetros dinámicos de seguridad dentro de límites seguros")
                        }
                        list
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            EcoFodaQuadrant(
                                title = "FORTALEZAS",
                                subtitle = "Estilo de Conducción Óptimo",
                                icon = "✓",
                                color = MeetColors.success,
                                items = strengthsList
                            )
                        }
                        item {
                            EcoFodaQuadrant(
                                title = "OPORTUNIDADES",
                                subtitle = "Acciones para Maximizar Kilometraje",
                                icon = "★",
                                color = MeetColors.cyberCyan,
                                items = opportunitiesList
                            )
                        }
                        item {
                            EcoFodaQuadrant(
                                title = "DEBILIDADES",
                                subtitle = "Puntos Críticos de Consumo",
                                icon = "⚠",
                                color = MeetColors.warning,
                                items = weaknessesList
                            )
                        }
                        item {
                            EcoFodaQuadrant(
                                title = "AMENAZAS",
                                subtitle = "Riesgos de Desgaste y Multas",
                                icon = "⚡",
                                color = MeetColors.error,
                                items = threatsList
                            )
                        }
                        item {
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripCard(trip: TripEntity, isPremium: Boolean, onExportPdf: (TripEntity) -> Unit) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val durationMin = trip.durationSeconds / 60
    val scoreColor = when {
        trip.ecoScore >= 85 -> MeetColors.success
        trip.ecoScore >= 70 -> MeetColors.warning
        else -> MeetColors.error
    }

    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = scoreColor.copy(alpha = 0.15f),
        borderColor = scoreColor.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sdf.format(Date(trip.startedAt)),
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ECO SCORE",
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${trip.ecoScore}",
                        color = scoreColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TripStatBox("Duración", "$durationMin min")
                TripStatBox("Distancia", "${String.format(Locale.US, "%.1f", trip.distanceKm)} km")
                if (isPremium) {
                    TripStatBox("Vel. Máx", "${trip.maxSpeedKmh.toInt()} km/h")
                    TripStatBox("Temp. Máx", "${trip.maxTempC.toInt()} °C")
                } else {
                    TripStatBox("Vel. Máx", "PRO")
                    TripStatBox("Temp. Máx", "PRO")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Score visual bar indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MeetColors.cardBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(trip.ecoScore.toFloat() / 100f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(scoreColor)
                )
            }
            
            if (isPremium) {
                Spacer(modifier = Modifier.height(16.dp))
                EliteButton(
                    onClick = { onExportPdf(trip) },
                    text = "📄 EXPORTAR REPORTE PDF",
                    color = MeetColors.neonGreen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun TripStatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EcoScoreGauge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val gaugeColor = when {
        score >= 85 -> MeetColors.success
        score >= 70 -> MeetColors.warning
        else -> MeetColors.error
    }

    Box(
        modifier = modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)
            
            // Draw background circle track
            drawCircle(
                color = MeetColors.cardBackground,
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth
                )
            )
            
            // Draw progress arc
            drawArc(
                color = gaugeColor,
                startAngle = -90f,
                sweepAngle = 360f * (score.toFloat() / 100f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth
                )
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
            Text(
                text = "ECO AVG",
                color = MeetColors.textSecondary,
                fontWeight = FontWeight.Black,
                fontSize = 7.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun EcoFodaQuadrant(
    title: String,
    subtitle: String,
    icon: String,
    color: Color,
    items: List<String>,
    modifier: Modifier = Modifier
) {
    EliteCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = color.copy(alpha = 0.2f),
        borderColor = color.copy(alpha = 0.3f),
        backgroundColor = MeetColors.cardBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(32.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedNeonGlyph(icon, contentDescription = null, tint = color, fontSize = 16.sp)
                    }
                }
                Column {
                    Text(title, color = color, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                    Text(subtitle, color = MeetColors.textSecondary, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text(
                    "Sin registros en esta categoría",
                    color = MeetColors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("•", color = color, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(item, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
