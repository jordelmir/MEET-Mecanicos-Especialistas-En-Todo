package com.elysium369.meet.ui.screens.serviceos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.core.services.serviceos.*
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun SmartServiceRequestComposerDialog(
    activeVehicle: Vehicle?,
    activeDtcs: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (ServiceRequestV2) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var userSymptomCategory by remember { mutableStateOf("No arranca / Se apaga") }
    var symptomDetail by remember { mutableStateOf("") }
    var selectedMobility by remember { mutableStateOf(MobilityCondition.SHORT_DISTANCE_ONLY) }
    var selectedUrgency by remember { mutableStateOf(ServiceRequestUrgency.NEXT_AVAILABLE_SLOT) }
    var selectedModality by remember { mutableStateOf(ServiceModality.WORKSHOP_FACILITY) }
    var locationZoneText by remember { mutableStateOf("San José, Costa Rica (Zona Aproximada)") }
    var budgetAmount by remember { mutableStateOf("") }

    val vehicleLabel = activeVehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo no seleccionado"
    val maskedVin = activeVehicle?.vin?.let {
        if (it.length >= 4) "*".repeat(it.length - 4) + it.takeLast(4) else it
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MeetColors.backgroundDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "SOLICITUD DE SERVICIO V2",
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Paso $step de 3 · Triaje y Evidencia",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textMuted)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Vehicle & Telemetry Box
                    EliteCard(
                        backgroundColor = MeetColors.cardBackground,
                        borderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🚗 VEHÍCULO ACTIVO:", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(vehicleLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (maskedVin != null) {
                                Text("VIN: $maskedVin", color = MeetColors.textMuted, fontSize = 11.sp)
                            }
                            if (activeDtcs.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("EVIDENCIA OBD DETECTADA:", color = MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(activeDtcs.joinToString(", "), color = MeetColors.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (step == 1) {
                        // Step 1: Symptom & Category
                        Text("1. ¿QUÉ OCURRE CON EL VEHÍCULO?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val categories = listOf(
                            "No arranca / Se apaga",
                            "Luz Check Engine / Falla motor",
                            "Frenos / Suspensión / Ruidos",
                            "Fuga de líquido / Sobrecalentamiento",
                            "Mantenimiento Programado / Aceite"
                        )
                        categories.forEach { cat ->
                            val isSelected = userSymptomCategory == cat
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MeetColors.neonGreen.copy(alpha = 0.12f) else MeetColors.cardBackground,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MeetColors.neonGreen else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { userSymptomCategory = cat }
                                    .padding(12.dp)
                            ) {
                                Text(cat, color = if (isSelected) MeetColors.neonGreen else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        OutlinedTextField(
                            value = symptomDetail,
                            onValueChange = { symptomDetail = it },
                            label = { Text("Detalles adicionales del síntoma") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderSubtle,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    } else if (step == 2) {
                        // Step 2: Mobility & Urgency
                        Text("2. CONDICIÓN DE MOVILIDAD Y URGENCIA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Text("Movilidad:", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        MobilityCondition.values().forEach { mob ->
                            val isSel = selectedMobility == mob
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSel) MeetColors.electricBlue.copy(alpha = 0.12f) else MeetColors.cardBackground, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSel) MeetColors.electricBlue else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { selectedMobility = mob }
                                    .padding(10.dp)
                            ) {
                                Text(mob.displayName, color = if (isSel) MeetColors.electricBlue else Color.White, fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Urgencia:", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        ServiceRequestUrgency.values().forEach { urg ->
                            val isSel = selectedUrgency == urg
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSel) Color(urg.badgeColorHex).copy(alpha = 0.12f) else MeetColors.cardBackground, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSel) Color(urg.badgeColorHex) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { selectedUrgency = urg }
                                    .padding(10.dp)
                            ) {
                                Text(urg.displayName, color = if (isSel) Color(urg.badgeColorHex) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Step 3: Modality, Location & Budget
                        Text("3. MODALIDAD, ZONA Y PRESUPUESTO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Text("Modalidad Preferida:", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        ServiceModality.values().forEach { mod ->
                            val isSel = selectedModality == mod
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSel) MeetColors.neonGreen.copy(alpha = 0.12f) else MeetColors.cardBackground, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSel) MeetColors.neonGreen else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { selectedModality = mod }
                                    .padding(10.dp)
                            ) {
                                Text(mod.displayName, color = if (isSel) MeetColors.neonGreen else Color.White, fontSize = 12.sp)
                            }
                        }

                        OutlinedTextField(
                            value = locationZoneText,
                            onValueChange = { locationZoneText = it },
                            label = { Text("Zona aproximada del vehículo") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderSubtle,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = budgetAmount,
                            onValueChange = { budgetAmount = it },
                            label = { Text("Presupuesto estimado (Opcional - CRC)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderSubtle,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (step > 1) {
                        EliteButton(
                            text = "ANTERIOR",
                            onClick = { step-- },
                            color = MeetColors.textSecondary,
                            modifier = Modifier.weight(1f).height(42.dp)
                        )
                    }
                    if (step < 3) {
                        EliteButton(
                            text = "SIGUIENTE",
                            onClick = { step++ },
                            color = MeetColors.neonGreen,
                            modifier = Modifier.weight(1f).height(42.dp)
                        )
                    } else {
                        EliteButton(
                            text = "PUBLICAR SOLICITUD",
                            onClick = {
                                val parsedBudget = budgetAmount.toLongOrNull()?.let {
                                    Money(it, CurrencyCode.CRC)
                                }
                                val request = ServiceRequestV2(
                                    urgency = selectedUrgency,
                                    mobility = selectedMobility,
                                    preferredModality = selectedModality,
                                    locationZone = PrivacyLocationZone(
                                        approximateZoneName = locationZoneText,
                                        approximateRadiusKm = 5.0
                                    ),
                                    evidence = ServiceEvidencePayload(
                                        vehicleId = activeVehicle?.id ?: "V_LOCAL",
                                        vehicleDisplayName = vehicleLabel,
                                        maskedVin = maskedVin,
                                        activeDtcs = activeDtcs,
                                        userReportedSymptom = symptomDetail.ifBlank { userSymptomCategory },
                                        userSymptomCategory = userSymptomCategory
                                    ),
                                    estimatedBudget = parsedBudget
                                )
                                onSubmit(request)
                            },
                            color = MeetColors.neonGreen,
                            modifier = Modifier.weight(1f).height(42.dp)
                        )
                    }
                }
            }
        }
    }
}
