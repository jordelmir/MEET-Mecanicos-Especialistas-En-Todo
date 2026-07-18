package com.elysium369.meet.visual3d.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.SphereNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.elysium369.meet.visual3d.domain.ApplicabilityState
import com.elysium369.meet.visual3d.domain.MaterialClass
import com.elysium369.meet.visual3d.domain.ReferenceVehicleServiceLayout
import com.elysium369.meet.visual3d.domain.ServicePosition
import com.elysium369.meet.visual3d.domain.VehicleTwinSystem
import com.elysium369.meet.visual3d.domain.VehicleTwinSystemAtlas
import com.elysium369.meet.visual3d.domain.CatalogSemanticScenePlanner
import com.elysium369.meet.visual3d.domain.CatalogMechanicalAssemblyPlanner
import com.elysium369.meet.visual3d.domain.GenericInlineFourAssetContract
import com.elysium369.meet.visual3d.domain.GenericVehicleSystemsAssetContract
import com.elysium369.meet.visual3d.domain.MechanicalElementShape
import com.elysium369.meet.visual3d.domain.MechanicalMaterial
import com.elysium369.meet.visual3d.domain.SemanticPrimitive
import com.elysium369.meet.visual3d.domain.VehicleTwinRenderPolicy
import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode
import com.elysium369.meet.domain.visualdiagnostics.DiagnosticComponent
import com.elysium369.meet.domain.visualdiagnostics.ComponentCategory
import kotlinx.coroutines.delay
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Mat4
import kotlin.math.abs
import kotlin.math.sin

private const val REFERENCE_VEHICLE_ASSET = "models/vehicle_twin/reference_vehicle.glb"

@Composable
fun CompleteVehicleTwinView(
    selectedSystemId: String,
    selectedEntityId: String?,
    viewportState: VehicleTwinViewportState,
    onSystemSelected: (String) -> Unit,
    onVehicleTapped: () -> Unit,
    onRenderedNodeCountChanged: (Int) -> Unit,
    fallbackContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    diagnosticComponents: List<DiagnosticComponent> = emptyList(),
    catalogNodes: List<UniversalCatalogSceneNode> = emptyList(),
    activeDtcs: List<String> = emptyList(),
    onComponentSelected: ((String, String) -> Unit)? = null,
    isObdConnected: Boolean = false
) {
    val context = LocalContext.current
    val supportsFilament = remember(context) { context.supportsFilament() }

    if (!supportsFilament) {
        SchematicFallback(modifier, fallbackContent)
        return
    }

    FilamentVehicleScene(
        selectedSystemId = selectedSystemId,
        selectedEntityId = selectedEntityId,
        viewportState = viewportState,
        onSystemSelected = onSystemSelected,
        onVehicleTapped = onVehicleTapped,
        onRenderedNodeCountChanged = onRenderedNodeCountChanged,
        fallbackContent = fallbackContent,
        modifier = modifier,
        diagnosticComponents = diagnosticComponents,
        catalogNodes = catalogNodes,
        activeDtcs = activeDtcs,
        onComponentSelected = onComponentSelected,
        isObdConnected = isObdConnected
    )
}

