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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.elysium369.meet.core.catalog.ProprietaryCatalogEntity
import com.elysium369.meet.core.catalog.ProprietaryCatalogManifest
import com.elysium369.meet.core.catalog.ProprietaryCatalogSystem
import com.elysium369.meet.core.catalog.ProprietaryPartsCatalogRepository
import com.elysium369.meet.core.catalog.ProprietarySourceBlock
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun ProprietaryPartsBrowser(
    navController: NavController,
    initialPartId: String?,
    onOpenGuidedPilot: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { ProprietaryPartsCatalogRepository(context) }
    val catalogResult = remember(repository) {
        runCatching { repository.loadManifest() to repository.loadEntityIndex() }
    }
    val manifest = catalogResult.getOrNull()?.first
    val index = catalogResult.getOrNull()?.second
    var query by remember { mutableStateOf("") }
    var selectedSystemId by remember { mutableStateOf<String?>(null) }
    var selectedEntity by remember(initialPartId, index) {
        mutableStateOf(index?.entities?.firstOrNull { it.id == initialPartId })
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF03070B))) {
        CatalogEnergyBackground()
        when {
            catalogResult.isFailure -> ProprietaryCatalogFailure(catalogResult.exceptionOrNull()?.message.orEmpty())
            manifest == null || index == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Validando base propietaria...", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
            }
            selectedEntity != null -> ProprietaryEntityDetail(
                manifest = manifest,
                entity = selectedEntity!!,
                blocks = remember(selectedEntity) {
                    runCatching { repository.literalContext(selectedEntity!!, maxBlocks = 360) }.getOrDefault(emptyList())
                },
                onBack = { selectedEntity = null },
                onOpen3d = { navController.navigate("component_locator?partId=${selectedEntity!!.id}") }
            )
            else -> ProprietaryCatalogList(
                manifest = manifest,
                entities = repository.search(query, selectedSystemId, includeRealCases = true, limit = 400),
                query = query,
                selectedSystemId = selectedSystemId,
                onBack = { navController.popBackStack() },
                onQueryChanged = { query = it },
                onSystemSelected = { selectedSystemId = it },
                onEntitySelected = { selectedEntity = it },
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
    query: String,
    selectedSystemId: String?,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSystemSelected: (String?) -> Unit,
    onEntitySelected: (ProprietaryCatalogEntity) -> Unit,
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

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MeetColors.cyberCyan) },
            placeholder = { Text("Buscar pieza, sensor, módulo o caso real", fontSize = 12.sp) },
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

        Text(
            if (entities.size == 400) "400 visibles · afine la búsqueda" else "${entities.size} resultados",
            color = MeetColors.textMuted,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(entities, key = { it.id }) { entity ->
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
    onBack: () -> Unit,
    onOpen3d: () -> Unit
) {
    val system = manifest.systems.firstOrNull { it.id == entity.systemId }
    val color = system?.color?.toCatalogColor() ?: MeetColors.cyberCyan
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
        }

        Text("INFORMACIÓN LITERAL", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(blocks, key = { it.blockId }) { block ->
                Column(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.34f)).border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(4.dp)).padding(10.dp)
                ) {
                    Text("${block.recordRole} · #${block.order} · SHA ${block.textHash.take(12)}", color = MeetColors.textMuted, fontSize = 7.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(block.text, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            item {
                Text("Fuente propietaria del usuario · ${entity.sourceFileName} · SHA-256 ${entity.sourceDocumentSha256}", color = MeetColors.textMuted, fontSize = 8.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
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
