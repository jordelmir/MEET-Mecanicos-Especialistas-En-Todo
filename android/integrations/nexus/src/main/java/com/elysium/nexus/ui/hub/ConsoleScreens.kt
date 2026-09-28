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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.SportsEsports
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
import com.elysium.nexus.core.device.ConsoleSubcategory
import com.elysium.nexus.core.device.DeviceCategory
import com.elysium.nexus.core.device.DeviceCatalog
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonSectionHeader
import com.elysium.nexus.ui.theme.NeonStatusPill

/**
 * The console sub-category picker.
 *
 * The user tapped "PlayStation" (or "Xbox" or
 * "Nintendo") on the Hub. This screen shows the
 * sub-categories (PS5, PS4, PS3, etc.). The user
 * taps one to see the device models.
 *
 * Each sub-category is a card with:
 *
 *  - The sub-category name (e.g. "PlayStation 5").
 *  - The release year.
 *  - A chevron indicating "tap to go deeper".
 */
@Composable
fun ConsoleSubcategoryScreen(
    category: DeviceCategory,
    onBack: () -> Unit,
    onSubcategorySelected: (ConsoleSubcategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    val subcategories = when (category) {
        DeviceCategory.PLAYSTATION -> DeviceCategory.playstationSubcategories
        DeviceCategory.XBOX -> DeviceCategory.xboxSubcategories
        DeviceCategory.NINTENDO -> DeviceCategory.nintendoSubcategories
        else -> emptyList()
    }
    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
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
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // === HERO CARD ===
            NeonHeroCard(
                title = category.labelEs,
                subtitle = "${subcategories.size} modelos disponibles",
                accent = ElysiumColors.NeonPurple,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = "Paso 1 de 3",
                        color = ElysiumColors.NeonOrange
                    )
                }
            )
            // === EXPLANATION ===
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonPurple,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Text(
                    text = "Elige tu ${category.labelEs.lowercase()}",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.NeonPurple
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Toca el modelo que tienes. Si no estás seguro, " +
                        "elige el más reciente — los modelos más nuevos " +
                        "son compatibles con los controles más viejos.",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    color = ElysiumColors.OnSurface
                )
            }
            // === SUBCATEGORY GRID ===
            NeonSectionHeader(
                text = "Modelos",
                accent = ElysiumColors.NeonPurple,
                modifier = Modifier.padding(
                    horizontal = info.sidePadding,
                    vertical = 8.dp
                )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding),
                verticalArrangement = Arrangement.spacedBy(info.cardSpacing)
            ) {
                subcategories.forEach { sub ->
                    SubcategoryCard(
                        subcategory = sub,
                        onClick = { onSubcategorySelected(sub) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — ${category.labelEs}",
            whatIsThis = "Esta pantalla muestra los diferentes modelos de ${category.labelEs}. " +
                "Toca el modelo que tienes en casa.",
            howToUse = listOf(
                "Busca el modelo de tu ${category.labelEs.lowercase()} en la lista.",
                "Si tienes un modelo reciente, probablemente esté en la parte de arriba.",
                "Si no estás seguro, elige el modelo más reciente."
            ),
            tip = "Los modelos más nuevos son compatibles con los controles de " +
                "los modelos más viejos, así que si tu modelo exacto no está, " +
                "elige el más cercano.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun SubcategoryCard(
    subcategory: ConsoleSubcategory,
    onClick: () -> Unit
) {
    NeonCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        accent = ElysiumColors.NeonPurple,
        cornerRadius = 16.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElysiumColors.NeonPurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = ElysiumColors.NeonPurple,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subcategory.labelEs,
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = ElysiumColors.OnSurface,
                    maxLines = 1
                )
                Text(
                    text = "${subcategory.labelEn} · ${subcategory.year}",
                    style = TextStyle(fontSize = 12.sp),
                    color = ElysiumColors.OnSurfaceMuted,
                    maxLines = 1
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = ElysiumColors.NeonPurple,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * The "coming soon" screen for consoles.
 *
 * The user picked a specific console (e.g. PS5).
 * This screen shows the device card + a clear
 * "Coming soon" message that explains the
 * Bluetooth HID transport is in Phase 2+.
 *
 * The screen has:
 *
 *  - A back button.
 *  - A hero card with the device name + a
 *    "Coming soon" pill.
 *  - A card describing the device (model, year,
 *    blurb).
 *  - A "Coming soon" explanation card with
 *    details about when the support will arrive.
 */
@Composable
fun ConsoleDeviceScreen(
    templateId: String,
    onBack: () -> Unit,
    onSettings: () -> Unit = { },
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    val template = DeviceCatalog.byId(templateId)
    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // === TOP BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
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
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (template == null) {
                // The template was removed from the
                // catalog (e.g. by a future update).
                // Show a "not found" card.
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = info.sidePadding, vertical = 4.dp),
                    accent = ElysiumColors.NeonOrange
                ) {
                    Text(
                        text = "Dispositivo no encontrado",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.NeonOrange
                    )
                }
                return@Column
            }
            // === HERO CARD ===
            NeonHeroCard(
                title = template.brand,
                subtitle = template.model,
                accent = ElysiumColors.NeonPurple,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(
                        label = "Próximamente",
                        color = ElysiumColors.NeonOrange
                    )
                }
            )
            // === DEVICE INFO CARD ===
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonPurple
            ) {
                Text(
                    text = "Sobre este dispositivo",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.NeonPurple
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = template.blurbEs,
                    style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                    color = ElysiumColors.OnSurface
                )
            }
            // === COMING SOON CARD ===
            NeonCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 4.dp),
                accent = ElysiumColors.NeonOrange
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = ElysiumColors.NeonOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Soporte en construcción",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ElysiumColors.NeonOrange
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estamos trabajando para que puedas usar tu " +
                        "${template.brand} desde esta app. " +
                        "El soporte requiere conexión por Bluetooth HID, " +
                        "que llegará en una próxima actualización (Fase 2).",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                    color = ElysiumColors.OnSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "¿Qué puedes hacer mientras tanto?",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ElysiumColors.NeonCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Por ahora, conecta tus TVs, barras de sonido y " +
                        "proyectores por infrarrojo (IR). Para tu " +
                        "${template.brand}, sigue usando el control físico.",
                    style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
                    color = ElysiumColors.OnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (showHelp) {
        HelpCard(
            title = "Ayuda — ${template?.brand ?: "Consola"}",
            whatIsThis = "Esta pantalla te muestra información sobre tu consola. " +
                "El soporte para controlarla desde esta app está en construcción.",
            howToUse = listOf(
                "Por ahora no puedes controlar esta consola desde la app.",
                "Usa el control físico de tu ${template?.brand ?: "consola"}.",
                "Vuelve a esta pantalla más tarde para ver si el soporte ya está disponible."
            ),
            tip = "Mientras tanto, puedes probar la app con TVs, barras de sonido " +
                "y otros dispositivos que sí soportamos.",
            onDismiss = { showHelp = false }
        )
    }
}
