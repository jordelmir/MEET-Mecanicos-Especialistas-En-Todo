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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.elysium.nexus.fabric.automation.Automation
import com.elysium.nexus.fabric.automation.AutomationId
import com.elysium.nexus.fabric.automation.Trigger
import com.elysium.nexus.fabric.automation.TriggerEvent
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.automation.Action
import com.elysium.nexus.fabric.automation.CommandValue
import com.elysium.nexus.fabric.automation.VerificationPolicy
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * §28 Automation editor screen.
 *
 * A form to create or edit an automation.
 * The user sets a name, picks a trigger event,
 * and adds one or more actions (device + command).
 */
@Composable
fun AutomationEditorScreen(
    existingAutomation: Automation? = null,
    onBack: () -> Unit,
    onSave: (Automation) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(existingAutomation?.name ?: "Mi automatizacion") }
    var selectedTrigger by remember {
        mutableStateOf(existingAutomation?.triggers?.firstOrNull()?.event ?: TriggerEvent.Motion)
    }
    var actionDeviceId by remember {
        mutableStateOf(existingAutomation?.actions?.firstOrNull()?.deviceId?.value ?: "device-1")
    }
    var actionTurnOn by remember {
        mutableStateOf(
            (existingAutomation?.actions?.firstOrNull()?.command as? CommandValue.OnOff)?.turnOn ?: true
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
                    label = "Guardar",
                    onClick = {
                        val automation = Automation(
                            id = existingAutomation?.id ?: AutomationId("auto-${System.currentTimeMillis()}"),
                            name = name.ifBlank { "Sin nombre" },
                            author = "user",
                            createdAtNs = existingAutomation?.createdAtNs ?: System.nanoTime(),
                            triggers = listOf(Trigger(event = selectedTrigger)),
                            conditions = emptyList(),
                            actions = listOf(
                                Action(
                                    deviceId = DeviceId(actionDeviceId),
                                    capability = Capability.OnOff,
                                    command = CommandValue.OnOff(turnOn = actionTurnOn)
                                )
                            ),
                            verification = VerificationPolicy(
                                timeoutMs = 5_000L,
                                requireStateConfirmation = false
                            )
                        )
                        onSave(automation)
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
                title = if (existingAutomation != null) "Editar" else "Nueva",
                subtitle = name.ifBlank { "Sin nombre" },
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp)
            )

            // === NAME ==============================================
            FormSection("Nombre", info.sidePadding) {
                NeonCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = ElysiumColors.NeonCyan,
                    cornerRadius = 8.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    Text(
                        text = name,
                        style = TextStyle(fontSize = 16.sp),
                        color = ElysiumColors.OnSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // In a real app, this would open a text input dialog
                                // For now, we cycle through preset names
                                name = when (name) {
                                    "Mi automatizacion" -> "Encender luces"
                                    "Encender luces" -> "Apagar luces"
                                    "Apagar luces" -> "Modo noche"
                                    else -> "Mi automatizacion"
                                }
                            }
                    )
                }
            }

            // === TRIGGER ===========================================
            FormSection("Trigger (cuando pasa)", info.sidePadding) {
                TriggerEvent.values().forEach { event ->
                    TriggerChip(
                        label = event.name,
                        selected = event == selectedTrigger,
                        onSelect = { selectedTrigger = event }
                    )
                }
            }

            // === ACTION ============================================
            FormSection("Accion (que hacer)", info.sidePadding) {
                NeonCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = ElysiumColors.NeonGreen,
                    cornerRadius = 8.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Dispositivo: $actionDeviceId",
                            style = TextStyle(fontSize = 14.sp),
                            color = ElysiumColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeonChip(
                                label = if (actionTurnOn) "Encender" else "Apagar",
                                onClick = { actionTurnOn = !actionTurnOn },
                                accent = if (actionTurnOn) ElysiumColors.NeonGreen else ElysiumColors.NeonMagenta
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Editor de automatizacion",
            whatIsThis = "Crea o edita una automatizacion. Un trigger define " +
                "cuando se ejecuta, y las acciones definen que hacer.",
            howToUse = listOf(
                "Escribe un nombre para identificar la automatizacion.",
                "Selecciona el trigger (evento que activa la accion).",
                "Configura la accion (que dispositivo controlar y como).",
                "Toca 'Guardar' para guardar la automatizacion."
            ),
            tip = "Los triggers mas comunes son Motion (movimiento) y Time (hora).",
            onDismiss = { showHelp = false }
        )
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

@Composable
private fun TriggerChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) ElysiumColors.NeonCyan.copy(alpha = 0.3f)
                else ElysiumColors.Surface
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) ElysiumColors.NeonCyan else ElysiumColors.OnSurface
        )
    }
}
