package com.elysium369.meet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ui.elysium.theme.ElysiumTheme

/** Shared home identity: original artwork and reactive four-channel energy rail. */
@Composable
fun ElysiumVanguardHeader() {
    val colors = ElysiumTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "ELYSIUM",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = colors.textPrimary,
        )
        Text(
            "VANGUARD AI OS",
            style = MaterialTheme.typography.titleMedium,
            color = colors.secondary,
            textAlign = TextAlign.Center,
        )
        Text(
            "OBD2 · AI · MOBILITY",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Spacer(
            Modifier.fillMaxWidth().height(2.dp).background(
                Brush.horizontalGradient(listOf(
                    colors.primary,
                    colors.secondary,
                    colors.tertiary,
                    colors.quaternary,
                ))
            )
        )
    }
}
