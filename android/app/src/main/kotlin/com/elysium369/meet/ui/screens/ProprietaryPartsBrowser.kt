package com.elysium369.meet.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.catalog.PROPRIETARY_VEHICLE_LABEL
import com.elysium369.meet.ai.ProprietaryGroundedContextBuilder
import com.elysium369.meet.core.catalog.ProprietaryCatalogEntity
import com.elysium369.meet.core.catalog.ProprietaryCatalogManifest
import com.elysium369.meet.core.catalog.ProprietaryCatalogSystem
import com.elysium369.meet.core.catalog.ProprietaryKnowledgeHit
import com.elysium369.meet.core.catalog.ProprietaryKnowledgeSearchRepository
import com.elysium369.meet.core.catalog.ProprietaryPartsCatalogRepository
import com.elysium369.meet.core.catalog.ProprietarySourceBlock
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptors
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ProprietaryPartsBrowser(
    navController: NavController,
    initialPartId: String?,
    onOpenGuidedPilot: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { ProprietaryPartsCatalogRepository(context) }
    val searchRepository = remember(context, repository) { ProprietaryKnowledgeSearchRepository(context, repository) }
    val groundedContextBuilder = remember { ProprietaryGroundedContextBuilder() }
    val catalogResult = remember(repository) {
        runCatching { repository.loadManifest() to repository.loadEntityIndex() }
    }
    val manifest = catalogResult.getOrNull()?.first
    val index = catalogResult.getOrNull()?.second
    var query by remember { mutableStateOf("") }
    var selectedSystemId by remember { mutableStateOf<String?>(null) }
    var selectedRole by remember { mutableStateOf(KnowledgeRoleFilter.ALL) }
    var knowledgeHits by remember { mutableStateOf<List<ProprietaryKnowledgeHit>>(emptyList()) }
    var searchInProgress by remember { mutableStateOf(false) }
    var searchFailure by remember { mutableStateOf<String?>(null) }
    var selectedStandaloneHit by remember { mutableStateOf<ProprietaryKnowledgeHit?>(null) }
    var showG4edAtlas by remember { mutableStateOf(initialPartId?.startsWith("g4ed-") == true) }
    var showTechnicalAtlases by remember {
        mutableStateOf(
            initialPartId?.let(VehicleTechnicalAtlasDescriptors::forCanonicalId) != null,
        )
    }
    var selectedEntity by remember(initialPartId, index) {
        mutableStateOf(index?.entities?.firstOrNull { it.id == initialPartId })
    }
    val entityById = remember(index) { index?.entities.orEmpty().associateBy { it.id } }
    val useLiteralSearch = query.isNotBlank() || selectedRole.blockOnly
    val showLiteralResults = useLiteralSearch && searchFailure == null
    val visibleEntities = remember(index, selectedSystemId, selectedRole, showLiteralResults, query) {
        if (showLiteralResults) emptyList() else index?.entities.orEmpty().asSequence()
            .filter { selectedSystemId == null || it.systemId == selectedSystemId }
            .filter { selectedRole.entityRole == null || it.recordRole == selectedRole.entityRole }
            .filter { query.isBlank() || it.nameOriginal.contains(query, ignoreCase = true) }
            .take(400)
            .toList()
    }

    LaunchedEffect(query, selectedSystemId, selectedRole, searchRepository) {
        if (!useLiteralSearch) {
            knowledgeHits = emptyList()
            searchInProgress = false
            searchFailure = null
            return@LaunchedEffect
        }
        searchInProgress = true
        searchFailure = null
        try {
            delay(180)
            knowledgeHits = withContext(Dispatchers.IO) {
                if (query.isBlank()) {
                    searchRepository.browse(selectedSystemId, selectedRole.roles, limit = 300)
                } else {
                    searchRepository.search(query, selectedSystemId, selectedRole.roles, limit = 300)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            knowledgeHits = emptyList()
            searchFailure = error.message ?: "No se pudo abrir el indice literal"
        } finally {
            searchInProgress = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF03070B))) {
        CatalogEnergyBackground()
        when {
            catalogResult.isFailure -> ProprietaryCatalogFailure(catalogResult.exceptionOrNull()?.message.orEmpty())
            manifest == null || index == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Validando base propietaria...", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
            }
            showG4edAtlas -> G4edAtlasExperience(
                navController = navController,
                initialPartId = initialPartId,
                onBack = { showG4edAtlas = false },
            )
            showTechnicalAtlases -> VehicleTechnicalAtlasesExperience(
                navController = navController,
                initialPartId = initialPartId,
                onBack = { showTechnicalAtlases = false },
            )
            selectedEntity != null -> {
                val activeEntity = selectedEntity!!
                val literalBlocks = remember(activeEntity) {
                    runCatching { repository.literalContext(activeEntity, maxBlocks = Int.MAX_VALUE) }.getOrDefault(emptyList())
                }
                ProprietaryEntityDetail(
                    manifest = manifest,
                    entity = activeEntity,
                    blocks = literalBlocks,
                    groundedAiContext = remember(activeEntity, literalBlocks) {
                        groundedContextBuilder.buildReadableBrief(activeEntity, literalBlocks)
                    },
                    onBack = { selectedEntity = null },
                    onOpen3d = { navController.navigate("component_locator?partId=${activeEntity.id}") }
                )
            }
            selectedStandaloneHit != null -> ProprietaryStandaloneBlockDetail(
                manifest = manifest,
                hit = selectedStandaloneHit!!,
                linkedEntity = selectedStandaloneHit!!.linkedEntityId?.let(entityById::get),
                onBack = { selectedStandaloneHit = null },
                onOpenLinkedEntity = { entity ->
                    selectedStandaloneHit = null
                    selectedEntity = entity
                }
            )
            else -> ProprietaryCatalogList(
                manifest = manifest,
                entities = visibleEntities,
                knowledgeHits = knowledgeHits,
                useLiteralSearch = showLiteralResults,
                searchInProgress = searchInProgress,
                searchFailure = searchFailure,
                query = query,
                selectedSystemId = selectedSystemId,
                selectedRole = selectedRole,
                onBack = { navController.popBackStack() },
                onQueryChanged = { query = it },
                onSystemSelected = { selectedSystemId = it },
                onRoleSelected = { selectedRole = it },
                onEntitySelected = { selectedEntity = it },
                onKnowledgeHitSelected = { hit ->
                    val directEntity = hit.entityId?.let(entityById::get)
                        ?.takeIf { hit.recordRole == "COMPONENT" || hit.recordRole == "REAL_CASE" }
                    if (directEntity != null) selectedEntity = directEntity else selectedStandaloneHit = hit
                },
                onOpenG4edAtlas = { showG4edAtlas = true },
                onOpenTechnicalAtlases = { showTechnicalAtlases = true },
                onOpenGuidedPilot = onOpenGuidedPilot
            )
        }
    }
}