@Composable
private fun FilamentVehicleScene(
    selectedSystemId: String,
    selectedEntityId: String?,
    viewportState: VehicleTwinViewportState,
    onSystemSelected: (String) -> Unit,
    onVehicleTapped: () -> Unit,
    onRenderedNodeCountChanged: (Int) -> Unit,
    fallbackContent: @Composable () -> Unit,
    modifier: Modifier,
    diagnosticComponents: List<DiagnosticComponent>,
    catalogNodes: List<UniversalCatalogSceneNode>,
    activeDtcs: List<String>,
    onComponentSelected: ((String, String) -> Unit)?,
    isObdConnected: Boolean
) {
    val engine = rememberEngine()
    val filamentView = rememberView(engine)
    val sceneCollisionSystem = rememberCollisionSystem(filamentView)
    val modelLoader = rememberModelLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, REFERENCE_VEHICLE_ASSET)
    val inlineFourModelInstance = rememberModelInstance(
        modelLoader,
        GenericInlineFourAssetContract.ASSET_PATH
    )
    val detailedSystemAsset = remember(selectedSystemId) {
        GenericVehicleSystemsAssetContract.assetForSystem(selectedSystemId)
    }
    val detailedSystemModelInstance = detailedSystemAsset?.let { asset ->
        androidx.compose.runtime.key(asset.id) {
            rememberModelInstance(modelLoader, asset.assetPath)
        }
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val materialLoader = remember(engine) { MaterialLoader(engine, context, coroutineScope) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val currentAutoRotate = rememberUpdatedState(viewportState.autoRotateEnabled)
    val currentExplodedProgress = rememberUpdatedState(viewportState.explodedProgress.coerceIn(0f, 1f))
    val originalPartTransforms = remember(modelInstance) { mutableMapOf<String, Mat4>() }
    var loadTimedOut by remember { mutableStateOf(false) }

    // Cache to prevent Filament native material leak crashes
    val dynamicMaterials = remember(engine) { mutableMapOf<String, com.google.android.filament.MaterialInstance>() }
    val activeNodes = remember(engine) { mutableListOf<io.github.sceneview.node.Node>() }

    fun destroyNodeRecursively(node: io.github.sceneview.node.Node) {
        node.childNodes.forEach { child ->
            destroyNodeRecursively(child)
        }
        try {
            node.destroy()
        } catch (e: Exception) {
            android.util.Log.e("CompleteVehicleTwinView", "Error destroying node", e)
        }
    }

    androidx.compose.runtime.DisposableEffect(engine) {
        onDispose {
            activeNodes.forEach {
                try { destroyNodeRecursively(it) } catch (e: Exception) {}
            }
            activeNodes.clear()

            dynamicMaterials.values.forEach { materialInstance ->
                try {
                    engine.destroyMaterialInstance(materialInstance)
                } catch (e: Exception) {
                    android.util.Log.e("CompleteVehicleTwinView", "Error destroying material instance", e)
                }
            }
            dynamicMaterials.clear()
        }
    }

    val showDiagnosticOverlay = isObdConnected ||
        selectedEntityId != null ||
        viewportState.xRayEnabled

    val currentSystemId = rememberUpdatedState(selectedSystemId)
    val currentXRay = rememberUpdatedState(viewportState.xRayEnabled)
    val currentSelectedEntityId = rememberUpdatedState(selectedEntityId)
    val currentShowDiagnosticOverlay = rememberUpdatedState(showDiagnosticOverlay)
    val originalInlineFourTransforms = remember(inlineFourModelInstance) { mutableMapOf<String, Mat4>() }
    val originalInlineFourMaterials = remember(inlineFourModelInstance) {
        mutableMapOf<String, List<com.google.android.filament.MaterialInstance>>()
    }
    val inlineFourNodeNameByEntity = remember(engine) { mutableMapOf<Int, String>() }
    val originalDetailedSystemTransforms = remember(detailedSystemModelInstance) {
        mutableMapOf<String, Mat4>()
    }
    val originalDetailedSystemMaterials = remember(detailedSystemModelInstance) {
        mutableMapOf<String, List<com.google.android.filament.MaterialInstance>>()
    }
    val detailedSystemNodeNameByEntity = remember(engine) { mutableMapOf<Int, String>() }
    val catalogPlacementByEntity = remember(engine) {
        mutableMapOf<Int, com.elysium369.meet.visual3d.domain.CatalogSemanticPlacement>()
    }
    val semanticPlacements = remember(
        catalogNodes,
        selectedEntityId,
        selectedSystemId,
        detailedSystemAsset
    ) {
        if (selectedSystemId == "engine" || detailedSystemAsset != null) {
            CatalogSemanticScenePlanner.placements(catalogNodes, selectedEntityId)
        } else {
            emptyList()
        }
    }
    val semanticElementCount = remember(semanticPlacements) {
        semanticPlacements.sumOf { placement ->
            if (!VehicleTwinRenderPolicy.isPhysicalComponentName(placement.node.name)) {
                0
            } else if (placement.occurrence == 0 && (
                selectedSystemId == "engine" &&
                    inlineFourModelInstance != null &&
                    GenericInlineFourAssetContract.bindingForSourceName(placement.node.name) != null ||
                    detailedSystemAsset != null &&
                    detailedSystemModelInstance != null &&
                    GenericVehicleSystemsAssetContract.bindingForSourceNode(
                        detailedSystemAsset,
                        placement.node
                    ) != null
                )) {
                0
            } else if (placement.occurrence == 0) {
                CatalogMechanicalAssemblyPlanner.elementsFor(
                    placement.node.name,
                    placement.primitive
                ).size
            } else {
                CatalogMechanicalAssemblyPlanner.sourceRecordToken(placement.occurrence).size
            }
        }
    }

    LaunchedEffect(modelInstance) {
        if (modelInstance == null) {
            delay(8_000)
            loadTimedOut = true
        } else {
            loadTimedOut = false
        }
    }

    val physicalSystems = remember {
        VehicleTwinSystemAtlas.systems.filter {
            it.applicability != ApplicabilityState.INFORMATIONAL
        }
    }
    LaunchedEffect(
        modelInstance,
        inlineFourModelInstance,
        detailedSystemModelInstance,
        selectedEntityId,
        semanticPlacements
    ) {
        onRenderedNodeCountChanged(
            if (modelInstance == null) {
                0
            } else {
                modelInstance.entities.size + physicalSystems.size + semanticElementCount +
                    if (viewportState.xRayEnabled && selectedSystemId == "engine") {
                        inlineFourModelInstance?.entities?.size ?: 0
                    } else if (viewportState.xRayEnabled && detailedSystemAsset != null) {
                        detailedSystemModelInstance?.entities?.size ?: 0
                    } else {
                        0
                    } +
                    if (selectedEntityId == null) 0 else 1
            }
        )
    }

    if (loadTimedOut && modelInstance == null) {
        SchematicFallback(modifier, fallbackContent)
        return
    }

    Box(modifier = modifier.background(Color(0xFF05090D))) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            view = filamentView,
            collisionSystem = sceneCollisionSystem,
            modelLoader = modelLoader,
            renderQuality = RenderQuality.Default,
            autoCenterContent = true,
            autoFitContent = true,
            onTouchEvent = { event, hitResult ->
                if (event.action != MotionEvent.ACTION_UP) {
                    false
                } else if (
                    currentXRay.value &&
                    (currentSystemId.value == "engine" || detailedSystemAsset != null)
                ) {
                    val viewport = filamentView.viewport
                    val pickX = event.x.toInt().coerceIn(0, (viewport.width - 1).coerceAtLeast(0))
                    val pickY = (viewport.height - event.y.toInt())
                        .coerceIn(0, (viewport.height - 1).coerceAtLeast(0))
                    filamentView.pick(pickX, pickY, mainHandler) { result ->
                        val pickedNodeName = inlineFourNodeNameByEntity[result.renderable]
                            ?: inlineFourModelInstance?.asset?.getName(result.renderable)
                            ?: detailedSystemNodeNameByEntity[result.renderable]
                            ?: detailedSystemModelInstance?.asset?.getName(result.renderable)
                        val selectedPlacement = catalogPlacementByEntity[result.renderable]
                            ?: pickedNodeName?.let { nodeName ->
                                if (currentSystemId.value == "engine") {
                                    GenericInlineFourAssetContract.placementForNodeName(
                                        nodeName,
                                        semanticPlacements
                                    )
                                } else {
                                    detailedSystemAsset?.let { asset ->
                                        GenericVehicleSystemsAssetContract.placementForNodeName(
                                            asset,
                                            nodeName,
                                            semanticPlacements
                                        )
                                    }
                                }
                            }
                        if (selectedPlacement != null) {
                            onComponentSelected?.invoke(
                                selectedPlacement.node.id,
                                selectedPlacement.node.name
                            )
                        }
                    }
                    true
                } else if (hitResult == null) {
                    false
                } else {
                    var hitNode: io.github.sceneview.node.Node? = hitResult.node
                    var catalogPartId: String? = null
                    while (hitNode != null && catalogPartId == null) {
                        catalogPartId = hitNode.name
                            ?.takeIf { it.startsWith("catalog_part_") }
                            ?.removePrefix("catalog_part_")
                        hitNode = hitNode.parent
                    }
                    val catalogPlacement = catalogPartId?.let { partId ->
                        semanticPlacements.firstOrNull { it.node.id == partId }
                    }
                    val assetPlacement = if (currentSystemId.value == "engine") {
                        GenericInlineFourAssetContract.placementForNodeName(
                            hitResult.node.name,
                            semanticPlacements
                        )
                    } else {
                        detailedSystemAsset?.let { asset ->
                            GenericVehicleSystemsAssetContract.placementForNodeName(
                                asset,
                                hitResult.node.name,
                                semanticPlacements
                            )
                        }
                    }
                    val selectedPlacement = catalogPlacement ?: assetPlacement
                    if (selectedPlacement != null) {
                        onComponentSelected?.invoke(
                            selectedPlacement.node.id,
                            selectedPlacement.node.name
                        )
                    } else {
                        onVehicleTapped()
                    }
                    true
                }
            }
        ) {
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    autoAnimate = false,
                    scaleToUnits = 2.4f + viewportState.explodedProgress * 0.06f,
                    centerOrigin = Position(0f, 0f, 0f),
                    rotation = Rotation(
                        x = if (viewportState.focusMode == TwinFocusMode.REPAIR) -4f else 0f,
                        y = 0f,
                        z = 0f
                    ),
                    apply = {
                        // The aggregate GLB bounds must not intercept taps intended for procedural parts.
                        isHittable = false
                        isTouchable = false
                        isVisible = !(
                            viewportState.xRayEnabled &&
                                (selectedSystemId == "engine" || detailedSystemAsset != null)
                            )
                        val servicePartNodes = renderableNodes.mapNotNull { partNode ->
                            val partName = partNode.name ?: return@mapNotNull null
                            if (partName in ReferenceVehicleServiceLayout.offsets) {
                                partName to partNode
                            } else {
                                null
                            }
                        }
                        servicePartNodes.forEach { (partName, partNode) ->
                            originalPartTransforms.getOrPut(partName) { Mat4(partNode.transform) }
                        }

                        // Remove existing markers to avoid duplication during recomposition and release native assets
                        activeNodes.forEach {
                            removeChildNode(it)
                            try { destroyNodeRecursively(it) } catch(e: Exception) {}
                        }
                        activeNodes.clear()

                        fun getAssemblyForComponent(comp: DiagnosticComponent): String? {
                            val id = comp.id
                            val cat = comp.category
                            return when {
                                cat == ComponentCategory.SUSPENSION || cat == ComponentCategory.BRAKES -> {
                                    val isLeft = id.contains("left") || id.contains("_l") || (id.hashCode() % 2 == 0)
                                    val isFront = id.contains("front") || id.contains("input") || (id.hashCode() % 4 < 2)
                                    when {
                                        isFront && isLeft -> "WheelFrontL"
                                        isFront && !isLeft -> "WheelFrontR"
                                        !isFront && isLeft -> "WheelRearL"
                                        else -> "WheelRearR"
                                    }
                                }
                                id.contains("axle") || id.contains("differential") -> "Axles"
                                cat == ComponentCategory.IGNITION || 
                                cat == ComponentCategory.COOLING || 
                                cat == ComponentCategory.LUBRICATION ||
                                cat == ComponentCategory.TURBO_SUPERCHARGER ||
                                (cat == ComponentCategory.FUEL && !id.contains("pump") && !id.contains("tank")) ||
                                (cat == ComponentCategory.AIR_INTAKE) -> "Engine"
                                else -> null
                            }
                        }

                        if (showDiagnosticOverlay && semanticPlacements.isEmpty()) {
                            // Build 3D sphere markers inside the 3D vehicle space
                            diagnosticComponents.forEach { comp ->
                                val isZeroPos = comp.position.x == 0f && comp.position.y == 0f && comp.position.z == 0f
                                if (isZeroPos) return@forEach

                                val hasDtc = activeDtcs.contains(comp.id) || comp.relatedDtcs.any { activeDtcs.contains(it.code) }
                                val isSelected = comp.id == selectedEntityId

                                val colorHex = when {
                                    hasDtc -> 0xFFFF2222.toInt() // Red for DTC
                                    isSelected -> 0xFF22D3EE.toInt() // Cyan for selected
                                    else -> when (comp.category) {
                                        ComponentCategory.IGNITION -> 0xFFF59E0B.toInt() // Amber
                                        ComponentCategory.AIR_INTAKE -> 0xFF10B981.toInt() // Emerald
                                        ComponentCategory.FUEL -> 0xFFEF4444.toInt() // Red
                                        ComponentCategory.EXHAUST -> 0xFFB45309.toInt() // Brown-orange
                                        ComponentCategory.ELECTRICAL -> 0xFF3B82F6.toInt() // Blue
                                        ComponentCategory.COOLING -> 0xFF06B6D4.toInt() // Cyan
                                        ComponentCategory.LUBRICATION -> 0xFFF59E0B.toInt() // Amber
                                        ComponentCategory.SENSOR -> 0xFF06B6D4.toInt() // Cyan
                                        ComponentCategory.RELAY_FUSE -> 0xFF3B82F6.toInt() // Blue
                                        ComponentCategory.TRANSMISSION -> 0xFF10B981.toInt() // Emerald
                                        ComponentCategory.SUSPENSION -> 0xFF8B5CF6.toInt() // Purple
                                        ComponentCategory.STEERING -> 0xFFEC4899.toInt() // Pink
                                        ComponentCategory.BRAKES -> 0xFFEF4444.toInt() // Red
                                        else -> 0xFF6B7280.toInt() // Gray
                                    }
                                }

                                val matKey = "marker_$colorHex"
                                val markerMaterial = dynamicMaterials.getOrPut(matKey) {
                                    materialLoader.createColorInstance(
                                        colorHex,
                                        0.1f, // roughness (shiny)
                                        0.8f, // metallic
                                        0.5f  // reflectance
                                    )
                                }

                                val radius = if (isSelected) 0.05f else 0.035f
                                val sphereNode = io.github.sceneview.node.SphereNode(
                                    engine,
                                    radius,
                                    Position(comp.position.x / 100f, comp.position.y / 100f, comp.position.z / 100f),
                                    8,
                                    8,
                                    markerMaterial,
                                    { }
                                )
                                sphereNode.name = "marker_${comp.id}"
                                sphereNode.isTouchable = true
                                sphereNode.onTouch = { motionEvent, _ ->
                                    if (motionEvent.action == MotionEvent.ACTION_UP) {
                                        onComponentSelected?.invoke(comp.id, comp.name)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                
                                addChildNode(sphereNode)
                                activeNodes.add(sphereNode)

                                // ═══ DYNAMIC HOLOGRAPHIC 3D MESH GENERATION ═══
                                // Build programmatic 3D assembly inline to avoid KAPT stub issues
                                if (
                                    VehicleTwinRenderPolicy.shouldRenderProgrammaticAssembly(
                                        category = comp.category,
                                        selectedSystemId = selectedSystemId,
                                        isSelected = isSelected,
                                        hasDtc = hasDtc,
                                        xRayEnabled = viewportState.xRayEnabled,
                                        hasCatalogSemanticGeometry = semanticPlacements.isNotEmpty()
                                    )
                                ) {
                                    val partContainer = io.github.sceneview.node.Node(engine)
                                    partContainer.name = "part_assembly_${comp.id}"
                                    partContainer.position = Position(comp.position.x / 100f, comp.position.y / 100f, comp.position.z / 100f)

                                    val whiteMat = dynamicMaterials.getOrPut("white") { materialLoader.createColorInstance(0xFFFFFFFF.toInt(), 0.8f, 0.0f, 0.2f) }
                                    val metalMat2 = dynamicMaterials.getOrPut("metal") { materialLoader.createColorInstance(0xFF888888.toInt(), 0.1f, 0.9f, 0.8f) }
                                    val blackPlasticMat = dynamicMaterials.getOrPut("blackPlastic") { materialLoader.createColorInstance(0xFF1E1E1E.toInt(), 0.4f, 0.1f, 0.1f) }
                                    val copperMat = dynamicMaterials.getOrPut("copper") { materialLoader.createColorInstance(0xFFD97706.toInt(), 0.2f, 0.8f, 0.7f) }
                                    val redMat2 = dynamicMaterials.getOrPut("red") { materialLoader.createColorInstance(0xFFEF4444.toInt(), 0.3f, 0.6f, 0.5f) }
                                    val blueMat2 = dynamicMaterials.getOrPut("blue") { materialLoader.createColorInstance(0xFF3B82F6.toInt(), 0.3f, 0.6f, 0.5f) }

                                when (comp.category) {
                                    ComponentCategory.IGNITION -> {
                                        val ceramic = io.github.sceneview.node.CylinderNode(engine, 0.008f, 0.04f, Position(0f, 0f, 0.015f), 12, whiteMat, {})
                                        val baseHex = io.github.sceneview.node.CubeNode(engine, Position(0.018f, 0.018f, 0.015f), Position(0f, 0f, -0.01f), metalMat2, {})
                                        val thread = io.github.sceneview.node.CylinderNode(engine, 0.006f, 0.02f, Position(0f, 0f, -0.025f), 12, metalMat2, {})
                                        partContainer.addChildNode(ceramic)
                                        partContainer.addChildNode(baseHex)
                                        partContainer.addChildNode(thread)
                                    }
                                    ComponentCategory.FUEL -> {
                                        if (comp.id.contains("injector")) {
                                            val body = io.github.sceneview.node.CylinderNode(engine, 0.007f, 0.05f, Position(0f, 0f, 0f), 12, metalMat2, {})
                                            val connector = io.github.sceneview.node.CubeNode(engine, Position(0.012f, 0.012f, 0.015f), Position(0.008f, 0f, 0.015f), blackPlasticMat, {})
                                            val nozzle = io.github.sceneview.node.CylinderNode(engine, 0.003f, 0.015f, Position(0f, 0f, -0.03f), 12, copperMat, {})
                                            partContainer.addChildNode(body)
                                            partContainer.addChildNode(connector)
                                            partContainer.addChildNode(nozzle)
                                        } else {
                                            val tank = io.github.sceneview.node.CubeNode(engine, Position(0.24f, 0.24f, 0.12f), Position(0f, 0f, 0f), blackPlasticMat, {})
                                            val pumpCap = io.github.sceneview.node.CylinderNode(engine, 0.04f, 0.02f, Position(0f, 0f, 0.06f), 12, metalMat2, {})
                                            partContainer.addChildNode(tank)
                                            partContainer.addChildNode(pumpCap)
                                        }
                                    }
                                    ComponentCategory.COOLING -> {
                                        if (comp.id.contains("radiator") || comp.id.contains("condenser")) {
                                            val grid = io.github.sceneview.node.CubeNode(engine, Position(0.02f, 0.38f, 0.24f), Position(0f, 0f, 0f), blackPlasticMat, {})
                                            val cap = io.github.sceneview.node.CylinderNode(engine, 0.012f, 0.015f, Position(0f, 0.18f, 0.13f), 12, metalMat2, {})
                                            partContainer.addChildNode(grid)
                                            partContainer.addChildNode(cap)
                                        } else {
                                            val coolBody = io.github.sceneview.node.SphereNode(engine, 0.03f, Position(0f, 0f, 0f), 8, 8, metalMat2, {})
                                            val pipe1 = io.github.sceneview.node.CylinderNode(engine, 0.012f, 0.04f, Position(0.02f, 0f, 0.02f), 12, metalMat2, {})
                                            partContainer.addChildNode(coolBody)
                                            partContainer.addChildNode(pipe1)
                                        }
                                    }
                                    ComponentCategory.LUBRICATION -> {
                                        if (comp.id.contains("filter")) {
                                            val filter = io.github.sceneview.node.CylinderNode(engine, 0.025f, 0.06f, Position(0f, 0f, 0f), 12, whiteMat, {})
                                            partContainer.addChildNode(filter)
                                        } else {
                                            val pan = io.github.sceneview.node.CubeNode(engine, Position(0.18f, 0.22f, 0.08f), Position(0f, 0f, 0f), metalMat2, {})
                                            partContainer.addChildNode(pan)
                                        }
                                    }
                                    ComponentCategory.RELAY_FUSE -> {
                                        if (comp.id.contains("box") || comp.id.contains("panel")) {
                                            val box = io.github.sceneview.node.CubeNode(engine, Position(0.08f, 0.12f, 0.06f), Position(0f, 0f, 0f), blackPlasticMat, {})
                                            val fuse1 = io.github.sceneview.node.CubeNode(engine, Position(0.01f, 0.015f, 0.01f), Position(-0.02f, 0.02f, 0.032f), redMat2, {})
                                            val fuse2 = io.github.sceneview.node.CubeNode(engine, Position(0.01f, 0.015f, 0.01f), Position(0.02f, -0.02f, 0.032f), blueMat2, {})
                                            partContainer.addChildNode(box)
                                            partContainer.addChildNode(fuse1)
                                            partContainer.addChildNode(fuse2)
                                        } else {
                                            val relay = io.github.sceneview.node.CubeNode(engine, Position(0.02f, 0.02f, 0.025f), Position(0f, 0f, 0f), blackPlasticMat, {})
                                            val terminal = io.github.sceneview.node.CylinderNode(engine, 0.003f, 0.01f, Position(0f, 0f, -0.015f), 12, copperMat, {})
                                            partContainer.addChildNode(relay)
                                            partContainer.addChildNode(terminal)
                                        }
                                    }
                                    ComponentCategory.SENSOR -> {
                                        val probe = io.github.sceneview.node.CylinderNode(engine, 0.006f, 0.03f, Position(0f, 0f, -0.01f), 12, metalMat2, {})
                                        val head = io.github.sceneview.node.CubeNode(engine, Position(0.016f, 0.016f, 0.016f), Position(0f, 0f, 0.01f), blackPlasticMat, {})
                                        partContainer.addChildNode(probe)
                                        partContainer.addChildNode(head)
                                    }
                                    ComponentCategory.BRAKES -> {
                                        val caliper = io.github.sceneview.node.CubeNode(engine, Position(0.03f, 0.08f, 0.05f), Position(0f, 0f, 0f), redMat2, {})
                                        partContainer.addChildNode(caliper)
                                    }
                                    ComponentCategory.SUSPENSION -> {
                                        val strut = io.github.sceneview.node.CylinderNode(engine, 0.012f, 0.15f, Position(0f, 0f, 0f), 12, metalMat2, {})
                                        val coil2 = io.github.sceneview.node.CylinderNode(engine, 0.022f, 0.12f, Position(0f, 0f, 0f), 12, blackPlasticMat, {})
                                        partContainer.addChildNode(strut)
                                        partContainer.addChildNode(coil2)
                                    }
                                    ComponentCategory.ELECTRICAL -> {
                                        if (comp.id.contains("alternator")) {
                                            val altBody = io.github.sceneview.node.CylinderNode(engine, 0.035f, 0.06f, Position(0f, 0f, 0f), 12, metalMat2, {})
                                            val pulley = io.github.sceneview.node.CylinderNode(engine, 0.02f, 0.015f, Position(0f, 0.032f, 0f), 12, blackPlasticMat, {})
                                            partContainer.addChildNode(altBody)
                                            partContainer.addChildNode(pulley)
                                        } else if (comp.id.contains("starter")) {
                                            val mainCyl = io.github.sceneview.node.CylinderNode(engine, 0.025f, 0.08f, Position(0f, 0f, 0f), 12, metalMat2, {})
                                            val solCyl = io.github.sceneview.node.CylinderNode(engine, 0.014f, 0.06f, Position(0.02f, 0f, 0.015f), 12, blackPlasticMat, {})
                                            partContainer.addChildNode(mainCyl)
                                            partContainer.addChildNode(solCyl)
                                        } else if (comp.id.contains("battery")) {
                                            val box = io.github.sceneview.node.CubeNode(engine, Position(0.12f, 0.16f, 0.12f), Position(0f, 0f, 0f), blackPlasticMat, {})
                                            val term1 = io.github.sceneview.node.CylinderNode(engine, 0.008f, 0.015f, Position(-0.04f, 0.05f, 0.065f), 12, redMat2, {})
                                            val term2 = io.github.sceneview.node.CylinderNode(engine, 0.008f, 0.015f, Position(0.04f, 0.05f, 0.065f), 12, blueMat2, {})
                                            partContainer.addChildNode(box)
                                            partContainer.addChildNode(term1)
                                            partContainer.addChildNode(term2)
                                        }
                                    }
                                    else -> {
                                        val core = io.github.sceneview.node.SphereNode(engine, 0.025f, Position(0f, 0f, 0f), 8, 8, metalMat2, {})
                                        partContainer.addChildNode(core)
                                    }
                                }
                                addChildNode(partContainer)
                                activeNodes.add(partContainer)
                            }
                        }
                    }

                        val catalogServiceNodes = mutableListOf<Pair<com.elysium369.meet.visual3d.domain.CatalogSemanticPlacement, io.github.sceneview.node.Node>>()
                        if (semanticPlacements.isNotEmpty()) {
                            val semanticMaterials = mapOf(
                                MechanicalMaterial.CAST_IRON to 0xFF465460.toInt(),
                                MechanicalMaterial.STEEL to 0xFFC4CED8.toInt(),
                                MechanicalMaterial.ALUMINUM to 0xFFAAB5C0.toInt(),
                                MechanicalMaterial.COPPER to 0xFFB87333.toInt(),
                                MechanicalMaterial.POLYMER to 0xFF252C33.toInt()
                            ).mapValues { (materialClass, color) ->
                                dynamicMaterials.getOrPut("catalog_mechanical_${materialClass.name}") {
                                    val metallic = if (materialClass == MechanicalMaterial.POLYMER) 0.08f else 0.78f
                                    val roughness = if (materialClass == MechanicalMaterial.POLYMER) 0.62f else 0.23f
                                    materialLoader.createColorInstance(color, roughness, metallic, 0.62f)
                                }
                            }
                            val selectedSemanticMaterial = dynamicMaterials.getOrPut("catalog_semantic_selected") {
                                materialLoader.createColorInstance(
                                    0xFF22D3EE.toInt(),
                                    0.12f,
                                    0.82f,
                                    0.72f
                                )
                            }
                            semanticPlacements.forEach { placement ->
                                if (!VehicleTwinRenderPolicy.isPhysicalComponentName(placement.node.name)) {
                                    return@forEach
                                }
                                val renderedByDetailedAsset = placement.occurrence == 0 && when {
                                    selectedSystemId == "engine" ->
                                        inlineFourModelInstance != null &&
                                            GenericInlineFourAssetContract.bindingForSourceName(
                                                placement.node.name
                                            ) != null
                                    detailedSystemAsset != null ->
                                        detailedSystemModelInstance != null &&
                                            GenericVehicleSystemsAssetContract.bindingForSourceNode(
                                                detailedSystemAsset,
                                                placement.node
                                            ) != null
                                    else -> false
                                }
                                if (renderedByDetailedAsset) return@forEach
                                val fallbackVisible = VehicleTwinRenderPolicy.shouldShowCatalogFallback(
                                    sourceName = placement.node.name,
                                    renderedByDetailedAsset = false,
                                    hasDetailedSystemAsset = if (selectedSystemId == "engine") {
                                        inlineFourModelInstance != null
                                    } else {
                                        detailedSystemModelInstance != null
                                    },
                                    xRayEnabled = viewportState.xRayEnabled,
                                    explodedProgress = viewportState.explodedProgress,
                                    isSelected = placement.node.id == selectedEntityId
                                )
                                val container = io.github.sceneview.node.Node(engine).apply {
                                    name = "catalog_part_${placement.node.id}"
                                    position = Position(placement.x, placement.y, placement.z)
                                    isVisible = fallbackVisible
                                    isHittable = fallbackVisible
                                }
                                val mechanicalElements = if (placement.occurrence == 0) {
                                    CatalogMechanicalAssemblyPlanner.elementsFor(
                                        placement.node.name,
                                        placement.primitive
                                    )
                                } else {
                                    CatalogMechanicalAssemblyPlanner.sourceRecordToken(placement.occurrence)
                                }
                                mechanicalElements.forEachIndexed { elementIndex, element ->
                                    val material = if (placement.node.id == selectedEntityId) {
                                        selectedSemanticMaterial
                                    } else {
                                        semanticMaterials.getValue(element.material)
                                    }
                                    val localPosition = Position(
                                        element.x * placement.scale,
                                        element.y * placement.scale,
                                        element.z * placement.scale
                                    )
                                    val elementNode = when (element.shape) {
                                        MechanicalElementShape.CUBE -> io.github.sceneview.node.CubeNode(
                                            engine,
                                            Position(
                                                element.sizeX * placement.scale,
                                                element.sizeY * placement.scale,
                                                element.sizeZ * placement.scale
                                            ),
                                            localPosition,
                                            material,
                                            {}
                                        )
                                        MechanicalElementShape.CYLINDER -> io.github.sceneview.node.CylinderNode(
                                            engine,
                                            element.radius * placement.scale,
                                            element.height * placement.scale,
                                            localPosition,
                                            16,
                                            material,
                                            {}
                                        )
                                        MechanicalElementShape.SPHERE -> io.github.sceneview.node.SphereNode(
                                            engine,
                                            element.radius * placement.scale,
                                            localPosition,
                                            12,
                                            12,
                                            material,
                                            {}
                                        )
                                    }.apply {
                                        name = "catalog_hit_${placement.node.id}_$elementIndex"
                                        rotation = Rotation(
                                            x = element.rotationX,
                                            y = element.rotationY,
                                            z = element.rotationZ
                                        )
                                        isTouchable = fallbackVisible
                                        isHittable = fallbackVisible
                                        onTouch = { motionEvent, _ ->
                                            if (motionEvent.action == MotionEvent.ACTION_UP) {
                                                onComponentSelected?.invoke(placement.node.id, placement.node.name)
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        onSingleTapConfirmed = {
                                            onComponentSelected?.invoke(placement.node.id, placement.node.name)
                                            true
                                        }
                                    }
                                    catalogPlacementByEntity[elementNode.entity] = placement
                                    container.addChildNode(elementNode)
                                }
                                addChildNode(container)
                                activeNodes.add(container)
                                catalogServiceNodes += placement to container
                            }
                        }

                        fun updateServiceLayout(progress: Float) {
                            servicePartNodes.forEach { (partName, partNode) ->
                                val sourceTransform = originalPartTransforms.getValue(partName)
                                val sourceTranslation = sourceTransform.w
                                val target = ReferenceVehicleServiceLayout.positionFor(
                                    nodeName = partName,
                                    sourcePosition = ServicePosition(
                                        x = sourceTranslation.x,
                                        y = sourceTranslation.y,
                                        z = sourceTranslation.z
                                    ),
                                    progress = progress
                                )
                                partNode.transform = Mat4(sourceTransform).apply {
                                    w = Float4(target.x, target.y, target.z, sourceTranslation.w)
                                }
                            }

                            catalogServiceNodes.forEach { (placement, catalogNode) ->
                                if (placement.occurrence > 0) return@forEach
                                val offset = CatalogMechanicalAssemblyPlanner.serviceOffset(
                                    name = placement.node.name,
                                    primitive = placement.primitive,
                                    assembledX = placement.x,
                                    progress = progress
                                )
                                catalogNode.position = Position(
                                    x = placement.x + offset.x,
                                    y = placement.y + offset.y,
                                    z = placement.z + offset.z
                                )
                            }

                            // Sync 3D markers with moving assemblies
                            childNodes.filter { it.name?.startsWith("marker_") == true }.forEach { markerNode ->
                                val compId = markerNode.name!!.removePrefix("marker_")
                                val comp = diagnosticComponents.firstOrNull { it.id == compId } ?: return@forEach
                                val assemblyName = getAssemblyForComponent(comp)
                                val basePos = Position(comp.position.x / 100f, comp.position.y / 100f, comp.position.z / 100f)
                                if (assemblyName != null) {
                                    val offset = ReferenceVehicleServiceLayout.offsetFor(assemblyName, progress)
                                    markerNode.position = Position(
                                        x = basePos.x + offset.x,
                                        y = basePos.y + offset.y,
                                        z = basePos.z + offset.z
                                    )
                                } else {
                                    markerNode.position = basePos
                                }
                            }

                            // Sync programmatic part assemblies with moving assemblies
                            childNodes.filter { it.name?.startsWith("part_assembly_") == true }.forEach { assemblyNode ->
                                val compId = assemblyNode.name!!.removePrefix("part_assembly_")
                                val comp = diagnosticComponents.firstOrNull { it.id == compId } ?: return@forEach
                                val assemblyName = getAssemblyForComponent(comp)
                                val basePos = Position(comp.position.x / 100f, comp.position.y / 100f, comp.position.z / 100f)
                                if (assemblyName != null) {
                                    val offset = ReferenceVehicleServiceLayout.offsetFor(assemblyName, progress)
                                    assemblyNode.position = Position(
                                        x = basePos.x + offset.x,
                                        y = basePos.y + offset.y,
                                        z = basePos.z + offset.z
                                    )
                                } else {
                                    assemblyNode.position = basePos
                                }
                            }
                        }

                        var previousFrame = 0L
                        var autoPhase = 0f
                        var renderedExplosion = currentExplodedProgress.value
                        updateServiceLayout(renderedExplosion)
                        onFrame = { frameTimeNanos ->
                            val deltaSeconds = if (previousFrame == 0L) {
                                0f
                            } else {
                                (frameTimeNanos - previousFrame).coerceAtMost(50_000_000L) / 1_000_000_000f
                            }
                            if (
                                currentAutoRotate.value &&
                                currentExplodedProgress.value <= 0.01f &&
                                deltaSeconds > 0f
                            ) {
                                autoPhase = (autoPhase + deltaSeconds * 0.62f) % 6.2831855f
                                rotation = Rotation(
                                    x = 0f,
                                    y = 0f,
                                    z = sin(autoPhase) * 7.5f
                                )
                            } else if (currentExplodedProgress.value <= 0.01f) {
                                rotation = Rotation(x = 0f, y = 0f, z = 0f)
                            }
                            val targetExplosion = currentExplodedProgress.value
                            if (abs(targetExplosion - renderedExplosion) > 0.0005f) {
                                val response = (deltaSeconds * 7.5f).coerceIn(0.08f, 1f)
                                renderedExplosion += (targetExplosion - renderedExplosion) * response
                                updateServiceLayout(renderedExplosion)
                            }
                            
                            // Dynamic 3D Marker & assembly visibility update
                            val showOverlay = currentShowDiagnosticOverlay.value
                            val currentActiveCategories = targetCategoryForSystem(currentSystemId.value)
                            childNodes.filter { it.name?.startsWith("marker_") == true }.forEach { markerNode ->
                                val compId = markerNode.name!!.removePrefix("marker_")
                                val comp = diagnosticComponents.firstOrNull { it.id == compId }
                                if (comp != null) {
                                    val targetVisible = showOverlay && if (currentActiveCategories.isNotEmpty()) {
                                        comp.category in currentActiveCategories
                                    } else {
                                        true
                                    }
                                    if (markerNode.isVisible != targetVisible) {
                                        markerNode.isVisible = targetVisible
                                    }
                                }
                            }
                            childNodes.filter { it.name?.startsWith("part_assembly_") == true }.forEach { assemblyNode ->
                                val compId = assemblyNode.name!!.removePrefix("part_assembly_")
                                val comp = diagnosticComponents.firstOrNull { it.id == compId }
                                if (comp != null) {
                                    val targetVisible = showOverlay && if (currentActiveCategories.isNotEmpty()) {
                                        comp.category in currentActiveCategories
                                    } else {
                                        true
                                    }
                                    if (assemblyNode.isVisible != targetVisible) {
                                        assemblyNode.isVisible = targetVisible
                                    }
                                }
                            }
                            childNodes.filter { it.name?.startsWith("catalog_part_") == true }.forEach { catalogNode ->
                                val catalogPartId = catalogNode.name?.removePrefix("catalog_part_")
                                val placement = semanticPlacements.firstOrNull {
                                    it.node.id == catalogPartId
                                } ?: return@forEach
                                val targetVisible = VehicleTwinRenderPolicy.shouldShowCatalogFallback(
                                    sourceName = placement.node.name,
                                    renderedByDetailedAsset = false,
                                    hasDetailedSystemAsset = if (currentSystemId.value == "engine") {
                                        inlineFourModelInstance != null
                                    } else {
                                        detailedSystemModelInstance != null
                                    },
                                    xRayEnabled = currentXRay.value,
                                    explodedProgress = currentExplodedProgress.value,
                                    isSelected = placement.node.id == currentSelectedEntityId.value
                                )
                                if (catalogNode.isVisible != targetVisible) {
                                    catalogNode.isVisible = targetVisible
                                }
                                if (catalogNode.isHittable != targetVisible) {
                                    catalogNode.isHittable = targetVisible
                                }
                                catalogNode.childNodes.forEach { elementNode ->
                                    if (elementNode.isHittable != targetVisible) {
                                        elementNode.isHittable = targetVisible
                                    }
                                    if (elementNode.isTouchable != targetVisible) {
                                        elementNode.isTouchable = targetVisible
                                    }
                                }
                            }
                            
                            // Determine which assemblies to hide to prevent occluding selected/DTC parts
                            val selId = currentSelectedEntityId.value
                            val selectedComp = diagnosticComponents.firstOrNull { it.id == selId }
                            val isEnginePartSelected = selectedComp?.let {
                                it.category in listOf(
                                    ComponentCategory.IGNITION,
                                    ComponentCategory.FUEL,
                                    ComponentCategory.COOLING,
                                    ComponentCategory.LUBRICATION,
                                    ComponentCategory.AIR_INTAKE,
                                    ComponentCategory.TURBO_SUPERCHARGER,
                                    ComponentCategory.SENSOR
                                )
                            } == true
                            val isSuspensionPartSelected = selectedComp?.let {
                                it.category == ComponentCategory.SUSPENSION || it.category == ComponentCategory.BRAKES
                            } == true

                            // ═══ X-RAY & SYSTEM FOCUS MESH HIDING ═══
                            val xrayOn = currentXRay.value
                            val detailedAssetFocused = currentSystemId.value == "engine" ||
                                detailedSystemAsset != null
                            val engineFocused = currentSystemId.value == "engine"
                            val referenceVisible = !(xrayOn && detailedAssetFocused)
                            if (isVisible != referenceVisible) isVisible = referenceVisible
                            renderableNodes.forEach { partNode ->
                                val pn = partNode.name ?: return@forEach
                                
                                val isBodyExterior = pn.startsWith("Body")
                                val isInterior = pn.startsWith("Interior")
                                val isHoodPart = pn.contains("Hood")
                                
                                val isMechanical = pn == "Engine" || pn == "Axles" ||
                                    pn.startsWith("Wheel") || pn.contains("Brake") ||
                                    pn.contains("Rim") || pn == "License Plate"
                                
                                val shouldHide = when {
                                    xrayOn && detailedAssetFocused -> true
                                    pn == "Engine" && isEnginePartSelected -> true // Hide engine block to reveal internal parts
                                    pn == "Axles" && isSuspensionPartSelected -> true // Hide axles to reveal brake calipers/struts
                                    isMechanical -> false
                                    xrayOn && isBodyExterior -> true
                                    xrayOn && isInterior -> true
                                    !xrayOn && engineFocused && isHoodPart -> true
                                    else -> false
                                }
                                
                                partNode.setLayerVisible(!shouldHide)
                                partNode.isHittable = !shouldHide
                            }
                            
                            previousFrame = frameTimeNanos
                        }
                    }
                )
            }

            inlineFourModelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    autoAnimate = false,
                    scaleToUnits = 2.30f,
                    centerOrigin = Position(0f, 0f, 0f),
                    apply = {
                        name = "meet_generic_inline4_l2"
                        isVisible = viewportState.xRayEnabled && selectedSystemId == "engine"
                        // Child renderables own precise collision boxes; the aggregate root must not
                        // intercept inspection taps before they reach a mechanical mesh family.
                        isHittable = false
                        isTouchable = false
                        inlineFourNodeNameByEntity.clear()

                        val selectedMaterial = dynamicMaterials.getOrPut("inline4_asset_selected") {
                            materialLoader.createColorInstance(
                                0xFF22D3EE.toInt(),
                                0.12f,
                                0.84f,
                                0.74f
                            )
                        }
                        val serviceNodes = renderableNodes.mapNotNull { partNode ->
                            val nodeName = partNode.name ?: return@mapNotNull null
                            val binding = GenericInlineFourAssetContract.bindingForNodeName(nodeName)
                                ?: return@mapNotNull null
                            originalInlineFourTransforms.putIfAbsent(nodeName, Mat4(partNode.transform))
                            originalInlineFourMaterials.putIfAbsent(
                                nodeName,
                                partNode.materialInstances.toList()
                            )
                            inlineFourNodeNameByEntity[partNode.entity] = nodeName
                            inlineFourNodeNameByEntity[partNode.renderableInstance] = nodeName
                            val placement = GenericInlineFourAssetContract.placementForNodeName(
                                nodeName,
                                semanticPlacements
                            )
                            partNode.isTouchable = placement != null
                            partNode.isHittable = false
                            if (placement != null) {
                                partNode.onSingleTapConfirmed = {
                                    onComponentSelected?.invoke(
                                        placement.node.id,
                                        placement.node.name
                                    )
                                    true
                                }
                            }
                            Triple(nodeName, partNode, binding)
                        }

                        fun updateInlineFourSelection() {
                            serviceNodes.forEach { (nodeName, partNode, _) ->
                                if (
                                    GenericInlineFourAssetContract.isNodeSelected(
                                        nodeName,
                                        semanticPlacements,
                                        currentSelectedEntityId.value
                                    )
                                ) {
                                    partNode.setMaterialInstances(selectedMaterial)
                                } else {
                                    originalInlineFourMaterials[nodeName]?.let { originalMaterials ->
                                        partNode.materialInstances = originalMaterials
                                    }
                                }
                            }
                        }

                        fun updateInlineFourExplosion(progress: Float) {
                            serviceNodes.forEach { (nodeName, partNode, _) ->
                                val sourceTransform = originalInlineFourTransforms.getValue(nodeName)
                                val sourceTranslation = sourceTransform.w
                                val offset = GenericInlineFourAssetContract.serviceOffset(
                                    nodeName,
                                    progress
                                )
                                partNode.transform = Mat4(sourceTransform).apply {
                                    w = Float4(
                                        sourceTranslation.x + offset.x,
                                        sourceTranslation.y + offset.y,
                                        sourceTranslation.z + offset.z,
                                        sourceTranslation.w
                                    )
                                }
                            }
                        }

                        var previousFrame = 0L
                        var autoPhase = 0f
                        var renderedExplosion = currentExplodedProgress.value
                        var renderedSelectionId: String? = null
                        updateInlineFourExplosion(renderedExplosion)
                        updateInlineFourSelection()
                        renderedSelectionId = currentSelectedEntityId.value
                        onFrame = { frameTimeNanos ->
                            val deltaSeconds = if (previousFrame == 0L) {
                                0f
                            } else {
                                (frameTimeNanos - previousFrame).coerceAtMost(50_000_000L) /
                                    1_000_000_000f
                            }
                            val targetVisible = currentXRay.value && currentSystemId.value == "engine"
                            if (isVisible != targetVisible) isVisible = targetVisible
                            serviceNodes.forEach { (_, partNode, _) ->
                                if (partNode.isHittable) partNode.isHittable = false
                            }
                            if (
                                targetVisible &&
                                currentAutoRotate.value &&
                                currentExplodedProgress.value <= 0.01f &&
                                deltaSeconds > 0f
                            ) {
                                autoPhase = (autoPhase + deltaSeconds * 0.54f) % 6.2831855f
                                rotation = Rotation(x = 0f, y = sin(autoPhase) * 8.5f, z = 0f)
                            } else if (currentExplodedProgress.value <= 0.01f) {
                                rotation = Rotation(x = 0f, y = 0f, z = 0f)
                            }
                            val targetExplosion = currentExplodedProgress.value
                            if (abs(targetExplosion - renderedExplosion) > 0.0005f) {
                                val response = (deltaSeconds * 7.5f).coerceIn(0.08f, 1f)
                                renderedExplosion += (targetExplosion - renderedExplosion) * response
                                updateInlineFourExplosion(renderedExplosion)
                            }
                            if (renderedSelectionId != currentSelectedEntityId.value) {
                                updateInlineFourSelection()
                                renderedSelectionId = currentSelectedEntityId.value
                            }
                            previousFrame = frameTimeNanos
                        }
                    }
                )
            }

            detailedSystemModelInstance?.let { instance ->
                val systemAsset = detailedSystemAsset
                ModelNode(
                    modelInstance = instance,
                    autoAnimate = false,
                    scaleToUnits = systemAsset.scaleToUnits,
                    centerOrigin = Position(0f, 0f, 0f),
                    apply = {
                        name = "meet_generic_${systemAsset.id}_l2"
                        isVisible = viewportState.xRayEnabled &&
                            selectedSystemId in systemAsset.supportedSystemIds
                        // Precise selection is resolved through Filament View.pick on child meshes.
                        isHittable = false
                        isTouchable = false
                        detailedSystemNodeNameByEntity.clear()

                        val selectedMaterial = dynamicMaterials.getOrPut("system_asset_selected") {
                            materialLoader.createColorInstance(
                                0xFF22D3EE.toInt(),
                                0.12f,
                                0.84f,
                                0.74f
                            )
                        }
                        val serviceNodes = renderableNodes.mapNotNull { partNode ->
                            val nodeName = partNode.name ?: return@mapNotNull null
                            val binding = GenericVehicleSystemsAssetContract.bindingForNodeName(
                                systemAsset,
                                nodeName
                            ) ?: return@mapNotNull null
                            originalDetailedSystemTransforms.putIfAbsent(
                                nodeName,
                                Mat4(partNode.transform)
                            )
                            originalDetailedSystemMaterials.putIfAbsent(
                                nodeName,
                                partNode.materialInstances.toList()
                            )
                            detailedSystemNodeNameByEntity[partNode.entity] = nodeName
                            detailedSystemNodeNameByEntity[partNode.renderableInstance] = nodeName
                            val placement = GenericVehicleSystemsAssetContract.placementForNodeName(
                                systemAsset,
                                nodeName,
                                semanticPlacements
                            )
                            // A shared GLB may contain adjacent systems. Only source-backed families
                            // in the active inspection scope are shown; unbound geometry is context.
                            partNode.setLayerVisible(!binding.isSelectable || placement != null)
                            partNode.isTouchable = placement != null
                            partNode.isHittable = false
                            if (placement != null) {
                                partNode.onSingleTapConfirmed = {
                                    onComponentSelected?.invoke(
                                        placement.node.id,
                                        placement.node.name
                                    )
                                    true
                                }
                            }
                            Triple(nodeName, partNode, binding)
                        }

                        fun updateSystemSelection() {
                            serviceNodes.forEach { (nodeName, partNode, _) ->
                                if (
                                    GenericVehicleSystemsAssetContract.isNodeSelected(
                                        systemAsset,
                                        nodeName,
                                        semanticPlacements,
                                        currentSelectedEntityId.value
                                    )
                                ) {
                                    partNode.setMaterialInstances(selectedMaterial)
                                } else {
                                    originalDetailedSystemMaterials[nodeName]?.let { originals ->
                                        partNode.materialInstances = originals
                                    }
                                }
                            }
                        }

                        fun updateSystemExplosion(progress: Float) {
                            serviceNodes.forEach { (nodeName, partNode, _) ->
                                val sourceTransform = originalDetailedSystemTransforms.getValue(nodeName)
                                val sourceTranslation = sourceTransform.w
                                val offset = GenericVehicleSystemsAssetContract.serviceOffset(
                                    systemAsset,
                                    nodeName,
                                    progress
                                )
                                partNode.transform = Mat4(sourceTransform).apply {
                                    w = Float4(
                                        sourceTranslation.x + offset.x,
                                        sourceTranslation.y + offset.y,
                                        sourceTranslation.z + offset.z,
                                        sourceTranslation.w
                                    )
                                }
                            }
                        }

                        var previousFrame = 0L
                        var autoPhase = 0f
                        var renderedExplosion = currentExplodedProgress.value
                        var renderedSelectionId: String? = null
                        updateSystemExplosion(renderedExplosion)
                        updateSystemSelection()
                        renderedSelectionId = currentSelectedEntityId.value
                        onFrame = { frameTimeNanos ->
                            val deltaSeconds = if (previousFrame == 0L) {
                                0f
                            } else {
                                (frameTimeNanos - previousFrame).coerceAtMost(50_000_000L) /
                                    1_000_000_000f
                            }
                            val targetVisible = currentXRay.value &&
                                currentSystemId.value in systemAsset.supportedSystemIds
                            if (isVisible != targetVisible) isVisible = targetVisible
                            serviceNodes.forEach { (_, partNode, _) ->
                                if (partNode.isHittable) partNode.isHittable = false
                            }
                            if (
                                targetVisible &&
                                currentAutoRotate.value &&
                                currentExplodedProgress.value <= 0.01f &&
                                deltaSeconds > 0f
                            ) {
                                autoPhase = (autoPhase + deltaSeconds * 0.54f) % 6.2831855f
                                rotation = Rotation(x = 0f, y = sin(autoPhase) * 8.5f, z = 0f)
                            } else if (currentExplodedProgress.value <= 0.01f) {
                                rotation = Rotation(x = 0f, y = 0f, z = 0f)
                            }
                            val targetExplosion = currentExplodedProgress.value
                            if (abs(targetExplosion - renderedExplosion) > 0.0005f) {
                                val response = (deltaSeconds * 7.5f).coerceIn(0.08f, 1f)
                                renderedExplosion += (targetExplosion - renderedExplosion) * response
                                updateSystemExplosion(renderedExplosion)
                            }
                            if (renderedSelectionId != currentSelectedEntityId.value) {
                                updateSystemSelection()
                                renderedSelectionId = currentSelectedEntityId.value
                            }
                            previousFrame = frameTimeNanos
                        }
                    }
                )
            }
        }

        if (modelInstance == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                color = Color(0xFF22D3EE),
                strokeWidth = 2.dp
            )
        } else {
            VehicleDiagnosticOverlay(
                systems = physicalSystems,
                selectedSystemId = selectedSystemId,
                xRayEnabled = viewportState.xRayEnabled,
                exploded = viewportState.explodedProgress > 0.01f,
                onSystemSelected = onSystemSelected,
                showDiagnosticOverlay = showDiagnosticOverlay,
                modifier = Modifier.fillMaxSize(),
                diagnosticComponents = diagnosticComponents,
                activeDtcs = activeDtcs,
                selectedComponentId = selectedEntityId,
                onComponentSelected = onComponentSelected
            )
        }
    }
}

