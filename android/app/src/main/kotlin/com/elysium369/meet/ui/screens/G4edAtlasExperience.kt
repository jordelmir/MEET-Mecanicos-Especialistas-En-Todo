package com.elysium369.meet.ui.screens

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.catalog.G4edAtlasElement
import com.elysium369.meet.core.catalog.G4edAtlasSection
import com.elysium369.meet.core.catalog.G4edEngineAtlas
import com.elysium369.meet.core.catalog.G4edEngineAtlasEngine
import com.elysium369.meet.core.catalog.G4edEngineAtlasRepository
import com.elysium369.meet.core.catalog.G4edGeometryPolicy
import com.elysium369.meet.core.catalog.G4edAtlasStatistics
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlas
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptors
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasRepository
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.visual3d.domain.G4edAtlas3dBinding
import com.elysium369.meet.visual3d.domain.G4edAtlas3dCatalog
import com.elysium369.meet.visual3d.domain.G4edAtlas3dManifest
import com.elysium369.meet.visual3d.domain.G4edAtlas3dRepository
import com.elysium369.meet.visual3d.domain.VehicleTechnicalAtlas3dCatalog
import com.elysium369.meet.visual3d.domain.VehicleTechnicalAtlas3dRepository
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView

private val atlasCyan = Color(0xFF00F5D4)
private val atlasViolet = Color(0xFFB026FF)
private val atlasAmber = Color(0xFFFFB000)
private val atlasGlass = Color(0xC9111822)

private data class AtlasExperienceContent(
    val domainId: String,
    val displayName: String,
    val vehicleLabel: String,
    val geometryPolicy: G4edGeometryPolicy,
    val statistics: G4edAtlasStatistics,
    val sections: List<G4edAtlasSection>,
    val elements: List<G4edAtlasElement>,
)

private fun G4edEngineAtlas.experienceContent(): AtlasExperienceContent =
    AtlasExperienceContent(
        domainId = "g4ed",
        displayName = displayName,
        vehicleLabel = vehicleLabel,
        geometryPolicy = geometryPolicy,
        statistics = statistics,
        sections = sections,
        elements = elements,
    )

private fun VehicleTechnicalAtlas.experienceContent(): AtlasExperienceContent =
    AtlasExperienceContent(
        domainId = domainId,
        displayName = displayName,
        vehicleLabel = vehicleLabel,
        geometryPolicy = geometryPolicy,
        statistics = statistics,
        sections = sections,
        elements = elements,
    )

@Composable
fun G4edAtlasExperience(
    navController: NavController,
    initialPartId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val atlasResult = remember(context) { runCatching { G4edEngineAtlasRepository(context).atlas } }
    val atlas = atlasResult.getOrNull()?.experienceContent()
    var query by remember { mutableStateOf("") }
    var selectedSystem by remember { mutableStateOf<String?>(null) }
    var sellableOnly by remember { mutableStateOf(false) }
    var selectedElement by remember(atlas, initialPartId) {
        mutableStateOf(atlas?.elements?.firstOrNull { it.canonicalId == initialPartId })
    }

    when {
        atlasResult.isFailure -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No se pudo validar el Atlas G4ED: ${atlasResult.exceptionOrNull()?.message}",
                color = MeetColors.error,
                modifier = Modifier.padding(24.dp),
            )
        }
        atlas == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Validando 420 contratos...", color = atlasCyan)
        }
        selectedElement != null -> G4edElementDetail(
            atlas = atlas,
            element = selectedElement!!,
            navController = navController,
            onBack = { selectedElement = null },
            onOpenParent = { parentId ->
                selectedElement = atlas.elements.singleOrNull { it.canonicalId == parentId }
            },
        )
        else -> G4edAtlasBrowser(
            atlas = atlas,
            query = query,
            selectedSystem = selectedSystem,
            sellableOnly = sellableOnly,
            onQueryChanged = { query = it },
            onSystemChanged = { selectedSystem = it },
            onSellableChanged = { sellableOnly = it },
            onElementSelected = { selectedElement = it },
            onBack = onBack,
        )
    }
}

