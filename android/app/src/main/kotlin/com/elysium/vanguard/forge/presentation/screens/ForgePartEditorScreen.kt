package com.elysium.vanguard.forge.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.forge.domain.FeatureType
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.MaterialSpec
import com.elysium.vanguard.forge.domain.ParametricFeature
import com.elysium.vanguard.forge.domain.SafetyClassification
import com.elysium.vanguard.forge.engine.ForgeGeometryCompiler
import com.elysium.vanguard.forge.presentation.components.NeonCard
import com.elysium.vanguard.forge.presentation.components.ProvenanceBadge
import com.elysium.vanguard.forge.presentation.components.SectionHeader
import com.elysium.vanguard.forge.presentation.components.SeverityBadge
import com.elysium.vanguard.forge.presentation.components.TechLabel
import com.elysium.vanguard.forge.presentation.components.UiState
import com.elysium.vanguard.forge.presentation.theme.ForgeColors
import com.elysium.vanguard.forge.presentation.viewmodels.DimensionField
import com.elysium.vanguard.forge.presentation.viewmodels.ForgePartEditorEvent
import com.elysium.vanguard.forge.presentation.viewmodels.ForgePartEditorViewModel

/**
 * ForgePartEditorScreen — editor paramétrico de pieza.
 *
 * Layout: viewport 3D placeholder + árbol de features + parámetros + material + validación.
 */
