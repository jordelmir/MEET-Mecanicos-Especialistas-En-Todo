package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium369.meet.ai.DiagnosticAiContextBuilder
import com.elysium369.meet.ai.ProprietaryGroundedContextBuilder
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.diagnostics.DiagnosticSpatialSystem
import com.elysium369.meet.core.diagnostics.DtcSpatialResolver
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.ComponentLocatorViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.core.engine3d.EngineType
import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode
import com.elysium369.meet.core.catalog.CatalogPart
import com.elysium369.meet.core.catalog.CatalogSubassemblyPlanner
import com.elysium369.meet.core.catalog.ProprietaryCatalogEntity
import com.elysium369.meet.core.catalog.ProprietaryPartsCatalogRepository
import com.elysium369.meet.core.catalog.PROPRIETARY_VEHICLE_LABEL
import com.elysium369.meet.core.catalog.UniversalPartsCatalogRepository
import com.elysium369.meet.data.visualdiagnostics.VisualDiagnosticRepositoryImpl
import com.elysium369.meet.domain.visualdiagnostics.DiagnosticComponent
import com.elysium369.meet.visual3d.domain.GenericInlineFourAssetContract
import com.elysium369.meet.visual3d.domain.GenericVehicleSystemsAssetContract
import com.elysium369.meet.visual3d.domain.MeetPlatformCatalog
import com.elysium369.meet.visual3d.domain.MeetPlatformProfile
import com.elysium369.meet.visual3d.domain.PlatformVisualMaturity
import com.elysium369.meet.visual3d.ui.CompleteVehicleTwinView
import com.elysium369.meet.visual3d.ui.TwinFocusMode
import com.elysium369.meet.visual3d.ui.VehicleTwinViewportState
import com.elysium369.meet.domain.visualdiagnostics.ComponentCategory as VisualComponentCategory
import com.elysium369.meet.domain.visualdiagnostics.EngineType as VisualEngineType
import com.elysium369.meet.domain.visualdiagnostics.CombustionType
import com.elysium369.meet.domain.visualdiagnostics.CylinderLayout
import com.elysium369.meet.domain.visualdiagnostics.PowertrainElectrification
import com.elysium369.meet.domain.visualdiagnostics.VehicleDataProvenance
import com.elysium369.meet.domain.visualdiagnostics.PowertrainFieldProvenance
import com.elysium369.meet.domain.visualdiagnostics.VehiclePowertrainTopologyResolver
import com.elysium369.meet.domain.visualdiagnostics.valueOrNull

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
    SUSPENSION("Suspensión", MeetColors.neonGreen),
    TRANSMISSION("Transmisión", Color(0xFF10B981)),
    BRAKES("Frenos", Color(0xFFFB7185)),
    STRUCTURE("Estructura", Color(0xFF38BDF8)),
    BODY("Carrocería", Color(0xFF94A3B8)),
    INTERIOR("Interior", Color(0xFFF59E0B)),
    HVAC("HVAC", Color(0xFF0EA5E9)),
    ADAS("ADAS", Color(0xFF06B6D4)),
    MODULES("Módulos", Color(0xFFC084FC)),
    HIGH_VOLTAGE("Alto Voltaje", Color(0xFFFF9100))
}

