package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.elysium369.meet.core.wallet.SpecialistTopup
import com.elysium369.meet.core.wallet.SpecialistWalletState
import com.elysium369.meet.core.wallet.SpecialistWalletStore
import com.elysium369.meet.ride.wallet.SinpeReceiptParser
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch
import java.io.File

/**
 * World-class Service Provider / Specialist Cockpit Components.
 * Brings Grúa, Mecánicos, Repuestos, and Servicios Elysium to full parity
 * with the elite Driver Cockpit experience (Viajes).
 */

@Composable
fun SpecialistRoleBanner(
    isSpecialistMode: Boolean,
    onToggleMode: (Boolean) -> Unit,
    clientLabel: String = "CLIENTE",
    specialistLabel: String = "ESPECIALISTA",
    specialistIcon: String = "⚡",
    accentColor: Color = MeetColors.neonGreen,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 8.dp.toPx()
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF081220)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    if (isSpecialistMode) accentColor.copy(alpha = 0.8f) else MeetColors.cyberCyan.copy(alpha = 0.5f),
                    if (isSpecialistMode) Color(0xFFC85CFF).copy(alpha = 0.6f) else MeetColors.electricBlue.copy(alpha = 0.5f),
                ),
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Tab Cliente
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (!isSpecialistMode) {
                            MeetColors.cyberCyan.copy(alpha = 0.22f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(
                        if (!isSpecialistMode) 1.dp else 0.dp,
                        if (!isSpecialistMode) MeetColors.cyberCyan else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onToggleMode(false) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("👤", fontSize = 14.sp)
                    Text(
                        text = clientLabel,
                        fontSize = 12.sp,
                        fontWeight = if (!isSpecialistMode) FontWeight.Black else FontWeight.Bold,
                        color = if (!isSpecialistMode) MeetColors.cyberCyan else MeetColors.textSecondary,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            // Tab Especialista / Proveedor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSpecialistMode) {
                            accentColor.copy(alpha = 0.22f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(
                        if (isSpecialistMode) 1.dp else 0.dp,
                        if (isSpecialistMode) accentColor else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onToggleMode(true) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(specialistIcon, fontSize = 14.sp)
                    Text(
                        text = specialistLabel,
                        fontSize = 12.sp,
                        fontWeight = if (isSpecialistMode) FontWeight.Black else FontWeight.Bold,
                        color = if (isSpecialistMode) accentColor else MeetColors.textSecondary,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun SpecialistProfileHeroCard(
    roleName: String,
    businessName: String,
    ownerName: String,
    phone: String,
    rating: Double = 4.95,
    reviewsCount: Int = 148,
    totalJobs: Int = 236,
    acceptanceRatePercent: Double = 99.1,
    isVerified: Boolean = true,
    isOnline: Boolean = true,
    onToggleOnline: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    accentColor: Color = MeetColors.neonGreen,
    secondaryColor: Color = MeetColors.cyberCyan,
    icon: String = "⚡",
    levelTitle: String = "ÉLITE PLATINUM",
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero-radar-pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "heroPulseAlpha",
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroPulseScale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 16.dp.toPx()
                cameraDistance = 16f * density
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091424)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(
                listOf(accentColor.copy(alpha = 0.85f), secondaryColor.copy(alpha = 0.6f)),
            ),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Row 1: Avatar + Business Name & Badges + Online Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    // Avatar Box with Online Badge
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.5.dp, accentColor),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(icon, fontSize = 24.sp)
                            }
                        }
                        // Live Status Dot
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(14.dp)
                                .graphicsLayer {
                                    if (isOnline) {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                }
                                .clip(CircleShape)
                                .background(if (isOnline) MeetColors.neonGreen else MeetColors.textMuted)
                                .border(2.dp, Color(0xFF091424), CircleShape),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = businessName.ifBlank { "Especialista Elysium" },
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isVerified) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MeetColors.neonGreen.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        text = "✔",
                                        color = MeetColors.neonGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            text = roleName.uppercase(),
                            color = secondaryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = levelTitle,
                                    color = accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x33FFFFFF),
                            ) {
                                Text(
                                    text = if (isVerified) "OFICIAL ACREDITADO" else "EN REVISIÓN",
                                    color = if (isVerified) Color.White else MeetColors.warning,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                // Online/Offline Dispatch Switch
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (isOnline) "DISPONIBLE" else "DESCONECTADO",
                        color = if (isOnline) MeetColors.neonGreen.copy(alpha = pulseAlpha) else MeetColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    )
                    Switch(
                        checked = isOnline,
                        onCheckedChange = onToggleOnline,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = MeetColors.textSecondary,
                            uncheckedTrackColor = Color(0xFF1E293B),
                        ),
                    )
                }
            }

            // Row 2: Four Key Performance Indicators (KPIs)
            Surface(
                color = Color(0xFF060E18),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpecialistMetricItem(
                        label = "REPUTACIÓN",
                        value = "★ ${String.format("%.1f", rating)}",
                        subValue = "$reviewsCount res.",
                        valueColor = Color(0xFFFFD700),
                    )
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x22FFFFFF)))
                    SpecialistMetricItem(
                        label = "ACEPTACIÓN",
                        value = "${String.format("%.1f", acceptanceRatePercent)}%",
                        subValue = "Óptima",
                        valueColor = MeetColors.neonGreen,
                    )
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x22FFFFFF)))
                    SpecialistMetricItem(
                        label = "TRABAJOS",
                        value = "$totalJobs",
                        subValue = "Completados",
                        valueColor = secondaryColor,
                    )
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x22FFFFFF)))
                    SpecialistMetricItem(
                        label = "GARANTÍA",
                        value = "100%",
                        subValue = "Escrow Activo",
                        valueColor = Color(0xFFC85CFF),
                    )
                }
            }

            // Row 3: Quick Info & Edit Profile Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = secondaryColor,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = phone.ifBlank { "Tel: Sin registrar" },
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                    )
                }

                OutlinedButton(
                    onClick = onEditProfile,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.8f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = secondaryColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "EDITAR PERFIL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = secondaryColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecialistMetricItem(
    label: String,
    value: String,
    subValue: String,
    valueColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = MeetColors.textSecondary,
            letterSpacing = 0.4.sp,
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = valueColor,
        )
        Text(
            text = subValue,
            fontSize = 8.sp,
            color = MeetColors.textMuted,
        )
    }
}

