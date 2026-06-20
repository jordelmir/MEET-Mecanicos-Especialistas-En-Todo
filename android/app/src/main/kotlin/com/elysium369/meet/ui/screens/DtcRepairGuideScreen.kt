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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import kotlinx.coroutines.launch

data class RepairStep(
    val title: String,
    val description: String,
    val icon: String,
    val minutes: Int,
    val difficulty: String
)

object DtcRepairHelper {
    fun parseStepString(stepStr: String, stepNum: Int): RepairStep {
        val cleanStr = stepStr.replace(Regex("^(?i)(paso\\s*\\d+[:.]*|\\d+[:.]*)\\s*"), "")
        val parts = cleanStr.split(":", limit = 2)
        val title = if (parts.size > 1) parts[0].trim() else "Procedimiento Técnico $stepNum"
        val description = if (parts.size > 1) parts[1].trim() else cleanStr.trim()
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
    var symptoms by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcSymptomEntity>>(emptyList()) }
    var causes by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcCauseEntity>>(emptyList()) }
    var dbProcedures by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcProcedureEntity>>(emptyList()) }
    var relatedPids by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcRelatedPidEntity>>(emptyList()) }
    var coOccurrences by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity>>(emptyList()) }
    var repairCosts by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcRepairCostEntity>>(emptyList()) }
    var verifiedFixes by remember { mutableStateOf<List<com.elysium369.meet.data.local.entities.DtcVerifiedFixEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var activeStepIdx by remember { mutableIntStateOf(0) }
    
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Upvoted fixes cache to disable upvote button after click
    val upvotedFixIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(dtcCode) {
        isLoading = true
        coroutineScope.launch {
            definition = viewModel.getDtcDefinition(dtcCode)
            symptoms = viewModel.getDtcSymptoms(dtcCode)
            causes = viewModel.getDtcCauses(dtcCode)
            dbProcedures = viewModel.getDtcProcedures(dtcCode)
            relatedPids = viewModel.getDtcRelatedPids(dtcCode)
            coOccurrences = viewModel.getDtcCoOccurrences(dtcCode)
            repairCosts = viewModel.getDtcRepairCosts(dtcCode)
            verifiedFixes = viewModel.getDtcVerifiedFixes(dtcCode)
            isLoading = false
        }
    }

    val steps = remember(definition, dbProcedures) {
        val rawStepsList = if (dbProcedures.isNotEmpty()) {
            dbProcedures.map { proc ->
                RepairStep(
                    title = proc.titleEs,
                    description = proc.descriptionEs,
                    icon = proc.icon,
                    minutes = proc.estimatedMinutes,
                    difficulty = when (proc.difficulty.lowercase()) {
                        "facil" -> "Fácil"
                        "medio" -> "Medio"
                        else -> "Difícil"
                    }
                )
            }
        } else {
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
        rawStepsList.distinctBy { it.description.trim().lowercase() }
    }

    val tools = remember(definition, dbProcedures) {
        val toolsFromProcedures = dbProcedures.mapNotNull { it.toolRequired }.filter { it.isNotBlank() }
        if (toolsFromProcedures.isNotEmpty()) {
            toolsFromProcedures
        } else {
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
    }

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
            ) {
                // Tab Navigation
                val tabs = listOf("Diagnóstico", "Procedimiento", "Parámetros Live", "Soluciones")
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MeetColors.cyberCyan,
                    divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) },
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MeetColors.cyberCyan,
                                height = 3.dp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Black else FontWeight.Bold,
                                    color = if (selectedTabIndex == index) MeetColors.cyberCyan else MeetColors.textSecondary,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        )
                    }
                }

                // Tab Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    when (selectedTabIndex) {
                        0 -> {
                            // ═══════════ TAB 0: DIAGNÓSTICO ═══════════
                            // Header Card
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = MeetColors.cyberCyan,
                                enableHolo3D = true
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "GRAFO DE CONOCIMIENTO OBD2",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Análisis Técnico $dtcCode",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    val system = definition?.system ?: "General"
                                    val severity = definition?.severity ?: "Media"
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .background(MeetColors.cyberCyan.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(system.uppercase(), color = MeetColors.cyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    when (severity.lowercase()) {
                                                        "high", "alta", "crítico" -> MeetColors.error.copy(alpha = 0.12f)
                                                        "low", "baja" -> MeetColors.neonGreen.copy(alpha = 0.12f)
                                                        else -> MeetColors.warning.copy(alpha = 0.12f)
                                                    },
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "SEVERIDAD: ${severity.uppercase()}",
                                                color = when (severity.lowercase()) {
                                                    "high", "alta", "crítico" -> MeetColors.error
                                                    "low", "baja" -> MeetColors.neonGreen
                                                    else -> MeetColors.warning
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val descEs = definition?.descriptionEs ?: ""
                                    if (descEs.isNotEmpty()) {
                                        Text(
                                            descEs,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }

                            // Symptoms
                            PhantomSectionHeader("Síntomas Comunes")
                            EliteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (symptoms.isNotEmpty()) {
                                        symptoms.forEach { symptom ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Text("•", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(symptom.symptomEs, color = Color.White, fontSize = 13.sp)
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        Text(
                                                            "Probabilidad: ${symptom.probability.uppercase()}",
                                                            color = MeetColors.textSecondary,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        if (symptom.isDriverNoticeable) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MeetColors.warning.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("PERCEPTIBLE POR CONDUCTOR", color = MeetColors.warning, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val fallbackSymptoms = definition?.symptoms
                                        if (!fallbackSymptoms.isNullOrBlank()) {
                                            Text(fallbackSymptoms, color = Color.White, fontSize = 13.sp)
                                        } else {
                                            Text("No se encontraron síntomas específicos registrados para este código.", color = MeetColors.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Causes
                            PhantomSectionHeader("Causas Probables")
                            EliteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (causes.isNotEmpty()) {
                                        causes.forEach { cause ->
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        cause.causeEs,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        if (cause.isElectronic) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MeetColors.electricBlue.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("⚡ ELEC", color = MeetColors.electricBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                        if (cause.isMechanical) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(MeetColors.warning.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("⚙️ MEC", color = MeetColors.warning, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }

                                                // Probability bar
                                                val probValue = when (cause.probability.lowercase()) {
                                                    "alta" -> 0.9f
                                                    "media" -> 0.6f
                                                    else -> 0.3f
                                                }
                                                val probColor = when (cause.probability.lowercase()) {
                                                    "alta" -> MeetColors.neonGreen
                                                    "media" -> MeetColors.warning
                                                    else -> MeetColors.textSecondary
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    LinearProgressIndicator(
                                                        progress = { probValue },
                                                        color = probColor,
                                                        trackColor = Color.White.copy(alpha = 0.05f),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(4.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = cause.probability.uppercase(),
                                                        color = probColor,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val fallbackCauses = definition?.possibleCauses
                                        if (!fallbackCauses.isNullOrBlank()) {
                                            Text(fallbackCauses.replace("|", "\n"), color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                                        } else {
                                            Text("No se encontraron causas comunes registradas.", color = MeetColors.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Co-occurrences
                            if (coOccurrences.isNotEmpty()) {
                                PhantomSectionHeader("Códigos Asociados (Co-ocurrencia)")
                                EliteCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Los siguientes códigos suelen aparecer de manera simultánea en el diagnóstico debido a fallas mecánicas en cascada:",
                                            color = MeetColors.textSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        coOccurrences.forEach { co ->
                                            val related = if (co.dtcCode == dtcCode) co.relatedDtcCode else co.dtcCode
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(6.dp))
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        related,
                                                        color = MeetColors.cyberCyan,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 13.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Fuerza: ${(co.correlationStrength * 100).toInt()}%",
                                                        color = MeetColors.textSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                val combo = co.combinedDiagnosisEs
                                                if (!combo.isNullOrBlank()) {
                                                    Text(
                                                        combo,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Repair Costs
                            if (repairCosts.isNotEmpty()) {
                                PhantomSectionHeader("Costos Estimados de Reparación")
                                EliteCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        repairCosts.forEach { cost ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        "Rango Estimado (${cost.region})",
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    val labor = cost.laborHours
                                                    if (labor != null && labor > 0) {
                                                        Text(
                                                            "Tiempo estimado de labor: $labor h",
                                                            color = MeetColors.textSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "$${cost.minCostUsd.toInt()} - $${cost.maxCostUsd.toInt()} ${cost.currency}",
                                                    color = MeetColors.neonGreen,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 16.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            val desc = cost.partsDescription
                                            if (!desc.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "Repuestos sugeridos: $desc",
                                                    color = MeetColors.textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val fallbackCost = definition?.repairCostUSD
                                if (!fallbackCost.isNullOrBlank()) {
                                    PhantomSectionHeader("Costos Estimados de Reparación")
                                    EliteCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Rango Promedio", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text(fallbackCost, color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                            }
                                            val estHours = definition?.laborHoursEstimate
                                            if (!estHours.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Tiempo estimado de labor: $estHours", color = MeetColors.textSecondary, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // ═══════════ TAB 1: PROCEDIMIENTO ═══════════
                            // Required tools
                            if (tools.isNotEmpty()) {
                                PhantomSectionHeader("Herramientas Requeridas")
                                EliteCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        tools.forEachIndexed { idx, tool ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { if (idx < toolChecks.size) toolChecks[idx] = !toolChecks[idx] }
                                                    .padding(vertical = 4.dp)
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

                            // Stepper
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
                                                            Text(text = targetStep.icon, fontSize = 14.sp)
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
                                                Text(
                                                    text = targetStep.title,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = targetStep.description,
                                                    color = MeetColors.textPrimary,
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("⏱️ Tiempo Estimado: ", color = MeetColors.textSecondary, fontSize = 11.sp)
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
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No se encontraron pasos de reparación para este código.", color = MeetColors.textSecondary, fontSize = 14.sp)
                                }
                            }
                        }

                        2 -> {
                            // ═══════════ TAB 2: PARÁMETROS LIVE ═══════════
                            PhantomSectionHeader("Sensores a Monitorear en Tiempo Real")
                            EliteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (relatedPids.isNotEmpty()) {
                                        Text(
                                            "Para validar la falla o confirmar la solución, monitorea los siguientes parámetros en vivo en la pantalla de datos en tiempo real:",
                                            color = MeetColors.textSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                        relatedPids.forEach { pid ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Pulsing live indicator
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(MeetColors.cyberCyan)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        pid.pidNameEs,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        "Comando OBD: ${pid.pidCommand}",
                                                        color = MeetColors.textSecondary,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                                val normalRange = pid.normalRange
                                                if (!normalRange.isNullOrBlank()) {
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text("RANGO NORMAL", color = MeetColors.cyberCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                        Text(
                                                            "$normalRange ${pid.unit ?: ""}".trim(),
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Black,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        val fallbackPids = definition?.freezeFramePIDs
                                        if (!fallbackPids.isNullOrBlank()) {
                                            Text(
                                                "Monitorea los siguientes PIDs relacionados:",
                                                color = MeetColors.textSecondary,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(fallbackPids, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                        } else {
                                            Text("No hay parámetros de sensores específicos vinculados a este código en la base de datos local.", color = MeetColors.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // ═══════════ TAB 3: SOLUCIONES VERIFICADAS ═══════════
                            PhantomSectionHeader("Soluciones Confirmadas (TSB / Comunidad)")
                            if (verifiedFixes.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    verifiedFixes.forEach { fix ->
                                        EliteCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            glowColor = MeetColors.neonGreen.copy(alpha = 0.2f)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Source Badge
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                when (fix.source?.lowercase()) {
                                                                    "tsb" -> MeetColors.electricBlue.copy(alpha = 0.12f)
                                                                    "oem" -> MeetColors.cyberCyan.copy(alpha = 0.12f)
                                                                    else -> MeetColors.neonGreen.copy(alpha = 0.12f)
                                                                },
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = (fix.source ?: "COMUNIDAD").uppercase(),
                                                            color = when (fix.source?.lowercase()) {
                                                                "tsb" -> MeetColors.electricBlue
                                                                "oem" -> MeetColors.cyberCyan
                                                                else -> MeetColors.neonGreen
                                                            },
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    // Success Rate tag
                                                    Text(
                                                        text = "Éxito: ${(fix.successRate * 100).toInt()}%",
                                                        color = MeetColors.neonGreen,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = fix.fixDescriptionEs,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 18.sp
                                                )

                                                val part = fix.partRequired
                                                if (!part.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "🔩 Repuesto: $part",
                                                        color = MeetColors.textSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))
                                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                                Spacer(modifier = Modifier.height(8.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val cost = fix.estimatedCostUsd
                                                    if (cost != null && cost > 0) {
                                                        Text(
                                                            text = "Costo repuesto: ~$${cost.toInt()} USD",
                                                            color = MeetColors.textSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    } else {
                                                        Spacer(modifier = Modifier.width(1.dp))
                                                    }

                                                    // Upvote Button
                                                    val isUpvoted = upvotedFixIds.contains(fix.id)
                                                    Button(
                                                        onClick = {
                                                            if (!isUpvoted) {
                                                                coroutineScope.launch {
                                                                    viewModel.upvoteDtcFix(fix.id)
                                                                    upvotedFixIds.add(fix.id)
                                                                    // Refresh list
                                                                    verifiedFixes = viewModel.getDtcVerifiedFixes(dtcCode)
                                                                }
                                                            }
                                                        },
                                                        enabled = !isUpvoted,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isUpvoted) Color.Transparent else MeetColors.neonGreen.copy(alpha = 0.1f),
                                                            contentColor = MeetColors.neonGreen,
                                                            disabledContainerColor = Color.Transparent,
                                                            disabledContentColor = MeetColors.textSecondary
                                                        ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        border = if (isUpvoted) null else androidx.compose.foundation.BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Text(
                                                            text = if (isUpvoted) "✓ VALIDADO" else "👍 ÚTIL (${fix.voteCount})",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                EliteCard(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🔧", fontSize = 32.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "No hay soluciones verificadas cargadas aún para este DTC.",
                                                color = MeetColors.textSecondary,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
