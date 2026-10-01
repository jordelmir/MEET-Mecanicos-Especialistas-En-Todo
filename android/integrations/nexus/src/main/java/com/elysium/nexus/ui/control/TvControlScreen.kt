package com.elysium.nexus.ui.control

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceButton
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.EncodeResult
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrTransmitResult
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.launch

private const val TAG = "ElysiumNexus.TvControlScreen"

/**
 * §22 Button model carrying IrAction directly — no string→action→string roundtrip.
 */
data class IrRemoteButtonModel(
    val action: IrAction,
    val label: String,
    val iconHint: String,
    val verificationStatus: VerificationStatus
)

/**
 * §3/§20/§21/§22 Authoritative TvControlScreen.
 *
 * §3: Loads profile from Room by profileId. No object transport.
 * §20: Singleton catalog repository. Not created per-button.
 * §21: Fingerprint verification before every transmission.
 * §22: Button grid generated from profile bindings, NOT from DeviceTemplate.
 */
@Composable
fun TvControlScreen(
    profileId: String,
    onBack: () -> Unit,
    irTransmitter: AndroidIrTransmitter,
    hasEmitter: Boolean,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var showFullRemote by remember { mutableStateOf(true) }
    var transmitStatusText by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // §3 Load profile from Room by profileId — single source of truth
    var activeProfile by remember { mutableStateOf<InstalledIrProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profileId) {
        isLoading = true
        errorMessage = null
        try {
            val repo = InstalledIrProfileRepository(context)
            activeProfile = repo.getProfileSuspend(profileId)
            if (activeProfile == null) {
                errorMessage = "Perfil $profileId no encontrado en Room"
                Log.e(TAG, "Profile $profileId not found in Room")
            }
        } catch (e: Exception) {
            errorMessage = "Error cargando perfil: ${e.message}"
            Log.e(TAG, "Failed to load profile $profileId: ${e.message}")
        }
        isLoading = false
    }

    // §20 Singleton catalog repository
    val catalogRepo = remember { IrCatalogRepository.getInstance(context) }

    // Resolve a fallback DeviceTemplate from the profile's brand
    val fallbackTemplate = remember(activeProfile) {
        activeProfile?.let { profile ->
            com.elysium.nexus.core.device.DeviceCatalog.all.firstOrNull {
                it.brand.equals(profile.brand, ignoreCase = true)
            } ?: com.elysium.nexus.core.device.DeviceCatalog.byId("tv-universal-generic")
                ?: com.elysium.nexus.core.device.DeviceCatalog.all.first()
        }
    }

    // §22 Generate buttons from profile bindings, carrying IrAction directly
    val effectiveButtons = remember(activeProfile, fallbackTemplate) {
        if (activeProfile != null && activeProfile!!.commands.isNotEmpty()) {
            activeProfile!!.commands.keys.map { action ->
                val templateBtn = fallbackTemplate?.buttons?.firstOrNull { mapButtonToIrAction(it.id) == action }
                IrRemoteButtonModel(
                    action = action,
                    label = templateBtn?.labelEs ?: action.name.replace("_", " "),
                    iconHint = templateBtn?.iconHint ?: action.name.lowercase(),
                    verificationStatus = activeProfile!!.verificationStatus
                )
            }
        } else {
            emptyList()
        }
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(modifier = Modifier.fillMaxSize()) {
            // === TOP BAR ===
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(label = "Atrás", onClick = onBack, accent = ElysiumColors.NeonPurple, icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) })
                if (transmitStatusText != null) {
                    NeonStatusPill(label = transmitStatusText!!, color = if (isStatusError) ElysiumColors.NeonOrange else ElysiumColors.NeonGreen)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Toggle grid / full remote
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(ElysiumColors.NeonCyan.copy(alpha = 0.6f)).clickable { showFullRemote = !showFullRemote },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (showFullRemote) Icons.Filled.GridView else Icons.Filled.SettingsRemote,
                            contentDescription = if (showFullRemote) "Vista grid" else "Control completo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(ElysiumColors.NeonPurple.copy(alpha = 0.6f)).clickable { showHelp = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = "Ayuda", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = ElysiumColors.NeonCyan)
                    }
                }
                errorMessage != null -> {
                    NeonCard(modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 16.dp), accent = ElysiumColors.NeonOrange, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Error", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = ElysiumColors.NeonOrange)
                            Text(errorMessage!!, style = TextStyle(fontSize = 13.sp), color = ElysiumColors.OnSurface)
                            NeonChip(label = "Volver", onClick = onBack, accent = ElysiumColors.NeonPurple, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                activeProfile != null -> {
                    val profile = activeProfile!!

                    // === HERO CARD ===
                    NeonHeroCard(
                        title = profile.brand,
                        subtitle = profile.displayName,
                        accent = ElysiumColors.NeonGreen,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 4.dp),
                        statusChips = {
                            NeonStatusPill(label = "Room DB", color = ElysiumColors.NeonGreen)
                            NeonStatusPill(label = "${profile.commands.size} comandos", color = ElysiumColors.NeonCyan)
                            if (hasEmitter) NeonStatusPill(label = "IR listo", color = ElysiumColors.NeonGreen)
                            else NeonStatusPill(label = "Sin emisor", color = ElysiumColors.NeonOrange)
                        }
                    )

                    // === TIP ===
                    NeonCard(modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 4.dp), accent = ElysiumColors.NeonOrange, cornerRadius = 12.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = ElysiumColors.NeonOrange, modifier = Modifier.size(20.dp))
                            Text("Apunta el emisor superior al ${profile.brand} para enviar cada comando.", style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp), color = ElysiumColors.OnSurface)
                        }
                    }

                    // === BUTTON AREA ===
                    if (showFullRemote) {
                        FullRemoteLayout(
                            profile = profile,
                            onAction = { action ->
                                val buttonModel = effectiveButtons.firstOrNull { it.action == action }
                                if (buttonModel != null) {
                                    val result = sendProfileCommand(catalogRepo, irTransmitter, profile, buttonModel)
                                    when (result) {
                                        is IrTransmitResult.Success -> { isStatusError = false; transmitStatusText = "Enviado: ${buttonModel.label}" }
                                        is IrTransmitResult.NoEmitter -> { isStatusError = true; transmitStatusText = "Sin emisor IR" }
                                        is IrTransmitResult.PermissionDenied -> { isStatusError = true; transmitStatusText = "Permiso denegado" }
                                        is IrTransmitResult.UnsupportedCarrier -> { isStatusError = true; transmitStatusText = "Frecuencia no soportada" }
                                        is IrTransmitResult.InvalidPattern -> { isStatusError = true; transmitStatusText = "Error: ${result.reason}" }
                                        is IrTransmitResult.Busy -> { isStatusError = true; transmitStatusText = "Emisor ocupado" }
                                        is IrTransmitResult.PlatformFailure -> { isStatusError = true; transmitStatusText = "Error Android" }
                                    }
                                } else {
                                    isStatusError = true
                                    transmitStatusText = "Acción $action no disponible en este perfil"
                                }
                            },
                            modifier = Modifier.fillMaxSize().padding(horizontal = info.sidePadding, vertical = 4.dp)
                        )
                    } else {
                    // === BUTTON GRID ===
                    val columns = when (info.size) {
                        com.elysium.nexus.ui.responsive.ScreenSize.Compact -> 4
                        com.elysium.nexus.ui.responsive.ScreenSize.Medium -> 5
                        com.elysium.nexus.ui.responsive.ScreenSize.Expanded -> 6
                        com.elysium.nexus.ui.responsive.ScreenSize.Large -> 7
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize().padding(horizontal = info.sidePadding, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                    ) {
                    items(effectiveButtons) { button ->
                        ControlButton(
                            button = button,
                            onClick = {
                                scope.launch {
                                    val result = sendProfileCommand(catalogRepo, irTransmitter, profile, button)
                                    when (result) {
                                        is IrTransmitResult.Success -> { isStatusError = false; transmitStatusText = "Enviado: ${button.label}" }
                                        is IrTransmitResult.NoEmitter -> { isStatusError = true; transmitStatusText = "Sin emisor IR" }
                                        is IrTransmitResult.PermissionDenied -> { isStatusError = true; transmitStatusText = "Permiso denegado" }
                                        is IrTransmitResult.UnsupportedCarrier -> { isStatusError = true; transmitStatusText = "Frecuencia no soportada" }
                                        is IrTransmitResult.InvalidPattern -> { isStatusError = true; transmitStatusText = "Error: ${result.reason}" }
                                        is IrTransmitResult.Busy -> { isStatusError = true; transmitStatusText = "Emisor ocupado" }
                                        is IrTransmitResult.PlatformFailure -> { isStatusError = true; transmitStatusText = "Error Android" }
                                    }
                                }
                            }
                        )
                    }
                    }
                    }
                }
            }
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Control ${activeProfile?.brand ?: ""}",
            whatIsThis = "Superficie de control IR basada en perfiles persistentes de Room.",
            howToUse = listOf("Apunta al sensor IR.", "Toca un botón para enviar la señal.", "Las señales se ejecutan desde el catálogo SQLite."),
            tip = "Mantén vista directa entre emisor y sensor.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun ControlButton(button: IrRemoteButtonModel, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.90f else 1.0f, animationSpec = tween(durationMillis = 100), label = "btn_scale")
    val icon = buttonIcon(button.iconHint)
    val color = when (button.iconHint) {
        "power", "POWER_TOGGLE" -> ElysiumColors.NeonOrange
        "vol_up", "VOLUME_UP", "vol_down", "VOLUME_DOWN", "mute", "MUTE" -> ElysiumColors.NeonCyan
        "ch_up", "CHANNEL_UP", "ch_down", "CHANNEL_DOWN" -> ElysiumColors.NeonGreen
        "nav", "UP", "DOWN", "LEFT", "RIGHT" -> ElysiumColors.NeonPurple
        else -> ElysiumColors.NeonCyan
    }
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.0f).scale(scale).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.08f))))
            .clickable { isPressed = true; onClick(); scope.launch { kotlinx.coroutines.delay(120); isPressed = false } },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = button.label, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(button.label, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = ElysiumColors.OnSurface, maxLines = 1)
        }
    }
}

