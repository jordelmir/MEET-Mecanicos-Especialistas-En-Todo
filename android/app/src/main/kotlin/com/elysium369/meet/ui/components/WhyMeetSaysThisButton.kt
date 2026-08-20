package com.elysium369.meet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun WhyMeetSaysThisButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() },
        color = MeetColors.electricBlue.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("🔍", fontSize = 10.sp)
            Text(
                "¿Por qué MEET dice esto?",
                color = MeetColors.electricBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
