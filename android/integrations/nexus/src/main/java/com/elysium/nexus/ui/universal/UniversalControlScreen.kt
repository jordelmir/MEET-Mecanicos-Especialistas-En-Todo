package com.elysium.nexus.ui.universal

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.elysium.nexus.core.motion.AirMouseController
import com.elysium.nexus.core.motion.AndroidMotionSensorSource
import com.elysium.nexus.core.motion.MotionSensorSource
import com.elysium.nexus.core.transport.hid.BluetoothHidTransport
import com.elysium.nexus.core.transport.hid.HidConnectionState
import com.elysium.nexus.core.transport.hid.HidDescriptors
import com.elysium.nexus.core.transport.hid.HidReports
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonFab
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Universal Control screen — Phase ULT.5.
 *
 * The "works for any device" experience:
 *
 *  1. **Pair** the phone with the host from
 *     the host's Bluetooth settings (Mac,
 *     Windows, Android TV, Linux…). The phone
 *     shows up as "Elysium Nexus Universal
 *     Remote".
 *  2. **Pick** the paired device from the
 *     list on this screen.
 *  3. **Tap** "Conectar" — the HID channel
 *     opens. The host now sees the phone as a
 *     keyboard + mouse.
 *  4. **Use** the trackpad + keyboard on this
 *     screen to control the host. Every
 *     gesture becomes a HID report.
 *
 * The trackpad is a real multi-touch surface
 * (`awaitEachGesture`):
 *
 *  - 1 finger drag → mouse move
 *  - 1 finger tap → left click
 *  - 2 fingers drag → scroll
 *  - 2 fingers tap → right click
 *  - 2 fingers pinch → scroll (zoom in apps
 *    that bind Cmd+Scroll to zoom)
 *  - 3 fingers swipe ↑/↓/←/→ → media keys
 *    (Volume Up/Down, Prev, Next) and home
 *    (Esc)
 *
 * The keyboard is the system IME — same
 * pattern as the Mac control surface.
 */