private fun buttonIcon(iconHint: String): ImageVector = when (iconHint) {
    "power", "POWER_TOGGLE" -> Icons.Filled.PowerSettingsNew
    "vol_up", "VOLUME_UP" -> Icons.Filled.VolumeUp
    "vol_down", "VOLUME_DOWN" -> Icons.Filled.VolumeDown
    "mute", "MUTE" -> Icons.Filled.VolumeOff
    "ch_up", "CHANNEL_UP" -> Icons.Filled.ArrowUpward
    "ch_down", "CHANNEL_DOWN" -> Icons.Filled.ArrowDownward
    "up", "UP" -> Icons.Filled.ArrowUpward
    "down", "DOWN" -> Icons.Filled.ArrowDownward
    "left", "LEFT" -> Icons.Filled.ChevronLeft
    "right", "RIGHT" -> Icons.Filled.ChevronRight
    "ok", "OK" -> Icons.Filled.Check
    "menu", "MENU" -> Icons.Filled.Menu
    "source", "input", "INPUT" -> Icons.Filled.Settings
    "back", "BACK" -> Icons.Filled.ArrowBack
    "info" -> Icons.Filled.Info
    "guide" -> Icons.Filled.Menu
    "exit" -> Icons.Filled.Close
    "last" -> Icons.Filled.Refresh
    "home", "HOME" -> Icons.Filled.Home
    "netflix", "youtube", "prime" -> Icons.Filled.LiveTv
    "play", "PLAY" -> Icons.Filled.PlayArrow
    "pause", "PAUSE" -> Icons.Filled.PlayArrow
    "stop", "STOP" -> Icons.Filled.Stop
    "num_1", "num_2", "num_3", "num_4", "num_5" -> Icons.Filled.Numbers
    "num_6", "num_7", "num_8", "num_9", "num_0" -> Icons.Filled.Numbers
    "minus" -> Icons.Filled.FastRewind
    "plus" -> Icons.Filled.FastForward
    "cross" -> Icons.Filled.Close
    "circle" -> Icons.Filled.Refresh
    "square" -> Icons.Filled.Stop
    "triangle" -> Icons.Filled.PlayArrow
    "l1", "r1", "l2", "r2" -> Icons.Filled.Keyboard
    "select", "start" -> Icons.Filled.Menu
    else -> Icons.Filled.Keyboard
}

