package com.elysium369.meet.fulfillment.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.fulfillment.domain.*
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun FulfillmentTimelineView(
    timeline: List<FulfillmentTimelineEvent>,
    modifier: Modifier = Modifier,
) {
    if (timeline.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "LÍNEA DE TIEMPO OPERACIONAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MeetColors.textSecondary,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(12.dp))

            timeline.forEachIndexed { index, event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Step dot & vertical indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        event.isCurrent -> MeetColors.neonGreen
                                        event.isCompleted -> MeetColors.electricBlue
                                        else -> Color.DarkGray
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (event.isCompleted && !event.isCurrent) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                        if (index < timeline.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .background(if (event.isCompleted) MeetColors.electricBlue.copy(alpha = 0.5f) else Color.DarkGray)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            event.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (event.isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (event.isCurrent) MeetColors.neonGreen else MeetColors.textPrimary
                        )
                        if (event.description.isNotBlank()) {
                            Text(
                                event.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MeetColors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderTrackingCard(
    provider: FulfillmentProviderInfo,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onPtt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar initials
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MeetColors.cardBackgroundLighter),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        provider.name.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.neonGreen,
                        fontSize = 20.sp
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            provider.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verificado",
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MeetColors.warning, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            String.format("%.2f", provider.rating),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textPrimary
                        )
                        Text(
                            " (${provider.totalJobs} servicios)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeetColors.textSecondary
                        )
                    }

                    provider.vehicleDescription?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MeetColors.electricBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ETA Pill if available
                provider.etaMinutes?.let { eta ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeetColors.neonGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$eta", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MeetColors.neonGreen)
                            Text("min", fontSize = 10.sp, color = MeetColors.neonGreen)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action Buttons: Call, Message, PTT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = MeetColors.textPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Llamar", color = MeetColors.textPrimary, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "Mensaje", tint = MeetColors.textPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chat", color = MeetColors.textPrimary, fontSize = 12.sp)
                }

                Button(
                    onClick = onPtt,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "PTT", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PTT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun PricingSummaryCard(
    pricing: FulfillmentPricing,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "DESGLOSE FINANCIERO CERTIFICADO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MeetColors.textSecondary,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(10.dp))

            when (pricing) {
                is FulfillmentPricing.EstimatedRange -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rango Estimado", color = MeetColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${pricing.min.formatted()} – ${pricing.max.formatted()}",
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.neonGreen,
                            fontSize = 16.sp
                        )
                    }
                }
                is FulfillmentPricing.Quote -> {
                    pricing.breakdown.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.label, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            Text(item.amount.formatted(), color = MeetColors.textPrimary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MeetColors.borderSubtle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cotización Autorizada", fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                        Text(pricing.amount.formatted(), fontWeight = FontWeight.Black, color = MeetColors.neonGreen, fontSize = 18.sp)
                    }
                }
                is FulfillmentPricing.AuthorizedAmount -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Monto Máximo Autorizado", fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                        Text(pricing.amount.formatted(), fontWeight = FontWeight.Bold, color = MeetColors.electricBlue, fontSize = 18.sp)
                    }
                }
                is FulfillmentPricing.FinalSettlement -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Liquidación Final (Doble Partida)", fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                        Text(pricing.total.formatted(), fontWeight = FontWeight.Black, color = MeetColors.neonGreen, fontSize = 20.sp)
                    }
                    pricing.ledgerAttestationHash?.let { hash ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Hash: $hash",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MeetColors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FulfillmentEvidenceCard(
    evidenceList: List<FulfillmentEvidenceSnapshot>,
    modifier: Modifier = Modifier,
) {
    if (evidenceList.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "CUSTODIA Y EVIDENCIA FORENSE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MeetColors.textSecondary,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(8.dp))

            evidenceList.forEach { ev ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ev.label, fontWeight = FontWeight.SemiBold, color = MeetColors.textPrimary, fontSize = 13.sp)
                        Text(
                            "SHA-256: ${ev.sha256Hash}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MeetColors.electricBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Protegido",
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
