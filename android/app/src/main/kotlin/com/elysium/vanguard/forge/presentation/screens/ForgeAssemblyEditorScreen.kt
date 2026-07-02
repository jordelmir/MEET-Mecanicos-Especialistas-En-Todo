package com.elysium.vanguard.forge.presentation.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.forge.domain.ForgeAssembly
import com.elysium.vanguard.forge.domain.ForgePart
import com.elysium.vanguard.forge.domain.JointType
import com.elysium.vanguard.forge.presentation.components.NeonCard
import com.elysium.vanguard.forge.presentation.components.SectionHeader
import com.elysium.vanguard.forge.presentation.components.TechLabel
import com.elysium.vanguard.forge.presentation.components.UiState
import com.elysium.vanguard.forge.presentation.theme.ForgeColors
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeAssemblyEditorEvent
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeAssemblyEditorViewModel

@Composable
fun ForgeAssemblyEditorScreen(
    viewModel: ForgeAssemblyEditorViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val parts by viewModel.parts.collectAsState()
    val validation by viewModel.validation.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is UiState.Loading -> CenterText("CARGANDO ENSAMBLE", ForgeColors.Primary)
            is UiState.Empty -> CenterText("Sin ensamble cargado", ForgeColors.OnSurface)
            is UiState.Error -> CenterText(state.message, ForgeColors.Error)
            is UiState.Ready -> Content(
                assembly = state.data,
                availableParts = parts,
                validation = validation,
                onEvent = viewModel::onEvent,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun CenterText(text: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun Content(
    assembly: ForgeAssembly,
    availableParts: List<ForgePart>,
    validation: com.elysium.vanguard.forge.domain.AssemblyValidationResult?,
    onEvent: (ForgeAssemblyEditorEvent) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForgeColors.SurfaceVariant,
                        contentColor = ForgeColors.OnSurface
                    )
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("BACK")
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = assembly.artifact.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                    TechLabel("ENSAMBLE · ${assembly.instances.size} piezas · ${assembly.joints.size} joints")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            NeonCard(modifier = Modifier.fillMaxWidth().height(180.dp), accentColor = ForgeColors.Accent) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ViewInAr,
                            contentDescription = null,
                            tint = ForgeColors.Accent,
                            modifier = Modifier.height(48.dp).width(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "VIEWPORT 3D",
                            color = ForgeColors.Accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        TechLabel("(${assembly.instances.size} instancias · exploded view: tap)")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            SectionHeader("INSTANCIAS (PIEZAS EN EL ENSAMBLE)")
        }
        if (assembly.instances.isEmpty()) {
            item {
                TechLabel("Vacío. Agrega piezas abajo.")
                Spacer(Modifier.height(8.dp))
            }
        } else {
            items(count = assembly.instances.size) { i ->
                val instance = assembly.instances[i]
                InstanceRow(
                    instanceId = instance.id,
                    partId = instance.partId,
                    damage = instance.damageState.healthPercent,
                    onRemove = { onEvent(ForgeAssemblyEditorEvent.OnRemoveInstance(instance.id)) }
                )
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            AddPartRow(
                availableParts = availableParts,
                onAdd = { partId, instanceId ->
                    onEvent(ForgeAssemblyEditorEvent.OnAddPart(partId, instanceId))
                }
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("JOINTS (RESTRICCIONES)")
        }
        if (assembly.joints.isEmpty()) {
            item {
                TechLabel("Sin joints. Agrega restricciones abajo.")
                Spacer(Modifier.height(8.dp))
            }
        } else {
            items(count = assembly.joints.size) { i ->
                JointRow(joint = assembly.joints[i])
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            AddJointRow(assembly = assembly, onAdd = { parentId, childId, type ->
                onEvent(ForgeAssemblyEditorEvent.OnCreateJoint(parentId, childId, type))
            })
        }
        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("VALIDACIÓN")
            if (validation != null) {
                ValidationPanel(validation!!)
            } else {
                TechLabel("Sin validar aún. Pulsa Validar abajo.")
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            ActionButtons(onEvent = onEvent)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InstanceRow(instanceId: String, partId: String, damage: Double, onRemove: () -> Unit) {
    NeonCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        accentColor = if (damage < 50.0) ForgeColors.Error else ForgeColors.Success
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(instanceId, color = ForgeColors.OnSurface, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                TechLabel("part=$partId · health=${damage.toInt()}%")
            }
            Button(
                onClick = onRemove,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForgeColors.Error.copy(alpha = 0.2f),
                    contentColor = ForgeColors.Error
                )
            ) { Text("Quitar", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun AddPartRow(
    availableParts: List<ForgePart>,
    onAdd: (partId: String, instanceId: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedPartId by remember { mutableStateOf<String?>(null) }
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
        Text(if (expanded) "Cancelar" else "Agregar pieza al ensamble")
    }
    if (expanded) {
        Spacer(Modifier.height(8.dp))
        availableParts.take(20).forEach { p ->
            Button(
                onClick = {
                    val instanceId = "inst_${p.artifact.id}_${System.currentTimeMillis()}"
                    onAdd(p.artifact.id, instanceId)
                    expanded = false
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForgeColors.Surface,
                    contentColor = ForgeColors.OnSurface
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text("+ ${p.artifact.name} (${p.artifact.id})", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun JointRow(joint: com.elysium.vanguard.forge.domain.MechanicalJoint) {
    NeonCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = ForgeColors.Secondary,
                modifier = Modifier.width(20.dp).height(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = joint.jointType.name,
                    color = ForgeColors.OnSurface,
                    fontSize = 12.sp
                )
                TechLabel(
                    "${joint.parentInstanceId} → ${joint.childInstanceId}"
                )
            }
        }
    }
}

@Composable
private fun AddJointRow(
    assembly: ForgeAssembly,
    onAdd: (parentId: String, childId: String, type: JointType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var parentId by remember { mutableStateOf<String?>(null) }
    var childId by remember { mutableStateOf<String?>(null) }
    var jointType by remember { mutableStateOf(JointType.FIXED) }
    val instances = assembly.instances
    if (instances.size < 2) {
        Text(
            text = "Necesitas ≥2 instancias para crear joints.",
            color = ForgeColors.OnSurface.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
        return
    }
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
        Text(if (expanded) "Cancelar" else "Crear joint")
    }
    if (expanded) {
        Spacer(Modifier.height(8.dp))
        Text("Parent:", color = ForgeColors.OnSurface, fontSize = 12.sp)
        instances.forEach { inst ->
            Button(
                onClick = { parentId = inst.id },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (parentId == inst.id) ForgeColors.Primary.copy(alpha = 0.3f) else ForgeColors.Surface,
                    contentColor = if (parentId == inst.id) ForgeColors.Primary else ForgeColors.OnSurface
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
            ) { Text(inst.id, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Child:", color = ForgeColors.OnSurface, fontSize = 12.sp)
        instances.forEach { inst ->
            Button(
                onClick = { childId = inst.id },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (childId == inst.id) ForgeColors.Primary.copy(alpha = 0.3f) else ForgeColors.Surface,
                    contentColor = if (childId == inst.id) ForgeColors.Primary else ForgeColors.OnSurface
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
            ) { Text(inst.id, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Tipo:", color = ForgeColors.OnSurface, fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(JointType.FIXED, JointType.REVOLUTE, JointType.SLIDER, JointType.SPRING_DAMPER).forEach { type ->
                Button(
                    onClick = { jointType = type },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (jointType == type) ForgeColors.Primary.copy(alpha = 0.3f) else ForgeColors.Surface,
                        contentColor = if (jointType == type) ForgeColors.Primary else ForgeColors.OnSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text(type.name, fontSize = 10.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (parentId != null && childId != null && parentId != childId) {
                    onAdd(parentId!!, childId!!, jointType)
                    expanded = false
                }
            },
            enabled = parentId != null && childId != null && parentId != childId,
            colors = ButtonDefaults.buttonColors(
                containerColor = ForgeColors.Primary.copy(alpha = 0.2f),
                contentColor = ForgeColors.Primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Crear joint") }
    }
}

@Composable
private fun ValidationPanel(validation: com.elysium.vanguard.forge.domain.AssemblyValidationResult) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = if (validation.isValid) ForgeColors.Success else ForgeColors.Warning
    ) {
        Column {
            Text(
                text = if (validation.isValid) "✓ ENSAMBLE VÁLIDO" else "⚠ ${validation.issues.size} PROBLEMAS",
                color = if (validation.isValid) ForgeColors.Success else ForgeColors.Warning,
                fontSize = 13.sp
            )
            if (validation.floatingInstanceIds.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                TechLabel("FLOTANTES: ${validation.floatingInstanceIds.joinToString(", ")}")
            }
            if (validation.interferencePairs.isNotEmpty()) {
                TechLabel("INTERFERENCIAS: ${validation.interferencePairs.size}")
            }
            if (validation.incompatibleJoints.isNotEmpty()) {
                TechLabel("JOINTS INVÁLIDOS: ${validation.incompatibleJoints.size}")
            }
            validation.issues.take(5).forEach { issue ->
                Spacer(Modifier.height(2.dp))
                TechLabel("• ${issue.message.take(80)}")
            }
        }
    }
}

@Composable
private fun ActionButtons(onEvent: (ForgeAssemblyEditorEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onEvent(ForgeAssemblyEditorEvent.OnValidate) },
            colors = ButtonDefaults.buttonColors(
                containerColor = ForgeColors.Accent.copy(alpha = 0.2f),
                contentColor = ForgeColors.Accent
            ),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Verified, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Validar")
        }
        Button(
            onClick = { onEvent(ForgeAssemblyEditorEvent.OnSave) },
            colors = ButtonDefaults.buttonColors(
                containerColor = ForgeColors.Primary.copy(alpha = 0.2f),
                contentColor = ForgeColors.Primary
            ),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Guardar")
        }
    }
}