@Composable
private fun VehicleDiagnosticOverlay(
    systems: List<VehicleTwinSystem>,
    selectedSystemId: String,
    xRayEnabled: Boolean,
    exploded: Boolean,
    onSystemSelected: (String) -> Unit,
    showDiagnosticOverlay: Boolean,
    modifier: Modifier = Modifier,
    diagnosticComponents: List<DiagnosticComponent> = emptyList(),
    activeDtcs: List<String> = emptyList(),
    selectedComponentId: String? = null,
    onComponentSelected: ((String, String) -> Unit)? = null
) {
    val transition = rememberInfiniteTransition(label = "vehicle-twin-scan")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400), RepeatMode.Reverse),
        label = "vehicle-twin-pulse"
    )

    Box(modifier = modifier) {
        // Scan line effect — only when diagnostics are active
        if (showDiagnosticOverlay) {
            Canvas(Modifier.fillMaxSize().alpha(if (xRayEnabled) 0.72f else 0.38f)) {
                val scanY = size.height * (0.18f + pulse * 0.64f)
                drawLine(
                    color = if (xRayEnabled) Color(0xFF34D399) else Color(0xFF22D3EE),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, scanY),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, scanY),
                    strokeWidth = if (xRayEnabled) 2.2f else 1.2f
                )
            }
        }
    }
}

