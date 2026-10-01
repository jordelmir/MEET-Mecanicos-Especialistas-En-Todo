package com.elysium.nexus.ui.hub

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HelpOutline
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceCatalog
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonSectionHeader
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * The device-picker screen — the second level of
 * the §15 hierarchy.
 *
 * The user picked a category on the Hub (e.g. "TV").
 * This screen shows every device in that category
 * (Samsung TV, LG TV, Sony TV, …) as a list. The
 * user taps one to start the connection flow.
 *
 * The screen has:
 *
 *  - A back button (top-left).
 *  - A hero card with the category name + a
 *    plain-language blurb.
 *  - A list / grid of device cards. Each card
 *    shows the brand, a 1-line blurb, and a
 *    "Conectar" chip.
 *  - A help button (top-right).
 *
 * The grid is responsive: 1 column on phones, 2 on
 * small tablets, etc.
 */
@Composable
fun DeviceCategoryScreen(
    category: DeviceCategory,
    onBack: () -> Unit,
    onDeviceSelected: (DeviceTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    val devices = DeviceCatalog.byCategory(category)
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
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // === HERO CARD =========================================
            NeonHeroCard(
                title = category.labelEs,
                subtitle = "${devices.size} dispositivos disponibles",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = "Paso 2 de 3",
                        color = ElysiumColors.NeonOrange
                    )
                }
            )
            // === EXPLANATION =======================================
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonCyan,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Text(
                    text = "Elige tu ${category.labelEs.lowercase()}",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.NeonCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = category.blurbEs,
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    color = ElysiumColors.OnSurface
                )
            }
            // === DEVICE LIST =======================================
            NeonSectionHeader(
                text = "Marcas",
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier.padding(
                    horizontal = info.sidePadding,
                    vertical = 8.dp
                )
            )
            val rows = devices.chunked(info.columns)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding),
                verticalArrangement = Arrangement.spacedBy(info.cardSpacing)
            ) {
                rows.forEach { rowDevices ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(info.cardSpacing)
                    ) {
                        rowDevices.forEach { device ->
                            DeviceCard(
                                device = device,
                                onClick = { onDeviceSelected(device) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(info.columns - rowDevices.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — ${category.labelEs}",
            whatIsThis = category.blurbEs,
            howToUse = listOf(
                "Busca la marca de tu dispositivo en la lista.",
                "Si no estás seguro, elige 'Genérico' — funciona con la mayoría.",
                "Toca 'Conectar' para empezar la conexión."
            ),
            tip = "Si tu marca no aparece o no funciona, puedes enseñarle los códigos " +
                "con el botón 'Aprender' en la pantalla de conexión.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun DeviceCard(
    device: DeviceTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonCard(
        modifier = modifier.clickable(onClick = onClick),
        accent = ElysiumColors.NeonCyan,
        cornerRadius = 16.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = device.brand,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = ElysiumColors.OnSurface,
                maxLines = 1
            )
            Text(
                text = device.model,
                style = TextStyle(fontSize = 12.sp),
                color = ElysiumColors.OnSurfaceMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = device.blurbEs,
                style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
                color = ElysiumColors.OnSurfaceVariant,
                maxLines = 3
            )
            device.hintEs?.let { hint ->
                Spacer(modifier = Modifier.height(4.dp))
                NeonStatusPill(label = hint, color = ElysiumColors.NeonGreen)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Toca para conectar",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = ElysiumColors.NeonCyan
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = ElysiumColors.NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