@Composable
fun ForgePartEditorScreen(
    viewModel: ForgePartEditorViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val materials by viewModel.materials.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is UiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CARGANDO PIEZA",
                    color = ForgeColors.Primary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            }
            is UiState.Empty -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Sin pieza cargada", color = ForgeColors.OnSurface.copy(alpha = 0.5f))
            }
            is UiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(state.message, color = ForgeColors.Error)
            }
            is UiState.Ready -> EditorContent(
                part = state.data,
                materials = materials,
                onEvent = viewModel::onEvent,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun EditorContent(
    part: ForgePart,
    materials: List<MaterialSpec>,
    onEvent: (ForgePartEditorEvent) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            HeaderBar(part = part, onBack = onBack, onEvent = onEvent)
            Spacer(Modifier.height(16.dp))
        }
        item {
            Viewport3DPlaceholder(part = part)
            Spacer(Modifier.height(16.dp))
        }
        item {
            SafetyClassificationPicker(
                current = part.artifact.safetyClassification,
                onChange = { sc -> onEvent(ForgePartEditorEvent.OnSetSafetyClassification(sc)) }
            )
            Spacer(Modifier.height(16.dp))
        }
        item {
            SectionHeader("DIMENSIONES")
        }
        items(count = DimensionField.values().size) { index ->
            val field = DimensionField.values()[index]
            DimensionRow(
                field = field,
                valueMm = readDimension(part, field),
                onChange = { v -> onEvent(ForgePartEditorEvent.OnUpdateDimension(field, v)) }
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("MATERIAL")
            MaterialPicker(
                currentId = part.materialId,
                materials = materials,
                onChange = { onEvent(ForgePartEditorEvent.OnAssignMaterial(it)) }
            )
            Spacer(Modifier.height(16.dp))
            SectionHeader("FEATURES")
        }
        items(count = part.featureTree.size) { index ->
            FeatureRow(feature = part.featureTree[index])
        }
        item {
            AddFeatureButton(onAdd = { type ->
                val newFeature = ParametricFeature(
                    id = "f_${System.currentTimeMillis()}",
                    type = type,
                    name = type.name
                )
                onEvent(ForgePartEditorEvent.OnAddFeature(newFeature))
            })
            Spacer(Modifier.height(16.dp))
        }
        item {
            ActionButtons(onEvent = onEvent)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HeaderBar(part: ForgePart, onBack: () -> Unit, onEvent: (ForgePartEditorEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = ForgeColors.SurfaceVariant,
                contentColor = ForgeColors.OnSurface
            )
        ) { Text("← BACK") }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            var name by remember { mutableStateOf(part.artifact.name) }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onEvent(ForgePartEditorEvent.OnRename(it))
                },
                singleLine = true,
                label = { Text("Nombre", fontSize = 11.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Viewport3DPlaceholder(part: ForgePart) {
    // Compile mesh from the current part definition
    val compiler = remember { ForgeGeometryCompiler() }
    val compileResult = remember(part.featureTree, part.dimensions, part.materialId) {
        compiler.compilePart(part)
    }

    // Camera state
    var yaw by remember { mutableStateOf(-0.6f) }
    var pitch by remember { mutableStateOf(-0.45f) }
    var autoRotate by remember { mutableStateOf(true) }

    // Auto-rotation animation (pauses when user interacts)
    var animTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(autoRotate) {
        if (!autoRotate) return@LaunchedEffect
        val startYaw = yaw
        val startMs = System.currentTimeMillis()
        while (autoRotate) {
            val elapsed = (System.currentTimeMillis() - startMs) / 1000f
            yaw = startYaw + elapsed * 0.25f
            animTime = elapsed
            kotlinx.coroutines.delay(32L)
        }
    }

    NeonCard(modifier = Modifier.fillMaxWidth().height(260.dp), accentColor = ForgeColors.Accent) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!compileResult.isValid || compileResult.mesh.isEmpty) {
                // Fallback: show warnings if compile failed
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "⚠ MESH NO COMPILABLE",
                        color = ForgeColors.Warning,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    compileResult.errors.take(2).forEach { err ->
                        Text(err, color = ForgeColors.Error, fontSize = 10.sp, maxLines = 2)
                    }
                    compileResult.warnings.take(2).forEach { warn ->
                        Text(warn, color = ForgeColors.Warning.copy(alpha = 0.7f), fontSize = 9.sp, maxLines = 1)
                    }
                }
            } else {
                // Real-time 3D Canvas renderer
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, _, _ ->
                                autoRotate = false
                                yaw += pan.x * 0.008f
                                pitch = (pitch + pan.y * 0.006f).coerceIn(-1.2f, 0.4f)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h * 0.52f
                    val focalLength = kotlin.math.min(w, h) * 1.4f
                    val camDist = 350f
                    val zoom = 1.0f

                    // Background subtle radial gradient effect
                    drawCircle(
                        color = ForgeColors.Accent.copy(alpha = 0.04f),
                        radius = kotlin.math.min(w, h) * 0.55f,
                        center = androidx.compose.ui.geometry.Offset(cx, cy)
                    )

                    // ── Ground grid ──
                    val gridColor = ForgeColors.Accent.copy(alpha = 0.06f)
                    val gridRange = 5
                    val gridStep = 30f
                    for (i in -gridRange..gridRange) {
                        val z = i * gridStep
                        val p1 = project3D(-gridRange * gridStep, 80f, z, yaw, pitch, zoom, cx, cy, focalLength, camDist)
                        val p2 = project3D(gridRange * gridStep, 80f, z, yaw, pitch, zoom, cx, cy, focalLength, camDist)
                        if (p1 != null && p2 != null) drawLine(gridColor, p1, p2, 0.8f)
                        val x = i * gridStep
                        val p3 = project3D(x, 80f, -gridRange * gridStep, yaw, pitch, zoom, cx, cy, focalLength, camDist)
                        val p4 = project3D(x, 80f, gridRange * gridStep, yaw, pitch, zoom, cx, cy, focalLength, camDist)
                        if (p3 != null && p4 != null) drawLine(gridColor, p3, p4, 0.8f)
                    }

                    // ── Mesh rendering ──
                    val mesh = compileResult.mesh
                    val verts = mesh.vertices

                    // Light direction
                    val lx = 0.5f; val ly = -1.0f; val lz = 0.5f
                    val ll = kotlin.math.sqrt(lx * lx + ly * ly + lz * lz)
                    val ldx = lx / ll; val ldy = ly / ll; val ldz = lz / ll

                    // Pre-project all vertices
                    data class ProjVert(val offset: androidx.compose.ui.geometry.Offset, val depth: Float)
                    val projVerts = verts.map { v ->
                        val rx = v.x * kotlin.math.cos(yaw) + v.z * kotlin.math.sin(yaw)
                        val rz = -v.x * kotlin.math.sin(yaw) + v.z * kotlin.math.cos(yaw)
                        val ry1 = v.y * kotlin.math.cos(pitch) - rz * kotlin.math.sin(pitch)
                        val rz1 = v.y * kotlin.math.sin(pitch) + rz * kotlin.math.cos(pitch)
                        val zDepth = rz1 + camDist
                        if (zDepth < 10f) null
                        else {
                            val px = cx + (rx * zoom * focalLength) / zDepth
                            val py = cy + (ry1 * zoom * focalLength) / zDepth
                            ProjVert(androidx.compose.ui.geometry.Offset(px, py), zDepth)
                        }
                    }

                    // Collect and sort faces by depth (painter's algorithm)
                    data class SortedFace(val path: Path, val depth: Float, val color: Color, val edgeColor: Color)
                    val sortedFaces = mutableListOf<SortedFace>()

                    mesh.faces.forEach { face ->
                        val pA = projVerts.getOrNull(face.a) ?: return@forEach
                        val pB = projVerts.getOrNull(face.b) ?: return@forEach
                        val pC = projVerts.getOrNull(face.c) ?: return@forEach

                        val vA = verts[face.a]; val vB = verts[face.b]; val vC = verts[face.c]

                        // Normal via cross product
                        val e1x = vB.x - vA.x; val e1y = vB.y - vA.y; val e1z = vB.z - vA.z
                        val e2x = vC.x - vA.x; val e2y = vC.y - vA.y; val e2z = vC.z - vA.z
                        val nx = e1y * e2z - e1z * e2y
                        val ny = e1z * e2x - e1x * e2z
                        val nz = e1x * e2y - e1y * e2x
                        val nl = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
                        val intensity = if (nl > 0.001f) {
                            kotlin.math.max(0.15f, (nx / nl) * ldx + (ny / nl) * ldy + (nz / nl) * ldz)
                        } else 0.3f

                        val baseR = vA.r; val baseG = vA.g; val baseB = vA.b
                        val litColor = Color(
                            red = (baseR * (intensity * 0.7f + 0.3f)).coerceIn(0f, 1f),
                            green = (baseG * (intensity * 0.7f + 0.3f)).coerceIn(0f, 1f),
                            blue = (baseB * (intensity * 0.7f + 0.3f)).coerceIn(0f, 1f),
                            alpha = vA.a
                        )
                        val avgDepth = (pA.depth + pB.depth + pC.depth) / 3f

                        val path = Path().apply {
                            moveTo(pA.offset.x, pA.offset.y)
                            lineTo(pB.offset.x, pB.offset.y)
                            lineTo(pC.offset.x, pC.offset.y)
                            close()
                        }

                        sortedFaces.add(SortedFace(
                            path, avgDepth, litColor,
                            ForgeColors.Accent.copy(alpha = 0.12f)
                        ))
                    }

                    // Draw back-to-front
                    sortedFaces.sortByDescending { it.depth }
                    sortedFaces.forEach { face ->
                        drawPath(face.path, face.color)
                        drawPath(face.path, face.edgeColor, style = Stroke(width = 0.6f))
                    }

                    // ── Axis indicators (bottom-left corner) ──
                    val axisLen = 28f
                    val axisOrigin = androidx.compose.ui.geometry.Offset(50f, h - 40f)
                    val xEnd = project3DAxis(axisLen, 0f, 0f, yaw, pitch)
                    val yEnd = project3DAxis(0f, -axisLen, 0f, yaw, pitch)
                    val zEnd = project3DAxis(0f, 0f, axisLen, yaw, pitch)
                    drawLine(Color.Red.copy(alpha = 0.6f), axisOrigin, axisOrigin + xEnd, 2f)
                    drawLine(Color.Green.copy(alpha = 0.6f), axisOrigin, axisOrigin + yEnd, 2f)
                    drawLine(Color(0xFF4488FF).copy(alpha = 0.6f), axisOrigin, axisOrigin + zEnd, 2f)
                }

                // HUD overlay: mesh stats
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${compileResult.mesh.vertices.size}v · ${compileResult.mesh.faces.size}f",
                        color = ForgeColors.Accent,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    val dims = part.dimensions
                    Text(
                        "${dims.lengthMm?.toInt() ?: 0}×${dims.widthMm?.toInt() ?: 0}×${dims.heightMm?.toInt() ?: 0}mm",
                        color = ForgeColors.OnSurface.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Warnings badge (if fallback was used or warnings exist)
                if (compileResult.usedFallback || compileResult.warnings.isNotEmpty()) {
                    Text(
                        text = if (compileResult.usedFallback) "⚠ FALLBACK" else "⚠ ${compileResult.warnings.size} avisos",
                        color = ForgeColors.Warning,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Drag hint (fades after auto-rotate stops)
                if (autoRotate) {
                    Text(
                        text = "⟲ ARRASTRE PARA ROTAR",
                        color = ForgeColors.OnSurface.copy(alpha = 0.3f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

/** Projects a 3D point to 2D screen coordinates, returns null if behind camera. */
private fun project3D(
    x: Float, y: Float, z: Float,
    yaw: Float, pitch: Float, zoom: Float,
    cx: Float, cy: Float,
    focalLength: Float, camDist: Float
): androidx.compose.ui.geometry.Offset? {
    val rx = x * kotlin.math.cos(yaw) + z * kotlin.math.sin(yaw)
    val rz = -x * kotlin.math.sin(yaw) + z * kotlin.math.cos(yaw)
    val ry1 = y * kotlin.math.cos(pitch) - rz * kotlin.math.sin(pitch)
    val rz1 = y * kotlin.math.sin(pitch) + rz * kotlin.math.cos(pitch)
    val depth = rz1 + camDist
    if (depth < 10f) return null
    return androidx.compose.ui.geometry.Offset(
        cx + (rx * zoom * focalLength) / depth,
        cy + (ry1 * zoom * focalLength) / depth
    )
}

/** Projects a 3D direction to a 2D offset (for axis indicators). */
private fun project3DAxis(x: Float, y: Float, z: Float, yaw: Float, pitch: Float): androidx.compose.ui.geometry.Offset {
    val rx = x * kotlin.math.cos(yaw) + z * kotlin.math.sin(yaw)
    val rz = -x * kotlin.math.sin(yaw) + z * kotlin.math.cos(yaw)
    val ry1 = y * kotlin.math.cos(pitch) - rz * kotlin.math.sin(pitch)
    return androidx.compose.ui.geometry.Offset(rx, ry1)
}

@Composable
private fun SafetyClassificationPicker(
    current: SafetyClassification,
    onChange: (SafetyClassification) -> Unit
) {
    Column {
        SectionHeader("CLASIFICACIÓN DE SEGURIDAD")
        Spacer(Modifier.height(8.dp))
        NeonCard(modifier = Modifier.fillMaxWidth(), accentColor = safetyColor(current)) {
            Column {
                Text(
                    text = current.displayName,
                    color = safetyColor(current),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SafetyClassification.values().forEach { sc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onChange(sc) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sc == current) safetyColor(sc).copy(alpha = 0.3f) else Color.Transparent,
                                contentColor = safetyColor(sc)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(sc.displayName, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionRow(
    field: DimensionField,
    valueMm: Double?,
    onChange: (Double) -> Unit
) {
    var text by remember(valueMm) { mutableStateOf(valueMm?.let { "%.1f".format(it) } ?: "") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = field.displayName,
            color = ForgeColors.OnSurface,
            fontSize = 13.sp,
            modifier = Modifier.width(140.dp)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                val v = it.replace(',', '.').toDoubleOrNull()
                if (v != null) onChange(v)
            },
            singleLine = true,
            suffix = { Text("mm", fontSize = 11.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MaterialPicker(
    currentId: String?,
    materials: List<MaterialSpec>,
    onChange: (String) -> Unit
) {
    if (materials.isEmpty()) {
        Text(
            text = "Cargando materiales…",
            color = ForgeColors.OnSurface.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        return
    }
    Column {
        materials.take(8).forEach { m ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onChange(m.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (m.id == currentId) ForgeColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = if (m.id == currentId) ForgeColors.Primary else ForgeColors.OnSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.displayName, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${m.densityKgM3.toInt()}kg/m³ · ${m.yieldStrengthMPa.toInt()}MPa",
                            fontSize = 10.sp,
                            color = ForgeColors.OnSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: ParametricFeature) {
    NeonCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        accentColor = if (feature.type.supportedV1) ForgeColors.Success else ForgeColors.Warning
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (feature.type.supportedV1) ForgeColors.Success else ForgeColors.Warning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(feature.name.ifBlank { feature.type.name }, fontSize = 12.sp, color = ForgeColors.OnSurface)
                Text(
                    "id=${feature.id} · ${feature.operation.name}",
                    fontSize = 10.sp,
                    color = ForgeColors.OnSurface.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace
                )
            }
            if (!feature.type.supportedV1) {
                SeverityBadge("MARK V1", ForgeColors.Warning)
            }
        }
    }
}

@Composable
private fun AddFeatureButton(onAdd: (FeatureType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Button(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.buttonColors(
                containerColor = ForgeColors.Secondary.copy(alpha = 0.2f),
                contentColor = ForgeColors.Secondary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "Cancelar" else "Agregar feature")
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            FeatureType.values().filter { it.supportedV1 }.forEach { type ->
                Button(
                    onClick = {
                        onAdd(type)
                        expanded = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForgeColors.Surface,
                        contentColor = ForgeColors.OnSurface
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("+ ${type.name}", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(onEvent: (ForgePartEditorEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onEvent(ForgePartEditorEvent.OnValidatePart) },
            colors = ButtonDefaults.buttonColors(
                containerColor = ForgeColors.Accent.copy(alpha = 0.2f),
                contentColor = ForgeColors.Accent
            ),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Verified, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Validar")
        }
        Button(
            onClick = { onEvent(ForgePartEditorEvent.OnSavePart) },
            colors = ButtonDefaults.buttonColors(
                containerColor = ForgeColors.Primary.copy(alpha = 0.2f),
                contentColor = ForgeColors.Primary
            ),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Guardar")
        }
    }
}

private fun readDimension(part: ForgePart, field: DimensionField): Double? = when (field) {
    DimensionField.LENGTH -> part.dimensions.lengthMm
    DimensionField.WIDTH -> part.dimensions.widthMm
    DimensionField.HEIGHT -> part.dimensions.heightMm
    DimensionField.DIAMETER -> part.dimensions.diameterMm
    DimensionField.INNER_DIAMETER -> part.dimensions.innerDiameterMm
    DimensionField.OUTER_DIAMETER -> part.dimensions.outerDiameterMm
    DimensionField.THICKNESS -> part.dimensions.thicknessMm
    DimensionField.TOLERANCE -> part.dimensions.toleranceMm
}

private fun safetyColor(sc: SafetyClassification): Color = when {
    sc.isSafetyCritical -> ForgeColors.Error
    sc.displayName.contains("Educational") -> ForgeColors.ProvenanceOffline
    else -> ForgeColors.Warning
}