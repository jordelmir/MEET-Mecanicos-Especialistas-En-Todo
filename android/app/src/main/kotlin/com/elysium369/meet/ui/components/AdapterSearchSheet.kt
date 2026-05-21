package com.elysium369.meet.ui.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdapterSearchSheet(
    isFullScreen: Boolean = true,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter

    // ═══ Mutable device list — supports manual clearing & re-scan ═══
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val pairedDevices = remember(refreshTrigger) {
        try {
            bluetoothAdapter?.bondedDevices?.map { Pair(it.name ?: "Unknown Device", it.address) } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    // ═══ BLE Scanning Simulation state ═══
    val simulatedBleDevices = listOf(
        Triple("OBDLink CX BLE", "12:34:56:AB:CD:EF", -62),
        Triple("Veepeak OBDCheck BLE+", "AA:BB:CC:DD:EE:FF", -74),
        Triple("V-Link BLE Adapter", "98:76:54:32:10:FE", -83),
        Triple("Carista OBD2 Smart", "24:68:1A:3C:5E:7D", -90)
    )
    var discoveredCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshTrigger, selectedTab) {
        if (selectedTab == 1) {
            discoveredCount = 0
            kotlinx.coroutines.delay(600)
            discoveredCount = 1
            kotlinx.coroutines.delay(1000)
            discoveredCount = 2
            kotlinx.coroutines.delay(900)
            discoveredCount = 3
            kotlinx.coroutines.delay(800)
            discoveredCount = 4
        }
    }

    // ═══ Radar Sweep Animation Spec ═══
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MeetColors.backgroundDeep,
                            MeetColors.backgroundDark
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CONECTAR ADAPTADOR OBD2",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "VIRTUAL DIAGNOSTIC INTERFACE",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MeetColors.cardBackground, RoundedCornerShape(8.dp))
                        .border(1.dp, MeetColors.borderBlue, RoundedCornerShape(8.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cyberpunk Custom Segmented Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeetColors.cardBackground, RoundedCornerShape(12.dp))
                    .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                val tabs = listOf(
                    Triple(0, "BT Clásico", MeetColors.hotMagenta),
                    Triple(1, "BLE (Smart)", MeetColors.electricBlue),
                    Triple(2, "Wi-Fi Red", MeetColors.cyberCyan)
                )
                tabs.forEach { (index, label, color) ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
                            .then(
                                if (isSelected) Modifier.border(1.dp, color, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = index }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (index) {
                                    0 -> "📡 "
                                    1 -> "⚡ "
                                    else -> "📶 "
                                } + label,
                                color = if (isSelected) Color.White else MeetColors.textSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Contents
            when (selectedTab) {
                0 -> {
                    // BT Clásico
                    val isBtEnabled = bluetoothAdapter?.isEnabled == true
                    if (!isBtEnabled) {
                        BluetoothDisabledView(context)
                    } else if (pairedDevices.isEmpty()) {
                        NoPairedDevicesView(context)
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${pairedDevices.size} DISPOSITIVOS VINCULADOS",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .background(MeetColors.cardBackground, RoundedCornerShape(6.dp))
                                    .border(1.dp, MeetColors.hotMagenta.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .clickable { refreshTrigger++ }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⟳ RE-ESCANEAR",
                                    color = MeetColors.hotMagenta,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(pairedDevices) { index, device ->
                                AnimatedEntrance(index = index) {
                                    val nameUpper = device.first.uppercase()
                                    val isSuggested = nameUpper.contains("OBD") || nameUpper.contains("ELM") || nameUpper.contains("LINK")
                                    EliteCard(
                                        backgroundColor = MeetColors.cardBackground,
                                        borderColor = if (isSuggested) MeetColors.hotMagenta.copy(alpha = 0.4f) else MeetColors.borderSubtle,
                                        glowColor = if (isSuggested) MeetColors.hotMagenta else null,
                                        onClick = {
                                            onConnect(device.first, device.second)
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(
                                                        if (isSuggested) MeetColors.hotMagenta.copy(alpha = 0.1f)
                                                        else MeetColors.backgroundDeep,
                                                        RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (isSuggested) "🚗" else "🔌",
                                                    fontSize = 20.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = device.first,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp
                                                    )
                                                    if (isSuggested) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    MeetColors.hotMagenta.copy(alpha = 0.15f),
                                                                    RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "RECOMENDADO",
                                                                color = MeetColors.hotMagenta,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Black
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = device.second,
                                                    color = MeetColors.textSecondary,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Text(
                                                text = "CONECTAR ›",
                                                color = if (isSuggested) MeetColors.hotMagenta else MeetColors.textSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // BLE (Bluetooth Low Energy)
                    val isBtEnabled = bluetoothAdapter?.isEnabled == true
                    if (!isBtEnabled) {
                        BluetoothDisabledView(context)
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            // Canvas Radar Scanner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(160.dp)) {
                                    val center = Offset(size.width / 2, size.height / 2)
                                    val maxRadius = size.minDimension / 2

                                    // Concentric HUD Circles
                                    drawCircle(
                                        color = MeetColors.electricBlue.copy(alpha = 0.1f),
                                        radius = maxRadius * 0.35f,
                                        style = Stroke(1.dp.toPx())
                                    )
                                    drawCircle(
                                        color = MeetColors.electricBlue.copy(alpha = 0.15f),
                                        radius = maxRadius * 0.7f,
                                        style = Stroke(1.dp.toPx())
                                    )
                                    drawCircle(
                                        color = MeetColors.electricBlue.copy(alpha = 0.25f),
                                        radius = maxRadius,
                                        style = Stroke(1.5f.dp.toPx())
                                    )

                                    // Reticle Crosshairs
                                    drawLine(
                                        color = MeetColors.electricBlue.copy(alpha = 0.2f),
                                        start = Offset(center.x - maxRadius, center.y),
                                        end = Offset(center.x + maxRadius, center.y),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    drawLine(
                                        color = MeetColors.electricBlue.copy(alpha = 0.2f),
                                        start = Offset(center.x, center.y - maxRadius),
                                        end = Offset(center.x, center.y + maxRadius),
                                        strokeWidth = 1.dp.toPx()
                                    )

                                    // Rotating radar sweep
                                    val angleRad = Math.toRadians(sweepAngle.toDouble())
                                    val sweepEnd = Offset(
                                        (center.x + maxRadius * cos(angleRad)).toFloat(),
                                        (center.y + maxRadius * sin(angleRad)).toFloat()
                                    )
                                    drawLine(
                                        color = MeetColors.electricBlue,
                                        start = center,
                                        end = sweepEnd,
                                        strokeWidth = 2.dp.toPx()
                                    )

                                    // Radar trail sweep
                                    for (i in 1..4) {
                                        val trailAngle = sweepAngle - (i * 10f)
                                        val trailAngleRad = Math.toRadians(trailAngle.toDouble())
                                        val trailEnd = Offset(
                                            (center.x + maxRadius * cos(trailAngleRad)).toFloat(),
                                            (center.y + maxRadius * sin(trailAngleRad)).toFloat()
                                        )
                                        drawLine(
                                            color = MeetColors.electricBlue.copy(alpha = 0.7f / (i + 1)),
                                            start = center,
                                            end = trailEnd,
                                            strokeWidth = 1.5f.dp.toPx()
                                        )
                                    }
                                }

                                Text(
                                    text = "SCANNING",
                                    color = MeetColors.electricBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "ADAPTADORES BLE DISPONIBLES",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (discoveredCount == 0) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Iniciando escaneo del bus BLE...",
                                        color = MeetColors.textMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    itemsIndexed(simulatedBleDevices.take(discoveredCount)) { index, device ->
                                        AnimatedEntrance(index = index) {
                                            EliteCard(
                                                backgroundColor = MeetColors.cardBackground,
                                                borderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                                                onClick = {
                                                    onConnect(device.first, device.second)
                                                    onDismiss()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .background(
                                                                MeetColors.electricBlue.copy(alpha = 0.1f),
                                                                RoundedCornerShape(8.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("⚡", fontSize = 18.sp)
                                                    }
                                                    Spacer(modifier = Modifier.width(14.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = device.first,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = device.second,
                                                            color = MeetColors.textSecondary,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 11.sp
                                                        )
                                                    }

                                                    // RSSI custom bar graph
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        val rssi = device.third
                                                        val barCount = when {
                                                            rssi > -65 -> 4
                                                            rssi > -75 -> 3
                                                            rssi > -85 -> 2
                                                            else -> 1
                                                        }
                                                        for (i in 1..4) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(3.dp)
                                                                    .height((6 + (i * 3)).dp)
                                                                    .background(
                                                                        if (i <= barCount) MeetColors.electricBlue
                                                                        else MeetColors.textMuted
                                                                    )
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "$rssi dBm",
                                                            color = MeetColors.textSecondary,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Wi-Fi Red
                    var wifiMode by remember { mutableStateOf("AUTO") }
                    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        // Segmented WiFi Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeetColors.cardBackground, RoundedCornerShape(12.dp))
                                  .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                                .padding(3.dp)
                        ) {
                            listOf("AUTO" to "Auto-Detección", "MANUAL" to "Config. Manual").forEach { (mode, label) ->
                                val isSelected = wifiMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isSelected) MeetColors.neonGreen.copy(alpha = 0.15f) else Color.Transparent)
                                        .then(
                                            if (isSelected) Modifier.border(1.dp, MeetColors.neonGreen, RoundedCornerShape(9.dp))
                                            else Modifier
                                        )
                                        .clickable { wifiMode = mode }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else MeetColors.textSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (wifiMode == "AUTO") {
                            // Technical Console Board
                            EliteCard(
                                backgroundColor = MeetColors.backgroundDeep,
                                borderColor = MeetColors.neonGreen.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MeetColors.neonGreen, RoundedCornerShape(50))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "SYSTEM OBD2 WLAN BOARD",
                                            color = MeetColors.neonGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val terminalLines = listOf(
                                        "SSID Target : WiFi_OBDII / ELM327",
                                        "IP Target   : 192.168.0.10",
                                        "Port Target : 35000 (TCP Socket)",
                                        "Protocol    : Raw ELM327 AutoNegotiation",
                                        "Status      : READY FOR HANDSHAKE"
                                    )
                                    terminalLines.forEach { line ->
                                        Text(
                                            text = "> $line",
                                            color = MeetColors.textSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Asegúrate de conectarte primero a la red Wi-Fi del adaptador OBD2 en los Ajustes del sistema.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            EliteButton(
                                text = "CONEXIÓN AUTOMÁTICA",
                                onClick = {
                                    onConnect("WiFi OBD (Auto)", "192.168.0.10:35000")
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Manual wifi inputs
                            var ip by remember { mutableStateOf("192.168.0.10") }
                            var port by remember { mutableStateOf("35000") }

                            val ipValid = remember(ip) {
                                ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))
                            }
                            val portValid = remember(port) {
                                port.toIntOrNull() != null && port.toInt() in 1..65535
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                CustomObdTextField(
                                    value = ip,
                                    onValueChange = { ip = it },
                                    label = "Dirección IP del Adaptador",
                                    placeholder = "ej. 192.168.0.10",
                                    keyboardType = KeyboardType.Number
                                )

                                CustomObdTextField(
                                    value = port,
                                    onValueChange = { port = it },
                                    label = "Puerto TCP",
                                    placeholder = "ej. 35000",
                                    keyboardType = KeyboardType.Number
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            EliteButton(
                                text = "GUARDAR Y CONECTAR",
                                onClick = {
                                    onConnect("WiFi OBD (Manual)", "$ip:$port")
                                    onDismiss()
                                },
                                isEnabled = ipValid && portValid,
                                color = if (ipValid && portValid) MeetColors.neonGreen else MeetColors.textMuted,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (isFullScreen) {
        Surface(
            color = MeetColors.backgroundDeep,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MeetColors.backgroundDark
        ) {
            content()
        }
    }
}

@Composable
fun BluetoothDisabledView(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(MeetColors.error.copy(alpha = 0.1f), RoundedCornerShape(50))
                .border(1.dp, MeetColors.error, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text("⚠️", fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "BLUETOOTH DESACTIVADO",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Es necesario habilitar Bluetooth para sincronizar con el adaptador OBD2 clásico.",
            color = MeetColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        EliteOutlinedButton(
            text = "ABRIR AJUSTES DE BLUETOOTH",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            color = MeetColors.error,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun NoPairedDevicesView(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(MeetColors.hotMagenta.copy(alpha = 0.1f), RoundedCornerShape(50))
                .border(1.dp, MeetColors.hotMagenta, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text("🔌", fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SIN DISPOSITIVOS VINCULADOS",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Primero debes vincular el adaptador ELM327 en la configuración de Bluetooth de tu teléfono móvil.",
            color = MeetColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        EliteOutlinedButton(
            text = "VINCULAR NUEVO DISPOSITIVO",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            color = MeetColors.hotMagenta,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CustomObdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            color = MeetColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeetColors.cardBackground, RoundedCornerShape(10.dp))
                .border(1.dp, MeetColors.borderBlue, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                cursorBrush = Brush.verticalGradient(listOf(MeetColors.neonGreen, MeetColors.neonGreen))
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MeetColors.textMuted,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
