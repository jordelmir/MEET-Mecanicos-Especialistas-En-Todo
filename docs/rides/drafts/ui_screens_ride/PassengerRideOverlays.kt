package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    ModalBottomSheetLayout(
        sheetContent = {
            ModalBottomSheet(
                sheetState = rememberModalBottomSheetState(
                    initialValue = ModalBottomSheetValue.Hidden,
                    confirmStateChange = { it != ModalBottomSheetValue.Hidden }
                ),
                modifier = Modifier.fillMaxWidth(),
                sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MeetColors.backgroundDeep,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(MeetColors.outlineVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(MeetColors.outlineVariant)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }

                    Text("Modalidad de tarifa", style = MaterialTheme.typography.headlineSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)

                    if (fareQuote != null) {
                        Text("Tarifa actual: ${fareQuote.formattedTotal} (${selectedMode.displayName})", style = MaterialTheme.typography.bodyMedium, color = MeetColors.textSecondary)
                        Spacer(Modifier.height(8.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FareModeOption(
                            mode = RideFareMode.METERED,
                            isSelected = selectedMode == RideFareMode.METERED,
                            onClick = { onSelect(RideFareMode.METERED) }
                        )
                        FareModeOption(
                            mode = RideFareMode.OPEN_BID,
                            isSelected = selectedMode == RideFareMode.OPEN_BID,
                            onClick = { onSelect(RideFareMode.OPEN_BID) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.electricBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Confirmar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        sheetState = rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            confirmStateChange = { true }
        ).also { state ->
            // Show sheet
            LaunchedEffect(Unit) { state.show() }
        }
    )
}

@Composable
fun FareModeOption(
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
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.12f) else MeetColors.cardBackground,
            contentColor = if (isSelected) color else MeetColors.textPrimary
        ),
        border = if (isSelected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MeetColors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = if (isSelected) color else MeetColors.outlineVariant, modifier = Modifier.size(24.dp)) {
                if (isSelected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp))
                }
            }
            Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
                Icon(icon, contentDescription = null, tint = if (isSelected) color else MeetColors.textSecondary, modifier = Modifier.padding(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = if (isSelected) color else MeetColors.textPrimary)
                Text(mode.shortDescription, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
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
    ModalBottomSheetLayout(
        sheetContent = {
            ModalBottomSheet(
                sheetState = rememberModalBottomSheetState(
                    initialValue = ModalBottomSheetValue.Hidden,
                    confirmStateChange = { it != ModalBottomSheetValue.Hidden }
                ),
                modifier = Modifier.fillMaxWidth(),
                sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MeetColors.backgroundDeep,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(MeetColors.outlineVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.width(40.dp).height(4.dp).background(MeetColors.outlineVariant).clip(RoundedCornerShape(2.dp)))
                    }

                    Text("Método de pago", style = MaterialTheme.typography.headlineSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PaymentMethodOption(
                            method = RidePaymentMethod.CASH,
                            isSelected = selectedMethod == RidePaymentMethod.CASH,
                            onClick = { onSelect(RidePaymentMethod.CASH) }
                        )
                        PaymentMethodOption(
                            method = RidePaymentMethod.SINPE_MOVIL,
                            isSelected = selectedMethod == RidePaymentMethod.SINPE_MOVIL,
                            onClick = { onSelect(RidePaymentMethod.SINPE_MOVIL) }
                        )
                        PaymentMethodOption(
                            method = RidePaymentMethod.CARD,
                            isSelected = selectedMethod == RidePaymentMethod.CARD,
                            onClick = { onSelect(RidePaymentMethod.CARD) }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.electricBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Confirmar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        sheetState = rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            confirmStateChange = { true }
        ).also { state ->
            LaunchedEffect(Unit) { state.show() }
        }
    )
}

@Composable
fun PaymentMethodOption(
    method: RidePaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val (icon, color) = when (method) {
        RidePaymentMethod.CASH -> Icons.Default.Money to MeetColors.neonGreen
        RidePaymentMethod.SINPE_MOVIL -> Icons.Default.QrCode to MeetColors.electricBlue
        RidePaymentMethod.CARD -> Icons.Default.CreditCard to MeetColors.electricPurple
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.12f) else MeetColors.cardBackground,
            contentColor = if (isSelected) color else MeetColors.textPrimary
        ),
        border = if (isSelected) BorderStroke(2.dp, color) else BorderStroke(1.dp, MeetColors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = if (isSelected) color else MeetColors.outlineVariant, modifier = Modifier.size(24.dp)) {
                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp))
            }
            Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
                Icon(icon, contentDescription = null, tint = if (isSelected) color else MeetColors.textSecondary, modifier = Modifier.padding(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(method.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = if (isSelected) color else MeetColors.textPrimary)
                Text(method.description, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
            }
        }
    }
}

@Composable
fun StopsBottomSheet(
    stops: List<RidePlaceInput>,
    onStopsChanged: (List<RidePlaceInput>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheetLayout(
        sheetContent = {
            ModalBottomSheet(
                sheetState = rememberModalBottomSheetState(
                    initialValue = ModalBottomSheetValue.Hidden,
                    confirmStateChange = { it != ModalBottomSheetValue.Hidden }
                ),
                modifier = Modifier.fillMaxWidth(),
                sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MeetColors.backgroundDeep,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(MeetColors.outlineVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.width(40.dp).height(4.dp).background(MeetColors.outlineVariant).clip(RoundedCornerShape(2.dp)))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Paradas intermedias", style = MaterialTheme.typography.headlineSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                        Text("${stops.size}/3", style = MaterialTheme.typography.bodyMedium, color = MeetColors.textSecondary)
                    }

                    if (stops.isEmpty()) {
                        Text("Añade hasta 3 paradas en tu ruta", style = MaterialTheme.typography.bodyMedium, color = MeetColors.textSecondary, modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth())
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            stops.forEachIndexed { index, stop ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(stop.displayName, style = MaterialTheme.typography.bodyMedium, color = MeetColors.textPrimary)
                                            Text(stop.address ?: "", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                                        }
                                        IconButton(onClick = { /* Remove stop */ }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar parada", tint = MeetColors.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (stops.size < 3) {
                        OutlinedButton(
                            onClick = { /* Add stop */ },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MeetColors.electricBlue)
                                Spacer(Modifier.width(8.dp))
                                Text("Añadir parada", style = MaterialTheme.typography.labelLarge, color = MeetColors.electricBlue)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue, contentColor = Color.White)
                    ) {
                        Text("Continuar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        sheetState = rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            confirmStateChange = { true }
        ).also { state ->
            LaunchedEffect(Unit) { state.show() }
        }
    )
}

@Composable
fun DriverMatchedOverlay(
    driver: MatchedDriver,
    onCancel: () -> Unit,
    onViewProfile: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onCancel() },
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .widthIn(max = 400.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("¡Conductor encontrado!", style = MaterialTheme.typography.headlineSmall, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = MeetColors.textSecondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Driver photo/avatar
                        Surface(
                            shape = CircleShape,
                            color = MeetColors.neonGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(driver.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(driver.name, style = MaterialTheme.typography.titleLarge, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RatingStars(rating = driver.rating, maxStars = 5, size = 16.sp)
                                Text("(${driver.totalTrips} viajes)", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                            }
                            Text(driver.vehicle, style = MaterialTheme.typography.bodyMedium, color = MeetColors.textSecondary)
                            Text("Placa: ${driver.plate}", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                        }
                    }

                    // ETA
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.electricBlue.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MeetColors.electricBlue)
                            Column {
                                Text("Llega en ${driver.etaMinutes} min", style = MaterialTheme.typography.titleMedium, color = MeetColors.electricBlue, fontWeight = FontWeight.Bold)
                                Text("${driver.distanceMeters}m de distancia", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                            }
                        }
                    }

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewProfile,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ver perfil", style = MaterialTheme.typography.labelLarge, color = MeetColors.electricBlue)
                        }
                        Button(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error, contentColor = Color.White)
                        ) {
                            Text("Cancelar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RatingStars(
    rating: Double,
    maxStars: Int = 5,
    size: Int = 16,
    color: Color = MeetColors.neonGreen
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..maxStars) {
            val fill = when {
                rating >= i -> 1.0
                rating >= i - 0.5 -> 0.5
                else -> 0.0
            }
            Icon(
                imageVector = when {
                    fill == 1.0 -> Icons.Default.Star
                    fill == 0.5 -> Icons.Default.StarHalf
                    else -> Icons.Default.StarOutline
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size.dp)
            )
        }
    }
}