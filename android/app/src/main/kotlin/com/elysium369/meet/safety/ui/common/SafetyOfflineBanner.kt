package com.elysium369.meet.safety.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Premium offline indicator banner.
 * Shown at the top of any safety screen when the device is offline.
 * Subtle pulsing animation to attract attention without being intrusive.
 */
@Composable
fun SafetyOfflineBanner(
    pendingCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "offline-pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "offline-alpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(pulseAlpha)
            .clip(RoundedCornerShape(12.dp))
            .background(MeetColors.warning.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = "Sin conexión",
                modifier = Modifier.size(18.dp),
                tint = MeetColors.warning,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Datos locales",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MeetColors.warning,
            )
            Text(
                text = " • ",
                fontSize = 12.sp,
                color = MeetColors.textSecondary,
            )
            Text(
                text = if (pendingCount > 0) "$pendingCount pendiente(s)"
                else "Sincronización pendiente",
                fontSize = 12.sp,
                color = MeetColors.textSecondary,
            )
        }
    }
}
