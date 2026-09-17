package com.elysium369.meet.ride.driver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.driver.NavigationGuidance
import com.elysium369.meet.ride.map.RideManeuverFormatter
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun DriverNavigationHeader(
    guidance: NavigationGuidance,
    modifier: Modifier = Modifier,
) {
    val symbol = RideManeuverFormatter.formatManeuverSymbol(
        guidance.maneuverType,
        guidance.maneuverModifier,
    )
    val distanceText = RideManeuverFormatter.formatDistance(guidance.distanceToManeuverMeters)
    val instructionText = RideManeuverFormatter.formatInstruction(
        guidance.maneuverType,
        guidance.maneuverModifier,
        guidance.streetName,
    )
    val remainingDistanceText = RideManeuverFormatter.formatDistance(guidance.remainingDistanceMeters)
    val remainingDurationText = RideManeuverFormatter.formatDuration(guidance.remainingDurationSeconds)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = MeetColors.cyberCyan),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MeetColors.cardBackground.copy(alpha = 0.96f),
        ),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Maneuver Glyph Circle
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(MeetColors.backgroundDark, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    color = MeetColors.neonGreen,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Guidance & Street Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = distanceText,
                        color = MeetColors.cyberCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "· $remainingDurationText ($remainingDistanceText)",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Text(
                    text = instructionText,
                    color = MeetColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