@SuppressLint("MissingPermission")
@Composable
fun UniversalControlScreen(
    onBack: () -> Unit,
    posture: com.elysium.nexus.core.posture.Posture = com.elysium.nexus.core.posture.Posture.UNKNOWN,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transport = remember { BluetoothHidTransport(context) }
    val state: HidConnectionState by transport.hidState.collectAsStateLifecycleSafe()
    // Phase ULT.7 — Air Mouse mode. The user
    // can switch between trackpad and air
    // mouse. The air mouse uses the phone's
    // gyroscope to drive the cursor; the
    // trackpad is touch-driven (the default).
    var airMouseEnabled by remember { mutableStateOf(false) }
    val motionSource: MotionSensorSource = remember { AndroidMotionSensorSource(context) }
    val airMouse = remember { AirMouseController(sensitivity = 800f, invertY = true) }
    // When air-mouse mode is enabled, drive
    // the controller from the IMU + flush
    // deltas to the host at 60 Hz.
    LaunchedEffect(airMouseEnabled, state) {
        if (!airMouseEnabled) return@LaunchedEffect
        if (state !is HidConnectionState.Connected) return@LaunchedEffect
        motionSource.samples().collect { sample ->
            airMouse.submit(sample)
        }
    }
    LaunchedEffect(airMouseEnabled, state) {
        if (!airMouseEnabled) return@LaunchedEffect
        if (state !is HidConnectionState.Connected) return@LaunchedEffect
        while (true) {
            val delta = airMouse.consume()
            if (delta.dx != 0 || delta.dy != 0) {
                transport.sendMouseReport(
                    HidReports.mouse(0, delta.dx, delta.dy, 0)
                )
            }
            delay(16) // ~60 Hz
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            transport.releaseAllKeys()
            transport.disconnectHid()
            motionSource.close()
        }
    }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var showHelp by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var hasPerms by remember { mutableStateOf(checkBluetoothPermissions(context)) }
    var bluetoothOn by remember { mutableStateOf(isBluetoothOn(context)) }

    // Permission launcher for Android 12+.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPerms = results.values.all { it }
        if (hasPerms) {
            scope.launch { transport.start() }
        }
    }

    // Start the transport when permissions are
    // granted.
    LaunchedEffect(hasPerms) {
        if (hasPerms && bluetoothOn) {
            transport.startHid()
        }
    }

    // Refresh the paired devices list every
    // time the screen comes into view, or when
    // the state transitions to Registered
    // (a new device might have been paired).
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            if (hasPerms) {
                pairedDevices = transport.pairedHosts()
                bluetoothOn = isBluetoothOn(context)
            }
        }
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = onBack,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NeonStatusPill(
                        label = when (state) {
                            is HidConnectionState.Connected -> "Conectado"
                            is HidConnectionState.Registered -> "Listo"
                            is HidConnectionState.Connecting -> "Conectando…"
                            is HidConnectionState.Registering -> "Registrando…"
                            is HidConnectionState.Disconnecting -> "Desconectando"
                            is HidConnectionState.Error -> "Error"
                            HidConnectionState.Idle -> "Apagado"
                            else -> "—"
                        },
                        color = when (state) {
                            is HidConnectionState.Connected -> ElysiumColors.NeonGreen
                            is HidConnectionState.Registered -> ElysiumColors.NeonCyan
                            is HidConnectionState.Connecting,
                            is HidConnectionState.Registering -> ElysiumColors.NeonOrange
                            is HidConnectionState.Error -> ElysiumColors.NeonMagenta
                            else -> ElysiumColors.OnSurfaceMuted
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = "Ayuda",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // === HERO CARD ===
            when (val s = state) {
                is HidConnectionState.Connected -> {
                    ConnectedHero(
                        deviceName = s.device.name ?: s.device.address,
                        deviceAddress = s.device.address,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = info.sidePadding, vertical = 4.dp)
                    )
                }
                else -> {
                    SetupHero(
                        hasPerms = hasPerms,
                        bluetoothOn = bluetoothOn,
                        state = state,
                        onEnableBluetooth = {
                            if (!hasPerms) {
                                requestBluetoothPermissions(context, permissionLauncher)
                            } else {
                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = info.sidePadding, vertical = 4.dp)
                    )
                }
            }
            // === PAIRED DEVICES LIST ===
            if (state !is HidConnectionState.Connected) {
                PairedDevicesSection(
                    devices = pairedDevices,
                    onConnect = { device ->
                        scope.launch {
                            transport.connectTo(device)
                        }
                    },
                    onRefresh = {
                        pairedDevices = transport.pairedHosts()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
            }
            // === TRACKPAD + KEYBOARD (only when connected) ===
            if (state is HidConnectionState.Connected) {
                ConnectedControls(
                    transport = transport,
                    showKeyboard = showKeyboard,
                    onToggleKeyboard = { showKeyboard = !showKeyboard },
                    airMouseEnabled = airMouseEnabled,
                    onToggleAirMouse = { airMouseEnabled = !airMouseEnabled },
                    onRecenter = { airMouse.reset() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 4.dp)
                )
            }
            // === HOW TO PAIR INSTRUCTIONS ===
            if (state !is HidConnectionState.Connected) {
                PairingInstructions(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — Universal Remote",
            whatIsThis = "Esta pantalla convierte tu teléfono en un control remoto " +
                "Bluetooth universal. Funciona con cualquier dispositivo que " +
                "acepte un teclado o ratón Bluetooth — Mac, Windows, Linux, " +
                "Android TV, smart TVs, Raspberry Pi, y más. No necesitas " +
                "instalar nada en el otro dispositivo.",
            howToUse = listOf(
                "En tu Mac/PC/TV: ve a Configuración → Bluetooth.",
                "Busca 'Elysium Nexus Universal Remote' y emparéjalo.",
                "Vuelve aquí, selecciona el dispositivo y toca Conectar.",
                "¡Listo! Tu teléfono ya controla el cursor y el teclado."
            ),
            tip = "Si no ves el dispositivo, asegúrate de que el Bluetooth esté " +
                "encendido y que el otro equipo esté en modo visible.",
            onDismiss = { showHelp = false }
        )
    }
}

// === HERO STATES ===

@Composable
private fun SetupHero(
    hasPerms: Boolean,
    bluetoothOn: Boolean,
    state: HidConnectionState,
    onEnableBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = when {
            !hasPerms -> ElysiumColors.NeonOrange
            !bluetoothOn -> ElysiumColors.NeonOrange
            state is HidConnectionState.Error -> ElysiumColors.NeonMagenta
            else -> ElysiumColors.NeonCyan
        },
        cornerRadius = 20.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                when {
                    !hasPerms -> Icons.Filled.BluetoothDisabled
                    !bluetoothOn -> Icons.Filled.BluetoothDisabled
                    state is HidConnectionState.Error -> Icons.Filled.BluetoothDisabled
                    else -> Icons.Filled.Bluetooth
                },
                contentDescription = null,
                tint = when {
                    !hasPerms -> ElysiumColors.NeonOrange
                    !bluetoothOn -> ElysiumColors.NeonOrange
                    state is HidConnectionState.Error -> ElysiumColors.NeonMagenta
                    else -> ElysiumColors.NeonCyan
                },
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = when {
                    !hasPerms -> "Permisos requeridos"
                    !bluetoothOn -> "Enciende Bluetooth"
                    state is HidConnectionState.Registering -> "Registrando…"
                    state is HidConnectionState.Error -> "Error"
                    state is HidConnectionState.Idle -> "Iniciando…"
                    else -> "Listo para emparejar"
                },
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = when {
                    !hasPerms -> "Necesitamos permiso de Bluetooth para conectar con tu dispositivo."
                    !bluetoothOn -> "Activa el Bluetooth en Configuración → Bluetooth."
                    state is HidConnectionState.Registering -> "Inicializando el perfil HID del sistema…"
                    state is HidConnectionState.Error -> (state as HidConnectionState.Error).reason
                    else -> "Selecciona un dispositivo emparejado abajo para empezar."
                },
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                color = ElysiumColors.OnSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!hasPerms || !bluetoothOn) {
                NeonChip(
                    label = if (!hasPerms) "Conceder permiso" else "Activar Bluetooth",
                    onClick = onEnableBluetooth,
                    accent = ElysiumColors.NeonCyan
                )
            }
        }
    }
}