@Composable
fun VehicleTechnicalAtlasesExperience(
    navController: NavController,
    initialPartId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { VehicleTechnicalAtlasRepository(context) }
    val initialDomain = remember(initialPartId) {
        initialPartId?.let(VehicleTechnicalAtlasDescriptors::forCanonicalId)?.domainId
    }
    var selectedDomain by remember(initialDomain) { mutableStateOf(initialDomain) }
    val atlasResult = remember(repository, selectedDomain) {
        selectedDomain?.let { domain -> runCatching { repository.atlas(domain) } }
    }
    val atlas = atlasResult?.getOrNull()?.experienceContent()
    var query by remember(selectedDomain) { mutableStateOf("") }
    var selectedSystem by remember(selectedDomain) { mutableStateOf<String?>(null) }
    var sellableOnly by remember(selectedDomain) { mutableStateOf(false) }
    var selectedElement by remember(atlas, initialPartId) {
        mutableStateOf(atlas?.elements?.firstOrNull { it.canonicalId == initialPartId })
    }

    when {
        selectedDomain == null -> TechnicalAtlasSelector(
            onSelect = { selectedDomain = it },
            onBack = onBack,
        )
        atlasResult?.isFailure == true -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No se pudo validar el atlas: ${atlasResult.exceptionOrNull()?.message}",
                color = MeetColors.error,
                modifier = Modifier.padding(24.dp),
            )
        }
        atlas == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Validando contratos técnicos...", color = atlasCyan)
        }
        selectedElement != null -> G4edElementDetail(
            atlas = atlas,
            element = selectedElement!!,
            navController = navController,
            onBack = { selectedElement = null },
            onOpenParent = { parentId ->
                selectedElement = atlas.elements.singleOrNull { it.canonicalId == parentId }
            },
        )
        else -> G4edAtlasBrowser(
            atlas = atlas,
            query = query,
            selectedSystem = selectedSystem,
            sellableOnly = sellableOnly,
            onQueryChanged = { query = it },
            onSystemChanged = { selectedSystem = it },
            onSellableChanged = { sellableOnly = it },
            onElementSelected = { selectedElement = it },
            onBack = { selectedDomain = null },
        )
    }
}

