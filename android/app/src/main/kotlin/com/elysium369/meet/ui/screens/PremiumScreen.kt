package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.monetization.EntitlementKey
import com.elysium369.meet.core.monetization.MonetizationPolicy
import com.elysium369.meet.core.monetization.MonetizationRemoteConfig
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteTextButton
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PremiumScreen(
    viewModel: ObdViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var selectedPlan by remember { mutableStateOf("PRO") }
    var billingStatusMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val activeEntitlements by viewModel.entitlementManager.entitlements.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "ELYSIUM VANGUARD",
                color = MeetColors.cyberCyan,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            
            Text(
                text = "Planes y Licencias",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Plan Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1B30), shape = RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("FREE", "PRO", "ELITE", "FLEET").forEach { plan ->
                    val isSelected = selectedPlan == plan
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) MeetColors.cardBackgroundLighter else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedPlan = plan }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = plan,
                            color = if (isSelected) MeetColors.neonGreen else MeetColors.textMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Plan Details Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B30)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                if (selectedPlan == "ELITE") MeetColors.hotMagenta else MeetColors.cyberCyan,
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedPlan) {
                                "FREE" -> "Plan Gratuito"
                                "PRO" -> "Plan Profesional"
                                "ELITE" -> "Tecnología Elite"
                                else -> "Gestión de Flota"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = when (selectedPlan) {
                                "FREE" -> "Gratis"
                                "PRO" -> MonetizationRemoteConfig.proPriceCopy
                                "ELITE" -> MonetizationRemoteConfig.elitePriceCopy
                                else -> "Cotizar"
                            },
                            color = MeetColors.neonGreen,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when (selectedPlan) {
                            "FREE" -> "Acceso básico para conductores independientes."
                            "PRO" -> "Diagnósticos profundos, reportes offline y soporte multi-vehículo."
                            "ELITE" -> "Certificación forense de reportes, osciloscopio y bidirectional safe tests."
                            else -> "Control operativo total multi-conductor con DVIR y costos centralizados."
                        },
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dynamic Feature List
                    val features = when (selectedPlan) {
                        "FREE" -> listOf(
                            "Scanner básico de DTCs OBD2",
                            "Lectura rápida de parámetros HUD",
                            "Límite de 1 vehículo en Garage",
                            "Anuncios publicitarios no intrusivos"
                        )
                        "PRO" -> listOf(
                            "Sin anuncios en el centro de mando",
                            "Garage multi-vehículo ilimitado",
                            "Generación de reportes PDF básicos",
                            "Acceso completo a manuales offline",
                            "LiveLink básico (30 minutos/mes)"
                        )
                        "ELITE" -> listOf(
                            "Reportes Certificados (QR + HASH + Firma)",
                            "Diagnóstico guiado por IA Avanzada",
                            "Control y test activo seguro (Bidireccional)",
                            "Acceso al módulo de Osciloscopio USB",
                            "Reseteo de servicios y adaptaciones",
                            "LiveLink PRO completo sin límite de tiempo"
                        )
                        else -> listOf(
                            "Organización empresarial y Multi-usuario",
                            "Firma y validación de DVIR obligatorios",
                            "Panel web de operaciones y costos",
                            "Alertas de mantenimiento programado",
                            "Roles y permisos (Admin / Mecánico / Chofer)"
                        )
                    }

                    features.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (selectedPlan == "ELITE") MeetColors.hotMagenta else MeetColors.neonGreen,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = feature,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (billingStatusMessage.isNotBlank()) {
                Text(
                    text = billingStatusMessage,
                    color = MeetColors.cyberCyan,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Checkout Buttons
            if (isProcessing) {
                CircularProgressIndicator(color = MeetColors.cyberCyan)
            } else {
                val entitlementKeyForPlan = when (selectedPlan) {
                    "PRO" -> EntitlementKey.PRO_ACCESS
                    "ELITE" -> EntitlementKey.ELITE_ACCESS
                    "FLEET" -> EntitlementKey.FLEET_ACCESS
                    else -> null
                }
                
                val isPlanActive = entitlementKeyForPlan?.let { key ->
                    activeEntitlements.any { it.entitlementKey == key && it.state == com.elysium369.meet.core.monetization.EntitlementState.ACTIVE }
                } ?: false

                if (selectedPlan != "FREE") {
                    EliteButton(
                        text = if (isPlanActive) "PLAN ACTIVO" else "ADQUIRIR PLAN $selectedPlan",
                        onClick = {
                            if (isPlanActive) return@EliteButton
                            isProcessing = true
                            billingStatusMessage = "Iniciando pasarela segura de Google Play..."
                            scope.launch {
                                // Simulate play billing delay
                                kotlinx.coroutines.delay(2000)
                                viewModel.entitlementManager.grantLocalAccess(
                                    key = entitlementKeyForPlan!!,
                                    productId = "${selectedPlan.lowercase()}_monthly",
                                    source = com.elysium369.meet.core.monetization.EntitlementSource.GOOGLE_PLAY_SUBSCRIPTION
                                )
                                isProcessing = false
                                billingStatusMessage = "¡Compra realizada con éxito y vinculada a tu cuenta!"
                            }
                        },
                        color = if (selectedPlan == "ELITE") MeetColors.hotMagenta else MeetColors.neonGreen,
                        textColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EliteTextButton(
                        text = "Restaurar Compras",
                        onClick = {
                            isProcessing = true
                            billingStatusMessage = "Restaurando compras vinculadas a tu cuenta..."
                            scope.launch {
                                kotlinx.coroutines.delay(1500)
                                viewModel.entitlementManager.restorePurchases()
                                isProcessing = false
                                billingStatusMessage = "Compras restauradas correctamente."
                            }
                        },
                        color = MeetColors.textMuted
                    )

                    EliteTextButton(
                        text = "Continuar Gratis",
                        onClick = onClose,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