@Composable
private fun ConnectedHero(
    deviceName: String,
    deviceAddress: String,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonGreen,
        cornerRadius = 20.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ElysiumColors.NeonGreen.copy(alpha = 0.2f))
                    .border(2.dp, ElysiumColors.NeonGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.BluetoothConnected,
                    contentDescription = null,
                    tint = ElysiumColors.NeonGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Conectado a",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = ElysiumColors.OnSurfaceMuted
                )
                Text(
                    text = deviceName,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = ElysiumColors.NeonGreen
                )
                Text(
                    text = deviceAddress,
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    color = ElysiumColors.OnSurfaceMuted
                )
            }
        }
    }
}

// === PAIRED DEVICES LIST ===

@SuppressLint("MissingPermission")
@Composable
private fun PairedDevicesSection(
    devices: List<BluetoothDevice>,
    onConnect: (BluetoothDevice) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dispositivos emparejados",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.NeonCyan
                )
                NeonChip(
                    label = "Refrescar",
                    onClick = onRefresh,
                    accent = ElysiumColors.OnSurfaceVariant,
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
                )
            }
            if (devices.isEmpty()) {
                Text(
                    text = "No hay dispositivos emparejados. Empareja tu " +
                        "Mac/PC/TV desde la configuración Bluetooth del otro " +
                        "equipo (busca 'Elysium Nexus Universal Remote').",
                    style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                    color = ElysiumColors.OnSurfaceMuted
                )
            } else {
                devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        onConnect = { onConnect(device) }
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(
    device: BluetoothDevice,
    onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ElysiumColors.Surface.copy(alpha = 0.4f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = ElysiumColors.NeonCyan,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: "Sin nombre",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = device.address,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = ElysiumColors.OnSurfaceMuted
            )
        }
        NeonChip(
            label = "Conectar",
            onClick = onConnect,
            accent = ElysiumColors.NeonGreen
        )
    }
}

@Composable
private fun PairingInstructions(modifier: Modifier = Modifier) {
    NeonCard(
        modifier = modifier,
        accent = ElysiumColors.NeonPurple,
        cornerRadius = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "¿Cómo emparejar?",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.NeonPurple
            )
            val steps = listOf(
                "Mac" to "Preferencias del Sistema → Bluetooth → Emparejar nuevo dispositivo",
                "Windows" to "Configuración → Bluetooth y dispositivos → Agregar dispositivo",
                "Linux" to "Configuración Bluetooth de tu escritorio (Gnome/KDE)",
                "Android TV" to "Configuración → Mandos y accesorios → Agregar accesorio",
                "Smart TV" to "Configuración → Bluetooth → Buscar dispositivos"
            )
            steps.forEach { (platform, instr) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        style = TextStyle(fontSize = 12.sp, color = ElysiumColors.NeonCyan)
                    )
                    Text(
                        text = "$platform: ",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = ElysiumColors.OnSurface
                    )
                    Text(
                        text = instr,
                        style = TextStyle(fontSize = 12.sp),
                        color = ElysiumColors.OnSurfaceMuted
                    )
                }
            }
            Text(
                text = "Busca 'Elysium Nexus Universal Remote' y emparéjalo. " +
                    "Aparecerá en la lista de arriba. Toca Conectar.",
                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                color = ElysiumColors.OnSurfaceMuted
            )
        }
    }
}

