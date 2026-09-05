package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.payment.RidePaymentMethod
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun FareModeBottomSheet(
    selectedMode: RideFareMode,
    fareQuote: FareQuote?,
    onSelect: (RideFareMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Modalidad de tarifa",
                            style = MaterialTheme.typography.titleLarge,
                            color = MeetColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                        }
                    }

                    if (fareQuote != null) {
                        Text(
                            "Tarifa estimada: ${fareQuote.formattedTotal}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FareModeChip(
                                mode = RideFareMode.METERED_TIME_DISTANCE,
                                isSelected = selectedMode == RideFareMode.METERED_TIME_DISTANCE,
                                onClick = {
                                    onSelect(RideFareMode.METERED_TIME_DISTANCE)
                                    onDismiss()
                                }
                            )
                            FareModeChip(
                                mode = RideFareMode.OPEN_BID,
                                isSelected = selectedMode == RideFareMode.OPEN_BID,
                                onClick = {
                                    onSelect(RideFareMode.OPEN_BID)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodBottomSheet(
    selectedMethod: RidePaymentMethod,
    onSelect: (RidePaymentMethod) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Método de pago",
                            style = MaterialTheme.typography.titleLarge,
                            color = MeetColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                        }
                    }

                    RidePaymentMethod.entries.forEach { method ->
                        val isSelected = method == selectedMethod
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(method)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MeetColors.electricBlue.copy(alpha = 0.15f) else MeetColors.cardBackground
                            ),
                            border = BorderStroke(1.dp, if (isSelected) MeetColors.electricBlue else MeetColors.borderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MeetColors.electricBlue.copy(alpha = 0.12f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = when (method) {
                                                    RidePaymentMethod.CASH -> Icons.Default.AttachMoney
                                                    RidePaymentMethod.SINPE_MOVIL -> Icons.Default.QrCode
                                                    RidePaymentMethod.CARD -> Icons.Default.CreditCard
                                                    RidePaymentMethod.WALLET -> Icons.Default.Security
                                                },
                                                contentDescription = null,
                                                tint = MeetColors.electricBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(method.displayName, style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            when (method) {
                                                RidePaymentMethod.CASH -> "Pago en efectivo al finalizar el viaje"
                                                RidePaymentMethod.SINPE_MOVIL -> "Transferencia instantánea SINPE Móvil"
                                                RidePaymentMethod.CARD -> "Tarjeta de crédito / débito"
                                                RidePaymentMethod.WALLET -> "Saldo digital prepagado MEET"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MeetColors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MeetColors.neonGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverProfileOverlay(
    driver: MatchedDriver,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MeetColors.neonGreen.copy(alpha = 0.15f),
                        border = BorderStroke(2.dp, MeetColors.neonGreen),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                driver.name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MeetColors.neonGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(driver.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                        Text("${driver.vehicle} • ${driver.plate}", style = MaterialTheme.typography.bodyMedium, color = MeetColors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RatingStars(rating = driver.rating)
                            Text("${driver.rating} (${driver.totalTrips} viajes)", style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
                        }
                    }

                    // Trust badges
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                                Text("Identidad Verificada biométricamente", style = MaterialTheme.typography.labelSmall, color = MeetColors.textPrimary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                                Text("Inspección Vehicular Certificada al día", style = MaterialTheme.typography.labelSmall, color = MeetColors.textPrimary)
                            }
                        }
                    }

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCall,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MeetColors.neonGreen)
                        ) {
                            Text("Llamar", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onMessage,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                        ) {
                            Text("Chat", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * RidePaymentConfirmationDialog — Two-party fare settlement confirmation (Item 8).
 */
@Composable
fun RidePaymentConfirmationDialog(
    fareQuote: FareQuote,
    paymentMethod: RidePaymentMethod,
    onConfirmPayment: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Confirmación de Pago",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )

                    Text(
                        "Liquidación final de viaje con registro de doble partida",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeetColors.textSecondary
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FareBreakdownRow("Tarifa Base", fareQuote.baseFare, MeetColors.neonGreen)
                            FareBreakdownRow("Distancia (${fareQuote.formattedDistance})", fareQuote.distanceFare, MeetColors.electricBlue)
                            FareBreakdownRow("Tiempo (${fareQuote.formattedDuration})", fareQuote.timeFare, MeetColors.hotMagenta)
                            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                            FareBreakdownRow("Total a liquidar", fareQuote.totalFare, MeetColors.neonGreen, isTotal = true)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Método:", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                        Text(paymentMethod.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MeetColors.neonGreen)
                    }

                    Button(
                        onClick = onConfirmPayment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                    ) {
                        Text("CONFIRMAR Y LIQUIDAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * RideRatingAndReviewSheet — Mutual 5-star rating, tip, and feedback (Item 9).
 */
@Composable
fun RideRatingAndReviewSheet(
    counterpartName: String,
    onSubmitReview: (stars: Int, tipAmount: Long, feedback: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var rating by remember { mutableStateOf(5) }
    var tipAmount by remember { mutableStateOf(0L) }
    var feedback by remember { mutableStateOf("") }
    val compliments = listOf("Excelente servicio", "Vehículo impecable", "Manejo seguro", "Puntualidad", "Ruta óptima")
    val selectedCompliments = remember { mutableStateListOf<String>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {}
                    .verticalScroll(rememberScrollState()),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "¿Cómo estuvo tu viaje con $counterpartName?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    RatingStars(
                        rating = rating.toDouble(),
                        size = 32.dp,
                        onRatingChanged = { rating = it }
                    )

                    Text(
                        when (rating) {
                            5 -> "¡Excelente viaje!"
                            4 -> "Muy buen viaje"
                            3 -> "Viaje regular"
                            2 -> "Hubo inconvenientes"
                            else -> "Mala experiencia"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Tips Presets
                    Text("Propina voluntaria para $counterpartName", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0L to "₡0", 500L to "₡500", 1000L to "₡1,000", 2000L to "₡2,000").forEach { (amount, label) ->
                            val isSelected = tipAmount == amount
                            OutlinedButton(
                                onClick = { tipAmount = amount },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                border = BorderStroke(1.dp, if (isSelected) MeetColors.neonGreen else MeetColors.borderSubtle)
                            ) {
                                Text(label, fontSize = 11.sp, color = if (isSelected) MeetColors.neonGreen else MeetColors.textSecondary)
                            }
                        }
                    }

                    // Compliments chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        compliments.take(3).forEach { c ->
                            val isSelected = c in selectedCompliments
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) selectedCompliments.remove(c) else selectedCompliments.add(c)
                                },
                                label = { Text(c, fontSize = 10.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = feedback,
                        onValueChange = { feedback = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escribe un comentario opcional...", color = MeetColors.textMuted) },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            val combinedNotes = (selectedCompliments + feedback).filter { it.isNotBlank() }.joinToString(" • ")
                            onSubmitReview(rating, tipAmount, combinedNotes)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                    ) {
                        Text("ENVIAR CALIFICACIÓN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
