package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.core.engine3d.EngineType
import kotlinx.coroutines.flow.map

// ═══════════════════════════════════════════════════════════════
// COMPONENT LOCATOR 3D — Interactive Graphics Engine & Part Finder
// ═══════════════════════════════════════════════════════════════

data class ComponentInfo(
    val id: String,
    val name: String,
    val category: ComponentCategory,
    val description: String,
    val commonFailures: List<String> = emptyList(),
    val relatedPids: List<String> = emptyList(),
    val relatedDtcs: List<String> = emptyList()
)

enum class ComponentCategory(val label: String, val color: Color) {
    ENGINE("Motor", MeetColors.warning),
    FUEL("Combustible", MeetColors.warning),
    COOLING("Enfriamiento", MeetColors.cyberCyan),
    ELECTRICAL("Eléctrico", MeetColors.electricBlue),
    INTAKE("Admisión", MeetColors.neonGreen),
    EXHAUST("Escape", MeetColors.error),
    SENSORS("Sensores", MeetColors.cyberCyan),
    HIGH_VOLTAGE("Alto Voltaje", Color(0xFFFF9100))
}

@Composable
fun ComponentLocatorScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    
    // Auto-detectar el tipo de motor del OBD2 del vehículo (Cubre L4, V6, V8, y EV desde el 2000 a hoy)
    val detectedEngineType = remember(selectedVehicle) {
        val eng = selectedVehicle?.engine
        val fuel = selectedVehicle?.fuel_type
        if (fuel?.contains("Electric", ignoreCase = true) == true || fuel?.contains("EV", ignoreCase = true) == true) {
            EngineType.ELECTRIC
        } else {
            val engLower = eng?.lowercase().orEmpty()
            when {
                engLower.contains("v8") || engLower.contains("8 cil") -> EngineType.V8
                engLower.contains("v6") || engLower.contains("6 cil") -> EngineType.V6
                engLower.contains("l4") || engLower.contains("i4") || engLower.contains("4 cil") || engLower.contains("linea") -> EngineType.INLINE_4
                else -> EngineType.INLINE_4 // Default estándar
            }
        }
    }

    var selectedEngineType by remember(detectedEngineType) { mutableStateOf(detectedEngineType) }
    
    // Base de datos de componentes filtrada por tipo de motor
    val components = remember(selectedEngineType) { buildComponentDatabase(selectedEngineType) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedComponent by remember { mutableStateOf<ComponentInfo?>(null) }
    var selectedCategory by remember { mutableStateOf<ComponentCategory?>(null) }
    
    var currentScene by remember { mutableStateOf(SceneType.ENGINE_BLOCK) }
    
    // Obtener códigos DTC activos del escáner en tiempo real
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val pendingDtcs by viewModel.pendingDtcs.collectAsState()
    val allActiveDtcs = remember(activeDtcs, pendingDtcs) { activeDtcs + pendingDtcs }

    val filteredComponents = remember(searchQuery, selectedCategory, components) {
        components.filter { c ->
            (searchQuery.isBlank() || c.name.contains(searchQuery, ignoreCase = true)) &&
            (selectedCategory == null || c.category == selectedCategory)
        }
    }

    // Mapeo inverso de Malla 3D a ComponentInfo al presionar la pantalla
    val onMeshSelected: (String, String) -> Unit = { meshId, meshName ->
        val mappedId = mapMeshToComponentId(meshId)
        val comp = components.find { it.id == mappedId }
        if (comp != null) {
            selectedComponent = comp
        } else {
            selectedComponent = ComponentInfo(
                id = meshId,
                name = meshName,
                category = if (selectedEngineType == EngineType.ELECTRIC) ComponentCategory.HIGH_VOLTAGE else ComponentCategory.ENGINE,
                description = "Componente detectado e interactivo en la escena 3D."
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                "Diagnóstico Visual 3D",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            if (allActiveDtcs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.error.copy(alpha = 0.2f))
                        .border(1.dp, MeetColors.error, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, "Warning", tint = MeetColors.error, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${allActiveDtcs.size} DTC",
                            color = MeetColors.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // ── Engine Type Selector Chips (Auto-detectado pero modificable) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EngineType.entries.forEach { type ->
                val isSelected = selectedEngineType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MeetColors.electricBlue.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (isSelected) MeetColors.electricBlue else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable { 
                            selectedEngineType = type
                            selectedComponent = null
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (type) {
                            EngineType.INLINE_4 -> "L4"
                            EngineType.V6 -> "V6"
                            EngineType.V8 -> "V8"
                            EngineType.ELECTRIC -> "EV ⚡"
                        },
                        color = if (isSelected) MeetColors.electricBlue else MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Scene Selector Tabs ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SceneSelectorTab(
                label = if (selectedEngineType == EngineType.ELECTRIC) "🌀 MOTOR/INVER." else "⚙️ MOTOR 3D",
                isSelected = currentScene == SceneType.ENGINE_BLOCK,
                onClick = {
                    currentScene = SceneType.ENGINE_BLOCK
                    selectedComponent = null
                },
                modifier = Modifier.weight(1f)
            )
            SceneSelectorTab(
                label = if (selectedEngineType == EngineType.ELECTRIC) "⚡ SIST. CONTROL" else "🔌 FUSIBLES/RELÉS",
                isSelected = currentScene == SceneType.RELAY_FUSE_BOX,
                onClick = {
                    currentScene = SceneType.RELAY_FUSE_BOX
                    selectedComponent = null
                },
                modifier = Modifier.weight(1f)
            )
            SceneSelectorTab(
                label = if (selectedEngineType == EngineType.ELECTRIC) "⛓️ ALTO VOLTAJE" else "🌀 ARNÉS CABLES",
                isSelected = currentScene == SceneType.WIRING_HARNESS,
                onClick = {
                    currentScene = SceneType.WIRING_HARNESS
                    selectedComponent = null
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── 3D Viewport Box ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(16.dp))
        ) {
            Interactive3DDiagView(
                sceneType = currentScene,
                engineType = selectedEngineType,
                activeDtcs = allActiveDtcs,
                selectedComponentId = selectedComponent?.id,
                onComponentSelected = onMeshSelected,
                modifier = Modifier.fillMaxSize()
            )

            // Etiqueta flotante
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${selectedEngineType.label} - ${currentScene.name.replace("_", " ")}",
                    color = MeetColors.textSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Floating Detail Panel if selected ──
        AnimatedVisibility(
            visible = selectedComponent != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            selectedComponent?.let { comp ->
                val activeDtcOnPiece = comp.relatedDtcs.firstOrNull { allActiveDtcs.contains(it) }

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    EliteCard(
                        backgroundColor = MeetColors.cardBackground,
                        borderColor = if (activeDtcOnPiece != null) MeetColors.error else comp.category.color,
                        glowColor = if (activeDtcOnPiece != null) MeetColors.error else comp.category.color,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    comp.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    comp.category.label,
                                    color = comp.category.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (activeDtcOnPiece != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MeetColors.error.copy(alpha = 0.15f))
                                        .border(0.5.dp, MeetColors.error, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, "Error", tint = MeetColors.error, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "DTC DETECTADO: $activeDtcOnPiece",
                                        color = MeetColors.error,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    EliteTextButton(
                                        text = "GUÍA REPARACIÓN",
                                        onClick = { navController.navigate("repair/$activeDtcOnPiece") },
                                        color = MeetColors.error,
                                        isEnabled = true
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(comp.description, color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)

                            if (comp.commonFailures.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Fallas comunes:", color = MeetColors.warning, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                comp.commonFailures.forEach { failure ->
                                    Text("• $failure", color = MeetColors.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Search Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, "Search", tint = MeetColors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) Text("Buscar componente...", color = MeetColors.textMuted, fontSize = 14.sp)
                    inner()
                }
            )
        }

        Spacer(Modifier.height(6.dp))

        // ── Component List ──
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredComponents, key = { it.id }) { comp ->
                val isSelected = selectedComponent?.id == comp.id
                val hasDtc = comp.relatedDtcs.any { allActiveDtcs.contains(it) }
                val borderColor = if (isSelected) {
                    if (hasDtc) MeetColors.error else comp.category.color
                } else MeetColors.borderSubtle

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) comp.category.color.copy(alpha = 0.08f) else MeetColors.cardBackground)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable {
                            selectedComponent = if (isSelected) null else comp
                            // Conmutar escena automáticamente
                            currentScene = when {
                                comp.id.contains("fuse") || comp.id.contains("relay") || comp.id == "fuel_pump" || comp.id.contains("contactor") || comp.id == "safety_disconnect" -> SceneType.RELAY_FUSE_BOX
                                comp.id.contains("wire") || comp.id == "harness" -> SceneType.WIRING_HARNESS
                                else -> SceneType.ENGINE_BLOCK
                            }
                        }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (hasDtc) MeetColors.error else comp.category.color, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            comp.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasDtc) {
                            Text(
                                "🚨 FALLA", color = MeetColors.error, fontSize = 10.sp, fontWeight = FontWeight.Black
                            )
                        } else {
                            Text(
                                comp.category.label, color = comp.category.color, fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneSelectorTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MeetColors.neonGreen.copy(alpha = 0.15f) else MeetColors.cardBackground)
            .border(1.dp, if (isSelected) MeetColors.neonGreen else MeetColors.borderSubtle, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) MeetColors.neonGreen else Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun mapMeshToComponentId(meshId: String): String? {
    return when {
        meshId.startsWith("spark_plug_") || meshId.startsWith("spark_metal_") || meshId.startsWith("spark_gap_") -> "spark_plugs"
        meshId.startsWith("piston_") || meshId.startsWith("rod_") -> "spark_plugs"
        meshId == "throttle_body" -> "throttle_body"
        meshId == "maf_sensor" -> "maf_sensor"
        meshId == "ect_sensor" -> "coolant_temp"
        meshId == "ckp_sensor" -> "crankshaft_sensor"
        meshId == "alternator" -> "alternator"
        meshId == "oil_pan" -> "oil_pan"
        meshId == "intake_manifold" || meshId.startsWith("intake_runner_") -> "throttle_body"
        meshId == "downpipe" || meshId.startsWith("exhaust_runner_") -> "o2_upstream"
        meshId == "relay_fuel_pump" || meshId == "fuse_10" -> "fuel_pump"
        meshId == "relay_starter" -> "alternator"
        meshId == "relay_fan" || meshId == "fuse_7" -> "thermostat"
        meshId.startsWith("fuse_0") -> "injectors"
        meshId.startsWith("fuse_5") -> "ignition_coils"
        // EV Mappings
        meshId == "electric_motor" || meshId == "stator_windings" -> "traction_motor"
        meshId == "inverter_module" -> "inverter"
        meshId == "hv_battery_pack" || meshId.startsWith("battery_module_") -> "hv_battery"
        meshId == "bms_module" -> "bms"
        meshId.startsWith("contactor") -> "hv_contactors"
        meshId == "safety_disconnect" -> "safety_plug"
        meshId == "hv_main_fuse" -> "hv_fuse"
        meshId.startsWith("wire_hv") -> "hv_battery"
        else -> null
    }
}

private fun buildComponentDatabase(engineType: EngineType): List<ComponentInfo> {
    if (engineType == EngineType.ELECTRIC) {
        return listOf(
            ComponentInfo("traction_motor", "Motor de Tracción Eléctrico", ComponentCategory.HIGH_VOLTAGE, "Convierte la energía eléctrica AC en fuerza motriz mecánica para las ruedas. Contiene el estator y rotor.",
                commonFailures = listOf("Falla de aislamiento en devanados", "Sobrecalentamiento del rotor", "Sensores de posición del rotor (Resolver) erráticos"),
                relatedPids = listOf("010C"), relatedDtcs = listOf("P0A90")),
            ComponentInfo("inverter", "Inversor de Potencia DC/AC", ComponentCategory.HIGH_VOLTAGE, "Módulo electrónico crítico que convierte corriente directa (DC) de la batería en corriente alterna (AC) para el motor, y viceversa en frenado regenerativo.",
                commonFailures = listOf("Cortocircuito en transistores IGBT", "Falla en sensor de temperatura", "Fugas en refrigeración líquida"),
                relatedDtcs = listOf("P0A78")),
            ComponentInfo("hv_battery", "Paquete de Baterías de Litio (HV)", ComponentCategory.HIGH_VOLTAGE, "Almacena la energía eléctrica del vehículo a altos voltajes (usualmente 300V - 800V). Compuesto por celdas en serie.",
                commonFailures = listOf("Desbalanceo de celdas", "Pérdida de capacidad por calor", "Falla de aislamiento a chasis"),
                relatedDtcs = listOf("P0A80")),
            ComponentInfo("bms", "BMS (Gestión de Batería)", ComponentCategory.HIGH_VOLTAGE, "Computadora que monitorea el voltaje, corriente, temperatura y estado de carga (SoC) de cada celda del paquete de baterías.",
                commonFailures = listOf("Falla de comunicación con los módulos", "Sensores de temperatura abiertos", "Falla del circuito de balanceo"),
                relatedDtcs = listOf("P0ABC")),
            ComponentInfo("hv_contactors", "Contactores de Alto Voltaje (+/-)", ComponentCategory.HIGH_VOLTAGE, "Relés electromecánicos de seguridad que conectan físicamente la batería al inversor. Se abren automáticamente en colisiones.",
                commonFailures = listOf("Contactores pegados/soldados por arco", "Bobina del solenoide abierta"),
                relatedDtcs = listOf("P0AA1", "P0AA4")),
            ComponentInfo("safety_plug", "Desconexión de Servicio (MSD)", ComponentCategory.HIGH_VOLTAGE, "Enchufe naranja extraíble que corta físicamente el circuito de alto voltaje a la mitad para proteger a los técnicos durante el servicio.",
                commonFailures = listOf("Pines sucios/desgastados", "Circuito de enclavamiento de seguridad (Interlock) abierto"),
                relatedDtcs = listOf("P0A0D")),
            ComponentInfo("hv_fuse", "Fusible Principal HV", ComponentCategory.HIGH_VOLTAGE, "Fusible especial de fundición rápida diseñado para soportar altos voltajes y corrientes de cortocircuito masivos.",
                commonFailures = listOf("Fusible fundido por sobrecorriente severa"),
                relatedDtcs = listOf("P0A09"))
        )
    }

    return listOf(
        // ENGINE
        ComponentInfo("spark_plugs", "Bujías / Encendido", ComponentCategory.ENGINE, "Generan la chispa que inicia la combustión. Se ubican en la parte superior del bloque, una por cilindro.",
            commonFailures = listOf("Electrodo desgastado", "Fouling por mezcla rica", "Gap incorrecto"),
            relatedPids = listOf("010C", "010E"), relatedDtcs = listOf("P0300", "P0301", "P0302", "P0303", "P0304", "P0305", "P0306", "P0307", "P0308")),
        ComponentInfo("ignition_coils", "Bobinas de Encendido", ComponentCategory.ELECTRICAL, "Transforman 12V del sistema eléctrico a ~40,000V para las bujías. Montadas sobre cada bujía en sistemas COP.",
            commonFailures = listOf("Cortocircuito interno", "Grietas en la bota aislante"),
            relatedPids = listOf("010E"), relatedDtcs = listOf("P0351", "P0352")),
        ComponentInfo("injectors", "Inyectores", ComponentCategory.FUEL, "Atomizan combustible a alta presión dentro de cada cilindro. Controlados por la PCM con pulsos eléctricos.",
            commonFailures = listOf("Obstrucción por depósitos", "Goteo (no sella)"),
            relatedPids = listOf("0106", "0107"), relatedDtcs = listOf("P0201", "P0202")),
        ComponentInfo("throttle_body", "Cuerpo de Aceleración", ComponentCategory.INTAKE, "Controla la cantidad de aire que entra al motor. En sistemas electrónicos, la mariposa se mueve por motor DC.",
            commonFailures = listOf("Carbón acumulado en mariposa", "Motor de mariposa fallido"),
            relatedPids = listOf("0111"), relatedDtcs = listOf("P0121", "P2135")),
        ComponentInfo("maf_sensor", "Sensor MAF", ComponentCategory.SENSORS, "Mide el flujo másico de aire entrante. Ubicado entre el filtro de aire y el cuerpo de aceleración.",
            commonFailures = listOf("Filamento contaminado", "Cortocircuito"),
            relatedPids = listOf("0110"), relatedDtcs = listOf("P0100", "P0102")),
        ComponentInfo("map_sensor", "Sensor MAP", ComponentCategory.SENSORS, "Mide la presión absoluta del múltiple de admisión. Usado para calcular carga del motor.",
            commonFailures = listOf("Manguera de vacío rota", "Sensor dañado"),
            relatedPids = listOf("010B"), relatedDtcs = listOf("P0105", "P0107")),
        ComponentInfo("o2_upstream", "Sensor O2 (Pre-Cat)", ComponentCategory.SENSORS, "Mide oxígeno residual en el escape ANTES del catalizador. La PCM usa esta señal para ajustar la mezcla aire-combustible.",
            commonFailures = listOf("Respuesta lenta (envejecido)", "Calentador abierto"),
            relatedPids = listOf("0114", "0134"), relatedDtcs = listOf("P0130", "P0135")),
        ComponentInfo("o2_downstream", "Sensor O2 (Post-Cat)", ComponentCategory.SENSORS, "Mide eficiencia del catalizador comparando O2 antes y después. Señal estable = catalizador funcional.",
            commonFailures = listOf("Contaminación por anticongelante", "Cable dañado"),
            relatedPids = listOf("0115"), relatedDtcs = listOf("P0136", "P0141")),
        ComponentInfo("catalytic_conv", "Catalizador", ComponentCategory.EXHAUST, "Convierte gases nocivos (CO, HC, NOx) en agua y CO2. Se deteriora por sobrecalentamiento o mezcla rica prolongada.",
            commonFailures = listOf("Eficiencia baja (envejecido)", "Sustrato fundido/obstruido"),
            relatedDtcs = listOf("P0420", "P0430")),
        ComponentInfo("coolant_temp", "Sensor Temp. Refrigerante (ECT)", ComponentCategory.COOLING, "Mide temperatura del refrigerante del motor. Afecta inyección, tiempo de encendido y ventilador.",
            commonFailures = listOf("Lectura errática", "Cortocircuito a tierra"),
            relatedPids = listOf("0105"), relatedDtcs = listOf("P0115", "P0117")),
        ComponentInfo("thermostat", "Termostato", ComponentCategory.COOLING, "Válvula que regula flujo de refrigerante. Cerrado en frío (calentamiento rápido), abierto en caliente.",
            commonFailures = listOf("Atascado abierto (no calienta)", "Atascado cerrado (sobrecalienta)"),
            relatedPids = listOf("0105"), relatedDtcs = listOf("P0128")),
        ComponentInfo("water_pump", "Bomba de Agua", ComponentCategory.COOLING, "Circula refrigerante por el bloque, cabeza y radiador. Accionada por banda o eléctrica.",
            commonFailures = listOf("Fuga por sello", "Impeller corroído", "Rodamiento ruidoso")),
        ComponentInfo("alternator", "Alternador", ComponentCategory.ELECTRICAL, "Genera electricidad para recargar la batería y alimentar el sistema eléctrico. Accionado por banda serpentina.",
            commonFailures = listOf("Diodos rectificadores quemados", "Regulador de voltaje fallido"),
            relatedPids = listOf("0142"), relatedDtcs = listOf("P0562", "P0563")),
        ComponentInfo("crankshaft_sensor", "Sensor Cigüeñal (CKP)", ComponentCategory.SENSORS, "Mide la posición y velocidad de rotación del cigüeñal. Señal crítica para sincronización de inyección y chispa.",
            commonFailures = listOf("Gap incorrecto", "Cable dañado por calor"),
            relatedPids = listOf("010C"), relatedDtcs = listOf("P0335", "P0336")),
        ComponentInfo("camshaft_sensor", "Sensor Árbol de Levas (CMP)", ComponentCategory.SENSORS, "Identifica la posición del árbol de levas para sincronización secuencial de inyectores.",
            commonFailures = listOf("Señal intermitente", "Contaminación por aceite"),
            relatedDtcs = listOf("P0340", "P0341")),
        ComponentInfo("egr_valve", "Válvula EGR", ComponentCategory.EXHAUST, "Recircula gases de escape hacia la admisión para reducir NOx. Se obstruye con carbón frecuentemente.",
            commonFailures = listOf("Obstrucción por carbón", "Diafragma roto"),
            relatedDtcs = listOf("P0401", "P0402")),
        ComponentInfo("evap_purge", "Válvula Purga EVAP", ComponentCategory.FUEL, "Controla el flujo de vapores de combustible del canister de carbón hacia el motor para quemarlos.",
            commonFailures = listOf("Atascada abierta/cerrada", "Fuga en conector"),
            relatedDtcs = listOf("P0441", "P0446")),
        ComponentInfo("fuel_pump", "Bomba de Gasolina", ComponentCategory.FUEL, "Suministra combustible a presión desde el tanque. Ubicada dentro del tanque en la mayoría de vehículos modernos.",
            commonFailures = listOf("Presión baja", "Ruido excesivo"),
            relatedPids = listOf("010A"), relatedDtcs = listOf("P0230", "P0087")),
        ComponentInfo("iat_sensor", "Sensor Temp. Aire (IAT)", ComponentCategory.SENSORS, "Mide la temperatura del aire de admisión. Usado por la PCM para corregir densidad del aire.",
            commonFailures = listOf("Lectura alta falsa", "Circuito abierto"),
            relatedPids = listOf("010F"), relatedDtcs = listOf("P0110", "P0112")),
        ComponentInfo("oil_pan", "Cárter de Aceite", ComponentCategory.ENGINE, "Reservorio inferior de aceite del motor. Contiene el sensor de nivel/presión de aceite.",
            commonFailures = listOf("Fuga por empaque", "Tapón de drenaje dañado"))
    )
}