// === CONNECTED: TRACKPAD + KEYBOARD ===

@Composable
private fun ConnectedControls(
    transport: BluetoothHidTransport,
    showKeyboard: Boolean,
    onToggleKeyboard: () -> Unit,
    airMouseEnabled: Boolean,
    onToggleAirMouse: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current

    Box(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The trackpad OR the air-mouse surface.
            if (airMouseEnabled) {
                AirMouseSurface(
                    onRecenter = onRecenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            } else {
                val trackpadState = remember { TrackpadState() }
                UniversalTrackpad(
                    transport = transport,
                    state = trackpadState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
            }

            // Dedicated Mouse Clic Izquierdo / Clic Derecho buttons (matching USB-C screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Button(
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        scope.launch {
                            transport.sendMouseReport(HidReports.mouse(HidDescriptors.MouseButton.LEFT, 0, 0))
                            delay(50)
                            transport.sendMouseReport(HidReports.mouse(0, 0, 0))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ElysiumColors.Surface
                    )
                ) {
                    Text(
                        "CLIC IZQUIERDO",
                        color = ElysiumColors.OnSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                androidx.compose.material3.Button(
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        scope.launch {
                            transport.sendMouseReport(HidReports.mouse(HidDescriptors.MouseButton.RIGHT, 0, 0))
                            delay(50)
                            transport.sendMouseReport(HidReports.mouse(0, 0, 0))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ElysiumColors.Surface
                    )
                ) {
                    Text(
                        "CLIC DERECHO",
                        color = ElysiumColors.OnSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // The mode toggle + modifier/action/media bar.
            ModeBar(
                airMouseEnabled = airMouseEnabled,
                onToggleAirMouse = onToggleAirMouse,
                modifier = Modifier.fillMaxWidth()
            )
            UniversalBar(
                transport = transport,
                modifier = Modifier.fillMaxWidth()
            )
        }
        NeonFab(
            icon = {
                Icon(
                    Icons.Filled.Keyboard,
                    contentDescription = "Teclado",
                    modifier = Modifier.size(28.dp)
                )
            },
            onClick = onToggleKeyboard,
            accent = ElysiumColors.NeonPurple,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        )
    }
    if (showKeyboard) {
        // Reuse the AndroidKeyboardPanel pattern
        // from the Mac screen, but writing to
        // the HID transport.
        UniversalKeyboardOverlay(
            transport = transport,
            onClose = onToggleKeyboard,
            autoFocus = showKeyboard
        )
    }
}

/** Lightweight trackpad state for the cursor visualisation. */
private class TrackpadState {
    var cursorX by mutableStateOf(0.5f)
    var cursorY by mutableStateOf(0.5f)
    val ripples = mutableStateListOf<UniversalRipple>()
}

private data class UniversalRipple(
    val x: Float,
    val y: Float,
    val createdAt: Long
)

@Composable
private fun UniversalTrackpad(
    transport: BluetoothHidTransport,
    state: TrackpadState,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        ElysiumColors.SurfaceHigh,
                        ElysiumColors.Surface
                    )
                )
            )
            .border(2.dp, ElysiumColors.NeonCyan.copy(alpha = 0.6f), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.currentTimeMillis()
                    var isTap = true
                    var peakPointerCount = currentEvent.changes.size
                    var totalDrag = Offset.Zero
                    var lastPositions = mutableMapOf<PointerId, Offset>()
                    lastPositions[firstDown.id] = firstDown.position
                    var pinchInitial: Float? = null
                    var pendingMouseButtons = 0
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val currentChanges = event.changes
                        if (currentChanges.size > peakPointerCount) {
                            peakPointerCount = currentChanges.size
                        }
                        val allUp = currentChanges.none { it.pressed }
                        if (allUp) {
                            val duration = System.currentTimeMillis() - startTime
                            if (isTap && duration < 200 && totalDrag.getDistance() < 10f) {
                                val pos = firstDown.position
                                val nx = (pos.x / size.width).coerceIn(0f, 1f)
                                val ny = (pos.y / size.height).coerceIn(0f, 1f)
                                when (peakPointerCount) {
                                    1 -> {
                                        // Left click: down + up
                                        transport.sendMouseReport(
                                            HidReports.mouse(
                                                HidDescriptors.MouseButton.LEFT, 0, 0
                                            )
                                        )
                                        transport.sendMouseReport(HidReports.mouse(0, 0, 0))
                                        state.ripples.add(
                                            UniversalRipple(nx, ny, System.currentTimeMillis())
                                        )
                                    }
                                    2 -> {
                                        // Right click: down + up
                                        transport.sendMouseReport(
                                            HidReports.mouse(
                                                HidDescriptors.MouseButton.RIGHT, 0, 0
                                            )
                                        )
                                        transport.sendMouseReport(HidReports.mouse(0, 0, 0))
                                    }
                                }
                            } else if (peakPointerCount >= 3) {
                                // 3-finger swipe → media keys.
                                val firstStart = lastPositions.values.first()
                                val currentCenter = currentChanges
                                    .filter { it.pressed }
                                    .map { it.position }
                                    .fold(Offset.Zero) { acc, p -> acc + p } /
                                    currentChanges.count { it.pressed }.toFloat().coerceAtLeast(1f)
                                val dx = currentCenter.x - firstStart.x
                                val dy = currentCenter.y - firstStart.y
                                if (abs(dx) > abs(dy) && abs(dx) > 80f) {
                                    if (dx > 0) {
                                        transport.sendConsumerReport(HidDescriptors.Consumer.SCAN_NEXT)
                                        transport.sendConsumerReport(0)
                                    } else {
                                        transport.sendConsumerReport(HidDescriptors.Consumer.SCAN_PREVIOUS)
                                        transport.sendConsumerReport(0)
                                    }
                                } else if (abs(dy) > 80f) {
                                    if (dy < 0) {
                                        transport.sendConsumerReport(HidDescriptors.Consumer.VOLUME_UP)
                                        transport.sendConsumerReport(0)
                                    } else {
                                        transport.sendConsumerReport(HidDescriptors.Consumer.VOLUME_DOWN)
                                        transport.sendConsumerReport(0)
                                    }
                                }
                            }
                            break
                        }
                        var frameDrag = Offset.Zero
                        var framePinch: Float? = null
                        for (change in currentChanges) {
                            val lastPos = lastPositions[change.id] ?: change.position
                            frameDrag += Offset(
                                change.position.x - lastPos.x,
                                change.position.y - lastPos.y
                            )
                            lastPositions[change.id] = change.position
                            if (currentChanges.size == 2) {
                                if (pinchInitial == null) {
                                    val p1 = currentChanges[0].position
                                    val p2 = currentChanges[1].position
                                    pinchInitial = (p2 - p1).getDistance()
                                }
                                val p1 = currentChanges[0].position
                                val p2 = currentChanges[1].position
                                framePinch = (p2 - p1).getDistance()
                            }
                        }
                        if (frameDrag.getDistance() > 5f) {
                            isTap = false
                        }
                        totalDrag += frameDrag
                        when (currentChanges.size) {
                            1 -> {
                                // Mouse move: scale the pixel delta to a
                                // HID-mouse-friendly range. We scale by
                                // 0.5 so the cursor doesn't fly off the
                                // screen.
                                val dx = (frameDrag.x * 0.5f).roundToInt()
                                val dy = (frameDrag.y * 0.5f).roundToInt()
                                if (dx != 0 || dy != 0) {
                                    transport.sendMouseReport(
                                        HidReports.mouse(pendingMouseButtons, dx, dy)
                                    )
                                }
                            }
                            2 -> {
                                // Scroll wheel.
                                val wheel = (frameDrag.y * 0.5f).roundToInt()
                                if (wheel != 0) {
                                    transport.sendMouseReport(
                                        HidReports.mouse(pendingMouseButtons, 0, 0, wheel)
                                    )
                                }
                            }
                        }
                        if (currentChanges.size == 2 && pinchInitial != null && framePinch != null) {
                            val initial = pinchInitial
                            if (initial > 10f && framePinch!! > 10f) {
                                val factor = framePinch!! / initial
                                if (abs(factor - 1f) > 0.05f) {
                                    val wheel = ((factor - 1f) * 10).roundToInt()
                                    if (wheel != 0) {
                                        transport.sendMouseReport(
                                            HidReports.mouse(pendingMouseButtons, 0, 0, wheel)
                                        )
                                    }
                                    pinchInitial = framePinch
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // The trackpad surface hint (a few dots).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = size.width / 30
            val stepY = size.height / 20
            for (i in 0..30) {
                for (j in 0..20) {
                    drawCircle(
                        color = ElysiumColors.NeonCyan.copy(alpha = 0.04f),
                        radius = 0.8.dp.toPx(),
                        center = Offset(i * stepX, j * stepY)
                    )
                }
            }
        }
        // The cursor + ripple overlay.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val cx by animateFloatAsState(
                state.cursorX,
                animationSpec = spring(stiffness = 600f),
                label = "cx"
            )
            val cy by animateFloatAsState(
                state.cursorY,
                animationSpec = spring(stiffness = 600f),
                label = "cy"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.4f),
                    radius = 14.dp.toPx(),
                    center = Offset(size.width * cx, size.height * cy)
                )
                drawCircle(
                    color = ElysiumColors.NeonCyan,
                    radius = 5.dp.toPx(),
                    center = Offset(size.width * cx, size.height * cy)
                )
                val now = System.currentTimeMillis()
                state.ripples.toList().forEach { ripple ->
                    val age = (now - ripple.createdAt) / 600f
                    if (age < 1f) {
                        val r = (10 + age * 50).dp.toPx()
                        val a = (1f - age) * 0.8f
                        drawCircle(
                            color = ElysiumColors.NeonCyan.copy(alpha = a),
                            radius = r,
                            center = Offset(size.width * ripple.x, size.height * ripple.y),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
        // Centre label
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.Mouse,
                    contentDescription = null,
                    tint = ElysiumColors.NeonCyan.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "TRACKPAD",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    ),
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.4f)
                )
            }
        }
        // Auto-remove old ripples.
        LaunchedEffect(Unit) {
            while (true) {
                delay(100)
                val now = System.currentTimeMillis()
                state.ripples.removeAll { now - it.createdAt > 600 }
            }
        }
    }
}

