package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors

data class RepairStep(
    val title: String,
    val description: String,
    val icon: String,
    val minutes: Int,
    val difficulty: String
)

object DtcRepairHelper {
    fun parseStepString(stepStr: String, stepNum: Int): RepairStep {
        // Clean prefix like "1. ", "Paso 1: ", etc.
        val cleanStr = stepStr.replace(Regex("^(?i)(paso\\s*\\d+[:.]*|\\d+[:.]*)\\s*"), "")
        
        // Split into title and description at the first ':'
        val parts = cleanStr.split(":", limit = 2)
        val title = if (parts.size > 1) parts[0].trim() else "Procedimiento Técnico $stepNum"
        val description = if (parts.size > 1) parts[1].trim() else cleanStr.trim()
        
        // Determine an icon based on title/description keywords
        val icon = when {
            cleanStr.contains("volt", ignoreCase = true) || cleanStr.contains("multímetro", ignoreCase = true) || cleanStr.contains("resistencia", ignoreCase = true) -> "⚡"
            cleanStr.contains("limpiar", ignoreCase = true) || cleanStr.contains("limpieza", ignoreCase = true) || cleanStr.contains("aerosol", ignoreCase = true) -> "🧼"
            cleanStr.contains("combustible", ignoreCase = true) || cleanStr.contains("gasolina", ignoreCase = true) || cleanStr.contains("presión", ignoreCase = true) -> "⛽"
            cleanStr.contains("escáner", ignoreCase = true) || cleanStr.contains("escanear", ignoreCase = true) || cleanStr.contains("meet", ignoreCase = true) -> "📱"
            cleanStr.contains("conducir", ignoreCase = true) || cleanStr.contains("ciclo", ignoreCase = true) || cleanStr.contains("ruta", ignoreCase = true) -> "🚗"
            else -> "🔧"
        }

        return RepairStep(
            title = title,
            description = description,
            icon = icon,
            minutes = 15 + (stepNum * 5) % 20,
            difficulty = if (stepNum > 3) "Difícil" else if (stepNum > 1) "Medio" else "Fácil"
        )
    }
}

object DtcRepairDatabase {
    fun getSteps(code: String): List<RepairStep> {
        val u = code.uppercase()
        return when {
            u.startsWith("P030") || u == "P0300" -> listOf(
                RepairStep("Prueba de Bobinas de Encendido", "Intercambia la bobina del cilindro afectado con uno sano (ej: cambia bobina 1 a la posición 2). Escanea de nuevo el motor; si la falla (misfire) se desplaza al cilindro 2, la bobina está defectuosa y debe ser reemplazada.", "⚡", 15, "Fácil"),
                RepairStep("Inspección de la Bujía", "Desmonta la bujía del cilindro que falla usando una copa para bujías de 5/8\". Inspecciona el desgaste del electrodo, presencia de aceite, depósitos de carbón o grietas en la cerámica. Calibra la holgura del electrodo según especificaciones del manual del fabricante o reemplázala.", "🔧", 20, "Fácil"),
                RepairStep("Verificación de Inyectores de Combustible", "Usa un destornillador largo como estetoscopio apoyándolo en el cuerpo del inyector. Debes escuchar un tic-tac constante con el motor en marcha. Adicionalmente, mide la resistencia del inyector (debe marcar entre 11 y 16 ohmios). Si marca infinito o resistencia fuera de rango, reemplázalo.", "⛽", 25, "Medio"),
                RepairStep("Prueba de Compresión", "Si hay chispa y combustible correctos, realiza una prueba de compresión utilizando un manómetro en el cilindro afectado. La presión debe ser superior a 120 PSI y no variar más del 10% con respecto a los otros cilindros. Valores bajos indican fuga en válvulas, junta de culata rota o anillos desgastados.", "📊", 30, "Difícil")
            )
            u == "P0171" || u == "P0174" -> listOf(
                RepairStep("Búsqueda de Fugas de Vacío", "Revisa todas las mangueras de vacío del colector de admisión, válvula PCV y conductos de hule del filtro de aire. El aire no medido que entra al motor causa mezcla pobre. Rocía un poco de agua jabonosa o limpiador de carburador en las juntas con el motor encendido; si las RPM cambian, localizaste la fuga.", "💨", 20, "Fácil"),
                RepairStep("Limpieza del Sensor MAF", "Desmonta el sensor de flujo de masa de aire (MAF) de la tubería de admisión. Aplica abundante aerosol limpiador de sensores MAF directamente sobre el filamento caliente (NUNCA lo toques físicamente ni uses solventes agresivos). Deja secar por 10 minutos y vuelve a instalar.", "🧼", 15, "Fácil"),
                RepairStep("Prueba de Presión de Combustible", "Conecta un manómetro en la válvula de servicio del riel de inyectores. Abre el switch y mide la presión de la bomba. Debe mantenerse constante (ej: 40-50 PSI). Si es baja, reemplaza el filtro de gasolina obstruido o la bomba de combustible.", "⛽", 30, "Medio"),
                RepairStep("Inspección del Sensor de Oxígeno", "Conéctate al scanner OBD2 y grafica el voltaje del Sensor de Oxígeno del Banco 1. El voltaje debe oscilar rápidamente entre 0.1V y 0.9V. Si el sensor se queda estancado en un solo valor cercano a cero voltios de manera constante, reemplázalo.", "📡", 25, "Medio")
            )
            else -> listOf(
                RepairStep("Inspección del Conector y Cableado", "Localiza el sensor o actuador asociado al código $code. Desconecta el terminal eléctrico, busca rastros de corrosión o humedad. Limpia los pines con limpiador de contactos electrónicos. Revisa que los cables del arnés no estén pelados, derretidos por calor o quebrados.", "🔌", 15, "Fácil"),
                RepairStep("Medición de Voltaje de Referencia", "Pon el switch en posición ON con el motor apagado. Con un multímetro digital en corriente directa (VDC), mide el voltaje en el pin de señal de alimentación del arnés desconectado del sensor. Debe marcar exactamente 5.0V o 12.0V de alimentación de la ECU.", "⚡", 15, "Fácil"),
                RepairStep("Prueba de Resistencia de Componente", "Configura el multímetro en ohmios (Ω). Mide la resistencia entre las terminales internas del sensor o solenoide retirado del vehículo. Compara la lectura con los valores especificados en el manual de taller. Si la lectura es cero (cortocircuito) o infinito (circuito abierto), el sensor está dañado.", "📟", 20, "Medio"),
                RepairStep("Ciclo de Manejo y Confirmación", "Utiliza la función de Borrar Códigos de MEET para apagar la luz Check Engine. Conduce el vehículo por 10-15 minutos en ciudad y carretera para completar un ciclo de conducción de la ECU. Vuelve a escanear; si el monitor está completo y no hay DTCs activos, la reparación fue exitosa.", "🚗", 20, "Fácil")
            )
        }
    }

