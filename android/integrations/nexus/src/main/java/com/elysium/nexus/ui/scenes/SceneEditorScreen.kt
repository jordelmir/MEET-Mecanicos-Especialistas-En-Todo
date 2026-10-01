package com.elysium.nexus.ui.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.fabric.automation.ActionStep
import com.elysium.nexus.fabric.automation.Scene
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard

private val PRESET_NAMES = listOf(
    "Modo cine",
    "Modo noche",
    "Apagar todo",
    "Fiesta"
)

private val PRESET_DESCRIPTIONS = listOf(
    "Enciende la TV, sube el volumen y baja las luces.",
    "Baja luces y silencia el audio.",
    "Apaga todos los dispositivos conectados.",
    "Sube el volumen y enciende todo."
)

private val PRESET_TAGS = listOf<String>("cine", "noche", "ahorro", "fiesta")

private val PRESET_DEVICES = listOf(
    "tv-salon",
    "receiver",
    "luz-salon",
    "luz-cocina",
    "ac-dormitorio"
)

private val PRESET_TIMEOUTS_MS = listOf(1_000L, 2_000L, 5_000L)

/** Action kinds exposed in the scene editor (subset of UniversalAction). */
private enum class StepActionKind(val label: String) {
    PowerOn("Encender"),
    PowerOff("Apagar"),
    MediaPlay("Play"),
    MediaPause("Pausa"),
    VolumeUp("Vol +"),
    Mute("Silencio"),
    SetTemperature("Temp 22C")
}

private data class StepDraft(
    val deviceId: String,
    val kind: StepActionKind,
    val timeoutMs: Long
)

/**
 * §36 Scene editor screen (PHASE 9).
 *
 * Multi-step scene editor. The user sets a name,
 * optional description, one or more steps
 * (device + action + timeout), and tags.
 * Save produces a durable [Scene] consumed by
 * the Room-backed [com.elysium.nexus.fabric.automation.SceneRegistry].
 *
 * NOTE: preset cycling replaces free text input for
 * now (same pattern as the automation editor); it is
 * honest UI scaffolding, not fake persistence.
 */
