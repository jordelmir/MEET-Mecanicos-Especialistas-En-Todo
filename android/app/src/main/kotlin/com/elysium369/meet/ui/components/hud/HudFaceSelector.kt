package com.elysium369.meet.ui.components.hud

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Horizontal scrollable selector for HUD face types.
 * Shows each face as a compact chip with icon + name.
 */
@Composable
fun HudFaceSelector(
    currentFace: HudFaceType,
    onFaceSelected: (HudFaceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "sel")
    val glow by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "selGlow"
    )

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(HudFaceType.entries) { face ->
            val isSelected = face == currentFace
            val accentColor = if (isSelected) MeetColors.neonGreen else MeetColors.textSecondary

            Box(
                modifier = Modifier
                    .then(
                        if (isSelected) Modifier.shadow(
                            8.dp, RoundedCornerShape(10.dp),
                            ambientColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                            spotColor = MeetColors.neonGreen.copy(alpha = 0.4f)
                        ) else Modifier
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected)
                            Brush.horizontalGradient(
                                listOf(
                                    MeetColors.neonGreen.copy(alpha = 0.12f),
                                    MeetColors.cyberCyan.copy(alpha = 0.08f)
                                )
                            )
                        else
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF111111),
                                    Color(0xFF0A0A0A)
                                )
                            )
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSelected)
                            MeetColors.neonGreen.copy(alpha = 0.6f * glow)
                        else
                            Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onFaceSelected(face) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(face.icon, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        face.displayName.uppercase(),
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        letterSpacing = if (isSelected) 1.sp else 0.5.sp
                    )
                }
            }
        }
    }
}