@Composable
private fun SchematicFallback(
    modifier: Modifier,
    fallbackContent: @Composable () -> Unit
) {
    Box(modifier) {
        fallbackContent()
        Text(
            text = "Vista esquematica",
            color = Color(0xFFFDE047),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(5.dp))
                .border(1.dp, Color(0xFFFDE047), RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

private fun targetCategoryForSystem(systemId: String): List<ComponentCategory> = when (systemId) {
    "engine" -> listOf(
        ComponentCategory.IGNITION,
        ComponentCategory.AIR_INTAKE,
        ComponentCategory.FUEL,
        ComponentCategory.EXHAUST,
        ComponentCategory.COOLING,
        ComponentCategory.LUBRICATION,
        ComponentCategory.TURBO_SUPERCHARGER
    )
    "transmission" -> listOf(ComponentCategory.TRANSMISSION)
    "suspension" -> listOf(ComponentCategory.SUSPENSION)
    "steering" -> listOf(ComponentCategory.STEERING)
    "brakes" -> listOf(ComponentCategory.BRAKES)
    "electrical" -> listOf(
        ComponentCategory.ELECTRICAL,
        ComponentCategory.RELAY_FUSE,
        ComponentCategory.HARNESS,
        ComponentCategory.CONNECTOR
    )
    "control_modules" -> listOf(ComponentCategory.BODY_CONTROL)
    "sensors" -> listOf(ComponentCategory.SENSOR)
    else -> emptyList()
}

private fun ComponentCategory.color(): Color = when (this) {
    ComponentCategory.IGNITION -> Color(0xFFF59E0B) // Amber
    ComponentCategory.AIR_INTAKE -> Color(0xFF10B981) // Emerald
    ComponentCategory.FUEL -> Color(0xFFEF4444) // Red
    ComponentCategory.EXHAUST -> Color(0xFFEF4444) // Red
    ComponentCategory.ELECTRICAL -> Color(0xFF3B82F6) // Blue
    ComponentCategory.COOLING -> Color(0xFF06B6D4) // Cyan
    ComponentCategory.LUBRICATION -> Color(0xFFF59E0B) // Amber
    ComponentCategory.SENSOR -> Color(0xFF06B6D4) // Cyan
    ComponentCategory.RELAY_FUSE -> Color(0xFF3B82F6) // Blue
    ComponentCategory.HARNESS -> Color(0xFF3B82F6) // Blue
    ComponentCategory.EV_HIGH_VOLTAGE -> Color(0xFFF59E0B) // Amber
    ComponentCategory.TRANSMISSION -> Color(0xFF10B981) // Emerald
    ComponentCategory.SUSPENSION -> Color(0xFF10B981) // Emerald
    ComponentCategory.BRAKES -> Color(0xFFFB7185) // Rose
    ComponentCategory.STEERING -> Color(0xFF10B981) // Emerald
    ComponentCategory.TURBO_SUPERCHARGER -> Color(0xFF10B981) // Emerald
    ComponentCategory.BODY_CONTROL -> Color(0xFF3B82F6) // Blue
    ComponentCategory.INFOTAINMENT -> Color(0xFF3B82F6) // Blue
    ComponentCategory.HVAC -> Color(0xFF06B6D4) // Cyan
    ComponentCategory.SAFETY_RESTRAINT -> Color(0xFF3B82F6) // Blue
    ComponentCategory.CONNECTOR -> Color(0xFF3B82F6) // Blue
    ComponentCategory.DIESEL_EMISSIONS -> Color(0xFFEF4444) // Red
}

private fun MaterialClass.color(): Color = when (this) {
    MaterialClass.STRUCTURE -> Color(0xFFCBD5E1)
    MaterialClass.POWERTRAIN -> Color(0xFFF59E0B)
    MaterialClass.CHASSIS -> Color(0xFFA3E635)
    MaterialClass.ELECTRICAL -> Color(0xFF22D3EE)
    MaterialClass.BODY -> Color(0xFFF472B6)
    MaterialClass.CABIN -> Color(0xFFC084FC)
    MaterialClass.FLUID -> Color(0xFF38BDF8)
    MaterialClass.HARDWARE -> Color(0xFFFDE047)
    MaterialClass.INFORMATIONAL -> Color(0xFF94A3B8)
}

private fun Context.supportsFilament(): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
}