    fun getRequiredTools(code: String): List<String> {
        val u = code.uppercase()
        return when {
            u.startsWith("P030") || u == "P0300" -> listOf("Llave de Bujías 5/8\"", "Multímetro Digital", "Bobina de Repuesto", "Escáner OBD2 MEET")
            u == "P0171" || u == "P0174" -> listOf("Limpiador de Sensor MAF", "Manómetro de Combustible", "Destornillador Plano/Fórmula", "Escáner OBD2 MEET")
            else -> listOf("Multímetro Digital", "Limpiador de Contactos Eléctricos", "Juego de Destornilladores y Llaves", "Escáner OBD2 MEET")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcRepairGuideScreen(
    navController: NavController,
    dtcCode: String,
    viewModel: ObdViewModel
) {
    var definition by remember { mutableStateOf<com.elysium369.meet.data.local.entities.DtcDefinitionEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(dtcCode) {
        isLoading = true
        definition = viewModel.getDtcDefinition(dtcCode)
        isLoading = false
    }

    val steps = remember(definition) {
        val rawSteps = definition?.diagnosticSteps
        if (!rawSteps.isNullOrEmpty()) {
            try {
                if (rawSteps.trim().startsWith("[")) {
                    val array = org.json.JSONArray(rawSteps)
                    val list = mutableListOf<RepairStep>()
                    for (i in 0 until array.length()) {
                        list.add(DtcRepairHelper.parseStepString(array.getString(i), i + 1))
                    }
                    list
                } else {
                    val lines = rawSteps.split(Regex("[|\n]")).map { it.trim() }.filter { it.isNotEmpty() }
                    lines.mapIndexed { idx, line -> DtcRepairHelper.parseStepString(line, idx + 1) }
                }
            } catch (e: Exception) {
                listOf(RepairStep("Procedimiento de Diagnóstico", rawSteps, "🔧", 20, "Medio"))
            }
        } else {
            DtcRepairDatabase.getSteps(dtcCode)
        }
    }

    val tools = remember(definition) {
        val rawTools = definition?.specialToolsRequired
        if (!rawTools.isNullOrEmpty()) {
            try {
                if (rawTools.trim().startsWith("[")) {
                    val array = org.json.JSONArray(rawTools)
                    val list = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        list.add(array.getString(i))
                    }
                    list
                } else {
                    rawTools.split(Regex("[,|\n]")).map { it.trim() }.filter { it.isNotEmpty() }
                }
            } catch (e: Exception) {
                listOf(rawTools)
            }
        } else {
            DtcRepairDatabase.getRequiredTools(dtcCode)
        }
    }

    var activeStepIdx by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()

    // Checklist for tools
    val toolChecks = remember(tools) { mutableStateListOf(*Array(tools.size) { false }) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            EliteTopAppBar(
                title = "GUÍA DE REPARACIÓN",
                subtitle = "Código DTC: $dtcCode",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MeetColors.cyberCyan)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // ═══════════ HEADER CARD ═══════════
                EliteCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.cyberCyan,
                    enableHolo3D = true
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "ASISTENTE DE MECÁNICA DE PRECISIÓN",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Procedimiento Técnico $dtcCode",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val descEs = definition?.descriptionEs ?: ""
                        if (descEs.isNotEmpty()) {
                            Text(
                                descEs,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            "Sigue las instrucciones paso a paso desarrolladas por ingenieros de servicio técnico automotriz.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                // ═══════════ REQUIRED TOOLS CHECKLIST ═══════════
                if (tools.isNotEmpty()) {
                    PhantomSectionHeader("Herramientas Requeridas")
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MeetColors.borderSubtle,
                        enableHolo3D = false
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            tools.forEachIndexed { idx, tool ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { if (idx < toolChecks.size) toolChecks[idx] = !toolChecks[idx] }
                                        .padding(vertical = 6.dp)
                                ) {
                                    val isChecked = idx < toolChecks.size && toolChecks[idx]
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { if (idx < toolChecks.size) toolChecks[idx] = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MeetColors.neonGreen,
                                            uncheckedColor = MeetColors.textSecondary,
                                            checkmarkColor = MeetColors.backgroundDeep
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = tool,
                                        color = if (isChecked) MeetColors.textSecondary else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ═══════════ STEP PROGRESS TIMELINE ═══════════
                if (steps.isNotEmpty()) {
                    PhantomSectionHeader("Progreso del Diagnóstico")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { idx, _ ->
                            val isCompleted = idx < activeStepIdx
                            val isActive = idx == activeStepIdx
                            val barColor = when {
                                isCompleted -> MeetColors.neonGreen
                                isActive -> MeetColors.cyberCyan
                                else -> MeetColors.borderSubtle
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(barColor)
                            )
                        }
                    }

                    // ═══════════ ACTIVE STEP CARD ═══════════
                    val currentStep = steps.getOrNull(activeStepIdx)
                    if (currentStep != null) {
                        AnimatedContent(
                            targetState = currentStep,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { it } togetherWith
                                fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { -it }
                            },
                            label = "stepAnim"
                        ) { targetStep ->
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = when (targetStep.difficulty) {
                                    "Fácil" -> MeetColors.neonGreen
                                    "Medio" -> MeetColors.warning
                                    else -> MeetColors.error
                                },
                                enableHolo3D = true
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    // Step header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(MeetColors.neonGreen.copy(alpha = 0.12f))
                                                    .border(1.dp, MeetColors.neonGreen, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = targetStep.icon,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                "PASO ${activeStepIdx + 1} DE ${steps.size}",
                                                color = MeetColors.neonGreen,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            )
                                        }

                                        // Difficulty tag
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when (targetStep.difficulty) {
                                                        "Fácil" -> MeetColors.neonGreen.copy(alpha = 0.12f)
                                                        "Medio" -> MeetColors.warning.copy(alpha = 0.12f)
                                                        else -> MeetColors.error.copy(alpha = 0.12f)
                                                    }
                                                )
                                                .border(
                                                    1.dp,
                                                    when (targetStep.difficulty) {
                                                        "Fácil" -> MeetColors.neonGreen
                                                        "Medio" -> MeetColors.warning
                                                        else -> MeetColors.error
                                                    },
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = targetStep.difficulty.uppercase(),
                                                color = when (targetStep.difficulty) {
                                                    "Fácil" -> MeetColors.neonGreen
                                                    "Medio" -> MeetColors.warning
                                                    else -> MeetColors.error
                                                },
                                                fontWeight = FontWeight.Black,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Step Title
                                    Text(
                                        text = targetStep.title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Step Instruction
                                    Text(
                                        text = targetStep.description,
                                        color = MeetColors.textPrimary,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Estimation
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "⏱️ Tiempo Estimado: ",
                                            color = MeetColors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "${targetStep.minutes} min",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ═══════════ NAVIGATION BUTTONS ═══════════
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (activeStepIdx > 0) {
                            EliteOutlinedButton(
                                text = "ANTERIOR",
                                onClick = { activeStepIdx-- },
                                color = MeetColors.cyberCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val isLast = activeStepIdx == steps.size - 1
                        EliteButton(
                            text = if (isLast) "REPARACIÓN LISTA" else "SIGUIENTE PASO",
                            onClick = {
                                if (isLast) {
                                    navController.popBackStack()
                                } else {
                                    activeStepIdx++
                                }
                            },
                            color = MeetColors.neonGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // Empty steps fallback
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No se encontraron pasos de reparación para este código.",
                            color = MeetColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
