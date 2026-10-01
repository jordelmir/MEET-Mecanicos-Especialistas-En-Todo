package com.elysium.nexus.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.elysium.nexus.core.transport.ControllerTransport
import com.elysium.nexus.core.transport.TransportState
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * The §17 transport selector — **Phase ULT.2**.
 *
 * Renders a horizontally-scrolling row of
 * [NeonChip]s (one per transport). The current
 * transport is the "active" chip — full-strength
 * purple accent. Tapping a non-current chip calls
 * [onTransportSelected].
 *
 * The transport's [TransportState] is rendered as
 * a small inline [NeonStatusPill] next to the
 * transport name. The status pill uses green for
 * `CONNECTED`, magenta for `ERROR`, cyan for
 * `IDLE` / `INITIALISING`, and orange for
 * `PAIRED`.
 */
@Composable
fun TransportSelector(
    transports: List<ControllerTransport>,
    currentTransport: ControllerTransport,
    onTransportSelected: (ControllerTransport) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        transports.forEach { transport ->
            val isSelected = transport === currentTransport
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NeonChip(
                    label = transport.capabilities.label,
                    onClick = { if (!isSelected) onTransportSelected(transport) },
                    accent = ElysiumColors.NeonPurple,
                    active = isSelected,
                    icon = { Icon(transportIconFor(transport.capabilities.label), contentDescription = null) }
                )
                NeonStatusPill(
                    label = stateLabel(transport.state),
                    color = stateAccent(transport.state)
                )
            }
        }
    }
}

private fun stateLabel(state: TransportState): String = when (state) {
    TransportState.IDLE -> "IDLE"
    TransportState.INITIALISING -> "INIT"
    TransportState.PAIRED -> "PAIRED"
    TransportState.CONNECTED -> "LIVE"
    TransportState.DISCONNECTED -> "OFF"
    TransportState.ERROR -> "ERR"
}

private fun stateAccent(state: TransportState): Color = when (state) {
    TransportState.CONNECTED -> ElysiumColors.NeonGreen
    TransportState.ERROR -> ElysiumColors.NeonMagenta
    TransportState.PAIRED -> ElysiumColors.NeonOrange
    else -> ElysiumColors.NeonCyan
}

private fun transportIconFor(label: String): ImageVector {
    val lower = label.lowercase()
    return when {
        "bluetooth" in lower -> Icons.Filled.Bluetooth
        "usb" in lower -> Icons.Filled.Usb
        "link" in lower || "elysium" in lower -> Icons.Filled.SettingsInputAntenna
        else -> Icons.Filled.SettingsInputAntenna
    }
}