@Composable
private fun TechnicalAtlasSelector(
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val cards = listOf(
        Triple("transmission_hydraulics", "TRANSMISIÓN + HIDRÁULICA", "838 elementos · 13 sistemas"),
        Triple("electrical", "SISTEMA ELÉCTRICO", "1.529 elementos · 34 sistemas"),
        Triple("body", "CARROCERÍA + INTERIOR", "1.665 elementos · 38 sistemas"),
        Triple("remaining_systems", "CHASIS + PERIFÉRICOS", "1.953 elementos · 25 sistemas"),
    )
    Column(
        Modifier.fillMaxSize().background(Color(0xFF03070B)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("ATLAS TÉCNICOS 3D", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("5.985 experiencias offline · 110 sistemas", color = atlasCyan, fontSize = 9.sp)
            }
            Text("360°", color = atlasViolet, fontWeight = FontWeight.Black)
        }
        AtlasGlassPanel("AUTORIDAD Y TRAZABILIDAD", atlasAmber) {
            Text(
                "Reconstrucciones técnicas de referencia enlazadas al corpus aportado. No sustituyen VIN, OEM, EPC, foto, conector ni medidas.",
                color = MeetColors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
        cards.forEachIndexed { index, (domainId, title, subtitle) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(atlasGlass, RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        if (index % 2 == 0) atlasCyan.copy(alpha = 0.48f) else atlasViolet.copy(alpha = 0.48f),
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(domainId) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtlasPartGlyph((index + 1) * 17, "EXPLODE_REASSEMBLE")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Text(subtitle, color = atlasCyan, fontSize = 9.sp)
                    Text("BÚSQUEDA · 3D · IA · DTC · REPUESTOS", color = atlasAmber, fontSize = 7.sp)
                }
                Icon(Icons.Default.ViewInAr, null, tint = atlasViolet)
            }
        }
    }
}

@Composable
private fun G4edAtlasBrowser(
    atlas: AtlasExperienceContent,
    query: String,
    selectedSystem: String?,
    sellableOnly: Boolean,
    onQueryChanged: (String) -> Unit,
    onSystemChanged: (String?) -> Unit,
    onSellableChanged: (Boolean) -> Unit,
    onElementSelected: (G4edAtlasElement) -> Unit,
    onBack: () -> Unit,
) {
    val sectionsBySystem = remember(atlas) { atlas.sections.associateBy { it.systemId } }
    val visible = remember(atlas, query, selectedSystem, sellableOnly) {
        G4edEngineAtlasEngine.search(
            elements = atlas.elements,
            query = query,
            systemId = selectedSystem,
            directlySellableOnly = sellableOnly,
        )
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF03070B))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(atlas.displayName.uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(atlas.vehicleLabel, color = atlasCyan, fontSize = 9.sp, maxLines = 1)
            }
            Box(
                Modifier
                    .background(atlasViolet.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .border(1.dp, atlasViolet.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text("${atlas.statistics.elementCount} · 3D", color = atlasViolet, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AtlasMetric(atlas.statistics.elementCount.toString(), "ELEMENTOS", atlasCyan, Modifier.weight(1f))
            AtlasMetric(atlas.statistics.sectionCount.toString(), "SISTEMAS", atlasViolet, Modifier.weight(1f))
            AtlasMetric(atlas.statistics.directlySellableCount.toString(), "REPUESTOS", atlasAmber, Modifier.weight(1f))
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = atlasCyan) },
            trailingIcon = if (query.isNotBlank()) {
                { IconButton(onClick = { onQueryChanged("") }) { Icon(Icons.Default.Close, "Limpiar") } }
            } else {
                null
            },
            placeholder = { Text("Cigüeñal, galería, conector, cárter...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AtlasChip("Todos", selectedSystem == null) { onSystemChanged(null) }
            atlas.sections.forEach { section ->
                AtlasChip(section.title, selectedSystem == section.systemId) {
                    onSystemChanged(section.systemId)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtlasChip("Solo repuestos", sellableOnly) { onSellableChanged(!sellableOnly) }
            Spacer(Modifier.weight(1f))
            Text("${visible.size} resultados · offline", color = MeetColors.textMuted, fontSize = 9.sp)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = G4edAtlasElement::canonicalId) { element ->
                val section = sectionsBySystem[element.systemId]
                G4edElementRow(element, section, onElementSelected)
            }
        }
    }
}

@Composable
private fun G4edElementRow(
    element: G4edAtlasElement,
    section: G4edAtlasSection?,
    onClick: (G4edAtlasElement) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .background(atlasGlass, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (element.commerce.directlySellable) atlasCyan.copy(alpha = 0.32f)
                else atlasViolet.copy(alpha = 0.36f),
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick(element) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtlasPartGlyph(element.ordinal, element.visual.animationMode)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "#${element.ordinal.toString().padStart(3, '0')} · ${element.nameOriginal}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(section?.title.orEmpty(), color = atlasCyan, fontSize = 8.sp, maxLines = 1)
            Text(
                if (element.commerce.directlySellable) "REPUESTO · 360°" else "REGIÓN INTEGRADA · 360°",
                color = if (element.commerce.directlySellable) atlasAmber else atlasViolet,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Icon(Icons.Default.ViewInAr, contentDescription = null, tint = atlasCyan, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun G4edElementDetail(
    atlas: AtlasExperienceContent,
    element: G4edAtlasElement,
    navController: NavController,
    onBack: () -> Unit,
    onOpenParent: (String) -> Unit,
) {
    val context = LocalContext.current
    val g4edRepository = remember(context) { G4edAtlas3dRepository(context) }
    val technicalRepository = remember(context) { VehicleTechnicalAtlas3dRepository(context) }
    val manifestResult = remember(atlas.domainId, element.visual.packId) {
        runCatching {
            if (atlas.domainId == "g4ed") {
                g4edRepository.manifest(element.visual.packId)
            } else {
                technicalRepository.manifest(atlas.domainId, element.visual.packId)
            }
        }
    }
    val manifest = manifestResult.getOrNull()
    val binding = remember(atlas.domainId, element, manifest) {
        manifest?.let {
            if (atlas.domainId == "g4ed") G4edAtlas3dCatalog.bindingFor(element, it)
            else VehicleTechnicalAtlas3dCatalog.bindingFor(element, it)
        }
    }
    val section = atlas.sections.single { it.systemId == element.systemId }
    val commerceElement = remember(element, atlas) {
        if (element.commerce.directlySellable) {
            element
        } else {
            atlas.elements.singleOrNull { it.canonicalId == element.parentCanonicalId }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF03070B))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(element.nameOriginal, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("#${element.ordinal} · ${section.title}", color = atlasCyan, fontSize = 9.sp)
            }
            Text("360°", color = atlasViolet, fontWeight = FontWeight.Black)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (manifest != null && binding != null) {
                    G4edPartViewer(manifest, binding)
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(320.dp).border(1.dp, MeetColors.error, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            manifestResult.exceptionOrNull()?.message ?: "Binding 3D no disponible",
                            color = MeetColors.error,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }

            item {
                AuthorityPanel(element, atlas.geometryPolicy.warning)
            }

            item {
                AtlasGlassPanel("CONOCIMIENTO VINCULADO", atlasCyan) {
                    Text(section.knowledge, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            item {
                AtlasGlassPanel("COMPARAR ANTES DE COMPRAR", atlasAmber) {
                    Text(
                        element.commerce.comparisonChecks.joinToString(" · "),
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Confirmar VIN, código de motor, OEM, foto, conector y medidas. La similitud visual no establece compatibilidad exacta.",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "POSICIÓN: ${element.applicability.side.replace('_', ' ')}",
                        color = atlasCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "CARROCERÍA: ${element.applicability.bodyStyleCondition.replace('_', ' ')}",
                        color = atlasCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (element.applicability.equipmentConditions.isNotEmpty()) {
                        Text(
                            "EQUIPAMIENTO: ${element.applicability.equipmentConditions.joinToString(" · ")}",
                            color = atlasViolet,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    element.normalization?.let { normalization ->
                        Text(
                            "OEM / CANTIDAD / SUPERSESIÓN: ${normalization.oemResolutionState.replace('_', ' ')}",
                            color = atlasAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { navController.navigate("dtc") },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.Warning, null)
                        Spacer(Modifier.width(6.dp))
                        Text("DTC", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            navController.navigate("ai?atlasPartId=${element.canonicalId}")
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.ViewInAr, null)
                        Spacer(Modifier.width(6.dp))
                        Text("IA CITADA", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!element.commerce.directlySellable && element.parentCanonicalId != null) {
                item {
                    OutlinedButton(
                        onClick = { onOpenParent(element.parentCanonicalId) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Default.Build, null)
                        Spacer(Modifier.width(8.dp))
                        Text("VER COMPONENTE PADRE")
                    }
                }
            }

            if (commerceElement != null) {
                item {
                    Button(
                        onClick = {
                            navController.navigate(
                                "part_request?vehicleInfo=&atlasPartId=${commerceElement.canonicalId}",
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = atlasCyan, contentColor = Color.Black),
                    ) {
                        Icon(Icons.Default.ShoppingCart, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (commerceElement == element) "PEDIR ESTE REPUESTO" else "PEDIR COMPONENTE PADRE",
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
internal fun G4edPartViewer(
    manifest: G4edAtlas3dManifest,
    binding: G4edAtlas3dBinding,
) {
    var isolate by remember(binding.ordinal) { mutableStateOf(true) }
    var exploded by remember(binding.ordinal) { mutableStateOf(false) }
    var autoRotate by remember(binding.ordinal) { mutableStateOf(true) }
    val transition = rememberInfiniteTransition(label = "g4ed-orbit")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000), repeatMode = RepeatMode.Restart),
        label = "g4ed-orbit-angle",
    )
    val engine = rememberEngine()
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    val modelLoader = rememberModelLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, manifest.assetPath)

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF071019), RoundedCornerShape(18.dp))
            .border(1.dp, atlasCyan.copy(alpha = 0.42f), RoundedCornerShape(18.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            if (modelInstance != null) {
                SceneView(
                    modifier = Modifier.fillMaxSize(),
                    engine = engine,
                    view = view,
                    collisionSystem = collisionSystem,
                    modelLoader = modelLoader,
                    renderQuality = RenderQuality.Default,
                    autoCenterContent = true,
                    autoFitContent = true,
                ) {
                    ModelNode(
                        modelInstance = modelInstance,
                        autoAnimate = false,
                        scaleToUnits = 2.1f,
                        centerOrigin = Position(0f, 0f, 0f),
                        rotation = Rotation(y = if (autoRotate) orbit else 0f),
                        apply = {
                            name = "g4ed_${binding.ordinal}"
                            isHittable = true
                            isTouchable = true
                            renderableNodes.forEachIndexed { index, node ->
                                val selected = G4edAtlas3dCatalog.isNodeForBinding(node.name, binding)
                                node.setLayerVisible(!isolate || selected)
                                node.isHittable = selected
                                node.isTouchable = selected
                                val direction = if (index % 2 == 0) 1f else -1f
                                node.position = if (exploded && selected) {
                                    Position(
                                        binding.explodeVector[0] * 0.12f * direction,
                                        binding.explodeVector[1] * 0.12f * direction,
                                        binding.explodeVector[2] * 0.12f * direction,
                                    )
                                } else {
                                    Position(0f, 0f, 0f)
                                }
                            }
                            onTouch = { event, _ -> event.action == MotionEvent.ACTION_UP }
                        },
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando paquete 3D offline...", color = atlasCyan)
                }
            }
            Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Text("RECONSTRUCCIÓN DE REFERENCIA", color = atlasAmber, fontSize = 8.sp, fontWeight = FontWeight.Black)
                Text("${manifest.meshCount} mallas · ${manifest.triangleCount} triángulos", color = MeetColors.textMuted, fontSize = 8.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ViewerToggle("AISLAR", isolate) { isolate = !isolate }
            ViewerToggle(if (exploded) "ENSAMBLAR" else "DESPIECE", exploded) { exploded = !exploded }
            ViewerToggle("AUTO 360", autoRotate) { autoRotate = !autoRotate }
            ViewerToggle("CONTEXTO", !isolate) { isolate = false }
            ViewerToggle("RESET", false) {
                isolate = true
                exploded = false
                autoRotate = false
            }
        }
    }
}

@Composable
private fun AtlasPartGlyph(ordinal: Int, mode: String) {
    Canvas(
        Modifier
            .size(50.dp)
            .background(atlasCyan.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .border(1.dp, atlasCyan.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val phase = (ordinal % 12) / 12f
        drawCircle(atlasCyan.copy(alpha = 0.18f), size.minDimension * 0.34f, center)
        drawCircle(atlasCyan, size.minDimension * (0.18f + phase * 0.05f), center, style = Stroke(2.dp.toPx()))
        if (mode.contains("ROTATIONAL")) {
            drawCircle(atlasViolet, size.minDimension * 0.3f, center, style = Stroke(1.dp.toPx()))
        } else {
            drawLine(atlasViolet, Offset(center.x - 12.dp.toPx(), center.y), Offset(center.x + 12.dp.toPx(), center.y), 2.dp.toPx())
        }
    }
}

@Composable
private fun AtlasMetric(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(color.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text(label, color = MeetColors.textMuted, fontSize = 7.sp)
    }
}

@Composable
private fun AtlasChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) atlasCyan.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) atlasCyan else MeetColors.borderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) atlasCyan else MeetColors.textSecondary, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun ViewerToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .background(if (active) atlasViolet.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .border(1.dp, if (active) atlasViolet else MeetColors.borderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) atlasViolet else MeetColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AuthorityPanel(element: G4edAtlasElement, warning: String) {
    AtlasGlassPanel("AUTORIDAD VISUAL", atlasAmber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = atlasAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (element.visual.authority == "SCHEMATIC_REGION") "REGIÓN ESQUEMÁTICA" else "RECONSTRUCCIÓN DE REFERENCIA",
                color = atlasAmber,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(warning, color = MeetColors.textSecondary, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun AtlasGlassPanel(
    title: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(atlasGlass, RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(title, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
