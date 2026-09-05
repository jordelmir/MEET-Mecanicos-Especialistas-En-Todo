package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.payment.RidePaymentMethod
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun FareModeSelector(
    selectedMode: RideFareMode,
    fareQuote: FareQuote?,
    onModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onModeClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
            border = BorderStroke(1.dp, MeetColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MeetColors.neonGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = MeetColors.neonGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                "Modalidad de tarifa",
                                style = MaterialTheme.typography.titleSmall,
                                color = MeetColors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                selectedMode.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MeetColors.textSecondary
                            )
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expandir detalles",
                            tint = MeetColors.textSecondary
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (fareQuote != null) {
                            FareBreakdownRow("Tarifa base", fareQuote.baseFare, MeetColors.neonGreen)
                            FareBreakdownRow("Distancia (${fareQuote.formattedDistance})", fareQuote.distanceFare, MeetColors.electricBlue)
                            FareBreakdownRow("Tiempo (${fareQuote.formattedDuration})", fareQuote.timeFare, MeetColors.hotMagenta)
                            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            FareBreakdownRow("Total estimado", fareQuote.totalFare, MeetColors.neonGreen, isTotal = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.FareModeChip(
    mode: RideFareMode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val (icon, color) = when (mode) {
        RideFareMode.METERED_TIME_DISTANCE -> Icons.Default.DirectionsCar to MeetColors.neonGreen
        RideFareMode.OPEN_BID -> Icons.Default.AttachMoney to MeetColors.electricBlue
    }

    Card(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else MeetColors.cardBackground,
            contentColor = if (isSelected) color else MeetColors.textPrimary
        ),
        border = if (isSelected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected) color else MeetColors.textSecondary, modifier = Modifier.size(24.dp))
            Text(mode.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (isSelected) color else MeetColors.textPrimary)
            Text(mode.shortDescription, style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary, textAlign = TextAlign.Center, maxLines = 2, fontSize = 10.sp)
        }
    }
}

@Composable
fun FareBreakdownRow(label: String, amount: Long, color: Color, isTotal: Boolean = false) {
    val formatted = "₡${amount.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (isTotal) MeetColors.textPrimary else MeetColors.textSecondary,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            formatted,
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
            color = if (isTotal) color else MeetColors.textPrimary,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun PaymentMethodSelector(
    selectedMethod: RidePaymentMethod,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MeetColors.electricBlue.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MeetColors.electricBlue, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text("Método de pago", style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(selectedMethod.displayName, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                }
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = MeetColors.textSecondary)
        }
    }
}

@Composable
fun RequestRideButton(
    fareQuote: FareQuote?,
    isRequesting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonText = when {
        isRequesting -> "Buscando conductor cercano..."
        fareQuote != null -> "Solicitar viaje • ${fareQuote.formattedTotal}"
        else -> "Solicitar viaje"
    }

    Button(
        onClick = { if (!isRequesting) onClick() },
        enabled = !isRequesting,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
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
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(buttonText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        } else {
            Text(buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) iconColor else MeetColors.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    icon()
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MeetColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.Search, contentDescription = null, tint = MeetColors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun RatingStars(
    rating: Double,
    maxStars: Int = 5,
    size: Dp = 16.dp,
    color: Color = MeetColors.neonGreen,
    onRatingChanged: ((Int) -> Unit)? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..maxStars) {
            val isFull = rating >= i
            val isHalf = !isFull && rating >= (i - 0.5)

            val icon = when {
                isFull -> Icons.Default.Star
                isHalf -> Icons.Default.StarHalf
                else -> Icons.Outlined.StarOutline
            }

            Icon(
                imageVector = icon,
                contentDescription = "Estrella $i",
                tint = if (isFull || isHalf) color else MeetColors.textMuted,
                modifier = Modifier
                    .size(size)
                    .then(
                        if (onRatingChanged != null) Modifier.clickable { onRatingChanged(i) } else Modifier
                    )
            )
        }
    }
}

@Composable
fun InfoPill(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = color.copy(alpha = 0.15f), modifier = Modifier.size(22.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        icon()
                    }
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MeetColors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MeetColors.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}