private fun mapButtonToIrAction(buttonId: String): IrAction? = when (buttonId) {
    "power" -> IrAction.POWER_TOGGLE
    "vol_up" -> IrAction.VOLUME_UP
    "vol_down" -> IrAction.VOLUME_DOWN
    "mute" -> IrAction.MUTE
    "ch_up" -> IrAction.CHANNEL_UP
    "ch_down" -> IrAction.CHANNEL_DOWN
    "up" -> IrAction.UP
    "down" -> IrAction.DOWN
    "left" -> IrAction.LEFT
    "right" -> IrAction.RIGHT
    "ok" -> IrAction.OK
    "menu" -> IrAction.MENU
    "home" -> IrAction.HOME
    "back" -> IrAction.BACK
    "play", "play_pause" -> IrAction.PLAY
    "pause" -> IrAction.PAUSE
    "stop" -> IrAction.STOP
    "source", "input" -> IrAction.INPUT
    "n1" -> IrAction.NUM_1
    "n2" -> IrAction.NUM_2
    "n3" -> IrAction.NUM_3
    "n4" -> IrAction.NUM_4
    "n5" -> IrAction.NUM_5
    "n6" -> IrAction.NUM_6
    "n7" -> IrAction.NUM_7
    "n8" -> IrAction.NUM_8
    "n9" -> IrAction.NUM_9
    "n0" -> IrAction.NUM_0
    "dash" -> IrAction.NUM_DASH
    "plus" -> IrAction.NUM_PLUS
    "info" -> IrAction.INFO
    "last" -> IrAction.LAST_CHANNEL
    "netflix" -> IrAction.NETFLIX
    "youtube" -> IrAction.YOUTUBE
    else -> null
}

