package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
    val relatedDtcs: List<String> = emptyList(),
    val location: String = "",
    val requiredTools: List<String> = emptyList(),
    val professionalChecks: List<String> = emptyList(),
    val repairWorkflow: List<String> = emptyList(),
    val serviceSpecs: List<String> = emptyList(),
    val safetyNotes: List<String> = emptyList()
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
    var explodedServiceView by remember { mutableStateOf(false) }
    
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
                description = "Componente detectado en el visor 3D. Valide alimentación, masa, señal y acceso físico antes de desmontar."
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
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
                        Icon(Icons.Default.Warning, "Alerta", tint = MeetColors.error, modifier = Modifier.size(12.dp))
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
                .height(360.dp)
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
                explodedServiceView = explodedServiceView,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${selectedEngineType.label} - ${sceneDisplayName(currentScene, selectedEngineType)}",
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (explodedServiceView) MeetColors.warning.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.52f))
                        .border(1.dp, if (explodedServiceView) MeetColors.warning else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable { explodedServiceView = !explodedServiceView }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (explodedServiceView) "CORTE SERVICIO" else "VISTA ENSAMBLE",
                        color = if (explodedServiceView) MeetColors.warning else MeetColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill("${components.size} piezas", MeetColors.cyberCyan)
                StatusPill("${allActiveDtcs.size} DTC", if (allActiveDtcs.isNotEmpty()) MeetColors.error else MeetColors.neonGreen)
                selectedComponent?.let { StatusPill(it.category.label, it.category.color) }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
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
                                    Icon(Icons.Default.Warning, "Falla", tint = MeetColors.error, modifier = Modifier.size(16.dp))
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

                            val location = comp.location.ifBlank { comp.serviceLocation() }
                            val tools = comp.requiredTools.ifEmpty { comp.defaultTools() }
                            val checks = comp.professionalChecks.ifEmpty { comp.defaultProfessionalChecks() }
                            val workflow = comp.repairWorkflow.ifEmpty { comp.defaultRepairWorkflow() }
                            val specs = comp.serviceSpecs.ifEmpty { comp.defaultServiceSpecs() }
                            val safety = comp.safetyNotes.ifEmpty { comp.defaultSafetyNotes() }

                            Spacer(Modifier.height(8.dp))
                            Text("Ubicación", color = comp.category.color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text(location, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)

                            if (comp.commonFailures.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Fallas comunes:", color = MeetColors.warning, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                comp.commonFailures.forEach { failure ->
                                    Text("• $failure", color = MeetColors.textSecondary, fontSize = 11.sp)
                                }
                            }

                            ProfessionalInfoSection("Pruebas de taller", checks, MeetColors.cyberCyan)
                            ProfessionalInfoSection("Flujo de reparación", workflow, MeetColors.neonGreen)
                            ProfessionalInfoSection("Especificaciones", specs, MeetColors.warning)
                            ProfessionalInfoSection("Herramientas", tools, comp.category.color)
                            ProfessionalInfoSection("Seguridad", safety, MeetColors.error)

                            if (comp.relatedPids.isNotEmpty() || comp.relatedDtcs.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (comp.relatedPids.isNotEmpty()) StatusPill("PID ${comp.relatedPids.joinToString(", ")}", MeetColors.electricBlue)
                                    if (comp.relatedDtcs.isNotEmpty()) StatusPill("DTC ${comp.relatedDtcs.take(4).joinToString(", ")}", MeetColors.error)
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
            Icon(Icons.Default.Search, "Buscar", tint = MeetColors.textSecondary, modifier = Modifier.size(18.dp))
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
                    val quickChecks = comp.professionalChecks.ifEmpty { comp.defaultProfessionalChecks() }
                    if (isSelected && quickChecks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            quickChecks.first(),
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ProfessionalInfoSection(title: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    items.take(5).forEachIndexed { index, item ->
        Text("${index + 1}. $item", color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
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

private fun sceneDisplayName(sceneType: SceneType, engineType: EngineType): String {
    return when (sceneType) {
        SceneType.ENGINE_BLOCK -> if (engineType == EngineType.ELECTRIC) "motor, inversor y batería" else "motor, admisión y escape"
        SceneType.RELAY_FUSE_BOX -> if (engineType == EngineType.ELECTRIC) "control y protecciones HV" else "fusibles, relés y protecciones"
        SceneType.WIRING_HARNESS -> if (engineType == EngineType.ELECTRIC) "arnés de alto voltaje" else "arnés, señales y alimentación"
    }
}

private fun mapMeshToComponentId(meshId: String): String? {
    return when {
        meshId.startsWith("spark_plug_") || meshId.startsWith("spark_metal_") || meshId.startsWith("spark_gap_") -> "spark_plugs"
        meshId.startsWith("piston_") || meshId.startsWith("rod_") -> "spark_plugs"
        meshId == "throttle_body" -> "throttle_body"
        meshId == "throttle_plate" -> "throttle_body"
        meshId == "maf_sensor" -> "maf_sensor"
        meshId == "map_sensor" -> "map_sensor"
        meshId == "iat_sensor" -> "iat_sensor"
        meshId == "ect_sensor" -> "coolant_temp"
        meshId == "ckp_sensor" -> "crankshaft_sensor"
        meshId == "camshaft_sensor" -> "camshaft_sensor"
        meshId == "alternator" -> "alternator"
        meshId == "oil_pan" -> "oil_pan"
        meshId == "oil_filter" -> "oil_pan"
        meshId == "water_pump" -> "water_pump"
        meshId == "thermostat_housing" -> "thermostat"
        meshId == "serpentine_belt" -> "alternator"
        meshId.startsWith("injector_") || meshId == "fuel_rail" || meshId == "fuel_pressure_sensor" -> "injectors"
        meshId.startsWith("ignition_coil_") -> "ignition_coils"
        meshId == "intake_manifold" || meshId.startsWith("intake_runner_") -> "throttle_body"
        meshId == "exhaust_manifold" || meshId.startsWith("exhaust_runner_") || meshId == "o2_upstream" -> "o2_upstream"
        meshId == "o2_downstream" -> "o2_downstream"
        meshId == "catalytic_converter" -> "catalytic_conv"
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
            commonFailures = listOf("Electrodo desgastado", "Aislador carbonizado o empapado por mezcla rica", "Separación de electrodos incorrecta"),
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
            commonFailures = listOf("Fuga por sello", "Impulsor corroído", "Rodamiento ruidoso")),
        ComponentInfo("alternator", "Alternador", ComponentCategory.ELECTRICAL, "Genera electricidad para recargar la batería y alimentar el sistema eléctrico. Accionado por banda serpentina.",
            commonFailures = listOf("Diodos rectificadores quemados", "Regulador de voltaje fallido"),
            relatedPids = listOf("0142"), relatedDtcs = listOf("P0562", "P0563")),
        ComponentInfo("crankshaft_sensor", "Sensor Cigüeñal (CKP)", ComponentCategory.SENSORS, "Mide la posición y velocidad de rotación del cigüeñal. Señal crítica para sincronización de inyección y chispa.",
            commonFailures = listOf("Separación incorrecta contra rueda reluctora", "Cable dañado por calor"),
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

private fun ComponentInfo.serviceLocation(): String {
    return when (id) {
        "spark_plugs" -> "Parte superior de la culata, debajo de bobinas o cables de encendido; una bujía por cilindro."
        "ignition_coils" -> "Encima de cada bujía en sistemas COP, o agrupadas en paquete de bobinas cerca de la tapa de válvulas."
        "injectors" -> "Insertados entre el riel de combustible y el múltiple de admisión; uno por cilindro en inyección multipunto."
        "throttle_body" -> "Entrada del múltiple de admisión, después del ducto del filtro de aire y del sensor MAF."
        "maf_sensor" -> "Ducto de admisión entre caja de filtro y cuerpo de aceleración, con flecha de flujo hacia el motor."
        "map_sensor" -> "Sobre el múltiple de admisión o conectado por una manguera corta de vacío."
        "iat_sensor" -> "En el ducto de admisión o integrado en el MAF, antes del cuerpo de aceleración."
        "o2_upstream" -> "En el múltiple o tubo de escape antes del catalizador; sensor de control de mezcla."
        "o2_downstream" -> "Después del catalizador; sensor de monitoreo de eficiencia."
        "catalytic_conv" -> "En la línea de escape principal entre el múltiple y el resonador o silenciador."
        "coolant_temp" -> "Rosca en culata, carcasa de termostato o salida de refrigerante hacia radiador."
        "thermostat" -> "Carcasa de salida de refrigerante, normalmente donde conecta la manguera superior o inferior del radiador."
        "water_pump" -> "Frente del motor, accionada por banda serpentina o correa de distribución; en algunos modelos es eléctrica."
        "alternator" -> "Frente/lateral del motor, alineado con la banda serpentina y cable positivo grueso hacia batería."
        "crankshaft_sensor" -> "Cerca del cigüeñal, polea, volante o campana de transmisión; apunta a una rueda reluctora."
        "camshaft_sensor" -> "En culata o tapa frontal, alineado con engrane o reluctor del árbol de levas."
        "egr_valve" -> "Entre múltiple de escape y admisión; puede estar al frente o atrás del motor."
        "evap_purge" -> "En vano motor, entre canister EVAP y múltiple de admisión."
        "fuel_pump" -> "Dentro del tanque de combustible o módulo externo en línea, alimentada por relé/fusible dedicado."
        "oil_pan" -> "Parte inferior del motor; incluye cárter, tapón de drenaje, empaque y acceso al filtro en algunos diseños."
        "traction_motor" -> "Conjunto motor-reductor eléctrico en eje delantero o trasero."
        "inverter" -> "Módulo de potencia junto al motor eléctrico, con cables naranja HV y refrigeración líquida."
        "hv_battery" -> "Paquete bajo el piso o detrás de asientos; protegido por carcasa sellada de alto voltaje."
        "bms" -> "Dentro o sobre el paquete HV, conectado a módulos de celdas por arneses de medición."
        "hv_contactors" -> "Dentro de la caja de batería HV; conectan positivo/negativo hacia el inversor."
        "safety_plug" -> "Conector naranja de servicio en el paquete HV o bajo cubierta de acceso."
        "hv_fuse" -> "Dentro del paquete HV o caja de distribución de alto voltaje."
        else -> "Ubicación dependiente del fabricante; use el visor para orientar el componente y confirme con el manual de servicio del vehículo."
    }
}

private fun ComponentInfo.defaultTools(): List<String> {
    return when (category) {
        ComponentCategory.HIGH_VOLTAGE -> listOf("Guantes clase 0 o superior", "Multímetro CAT III/IV", "Detector de ausencia de tensión", "Torquímetro aislado", "Manual OEM HV")
        ComponentCategory.ELECTRICAL, ComponentCategory.SENSORS -> listOf("Multímetro automotriz", "Osciloscopio de 2 canales", "Puntas back-probe", "Diagrama eléctrico", "Limpiador dieléctrico")
        ComponentCategory.FUEL -> listOf("Manómetro de combustible", "Noid light u osciloscopio", "Pinzas para líneas", "Charola de seguridad", "Extintor clase B")
        ComponentCategory.COOLING -> listOf("Probador de presión", "Termómetro infrarrojo", "Embudo de purga", "Refractómetro", "Torquímetro")
        ComponentCategory.EXHAUST -> listOf("Escáner OBD-II", "Termómetro infrarrojo", "Osciloscopio", "Dado para sensor O2", "Detector de fugas")
        else -> listOf("Escáner OBD-II", "Torquímetro", "Juego de dados", "Manual OEM", "Lámpara de inspección")
    }
}

private fun ComponentInfo.defaultProfessionalChecks(): List<String> {
    return when (id) {
        "spark_plugs" -> listOf("Compare color del aislador por cilindro: café claro normal; negro mezcla rica/aceite; blanco sobretemperatura.", "Mida separación de electrodos y compárela contra etiqueta del vano o manual OEM.", "Si hay misfire, intercambie bujía con otro cilindro y confirme si el DTC se mueve.", "Revise compresión/fuga si la bujía sale aceitosa o mojada de combustible.")
        "ignition_coils" -> listOf("Confirme alimentación B+ y masa con lámpara de carga, no solo con voltaje flotante.", "Verifique señal de mando de PCM con osciloscopio o probador COP.", "Intercambie bobina con cilindro vecino si el misfire es específico.", "Inspeccione bota, resorte y tracking de alto voltaje.")
        "injectors" -> listOf("Escuche pulso con estetoscopio y confirme mando con noid light u osciloscopio.", "Mida resistencia de bobina y compare entre cilindros.", "Haga prueba de caída de presión si sospecha goteo u obstrucción.", "Revise combustible contaminado antes de reemplazar inyectores en serie.")
        "throttle_body" -> listOf("Revise carbonilla en borde de mariposa sin forzar engranes plásticos.", "Compare TPS 1/TPS 2: deben moverse suave e inverso/relacionado según diseño.", "Ejecute reaprendizaje de ralentí después de limpieza o reemplazo.", "Verifique alimentación, masa y CAN/LIN si aplica.")
        "maf_sensor" -> listOf("Revise ducto roto o abrazaderas flojas después del MAF.", "Compare g/s en ralentí con cilindrada y carga; valores muy bajos sugieren fuga o sensor sucio.", "Inspeccione contaminación por filtro aceitado.", "No toque el elemento sensor; use limpiador específico MAF.")
        "map_sensor" -> listOf("Compare kPa KOEO contra presión barométrica local.", "Aplique vacío con bomba manual y confirme respuesta lineal.", "Revise manguera de vacío por grietas o aceite.", "Verifique referencia 5V, masa y señal.")
        "iat_sensor" -> listOf("Compare temperatura IAT KOEO contra temperatura ambiente.", "Caliente suavemente el sensor y confirme cambio progresivo.", "Revise si está integrado en MAF antes de comprar pieza separada.", "Lecturas fijas en -40 °C o 150 °C suelen indicar abierto/corto.")
        "o2_upstream" -> listOf("Con motor caliente debe oscilar rápido en lazo cerrado en sensores narrowband.", "Revise calentador: alimentación, masa y resistencia según OEM.", "Induzca mezcla rica/pobre controlada y confirme respuesta.", "Corrija fugas de escape antes de culpar al sensor.")
        "o2_downstream" -> listOf("La señal post-cat debe ser más estable que la pre-cat.", "Si copia la señal pre-cat, sospeche catalizador ineficiente o fuga.", "Revise calentador y cableado lejos del escape.", "Confirme temperatura de catalizador antes/después bajo carga.")
        "catalytic_conv" -> listOf("Corrija misfires, mezcla rica o consumo de aceite antes de reemplazar.", "Compare temperatura entrada/salida con motor caliente bajo carga.", "Revise contrapresión si hay pérdida de potencia.", "Use datos de O2 pre/post para confirmar eficiencia real.")
        "coolant_temp" -> listOf("Compare ECT KOEO contra temperatura ambiente.", "Caliente motor y confirme aumento progresivo sin saltos.", "Verifique 5V referencia, masa y señal.", "Revise nivel de refrigerante antes de diagnosticar sensor.")
        "thermostat" -> listOf("Monitoree ECT desde arranque frío: debe subir y estabilizar cerca de temperatura de apertura.", "Manguera de radiador no debe calentarse fuerte antes de apertura.", "P0128 suele requerir revisar termostato, nivel, ventilador y sensor ECT.", "Purgue aire después de cualquier intervención.")
        "water_pump" -> listOf("Busque fuga por orificio testigo y juego axial.", "Compare temperatura entrada/salida radiador para flujo.", "Inspeccione banda/polea o comando eléctrico según diseño.", "Ruido de rodamiento cambia con rpm.")
        "alternator" -> listOf("Mida voltaje de carga en batería con luces y ventilador encendidos.", "Revise caída de voltaje positivo y negativo bajo carga.", "Mida rizado AC para detectar diodos dañados.", "Confirme tensión de banda antes de reemplazar.")
        "crankshaft_sensor" -> listOf("Revise rpm durante arranque en datos vivos.", "Capture señal CKP con osciloscopio; busque dientes perdidos o ruido.", "Inspeccione separación contra reluctor y limaduras metálicas.", "Sin CKP confiable no hay chispa/inyección en muchos sistemas.")
        "camshaft_sensor" -> listOf("Compare sincronía CMP/CKP con patrón conocido si hay P0340/P0341.", "Revise aceite contaminado en conector.", "Confirme 5V/12V según diseño, masa y señal.", "No descarte cadena/correa fuera de tiempo.")
        "fuel_pump" -> listOf("Mida presión KOEO, en ralentí y bajo carga.", "Revise caída de voltaje en bomba y relé.", "Confirme flujo/volumen, no solo presión estática.", "Revise filtro/regulador si presión cae.")
        "oil_pan" -> listOf("Inspeccione fugas tras limpiar zona; diferencie cárter, retenes y filtro.", "Revise presión real con manómetro si hay DTC de aceite.", "Busque limadura en aceite si hay ruido mecánico.", "Use torque correcto en tapón y tornillos de cárter.")
        "traction_motor", "inverter", "hv_battery", "bms", "hv_contactors", "safety_plug", "hv_fuse" -> listOf("Aplique procedimiento de desenergización OEM antes de tocar conectores naranja.", "Confirme ausencia de tensión con equipo CAT adecuado.", "Revise DTC híbridos/EV y freeze frame antes de borrar.", "Inspeccione interlock HV, aislamiento a chasis y refrigeración líquida.")
        else -> when (category) {
            ComponentCategory.ELECTRICAL, ComponentCategory.SENSORS -> listOf("Verifique alimentación, masa y señal bajo carga.", "Haga back-probe sin deformar terminales.", "Compare dato vivo con condición física real.", "Revise arnés por calor, aceite, sulfato o rozamiento.")
            ComponentCategory.FUEL -> listOf("Confirme presión, volumen y comando eléctrico.", "Despresurice antes de abrir líneas.", "Revise fugas y olor a combustible.", "Compare trims de combustible para validar causa.")
            ComponentCategory.COOLING -> listOf("Revise nivel, presión y purga de aire.", "Compare temperatura OBD contra medición externa.", "Inspeccione ventiladores y tapa de radiador.", "No abra sistema caliente.")
            else -> listOf("Confirme el DTC, freeze frame y dato vivo antes de desmontar.", "Inspeccione conectores y arnés asociados.", "Valide la falla con prueba física independiente.", "Borre códigos solo después de reparación y ciclo de manejo.")
        }
    }
}

private fun ComponentInfo.defaultRepairWorkflow(): List<String> {
    return when (category) {
        ComponentCategory.HIGH_VOLTAGE -> listOf("Guardar DTC y freeze frame.", "Desenergizar HV según OEM y esperar el tiempo especificado.", "Confirmar cero voltios en puntos de prueba.", "Reparar componente o arnés con piezas HV correctas.", "Reensamblar, habilitar sistema y ejecutar prueba de aislamiento.")
        ComponentCategory.FUEL -> listOf("Confirmar síntoma y presión/volumen.", "Despresurizar sistema.", "Reparar fuga, conector, filtro, bomba o inyector según prueba.", "Verificar presión bajo carga.", "Confirmar fuel trims y ausencia de fugas.")
        ComponentCategory.COOLING -> listOf("Diagnosticar en frío con nivel correcto.", "Presurizar sistema y localizar fuga o restricción.", "Reemplazar componente con empaque nuevo.", "Llenar con mezcla correcta y purgar aire.", "Confirmar temperatura estable y ventiladores.")
        ComponentCategory.ELECTRICAL, ComponentCategory.SENSORS -> listOf("Guardar datos antes de desconectar.", "Probar alimentación, masa y señal.", "Reparar terminal/arnés si la señal falla bajo carga.", "Reemplazar componente solo después de confirmar circuito.", "Borrar DTC y validar con dato vivo.")
        ComponentCategory.EXHAUST -> listOf("Corregir misfires, mezcla rica y fugas de escape.", "Verificar sensores O2 y calentadores.", "Confirmar eficiencia o restricción.", "Reemplazar juntas/sensor/catalizador según prueba.", "Hacer ciclo de manejo hasta monitor listo.")
        else -> listOf("Confirmar código y síntoma.", "Inspeccionar acceso y conectores.", "Ejecutar prueba mecánica/eléctrica.", "Reparar con torque y sellos correctos.", "Validar en prueba de manejo.")
    }
}

private fun ComponentInfo.defaultServiceSpecs(): List<String> {
    return when (id) {
        "spark_plugs" -> listOf("Separación y torque dependen del motor; use etiqueta bajo cofre o manual OEM.", "No use anti-seize salvo que el fabricante lo indique.", "Roscar a mano primero para evitar dañar culata.")
        "ignition_coils" -> listOf("Alimentación típica: B+ con llave ON y pulso de control desde PCM.", "La resistencia primaria no siempre diagnostica bobinas modernas; prefiera osciloscopio o intercambio controlado.")
        "injectors" -> listOf("Resistencia debe ser pareja entre inyectores del mismo banco.", "Caída de presión desigual indica inyector obstruido o con fuga.", "Use sellos/O-rings nuevos lubricados con aceite limpio.")
        "throttle_body" -> listOf("No forzar mariposa electrónica con llave ON.", "Reaprendizaje de ralentí puede ser obligatorio después de limpieza.")
        "maf_sensor" -> listOf("En ralentí suele aumentar con cilindrada; validar contra motor específico.", "La señal debe subir suave al acelerar sin cortes.")
        "map_sensor" -> listOf("KOEO debe acercarse a presión barométrica; en ralentí baja por vacío del motor.", "Referencia típica de sensores: 5V.")
        "o2_upstream", "o2_downstream" -> listOf("Narrowband trabaja cerca de 0.1-0.9V; wideband usa corriente/AFR calculado.", "El calentador debe probarse con diagrama específico.")
        "alternator" -> listOf("Carga típica: 13.5-14.8V según temperatura y estrategia del vehículo.", "Rizado AC excesivo sugiere diodos dañados.")
        "coolant_temp", "iat_sensor" -> listOf("Termistor NTC: resistencia baja al calentar y alta al enfriar.", "Lectura KOEO debe coincidir aproximadamente con ambiente si el motor está frío.")
        "thermostat" -> listOf("Temperatura de apertura común: 82-95 °C, confirmar por aplicación.", "P0128 no debe diagnosticarse con nivel bajo o aire en sistema.")
        "fuel_pump" -> listOf("Presión objetivo depende del sistema; confirmar especificación OEM.", "Caída de voltaje ideal bajo carga debe ser baja en positivo y masa.")
        "traction_motor", "inverter", "hv_battery", "bms", "hv_contactors", "safety_plug", "hv_fuse" -> listOf("Voltajes HV pueden superar 300-800V DC.", "No medir aislamiento con multímetro común; use equipo adecuado.", "Torque de conectores HV y secuencia de habilitación son OEM.")
        else -> listOf("Rangos generales: confirme valores exactos por año, motor y fabricante.", "Torque, secuencia y selladores dependen del diseño.")
    }
}

private fun ComponentInfo.defaultSafetyNotes(): List<String> {
    return when (category) {
        ComponentCategory.HIGH_VOLTAGE -> listOf("Riesgo letal: no intervenir HV sin capacitación y EPP.", "Retire joyería y use herramientas aisladas.", "Respete tiempo de descarga de capacitores indicado por OEM.")
        ComponentCategory.FUEL -> listOf("No fumar ni generar chispas.", "Despresurice antes de abrir líneas.", "Tenga extintor adecuado al alcance.")
        ComponentCategory.COOLING -> listOf("No abrir tapa caliente.", "Use refrigerante correcto; mezclar tipos puede generar lodos.", "Limpie derrames por toxicidad.")
        ComponentCategory.EXHAUST -> listOf("Escape y catalizador pueden quemar incluso minutos después de apagar.", "Use soportes seguros si trabaja debajo del vehículo.")
        ComponentCategory.ELECTRICAL, ComponentCategory.SENSORS -> listOf("No perfore cables innecesariamente.", "Desconecte batería si manipula alimentación principal.", "Evite cortos con puntas de prueba.")
        else -> listOf("Asegure vehículo, motor apagado cuando aplique y use EPP.", "Siga torque y secuencia del fabricante.")
    }
}
