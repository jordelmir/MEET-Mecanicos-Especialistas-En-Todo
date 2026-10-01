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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
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
import com.elysium.nexus.fabric.automation.Scene
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * §36 Scene list screen.
 *
 * Lists durable scenes (Room-backed via SceneRegistry).
 * The user can create, edit, run, or delete scenes.
 * Runs execute through the concrete automation engine.
 */
@Composable
fun SceneListScreen(
    scenes: List<Scene>,
    onBack: () -> Unit,
    onCreateNew: () -> Unit,
    onEditScene: (Scene) -> Unit,
    onDeleteScene: (Scene) -> Unit,
    onRunScene: (Scene) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier.fillMaxSize()
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
                    label = "Nueva",
                    onClick = onCreateNew,
                    accent = ElysiumColors.NeonGreen,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) }
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
                title = "Escenas",
                subtitle = "${scenes.size} escenas guardadas",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(label = "§36", color = ElysiumColors.NeonCyan)
                }
            )

            // === SCENE LIST ========================================
            if (scenes.isEmpty()) {
                EmptyState(info.sidePadding)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = info.sidePadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(scenes) { scene ->
                        SceneCard(
                            scene = scene,
                            onEdit = { onEditScene(scene) },
                            onDelete = { onDeleteScene(scene) },
                            onRun = { onRunScene(scene) }
                        )
                    }
                }
            }
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Escenas",
            whatIsThis = "Una escena es una secuencia de acciones a varios " +
                "dispositivos. Ej: 'Modo cine' enciende la TV, sube el " +
                "volumen del soundbar y apaga las luces.",
            howToUse = listOf(
                "Toca 'Nueva' para crear una escena.",
                "Añade pasos (dispositivo + accion).",
                "Toca 'Guardar' — la escena queda en el almacenamiento durable.",
                "Toca el play para ejecutar la escena manualmente."
            ),
            tip = "Las escenas se guardan en la base de datos Room y " +
                "sobreviven reinicios de la app.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun SceneCard(
    scene: Scene,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRun: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scene.name,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${scene.steps.size} pasos",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
                )
                if (scene.description.isNotBlank()) {
                    Text(
                        text = scene.description,
                        style = TextStyle(fontSize = 12.sp),
                        color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NeonChip(
                    label = "",
                    onClick = onRun,
                    accent = ElysiumColors.NeonGreen,
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Ejecutar", modifier = Modifier.size(18.dp)) }
                )
                NeonChip(
                    label = "",
                    onClick = onDelete,
                    accent = ElysiumColors.NeonMagenta,
                    icon = { Icon(Icons.Filled.Delete, contentDescription = "Borrar", modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(sidePadding: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = ElysiumColors.NeonOrange,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Sin escenas",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Toca 'Nueva' para crear tu primera escena.",
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
            )
        }
    }
}