package com.elysium.nexus.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.elysium.nexus.fabric.automation.Automation
import com.elysium.nexus.fabric.automation.AutomationId
import com.elysium.nexus.fabric.automation.TriggerEvent
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * §28 Automation list screen.
 *
 * Shows all automations with their name,
 * trigger, action count, and enabled state.
 * The user can create new automations, edit
 * existing ones, or delete them.
 */
@Composable
fun AutomationListScreen(
    automations: List<Automation>,
    onBack: () -> Unit,
    onCreateNew: () -> Unit,
    onEditAutomation: (Automation) -> Unit,
    onDeleteAutomation: (Automation) -> Unit,
    onRunAutomation: (Automation) -> Unit,
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
                title = "Automatizaciones",
                subtitle = "${automations.size} reglas configuradas",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = "§28",
                        color = ElysiumColors.NeonCyan
                    )
                }
            )

            // === AUTOMATION LIST ===================================
            if (automations.isEmpty()) {
                EmptyState(info.sidePadding)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = info.sidePadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 16.dp
                    )
                ) {
                    items(automations) { automation ->
                        AutomationCard(
                            automation = automation,
                            onEdit = { onEditAutomation(automation) },
                            onDelete = { onDeleteAutomation(automation) },
                            onRun = { onRunAutomation(automation) }
                        )
                    }
                }
            }
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Automatizaciones",
            whatIsThis = "Las automatizaciones ejecutan acciones automaticamente " +
                "cuando se cumple una condicion (ej: cuando hay movimiento, " +
                "encender la luz).",
            howToUse = listOf(
                "Toca 'Nueva' para crear una automatizacion.",
                "Toca una automatizacion para editarla.",
                "Toca el boton de play para ejecutar manualmente.",
                "Toca el boton de borrar para eliminar."
            ),
            tip = "Cada automatizacion tiene un trigger (cuando pasa), " +
                "condiciones (si se cumple), y acciones (que hacer).",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun AutomationCard(
    automation: Automation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRun: () -> Unit
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 12.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = automation.name,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Trigger: ${automation.triggers.firstOrNull()?.event?.name ?: "N/A"}",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "${automation.actions.size} acciones",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
                )
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
                text = "Sin automatizaciones",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = ElysiumColors.OnSurface
            )
            Text(
                text = "Toca 'Nueva' para crear tu primera\nautomatizacion.",
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = ElysiumColors.OnSurface.copy(alpha = 0.6f)
            )
        }
    }
}