/**
 * The mode toggle bar. The user can switch
 * between "Trackpad" (touch-driven) and "Air
 * Mouse" (gyro-driven). The two modes are
 * complementary: trackpad for precision work,
 * air mouse for couch / TV use.
 */
@Composable
private fun ModeBar(
    airMouseEnabled: Boolean,
    onToggleAirMouse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeButton(
            label = "Trackpad",
            color = ElysiumColors.NeonCyan,
            selected = !airMouseEnabled,
            onClick = { if (airMouseEnabled) onToggleAirMouse() },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            label = "Air Mouse",
            color = ElysiumColors.NeonGreen,
            selected = airMouseEnabled,
            onClick = { if (!airMouseEnabled) onToggleAirMouse() },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RowScope.ModeButton(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) color.copy(alpha = 0.3f)
                else ElysiumColors.SurfaceHigh
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) color else ElysiumColors.Outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = if (selected) color else ElysiumColors.OnSurfaceMuted
        )
    }
}

/**
 * The air mouse surface. A big "TILT TO MOVE"
 * indicator with a "Recentrar" button. The
 * actual sensor-driven deltas are sent in the
 * background; this surface is purely visual
 * feedback (so the user knows air mouse is
 * active).
 */
@Composable
private fun AirMouseSurface(
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val infinite = rememberInfiniteTransition(label = "airmouse")
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 1500),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "airmouse_pulse"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(ElysiumColors.SurfaceHigh, ElysiumColors.Surface)
                )
            )
            .border(2.dp, ElysiumColors.NeonGreen.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .alpha(pulse)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ElysiumColors.NeonGreen.copy(alpha = 0.4f),
                                ElysiumColors.NeonGreen.copy(alpha = 0f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = ElysiumColors.NeonGreen,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = "AIR MOUSE",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                ),
                color = ElysiumColors.NeonGreen
            )
            Text(
                text = "Inclina el teléfono para mover el cursor",
                style = TextStyle(fontSize = 12.sp),
                color = ElysiumColors.OnSurfaceMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            NeonChip(
                label = "Recentrar",
                onClick = onRecenter,
                accent = ElysiumColors.NeonOrange
            )
        }
    }
}

