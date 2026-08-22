package com.elysium369.meet.ui.screens.serviceos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.core.services.serviceos.StructuredServiceOffer
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun OfferComparatorDialog(
    offers: List<StructuredServiceOffer>,
    onDismiss: () -> Unit,
    onAcceptOffer: (StructuredServiceOffer) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = MeetColors.backgroundDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "COMPARADOR DE OFERTAS",
                            color = MeetColors.electricBlue,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "${offers.size} Oferta(s) Estructurada(s) Recibida(s)",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textMuted)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (offers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay ofertas recibidas para esta solicitud aún.", color = MeetColors.textMuted, fontSize = 13.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        offers.forEachIndexed { index, offer ->
                            val isTopRanked = index == 0
                            EliteCard(
                                backgroundColor = MeetColors.cardBackground,
                                borderColor = if (isTopRanked) MeetColors.neonGreen.copy(alpha = 0.6f) else MeetColors.borderSubtle,
                                glowColor = if (isTopRanked) MeetColors.neonGreen else MeetColors.cardBackground,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (isTopRanked) {
                                        Surface(
                                            color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MeetColors.neonGreen)
                                        ) {
                                            Text(
                                                "⭐ RECOMENDACIÓN TÉCNICA MEET",
                                                color = MeetColors.neonGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(offer.providerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.Star, contentDescription = "Rating", tint = MeetColors.warning, modifier = Modifier.size(12.dp))
                                                Text("${offer.providerRating} · ${offer.providerVerifiedCasesCount} trabajos verificados", color = MeetColors.textSecondary, fontSize = 11.sp)
                                            }
                                        }
                                        Text(
                                            offer.totalEstimatedCost.formatted(),
                                            color = MeetColors.neonGreen,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 18.sp
                                        )
                                    }

                                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 0.5.dp)

                                    Text("Hipótesis Diagnóstica:", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(offer.technicalHypothesis, color = Color.White, fontSize = 12.sp)

                                    Text("Alcance Propuesto:", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(offer.proposedScopeOfWork, color = MeetColors.textSecondary, fontSize = 12.sp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("⏱ Duración: ~${offer.estimatedDurationHours}h", color = MeetColors.textMuted, fontSize = 11.sp)
                                        Text("🛡 Garantía: ${offer.warrantyDays} días", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("📍 ${String.format("%.1f", offer.roadDistanceKm)} km", color = MeetColors.textMuted, fontSize = 11.sp)
                                    }

                                    if (offer.positiveMatchingSignals.isNotEmpty()) {
                                        Surface(
                                            color = Color(0xFF0C2018),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                offer.positiveMatchingSignals.forEach { sig ->
                                                    Text("✓ $sig", color = MeetColors.neonGreen, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    EliteButton(
                                        text = "ACEPTAR OFERTA Y ASIGNAR",
                                        onClick = { onAcceptOffer(offer) },
                                        color = if (isTopRanked) MeetColors.neonGreen else MeetColors.electricBlue,
                                        modifier = Modifier.fillMaxWidth().height(38.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
