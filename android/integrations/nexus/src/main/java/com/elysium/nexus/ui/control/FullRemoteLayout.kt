package com.elysium.nexus.ui.control

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.ui.theme.ElysiumColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full remote control layout — realistic TV remote shape.
 *
 * Layout:
 * ```
 *        [POWER]
 *
 *  [CH+]  [▲]  [VOL+]
 *        [OK]
 *  [CH-]  [▼]  [VOL-]
 *
 *  [1] [2] [3]
 *  [4] [5] [6]
 *  [7] [8] [9]
 *  [-] [0] [+]
 *
 *  [BACK] [HOME] [MENU]
 *  [NETFLIX] [YOUTUBE]
 *  [INFO] [LAST]
 *  [▶] [⏸] [⏹]
 * ```
 */
@Composable
fun FullRemoteLayout(
    profile: InstalledIrProfile,
    onAction: suspend (IrAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val hasSignal: (IrAction) -> Boolean = { action -> profile.commands.containsKey(action) }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // === POWER ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            RemoteCircleButton(
                action = IrAction.POWER_TOGGLE,
                icon = Icons.Filled.PowerSettingsNew,
                label = "Power",
                color = ElysiumColors.NeonOrange,
                size = 52,
                enabled = hasSignal(IrAction.POWER_TOGGLE),
                onClick = { scope.launch { onAction(IrAction.POWER_TOGGLE) } }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // === D-PAD + VOL/CH ROW ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CH+ button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RemoteSquareButton(
                    action = IrAction.CHANNEL_UP,
                    icon = Icons.Filled.ArrowUpward,
                    label = "CH+",
                    color = ElysiumColors.NeonGreen,
                    enabled = hasSignal(IrAction.CHANNEL_UP),
                    onClick = { scope.launch { onAction(IrAction.CHANNEL_UP) } }
                )
                Spacer(modifier = Modifier.height(24.dp))
                RemoteSquareButton(
                    action = IrAction.CHANNEL_DOWN,
                    icon = Icons.Filled.ArrowDownward,
                    label = "CH-",
                    color = ElysiumColors.NeonGreen,
                    enabled = hasSignal(IrAction.CHANNEL_DOWN),
                    onClick = { scope.launch { onAction(IrAction.CHANNEL_DOWN) } }
                )
            }

            // D-Pad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RemoteCircleButton(
                    action = IrAction.UP,
                    icon = Icons.Filled.ArrowUpward,
                    label = "",
                    color = ElysiumColors.NeonPurple,
                    size = 44,
                    enabled = hasSignal(IrAction.UP),
                    onClick = { scope.launch { onAction(IrAction.UP) } }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    RemoteCircleButton(
                        action = IrAction.LEFT,
                        icon = Icons.Filled.ChevronLeft,
                        label = "",
                        color = ElysiumColors.NeonPurple,
                        size = 44,
                        enabled = hasSignal(IrAction.LEFT),
                        onClick = { scope.launch { onAction(IrAction.LEFT) } }
                    )
                    RemoteCircleButton(
                        action = IrAction.OK,
                        icon = Icons.Filled.Check,
                        label = "OK",
                        color = ElysiumColors.NeonCyan,
                        size = 52,
                        enabled = hasSignal(IrAction.OK),
                        onClick = { scope.launch { onAction(IrAction.OK) } }
                    )
                    RemoteCircleButton(
                        action = IrAction.RIGHT,
                        icon = Icons.Filled.ChevronRight,
                        label = "",
                        color = ElysiumColors.NeonPurple,
                        size = 44,
                        enabled = hasSignal(IrAction.RIGHT),
                        onClick = { scope.launch { onAction(IrAction.RIGHT) } }
                    )
                }
                RemoteCircleButton(
                    action = IrAction.DOWN,
                    icon = Icons.Filled.ArrowDownward,
                    label = "",
                    color = ElysiumColors.NeonPurple,
                    size = 44,
                    enabled = hasSignal(IrAction.DOWN),
                    onClick = { scope.launch { onAction(IrAction.DOWN) } }
                )
            }

            // VOL buttons
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RemoteSquareButton(
                    action = IrAction.VOLUME_UP,
                    icon = Icons.Filled.VolumeUp,
                    label = "VOL+",
                    color = ElysiumColors.NeonCyan,
                    enabled = hasSignal(IrAction.VOLUME_UP),
                    onClick = { scope.launch { onAction(IrAction.VOLUME_UP) } }
                )
                Spacer(modifier = Modifier.height(24.dp))
                RemoteSquareButton(
                    action = IrAction.VOLUME_DOWN,
                    icon = Icons.Filled.VolumeDown,
                    label = "VOL-",
                    color = ElysiumColors.NeonCyan,
                    enabled = hasSignal(IrAction.VOLUME_DOWN),
                    onClick = { scope.launch { onAction(IrAction.VOLUME_DOWN) } }
                )
            }
        }

        // Mute row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            RemoteSquareButton(
                action = IrAction.MUTE,
                icon = Icons.Filled.VolumeOff,
                label = "Mute",
                color = ElysiumColors.NeonCyan,
                enabled = hasSignal(IrAction.MUTE),
                onClick = { scope.launch { onAction(IrAction.MUTE) } }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // === NUMBER PAD ===
        // Row 1: 1 2 3
        RemoteNumberRow(
            actions = listOf(IrAction.NUM_1, IrAction.NUM_2, IrAction.NUM_3),
            hasSignal = hasSignal,
            onAction = onAction
        )
        // Row 2: 4 5 6
        RemoteNumberRow(
            actions = listOf(IrAction.NUM_4, IrAction.NUM_5, IrAction.NUM_6),
            hasSignal = hasSignal,
            onAction = onAction
        )
        // Row 3: 7 8 9
        RemoteNumberRow(
            actions = listOf(IrAction.NUM_7, IrAction.NUM_8, IrAction.NUM_9),
            hasSignal = hasSignal,
            onAction = onAction
        )
        // Row 4: - 0 +
        RemoteNumberRow(
            actions = listOf(IrAction.NUM_DASH, IrAction.NUM_0, IrAction.NUM_PLUS),
            hasSignal = hasSignal,
            onAction = onAction
        )

        Spacer(modifier = Modifier.height(8.dp))

        // === NAVIGATION ROW ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteSmallButton(
                action = IrAction.BACK,
                icon = Icons.Filled.ArrowBack,
                label = "Atrás",
                color = ElysiumColors.NeonPurple,
                enabled = hasSignal(IrAction.BACK),
                onClick = { scope.launch { onAction(IrAction.BACK) } }
            )
            RemoteSmallButton(
                action = IrAction.HOME,
                icon = Icons.Filled.Home,
                label = "Inicio",
                color = ElysiumColors.NeonCyan,
                enabled = hasSignal(IrAction.HOME),
                onClick = { scope.launch { onAction(IrAction.HOME) } }
            )
            RemoteSmallButton(
                action = IrAction.MENU,
                icon = Icons.Filled.Menu,
                label = "Menú",
                color = ElysiumColors.NeonCyan,
                enabled = hasSignal(IrAction.MENU),
                onClick = { scope.launch { onAction(IrAction.MENU) } }
            )
        }

        // === SMART BUTTONS ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteSmartButton(
                action = IrAction.NETFLIX,
                label = "Netflix",
                color = Color(0xFFE50914),
                enabled = hasSignal(IrAction.NETFLIX),
                onClick = { scope.launch { onAction(IrAction.NETFLIX) } }
            )
            RemoteSmartButton(
                action = IrAction.YOUTUBE,
                label = "YouTube",
                color = Color(0xFFFF0000),
                enabled = hasSignal(IrAction.YOUTUBE),
                onClick = { scope.launch { onAction(IrAction.YOUTUBE) } }
            )
        }

        // === INFO / LAST ROW ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteSmallButton(
                action = IrAction.INFO,
                icon = Icons.Filled.Info,
                label = "Info",
                color = ElysiumColors.NeonCyan,
                enabled = hasSignal(IrAction.INFO),
                onClick = { scope.launch { onAction(IrAction.INFO) } }
            )
            RemoteSmallButton(
                action = IrAction.LAST_CHANNEL,
                icon = Icons.Filled.Refresh,
                label = "Último",
                color = ElysiumColors.NeonCyan,
                enabled = hasSignal(IrAction.LAST_CHANNEL),
                onClick = { scope.launch { onAction(IrAction.LAST_CHANNEL) } }
            )
            RemoteSmallButton(
                action = IrAction.INPUT,
                icon = Icons.Filled.LiveTv,
                label = "Fuente",
                color = ElysiumColors.NeonCyan,
                enabled = hasSignal(IrAction.INPUT),
                onClick = { scope.launch { onAction(IrAction.INPUT) } }
            )
        }

        // === MEDIA CONTROLS ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteSmallButton(
                action = IrAction.PLAY,
                icon = Icons.Filled.PlayArrow,
                label = "Play",
                color = ElysiumColors.NeonGreen,
                enabled = hasSignal(IrAction.PLAY),
                onClick = { scope.launch { onAction(IrAction.PLAY) } }
            )
            RemoteSmallButton(
                action = IrAction.PAUSE,
                icon = Icons.Filled.FastForward,
                label = "Pausa",
                color = ElysiumColors.NeonGreen,
                enabled = hasSignal(IrAction.PAUSE),
                onClick = { scope.launch { onAction(IrAction.PAUSE) } }
            )
            RemoteSmallButton(
                action = IrAction.STOP,
                icon = Icons.Filled.Stop,
                label = "Stop",
                color = ElysiumColors.NeonOrange,
                enabled = hasSignal(IrAction.STOP),
                onClick = { scope.launch { onAction(IrAction.STOP) } }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────
//  Button components
// ─────────────────────────────────────────────

@Composable
private fun RemoteCircleButton(
    action: IrAction,
    icon: ImageVector,
    label: String,
    color: Color,
    size: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "circle_scale"
    )
    val bgColor = if (enabled) color.copy(alpha = 0.20f) else Color.Gray.copy(alpha = 0.08f)
    val iconTint = if (enabled) color else Color.Gray.copy(alpha = 0.4f)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(bgColor, bgColor.copy(alpha = 0.05f))))
            .then(
                if (enabled) Modifier.clickable {
                    isPressed = true; onClick()
                    scope.launch { delay(100); isPressed = false }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size((size / 2).dp))
            if (label.isNotEmpty()) {
                Text(
                    label,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (enabled) ElysiumColors.OnSurface else Color.Gray.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RemoteSquareButton(
    action: IrAction,
    icon: ImageVector,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "sq_scale"
    )
    val bgColor = if (enabled) color.copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.06f)
    val iconTint = if (enabled) color else Color.Gray.copy(alpha = 0.4f)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .width(56.dp)
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(bgColor, bgColor.copy(alpha = 0.05f))))
            .then(
                if (enabled) Modifier.clickable {
                    isPressed = true; onClick()
                    scope.launch { delay(100); isPressed = false }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) ElysiumColors.OnSurface else Color.Gray.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RemoteSmallButton(
    action: IrAction,
    icon: ImageVector,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "small_scale"
    )
    val bgColor = if (enabled) color.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.06f)
    val iconTint = if (enabled) color else Color.Gray.copy(alpha = 0.4f)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .width(80.dp)
            .height(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(bgColor, bgColor.copy(alpha = 0.05f))))
            .then(
                if (enabled) Modifier.clickable {
                    isPressed = true; onClick()
                    scope.launch { delay(100); isPressed = false }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = if (enabled) ElysiumColors.OnSurface else Color.Gray.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun RemoteSmartButton(
    action: IrAction,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = tween(durationMillis = 80),
        label = "smart_scale"
    )
    val bgColor = if (enabled) color.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.06f)
    val textColor = if (enabled) color else Color.Gray.copy(alpha = 0.5f)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .width(120.dp)
            .height(36.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(bgColor, bgColor.copy(alpha = 0.10f))))
            .then(
                if (enabled) Modifier.clickable {
                    isPressed = true; onClick()
                    scope.launch { delay(100); isPressed = false }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
private fun RemoteNumberRow(
    actions: List<IrAction>,
    hasSignal: (IrAction) -> Boolean,
    onAction: suspend (IrAction) -> Unit
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        actions.forEach { action ->
            val numLabel = when (action) {
                IrAction.NUM_0 -> "0"
                IrAction.NUM_1 -> "1"
                IrAction.NUM_2 -> "2"
                IrAction.NUM_3 -> "3"
                IrAction.NUM_4 -> "4"
                IrAction.NUM_5 -> "5"
                IrAction.NUM_6 -> "6"
                IrAction.NUM_7 -> "7"
                IrAction.NUM_8 -> "8"
                IrAction.NUM_9 -> "9"
                IrAction.NUM_DASH -> "-"
                IrAction.NUM_PLUS -> "+"
                else -> "?"
            }
            val enabled = hasSignal(action)
            var isPressed by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.88f else 1.0f,
                animationSpec = tween(durationMillis = 80),
                label = "num_scale"
            )
            val bgColor = if (enabled) ElysiumColors.NeonCyan.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.06f)
            val textColor = if (enabled) ElysiumColors.NeonCyan else Color.Gray.copy(alpha = 0.4f)

            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(44.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(bgColor, bgColor.copy(alpha = 0.05f))))
                    .then(
                        if (enabled) Modifier.clickable {
                            isPressed = true; scope.launch { onAction(action) }
                            scope.launch { delay(100); isPressed = false }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    numLabel,
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = textColor
                )
            }
        }
    }
}