@Composable
private fun CatalogEnergyBackground() {
    val transition = rememberInfiniteTransition(label = "catalogEnergy")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_800), repeatMode = RepeatMode.Restart),
        label = "catalogScan"
    )
    Canvas(Modifier.fillMaxSize()) {
        val grid = 42.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(MeetColors.cyberCyan.copy(alpha = 0.045f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += grid
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(MeetColors.neonGreen.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f)
            y += grid
        }
        val scanY = size.height * phase
        drawLine(MeetColors.cyberCyan.copy(alpha = 0.55f), Offset(0f, scanY), Offset(size.width, scanY), 2.dp.toPx())
        drawLine(MeetColors.neonGreen.copy(alpha = 0.12f), Offset(0f, scanY + 14.dp.toPx()), Offset(size.width, scanY + 14.dp.toPx()), 8.dp.toPx())
    }
}

@Composable
private fun ProprietaryCatalogList(
    manifest: ProprietaryCatalogManifest,
    entities: List<ProprietaryCatalogEntity>,
    knowledgeHits: List<ProprietaryKnowledgeHit>,
    useLiteralSearch: Boolean,
    searchInProgress: Boolean,
    searchFailure: String?,
    query: String,
    selectedSystemId: String?,
    selectedRole: KnowledgeRoleFilter,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSystemSelected: (String?) -> Unit,
    onRoleSelected: (KnowledgeRoleFilter) -> Unit,
    onEntitySelected: (ProprietaryCatalogEntity) -> Unit,
    onKnowledgeHitSelected: (ProprietaryKnowledgeHit) -> Unit,
    onOpenG4edAtlas: () -> Unit,
    onOpenTechnicalAtlases: () -> Unit,
    onOpenGuidedPilot: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Piezas", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(PROPRIETARY_VEHICLE_LABEL, color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onOpenGuidedPilot) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Taller", fontSize = 10.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CatalogMetric("${manifest.statistics.entityCount}", "PIEZAS", MeetColors.neonGreen, Modifier.weight(1f))
            CatalogMetric("${manifest.statistics.realCaseCount}", "CASOS REALES", MeetColors.warning, Modifier.weight(1f))
            CatalogMetric("${manifest.statistics.blockCount}", "BLOQUES", MeetColors.cyberCyan, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .background(MeetColors.cyberCyan.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenG4edAtlas)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MeetColors.neonGreen.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = MeetColors.neonGreen,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "ATLAS G4ED · 420 EXPERIENCIAS 3D",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                )
                Text(
                    "Piezas, regiones internas y repuestos · offline · 360°",
                    color = MeetColors.cyberCyan,
                    fontSize = 9.sp,
                )
            }
            Text("ABRIR", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .background(MeetColors.electricBlue.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenTechnicalAtlases)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MeetColors.electricBlue.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ViewInAr, null, tint = MeetColors.electricBlue, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "ATLAS TÉCNICOS · 5.985 EXPERIENCIAS 3D",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                )
                Text(
                    "Transmisión · eléctrico · carrocería · chasis y periféricos",
                    color = MeetColors.electricBlue,
                    fontSize = 9.sp,
                )
            }
            Text("ABRIR", color = MeetColors.electricBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MeetColors.cyberCyan) },
            placeholder = { Text("Buscar en piezas, detalles, tablas y casos", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(6.dp)
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SystemChip(null, "Todos", selectedSystemId == null, MeetColors.cyberCyan, onSystemSelected)
            manifest.systems.forEach { system ->
                SystemChip(system.id, "${system.title} · ${system.entityCount}", selectedSystemId == system.id, system.color.toCatalogColor(), onSystemSelected)
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KnowledgeRoleFilter.entries.forEach { role ->
                RoleChip(role, selectedRole == role, onRoleSelected)
            }
        }

        Text(
            when {
                searchInProgress -> "Buscando dentro de 74.648 bloques..."
                searchFailure != null -> "Indice no disponible · $searchFailure"
                useLiteralSearch && knowledgeHits.size == 300 -> "300 coincidencias visibles · afine la búsqueda"
                useLiteralSearch -> "${knowledgeHits.size} coincidencias literales"
                entities.size == 400 -> "400 entidades visibles · use búsqueda o filtros"
                else -> "${entities.size} entidades"
            },
            color = if (searchFailure == null) MeetColors.textMuted else MeetColors.error,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (useLiteralSearch) {
                items(knowledgeHits, key = { "${it.sourceDocumentId}:${it.blockId}" }) { hit ->
                    KnowledgeHitRow(manifest, hit, onKnowledgeHitSelected)
                }
            } else items(entities, key = { it.id }) { entity ->
                val system = manifest.systems.firstOrNull { it.id == entity.systemId }
                val color = system?.color?.toCatalogColor() ?: MeetColors.cyberCyan
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
                        .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .clickable { onEntitySelected(entity) }
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(34.dp).border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (entity.recordRole == "REAL_CASE") Icons.Default.CheckCircle else Icons.Default.ViewInAr,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entity.nameOriginal, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${system?.title.orEmpty()} · ${entity.sourceFileName} #${entity.sourceOrder}", color = MeetColors.textMuted, fontSize = 8.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProprietaryEntityDetail(
    manifest: ProprietaryCatalogManifest,
    entity: ProprietaryCatalogEntity,
    blocks: List<ProprietarySourceBlock>,
    groundedAiContext: String,
    onBack: () -> Unit,
    onOpen3d: () -> Unit
) {
    val system = manifest.systems.firstOrNull { it.id == entity.systemId }
    val color = system?.color?.toCatalogColor() ?: MeetColors.cyberCyan
    var showAiContext by remember(entity.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(system?.title.orEmpty(), color = color, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(entity.nameOriginal, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        Box(
            Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            HolographicEntityGlyph(entity, color)
            Button(
                onClick = onOpen3d,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.22f), contentColor = color)
            ) {
                Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("MOTOR 3D", fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = { showAiContext = !showAiContext },
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            ) {
                Text(if (showAiContext) "CERRAR ANALISIS" else "ANALISIS CITADO", fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
        }

        Text("INFORMACIÓN LITERAL", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp))
        AnimatedVisibility(visible = showAiContext) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .heightIn(max = 180.dp)
                    .background(MeetColors.neonGreen.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.42f), RoundedCornerShape(4.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text("CONOCIMIENTO PROPIETARIO CON EVIDENCIA", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(
                    groundedAiContext,
                    color = MeetColors.textSecondary,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(blocks, key = { it.blockId }) { block -> LiteralBlockCard(block, color) }
            item {
                Text("Fuente propietaria del usuario · ${entity.sourceFileName} · SHA-256 ${entity.sourceDocumentSha256}", color = MeetColors.textMuted, fontSize = 8.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ProprietaryStandaloneBlockDetail(
    manifest: ProprietaryCatalogManifest,
    hit: ProprietaryKnowledgeHit,
    linkedEntity: ProprietaryCatalogEntity?,
    onBack: () -> Unit,
    onOpenLinkedEntity: (ProprietaryCatalogEntity) -> Unit
) {
    val system = manifest.systems.firstOrNull { it.id == hit.systemId }
    val color = system?.color?.toCatalogColor() ?: MeetColors.cyberCyan
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(system?.title.orEmpty(), color = color, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(hit.sectionTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 2)
            }
            if (linkedEntity != null) {
                IconButton(onClick = { onOpenLinkedEntity(linkedEntity) }) {
                    Icon(Icons.Default.ViewInAr, contentDescription = "Abrir pieza relacionada", tint = color)
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item { LiteralBlockCard(hit.asSourceBlock(), color) }
            if (linkedEntity != null) item {
                OutlinedButton(
                    onClick = { onOpenLinkedEntity(linkedEntity) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ABRIR PIEZA RELACIONADA", fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            }
            item {
                Text(
                    "${hit.sourceFileName} · bloque #${hit.sourceOrder} · SHA-256 ${hit.textHash}",
                    color = MeetColors.textMuted,
                    fontSize = 8.sp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun LiteralBlockCard(block: ProprietarySourceBlock, color: Color) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.34f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Text(
            "${block.recordRole} · #${block.order} · SHA ${block.textHash.take(12)}",
            color = MeetColors.textMuted,
            fontSize = 7.sp
        )
        Spacer(Modifier.height(4.dp))
        if (block.kind == "table" && !block.rows.isNullOrEmpty()) {
            LiteralTable(block.rows, color)
        } else {
            Text(block.text, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun LiteralTable(rows: List<List<String>>, color: Color) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        rows.forEachIndexed { rowIndex, row ->
            Row {
                row.forEach { cell ->
                    Box(
                        Modifier.widthIn(min = 116.dp, max = 260.dp)
                            .background(if (rowIndex == 0) color.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.18f))
                            .border(0.5.dp, color.copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cell,
                            color = if (rowIndex == 0) Color.White else MeetColors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeHitRow(
    manifest: ProprietaryCatalogManifest,
    hit: ProprietaryKnowledgeHit,
    onSelected: (ProprietaryKnowledgeHit) -> Unit
) {
    val system = manifest.systems.firstOrNull { it.id == hit.systemId }
    val color = system?.color?.toCatalogColor() ?: MeetColors.cyberCyan
    Row(
        Modifier.fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .clickable { onSelected(hit) }
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(hit.recordRole.take(1), color = color, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                "${hit.recordRole} · ${system?.title.orEmpty()} · ${hit.sourceFileName} #${hit.sourceOrder}",
                color = MeetColors.textMuted,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HolographicEntityGlyph(entity: ProprietaryCatalogEntity, color: Color) {
    val transition = rememberInfiniteTransition(label = "entityGlyph")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "entityPulse"
    )
    Canvas(Modifier.size(130.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        repeat(5) { ring ->
            drawCircle(color.copy(alpha = (0.12f + ring * 0.05f) * pulse), radius = (18f + ring * 11f) * density, center = center)
        }
        val seed = entity.threeDimensionalBinding.seed
        repeat(8) { index ->
            val span = 22.dp.toPx() + ((seed shr (index % 12)) and 31).toFloat()
            val y = center.y - 42.dp.toPx() + index * 12.dp.toPx()
            drawLine(color.copy(alpha = pulse), Offset(center.x - span, y), Offset(center.x + span, y), 2.dp.toPx())
        }
    }
}

@Composable
private fun CatalogMetric(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(4.dp)).padding(8.dp)) {
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text(label, color = MeetColors.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SystemChip(id: String?, label: String, selected: Boolean, color: Color, onSelected: (String?) -> Unit) {
    Box(
        Modifier.background(if (selected) color.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) color else MeetColors.borderSubtle, RoundedCornerShape(6.dp))
            .clickable { onSelected(id) }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) color else MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private enum class KnowledgeRoleFilter(
    val label: String,
    val roles: Set<String>,
    val entityRole: String? = null,
    val blockOnly: Boolean = false
) {
    ALL("Todo", emptySet()),
    COMPONENTS("Piezas", setOf("COMPONENT"), entityRole = "COMPONENT"),
    REAL_CASES("Casos reales", setOf("REAL_CASE"), entityRole = "REAL_CASE"),
    TABLES("Tablas", setOf("TABLE"), blockOnly = true),
    DETAILS("Detalles", setOf("SOURCE_DETAIL"), blockOnly = true)
}

@Composable
private fun RoleChip(
    role: KnowledgeRoleFilter,
    selected: Boolean,
    onSelected: (KnowledgeRoleFilter) -> Unit
) {
    val color = when (role) {
        KnowledgeRoleFilter.ALL -> MeetColors.cyberCyan
        KnowledgeRoleFilter.COMPONENTS -> MeetColors.neonGreen
        KnowledgeRoleFilter.REAL_CASES -> MeetColors.warning
        KnowledgeRoleFilter.TABLES -> MeetColors.electricBlue
        KnowledgeRoleFilter.DETAILS -> MeetColors.textSecondary
    }
    Box(
        Modifier.background(if (selected) color.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) color else MeetColors.borderSubtle, RoundedCornerShape(6.dp))
            .clickable { onSelected(role) }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(role.label, color = if (selected) color else MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProprietaryCatalogFailure(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(10.dp))
        Text("La base propietaria no superó la validación", color = Color.White, fontWeight = FontWeight.Bold)
        Text(message, color = MeetColors.error, fontSize = 10.sp)
    }
}

private fun String.toCatalogColor(): Color = runCatching {
    Color(0xFF000000L or removePrefix("#").toLong(16))
}.getOrDefault(MeetColors.cyberCyan)