@Composable
fun SpecialistEarningsHeroCard(
    todayEarningsCrc: Double,
    todayJobsCount: Int,
    availablePayoutCrc: Double,
    currencySymbol: String = "₡",
    accentColor: Color = MeetColors.neonGreen,
    onViewDetails: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 14.dp.toPx()
                cameraDistance = 16f * density
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071B1E)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.3.dp, accentColor.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("💰", fontSize = 16.sp)
                    Text(
                        text = "GANANCIAS NETAS HOY",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "$todayJobsCount SERVICIOS HOY",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Big Hero Number
            Text(
                text = "$currencySymbol${String.format("%,.0f", todayEarningsCrc)}",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Disponible para Retiro",
                        color = MeetColors.textMuted,
                        fontSize = 10.sp,
                    )
                    Text(
                        text = "$currencySymbol${String.format("%,.0f", availablePayoutCrc)}",
                        color = MeetColors.cyberCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                Button(
                    onClick = onViewDetails,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.copy(alpha = 0.18f),
                        contentColor = accentColor,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accentColor),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("BILLETERA & RETIROS", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun SpecialistOperationalMetricsRow(
    radiusKm: Double = 25.0,
    etaMinutes: Int = 12,
    escrowGuaranteed: Boolean = true,
    accentColor: Color = MeetColors.neonGreen,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OperationalKpiCard(
            title = "RADIO DE ACCIÓN",
            value = "${radiusKm.toInt()} km",
            subtitle = "Cobertura Satelital",
            icon = "📡",
            tint = MeetColors.cyberCyan,
            modifier = Modifier.weight(1f),
        )
        OperationalKpiCard(
            title = "TIEMPO RESPUESTA",
            value = "~$etaMinutes min",
            subtitle = "Despacho Rápido",
            icon = "⚡",
            tint = accentColor,
            modifier = Modifier.weight(1f),
        )
        OperationalKpiCard(
            title = "CUSTODIA ESCROW",
            value = if (escrowGuaranteed) "100%" else "Básico",
            subtitle = "Pagos Protegidos",
            icon = "🛡️",
            tint = Color(0xFFC85CFF),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OperationalKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.graphicsLayer { shadowElevation = 6.dp.toPx() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091322)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(icon, fontSize = 11.sp)
                Text(
                    text = title,
                    color = MeetColors.textSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = subtitle,
                color = MeetColors.textMuted,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SpecialistEditProfileDialog(
    initialBusinessName: String,
    initialPhone: String,
    initialSpecialties: String = "",
    roleTitle: String,
    onDismiss: () -> Unit,
    onSave: (businessName: String, phone: String, specialties: String) -> Unit,
) {
    var businessName by remember { mutableStateOf(initialBusinessName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var specialties by remember { mutableStateOf(initialSpecialties) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚙️", fontSize = 20.sp)
                Text("Configurar Perfil de $roleTitle", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Nombre Comercial / Unidad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.neonGreen,
                        unfocusedBorderColor = MeetColors.borderSubtle,
                    ),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono / WhatsApp de Contacto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = MeetColors.borderSubtle,
                    ),
                )
                OutlinedTextField(
                    value = specialties,
                    onValueChange = { specialties = it },
                    label = { Text("Especialidades / Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFC85CFF),
                        unfocusedBorderColor = MeetColors.borderSubtle,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(businessName, phone, specialties)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("GUARDAR CAMBIOS", fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = MeetColors.textSecondary, fontSize = 11.sp)
            }
        },
        containerColor = Color(0xFF0C1626),
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
fun SpecialistWalletCard(
    walletState: SpecialistWalletState,
    roleTitle: String = "ESPECIALISTA",
    accentColor: Color = MeetColors.neonGreen,
    onRechargeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 14.dp.toPx()
                cameraDistance = 16f * density
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071422)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.4.dp,
            Brush.horizontalGradient(
                listOf(accentColor.copy(alpha = 0.85f), MeetColors.cyberCyan.copy(alpha = 0.65f))
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Row: Wallet Title & 5% Global Guarantee Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("💳", fontSize = 18.sp)
                    Column {
                        Text(
                            text = "BILLETERA DE OPERACIÓN · $roleTitle",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 0.6.sp,
                        )
                        Text(
                            text = "SISTEMA INTEGRAL ELYSIUM DE SALDO",
                            color = MeetColors.cyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = "COMISIÓN 5% GLOBAL",
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            // Hero Balances Card
            Surface(
                color = Color(0xFF040A12),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "SALDO DISPONIBLE",
                            color = MeetColors.textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            text = "₡${String.format("%,.0f", walletState.balanceCrc)}",
                            color = accentColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            text = "100% disponible para operar y respaldar servicios",
                            color = MeetColors.textMuted,
                            fontSize = 8.sp,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF132838),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    text = "REGALO BIENVENIDA",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    text = "₡15,000 REGALADOS",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }

                        Text(
                            text = "Por Jorge Del Valle / MEET",
                            color = MeetColors.textMuted,
                            fontSize = 8.sp,
                        )
                    }
                }
            }

            // Operational Specs & Commission Guarantee
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF0A1828),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("GANANCIA NETA", color = MeetColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("95% para ti", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text("Retención directa", color = MeetColors.textMuted, fontSize = 8.sp)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF0A1828),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("COMISIÓN MEET", color = MeetColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("5% fija", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text("Límite constitucional", color = MeetColors.textMuted, fontSize = 8.sp)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF0A1828),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x1FFFFFFF)),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("VERIFICACIÓN", color = MeetColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("Trust Center", color = MeetColors.cyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text("Acreditación 1-tap", color = MeetColors.textMuted, fontSize = 8.sp)
                    }
                }
            }

            // SINPE Móvil Reference Box
            Surface(
                color = Color(0xFF0A1624),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("📱", fontSize = 16.sp)
                        Column {
                            Text(
                                text = "SINPE Móvil Oficial: ${SpecialistWalletStore.SINPE_PHONE}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = "${SpecialistWalletStore.SINPE_RECIPIENT_NAME} · ${SpecialistWalletStore.SINPE_EMAIL}",
                                color = MeetColors.cyberCyan,
                                fontSize = 9.sp,
                            )
                        }
                    }
                    Text(
                        text = "Verificado",
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                    )
                }
            }

            // Action Button: Recharge Balance
            Button(
                onClick = onRechargeClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = "RECARGAR SALDO CON SINPE MÓVIL",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            // Recent Topups Feed
            if (walletState.topups.isNotEmpty()) {
                Text(
                    text = "RECARGAS RECIENTES",
                    color = MeetColors.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    walletState.topups.take(3).forEach { topup ->
                        val (statusText, statusColor) = when (topup.status) {
                            "APPROVED" -> "ACREDITADA ✓" to MeetColors.neonGreen
                            "REJECTED" -> "RECHAZADA ✗" to MeetColors.error
                            else -> "PENDIENTE TRUST CENTER ⏳" to MeetColors.warning
                        }
                        Surface(
                            color = Color(0xFF050D18),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "₡${String.format("%,.0f", topup.amountCrc)} · Ref: ${topup.referenceNumber}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    topup.senderPhoneOrName?.let {
                                        Text(it, color = MeetColors.textMuted, fontSize = 8.sp)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusColor.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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

@Composable
fun SpecialistSinpeTopupDialog(
    serviceTitle: String,
    specialistId: String,
    serviceVertical: String,
    onDismiss: () -> Unit,
    onTopupSubmitted: (amountCrc: Double, reference: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pastedReceiptText by remember { mutableStateOf("") }
    var detectedRef by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("10000") }
    var senderNameOrPhone by remember { mutableStateOf("") }
    var selectedProofFile by remember { mutableStateOf<File?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val parsedReceipt = remember(pastedReceiptText) {
        SinpeReceiptParser.parse(pastedReceiptText)
    }

    LaunchedEffect(parsedReceipt) {
        if (parsedReceipt != null) {
            amountInput = parsedReceipt.amountCrc.toInt().toString()
            detectedRef = parsedReceipt.referenceNumber
            parsedReceipt.senderPhoneOrName?.let { senderNameOrPhone = it }
        }
    }

    val proofPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        val file = File(context.cacheDir, "specialist-topup-${System.currentTimeMillis()}.jpg")
                        file.writeBytes(bytes)
                        selectedProofFile = file
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💰", fontSize = 20.sp)
                Column {
                    Text(
                        "Recargar Saldo Operativo (SINPE)",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.White,
                    )
                    Text(
                        "$serviceTitle · Validación Trust Center",
                        fontSize = 10.sp,
                        color = MeetColors.cyberCyan,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Official SINPE Móvil Banner
                Surface(
                    color = Color(0xFF101E30),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            "📱 SINPE Móvil: ${SpecialistWalletStore.SINPE_PHONE}",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = Color.White,
                        )
                        Text(
                            "👤 Destinatario: ${SpecialistWalletStore.SINPE_RECIPIENT_NAME}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MeetColors.neonGreen,
                        )
                        Text(
                            "✉️ Correo vinculado: ${SpecialistWalletStore.SINPE_EMAIL}",
                            fontSize = 10.sp,
                            color = MeetColors.cyberCyan,
                        )
                        Text(
                            "El creador valida el ingreso en su cuenta bancaria en el Trust Center para liberar el saldo.",
                            fontSize = 9.sp,
                            color = MeetColors.warning,
                        )
                    }
                }

                // Paste Bank Receipt Text
                OutlinedTextField(
                    value = pastedReceiptText,
                    onValueChange = { pastedReceiptText = it },
                    label = { Text("Pegar comprobante bancario (BAC, BNCR, BCR, etc.)") },
                    placeholder = { Text("Ej: Transferencia SINPE por ₡10,000 Ref: 20260913...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.neonGreen,
                        unfocusedBorderColor = MeetColors.borderSubtle,
                    ),
                )

                // Parsed Receipt Recognition Indicator
                if (parsedReceipt != null) {
                    Surface(
                        color = MeetColors.neonGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MeetColors.neonGreen),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("✓", color = MeetColors.neonGreen, fontWeight = FontWeight.Black)
                            Text(
                                "${parsedReceipt.bank.displayName}: Ref ${parsedReceipt.referenceNumber} (₡${parsedReceipt.amountCrc.toInt()})",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Quick Amount Selection Chips
                Text("Monto a Recargar:", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(5_000, 10_000, 20_000, 50_000).forEach { amt ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (amountInput == amt.toString()) MeetColors.neonGreen.copy(alpha = 0.25f) else Color(0xFF142436),
                            border = BorderStroke(1.dp, if (amountInput == amt.toString()) MeetColors.neonGreen else Color(0x33FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { amountInput = amt.toString() },
                        ) {
                            Text(
                                text = "₡${amt / 1000}k",
                                color = if (amountInput == amt.toString()) MeetColors.neonGreen else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }

                // Custom Amount Field
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { value ->
                        amountInput = value.filter(Char::isDigit).take(8)
                    },
                    label = { Text("Monto en Colones (CRC)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = MeetColors.borderSubtle,
                    ),
                )

                // Reference Number Field
                OutlinedTextField(
                    value = detectedRef,
                    onValueChange = { detectedRef = it },
                    label = { Text("Número de Comprobante / Referencia") },
                    singleLine = true,
                    placeholder = { Text("Ej: 20260913987654") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = MeetColors.borderSubtle,
                    ),
                )

                // Attach File Proof Button
                OutlinedButton(
                    onClick = {
                        proofPicker.launch(arrayOf("image/*", "application/pdf"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (selectedProofFile != null) MeetColors.neonGreen else MeetColors.cyberCyan),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (selectedProofFile != null) Icons.Default.CheckCircle else Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = if (selectedProofFile != null) MeetColors.neonGreen else MeetColors.cyberCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (selectedProofFile != null) "COMPROBANTE ADJUNTADO (${selectedProofFile!!.name.take(16)})" else "ADJUNTAR CAPTURA O PDF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedProofFile != null) MeetColors.neonGreen else Color.White,
                    )
                }
            }
        },
        confirmButton = {
            val amountNum = amountInput.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (amountNum > 0) {
                        isSubmitting = true
                        coroutineScope.launch {
                            SpecialistWalletStore.submitTopup(
                                context = context,
                                specialistId = specialistId,
                                serviceVertical = serviceVertical,
                                amountCrc = amountNum,
                                referenceNumber = detectedRef,
                                senderPhoneOrName = senderNameOrPhone.ifBlank { null },
                                proofFile = selectedProofFile,
                            )
                            isSubmitting = false
                            onTopupSubmitted(amountNum, detectedRef)
                            android.widget.Toast.makeText(
                                context,
                                "Recarga de ₡${String.format("%,.0f", amountNum)} enviada a revisión en Trust Center.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                            onDismiss()
                        }
                    }
                },
                enabled = !isSubmitting && (amountInput.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.neonGreen,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Text("ENVIAR Y NOTIFICAR AL TRUST CENTER", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text("CANCELAR", color = MeetColors.textSecondary, fontSize = 11.sp)
            }
        },
        containerColor = Color(0xFF0A1524),
        shape = RoundedCornerShape(20.dp),
    )
}

