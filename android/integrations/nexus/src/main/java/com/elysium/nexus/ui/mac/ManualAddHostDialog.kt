package com.elysium.nexus.ui.mac

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip

/**
 * A dialog for adding a Mac/PC host manually.
 *
 * Use case: the Mac agent is on the same Wi-Fi
 * but Bonjour / mDNS discovery is blocked
 * (corporate networks, some firewalls, or
 * the user knows the IP from the Mac's
 * System Settings).
 *
 * The user types the host's IP address and
 * port (default 7878, the agent's listening
 * port), taps "Conectar", and the activity
 * routes to the pairing flow with a synthetic
 * `DiscoveredHost`.
 */
@Composable
fun ManualAddHostDialog(
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7878") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ElysiumColors.SurfaceHigh,
                            ElysiumColors.Surface
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Computer,
                            contentDescription = null,
                            tint = ElysiumColors.NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Agregar Mac/PC",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = ElysiumColors.NeonCyan
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElysiumColors.Surface)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = ElysiumColors.OnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "Escribe la IP de tu Mac o PC. La encontrarás en " +
                        "Preferencias del Sistema → Red en macOS, o " +
                        "ejecutando 'ipconfig' en Windows / 'ip addr' en Linux.",
                    style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                    color = ElysiumColors.OnSurfaceMuted
                )
                NeonCard(
                    accent = ElysiumColors.NeonCyan,
                    cornerRadius = 12.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "IP / hostname",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = ElysiumColors.NeonCyan
                        )
                        BasicTextField(
                            value = host,
                            onValueChange = {
                                if (it.length <= 64) host = it
                                errorText = null
                            },
                            textStyle = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = ElysiumColors.OnSurface
                            ),
                            cursorBrush = SolidColor(ElysiumColors.NeonCyan),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri
                            ),
                            singleLine = true,
                            decorationBox = { inner ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (host.isEmpty()) {
                                        Text(
                                            text = "192.168.1.42",
                                            style = TextStyle(
                                                fontSize = 18.sp,
                                                color = ElysiumColors.OnSurfaceMuted
                                            )
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Puerto (por defecto 7878)",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = ElysiumColors.NeonCyan
                        )
                        BasicTextField(
                            value = port,
                            onValueChange = {
                                if (it.length <= 5 && it.all { c -> c.isDigit() }) port = it
                            },
                            textStyle = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = ElysiumColors.OnSurface
                            ),
                            cursorBrush = SolidColor(ElysiumColors.NeonCyan),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true
                        )
                    }
                }
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = TextStyle(fontSize = 12.sp),
                        color = ElysiumColors.NeonMagenta
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeonChip(
                        label = "Cancelar",
                        onClick = onDismiss,
                        accent = ElysiumColors.OnSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    NeonChip(
                        label = "Conectar",
                        onClick = {
                            val trimmed = host.trim()
                            val portInt = port.toIntOrNull() ?: 7878
                            when {
                                trimmed.isEmpty() -> errorText = "La IP no puede estar vacía"
                                portInt !in 1..65535 -> errorText = "Puerto inválido"
                                else -> onConnect(trimmed, portInt)
                            }
                        },
                        accent = ElysiumColors.NeonGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
