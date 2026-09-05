package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

enum class PttVoiceState {
    IDLE,
    REQUESTING,
    TRANSMITTING,
    RECEIVING
}

/**
 * PttFloatingButton — World-Class Push-To-Talk Voice Control.
 * Realizes instantaneous two-way voice communication between rider, driver, and emergency dispatch.
 */
@Composable
fun PttFloatingButton(
    modifier: Modifier = Modifier,
    state: PttVoiceState = PttVoiceState.IDLE,
    activeSpeakerName: String? = null,
    onPressStart: () -> Unit = {},
    onPressEnd: () -> Unit = {},
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PttPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val (bgColor, ringColor, statusLabel) = when (state) {
        PttVoiceState.IDLE -> Triple(
            MeetColors.cardBackground,
            MeetColors.neonGreen.copy(alpha = 0.4f),
            "Presiona para hablar"
        )
        PttVoiceState.REQUESTING -> Triple(
            Color(0xFFE65100),
            Color(0xFFFFB74D),
            "Solicitando turno..."
        )
        PttVoiceState.TRANSMITTING -> Triple(
            MeetColors.neonGreen,
            MeetColors.neonGreen.copy(alpha = 0.5f),
            "Transmitiendo en vivo"
        )
        PttVoiceState.RECEIVING -> Triple(
            MeetColors.electricBlue,
            MeetColors.electricBlue.copy(alpha = 0.5f),
            "Escuchando a ${activeSpeakerName ?: "otro"}"
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp)
        ) {
            if (state == PttVoiceState.TRANSMITTING || state == PttVoiceState.RECEIVING) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(ringColor)
                )
            }

            Surface(
                shape = CircleShape,
                color = bgColor,
                border = androidx.compose.foundation.BorderStroke(2.dp, ringColor),
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onPressStart()
                                tryAwaitRelease()
                                onPressEnd()
                            }
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (state == PttVoiceState.RECEIVING) Icons.Default.VolumeUp else Icons.Default.Mic,
                        contentDescription = "Push To Talk",
                        tint = if (state == PttVoiceState.TRANSMITTING) Color.Black else MeetColors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (state == PttVoiceState.TRANSMITTING) MeetColors.neonGreen else MeetColors.textSecondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * PttAudioSessionBar — Audio status strip showing channel, active speaker, and encryption status.
 */
@Composable
fun PttAudioSessionBar(
    channelName: String = "Canal Directo Viaje",
    state: PttVoiceState = PttVoiceState.IDLE,
    speakerName: String? = null,
    latencyMs: Long = 18L,
    modifier: Modifier = Modifier,
    onMuteToggle: () -> Unit = {}
) {
    var isMuted by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                PttVoiceState.TRANSMITTING -> MeetColors.neonGreen
                                PttVoiceState.RECEIVING -> MeetColors.electricBlue
                                else -> Color.Gray
                            }
                        )
                )

                Column {
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MeetColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (state) {
                            PttVoiceState.TRANSMITTING -> "🎙️ Tu micrófono está abierto"
                            PttVoiceState.RECEIVING -> "🔊 ${speakerName ?: "Conductor"} hablando"
                            else -> "PTT Listo • ${latencyMs}ms • Cifrado E2E"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(
                onClick = {
                    isMuted = !isMuted
                    onMuteToggle()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mute",
                    tint = if (isMuted) MeetColors.error else MeetColors.neonGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
