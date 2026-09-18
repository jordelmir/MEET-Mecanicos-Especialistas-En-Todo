package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.communications.LiveCallState
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay

/**
 * RideLiveCallOverlay — World-class in-call HUD overlay for driver and passenger.
 *
 * Shows real-time call duration, peer connection status, audio muting, and dual-transport state
 * (Supabase Realtime Cloud Broadcast + Local UDP Peer-to-Peer Mesh).
 */
@Composable
fun RideLiveCallOverlay(
    state: LiveCallState,
    onToggleMute: () -> Unit,
    onHangUp: () -> Unit,
    onAnswer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state is LiveCallState.Connecting || state is LiveCallState.Incoming || state is LiveCallState.Active || state is LiveCallState.Ended,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        when (state) {
            is LiveCallState.Incoming -> {
                IncomingCallCard(state, onAnswer, onHangUp)
            }
            is LiveCallState.Connecting -> {
                ConnectingCallCard(state)
            }
            is LiveCallState.Active -> {
                ActiveCallCard(state, onToggleMute, onHangUp)
            }
            is LiveCallState.Ended -> {
                EndedCallCard(state.reason)
            }
            else -> Unit
        }
    }
}

@Composable
private fun ConnectingCallCard(state: LiveCallState.Connecting) {
    val pulseTransition = rememberInfiniteTransition(label = "pulse_connecting")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF00A1926),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MeetColors.cyberCyan.copy(alpha = pulseAlpha)),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MeetColors.cyberCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    tint = MeetColors.cyberCyan,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CONECTANDO LLAMADA EN VIVO",
                    color = MeetColors.cyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = "Descubrimiento directo Cloud & Mesh...",
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ActiveCallCard(
    state: LiveCallState.Active,
    onToggleMute: () -> Unit,
    onHangUp: () -> Unit,
) {
    var elapsedSeconds by remember(state.startedAtEpochMs) {
        mutableLongStateOf((System.currentTimeMillis() - state.startedAtEpochMs).coerceAtLeast(0L) / 1000L)
    }

    LaunchedEffect(state.startedAtEpochMs) {
        while (true) {
            delay(1000L)
            elapsedSeconds = ((System.currentTimeMillis() - state.startedAtEpochMs).coerceAtLeast(0L) / 1000L)
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val formattedDuration = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF5061623),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MeetColors.neonGreen.copy(alpha = 0.85f)),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (state.peerConnected) MeetColors.neonGreen else Color(0xFFFFB300)),
                    )
                    Text(
                        text = if (state.peerConnected) "EN LLAMADA EN VIVO" else "ESPERANDO PEER...",
                        color = if (state.peerConnected) MeetColors.neonGreen else Color(0xFFFFB300),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = formattedDuration,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Transport details chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = "📡 ${state.transport}",
                        color = MeetColors.cyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (state.isMuted) {
                    Surface(
                        color = MeetColors.error.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "🔇 MICRÓFONO SILENCIADO",
                            color = MeetColors.error,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onToggleMute,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (state.isMuted) MeetColors.error.copy(alpha = 0.25f) else Color(0xFF1E3A52),
                    ),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .border(1.dp, if (state.isMuted) MeetColors.error else MeetColors.cyberCyan, CircleShape),
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (state.isMuted) "Activar micrófono" else "Silenciar micrófono",
                        tint = if (state.isMuted) MeetColors.error else MeetColors.cyberCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                IconButton(
                    onClick = onHangUp,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Colgar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EndedCallCard(reason: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF01A1A1A),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = null,
                tint = MeetColors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "Llamada finalizada (${reason.take(28)})",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun IncomingCallCard(
    state: LiveCallState.Incoming,
    onAnswer: (() -> Unit)?,
    onReject: () -> Unit,
) {
    val pulseTransition = rememberInfiniteTransition(label = "pulse_incoming")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "incoming_alpha",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF00A1926),
        border = BorderStroke(2.dp, MeetColors.neonGreen.copy(alpha = pulseAlpha)),
        shadowElevation = 16.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MeetColors.neonGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column {
                    Text(
                        text = "LLAMADA EN VIVO ENTRANTE",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = if (state.callerRole.equals("DRIVER", ignoreCase = true)) {
                            "El chofer te está llamando"
                        } else {
                            "El pasajero te está llamando"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Reject Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeetColors.error)
                        .clickable { onReject() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Rechazar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Answer Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeetColors.neonGreen)
                        .clickable { onAnswer?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = "Contestar",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