@Composable
fun SceneEditorScreen(
    existingScene: Scene? = null,
    onBack: () -> Unit,
    onSave: (Scene) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(existingScene?.name ?: PRESET_NAMES.first()) }
    var description by remember { mutableStateOf(existingScene?.description ?: PRESET_DESCRIPTIONS.first()) }

    var nameIndex by remember { mutableStateOf(PRESET_NAMES.indexOf(existingScene?.name).coerceAtLeast(0)) }
    var descriptionIndex by remember { mutableStateOf(PRESET_DESCRIPTIONS.indexOf(existingScene?.description).coerceAtLeast(0)) }

    var selectedTag by remember { mutableStateOf(existingScene?.tags?.firstOrNull() ?: "") }

    var steps by remember {
        mutableStateOf(
            existingScene?.steps?.map { step ->
                StepDraft(
                    deviceId = step.targetDeviceId.value,
                    kind = kindOfStep(step.action),
                    timeoutMs = step.timeoutMs
                )
            } ?: listOf(StepDraft(PRESET_DEVICES.first(), StepActionKind.PowerOn, 5_000L))
        )
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = info.sidePadding,
                        vertical = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = onBack,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                NeonChip(
                    label = "Añadir paso",
                    onClick = {
                        steps = steps + StepDraft(PRESET_DEVICES.first(), StepActionKind.PowerOn, 5_000L)
                    },
                    accent = ElysiumColors.NeonOrange,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) }
                )
                NeonChip(
                    label = "Guardar",
                    onClick = {
                        val scene = Scene(
                            id = existingScene?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { "Sin nombre" },
                            description = description.ifBlank { "" },
                            steps = steps.mapIndexed { index, draft ->
                                ActionStep(
                                    stepId = existingScene?.steps?.getOrNull(index)?.stepId
                                        ?: java.util.UUID.randomUUID().toString(),
                                    targetDeviceId = DeviceId(draft.deviceId),
                                    action = SceneStepBuilder.build(draft.kind, draft.deviceId),
                                    timeoutMs = draft.timeoutMs
                                )
                            },
                            tags = if (selectedTag.isBlank()) emptySet() else setOf(selectedTag),
                            metadata = if (existingScene != null) existingScene.metadata else emptyMap()
                        )
                        onSave(scene)
                    },
                    accent = ElysiumColors.NeonGreen,
                    icon = { Icon(Icons.Filled.Save, contentDescription = null) }
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.HelpOutline,
                        contentDescription = "Ayuda",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // === HERO ==============================================
            NeonHeroCard(
                title = if (existingScene != null) "Editar escena" else "Nueva escena",
                subtitle = "${steps.size} pasos",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp)
            )

            // === NAME ================================================
            FormSection("Nombre", info.sidePadding) {
                NeonCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = ElysiumColors.NeonCyan,
                    cornerRadius = 8.dp,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = name,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElysiumColors.OnSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nameIndex = (nameIndex + 1) % PRESET_NAMES.size
                                name = PRESET_NAMES[nameIndex]
                                descriptionIndex = nameIndex % PRESET_DESCRIPTIONS.size
                                description = PRESET_DESCRIPTIONS[descriptionIndex]
                            }
                    )
                }
            }

            // === DESCRIPTION =========================================
            FormSection("Descripcion", info.sidePadding) {
                NeonCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = ElysiumColors.NeonCyan,
                    cornerRadius = 8.dp,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = description,
                        style = TextStyle(fontSize = 14.sp),
                        color = ElysiumColors.OnSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                descriptionIndex = (descriptionIndex + 1) % PRESET_DESCRIPTIONS.size
                                description = PRESET_DESCRIPTIONS[descriptionIndex]
                            }
                    )
                }
            }

            // === TAGS ===================================================
            FormSection("Etiqueta", info.sidePadding) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_TAGS.forEach { tag ->
                        NeonChip(
                            label = tag,
                            onClick = {
                                selectedTag = if (selectedTag == tag) "" else tag
                            },
                            accent = if (selectedTag == tag) ElysiumColors.NeonCyan
                            else ElysiumColors.NeonPurple
                        )
                    }
                }
            }

            // === STEPS ==================================================
            FormSection("Pasos (${steps.size})", info.sidePadding) {
                steps.forEachIndexed { index, step ->
                    StepEditorCard(
                        index = index,
                        step = step,
                        onUpdate = { updated ->
                            steps = steps.toMutableList().apply { set(index, updated) }
                        },
                        onRemove = {
                            steps = steps.toMutableList().apply { removeAt(index) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Editor de escena",
            whatIsThis = "Una escena ejecuta varios pasos (dispositivo + accion) " +
                "en secuencia. Se guarda en el almacenamiento durable de la app.",
            howToUse = listOf(
                "Toca el nombre para rotar entre presets (no hay teclado en esta fase).",
                "Toca 'Añadir paso' para encadenar mas acciones.",
                "En cada paso selecciona dispositivo, accion y timeout rotando con taps.",
                "Toca 'Guardar' — la escena se persiste en Room (SceneRegistry)."
            ),
            tip = "La ejecucion de escenas pasa por el motor de automatizacion; " +
                "sin adaptadores conectados el resultado queda registrado como no entregado.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun StepEditorCard(
    index: Int,
    step: StepDraft,
    onUpdate: (StepDraft) -> Unit,
    onRemove: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (index % 2 == 0) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange,
        cornerRadius = 8.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Paso ${index + 1}",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.NeonCyan
                )
                NeonChip(
                    label = "",
                    onClick = onRemove,
                    accent = ElysiumColors.NeonMagenta,
                    icon = { Icon(Icons.Filled.Delete, contentDescription = "Borrar paso", modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Dispositivo:",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                NeonChip(
                    label = step.deviceId,
                    onClick = {
                        val nextIndex = (PRESET_DEVICES.indexOf(step.deviceId).coerceAtLeast(0) + 1) % PRESET_DEVICES.size
                        onUpdate(step.copy(deviceId = PRESET_DEVICES[nextIndex]))
                    },
                    accent = ElysiumColors.NeonPurple
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Accion:",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                NeonChip(
                    label = step.kind.label,
                    onClick = {
                        val next = (step.kind.ordinal + 1) % StepActionKind.entries.size
                        onUpdate(step.copy(kind = StepActionKind.entries[next]))
                    },
                    accent = ElysiumColors.NeonGreen
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Timeout:",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                NeonChip(
                    label = "${step.timeoutMs} ms",
                    onClick = {
                        val next = (PRESET_TIMEOUTS_MS.indexOf(step.timeoutMs).coerceAtLeast(0) + 1) % PRESET_TIMEOUTS_MS.size
                        onUpdate(step.copy(timeoutMs = PRESET_TIMEOUTS_MS[next]))
                    },
                    accent = ElysiumColors.NeonOrange
                )
            }
        }
    }
}

@Composable
private fun FormSection(
    title: String,
    sidePadding: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePadding, vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = ElysiumColors.NeonCyan,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

private fun kindOfStep(action: UniversalAction): StepActionKind =
    when (action) {
        is UniversalAction.PowerOn -> StepActionKind.PowerOn
        is UniversalAction.PowerOff -> StepActionKind.PowerOff
        is UniversalAction.MediaPlay -> StepActionKind.MediaPlay
        is UniversalAction.MediaPause -> StepActionKind.MediaPause
        is UniversalAction.VolumeUp -> StepActionKind.VolumeUp
        is UniversalAction.Mute -> StepActionKind.Mute
        is UniversalAction.SetTemperature -> StepActionKind.SetTemperature
        else -> StepActionKind.PowerOn
    }

/** Internal builder that maps a step kind to a concrete UniversalAction. */
private object SceneStepBuilder {
    fun build(kind: StepActionKind, deviceId: String): UniversalAction {
        val target = DeviceId(deviceId)
        return when (kind) {
            StepActionKind.PowerOn -> UniversalAction.PowerOn(target)
            StepActionKind.PowerOff -> UniversalAction.PowerOff(target)
            StepActionKind.MediaPlay -> UniversalAction.MediaPlay(target)
            StepActionKind.MediaPause -> UniversalAction.MediaPause(target)
            StepActionKind.VolumeUp -> UniversalAction.VolumeUp(target)
            StepActionKind.Mute -> UniversalAction.Mute(target)
            StepActionKind.SetTemperature -> UniversalAction.SetTemperature(target, 22.0f)
        }
    }
}