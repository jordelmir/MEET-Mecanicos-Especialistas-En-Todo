package com.elysium.vanguard.forge.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.forge.domain.FeaturePreset
import com.elysium.vanguard.forge.domain.FeatureType
import com.elysium.vanguard.forge.domain.featurePresets
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.MaterialSpec
import com.elysium.vanguard.forge.domain.ParametricFeature
import com.elysium.vanguard.forge.domain.SafetyClassification
import kotlin.math.PI
import com.elysium.vanguard.forge.presentation.components.IsometricMeshRenderer
import com.elysium.vanguard.forge.presentation.components.NeonCard
import com.elysium.vanguard.forge.presentation.components.RotationState
import com.elysium.vanguard.forge.presentation.components.ProvenanceBadge
import com.elysium.vanguard.forge.presentation.components.SectionHeader
import com.elysium.vanguard.forge.presentation.components.SeverityBadge
import com.elysium.vanguard.forge.presentation.components.TechLabel
import com.elysium.vanguard.forge.presentation.components.UiState
import com.elysium.vanguard.forge.presentation.theme.ForgeColors
import com.elysium.vanguard.forge.presentation.viewmodels.DimensionField
import com.elysium.vanguard.forge.presentation.viewmodels.ForgePartEditorEvent
import com.elysium.vanguard.forge.presentation.viewmodels.ForgePartEditorViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.SaveStatus

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
    val safetyClassification by viewModel.safetyClassification.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

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
                safetyClassification = safetyClassification,
                saveStatus = saveStatus,
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
    safetyClassification: SafetyClassification,
    saveStatus: SaveStatus,
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
            Viewport3D(part = part, materials = materials)
            Spacer(Modifier.height(16.dp))
        }
        item {
            SafetyClassificationPicker(
                current = safetyClassification,
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
            Spacer(Modifier.height(8.dp))
            FeaturePresetsRow(onPresetSelected = { preset ->
                val newFeature = ParametricFeature(
                    id = "f_${System.currentTimeMillis()}",
                    type = preset.type,
                    name = preset.displayName,
                    parameters = preset.defaultParameters
                )
                onEvent(ForgePartEditorEvent.OnAddFeature(newFeature))
            })
            Spacer(Modifier.height(8.dp))
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
            ActionButtons(onEvent = onEvent, saveStatus = saveStatus)
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
private fun Viewport3D(part: ForgePart, materials: List<MaterialSpec>) {
    // El compilador es puro y barato para piezas pequeñas (1-10 features).
    // remember(part) recompila solo cuando el part cambia de identidad.
    val compiler = remember { com.elysium.vanguard.forge.engine.ForgeGeometryCompiler() }
    val mesh = remember(part) { compiler.compilePart(part).mesh }

    // Estado de rotación elevado al composable padre para poder controlarlo
    // desde el slider de pitch sin entrar en conflicto con el scroll vertical.
    var rotation by remember { mutableStateOf(RotationState()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        NeonCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 320.dp)
                .aspectRatio(1.6f),
            accentColor = ForgeColors.Accent
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (mesh.isEmpty) {
                    Viewport3DPlaceholder(part = part, materials = materials)
                } else {
                    IsometricMeshRenderer(
                        mesh = mesh,
                        yaw = rotation.yaw,
                        pitch = rotation.pitch,
                        modifier = Modifier.fillMaxSize(),
                        onYawChange = { newYaw ->
                            rotation = rotation.copy(yaw = wrapAngle(newYaw))
                        },
                        onReset = {
                            rotation = RotationState()
                        }
                    )
                    Viewport3DOverlay(
                        part = part,
                        materials = materials,
                        triangleCount = mesh.faces.size,
                        vertexCount = mesh.vertices.size,
                        rotation = rotation
                    )
                }
            }
        }
        // Slider horizontal para pitch (rotación vertical). Evita el conflicto
        // con el scroll vertical del LazyColumn padre.
        if (!mesh.isEmpty) {
            PitchSlider(
                pitch = rotation.pitch,
                onPitchChange = { rotation = rotation.copy(pitch = it) },
                onReset = { rotation = RotationState() }
            )
        }
    }
}

/**
 * Slider de pitch (rotación vertical). Rango -60° a +60° (~±1.047 rad).
 */
/**
 * Envuelve un ángulo en radianes al rango [-π, π).
 * Evita drift de precisión en sin/cos cuando el yaw acumula muchas rotaciones.
 * Como sin/cos son periódicos, el wrap es visualmente invisible.
 */
private fun wrapAngle(rad: Float): Float {
    val twoPi = (2.0 * PI).toFloat()
    var n = rad % twoPi
    if (n > PI.toFloat()) n -= twoPi
    if (n < -PI.toFloat()) n += twoPi
    return n
}

@Composable
private fun PitchSlider(
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val minPitch = -1.047f
    val maxPitch = 1.047f
    val degrees = Math.toDegrees(pitch.toDouble()).toInt()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TechLabel("PITCH")
        Slider(
            value = pitch,
            onValueChange = onPitchChange,
            valueRange = minPitch..maxPitch,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .semantics {
                    contentDescription = "Inclinación vertical de la pieza 3D"
                },
            colors = SliderDefaults.colors(
                thumbColor = ForgeColors.Accent,
                activeTrackColor = ForgeColors.Accent.copy(alpha = 0.6f),
                inactiveTrackColor = ForgeColors.OnSurface.copy(alpha = 0.2f)
            )
        )
        // Botón reset compacto.
        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = ForgeColors.OnSurface.copy(alpha = 0.6f)
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier
                .height(28.dp)
                .semantics { contentDescription = "Resetear rotación 3D a cero" }
        ) {
            Text("↺", fontSize = 14.sp)
        }
        Spacer(Modifier.width(4.dp))
        // Lectura compacta del valor en grados.
        TechLabel("${degrees}°")
    }
}

