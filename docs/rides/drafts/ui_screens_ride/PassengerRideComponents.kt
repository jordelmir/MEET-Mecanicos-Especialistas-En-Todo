package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clock
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun FareModeSelector(
    selectedMode: RideFareMode,
    fareQuote: FareQuote?,
    onModeClick: () -> Unit,
) {
    val expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable { onModeClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MeetColors.neonGreen.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.padding(8.dp))
                }
                Column {
                    Text("Modalidad de tarifa", style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Medium)
                    Text(selectedMode.displayName, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MeetColors.textSecondary
            )
        }

        // Expanded fare breakdown
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (fareQuote != null) {
                    FareBreakdownRow("Tarifa base", fareQuote.baseFare, MeetColors.neonGreen)
                    FareBreakdownRow("Distancia (${fareQuote.formattedDistance})", fareQuote.distanceFare, MeetColors.electricBlue)
                    FareBreakdownRow("Tiempo (${fareQuote.formattedDuration})", fareQuote.timeFare, MeetColors.electricPurple)
                    Divider(color = MeetColors.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    FareBreakdownRow("Total estimado", fareQuote.totalFare, MeetColors.neonGreen, isTotal = true)
                }

                // Mode options
                Spacer(Modifier.height(12.dp))
                Text("Otras modalidades", style = MaterialTheme.typography.labelMedium, color = MeetColors.textSecondary)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FareModeChip(
                        mode = RideFareMode.METERED,
                        isSelected = selectedMode == RideFareMode.METERED,
                        onClick = { /* Select */ }
                    )
                    FareModeChip(
                        mode = RideFareMode.OPEN_BID,
                        isSelected = selectedMode == RideFareMode.OPEN_BID,
                        onClick = { /* Select */ }
                    )
                }
            }
        }
    }
}

@Composable
fun FareModeChip(
    mode: RideFareMode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val (icon, color) = when (mode) {
        RideFareMode.METERED -> Icons.Default.DirectionsCar to MeetColors.neonGreen
        RideFareMode.OPEN_BID -> Icons.Default.AttachMoney to MeetColors.electricBlue
    }

    Card(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else MeetColors.cardBackground,
            contentColor = if (isSelected) color else MeetColors.textPrimary
        ),
        border = if (isSelected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MeetColors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected) color else MeetColors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Text(mode.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = if (isSelected) color else MeetColors.textPrimary)
            Text(mode.shortDescription, style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
fun FareBreakdownRow(label: String, amount: Long, color: Color, isTotal: Boolean = false) {
    val formatted = "₡${amount.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "\$1,")}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (isTotal) MeetColors.textPrimary else MeetColors.textSecondary,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            formatted,
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (isTotal) color else MeetColors.textSecondary,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun PaymentMethodSelector(
    selectedMethod: RidePaymentMethod,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MeetColors.cardBackground,
            contentColor = MeetColors.textPrimary
        ),
        border = BorderStroke(1.dp, MeetColors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MeetColors.electricBlue.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MeetColors.electricBlue, modifier = Modifier.padding(8.dp))
                }
                Column {
                    Text("Método de pago", style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Medium)
                    Text(selectedMethod.displayName, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                }
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MeetColors.textSecondary)
        }
    }
}

@Composable
fun RequestRideButton(
    fareQuote: FareQuote,
    isRequesting: Boolean,
    onClick: () -> Unit,
) {
    val buttonText = if (isRequesting) "Buscando conductor..." else "Solicitar viaje por ${fareQuote.formattedTotal}"

    Button(
        onClick = { if (!isRequesting) onClick() },
        enabled = !isRequesting,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MeetColors.neonGreen,
            contentColor = Color.Black
        )
    ) {
        if (isRequesting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(buttonText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        } else {
            Text(buttonText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun RideInputCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    iconColor: Color,
    isActive: Boolean = false,
    onClick: () -> Unit,
    showCurrentLocation: Boolean = false,
    currentLocation: com.elysium369.meet.ride.map.RideLocationPoint? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() }
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MeetColors.neonGreen.copy(alpha = 0.08f) else MeetColors.cardBackground,
            contentColor = MeetColors.textPrimary
        ),
        border = if (isActive) BorderStroke(2.dp, iconColor) else BorderStroke(1.dp, MeetColors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                icon()
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MeetColors.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MeetColors.textPrimary, maxLines = 1)
            }

            if (showCurrentLocation && currentLocation != null) {
                Badge(
                    badgeContent = { Text("GPS", style = MaterialTheme.typography.labelSmall, color = Color.Black) },
                    modifier = Modifier.padding(start = 8.dp),
                    backgroundColor = MeetColors.neonGreen,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

@Composable
fun InfoCard(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    color: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MeetColors.cardBackground,
            contentColor = MeetColors.textPrimary
        ),
        border = BorderStroke(1.dp, MeetColors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                icon()
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Medium)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
            }
        }
    }
}