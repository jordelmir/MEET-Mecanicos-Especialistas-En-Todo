package com.elysium.nexus.ui.control

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.fabric.infrared.IrLearner
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill

import androidx.compose.ui.unit.dp

/**
 * The IR Learner screen.
 *
 * Shows the result of an IR capture session.
 * The user points their phone's IR receiver
 * at a remote and presses a button; the
 * learner decodes the protocol and displays
 * the result.
 *
 * The screen shows:
 *  - Protocol detected (NEC, RC5, SIRC, Samsung)
 *  - Address and command values
 *  - Carrier frequency
 *  - Confidence score
 *  - Raw waveform (collapsible)
 */
@Composable
fun IrLearnerScreen(
    learnResult: IrLearner.LearnResult?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (IrLearner.LearnResult) -> Unit,
    onTransmit: (IrLearner.LearnResult) -> Unit = {},
    listenPort: Int = com.elysium.nexus.fabric.infrared.IrCaptureBridge.DEFAULT_PORT,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var showRawWaveform by remember { mutableStateOf(false) }

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
                NeonChip(
                    label = "Reintentar",
                    onClick = onRetry,
                    accent = ElysiumColors.NeonCyan,
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }
                )
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

            if (learnResult == null) {
                // No result yet — listening on the capture bridge
                WaitingForCapture(info.sidePadding, listenPort)
            } else {
                // Show the result
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = info.sidePadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 16.dp
                    )
                ) {
                    item {
                        NeonHeroCard(
                            title = "Señal capturada",
                            subtitle = learnResult.command?.protocol?.name ?: "Desconocido",
                            accent = when {
                                learnResult.confidence >= 0.9f -> ElysiumColors.NeonGreen
                                learnResult.confidence >= 0.6f -> ElysiumColors.NeonOrange
                                else -> ElysiumColors.NeonMagenta
                            },
                            modifier = Modifier.fillMaxWidth(),
                            statusChips = {
                                NeonStatusPill(
                                    label = "${(learnResult.confidence * 100).toInt()}%",
                                    color = when {
                                        learnResult.confidence >= 0.9f -> ElysiumColors.NeonGreen
                                        learnResult.confidence >= 0.6f -> ElysiumColors.NeonOrange
                                        else -> ElysiumColors.NeonMagenta
                                    }
                                )
                                NeonStatusPill(
                                    label = "${learnResult.carrierHz / 1000} kHz",
                                    color = ElysiumColors.NeonCyan
                                )
                            }
                        )
                    }

                    item {
                        ProtocolDetails(learnResult)
                    }

                    item {
                        NeonCard(
                            modifier = Modifier.fillMaxWidth(),
                            accent = ElysiumColors.NeonGreen,
                            cornerRadius = 12.dp,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSave(learnResult) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Guardar señal",
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ElysiumColors.OnSurface
                                )
                                Text(
                                    text = "La señal se guardará en la base de datos IR",
                                    style = TextStyle(fontSize = 12.sp),
                                    color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    item {
                        NeonCard(
                            modifier = Modifier.fillMaxWidth(),
                            accent = ElysiumColors.NeonCyan,
                            cornerRadius = 12.dp,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTransmit(learnResult) }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Probar transmitir",
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ElysiumColors.OnSurface
                                )
                                Text(
                                    text = "Dispara la señal por el emisor IR del teléfono",
                                    style = TextStyle(fontSize = 12.sp),
                                    color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    item {
                        NeonCard(
                            modifier = Modifier.fillMaxWidth(),
                            accent = ElysiumColors.NeonPurple,
                            cornerRadius = 12.dp,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRawWaveform = !showRawWaveform }
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = ElysiumColors.NeonPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (showRawWaveform) "Ocultar forma de onda" else "Ver forma de onda",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = ElysiumColors.OnSurface
                                    )
                                }
                                if (showRawWaveform) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = learnResult.rawWaveform.pattern.take(60).joinToString(" ") {
                                            "${it}µs"
                                        },
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = ElysiumColors.OnSurface.copy(alpha = 0.7f)
                                    )
                                    if (learnResult.rawWaveform.pattern.size > 60) {
                                        Text(
                                            text = "... +${learnResult.rawWaveform.pattern.size - 60} entradas más",
                                            style = TextStyle(fontSize = 11.sp),
                                            color = ElysiumColors.NeonCyan
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

    if (showHelp) {
        HelpCard(
            title = "Ayuda — IR Learner",
            whatIsThis = "El IR Learner captura señales infrarrojas de controles remotos y las identifica.",
            howToUse = listOf(
                "Apunta el control remoto hacia la parte de arriba del telefono.",
                "Toca el boton que quieras capturar en el control remoto.",
                "El learner decodificara la señal y mostrara el protocolo detectado.",
                "Si la confianza es alta (>90%), guarda la señal en la base de datos."
            ),
            tip = "Si la señal no se detecta, acercate al control remoto (menos de 1 metro) y asegurate de que no haya luz solar directa.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun WaitingForCapture(sidePadding: androidx.compose.ui.unit.Dp, listenPort: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = ElysiumColors.NeonOrange.copy(alpha = pulse),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Esperando señal IR...",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Apunta el control remoto hacia la parte\nde arriba del telefono y presiona un boton.",
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
            )
            NeonChip(
                label = "Receptor en puerto $listenPort",
                onClick = {},
                accent = ElysiumColors.NeonCyan,
                icon = { Icon(Icons.Filled.Info, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun ProtocolDetails(result: IrLearner.LearnResult) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 12.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailRow("Protocolo", result.command?.protocol?.name ?: "Desconocido")
            DetailRow("Dirección", "0x${(result.command?.address ?: 0).toString(16).uppercase()}")
            DetailRow("Comando", "0x${(result.command?.command ?: 0).toString(16).uppercase()}")
            DetailRow("Portadora", "${result.carrierHz / 1000} kHz")
            DetailRow("Confianza", "${(result.confidence * 100).toInt()}%")
            result.command?.extras?.forEach { (key, value) ->
                DetailRow(key, value)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = ElysiumColors.OnSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = ElysiumColors.OnSurface
        )
    }
}
