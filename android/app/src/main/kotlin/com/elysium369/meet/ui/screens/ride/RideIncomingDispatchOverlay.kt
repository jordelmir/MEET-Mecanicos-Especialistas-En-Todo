package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ui.screens.calculateDistance
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * DiDi/Uber style floating heads-up dispatch card.
 * Appears anchored at the bottom of the driver screen when an eligible ride request
 * is available. Features a 25-second animated countdown and a 1-tap "ACEPTAR VIAJE REAL" CTA.
 */
@Composable
fun RideIncomingDispatchOverlay(
    ride: RideRequestEntity,
    driverLat: Double?,
    driverLng: Double?,
    onAccept: (RideRequestEntity) -> Unit,
    onCounterOffer: (RideRequestEntity) -> Unit,
    onDismiss: (RideRequestEntity) -> Unit,
    modifier: Modifier = Modifier,
    timeoutSeconds: Int = 25,
) {
    val haptic = LocalHapticFeedback.current
    var secondsRemaining by remember(ride.requestId) { mutableIntStateOf(timeoutSeconds) }
    var isDismissed by remember(ride.requestId) { mutableStateOf(false) }

    // Haptic buzz when incoming ride is shown
    LaunchedEffect(ride.requestId) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}
    }

    // Countdown timer
    LaunchedEffect(ride.requestId, isDismissed) {
        if (isDismissed) return@LaunchedEffect
        secondsRemaining = timeoutSeconds
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
        if (!isDismissed) {
            onDismiss(ride)
        }
    }

    val progress by animateFloatAsState(
        targetValue = secondsRemaining.toFloat() / timeoutSeconds.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "dispatch_progress"
    )

    val distanceText = remember(driverLat, driverLng, ride.pickupLatitude, ride.pickupLongitude) {
        if (driverLat != null && driverLng != null) {
            val distKm = calculateDistance(driverLat, driverLng, ride.pickupLatitude, ride.pickupLongitude)
            String.format(Locale.US, "%.1f km al cliente (~%d min)", distKm, (distKm * 2.5).toInt().coerceAtLeast(2))
        } else {
            "Cliente en punto de recogida"
        }
    }

    val isUrgent = secondsRemaining <= 5
    val badgeColor: Color = if (isUrgent) MeetColors.error else Color.White
    val badgeBgColor: Color = if (isUrgent) MeetColors.error.copy(alpha = 0.25f) else Color(0xFF1B2E4B)

    AnimatedVisibility(
        visible = !isDismissed && secondsRemaining > 0,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0D1826)
            ),
            border = BorderStroke(1.5.dp, MeetColors.cyberCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Countdown Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isUrgent) MeetColors.error else MeetColors.cyberCyan,
                    trackColor = Color(0xFF1B2E4B),
                )

                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MeetColors.cyberCyan.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🚨", fontSize = 14.sp)
                            }
                        }
                        Text(
                            "NUEVO VIAJE DISPONIBLE",
                            color = MeetColors.cyberCyan,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = badgeBgColor,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "${secondsRemaining}s",
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                isDismissed = true
                                onDismiss(ride)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Ignorar",
                                tint = MeetColors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Price and Distance Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF142236))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "TARIFA DEL PASAJERO",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "₡ ${ride.priceOffer.toInt()}",
                            color = Color(0xFFFFD700),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "DISTANCIA ESTIMADA",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            distanceText,
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Route summary: Pickup -> Destination
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MeetColors.neonGreen,
                            shape = CircleShape,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Text(
                            ride.pickupAddress,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MeetColors.error,
                            shape = CircleShape,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Text(
                            ride.destAddress,
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Secondary Counter-Offer Button (+500 CRC)
                    OutlinedButton(
                        onClick = {
                            isDismissed = true
                            onCounterOffer(ride)
                        },
                        modifier = Modifier
                            .weight(0.42f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MeetColors.cyberCyan
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            "CONTRAOFERTA ⚡",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    // Dominant 1-Tap Real Ride Acceptance
                    Button(
                        onClick = {
                            if (!isDismissed) {
                                isDismissed = true
                                onAccept(ride)
                            }
                        },
                        enabled = !isDismissed,
                        modifier = Modifier
                            .weight(0.58f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDismissed) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.neonGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = MeetColors.neonGreen.copy(alpha = 0.4f),
                            disabledContentColor = Color.Black.copy(alpha = 0.5f),
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        if (isDismissed) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "ACEPTANDO...",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                "ACEPTAR VIAJE (₡${ride.priceOffer.toInt()}) 🚕",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
