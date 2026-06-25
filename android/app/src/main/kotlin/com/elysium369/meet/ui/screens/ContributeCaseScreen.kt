package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.RepairNetworkViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributeCaseScreen(navController: NavController, viewModel: RepairNetworkViewModel) {
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var dtc by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf("") }
    var solution by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var timeSpentText by remember { mutableStateOf("") }
    var partsUsed by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.isLoading.collectAsState()

    val scrollState = rememberScrollState()
    val normalizedDtc = dtc.trim().uppercase()
    val requiredCompletion = listOf(
        make.isNotBlank(),
        model.isNotBlank(),
        yearText.toIntOrNull() != null,
        engine.isNotBlank(),
        country.isNotBlank(),
        normalizedDtc.isNotBlank(),
        symptoms.isNotBlank(),
        solution.isNotBlank(),
        costText.toDoubleOrNull() != null,
        timeSpentText.toIntOrNull() != null
    ).count { it }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "CONTRIBUIR CASO",
                subtitle = "COMPARTE TU EXPERIENCIA TÉCNICA",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Registra un caso de reparación real para que otros mecánicos y entusiastas puedan encontrar la solución exacta de forma offline y online.",
                color = MeetColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            EliteCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = MeetColors.cyberCyan,
                backgroundColor = MeetColors.backgroundDeep,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "CAPTURA MÍNIMA PARA UN CASO ÚTIL",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Cliente: qué falla, cuándo pasa, luces, olores o ruido. Taller: cómo lo confirmaste, qué pieza/procedimiento resolvió, cuánto tardó y cómo verificaste la reparación.",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        "Completitud actual: $requiredCompletion/10",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (errorMessage != null) {
                EliteCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.error,
                    backgroundColor = MeetColors.error.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Error", tint = MeetColors.error)
                        Text(errorMessage!!, color = MeetColors.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            EliteCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = MeetColors.neonGreen,
                backgroundColor = MeetColors.backgroundDeep,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "DATOS DEL VEHÍCULO",
                        color = MeetColors.neonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = make,
                            onValueChange = { make = it },
                            label = { Text("Marca (Ej. Toyota)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.borderSubtle),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Modelo (Ej. Corolla)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.borderSubtle),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { yearText = it },
                            label = { Text("Año (Ej. 2018)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.borderSubtle),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = engine,
                            onValueChange = { engine = it },
                            label = { Text("Motor (Ej. 1.8L Dual VVT-i)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.borderSubtle),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("País (Ej. México)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.borderSubtle),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            EliteCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = MeetColors.cyberCyan,
                backgroundColor = MeetColors.backgroundDeep,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "DIAGNÓSTICO Y SOLUCIÓN",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = dtc,
                        onValueChange = { dtc = it.uppercase() },
                        label = { Text("Código DTC Asociado (Ej. P0300)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan, unfocusedBorderColor = MeetColors.borderSubtle),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = symptoms,
                        onValueChange = { symptoms = it },
                        label = { Text("Síntomas y contexto del cliente", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan, unfocusedBorderColor = MeetColors.borderSubtle),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = solution,
                        onValueChange = { solution = it },
                        label = { Text("Diagnóstico que confirmó la causa y solución paso a paso", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan, unfocusedBorderColor = MeetColors.borderSubtle),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = partsUsed,
                        onValueChange = { partsUsed = it },
                        label = { Text("Repuestos/Piezas Reemplazadas (Ej. Bobina de encendido cilindro 3)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan, unfocusedBorderColor = MeetColors.borderSubtle),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            EliteCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = MeetColors.warning,
                backgroundColor = MeetColors.backgroundDeep,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "COSTOS Y TIEMPO",
                        color = MeetColors.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = costText,
                            onValueChange = { costText = it },
                            label = { Text("Costo Total Est. (USD)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.warning, unfocusedBorderColor = MeetColors.borderSubtle),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = timeSpentText,
                            onValueChange = { timeSpentText = it },
                            label = { Text("Tiempo Invertido (Minutos)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.warning, unfocusedBorderColor = MeetColors.borderSubtle),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MeetColors.neonGreen)
                }
            } else {
                EliteButton(
                    text = "Publicar Caso de Reparación",
                    onClick = {
                        val year = yearText.toIntOrNull()
                        val cost = costText.toDoubleOrNull()
                        val timeSpent = timeSpentText.toIntOrNull()
                        val dtcRegex = Regex("^[PCBU][0-3][0-9A-F]{3}$", RegexOption.IGNORE_CASE)

                        if (make.isBlank() || model.isBlank() || year == null || engine.isBlank() ||
                            country.isBlank() || normalizedDtc.isBlank() || symptoms.isBlank() || solution.isBlank() ||
                            cost == null || timeSpent == null
                        ) {
                            errorMessage = "Por favor, complete todos los campos obligatorios con valores válidos."
                        } else if (!dtcRegex.matches(normalizedDtc)) {
                            errorMessage = "El DTC debe tener formato real OBD/UDS. Ejemplo: P0300, U0100, B0028."
                        } else {
                            errorMessage = null
                            viewModel.submitCase(
                                make = make,
                                model = model,
                                year = year,
                                engine = engine,
                                country = country,
                                dtc = normalizedDtc,
                                symptoms = symptoms,
                                solution = solution,
                                cost = cost,
                                timeSpent = timeSpent,
                                partsUsed = partsUsed,
                                onSuccess = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
