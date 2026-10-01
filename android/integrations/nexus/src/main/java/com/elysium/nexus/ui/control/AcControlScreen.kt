package com.elysium.nexus.ui.control

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.IrWaveform
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.unit.dp

/**
 * AC / HVAC control screen.
 *
 * Stateful IR control for air conditioners.
 * The screen shows temperature, mode, and
 * fan speed controls. Each adjustment sends
 * a stateful IR command that encodes the
 * full AC state (temperature + mode + fan).
 *
 * The layout:
 *  - Temperature display (large)
 *  - Temperature +/- buttons
 *  - Mode selector (Cool, Heat, Auto, Dry, Fan)
 *  - Fan speed selector (Low, Med, High, Auto)
 *  - Power toggle
 */
@Composable
fun AcControlScreen(
    template: DeviceTemplate,
    onBack: () -> Unit,
    irTransmitter: AndroidIrTransmitter,
    hasEmitter: Boolean,
    acStateStore: com.elysium.nexus.core.settings.AcStateStore? = null,
    modifier: Modifier = Modifier
) {
    val savedState = remember { acStateStore?.get(template.id) }
    var showHelp by remember { mutableStateOf(false) }
    var temperature by remember { mutableIntStateOf(savedState?.temperature ?: 24) }
    var mode by remember { mutableIntStateOf(savedState?.mode ?: 1) }
    var fanSpeed by remember { mutableIntStateOf(savedState?.fanSpeed ?: 0) }
    var powerOn by remember { mutableStateOf(savedState?.powerOn ?: true) }
    var lastCommandLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun sendAcCommand() {
        if (!hasEmitter) return
        lastCommandLabel = when (mode) {
            1 -> "Frío ${temperature}°C"
            4 -> "Calor ${temperature}°C"
            0 -> "Auto ${temperature}°C"
            2 -> "Secar ${temperature}°C"
            3 -> "Ventilador ${temperature}°C"
            else -> "${temperature}°C"
        }
        scope.launch {
            val waveform = when (template.brand.lowercase()) {
                "daikin" -> IrWaveform.encodeDaikin(
                    address = template.deviceAddress,
                    powerOn = powerOn,
                    temperatureCelsius = temperature,
                    mode = mode,
                    fanSpeed = fanSpeed
                )
                "gree" -> IrWaveform.encodeGree(
                    address = template.deviceAddress,
                    powerOn = powerOn,
                    temperatureCelsius = temperature,
                    mode = mode,
                    fanSpeed = fanSpeed
                )
                "midea" -> IrWaveform.encodeMidea(
                    address = template.deviceAddress,
                    powerOn = powerOn,
                    temperatureCelsius = temperature,
                    mode = mode,
                    fanSpeed = fanSpeed
                )
                "mitsubishi" -> IrWaveform.encodeMitsubishi(
                    address = template.deviceAddress,
                    powerOn = powerOn,
                    temperatureCelsius = temperature,
                    mode = mode,
                    fanSpeed = fanSpeed
                )
                else -> IrWaveform.encodeDaikin(
                    address = template.deviceAddress,
                    powerOn = powerOn,
                    temperatureCelsius = temperature,
                    mode = mode,
                    fanSpeed = fanSpeed
                )
            }
            irTransmitter.transmit(waveform)
            acStateStore?.set(
                template.id,
                com.elysium.nexus.core.settings.AcState(
                    temperature = temperature,
                    mode = mode,
                    fanSpeed = fanSpeed,
                    powerOn = powerOn
                )
            )
        }
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // === TOP BAR ===========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = onBack,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                if (lastCommandLabel != null) {
                    NeonStatusPill(
                        label = "Enviado: $lastCommandLabel",
                        color = ElysiumColors.NeonGreen
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = "Ayuda",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // === HERO CARD =========================================
            NeonHeroCard(
                title = template.brand,
                subtitle = template.model,
                accent = if (powerOn) ElysiumColors.NeonGreen else ElysiumColors.NeonMagenta,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = if (powerOn) "Encendido" else "Apagado",
                        color = if (powerOn) ElysiumColors.NeonGreen else ElysiumColors.NeonMagenta
                    )
                    if (hasEmitter) {
                        NeonStatusPill(
                            label = "IR listo",
                            color = ElysiumColors.NeonCyan
                        )
                    }
                }
            )

            // === TEMPERATURE DISPLAY ==============================
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
                accent = ElysiumColors.NeonCyan,
                cornerRadius = 16.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Temp -
                    AcButton(
                        icon = { Icon(Icons.Filled.Remove, contentDescription = "Bajar temperatura") },
                        accent = ElysiumColors.NeonOrange,
                        onClick = {
                            if (temperature > 16) {
                                temperature--
                                sendAcCommand()
                            }
                        }
                    )
                    // Temperature display
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${temperature}°C",
                            style = TextStyle(
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ElysiumColors.OnSurface
                        )
                        Text(
                            text = "Temperatura",
                            style = TextStyle(fontSize = 14.sp),
                            color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
                        )
                    }
                    // Temp +
                    AcButton(
                        icon = { Icon(Icons.Filled.Add, contentDescription = "Subir temperatura") },
                        accent = ElysiumColors.NeonGreen,
                        onClick = {
                            if (temperature < 32) {
                                temperature++
                                sendAcCommand()
                            }
                        }
                    )
                }
            }

            // === MODE SELECTOR =====================================
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonPurple,
                cornerRadius = 12.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                Column {
                    Text(
                        text = "Modo",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeChip("Auto", 0, mode, ElysiumColors.NeonCyan) { mode = it; sendAcCommand() }
                        ModeChip("Frío", 1, mode, ElysiumColors.NeonCyan) { mode = it; sendAcCommand() }
                        ModeChip("Secar", 2, mode, ElysiumColors.NeonOrange) { mode = it; sendAcCommand() }
                        ModeChip("Vent", 3, mode, ElysiumColors.NeonGreen) { mode = it; sendAcCommand() }
                        ModeChip("Calor", 4, mode, ElysiumColors.NeonMagenta) { mode = it; sendAcCommand() }
                    }
                }
            }

            // === FAN SPEED =========================================
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonGreen,
                cornerRadius = 12.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                Column {
                    Text(
                        text = "Ventilador",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeChip("Auto", 0, fanSpeed, ElysiumColors.NeonCyan) { fanSpeed = it; sendAcCommand() }
                        ModeChip("Bajo", 1, fanSpeed, ElysiumColors.NeonGreen) { fanSpeed = it; sendAcCommand() }
                        ModeChip("Medio", 2, fanSpeed, ElysiumColors.NeonOrange) { fanSpeed = it; sendAcCommand() }
                        ModeChip("Alto", 3, fanSpeed, ElysiumColors.NeonMagenta) { fanSpeed = it; sendAcCommand() }
                    }
                }
            }

            // === POWER BUTTON ======================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                AcButton(
                    icon = {
                        Icon(
                            Icons.Filled.PowerSettingsNew,
                            contentDescription = if (powerOn) "Apagar" else "Encender",
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    accent = if (powerOn) ElysiumColors.NeonGreen else ElysiumColors.NeonMagenta,
                    size = 64.dp,
                    onClick = {
                        powerOn = !powerOn
                        sendAcCommand()
                    }
                )
            }
        }
    }

    // Auto-clear the "Enviado" pill after 2s
    LaunchedEffect(lastCommandLabel) {
        if (lastCommandLabel != null) {
            delay(2000)
            lastCommandLabel = null
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Control AC ${template.brand}",
            whatIsThis = "Esta es la pantalla de control de aire acondicionado. Cada ajuste envia un " +
                "comando IR codificado con la temperatura, modo y ventilador.",
            howToUse = listOf(
                "Apunta la parte de arriba del telefono al ${template.brand}.",
                "Ajusta la temperatura con los botones +/-.",
                "Selecciona el modo (Frío, Calor, Auto, Secar, Ventilador).",
                "Selecciona la velocidad del ventilador.",
                "Cada ajuste envia un comando IR completo con el estado del AC."
            ),
            tip = "Si el AC no responde, verifica que el protocolo sea el correcto para tu modelo.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun AcButton(
    icon: @Composable () -> Unit,
    accent: Color,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.3f),
                        accent.copy(alpha = 0.1f)
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun ModeChip(
    label: String,
    value: Int,
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit
) {
    val isSelected = value == selected
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) accent.copy(alpha = 0.3f)
                else ElysiumColors.Surface
            )
            .clickable(onClick = { onSelect(value) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) accent else ElysiumColors.OnSurface
        )
    }
}