/**
 * §21/§22 Authoritative command sending — uses IrAction directly from button model.
 * Fingerprint verification before every transmit.
 */
private suspend fun sendProfileCommand(
    catalogRepo: IrCatalogRepository,
    transmitter: AndroidIrTransmitter,
    profile: InstalledIrProfile,
    button: IrRemoteButtonModel
): IrTransmitResult {
    // §22 Use IrAction directly — no string roundtrip
    val action = button.action

    val binding = profile.commands[action]
        ?: return IrTransmitResult.InvalidPattern("Acción $action no mapeada en perfil ${profile.id}")

    val signal = catalogRepo.resolveExecutableSignal(binding.signalId)
        ?: return IrTransmitResult.InvalidPattern("Signal ${binding.signalId} no encontrado en catálogo SQLite")

    // §21 Fingerprint verification
    val actualFingerprint = IrProbeEngine.fingerprintSignal(signal)
    if (actualFingerprint != binding.physicalFingerprint) {
        Log.e(TAG, "FINGERPRINT MISMATCH action=$action signalId=${binding.signalId}: expected=${binding.physicalFingerprint}, actual=$actualFingerprint")
        return IrTransmitResult.InvalidPattern("Fingerprint mismatch para $action — reinstalar perfil")
    }

    val encodeResult = IrProtocol.encode(signal)
    return if (encodeResult is EncodeResult.Success) {
        Log.d(TAG, "Transmitting action=$action signalId=${binding.signalId} codeSetId=${profile.codeSetId}")
        transmitter.transmit(encodeResult.waveform)
    } else {
        IrTransmitResult.InvalidPattern("Error codificando señal para $action")
    }
}
