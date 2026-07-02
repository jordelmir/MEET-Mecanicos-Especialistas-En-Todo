package com.elysium.vanguard.forge.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.forge.presentation.theme.ForgeColors

/**
 * Card neón con borde teal — identidad ELYSIUM.
 */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    accentColor: Color = ForgeColors.Primary,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = ForgeColors.Surface,
            contentColor = ForgeColors.OnSurface
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick ?: {}
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

/**
 * Badge de provenance. Color según tipo.
 */
@Composable
fun ProvenanceBadge(
    label: String,
    color: Color = ForgeColors.ProvenanceOffline,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Badge de severidad.
 */
@Composable
fun SeverityBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Etiqueta técnica monoespaciada.
 */
@Composable
fun TechLabel(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

/**
 * Indicador LED-style (punto + label) — usado para estados.
 */
@Composable
fun StatusLed(
    label: String,
    color: Color,
    pulse: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Sección con título y divisor neón.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color = ForgeColors.Primary
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(2.dp)
                    .width(32.dp)
                    .background(accentColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Estado genérico de UI para ViewModels (Loading/Ready/Empty/Error pattern).
 */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Ready<T>(val data: T) : UiState<T>()
    data object Empty : UiState<Nothing>()
    data class Error(val message: String, val recoverable: Boolean = true) : UiState<Nothing>()
}

/**
 * Padded box estándar Forge.
 */
@Composable
fun ForgePaddedBox(
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.padding(padding)) {
        content()
    }
}