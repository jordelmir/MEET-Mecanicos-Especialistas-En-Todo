package com.elysium369.meet.ui.components.gauges

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.ui.theme.ThemeColors
import com.elysium369.meet.ui.theme.ColorEntry
import com.elysium369.meet.ui.theme.ColorCategory

// ═══════════════════════════════════════════════════════
// CUSTOMIZER TARGET TABS
// ═══════════════════════════════════════════════════════

private enum class ColorTarget(val label: String, val icon: String) {
    BEZEL("Borde", "⭕"),
    INTERNAL("Internos", "🔵"),
    TEXT("Números", "🔢"),
    LABEL("Etiqueta", "🏷️"),
    UNIT("Unidades", "📊"),
    NEEDLE("Aguja", "📌"),
    SPECIAL("Efectos", "✨"),
}

// ═══════════════════════════════════════════════════════
// MAIN CUSTOMIZER DIALOG
// ═══════════════════════════════════════════════════════

@Composable
fun GaugeCustomizerDialog(
    currentStyle: GaugeStyleSet,
    currentScheme: GaugeColorScheme,
    onSchemeChange: (GaugeColorScheme) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    var selectedTarget by remember { mutableStateOf(ColorTarget.BEZEL) }
    var activeTab by remember { mutableIntStateOf(if (currentStyle == GaugeStyleSet.CUSTOM_DIY) 1 else 0) } // 0 = Colors, 1 = DIY Design
    val diyTrigger = GaugeStyleManager.diyUpdateTrigger
    var primaryCategoryIndex by remember { mutableIntStateOf(0) }
    var secondaryCategoryIndex by remember { mutableIntStateOf(0) }

    // Local mutable copy for instant UI feedback — synced with external prop
    var liveScheme by remember { mutableStateOf(currentScheme) }
    LaunchedEffect(currentScheme) { liveScheme = currentScheme }

    val currentTargetColor = when (selectedTarget) {
        ColorTarget.BEZEL -> liveScheme.bezelColor
        ColorTarget.INTERNAL -> liveScheme.internalColor
        ColorTarget.TEXT -> liveScheme.textColor
        ColorTarget.LABEL -> liveScheme.labelColor
        ColorTarget.UNIT -> liveScheme.unitColor
        ColorTarget.NEEDLE -> liveScheme.needleColor
        ColorTarget.SPECIAL -> liveScheme.specialColor
    }

    val inf = rememberInfiniteTransition(label = "customizer")
    val borderGlow by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "borderGlow"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D1117),
                            Color(0xFF0A0E1A),
                            Color(0xFF060810),
                        )
                    )
                )
                .border(
                    1.5.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            currentTargetColor.copy(alpha = borderGlow * 0.6f),
                            currentTargetColor.copy(alpha = borderGlow * 0.15f),
                            currentTargetColor.copy(alpha = borderGlow * 0.4f),
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ══════════════════════════════════════
                // HEADER
                // ══════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 16.dp, 16.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "🎨 Personalizar",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Reset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x33FF6B6B))
                                .clickable { onReset() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "🔄 Reset",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Close
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Style name subtitle
                Text(
                    "${currentStyle.icon} ${currentStyle.displayName}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(Modifier.height(12.dp))

                // ══════════════════════════════════════
                // TARGET TABS (Borde, Internos, etc.)
                // ══════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ColorTarget.entries.forEach { target ->
                        val isSelected = target == selectedTarget
                        val targetColor = when (target) {
                            ColorTarget.BEZEL -> liveScheme.bezelColor
                            ColorTarget.INTERNAL -> liveScheme.internalColor
                            ColorTarget.TEXT -> liveScheme.textColor
                            ColorTarget.LABEL -> liveScheme.labelColor
                            ColorTarget.UNIT -> liveScheme.unitColor
                            ColorTarget.NEEDLE -> liveScheme.needleColor
                            ColorTarget.SPECIAL -> liveScheme.specialColor
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) targetColor.copy(alpha = 0.2f)
                                    else Color(0x15FFFFFF)
                                )
                                .then(
                                    if (isSelected) Modifier.border(
                                        1.dp,
                                        targetColor.copy(alpha = 0.6f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    else Modifier
                                )
                                .clickable { selectedTarget = target }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(target.icon, fontSize = 16.sp)
                                Text(
                                    target.label,
                                    color = if (isSelected) targetColor else Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Thin separator
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .background(currentTargetColor.copy(alpha = 0.15f))
                )

                Spacer(Modifier.height(4.dp))

                // Tab Selection for DIY style
                if (currentStyle == GaugeStyleSet.CUSTOM_DIY) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TabButton(label = "DISEÑO DIY", active = activeTab == 1, accentColor = currentTargetColor, onClick = { activeTab = 1 }, modifier = Modifier.weight(1f))
                        TabButton(label = "COLORES NEÓN", active = activeTab == 0, accentColor = currentTargetColor, onClick = { activeTab = 0 }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                ) {
                    if (currentStyle == GaugeStyleSet.CUSTOM_DIY && activeTab == 1) {
                        // ── DIY DESIGNER CONTROLS ──
                        val diyBgType = remember(diyTrigger) { gaugeStyleManager.getDiyBgType() }
                        val diyBgPreset = remember(diyTrigger) { gaugeStyleManager.getDiyBgPresetIndex() }
                        val diyBgUri = remember(diyTrigger) { gaugeStyleManager.getDiyBgImageUri() }
                        val diyBezel = remember(diyTrigger) { gaugeStyleManager.getDiyBezelStyle() }
                        val diyNeedle = remember(diyTrigger) { gaugeStyleManager.getDiyNeedleStyle() }
                        val diyTicks = remember(diyTrigger) { gaugeStyleManager.getDiyTicksStyle() }
                        val diyAccentArgb = remember(diyTrigger) { gaugeStyleManager.getDiyAccentColor() }
                        val diyAccent2Argb = remember(diyTrigger) { gaugeStyleManager.getDiyAccentColor2() }
                        val diyGlowIntensity = remember(diyTrigger) { gaugeStyleManager.getDiyGlowIntensity() }
                        val diyImageOpacity = remember(diyTrigger) { gaugeStyleManager.getDiyImageOpacity() }
                        val diyGaugeName = remember(diyTrigger) { gaugeStyleManager.getDiyGaugeName() }
                        val diyAnimation = remember(diyTrigger) { gaugeStyleManager.getDiyAnimation() }
                        val accentColor = Color(diyAccentArgb)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF080C14))
                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Gauge3DWrapper(
                                glowColor = accentColor,
                                style = GaugeStyleSet.CUSTOM_DIY,
                                modifier = Modifier.size(180.dp)
                            ) {
                                GaugeDiyWidget(
                                    label = diyGaugeName.ifEmpty { "PREVIEW" },
                                    value = 65f,
                                    minVal = 0f,
                                    maxVal = 100f,
                                    unit = "%",
                                    warningThreshold = 70f,
                                    criticalThreshold = 90f,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        
                        Text(
                            text = "Vista previa en tiempo real",
                            color = accentColor.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )

                        DiySectionHeader(icon = "🏷️", title = "NOMBRE DEL GAUGE")
                        OutlinedTextField(
                            value = diyGaugeName,
                            onValueChange = { gaugeStyleManager.saveDiyGaugeName(it) },
                            placeholder = { Text("Mi Reloj Personalizado", color = Color.White.copy(alpha = 0.3f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                cursorColor = accentColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White.copy(alpha = 0.7f)
                            ),
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )

                        val categories = ThemeColors.FULL_COLOR_PALETTE

                        DiySectionHeader(icon = "🎨", title = "COLOR DE ACENTO PRIMARIO")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            itemsIndexed(categories) { index, category ->
                                val isSelected = primaryCategoryIndex == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0x0CFFFFFF))
                                        .border(1.dp, if (isSelected) accentColor else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .clickable { primaryCategoryIndex = index }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${category.icon} ${category.title}",
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            items(categories[primaryCategoryIndex].colors) { entry ->
                                NeonColorSwatch(
                                    color = entry.color,
                                    isSelected = entry.color.toArgb() == diyAccentArgb,
                                    onClick = { gaugeStyleManager.saveDiyAccentColor(entry.color.toArgb()) }
                                )
                            }
                        }

                        DiySectionHeader(icon = "✨", title = "COLOR DE ACENTO SECUNDARIO")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            itemsIndexed(categories) { index, category ->
                                val isSelected = secondaryCategoryIndex == index
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0x0CFFFFFF))
                                        .border(1.dp, if (isSelected) accentColor else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .clickable { secondaryCategoryIndex = index }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${category.icon} ${category.title}",
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            items(categories[secondaryCategoryIndex].colors) { entry ->
                                NeonColorSwatch(
                                    color = entry.color,
                                    isSelected = entry.color.toArgb() == diyAccent2Argb,
                                    onClick = { gaugeStyleManager.saveDiyAccentColor2(entry.color.toArgb()) }
                                )
                            }
                        }

                        DiySectionHeader(icon = "🗡️", title = "ESTILO DE AGUJA")
                        val needles = listOf(
                            Triple(0, "⚡", "Cyber"),
                            Triple(1, "🏎️", "Deportiva"),
                            Triple(2, "💫", "Plasma"),
                            Triple(3, "🔮", "Esfera"),
                            Triple(4, "⚔️", "Katana"),
                            Triple(5, "⚡", "Rayo"),
                            Triple(6, "📟", "Digital"),
                            Triple(7, "☄️", "Cometa")
                        )
                        needles.chunked(4).forEach { rowNeedles ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowNeedles.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyNeedle == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyNeedleStyle(index) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        DiySectionHeader(icon = "⭕", title = "ESTILO DE BORDE")
                        val bezels = listOf(
                            Triple(0, "💠", "Neón"),
                            Triple(1, "⏱️", "Cronógrafo"),
                            Triple(2, "🔲", "Carbono"),
                            Triple(3, "◽", "Minimal"),
                            Triple(4, "⭕", "Doble"),
                            Triple(5, "💎", "Diamante"),
                            Triple(6, "💓", "Pulso"),
                            Triple(7, "🏁", "Racing")
                        )
                        bezels.chunked(4).forEach { rowBezels ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowBezels.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyBezel == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyBezelStyle(index) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        DiySectionHeader(icon = "📊", title = "MARCADORES")
                        val ticks = listOf(
                            Triple(0, "📏", "Radial"),
                            Triple(1, "🌈", "Arco"),
                            Triple(2, "⚫", "Puntos"),
                            Triple(3, "❌", "Ninguno"),
                            Triple(4, "🌡️", "Gradiente"),
                            Triple(5, "🟩", "LED Bar"),
                            Triple(6, "🔺", "Triángulos"),
                            Triple(7, "🕐", "Reloj")
                        )
                        ticks.chunked(4).forEach { rowTicks ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowTicks.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyTicks == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyTicksStyle(index) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        DiySectionHeader(icon = "🎨", title = "TIPO DE FONDO")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val bgTypes = listOf("🌀 Gradiente", "🎭 Preestablecido", "📸 Mi Imagen")
                            bgTypes.forEachIndexed { idx, labelText ->
                                val isSelected = diyBgType == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0xFF0A0E14))
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { gaugeStyleManager.saveDiyBgType(idx) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = labelText,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (diyBgType == 1) {
                            DiySectionHeader(icon = "🎭", title = "FONDO PREESTABLECIDO")
                            val presets = listOf(
                                Triple(0, "⚙️", "Metal"),
                                Triple(1, "🔳", "Carbono"),
                                Triple(2, "📡", "Cyber"),
                                Triple(3, "🌌", "Espacio"),
                                Triple(4, "🌋", "Lava"),
                                Triple(5, "🔌", "Circuito"),
                                Triple(6, "🐝", "Panal"),
                                Triple(7, "🌀", "Nebulosa")
                            )
                            presets.chunked(4).forEach { rowPresets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPresets.forEach { (index, emoji, name) ->
                                        DiyVisualCard(
                                            icon = emoji,
                                            name = name,
                                            isSelected = diyBgPreset == index,
                                            accentColor = accentColor,
                                            onClick = { gaugeStyleManager.saveDiyBgPresetIndex(index) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        } else if (diyBgType == 2) {
                            val imagePicker = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.GetContent()
                            ) { uri: android.net.Uri? ->
                                if (uri != null) {
                                    gaugeStyleManager.saveDiyBgImageUri(uri.toString())
                                    gaugeStyleManager.saveDiyBgType(2)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { imagePicker.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, accentColor),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (diyBgUri.isEmpty()) "📂 SELECCIONAR IMAGEN..." else "📸 CAMBIAR IMAGEN...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                if (diyBgUri.isNotEmpty()) {
                                    val previewBitmap = remember(diyBgUri) {
                                        if (diyBgUri.isNotEmpty()) {
                                            try {
                                                val uri = android.net.Uri.parse(diyBgUri)
                                                val stream = context.contentResolver.openInputStream(uri)
                                                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                                                stream?.close()
                                                bmp?.asImageBitmap()
                                            } catch (e: Exception) { null }
                                        } else null
                                    }
                                    if (previewBitmap != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, accentColor, CircleShape)
                                        ) {
                                            Image(
                                                bitmap = previewBitmap,
                                                contentDescription = "Preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }

                            if (diyBgUri.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Ruta: ${diyBgUri.substringAfterLast("/")}",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        DiySectionHeader(icon = "🌀", title = "ANIMACIÓN DE FONDO")
                        val animations = listOf(
                            Triple(0, "❌", "Ninguna"),
                            Triple(1, "🔥", "Fuego"),
                            Triple(2, "⚡", "Rayos"),
                            Triple(3, "❄️", "Nieve"),
                            Triple(4, "🌧️", "Lluvia"),
                            Triple(5, "⚙️", "Engranajes"),
                            Triple(6, "🌌", "Galaxia"),
                            Triple(7, "📡", "Radar"),
                            Triple(8, "📟", "Matriz"),
                            Triple(9, "🌀", "Aurora")
                        )
                        animations.chunked(5).forEach { rowAnims ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowAnims.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyAnimation == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyAnimation(index) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        DiySectionHeader(icon = "🔧", title = "AJUSTE FINO")
                        
                        Text(
                            text = "Intensidad del Brillo: ${(diyGlowIntensity * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Slider(
                            value = diyGlowIntensity,
                            onValueChange = { gaugeStyleManager.saveDiyGlowIntensity(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (diyBgType == 2) {
                            Text(
                                text = "Opacidad de Imagen: ${(diyImageOpacity * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Slider(
                                value = diyImageOpacity,
                                onValueChange = { gaugeStyleManager.saveDiyImageOpacity(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))
                    } else {
                        // Render standard color palette categories
                        ThemeColors.FULL_COLOR_PALETTE.forEach { category ->
                            // Category header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp, top = 12.dp)
                            ) {
                                Text(category.icon, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    category.title,
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                            }

                            // Color grid — rows of 6
                            val columns = 6
                            category.colors.chunked(columns).forEach { rowColors ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowColors.forEach { entry ->
                                        NeonColorSwatch(
                                            color = entry.color,
                                            isSelected = entry.color.toArgb() == currentTargetColor.toArgb(),
                                            onClick = {
                                                val newScheme = when (selectedTarget) {
                                                    ColorTarget.BEZEL -> liveScheme.copy(bezelColor = entry.color)
                                                    ColorTarget.INTERNAL -> liveScheme.copy(internalColor = entry.color)
                                                    ColorTarget.TEXT -> liveScheme.copy(textColor = entry.color)
                                                    ColorTarget.LABEL -> liveScheme.copy(labelColor = entry.color)
                                                    ColorTarget.UNIT -> liveScheme.copy(unitColor = entry.color)
                                                    ColorTarget.NEEDLE -> liveScheme.copy(needleColor = entry.color)
                                                    ColorTarget.SPECIAL -> liveScheme.copy(specialColor = entry.color)
                                                }
                                                liveScheme = newScheme  // instant local update
                                                onSchemeChange(newScheme) // persist + notify
                                            }
                                        )
                                    }
                                    // Fill remaining cells with spacers
                                    repeat(columns - rowColors.size) {
                                        Spacer(Modifier.size(38.dp))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// NEON COLOR SWATCH (single circle with glow effect)
// ═══════════════════════════════════════════════════════

@Composable
private fun NeonColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "swatch_${color.toArgb()}")
    val glow by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swGlow"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .drawBehind {
                // Neon glow ring behind selected swatch
                if (isSelected) {
                    drawCircle(
                        color = color.copy(alpha = glow * 0.45f),
                        radius = size.minDimension / 2f + 5.dp.toPx()
                    )
                    drawCircle(
                        color = color.copy(alpha = glow * 0.2f),
                        radius = size.minDimension / 2f + 9.dp.toPx()
                    )
                }
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.75f)
                    )
                )
            )
            .then(
                if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape)
                else Modifier.border(1.dp, color.copy(alpha = 0.25f), CircleShape)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // White checkmark with shadow
            Text(
                "✓",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) accentColor.copy(alpha = 0.2f) else Color(0x0CFFFFFF))
            .border(1.dp, if (active) accentColor else Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun DiyOptionSelector(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            color = accentColor.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEachIndexed { idx, label ->
                val isSelected = idx == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0x0CFFFFFF))
                        .border(1.dp, if (isSelected) accentColor else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .clickable { onSelect(idx) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DiySectionHeader(icon: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun DiyVisualCard(
    icon: String,
    name: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0xFF0A0E14))
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                name,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