@Composable
private fun UniversalBar(
    transport: BluetoothHidTransport,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // First row: ⌘ ⌥ ⌃ ⇧ Esc
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "⌘" to HidDescriptors.Modifier.LEFT_GUI,
                "⌥" to HidDescriptors.Modifier.LEFT_ALT,
                "⌃" to HidDescriptors.Modifier.LEFT_CTRL,
                "⇧" to HidDescriptors.Modifier.LEFT_SHIFT,
                "Esc" to 0x29 // HID Escape
            ).forEach { (label, _) ->
                BarButton(
                    label = label,
                    color = ElysiumColors.NeonCyan,
                    onClick = {
                        // Toggle the modifier via a keyboard
                        // report. We send a press (modifier
                        // + keycode) then release.
                        val mod = when (label) {
                            "⌘" -> HidDescriptors.Modifier.LEFT_GUI
                            "⌥" -> HidDescriptors.Modifier.LEFT_ALT
                            "⌃" -> HidDescriptors.Modifier.LEFT_CTRL
                            "⇧" -> HidDescriptors.Modifier.LEFT_SHIFT
                            else -> 0
                        }
                        if (label == "Esc") {
                            transport.sendKeyboardReport(HidReports.keyboard(0, 0x29))
                            transport.sendKeyboardReport(HidReports.keyboardReleaseAll())
                        } else {
                            transport.sendKeyboardReport(HidReports.keyboard(mod))
                            transport.sendKeyboardReport(HidReports.keyboardReleaseAll())
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Second row: media keys.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BarButton(
                label = "🔇",
                color = ElysiumColors.NeonOrange,
                onClick = {
                    transport.sendConsumerReport(HidDescriptors.Consumer.VOLUME_MUTE)
                    transport.sendConsumerReport(0)
                },
                modifier = Modifier.weight(1f)
            )
            BarButton(
                label = "🔉",
                color = ElysiumColors.NeonOrange,
                onClick = {
                    transport.sendConsumerReport(HidDescriptors.Consumer.VOLUME_DOWN)
                    transport.sendConsumerReport(0)
                },
                modifier = Modifier.weight(1f)
            )
            BarButton(
                label = "🔊",
                color = ElysiumColors.NeonOrange,
                onClick = {
                    transport.sendConsumerReport(HidDescriptors.Consumer.VOLUME_UP)
                    transport.sendConsumerReport(0)
                },
                modifier = Modifier.weight(1f)
            )
            BarButton(
                label = "⏮",
                color = ElysiumColors.NeonPurple,
                onClick = {
                    transport.sendConsumerReport(HidDescriptors.Consumer.SCAN_PREVIOUS)
                    transport.sendConsumerReport(0)
                },
                modifier = Modifier.weight(1f)
            )
            BarButton(
                label = "⏯",
                color = ElysiumColors.NeonGreen,
                onClick = {
                    transport.sendConsumerReport(HidDescriptors.Consumer.PLAY_PAUSE)
                    transport.sendConsumerReport(0)
                },
                modifier = Modifier.weight(1f)
            )
            BarButton(
                label = "⏭",
                color = ElysiumColors.NeonPurple,
                onClick = {
                    transport.sendConsumerReport(HidDescriptors.Consumer.SCAN_NEXT)
                    transport.sendConsumerReport(0)
                },
                modifier = Modifier.weight(1f)
            )
        }
        // Third row: arrows + backspace + enter.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BarButton("↑", ElysiumColors.NeonCyan, { sendKey(transport, 0x52, 0) }, Modifier.weight(1f))
            BarButton("↓", ElysiumColors.NeonCyan, { sendKey(transport, 0x51, 0) }, Modifier.weight(1f))
            BarButton("←", ElysiumColors.NeonCyan, { sendKey(transport, 0x50, 0) }, Modifier.weight(1f))
            BarButton("→", ElysiumColors.NeonCyan, { sendKey(transport, 0x4F, 0) }, Modifier.weight(1f))
            BarButton("⌫", ElysiumColors.NeonOrange, { sendKey(transport, 0x2A, 0) }, Modifier.weight(1f))
            BarButton("⏎", ElysiumColors.NeonGreen, { sendKey(transport, 0x28, 0) }, Modifier.weight(1f))
        }
    }
}