@Composable
fun ComponentLocatorScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    locatorViewModel: ComponentLocatorViewModel = hiltViewModel(),
    initialPartId: String? = null,
    initialFindingId: String? = null,
) {
    val context = LocalContext.current
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val canonicalFindingProjection by locatorViewModel.spatialProjection.collectAsState()
    val canonicalFindingCode by locatorViewModel.spatialFindingCode.collectAsState()
    val diagnosticPathTargets by locatorViewModel.diagnosticPathTargets.collectAsState()
    LaunchedEffect(initialFindingId) {
        initialFindingId?.takeIf(String::isNotBlank)?.let(locatorViewModel::loadFindingProjection)
    }
    val effectiveDtcCode = canonicalFindingCode
    
    val detectedPowertrainTopology = remember(selectedVehicle) {
        VehiclePowertrainTopologyResolver.resolve(
            engineDescription = listOfNotNull(selectedVehicle?.engine, selectedVehicle?.engine_tech)
                .joinToString(" "),
            fuelDescription = selectedVehicle?.fuel_type,
            transmissionDescription = listOfNotNull(
                selectedVehicle?.transmission_type,
                selectedVehicle?.transmission_subtype,
            ).joinToString(" "),
            displacementCc = selectedVehicle?.displacement_cc,
            provenance = PowertrainFieldProvenance(
                engine = if (selectedVehicle?.engine.isNullOrBlank() && selectedVehicle?.engine_tech.isNullOrBlank()) VehicleDataProvenance.UNKNOWN else VehicleDataProvenance.INFERRED,
                fuel = if (selectedVehicle?.fuel_type.isNullOrBlank()) VehicleDataProvenance.UNKNOWN else VehicleDataProvenance.INFERRED,
                transmission = if (selectedVehicle?.transmission_type.isNullOrBlank() && selectedVehicle?.transmission_subtype.isNullOrBlank()) VehicleDataProvenance.UNKNOWN else VehicleDataProvenance.INFERRED,
                displacement = if ((selectedVehicle?.displacement_cc ?: 0) > 0) VehicleDataProvenance.INFERRED else VehicleDataProvenance.UNKNOWN,
            ),
        )
    }
    // EngineType remains a rendering profile only; topology above is the authority.
    val detectedEngineType = remember(detectedPowertrainTopology) {
        val topology = detectedPowertrainTopology
        val count = topology.cylinderCount.valueOrNull
        val layout = topology.cylinderLayout.valueOrNull
        when (topology.electrification.valueOrNull) {
            PowertrainElectrification.PHEV -> EngineType.PHEV
            PowertrainElectrification.HEV, PowertrainElectrification.MHEV -> EngineType.HYBRID
            PowertrainElectrification.BEV -> EngineType.ELECTRIC
            else -> when {
                topology.combustionType.valueOrNull == CombustionType.DIESEL && layout == CylinderLayout.V && count == 8 -> EngineType.DIESEL_V8
                topology.combustionType.valueOrNull == CombustionType.DIESEL && layout == CylinderLayout.V && count == 6 -> EngineType.DIESEL_V6
                topology.combustionType.valueOrNull == CombustionType.DIESEL && layout == CylinderLayout.INLINE && count == 4 -> EngineType.DIESEL_L4
                layout == CylinderLayout.V && count == 12 -> EngineType.V12
                layout == CylinderLayout.V && count == 10 -> EngineType.V10
                layout == CylinderLayout.V && count == 8 -> EngineType.V8
                layout == CylinderLayout.V && count == 6 -> EngineType.V6
                layout == CylinderLayout.BOXER && count == 6 -> EngineType.BOXER_6
                layout == CylinderLayout.BOXER && count == 4 -> EngineType.BOXER_4
                layout == CylinderLayout.ROTARY -> EngineType.ROTARY
                layout == CylinderLayout.INLINE && count == 6 -> EngineType.INLINE_6
                layout == CylinderLayout.INLINE && count == 5 -> EngineType.INLINE_5
                layout == CylinderLayout.INLINE && count == 4 -> EngineType.INLINE_4
                layout == CylinderLayout.INLINE && count == 3 -> EngineType.INLINE_3
                else -> EngineType.UNKNOWN
            }
        }
    }

    var selectedEngineType by remember(detectedEngineType) { mutableStateOf(detectedEngineType) }
    val visualRepository = locatorViewModel.visualRepository
    val aiContextBuilder = locatorViewModel.diagnosticAiContextBuilder
    val proprietaryAiContextBuilder = locatorViewModel.proprietaryGroundedContextBuilder
    val visualEngineType = remember(selectedEngineType) { selectedEngineType.toVisualEngineType() }
    
    // Base profesional de componentes filtrada por tipo de motor.
    val diagnosticComponents = remember(visualEngineType) { visualRepository.componentsForEngine(visualEngineType) }
    val engineComponents = remember(diagnosticComponents) { diagnosticComponents.map { it.toComponentInfo() } }
    val catalogPack = remember(context) {
        runCatching { UniversalPartsCatalogRepository(context).load() }.getOrNull()
    }
    val suspensionComponents = remember(catalogPack) {
        catalogPack?.parts.orEmpty().map { it.toComponentInfo() }
    }
    val proprietaryRepository = remember(context) { ProprietaryPartsCatalogRepository(context) }
    val proprietaryManifest = remember(proprietaryRepository) {
        runCatching { proprietaryRepository.loadManifest() }.getOrNull()
    }
    val proprietaryIndex = remember(proprietaryRepository) {
        runCatching { proprietaryRepository.loadEntityIndex() }.getOrNull()
    }
    val initialProprietaryEntity = remember(initialPartId, proprietaryIndex) {
        proprietaryIndex?.entities?.firstOrNull { it.id == initialPartId }
    }
    var currentCatalogSystemId by remember(initialProprietaryEntity, proprietaryManifest) {
        mutableStateOf(initialProprietaryEntity?.systemId ?: proprietaryManifest?.systems?.firstOrNull()?.id ?: "engine")
    }
    val allSystemProprietaryEntities = remember(proprietaryIndex, currentCatalogSystemId) {
        proprietaryIndex?.entities.orEmpty().filter {
            it.systemId == currentCatalogSystemId && it.recordRole == "COMPONENT"
        }
    }
    val catalogSubassemblies = remember(proprietaryManifest, currentCatalogSystemId) {
        CatalogSubassemblyPlanner.groups(proprietaryManifest, currentCatalogSystemId)
    }
    var currentCatalogSubassemblyId by remember(currentCatalogSystemId, initialProprietaryEntity) {
        mutableStateOf(
            catalogSubassemblies.firstOrNull { group ->
                initialProprietaryEntity?.sectionId in group.sectionIds
            }?.id
        )
    }
    val currentCatalogSubassembly = remember(catalogSubassemblies, currentCatalogSubassemblyId) {
        catalogSubassemblies.firstOrNull { it.id == currentCatalogSubassemblyId }
    }
    val proprietaryEntities = remember(allSystemProprietaryEntities, currentCatalogSubassembly) {
        CatalogSubassemblyPlanner.entitiesFor(allSystemProprietaryEntities, currentCatalogSubassembly)
    }
    val proprietaryEntitiesById = remember(proprietaryIndex) {
        proprietaryIndex?.entities.orEmpty().associateBy { it.id }
    }
    val proprietaryComponentsBySystem = remember(proprietaryIndex, proprietaryRepository) {
        proprietaryIndex?.entities.orEmpty()
            .asSequence()
            .filter { it.recordRole == "COMPONENT" }
            .groupBy(ProprietaryCatalogEntity::systemId)
            .mapValues { (_, entities) ->
                entities.map { it.toComponentInfo(proprietaryRepository, includeLiteralContext = false) }
            }
    }
    val proprietaryComponents = remember(proprietaryEntities, proprietaryRepository) {
        proprietaryEntities.map { it.toComponentInfo(proprietaryRepository, includeLiteralContext = false) }
    }
    val catalogSceneNodes = remember(proprietaryEntities) {
        proprietaryEntities.map { entity ->
            UniversalCatalogSceneNode(
                id = entity.id,
                name = entity.nameOriginal,
                systemId = entity.systemId,
                seed = entity.threeDimensionalBinding.seed,
                sectionId = entity.sectionId
            )
        }
    }
    val orderedProprietarySceneNodes = remember(proprietaryIndex) {
        proprietaryIndex?.entities.orEmpty()
            .asSequence()
            .filter { it.recordRole == "COMPONENT" }
            .sortedBy(ProprietaryCatalogEntity::sourceOrder)
            .map { entity ->
                UniversalCatalogSceneNode(
                    id = entity.id,
                    name = entity.nameOriginal,
                    systemId = entity.systemId,
                    seed = entity.threeDimensionalBinding.seed,
                    sectionId = entity.sectionId
                )
            }
            .toList()
    }
    val engineAssetCatalogSceneNodes = remember(orderedProprietarySceneNodes) {
        GenericInlineFourAssetContract.sourceBackedNodes(
            orderedProprietarySceneNodes.filter { it.systemId == "engine" }
        )
    }
    val currentSystemAssetCatalogSceneNodes = remember(
        currentCatalogSystemId,
        catalogSceneNodes
    ) {
        val currentSystemNodes = catalogSceneNodes.filter {
            it.systemId == currentCatalogSystemId
        }
        when (currentCatalogSystemId) {
            "engine" -> GenericInlineFourAssetContract.sourceBackedNodes(currentSystemNodes)
            else -> GenericVehicleSystemsAssetContract.assetForSystem(currentCatalogSystemId)
                ?.let { asset ->
                    GenericVehicleSystemsAssetContract.sourceBackedNodes(asset, currentSystemNodes)
                }
                ?: currentSystemNodes
        }
    }
    
    val initialDtcProjection = remember(
        canonicalFindingProjection,
    ) {
        canonicalFindingProjection ?: DtcSpatialResolver.resolve(null, null)
    }
    val initialDtcScene = remember(initialDtcProjection) {
        when (initialDtcProjection.primarySystem) {
            DiagnosticSpatialSystem.POWERTRAIN_ENGINE -> SceneType.ENGINE_BLOCK
            DiagnosticSpatialSystem.TRANSMISSION -> SceneType.TRANSMISSION
            DiagnosticSpatialSystem.CHASSIS,
            DiagnosticSpatialSystem.BRAKES_STEERING -> SceneType.BRAKES_STEERING
            DiagnosticSpatialSystem.BODY_ELECTRICAL,
            DiagnosticSpatialSystem.RESTRAINTS -> SceneType.RELAY_FUSE_BOX
            DiagnosticSpatialSystem.COMMUNICATION_NETWORK -> SceneType.WIRING_HARNESS
            DiagnosticSpatialSystem.UNIVERSAL -> SceneType.UNIVERSAL_CATALOG
        }
    }
    val dtcRelationCandidates = remember(
        engineComponents,
        suspensionComponents,
        proprietaryComponentsBySystem,
    ) {
        (engineComponents + suspensionComponents + proprietaryComponentsBySystem.values.flatten())
            .distinctBy(ComponentInfo::id)
    }
    val initialDtcComponents = remember(effectiveDtcCode, initialDtcProjection, dtcRelationCandidates) {
        val graphIds = initialDtcProjection.candidateComponents.map { it.componentId }.toSet()
        dtcRelationCandidates.filter { component ->
            component.id in graphIds
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedComponent by remember(initialPartId, suspensionComponents, initialProprietaryEntity) {
        mutableStateOf(
            initialProprietaryEntity?.toComponentInfo(proprietaryRepository, includeLiteralContext = true)
                ?: suspensionComponents.firstOrNull { it.id == initialPartId }
        )
    }
    var selectedCategory by remember { mutableStateOf<ComponentCategory?>(null) }
    var aiContextPreview by remember { mutableStateOf<String?>(null) }
    
    var currentScene by remember(initialPartId, effectiveDtcCode, initialProprietaryEntity) {
        mutableStateOf(
            when {
                initialProprietaryEntity != null -> SceneType.UNIVERSAL_CATALOG
                initialPartId != null -> SceneType.SUSPENSION
                effectiveDtcCode != null -> initialDtcScene
                else -> SceneType.UNIVERSAL_CATALOG
            }
        )
    }
    var explodedServiceView by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf(MeetPlatformCatalog.default) }
    var twinViewportState by remember(initialProprietaryEntity, initialPartId, effectiveDtcCode) {
        mutableStateOf(
            VehicleTwinViewportState(
                focusMode = when {
                    initialProprietaryEntity != null || initialPartId != null -> TwinFocusMode.COMPONENT
                    effectiveDtcCode != null -> TwinFocusMode.DIAGNOSTIC_TWIN
                    else -> TwinFocusMode.COMPLETE_VEHICLE
                },
                xRayEnabled = effectiveDtcCode != null,
                autoRotateEnabled = effectiveDtcCode == null,
            )
        )
    }
    var renderedTwinNodeCount by remember { mutableIntStateOf(0) }
    val components = remember(
        currentScene,
        engineComponents,
        suspensionComponents,
        proprietaryComponents,
        proprietaryComponentsBySystem
    ) {
        componentsForScene(
            currentScene,
            engineComponents,
            suspensionComponents,
            proprietaryComponents,
            proprietaryComponentsBySystem
        )
    }
    
    // Obtener códigos DTC activos del escáner en tiempo real
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val pendingDtcs by viewModel.pendingDtcs.collectAsState()
    val allActiveDtcs = remember(activeDtcs, pendingDtcs) {
        (activeDtcs + pendingDtcs).distinct()
    }
    val liveData by viewModel.liveData.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isObdConnected = connectionState == ObdState.CONNECTED
    val vehicleLabel = remember(selectedVehicle) {
        selectedVehicle?.let { vehicle ->
            listOf(vehicle.year.toString(), vehicle.make, vehicle.model, vehicle.engine)
                .filterNot { it.isBlank() }
                .joinToString(" ")
        }
    }

    val filteredComponents = remember(searchQuery, selectedCategory, components, effectiveDtcCode, initialDtcProjection) {
        val graphIds = initialDtcProjection.candidateComponents.map { it.componentId }.toSet()
        components.filter { c ->
            (searchQuery.isBlank() || c.name.contains(searchQuery, ignoreCase = true)) &&
            (selectedCategory == null || c.category == selectedCategory) &&
            (effectiveDtcCode == null || c.id in graphIds)
        }
    }
    val pageScrollState = rememberScrollState()
    var viewportGestureActive by remember { mutableStateOf(false) }

    // Mapeo inverso de Malla 3D a ComponentInfo al presionar la pantalla
    val onMeshSelected: (String, String) -> Unit = { meshId, meshName ->
        val mappedId = mapMeshToComponentId(meshId) ?: meshId
        val proprietaryEntity = proprietaryEntitiesById[mappedId]
        val comp = proprietaryEntity?.toComponentInfo(proprietaryRepository, includeLiteralContext = true)
            ?: components.find { it.id == mappedId }
        aiContextPreview = null
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

    // Grouped engine types for selector
    data class EngineGroup(val label: String, val types: List<Pair<EngineType, String>>)
    val engineGroups = listOf(
        EngineGroup("◌ Genérico", listOf(EngineType.UNKNOWN to "N/D")),
        EngineGroup("⛽ Gas", listOf(
            EngineType.INLINE_3 to "L3", EngineType.INLINE_4 to "L4", EngineType.INLINE_5 to "L5",
            EngineType.INLINE_6 to "L6", EngineType.V6 to "V6", EngineType.V8 to "V8",
            EngineType.V10 to "V10", EngineType.V12 to "V12"
        )),
        EngineGroup("🛢️ Diesel", listOf(
            EngineType.DIESEL_L4 to "D·L4", EngineType.DIESEL_V6 to "D·V6", EngineType.DIESEL_V8 to "D·V8"
        )),
        EngineGroup("⚡ Electrificado", listOf(
            EngineType.HYBRID to "HEV", EngineType.PHEV to "PHEV", EngineType.ELECTRIC to "EV"
        )),
        EngineGroup("🔧 Especial", listOf(
            EngineType.BOXER_4 to "H4", EngineType.BOXER_6 to "H6", EngineType.ROTARY to "RX"
        ))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
            .verticalScroll(pageScrollState, enabled = !viewportGestureActive)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.backOrHome() }) {
                AnimatedNeonIcon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Text(
                "Diagnóstico Visual 3D",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            // Component count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                    .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "${components.size} piezas",
                    color = MeetColors.cyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(6.dp))
            if (allActiveDtcs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.error.copy(alpha = 0.2f))
                        .border(1.dp, MeetColors.error, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedNeonIcon(Icons.Default.Warning, "Alerta", tint = MeetColors.error, modifier = Modifier.size(12.dp))
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

        if (diagnosticPathTargets.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "RUTA CAUSAL · MEDICIÓN GUIADA",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    diagnosticPathTargets.forEach { target ->
                        Column(
                            modifier = Modifier
                                .widthIn(min = 170.dp, max = 260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MeetColors.cardBackground.copy(alpha = 0.86f))
                                .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        ) {
                            Text(target.layer, color = MeetColors.electricBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text(target.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(target.path, color = MeetColors.textSecondary, fontSize = 9.sp, maxLines = 2)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "MEDIR AQUÍ",
                                color = MeetColors.neonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.clickable {
                                    navController.navigate("scanner") { launchSingleTop = true }
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── Engine Type Selector (Grouped horizontal scroll) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            engineGroups.forEachIndexed { groupIdx, group ->
                // Group separator (dot divider between groups)
                if (groupIdx > 0) {
                    Text("·", color = MeetColors.textMuted.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp))
                }
                group.types.forEach { (type, chipLabel) ->
                    val isSelected = selectedEngineType == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MeetColors.electricBlue.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (isSelected) MeetColors.electricBlue else MeetColors.borderSubtle.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .clickable {
                                selectedEngineType = type
                                selectedComponent = null
                                aiContextPreview = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chipLabel,
                            color = if (isSelected) MeetColors.electricBlue else MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Scene Selector Tabs ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SceneSelectorTab(
                label = "G4ED · 420 PIEZAS",
                isSelected = false,
                onClick = {
                    navController.navigate(
                        "parts_repairs?partId=g4ed-001-bloque-de-cilindros",
                    )
                },
            )
            SceneSelectorTab(
                label = "ATLAS · 5.985 SISTEMAS",
                isSelected = false,
                onClick = {
                    navController.navigate(
                        "parts_repairs?partId=transmission_hydraulics-0001-transaxle-automatica-completa",
                    )
                },
            )
            SceneSelectorTab(
                label = "MOTOR 3D UNIVERSAL",
                isSelected = currentScene == SceneType.UNIVERSAL_CATALOG,
                onClick = {
                    currentScene = SceneType.UNIVERSAL_CATALOG
                    selectedComponent = null
                    aiContextPreview = null
                    explodedServiceView = false
                    twinViewportState = twinViewportState.returnToVehicle()
                }
            )
            SceneSelectorTab(
                label = if (selectedEngineType == EngineType.ELECTRIC) "MOTOR/INVER." else "MOTOR 3D",
                isSelected = currentScene == SceneType.ENGINE_BLOCK,
                onClick = {
                    currentScene = SceneType.ENGINE_BLOCK
                    selectedComponent = null
                    aiContextPreview = null
                }
            )
            SceneSelectorTab(
                label = "SUSPENSIÓN",
                isSelected = currentScene == SceneType.SUSPENSION,
                onClick = {
                    currentScene = SceneType.SUSPENSION
                    selectedComponent = null
                    aiContextPreview = null
                }
            )
            SceneSelectorTab(
                label = "TRANSMISIÓN",
                isSelected = currentScene == SceneType.TRANSMISSION,
                onClick = {
                    currentScene = SceneType.TRANSMISSION
                    selectedComponent = null
                    aiContextPreview = null
                }
            )
            SceneSelectorTab(
                label = "FRENOS y DIR.",
                isSelected = currentScene == SceneType.BRAKES_STEERING,
                onClick = {
                    currentScene = SceneType.BRAKES_STEERING
                    selectedComponent = null
                    aiContextPreview = null
                }
            )
            SceneSelectorTab(
                label = if (selectedEngineType == EngineType.ELECTRIC) "SIST. CONTROL" else "FUSIBLES/RELÉS",
                isSelected = currentScene == SceneType.RELAY_FUSE_BOX,
                onClick = {
                    currentScene = SceneType.RELAY_FUSE_BOX
                    selectedComponent = null
                    aiContextPreview = null
                }
            )
            SceneSelectorTab(
                label = if (selectedEngineType == EngineType.ELECTRIC) "ALTO VOLTAJE" else "ARNÉS CABLES",
                isSelected = currentScene == SceneType.WIRING_HARNESS,
                onClick = {
                    currentScene = SceneType.WIRING_HARNESS
                    selectedComponent = null
                    aiContextPreview = null
                }
            )
        }

        if (currentScene == SceneType.UNIVERSAL_CATALOG) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                proprietaryManifest?.systems.orEmpty().forEach { system ->
                    val selected = currentCatalogSystemId == system.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) system.color.toComposeColor().copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.2f))
                            .border(1.dp, if (selected) system.color.toComposeColor() else MeetColors.borderSubtle.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable {
                                currentCatalogSystemId = system.id
                                selectedComponent = null
                                aiContextPreview = null
                                explodedServiceView = false
                                twinViewportState = twinViewportState.focusSystem()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${system.title} · ${system.entityCount}",
                            color = if (selected) system.color.toComposeColor() else MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (catalogSubassemblies.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CatalogSubassemblyChip(
                        label = "TODOS · ${allSystemProprietaryEntities.size}",
                        selected = currentCatalogSubassembly == null,
                        onClick = {
                            currentCatalogSubassemblyId = null
                            selectedComponent = null
                            aiContextPreview = null
                            twinViewportState = twinViewportState.focusSystem()
                        }
                    )
                    catalogSubassemblies.forEach { subassembly ->
                        CatalogSubassemblyChip(
                            label = "${subassembly.title} · ${subassembly.entityCount}",
                            selected = currentCatalogSubassemblyId == subassembly.id,
                            onClick = {
                                currentCatalogSubassemblyId = subassembly.id
                                selectedComponent = null
                                aiContextPreview = null
                                twinViewportState = twinViewportState.focusSystem()
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MeetPlatformCatalog.profiles.forEach { platform ->
                PlatformSelectorButton(
                    platform = platform,
                    selected = platform.id == selectedPlatform.id,
                    onClick = {
                        selectedPlatform = platform
                        selectedComponent = null
                        aiContextPreview = null
                        explodedServiceView = false
                        twinViewportState = twinViewportState.returnToVehicle()
                    }
                )
            }
        }

        val disallowPageIntercept = remember { RequestDisallowInterceptTouchEvent() }

        // ── 3D Viewport Box ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(392.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(16.dp))
                .pointerInput(disallowPageIntercept) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        viewportGestureActive = true
                        disallowPageIntercept(true)
                        try {
                            do {
                                val pointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                            } while (pointerEvent.changes.any { it.pressed })
                        } finally {
                            viewportGestureActive = false
                            disallowPageIntercept(false)
                        }
                    }
                }
        ) {
            val mappedSystemId = when (currentScene) {
                SceneType.ENGINE_BLOCK -> "engine"
                SceneType.SUSPENSION -> "suspension"
                SceneType.TRANSMISSION -> "transmission"
                SceneType.BRAKES_STEERING -> "steering"
                SceneType.RELAY_FUSE_BOX -> "control_modules"
                SceneType.WIRING_HARNESS -> "electrical"
                SceneType.UNIVERSAL_CATALOG -> currentCatalogSystemId
            }
            val activeCatalogSceneNodes = when (currentScene) {
                SceneType.UNIVERSAL_CATALOG -> currentSystemAssetCatalogSceneNodes
                SceneType.ENGINE_BLOCK -> engineAssetCatalogSceneNodes
                else -> GenericVehicleSystemsAssetContract.assetForSystem(mappedSystemId)
                    ?.let { asset ->
                        GenericVehicleSystemsAssetContract.sourceBackedNodes(
                            asset,
                            orderedProprietarySceneNodes
                        )
                    }
                    .orEmpty()
            }

            key(
                selectedPlatform.id,
                mappedSystemId,
                activeCatalogSceneNodes.size,
                activeCatalogSceneNodes.firstOrNull()?.sectionId,
                activeCatalogSceneNodes.lastOrNull()?.sectionId
            ) {
            CompleteVehicleTwinView(
                platformAssetPath = selectedPlatform.assetPath,
                selectedSystemId = mappedSystemId,
                selectedEntityId = selectedComponent?.id,
                viewportState = twinViewportState.copy(
                    explodedProgress = if (explodedServiceView) 1f else 0f
                ),
                onSystemSelected = { systemId ->
                    if (currentScene == SceneType.UNIVERSAL_CATALOG) {
                        currentCatalogSystemId = systemId
                    } else {
                        currentScene = when (systemId) {
                            "engine" -> SceneType.ENGINE_BLOCK
                            "suspension" -> SceneType.SUSPENSION
                            "transmission" -> SceneType.TRANSMISSION
                            "steering" -> SceneType.BRAKES_STEERING
                            "control_modules" -> SceneType.RELAY_FUSE_BOX
                            "electrical" -> SceneType.WIRING_HARNESS
                            else -> currentScene
                        }
                    }
                    selectedComponent = null
                    aiContextPreview = null
                    explodedServiceView = false
                    twinViewportState = twinViewportState.focusSystem()
                },
                onVehicleTapped = {
                    explodedServiceView = false
                    twinViewportState = twinViewportState.returnToVehicle()
                },
                onRenderedNodeCountChanged = { renderedTwinNodeCount = it },
                fallbackContent = {
                    Interactive3DDiagView(
                        sceneType = currentScene,
                        engineType = selectedEngineType,
                        activeDtcs = allActiveDtcs,
                        selectedComponentId = selectedComponent?.id,
                        onComponentSelected = onMeshSelected,
                        catalogNodes = activeCatalogSceneNodes,
                        explodedServiceView = explodedServiceView,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                modifier = Modifier.fillMaxSize(),
                diagnosticComponents = diagnosticComponents,
                catalogNodes = activeCatalogSceneNodes,
                activeDtcs = allActiveDtcs,
                onComponentSelected = onMeshSelected,
                isObdConnected = isObdConnected,
                onViewportGestureActiveChanged = { viewportGestureActive = it }
            )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .widthIn(max = 226.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (currentScene == SceneType.UNIVERSAL_CATALOG) {
                            "${selectedPlatform.displayName} · ${proprietaryManifest?.systems?.firstOrNull { it.id == currentCatalogSystemId }?.title.orEmpty()}"
                        } else {
                            "${selectedEngineType.label} - ${sceneDisplayName(currentScene, selectedEngineType)}"
                        },
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentScene == SceneType.UNIVERSAL_CATALOG) {
                        Text(
                            text = PROPRIETARY_VEHICLE_LABEL,
                            color = MeetColors.cyberCyan,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentCatalogSystemId == "engine") {
                                "Malla mecanica MEET L2 · no dimensional/OEM"
                            } else {
                                "Construccion procedural MEET · no dimensional/OEM"
                            },
                            color = MeetColors.warning,
                            fontSize = 7.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        selectedComponent?.let { selected ->
                            Text(
                                text = "SELECCION · ${selected.name}",
                                color = MeetColors.neonGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "L2 · conjunto mecanico MEET · ID literal",
                                color = MeetColors.textSecondary,
                                fontSize = 7.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (selectedEngineType == EngineType.UNKNOWN && currentScene != SceneType.UNIVERSAL_CATALOG) {
                        Text(
                            text = "CONFIGURACIÓN NO CONFIRMADA · NO ES GEOMETRÍA OEM",
                            color = MeetColors.warning,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2
                        )
                    }
                    if (effectiveDtcCode != null && currentScene != SceneType.UNIVERSAL_CATALOG) {
                        Text(
                            text = "HALLAZGO CANÓNICO · " +
                                "${initialDtcComponents.size} RELACIONES PARA ${effectiveDtcCode.uppercase()} · " +
                                "${initialDtcProjection.primarySystem.name.replace('_', ' ')} · NO CONFIRMAN PIEZA DAÑADA",
                            color = MeetColors.cyberCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (explodedServiceView) MeetColors.warning.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.52f))
                        .border(1.dp, if (explodedServiceView) MeetColors.warning else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable {
                            explodedServiceView = !explodedServiceView
                            twinViewportState = if (explodedServiceView) {
                                twinViewportState.enterRepair()
                            } else if (selectedComponent != null) {
                                twinViewportState.focusComponent()
                            } else {
                                twinViewportState.focusSystem()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (explodedServiceView) "ENSAMBLAR" else "DESPIECE 3D",
                        color = if (explodedServiceView) MeetColors.warning else MeetColors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            if (explodedServiceView && currentScene == SceneType.UNIVERSAL_CATALOG) {
                Text(
                    text = "SECUENCIA DIDACTICA 1→6 · DISTANCIAS NO DIMENSIONALES",
                    color = MeetColors.warning,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 20.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            if (true) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    TwinCommandButton(
                        label = "UBICAR",
                        active = twinViewportState.focusMode == TwinFocusMode.SYSTEM,
                        onClick = { twinViewportState = twinViewportState.focusSystem() }
                    )
                    TwinCommandButton(
                        label = "ENFOCAR",
                        active = twinViewportState.focusMode == TwinFocusMode.COMPONENT,
                        enabled = selectedComponent != null,
                        onClick = { twinViewportState = twinViewportState.focusComponent() }
                    )
                    TwinCommandButton(
                        label = "RAYOS X",
                        active = twinViewportState.xRayEnabled,
                        onClick = { twinViewportState = twinViewportState.toggleXRay() }
                    )
                    TwinCommandButton(
                        label = "AUTO",
                        active = twinViewportState.autoRotateEnabled,
                        onClick = { twinViewportState = twinViewportState.toggleAutoRotate() }
                    )
                    TwinCommandButton(
                        label = "VEHICULO",
                        active = twinViewportState.focusMode == TwinFocusMode.COMPLETE_VEHICLE,
                        onClick = {
                            selectedComponent = null
                            explodedServiceView = false
                            twinViewportState = twinViewportState.returnToVehicle()
                        }
                    )
                }
            }

            if (effectiveDtcCode != null && initialDtcProjection.candidateComponents.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 48.dp)
                        .widthIn(max = 330.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("DIAGNOSTIC TWIN · RUTAS", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    (initialDtcProjection.signalPaths.map { "SEÑAL · $it" } +
                        initialDtcProjection.electricalPaths.map { "ELÉCTRICA · $it" } +
                        initialDtcProjection.communicationPaths.map { "RED · $it" } +
                        initialDtcProjection.fluidPaths.map { "FLUIDO · $it" } +
                        initialDtcProjection.mechanicalPaths.map { "MECÁNICA · $it" })
                        .take(4)
                        .forEach { path ->
                            Text(path, color = Color.White, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    Text("Ruta esquemática; posición OEM pendiente de confirmar.", color = MeetColors.warning, fontSize = 8.sp)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    if (currentScene == SceneType.UNIVERSAL_CATALOG) {
                        "${proprietaryEntities.size} piezas visibles"
                    } else {
                        "${components.size} piezas"
                    },
                    MeetColors.cyberCyan
                )
                if (currentScene == SceneType.UNIVERSAL_CATALOG) {
                    StatusPill(
                        "${minOf(catalogSceneNodes.size, 72)} en Rayos X",
                        MeetColors.warning
                    )
                }
                StatusPill("${allActiveDtcs.size} DTC", if (allActiveDtcs.isNotEmpty()) MeetColors.error else MeetColors.neonGreen)
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
                val proprietaryEntity = proprietaryEntitiesById[comp.id]
                val diagnosticComponent = remember(comp.id, visualEngineType) {
                    visualRepository.findComponent(visualEngineType, comp.id)
                }
                val livePidValues = remember(diagnosticComponent, liveData) {
                    diagnosticComponent?.relatedPids.orEmpty().associate { pid ->
                        pid.pid to liveData.readPid(pid.pid, pid.label, pid.unit)
                    }
                }
                val livePidCount = livePidValues.values.count { it != "Sin lectura en vivo" }

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    EliteCard(
                        backgroundColor = MeetColors.cardBackground,
                        borderColor = if (activeDtcOnPiece != null) MeetColors.error else comp.category.color,
                        glowColor = if (activeDtcOnPiece != null) MeetColors.error else comp.category.color,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 560.dp)
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
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                StatusPill(
                                    text = if (isObdConnected) "OBD conectado" else "Sin conexion OBD",
                                    color = if (isObdConnected) MeetColors.neonGreen else MeetColors.textMuted
                                )
                                if (livePidValues.isNotEmpty()) {
                                    StatusPill("$livePidCount/${livePidValues.size} PIDs vivos", MeetColors.electricBlue)
                                }
                                if (diagnosticComponent != null) {
                                    StatusPill("Ficha OEM-ready", MeetColors.cyberCyan)
                                }
                                if (proprietaryEntity != null) {
                                    StatusPill("Fuente literal", MeetColors.neonGreen)
                                }
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
                                    AnimatedNeonIcon(Icons.Default.Warning, "Falla", tint = MeetColors.error, modifier = Modifier.size(16.dp))
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
                            if (diagnosticComponent != null) {
                                ProfessionalInfoSection(
                                    title = "Datos OBD en vivo",
                                    items = livePidValues.entries.map { "${it.key}: ${it.value}" }
                                        .ifEmpty { listOf(if (isObdConnected) "No hay PIDs asociados para esta pieza." else "Conecta el escáner para poblar valores reales de este componente.") },
                                    color = if (isObdConnected) MeetColors.electricBlue else MeetColors.textMuted
                                )
                            }
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

                            if (diagnosticComponent != null || proprietaryEntity != null) {
                                Spacer(Modifier.height(8.dp))
                                EliteTextButton(
                                    text = if (proprietaryEntity != null) "ANALIZAR CON FUENTE CITADA" else "ARMAR CONTEXTO IA DE ESTA PIEZA",
                                    onClick = {
                                        aiContextPreview = if (proprietaryEntity != null) {
                                            val literalBlocks = runCatching {
                                                proprietaryRepository.literalContext(proprietaryEntity, maxBlocks = Int.MAX_VALUE)
                                            }.getOrDefault(emptyList())
                                            proprietaryAiContextBuilder.buildReadableBrief(
                                                entity = proprietaryEntity,
                                                blocks = literalBlocks
                                            )
                                        } else {
                                            aiContextBuilder.build(
                                                vehicleLabel = vehicleLabel,
                                                engineType = visualEngineType,
                                                component = checkNotNull(diagnosticComponent),
                                                presentationOnlyDtcCodes = allActiveDtcs.toSet(),
                                                livePidValues = livePidValues
                                            )
                                        }
                                    },
                                    color = MeetColors.neonGreen,
                                    isEnabled = true
                                )

                                AnimatedVisibility(visible = aiContextPreview != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black.copy(alpha = 0.35f))
                                            .border(0.5.dp, MeetColors.neonGreen.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            if (proprietaryEntity != null) "Analisis literal citado" else "Contexto tecnico listo para IA",
                                            color = MeetColors.neonGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            aiContextPreview.orEmpty(),
                                            color = MeetColors.textSecondary,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
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
            AnimatedNeonIcon(Icons.Default.Search, "Buscar", tint = MeetColors.textSecondary, modifier = Modifier.size(18.dp))
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
                .fillMaxWidth()
                .height(560.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (effectiveDtcCode != null) {
                item {
                    Surface(
                        color = MeetColors.cyberCyan.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                "TRAZA CAUSAL · ${effectiveDtcCode.uppercase()}",
                                color = MeetColors.cyberCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                            )
                            Text(initialDtcProjection.explanation, color = Color.White, fontSize = 11.sp)
                            Text(initialDtcProjection.relationNotice, color = MeetColors.warning, fontSize = 10.sp)
                            if (initialDtcProjection.candidateComponents.isNotEmpty()) {
                                Text(
                                    "Relaciones estructuradas: ${initialDtcProjection.candidateComponents.size} · " +
                                        "evidencia ${"%.0f".format(initialDtcProjection.projectionEvidenceScore * 100)}%",
                                    color = MeetColors.textSecondary,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
            if (filteredComponents.isEmpty() && effectiveDtcCode != null) {
                item {
                    Surface(
                        color = MeetColors.warning.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "No existe una pieza con vínculo estructurado aplicable para $effectiveDtcCode. No se abrirá un atlas general sin relación; vuelve a la guía y confirma con VIN/OEM y pruebas físicas.",
                            color = MeetColors.warning,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
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
                            val proprietaryEntity = proprietaryEntitiesById[comp.id]
                            selectedComponent = if (isSelected) null else {
                                proprietaryEntity?.toComponentInfo(proprietaryRepository, includeLiteralContext = true) ?: comp
                            }
                            if (proprietaryEntity != null) {
                                currentCatalogSystemId = proprietaryEntity.systemId
                                currentCatalogSubassemblyId = null
                                twinViewportState = if (isSelected) {
                                    twinViewportState.focusSystem()
                                } else {
                                    twinViewportState.focusComponent()
                                }
                            }
                            aiContextPreview = null
                            // Conmutar escena automáticamente
                            currentScene = when {
                                proprietaryEntity != null -> SceneType.UNIVERSAL_CATALOG
                                comp.category == ComponentCategory.SUSPENSION -> SceneType.SUSPENSION
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

internal fun componentsForScene(
    scene: SceneType,
    engineComponents: List<ComponentInfo>,
    suspensionComponents: List<ComponentInfo>,
    proprietaryComponents: List<ComponentInfo>,
    proprietaryComponentsBySystem: Map<String, List<ComponentInfo>>
): List<ComponentInfo> = when (scene) {
    SceneType.UNIVERSAL_CATALOG -> proprietaryComponents
    SceneType.SUSPENSION -> mergeSceneComponents(
        proprietaryComponentsBySystem.componentsFor("suspension"),
        suspensionComponents.filter { it.category == ComponentCategory.SUSPENSION }
    )
    SceneType.TRANSMISSION -> mergeSceneComponents(
        proprietaryComponentsBySystem.componentsFor("transmission"),
        engineComponents.filter {
            it.category == ComponentCategory.ENGINE &&
                listOf("clutch", "trans", "diff", "gear", "axle").any(it.id::contains)
        }
    )
    SceneType.BRAKES_STEERING -> mergeSceneComponents(
        proprietaryComponentsBySystem.componentsFor("brakes", "steering", "wheels"),
        engineComponents.filter {
            it.category == ComponentCategory.ENGINE &&
                listOf("brake", "steering", "rack", "caliper", "pump").any(it.id::contains)
        }
    )
    SceneType.ENGINE_BLOCK -> mergeSceneComponents(
        proprietaryComponentsBySystem.componentsFor("engine", "intake", "forced_induction"),
        engineComponents.filter {
            it.category == ComponentCategory.ENGINE ||
                it.category == ComponentCategory.FUEL ||
                it.category == ComponentCategory.COOLING ||
                it.category == ComponentCategory.INTAKE ||
                it.category == ComponentCategory.EXHAUST
        }
    )
    SceneType.RELAY_FUSE_BOX,
    SceneType.WIRING_HARNESS -> mergeSceneComponents(
        proprietaryComponentsBySystem.componentsFor(
            if (scene == SceneType.RELAY_FUSE_BOX) "control_modules" else "electrical"
        ),
        engineComponents.filter {
            it.category == ComponentCategory.ELECTRICAL ||
                it.category == ComponentCategory.HIGH_VOLTAGE
        }
    )
}

private fun Map<String, List<ComponentInfo>>.componentsFor(vararg systemIds: String): List<ComponentInfo> =
    systemIds.flatMap { get(it).orEmpty() }

private fun mergeSceneComponents(
    proprietary: List<ComponentInfo>,
    existing: List<ComponentInfo>
): List<ComponentInfo> = (proprietary + existing).distinctBy(ComponentInfo::id)

@Composable
private fun PlatformSelectorButton(
    platform: MeetPlatformProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MeetColors.neonGreen else MeetColors.cyberCyan
    Column(
        modifier = Modifier
            .width(132.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) color.copy(alpha = 0.16f) else MeetColors.cardBackground)
            .border(1.dp, color.copy(alpha = if (selected) 0.9f else 0.32f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            platform.displayName,
            color = if (selected) color else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (platform.visualMaturity == PlatformVisualMaturity.REALISTIC_REFERENCE) {
                "Referencia realista"
            } else {
                "Concepto 3D · malla final pendiente"
            },
            color = if (platform.visualMaturity == PlatformVisualMaturity.REALISTIC_REFERENCE) {
                MeetColors.neonGreen
            } else {
                MeetColors.warning
            },
            fontSize = 7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TwinCommandButton(
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val color = when {
        !enabled -> MeetColors.textMuted
        active -> MeetColors.neonGreen
        else -> MeetColors.cyberCyan
    }
    Box(
        modifier = Modifier
            .width(70.dp)
            .height(27.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = if (active) 0.78f else 0.6f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.72f else 0.3f), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
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
            .border(1.dp, if (isSelected) MeetColors.neonGreen else MeetColors.borderSubtle.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) MeetColors.neonGreen else Color.White.copy(alpha = 0.7f),
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun CatalogSubassemblyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MeetColors.warning.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.2f))
            .border(
                1.dp,
                if (selected) MeetColors.warning else MeetColors.borderSubtle.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp)
    ) {
        Text(
            text = label,
            color = if (selected) MeetColors.warning else MeetColors.textSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun sceneDisplayName(sceneType: SceneType, engineType: EngineType): String {
    return when (sceneType) {
        SceneType.ENGINE_BLOCK -> if (engineType == EngineType.ELECTRIC) "motor, inversor y batería" else "motor, admisión y escape"
        SceneType.RELAY_FUSE_BOX -> if (engineType == EngineType.ELECTRIC) "control y protecciones HV" else "fusibles, relés y protecciones"
        SceneType.WIRING_HARNESS -> if (engineType == EngineType.ELECTRIC) "arnés de alto voltaje" else "arnés, señales y alimentación"
        SceneType.SUSPENSION -> "construccion MEET, no dimensional ni OEM"
        SceneType.TRANSMISSION -> "transmisión, embrague y diferencial"
        SceneType.BRAKES_STEERING -> "sistema de frenado, dirección hidráulica/asistida"
        SceneType.UNIVERSAL_CATALOG -> "catálogo propietario completo · esquema procedural"
    }
}

private fun ProprietaryCatalogEntity.toComponentInfo(
    repository: ProprietaryPartsCatalogRepository,
    includeLiteralContext: Boolean
): ComponentInfo {
    val literalBlocks = if (includeLiteralContext) {
        runCatching { repository.literalContext(this, maxBlocks = 360) }.getOrDefault(emptyList())
    } else {
        emptyList()
    }
    val literalText = literalBlocks.joinToString("\n\n") { block -> block.text }
    return ComponentInfo(
        id = id,
        name = nameOriginal,
        category = systemId.toProprietaryCategory(),
        description = literalText.ifBlank { nameOriginal },
        location = "$vehicleScope · $sourceFileName · orden $sourceOrder",
        professionalChecks = literalBlocks.drop(1).map { it.text },
        serviceSpecs = listOf(
            "Fuente propietaria: $sourceFileName",
            "SHA-256 documento: $sourceDocumentSha256",
            "SHA-256 bloque: $sourceTextHash"
        ),
        safetyNotes = listOf("Esquema 3D procedural; no es una malla OEM ni una afirmación dimensional.")
    )
}

private fun String.toProprietaryCategory(): ComponentCategory = when (this) {
    "structure" -> ComponentCategory.STRUCTURE
    "engine", "forced_induction" -> ComponentCategory.ENGINE
    "intake" -> ComponentCategory.INTAKE
    "transmission" -> ComponentCategory.TRANSMISSION
    "suspension" -> ComponentCategory.SUSPENSION
    "steering" -> ComponentCategory.SUSPENSION
    "brakes", "wheels" -> ComponentCategory.BRAKES
    "electrical", "lighting", "actuators" -> ComponentCategory.ELECTRICAL
    "control_modules" -> ComponentCategory.MODULES
    "sensors" -> ComponentCategory.SENSORS
    "hvac" -> ComponentCategory.HVAC
    "passive_safety", "adas" -> ComponentCategory.ADAS
    "body", "wipers", "access" -> ComponentCategory.BODY
    "interior", "infotainment" -> ComponentCategory.INTERIOR
    "hybrid_ev" -> ComponentCategory.HIGH_VOLTAGE
    else -> ComponentCategory.STRUCTURE
}

private fun String.toComposeColor(): Color = runCatching {
    val rgb = removePrefix("#").toLong(16)
    Color(0xFF000000L or rgb)
}.getOrDefault(MeetColors.cyberCyan)

private fun CatalogPart.toComponentInfo(): ComponentInfo {
    return ComponentInfo(
        id = id,
        name = nameEs,
        category = ComponentCategory.SUSPENSION,
        description = description,
        location = "$system / $subsystem / $assembly. Posición: $position.",
        requiredTools = emptyList(),
        professionalChecks = listOf("Confirme VIN, OEM, foto, conector o medidas antes de decidir compatibilidad."),
        repairWorkflow = listOf("Use el catálogo de Piezas y Reparaciones para abrir un procedimiento revisable."),
        serviceSpecs = listOf("Datos técnicos: no confirmados para esta variante."),
        safetyNotes = listOf("Modelo visual MEET; no es una geometria OEM ni dimensional.")
    )
}

private fun mapMeshToComponentId(meshId: String): String? {
    val normalizedMeshId = if (meshId.startsWith("socket_")) meshId.removePrefix("socket_") else meshId
    val powerDistributionIds = listOf(
        "fuse_ecm_batt",
        "fuse_ecm_ign",
        "fuse_injectors",
        "fuse_ignition_coils",
        "fuse_o2_heater",
        "fuse_maf_map",
        "fuse_fuel_pump",
        "fuse_cooling_fan_low",
        "fuse_cooling_fan_high",
        "fuse_starter",
        "fuse_abs",
        "fuse_eps",
        "fuse_ac_clutch",
        "fuse_obd_dlc",
        "fuse_headlamp",
        "fuse_blower",
        "fuse_battery_main",
        "relay_fuel_pump",
        "relay_starter",
        "relay_ignition",
        "relay_fan",
        "relay_ac_clutch",
        "relay_headlamp",
        "relay_horn",
        "relay_aux"
    )
    powerDistributionIds.firstOrNull { normalizedMeshId == it || normalizedMeshId.startsWith("${it}_") }?.let { return it }

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
        meshId == "fuse_10" -> "fuel_pump"
        meshId == "fuse_7" -> "thermostat"
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
    ) + powerDistributionComponentDatabase()
}

private data class FuseInfoSpec(
    val id: String,
    val slot: String,
    val type: String,
    val amps: Int,
    val colorName: String,
    val function: String,
    val feed: String,
    val relatedDtcs: List<String> = emptyList(),
    val circuit: String = ""
)

private data class RelayInfoSpec(
    val id: String,
    val label: String,
    val function: String,
    val feed: String,
    val relatedDtcs: List<String> = emptyList()
)

private fun powerDistributionComponentDatabase(): List<ComponentInfo> {
    val fuses = listOf(
        FuseInfoSpec("fuse_ecm_batt", "F1", "Micro2", 10, "Rojo", "ECM memoria permanente", "B+ permanente", listOf("P0603", "P0685"), "PCM BATT"),
        FuseInfoSpec("fuse_ecm_ign", "F2", "Micro2", 15, "Azul", "ECM ignición/ACC", "Llave ON/START", listOf("P0685"), "PCM IGN"),
        FuseInfoSpec("fuse_injectors", "F3", "Mini", 15, "Azul", "Inyectores", "Relé principal o ASD", listOf("P0201", "P0202", "P0203", "P0204"), "INY"),
        FuseInfoSpec("fuse_ignition_coils", "F4", "Mini", 20, "Amarillo", "Bobinas de encendido", "Relé principal/ignición", listOf("P0351", "P0352", "P0300"), "IGN COIL"),
        FuseInfoSpec("fuse_o2_heater", "F5", "Micro2", 15, "Azul", "Calentadores sensores O2", "Llave ON con control PCM", listOf("P0135", "P0141"), "HTR O2"),
        FuseInfoSpec("fuse_maf_map", "F6", "Micro2", 10, "Rojo", "Sensores MAF/MAP/IAT", "Referencia/ignición sensores", listOf("P0100", "P0105", "P0110"), "SNSR"),
        FuseInfoSpec("fuse_fuel_pump", "F7", "Mini", 20, "Amarillo", "Bomba de combustible", "B+ vía relé bomba", listOf("P0230", "P0087"), "F/PMP"),
        FuseInfoSpec("fuse_cooling_fan_low", "F8", "JCASE", 30, "Verde", "Ventilador radiador baja", "B+ relé ventilador", listOf("P0480"), "FAN LOW"),
        FuseInfoSpec("fuse_cooling_fan_high", "F9", "JCASE", 40, "Naranja", "Ventilador radiador alta", "B+ relé ventilador", listOf("P0481"), "FAN HI"),
        FuseInfoSpec("fuse_starter", "F10", "Maxi", 40, "Naranja", "Solenoide de arranque", "START desde relé", listOf("P0512"), "START"),
        FuseInfoSpec("fuse_abs", "F11", "JCASE", 30, "Verde", "Módulo ABS", "B+ permanente ABS", listOf("C0035", "C0040"), "ABS"),
        FuseInfoSpec("fuse_eps", "F12", "PAL", 60, "Azul", "Dirección asistida EPS", "B+ alto consumo", listOf("C1604"), "EPS"),
        FuseInfoSpec("fuse_ac_clutch", "F13", "Mini", 10, "Rojo", "Embrague compresor A/C", "ACC con solicitud A/C", listOf("P0645"), "A/C CLT"),
        FuseInfoSpec("fuse_obd_dlc", "F14", "Micro2", 10, "Rojo", "Puerto OBD-II DLC", "B+ permanente pin 16", listOf("U0100"), "DLC"),
        FuseInfoSpec("fuse_headlamp", "F15", "Mini", 15, "Azul", "Faros principales", "B+ iluminación", circuit = "HEAD"),
        FuseInfoSpec("fuse_blower", "F16", "JCASE", 40, "Naranja", "Soplador HVAC", "B+ motor soplador", circuit = "BLWR"),
        FuseInfoSpec("fuse_battery_main", "F17", "PAL", 80, "Amarillo", "Alimentación principal B+", "B+ batería directo", listOf("P0562", "P0563"), "MAIN")
    ).map { fuse ->
        ComponentInfo(
            id = fuse.id,
            name = "${fuse.slot} ${fuse.type} ${fuse.amps}A - ${fuse.function}",
            category = ComponentCategory.ELECTRICAL,
            description = "Protección de circuito ${fuse.circuit.ifBlank { fuse.function }}. Tipo ${fuse.type}, ${fuse.amps}A, color ${fuse.colorName}. Alimentación esperada: ${fuse.feed}.",
            commonFailures = listOf("Elemento interno abierto", "Terminal floja o sulfatada", "Fusible reemplazado por amperaje incorrecto", "Corto intermitente aguas abajo"),
            relatedDtcs = fuse.relatedDtcs,
            location = "Caja de fusibles del vano motor, ranura ${fuse.slot}. Confirmar tapa/diagrama físico y manual OEM por VIN.",
            requiredTools = listOf("Multímetro", "Lámpara de prueba de baja corriente", "Pinza extractora de fusibles", "Diagrama eléctrico OEM", "Puntas back-probe"),
            professionalChecks = listOf(
                "Medir voltaje en ambos puntos de prueba del fusible: debe existir el mismo voltaje en entrada y salida cuando el circuito está alimentado.",
                "Si hay voltaje en un lado y cero en el otro, el elemento está abierto; reemplace por ${fuse.amps}A, no suba amperaje.",
                "Si vuelve a quemarse, desconecte cargas aguas abajo y busque corto a masa antes de instalar otro fusible.",
                "Prueba de continuidad solo con fusible fuera del circuito; valor esperado cercano a 0 ohmios.",
                "Pin ECM/PCM exacto depende del diagrama OEM; use ${fuse.circuit.ifBlank { fuse.slot }} como circuito guía."
            ),
            repairWorkflow = listOf(
                "Guardar DTC/freeze frame antes de borrar.",
                "Confirmar alimentación ${fuse.feed} en la ranura ${fuse.slot}.",
                "Extraer fusible, inspeccionar elemento y terminales de caja.",
                "Si el fusible está abierto, aislar carga y arnés antes de reemplazar.",
                "Instalar fusible ${fuse.type} ${fuse.amps}A y validar funcionamiento del sistema."
            ),
            serviceSpecs = listOf(
                "Tipo: ${fuse.type}",
                "Amperaje: ${fuse.amps}A",
                "Color: ${fuse.colorName}",
                "Continuidad esperada fuera de circuito: aproximadamente 0 ohmios",
                "Voltaje esperado: ${fuse.feed}"
            ),
            safetyNotes = listOf("No puentear con cable ni papel metálico.", "No aumentar amperaje para evitar que se queme.", "Si el fusible protege bomba/inyectores, despresurice combustible antes de intervenir cargas.")
        )
    }

    val relays = listOf(
        RelayInfoSpec("relay_fuel_pump", "Relé Bomba Gasolina", "Alimenta bomba de combustible durante cebado, arranque y motor en marcha.", "B+ permanente + comando PCM", listOf("P0230", "P0087")),
        RelayInfoSpec("relay_starter", "Relé Motor Arranque", "Entrega señal al solenoide de arranque cuando se cumplen condiciones de seguridad.", "START/PNP/inmovilizador", listOf("P0512")),
        RelayInfoSpec("relay_ignition", "Relé Principal ECM", "Alimenta ECM, bobinas, inyectores y sensores clave según arquitectura.", "Llave ON con control ECM", listOf("P0685", "P0603")),
        RelayInfoSpec("relay_fan", "Relé Ventilador", "Controla velocidad baja/alta de ventiladores de radiador.", "B+ batería + comando ECM", listOf("P0480", "P0481")),
        RelayInfoSpec("relay_ac_clutch", "Relé Compresor A/C", "Activa embrague o solicitud de compresor cuando presiones y temperatura son válidas.", "ACC + solicitud HVAC/PCM", listOf("P0645")),
        RelayInfoSpec("relay_headlamp", "Relé Faros", "Alimenta faros principales sin cargar directamente el interruptor.", "B+ iluminación"),
        RelayInfoSpec("relay_horn", "Relé Bocina", "Alimenta bocina desde mando de volante/BCM.", "B+ permanente + mando BCM"),
        RelayInfoSpec("relay_aux", "Relé Auxiliar", "Reserva para cargas auxiliares según equipamiento.", "Según diagrama OEM")
    ).map { relay ->
        ComponentInfo(
            id = relay.id,
            name = relay.label,
            category = ComponentCategory.ELECTRICAL,
            description = "${relay.function} Alimentación esperada: ${relay.feed}.",
            commonFailures = listOf("Contactos internos carbonizados", "Bobina abierta", "Terminal floja en zócalo", "Comando PCM/BCM ausente"),
            relatedDtcs = relay.relatedDtcs,
            location = "Caja de fusibles/relés del vano motor. Confirmar posición exacta en tapa y diagrama OEM.",
            requiredTools = listOf("Multímetro", "Pinza amperimétrica", "Lámpara de prueba", "Cable puente con fusible", "Diagrama eléctrico OEM"),
            professionalChecks = listOf(
                "Identificar terminales 30, 87, 85 y 86 según diagrama; no asumir distribución sin verificar.",
                "Terminal 30 debe tener alimentación cuando aplique; terminal 87 debe alimentar la carga al activar.",
                "Bobina 85/86 debe recibir comando y masa/control PCM; medir caída de voltaje bajo carga.",
                "Intercambiar solo con relé idéntico de mismo número y capacidad para prueba rápida.",
                "Si el relé activa pero la carga no funciona, medir consumo y continuidad hacia la carga."
            ),
            repairWorkflow = listOf(
                "Guardar DTC y condición de falla.",
                "Probar alimentación/comando en zócalo con el relé retirado.",
                "Activar relé con escáner o condición de mando y medir salida.",
                "Reparar zócalo, arnés o carga si el relé nuevo también falla.",
                "Validar sistema y revisar calentamiento anormal del relé."
            ),
            serviceSpecs = listOf(
                "Terminal 30: alimentación principal según circuito.",
                "Terminal 87: salida a carga.",
                "Terminal 85/86: bobina de mando.",
                "Resistencia de bobina típica varía por relé; confirmar OEM.",
                "Caída de voltaje en contactos debe ser baja bajo carga."
            ),
            safetyNotes = listOf("Use puente con fusible, nunca cable directo sin protección.", "No fuerce relés de diferente patillaje.", "Cargas como bomba o ventilador pueden arrancar inesperadamente.")
        )
    }

    return fuses + relays
}

private fun ComponentInfo.serviceLocation(): String {
    if (id.startsWith("fuse_")) return "Caja de fusibles del vano motor. Use la ranura indicada y confirme contra la tapa física o diagrama OEM por VIN."
    if (id.startsWith("relay_")) return "Caja de relés del vano motor. Confirmar posición por tapa/diagrama; algunos vehículos repiten relés de misma apariencia con patillaje distinto."
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

private fun EngineType.toVisualEngineType(): VisualEngineType {
    return when (this) {
        EngineType.UNKNOWN -> VisualEngineType.UNKNOWN
        EngineType.INLINE_3 -> VisualEngineType.L3
        EngineType.INLINE_4 -> VisualEngineType.L4
        EngineType.INLINE_5 -> VisualEngineType.L5
        EngineType.INLINE_6 -> VisualEngineType.L6
        EngineType.V6 -> VisualEngineType.V6
        EngineType.V8 -> VisualEngineType.V8
        EngineType.V10 -> VisualEngineType.V10
        EngineType.V12 -> VisualEngineType.V12
        EngineType.BOXER_4 -> VisualEngineType.H4
        EngineType.BOXER_6 -> VisualEngineType.H6
        EngineType.ROTARY -> VisualEngineType.ROTARY
        EngineType.DIESEL_L4 -> VisualEngineType.DIESEL_L4
        EngineType.DIESEL_V6 -> VisualEngineType.DIESEL_V6
        EngineType.DIESEL_V8 -> VisualEngineType.DIESEL_V8
        EngineType.HYBRID -> VisualEngineType.HYBRID
        EngineType.PHEV -> VisualEngineType.PHEV
        EngineType.ELECTRIC -> VisualEngineType.EV
    }
}

private fun DiagnosticComponent.toComponentInfo(): ComponentInfo {
    return ComponentInfo(
        id = id,
        name = name,
        category = category.toUiCategory(),
        description = description,
        commonFailures = commonFailures,
        relatedPids = relatedPids.map { it.pid },
        relatedDtcs = relatedDtcs.map { it.code },
        location = location,
        requiredTools = requiredTools,
        professionalChecks = workshopTests.map { test ->
            "${test.title}: ${test.procedure} Resultado esperado: ${test.expectedResult}. Herramienta: ${test.tool}."
        },
        repairWorkflow = repairFlow.sortedBy { it.order }.map { step ->
            "${step.order}. ${step.action} Confirmación: ${step.confirmation}."
        },
        serviceSpecs = specs.map { spec ->
            buildString {
                append("${spec.label}: ${spec.expectedValue}")
                if (spec.notes.isNotBlank()) append(". ${spec.notes}")
            }
        },
        safetyNotes = safetyWarnings.map { warning ->
            "${warning.severity}: ${warning.message}"
        }
    )
}

private fun VisualComponentCategory.toUiCategory(): ComponentCategory {
    return when (this) {
        VisualComponentCategory.IGNITION -> ComponentCategory.ENGINE
        VisualComponentCategory.AIR_INTAKE -> ComponentCategory.INTAKE
        VisualComponentCategory.FUEL -> ComponentCategory.FUEL
        VisualComponentCategory.EXHAUST -> ComponentCategory.EXHAUST
        VisualComponentCategory.ELECTRICAL -> ComponentCategory.ELECTRICAL
        VisualComponentCategory.COOLING -> ComponentCategory.COOLING
        VisualComponentCategory.LUBRICATION -> ComponentCategory.ENGINE
        VisualComponentCategory.SENSOR -> ComponentCategory.SENSORS
        VisualComponentCategory.RELAY_FUSE -> ComponentCategory.ELECTRICAL
        VisualComponentCategory.HARNESS -> ComponentCategory.ELECTRICAL
        VisualComponentCategory.EV_HIGH_VOLTAGE -> ComponentCategory.HIGH_VOLTAGE
        VisualComponentCategory.TRANSMISSION -> ComponentCategory.ENGINE
        VisualComponentCategory.SUSPENSION -> ComponentCategory.ENGINE
        VisualComponentCategory.BRAKES -> ComponentCategory.ENGINE
        VisualComponentCategory.STEERING -> ComponentCategory.ENGINE
        VisualComponentCategory.TURBO_SUPERCHARGER -> ComponentCategory.INTAKE
        VisualComponentCategory.BODY_CONTROL -> ComponentCategory.ELECTRICAL
        VisualComponentCategory.INFOTAINMENT -> ComponentCategory.ELECTRICAL
        VisualComponentCategory.HVAC -> ComponentCategory.COOLING
        VisualComponentCategory.SAFETY_RESTRAINT -> ComponentCategory.ELECTRICAL
        VisualComponentCategory.CONNECTOR -> ComponentCategory.ELECTRICAL
        VisualComponentCategory.DIESEL_EMISSIONS -> ComponentCategory.EXHAUST
    }
}

private fun Map<String, Float>.readPid(pid: String, label: String, unit: String): String {
    val value = this[pid]
        ?: this[pid.uppercase()]
        ?: this[label]
        ?: this[label.uppercase()]
        ?: this[label.lowercase()]
    return if (value != null) {
        val suffix = unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        "%.2f%s".format(value, suffix)
    } else {
        "Sin lectura en vivo"
    }
}