/**
 * Overlay textual sobre el renderer 3D real. Mantiene la información que
 * mostraba el placeholder (dimensiones, material, desglose) sin tapar la malla.
 */
@Composable
private fun Viewport3DOverlay(
    part: ForgePart,
    materials: List<MaterialSpec>,
    triangleCount: Int,
    vertexCount: Int,
    rotation: RotationState
) {
    val featureBreakdown = part.featureTree
        .groupingBy { it.type }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .joinToString(separator = " · ") { (type, count) -> "$count $type" }
        .ifBlank { "—" }

    val materialName = materials
        .firstOrNull { it.id == part.materialId }
        ?.displayName
        ?.let { "Material: $it" }
        ?: "Material: sin asignar"

    val dimsLabel = "${part.dimensions.lengthMm?.toInt() ?: 0}×" +
        "${part.dimensions.widthMm?.toInt() ?: 0}×" +
        "${part.dimensions.heightMm?.toInt() ?: 0} mm"

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header superior
        Column {
            Text(
                text = "3D VIEWPORT · ISOMETRIC · INTERACTIVE",
                color = ForgeColors.Accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = "$triangleCount triángulos · $vertexCount vértices",
                color = ForgeColors.OnSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
        // Footer con metadata + hint
        Column {
            TechLabel("↔ arrastra (yaw) · slider PITCH abajo · doble tap reset")
            Spacer(Modifier.height(4.dp))
            TechLabel("BBox: $dimsLabel")
            TechLabel(materialName)
            TechLabel("Features (${part.featureTree.size}): $featureBreakdown")
        }
    }
}

@Composable
private fun Viewport3DPlaceholder(part: ForgePart, materials: List<MaterialSpec>) {
    // Desglose de features por tipo (e.g. "2 BOX · 1 CYLINDER").
    val featureBreakdown = part.featureTree
        .groupingBy { it.type }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .joinToString(separator = " · ") { (type, count) -> "$count $type" }
        .ifBlank { "—" }

    val materialName = materials
        .firstOrNull { it.id == part.materialId }
        ?.displayName
        ?.let { "Material: $it" }
        ?: "Material: sin asignar"

    val dimsLabel = "${part.dimensions.lengthMm?.toInt() ?: 0}×" +
        "${part.dimensions.widthMm?.toInt() ?: 0}×" +
        "${part.dimensions.heightMm?.toInt() ?: 0} mm"

    NeonCard(modifier = Modifier.fillMaxWidth().height(220.dp), accentColor = ForgeColors.Accent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "3D VIEWPORT",
                    color = ForgeColors.Accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(6.dp))
                TechLabel("BBox: $dimsLabel")
                Spacer(Modifier.height(2.dp))
                TechLabel(materialName)
                Spacer(Modifier.height(2.dp))
                TechLabel("Features (${part.featureTree.size}): $featureBreakdown")
                Spacer(Modifier.height(8.dp))
                TechLabel("(MESH SE COMPONE EN FORGE GEOMETRY COMPILER · BACKEND)")
            }
        }
    }
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
private fun ActionButtons(onEvent: (ForgePartEditorEvent) -> Unit, saveStatus: SaveStatus) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Indicador de estado de guardado automático.
        SaveStatusBadge(saveStatus)
        Spacer(Modifier.height(8.dp))
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
}

/**
 * Badge pequeño que muestra el estado de persistencia del editor.
 * Mapea `SaveStatus` → (label, color).
 */
@Composable
private fun SaveStatusBadge(status: SaveStatus) {
    val (label, color) = when (status) {
        SaveStatus.IDLE -> "SIN CAMBIOS" to ForgeColors.OnSurface.copy(alpha = 0.4f)
        SaveStatus.SCHEDULED -> "GUARDANDO EN 1.5s…" to ForgeColors.Warning
        SaveStatus.SAVING -> "GUARDANDO…" to ForgeColors.Primary
        SaveStatus.SAVED -> "GUARDADO ✓" to ForgeColors.Success
        SaveStatus.ERROR -> "ERROR AL GUARDAR" to ForgeColors.Error
    }
    if (status == SaveStatus.IDLE) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold
        )
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

// ─────────── Feature presets (8 quick-add primitives) ───────────
// `FeaturePreset` y `featurePresets` viven en `domain/FeaturePresets.kt` (internal).
// Aquí solo se referencian para construir la fila y los tiles.

@Composable
private fun FeaturePresetsRow(onPresetSelected: (FeaturePreset) -> Unit) {
    Column {
        Text(
            text = "PRESETS · un toque para agregar",
            color = ForgeColors.OnSurface.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(featurePresets) { preset ->
                FeaturePresetTile(preset = preset, onClick = { onPresetSelected(preset) })
            }
        }
    }
}

@Composable
private fun FeaturePresetTile(preset: FeaturePreset, onClick: () -> Unit) {
    NeonCard(
        modifier = Modifier
            .width(120.dp)
            .height(72.dp),
        accentColor = if (preset.type.supportedV1) ForgeColors.Success else ForgeColors.Warning,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = preset.displayName,
                color = ForgeColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preset.shortSpec,
                color = ForgeColors.OnSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preset.type.name,
                color = if (preset.type.supportedV1) {
                    ForgeColors.Success.copy(alpha = 0.8f)
                } else {
                    ForgeColors.Warning.copy(alpha = 0.8f)
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}