private fun sendKey(transport: BluetoothHidTransport, hidUsage: Int, modifier: Int) {
    transport.sendKeyboardReport(HidReports.keyboard(modifier, hidUsage))
    transport.sendKeyboardReport(HidReports.keyboardReleaseAll())
}

@Composable
private fun RowScope.BarButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.18f), ElysiumColors.Surface)
                )
            )
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

@Composable
private fun UniversalKeyboardOverlay(
    transport: BluetoothHidTransport,
    onClose: () -> Unit,
    autoFocus: Boolean
) {
    var text by remember { mutableStateOf("") }
    val ime = LocalSoftwareKeyboardController.current
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f)
    ) {
        NeonCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            accent = ElysiumColors.NeonPurple,
            cornerRadius = 16.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Teclado",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.NeonPurple
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ElysiumColors.Surface)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = ElysiumColors.OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElysiumColors.Surface)
                        .border(1.dp, ElysiumColors.NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Toca aquí para escribir...",
                            style = TextStyle(fontSize = 14.sp),
                            color = ElysiumColors.OnSurfaceMuted
                        )
                    } else {
                        Text(
                            text = text,
                            style = TextStyle(fontSize = 16.sp),
                            color = ElysiumColors.OnSurface
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { newText ->
                            if (newText.length > text.length) {
                                val newChars = newText.substring(text.length)
                                newChars.forEach { c ->
                                    val (hid, needsShift) = asciiToHid(c)
                                    val mod = if (needsShift) HidDescriptors.Modifier.LEFT_SHIFT else 0
                                    transport.sendKeyboardReport(HidReports.keyboard(mod, hid))
                                    transport.sendKeyboardReport(HidReports.keyboardReleaseAll())
                                }
                            } else if (newText.length < text.length) {
                                val deletedCount = text.length - newText.length
                                repeat(deletedCount) {
                                    sendKey(transport, 0x2A, 0)
                                }
                            }
                            text = newText
                        },
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = Color.Transparent
                        ),
                        cursorBrush = SolidColor(Color.Transparent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Default
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BarButton(
                        label = "⌫ Borrar",
                        color = ElysiumColors.NeonOrange,
                        onClick = {
                            if (text.isNotEmpty()) text = text.dropLast(1)
                            sendKey(transport, 0x2A, 0)
                        },
                        modifier = Modifier.weight(2f)
                    )
                    BarButton(
                        label = "Espacio",
                        color = ElysiumColors.NeonCyan,
                        onClick = {
                            text = "$text "
                            sendKey(transport, 0x2C, 0)
                        },
                        modifier = Modifier.weight(2f)
                    )
                    BarButton(
                        label = "⏎",
                        color = ElysiumColors.NeonGreen,
                        onClick = {
                            text = "$text\n"
                            sendKey(transport, 0x28, 0)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(200)
            ime?.show()
        }
    }
}

private fun asciiToHid(c: Char): Pair<Int, Boolean> {
    if (c in 'a'..'z') return Pair(0x04 + (c - 'a'), false)
    if (c in 'A'..'Z') return Pair(0x04 + (c - 'A'), true)
    if (c in '1'..'9') return Pair(0x1E + (c - '1'), false)
    return when (c) {
        '0' -> Pair(0x27, false)
        ' ' -> Pair(0x2C, false)
        '\n' -> Pair(0x28, false)
        '\t' -> Pair(0x2B, false)
        '-' -> Pair(0x2D, false)
        '=' -> Pair(0x2E, false)
        '[' -> Pair(0x2F, false)
        ']' -> Pair(0x30, false)
        '\\' -> Pair(0x31, false)
        ';' -> Pair(0x33, false)
        '\'' -> Pair(0x34, false)
        '`' -> Pair(0x35, false)
        ',' -> Pair(0x36, false)
        '.' -> Pair(0x37, false)
        '/' -> Pair(0x38, false)
        '!' -> Pair(0x1E, true)
        '@' -> Pair(0x1F, true)
        '#' -> Pair(0x20, true)
        '$' -> Pair(0x21, true)
        '%' -> Pair(0x22, true)
        '^' -> Pair(0x23, true)
        '&' -> Pair(0x24, true)
        '*' -> Pair(0x25, true)
        '(' -> Pair(0x26, true)
        ')' -> Pair(0x27, true)
        '_' -> Pair(0x2D, true)
        '+' -> Pair(0x2E, true)
        '{' -> Pair(0x2F, true)
        '}' -> Pair(0x30, true)
        '|' -> Pair(0x31, true)
        ':' -> Pair(0x33, true)
        '"' -> Pair(0x34, true)
        '~' -> Pair(0x35, true)
        '<' -> Pair(0x36, true)
        '>' -> Pair(0x37, true)
        '?' -> Pair(0x38, true)
        else -> Pair(0x2C, false)
    }
}

// === HELPERS ===

private fun checkBluetoothPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@SuppressLint("MissingPermission")
private fun isBluetoothOn(context: Context): Boolean {
    return try {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.isEnabled == true
    } catch (e: Throwable) {
        false
    }
}

private fun requestBluetoothPermissions(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val perms = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) perms.add(Manifest.permission.BLUETOOTH_SCAN)
    } else {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
            != PackageManager.PERMISSION_GRANTED
        ) perms.add(Manifest.permission.BLUETOOTH)
    }
    if (perms.isNotEmpty()) {
        launcher.launch(perms.toTypedArray())
    }
}

/**
 * Compose `collectAsState` wrapper that handles
 * the StateFlow import locally. Centralised so
 * the rest of the file does not need an extra
 * import.
 */
@Composable
private fun <T> StateFlow<T>.collectAsStateLifecycleSafe(): androidx.compose.runtime.State<T> {
    val initialValue = remember { this.value }
    return collectAsState(initial = initialValue)
}
