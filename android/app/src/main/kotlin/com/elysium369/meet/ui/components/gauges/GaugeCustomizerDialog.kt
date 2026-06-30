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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.elysium369.meet.core.share.GaugeQrImport
import com.elysium369.meet.core.share.QrCodeSharing
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.entities.GaugeConfig
import com.elysium369.meet.data.local.entities.SavedGaugeEntity
import com.elysium369.meet.ui.components.AnimatedNeonGlyph
import com.elysium369.meet.ui.theme.ThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private data class DiyGuidedStep(
    val title: String,
    val summary: String,
    val cue: String,
)

private data class DiyQuickPreset(
    val label: String,
    val description: String,
    val color: Color,
    val apply: () -> Unit,
)

private val diyGuidedSteps =
    listOf(
        DiyGuidedStep(
            title = "Identidad",
            summary = "Define qué mide y cómo debe leerse de un vistazo.",
            cue = "Usa nombres cortos: RPM, BOOST PSI, TEMP ECT, VOLTAJE.",
        ),
        DiyGuidedStep(
            title = "Base visual",
            summary = "Elige fondo, imagen o preset sin sacrificar contraste.",
            cue = "Si el fondo tiene textura, baja la opacidad o sube el glow.",
        ),
        DiyGuidedStep(
            title = "Lectura",
            summary = "Aguja, borde y escala deben contar la misma historia.",
            cue = "Para datos rápidos usa aguja simple y marcadores limpios.",
        ),
        DiyGuidedStep(
            title = "Color",
            summary = "Primario para lectura; secundario para profundidad.",
            cue = "Reserva rojo/naranja para advertencia o sensación racing.",
        ),
        DiyGuidedStep(
            title = "Movimiento",
            summary = "Animación y brillo agregan vida sin tapar el valor.",
            cue = "En tableros diarios suele bastar 35-70% de glow.",
        ),
        DiyGuidedStep(
            title = "Salida",
            summary = "Guarda, comparte por QR o publica cuando esté listo.",
            cue = "Antes de compartir confirma nombre, fondo, escala y contraste.",
        ),
    )

// ═══════════════════════════════════════════════════════
// MAIN CUSTOMIZER DIALOG
// ═══════════════════════════════════════════════════════

@Composable
fun GaugeCustomizerDialog(
    currentStyle: GaugeStyleSet,
    currentScheme: GaugeColorScheme,
    onSchemeChange: (GaugeColorScheme) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    navController: NavController? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    var selectedTarget by remember { mutableStateOf(ColorTarget.BEZEL) }
    var activeTab by remember {
        mutableIntStateOf(if (currentStyle == GaugeStyleSet.CUSTOM_DIY) 1 else 0)
    } // 0 = Colors, 1 = DIY Design
    var diyGuideStep by remember { mutableIntStateOf(0) }
    val diyTrigger = GaugeStyleManager.diyUpdateTrigger
    var primaryCategoryIndex by remember { mutableIntStateOf(0) }
    var secondaryCategoryIndex by remember { mutableIntStateOf(0) }

    // Local mutable copy for instant UI feedback — synced with external prop
    var liveScheme by remember { mutableStateOf(currentScheme) }
    LaunchedEffect(currentScheme) { liveScheme = currentScheme }

    val currentTargetColor =
        when (selectedTarget) {
            ColorTarget.BEZEL -> liveScheme.bezelColor
            ColorTarget.INTERNAL -> liveScheme.internalColor
            ColorTarget.TEXT -> liveScheme.textColor
            ColorTarget.LABEL -> liveScheme.labelColor
            ColorTarget.UNIT -> liveScheme.unitColor
            ColorTarget.NEEDLE -> liveScheme.needleColor
            ColorTarget.SPECIAL -> liveScheme.specialColor
        }

    val inf = rememberInfiniteTransition(label = "customizer")
    val borderGlow by
        inf.animateFloat(
            0.3f,
            0.8f,
            infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "borderGlow",
        )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(0.92f)
                    .fillMaxHeight(0.82f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0xFF0D1117),
                                    Color(0xFF0A0E1A),
                                    Color(0xFF060810),
                                )
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    currentTargetColor.copy(alpha = borderGlow * 0.6f),
                                    currentTargetColor.copy(alpha = borderGlow * 0.15f),
                                    currentTargetColor.copy(alpha = borderGlow * 0.4f),
                                )
                        ),
                        RoundedCornerShape(24.dp),
                    )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ══════════════════════════════════════
                // HEADER
                // ══════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp, 16.dp, 16.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "🎨 Personalizar",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Reset
                        Box(
                            modifier =
                                Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33FF6B6B))
                                    .clickable { onReset() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "🔄 Reset",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        // Close
                        Box(
                            modifier =
                                Modifier.size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                                    .clickable { onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✕",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
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
                    fontFamily = FontFamily.SansSerif,
                )

                Spacer(Modifier.height(12.dp))

                // ══════════════════════════════════════
                // TARGET TABS (Borde, Internos, etc.)
                // ══════════════════════════════════════
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ColorTarget.entries.forEach { target ->
                        val isSelected = target == selectedTarget
                        val targetColor =
                            when (target) {
                                ColorTarget.BEZEL -> liveScheme.bezelColor
                                ColorTarget.INTERNAL -> liveScheme.internalColor
                                ColorTarget.TEXT -> liveScheme.textColor
                                ColorTarget.LABEL -> liveScheme.labelColor
                                ColorTarget.UNIT -> liveScheme.unitColor
                                ColorTarget.NEEDLE -> liveScheme.needleColor
                                ColorTarget.SPECIAL -> liveScheme.specialColor
                            }
                        Box(
                            modifier =
                                Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) targetColor.copy(alpha = 0.2f)
                                        else Color(0x15FFFFFF)
                                    )
                                    .then(
                                        if (isSelected)
                                            Modifier.border(
                                                1.dp,
                                                targetColor.copy(alpha = 0.6f),
                                                RoundedCornerShape(14.dp),
                                            )
                                        else Modifier
                                    )
                                    .clickable { selectedTarget = target }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedNeonGlyph(target.icon, contentDescription = null, fontSize = 16.sp)
                                Text(
                                    target.label,
                                    color =
                                        if (isSelected) targetColor
                                        else Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight =
                                        if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Thin separator
                Box(
                    Modifier.fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .background(currentTargetColor.copy(alpha = 0.15f))
                )

                Spacer(Modifier.height(4.dp))

                // Tab Selection for DIY style
                if (currentStyle == GaugeStyleSet.CUSTOM_DIY) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TabButton(
                            label = "DISEÑO DIY",
                            active = activeTab == 1,
                            accentColor = currentTargetColor,
                            onClick = { activeTab = 1 },
                            modifier = Modifier.weight(1f),
                        )
                        TabButton(
                            label = "COLORES NEÓN",
                            active = activeTab == 0,
                            accentColor = currentTargetColor,
                            onClick = { activeTab = 0 },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val scrollState = rememberScrollState()
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                ) {
                    if (currentStyle == GaugeStyleSet.CUSTOM_DIY && activeTab == 1) {
                        // ── DIY DESIGNER CONTROLS ──
                        val diyBgType = remember(diyTrigger) { gaugeStyleManager.getDiyBgType() }
                        val diyBgPreset =
                            remember(diyTrigger) { gaugeStyleManager.getDiyBgPresetIndex() }
                        val diyBgUri = remember(diyTrigger) { gaugeStyleManager.getDiyBgImageUri() }
                        val diyBezel = remember(diyTrigger) { gaugeStyleManager.getDiyBezelStyle() }
                        val diyNeedle =
                            remember(diyTrigger) { gaugeStyleManager.getDiyNeedleStyle() }
                        val diyTicks = remember(diyTrigger) { gaugeStyleManager.getDiyTicksStyle() }
                        val diyAccentArgb =
                            remember(diyTrigger) { gaugeStyleManager.getDiyAccentColor() }
                        val diyAccent2Argb =
                            remember(diyTrigger) { gaugeStyleManager.getDiyAccentColor2() }
                        val diyGlowIntensity =
                            remember(diyTrigger) { gaugeStyleManager.getDiyGlowIntensity() }
                        val diyImageOpacity =
                            remember(diyTrigger) { gaugeStyleManager.getDiyImageOpacity() }
                        val diyGaugeName =
                            remember(diyTrigger) { gaugeStyleManager.getDiyGaugeName() }
                        val diyAnimation =
                            remember(diyTrigger) { gaugeStyleManager.getDiyAnimation() }
                        val diyTypography =
                            remember(diyTrigger) { gaugeStyleManager.getDiyTypography() }
                        val accentColor = Color(diyAccentArgb)

                        val applyDiyPreset =
                            {
                                nameStr: String,
                                bgTypeVal: Int,
                                bgPresetVal: Int,
                                bezelVal: Int,
                                needleVal: Int,
                                ticksVal: Int,
                                accentVal: Int,
                                accent2Val: Int,
                                glowVal: Float,
                                animVal: Int,
                                typoVal: Int ->
                                gaugeStyleManager.saveDiyGaugeName(nameStr)
                                gaugeStyleManager.saveDiyBgType(bgTypeVal)
                                gaugeStyleManager.saveDiyBgPresetIndex(bgPresetVal)
                                gaugeStyleManager.saveDiyBezelStyle(bezelVal)
                                gaugeStyleManager.saveDiyNeedleStyle(needleVal)
                                gaugeStyleManager.saveDiyTicksStyle(ticksVal)
                                gaugeStyleManager.saveDiyAccentColor(accentVal)
                                gaugeStyleManager.saveDiyAccentColor2(accent2Val)
                                gaugeStyleManager.saveDiyGlowIntensity(glowVal)
                                gaugeStyleManager.saveDiyAnimation(animVal)
                                gaugeStyleManager.saveDiyTypography(typoVal)
                            }

                        DiyGuidedHeader(
                            steps = diyGuidedSteps,
                            currentIndex = diyGuideStep.coerceIn(0, diyGuidedSteps.lastIndex),
                            accentColor = accentColor,
                            onSelect = { diyGuideStep = it },
                            onPrev = { diyGuideStep = (diyGuideStep - 1).coerceAtLeast(0) },
                            onNext = {
                                diyGuideStep = (diyGuideStep + 1).coerceAtMost(diyGuidedSteps.lastIndex)
                            },
                        )

                        Spacer(Modifier.height(10.dp))

                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(200.dp)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF080C14))
                                    .border(
                                        1.dp,
                                        accentColor.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Gauge3DWrapper(
                                glowColor = accentColor,
                                style = GaugeStyleSet.CUSTOM_DIY,
                                modifier = Modifier.size(180.dp),
                            ) {
                                GaugeDiyWidget(
                                    label = diyGaugeName.ifEmpty { "PREVIEW" },
                                    value = 65f,
                                    minVal = 0f,
                                    maxVal = 100f,
                                    unit = "%",
                                    warningThreshold = 70f,
                                    criticalThreshold = 90f,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        Text(
                            text = "Vista previa en tiempo real",
                            color = accentColor.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )

                        DiyReadinessStrip(
                            statusItems =
                                listOf(
                                    "Nombre" to diyGaugeName.ifBlank { "Pendiente" },
                                    "Base" to
                                        diyBackgroundSummary(diyBgType, diyBgPreset, diyBgUri),
                                    "Lectura" to "Aguja ${diyNeedle + 1} · Escala ${diyTicks + 1}",
                                    "Movimiento" to diyAnimationSummary(diyAnimation),
                                    "Salida" to
                                        if (diyGaugeName.isBlank()) "Nombra y guarda"
                                        else "Listo para guardar",
                                ),
                            accentColor = accentColor,
                        )

                        // ══════════════════════════════════════
                        // 💾 SAVE / SHARE / MARKET BUTTONS
                        // ══════════════════════════════════════
                        DiySectionHeader(icon = "🚀", title = "ACCIONES")
                        DiySectionHint(
                            text =
                                "Cuando el preview comunique métrica, estilo y contraste, guárdalo o compártelo por QR.",
                            accentColor = accentColor,
                        )

                        var showSaveDialog by remember { mutableStateOf(false) }
                        var showMyGaugesDialog by remember { mutableStateOf(false) }
                        var saveGaugeName by remember {
                            mutableStateOf(diyGaugeName.ifEmpty { "Mi Gauge" })
                        }
                        var editingGaugeId by remember { mutableStateOf<String?>(null) }
                        var editingGaugeCreatedAt by remember { mutableStateOf<Long?>(null) }
                        var editingGaugePublished by remember { mutableStateOf(false) }
                        var editingGaugeMarketplaceId by remember { mutableStateOf<String?>(null) }
                        var editingGaugeThumbnailPath by remember { mutableStateOf<String?>(null) }
                        var saveFeedback by remember { mutableStateOf<String?>(null) }
                        var showQrCodeDialog by remember { mutableStateOf(false) }
                        var showQrScannerDialog by remember { mutableStateOf(false) }
                        var pendingQrImport by remember { mutableStateOf<GaugeQrImport?>(null) }
                        var qrImportActionInProgress by remember { mutableStateOf(false) }
                        var qrText by remember { mutableStateOf("") }
                        var qrShareTitle by remember { mutableStateOf("MEET Gauge") }
                        var qrExportWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
                        var isSavingGauge by remember { mutableStateOf(false) }
                        var deletingGaugeIds by remember { mutableStateOf<Set<String>>(emptySet()) }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF00B0FF).copy(alpha = 0.12f))
                                        .border(
                                            1.dp,
                                            Color(0xFF00B0FF).copy(alpha = 0.4f),
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable {
                                            gaugeStyleManager.resetDiyDraft()
                                            editingGaugeId = null
                                            editingGaugeCreatedAt = null
                                            editingGaugePublished = false
                                            editingGaugeMarketplaceId = null
                                            editingGaugeThumbnailPath = null
                                            saveGaugeName = "Mi Gauge"
                                            showSaveDialog = false
                                            showQrCodeDialog = false
                                            showQrScannerDialog = false
                                            pendingQrImport = null
                                            qrImportActionInProgress = false
                                            saveFeedback = "✅ Nuevo gauge listo"
                                        }
                                        .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "➕ NUEVO",
                                    color = Color(0xFF00B0FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // 💾 SAVE
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors =
                                                    listOf(
                                                        Color(0xFF00E676).copy(alpha = 0.12f),
                                                        Color(0xFF00C853).copy(alpha = 0.08f),
                                                    )
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            Color(0xFF00E676).copy(alpha = 0.4f),
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { showSaveDialog = true }
                                        .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "💾 GUARDAR",
                                    color = Color(0xFF00E676),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // 📂 MY GAUGES
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors =
                                                    listOf(
                                                        accentColor.copy(alpha = 0.12f),
                                                        accentColor.copy(alpha = 0.06f),
                                                    )
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            accentColor.copy(alpha = 0.4f),
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { showMyGaugesDialog = true }
                                        .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "📂 MIS",
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // 🌐 MARKET
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors =
                                                    listOf(
                                                        Color(0xFF7C4DFF).copy(alpha = 0.12f),
                                                        Color(0xFFB388FF).copy(alpha = 0.06f),
                                                    )
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            Color(0xFF7C4DFF).copy(alpha = 0.4f),
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable {
                                            if (navController != null) {
                                                navController.navigate("gauge_marketplace?publishGaugeId=draft")
                                                onDismiss()
                                            } else {
                                                saveFeedback = "🌐 Marketplace no disponible"
                                            }
                                        }
                                        .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "🌐 MARKET",
                                    color = Color(0xFF7C4DFF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 📤 COMPARTIR QR
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF00B0FF).copy(alpha = 0.12f))
                                        .border(
                                            1.dp,
                                            Color(0xFF00B0FF).copy(alpha = 0.4f),
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable {
                                            try {
                                                val config = gaugeStyleManager.exportDiyConfig()
                                                val export =
                                                    QrCodeSharing.createGaugeQrExport(
                                                        config = config,
                                                        sourceGaugeId = editingGaugeId,
                                                        sourceMarketplaceId = editingGaugeMarketplaceId,
                                                        sourcePublished = editingGaugePublished,
                                                    )
                                                qrText = export.qrText
                                                qrShareTitle = export.envelope.displayName
                                                qrExportWarnings = export.warnings
                                                showQrCodeDialog = true
                                            } catch (e: Exception) {
                                                saveFeedback = "❌ Error al generar QR"
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "📤 COMPARTIR QR",
                                    color = Color(0xFF00B0FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }

                            // 📥 ESCANEAR QR
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFCCFF00).copy(alpha = 0.12f))
                                        .border(
                                            1.dp,
                                            Color(0xFFCCFF00).copy(alpha = 0.4f),
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { showQrScannerDialog = true }
                                        .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "📥 ESCANEAR QR",
                                    color = Color(0xFFCCFF00),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                        DiySectionHeader(icon = "🚀", title = "PLANTILLAS RÁPIDAS (1-CLICK)")
                        DiySectionHint(
                            text =
                                "Arranca con una receta completa y luego ajusta color, aguja, escala y fondo.",
                            accentColor = accentColor,
                        )
                        val quickPresets =
                            listOf(
                                DiyQuickPreset(
                                    "🏁 F1 Racing",
                                    "RPM, velocidad y redline con lectura agresiva.",
                                    Color(0xFFFF1744),
                                ) {
                                    applyDiyPreset(
                                        "F1 Racing",
                                        1,
                                        1,
                                        7,
                                        1,
                                        5,
                                        0xFFFF1744.toInt(),
                                        0xFFFFEA00.toInt(),
                                        0.8f,
                                        1,
                                        5,
                                    )
                                },
                                DiyQuickPreset(
                                    "🌌 Cyber Space",
                                    "Boost, voltaje o sensores con estética HUD.",
                                    Color(0xFF00FFCC),
                                ) {
                                    applyDiyPreset(
                                        "Cyber Space",
                                        1,
                                        2,
                                        0,
                                        2,
                                        4,
                                        0xFF00FFCC.toInt(),
                                        0xFF7C4DFF.toInt(),
                                        1.0f,
                                        9,
                                        4,
                                    )
                                },
                                DiyQuickPreset(
                                    "⏱️ Retro Vintage",
                                    "Temperatura, presión o lectura clásica.",
                                    Color(0xFFFFB74D),
                                ) {
                                    applyDiyPreset(
                                        "Retro Vintage",
                                        1,
                                        0,
                                        1,
                                        4,
                                        7,
                                        0xFFFFB74D.toInt(),
                                        0xFFE0E0E0.toInt(),
                                        0.3f,
                                        0,
                                        7,
                                    )
                                },
                                DiyQuickPreset(
                                    "🌋 Lava Core",
                                    "Alertas calientes, turbo y eventos críticos.",
                                    Color(0xFFFF3D00),
                                ) {
                                    applyDiyPreset(
                                        "Lava Core",
                                        1,
                                        4,
                                        2,
                                        5,
                                        6,
                                        0xFFFF3D00.toInt(),
                                        0xFFFFEA00.toInt(),
                                        0.9f,
                                        1,
                                        6,
                                    )
                                },
                                DiyQuickPreset(
                                    "🛡️ Stealth Pro",
                                    "Diario sobrio, alto contraste y poco movimiento.",
                                    Color(0xFFB0BEC5),
                                ) {
                                    applyDiyPreset(
                                        "Stealth Pro",
                                        1,
                                        12,
                                        3,
                                        6,
                                        13,
                                        0xFFB0BEC5.toInt(),
                                        0xFF00E5FF.toInt(),
                                        0.45f,
                                        0,
                                        0,
                                    )
                                },
                                DiyQuickPreset(
                                    "🏎️ Rally Night",
                                    "Racing nocturno para tablero de performance.",
                                    Color(0xFFFFD54F),
                                ) {
                                    applyDiyPreset(
                                        "Rally Night",
                                        1,
                                        51,
                                        14,
                                        1,
                                        11,
                                        0xFFFFD54F.toInt(),
                                        0xFFFF1744.toInt(),
                                        0.65f,
                                        5,
                                        5,
                                    )
                                },
                            )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            items(quickPresets) { preset ->
                                DiyQuickPresetCard(
                                    preset = preset,
                                    onClick = preset.apply,
                                )
                            }
                        }

                        DiySectionHeader(icon = "🏷️", title = "NOMBRE DEL GAUGE")
                        DiySectionHint(
                            text =
                                "Pon una etiqueta breve y práctica; será lo primero que reconocerás al manejar.",
                            accentColor = accentColor,
                        )
                        OutlinedTextField(
                            value = diyGaugeName,
                            onValueChange = { gaugeStyleManager.saveDiyGaugeName(it) },
                            placeholder = {
                                Text(
                                    "Mi Reloj Personalizado",
                                    color = Color.White.copy(alpha = 0.3f),
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    cursorColor = accentColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White.copy(alpha = 0.7f),
                                ),
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        )

                        val categories = ThemeColors.FULL_COLOR_PALETTE

                        DiySectionHeader(icon = "🎨", title = "COLOR DE ACENTO PRIMARIO")
                        DiySectionHint(
                            text =
                                "Este color manda aguja, brillo y foco visual. Úsalo para la lectura principal.",
                            accentColor = accentColor,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            itemsIndexed(categories) { index, category ->
                                val isSelected = primaryCategoryIndex == index
                                Box(
                                    modifier =
                                        Modifier.clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) accentColor.copy(alpha = 0.2f)
                                                else Color(0x0CFFFFFF)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) accentColor
                                                else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(8.dp),
                                            )
                                            .clickable { primaryCategoryIndex = index }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${category.icon} ${category.title}",
                                        color =
                                            if (isSelected) Color.White
                                            else Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        ) {
                            items(categories[primaryCategoryIndex].colors) { entry ->
                                NeonColorSwatch(
                                    color = entry.color,
                                    isSelected = entry.color.toArgb() == diyAccentArgb,
                                    onClick = {
                                        gaugeStyleManager.saveDiyAccentColor(entry.color.toArgb())
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "🌈 SELECCIÓN LIBRE DE TONO PRIMARIO",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                        var primaryHue by
                            remember(diyAccentArgb) {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(diyAccentArgb, hsv)
                                mutableFloatStateOf(hsv[0])
                            }
                        Slider(
                            value = primaryHue,
                            onValueChange = {
                                primaryHue = it
                                val colorInt =
                                    android.graphics.Color.HSVToColor(floatArrayOf(it, 1f, 1f))
                                gaugeStyleManager.saveDiyAccentColor(colorInt)
                            },
                            valueRange = 0f..360f,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = Color(diyAccentArgb),
                                    activeTrackColor = Color(diyAccentArgb).copy(alpha = 0.5f),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                                ),
                            modifier = Modifier.height(24.dp).fillMaxWidth(),
                        )

                        DiySectionHeader(icon = "✨", title = "COLOR DE ACENTO SECUNDARIO")
                        DiySectionHint(
                            text =
                                "El secundario crea profundidad; combínalo con contraste, no solo con gusto.",
                            accentColor = accentColor,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            itemsIndexed(categories) { index, category ->
                                val isSelected = secondaryCategoryIndex == index
                                Box(
                                    modifier =
                                        Modifier.clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) accentColor.copy(alpha = 0.2f)
                                                else Color(0x0CFFFFFF)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) accentColor
                                                else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(8.dp),
                                            )
                                            .clickable { secondaryCategoryIndex = index }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${category.icon} ${category.title}",
                                        color =
                                            if (isSelected) Color.White
                                            else Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        ) {
                            items(categories[secondaryCategoryIndex].colors) { entry ->
                                NeonColorSwatch(
                                    color = entry.color,
                                    isSelected = entry.color.toArgb() == diyAccent2Argb,
                                    onClick = {
                                        gaugeStyleManager.saveDiyAccentColor2(entry.color.toArgb())
                                    },
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "🌈 SELECCIÓN LIBRE DE TONO SECUNDARIO",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                        var secondaryHue by
                            remember(diyAccent2Argb) {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(diyAccent2Argb, hsv)
                                mutableFloatStateOf(hsv[0])
                            }
                        Slider(
                            value = secondaryHue,
                            onValueChange = {
                                secondaryHue = it
                                val colorInt =
                                    android.graphics.Color.HSVToColor(floatArrayOf(it, 1f, 1f))
                                gaugeStyleManager.saveDiyAccentColor2(colorInt)
                            },
                            valueRange = 0f..360f,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = Color(diyAccent2Argb),
                                    activeTrackColor = Color(diyAccent2Argb).copy(alpha = 0.5f),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                                ),
                            modifier = Modifier.height(24.dp).fillMaxWidth(),
                        )

                        DiySectionHeader(icon = "🗡️", title = "ESTILO DE AGUJA (15 ESTILOS)")
                        DiySectionHint(
                            text =
                                "La aguja debe ser clara al primer vistazo: simple para precisión, dramática para show.",
                            accentColor = accentColor,
                        )
                        val needles =
                            listOf(
                                Triple(0, "⚡", "Cyber"),
                                Triple(1, "🏎️", "Deportiva"),
                                Triple(2, "💫", "Plasma"),
                                Triple(3, "🔮", "Esfera"),
                                Triple(4, "⚔️", "Katana"),
                                Triple(5, "⚡", "Rayo"),
                                Triple(6, "📟", "Digital"),
                                Triple(7, "☄️", "Cometa"),
                                Triple(8, "⏱️", "Retro"),
                                Triple(9, "🗡️", "Espada"),
                                Triple(10, "🧵", "Fibra"),
                                Triple(11, "➖", "Guiones"),
                                Triple(12, "🔷", "Hexágono"),
                                Triple(13, "🔱", "Doble"),
                                Triple(14, "🛸", "Virtual"),
                            )
                        needles.chunked(4).forEach { rowNeedles ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowNeedles.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyNeedle == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyNeedleStyle(index) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        DiySectionHeader(icon = "⭕", title = "ESTILO DE BORDE (20 ESTILOS)")
                        DiySectionHint(
                            text =
                                "El borde define personalidad y peso visual; si hay mucho fondo, elige un borde limpio.",
                            accentColor = accentColor,
                        )
                        val bezels =
                            listOf(
                                Triple(0, "💠", "Neón"),
                                Triple(1, "⏱️", "Cronógrafo"),
                                Triple(2, "🔲", "Carbono"),
                                Triple(3, "◽", "Minimal"),
                                Triple(4, "⭕", "Doble"),
                                Triple(5, "💎", "Diamante"),
                                Triple(6, "💓", "Pulso"),
                                Triple(7, "🏁", "Racing"),
                                Triple(8, "🌀", "Holo"),
                                Triple(9, "🛑", "Láser Pts"),
                                Triple(10, "⚙️", "Metal Pes"),
                                Triple(11, "🧬", "Carb Ros"),
                                Triple(12, "🌈", "Dual Glow"),
                                Triple(13, "⏹️", "Cuadrantes"),
                                Triple(14, "📏", "Tacómetro"),
                                Triple(15, "💫", "Shimmer"),
                                Triple(16, "🛑", "Hexágono"),
                                Triple(17, "🟩", "Cuadrado"),
                                Triple(18, "⚙️", "Steampunk"),
                                Triple(19, "🕸️", "Grid Cyber"),
                            )
                        bezels.chunked(4).forEach { rowBezels ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowBezels.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyBezel == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyBezelStyle(index) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        DiySectionHeader(icon = "📊", title = "MARCADORES / ESCALAS (15 ESTILOS)")
                        DiySectionHint(
                            text =
                                "Los marcadores son la guía de lectura. Para RPM o boost, prioriza marcas rápidas y redline.",
                            accentColor = accentColor,
                        )
                        val ticks =
                            listOf(
                                Triple(0, "📏", "Radial"),
                                Triple(1, "🌈", "Arco"),
                                Triple(2, "⚫", "Puntos"),
                                Triple(3, "❌", "Ninguno"),
                                Triple(4, "🌡️", "Gradiente"),
                                Triple(5, "🟩", "LED Bar"),
                                Triple(6, "🔺", "Triángulos"),
                                Triple(7, "🕐", "Reloj"),
                                Triple(8, "〰️", "Doble Arc"),
                                Triple(9, "📱", "Segmentado"),
                                Triple(10, "🌓", "Semicírculo"),
                                Triple(11, "🏎️", "Redline"),
                                Triple(12, "🟥", "Bloques"),
                                Triple(13, "➖", "Dashes Min"),
                                Triple(14, "📐", "Cyber Dash"),
                            )
                        ticks.chunked(4).forEach { rowTicks ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowTicks.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyTicks == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyTicksStyle(index) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        DiySectionHeader(icon = "🎨", title = "TIPO DE FONDO")
                        DiySectionHint(
                            text =
                                "El fondo debe apoyar la lectura. Si compite con números o aguja, simplifícalo.",
                            accentColor = accentColor,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val bgTypes =
                                listOf("🌀 Gradiente", "🎭 Preestablecido", "📸 Mi Imagen")
                            bgTypes.forEachIndexed { idx, labelText ->
                                val isSelected = diyBgType == idx
                                Box(
                                    modifier =
                                        Modifier.weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) accentColor.copy(alpha = 0.15f)
                                                else Color(0xFF0A0E14)
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                                color =
                                                    if (isSelected) accentColor
                                                    else Color.White.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .clickable { gaugeStyleManager.saveDiyBgType(idx) }
                                            .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = labelText,
                                        color =
                                            if (isSelected) Color.White
                                            else Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        if (diyBgType == 1) {
                            DiySectionHeader(
                                icon = "🎭",
                                title = "FONDO PREESTABLECIDO (60 PRESETS)",
                            )
                            DiySectionHint(
                                text =
                                    "Prueba metal, carbono o grid para tableros técnicos; lava y galaxia para piezas de show.",
                                accentColor = accentColor,
                            )
                            val backgroundPresets =
                                (0..59).map { index ->
                                    val (emoji, name) =
                                        when (index) {
                                            0 -> "⚙️" to "Metal"
                                            1 -> "🔳" to "Carbono"
                                            2 -> "📡" to "Cyber Grid"
                                            3 -> "🌌" to "Espacio"
                                            4 -> "🌋" to "Lava"
                                            5 -> "🔌" to "Circuito"
                                            6 -> "🐝" to "Panal"
                                            7 -> "🌀" to "Nebulosa"
                                            8 -> "🟫" to "Cobre"
                                            9 -> "🌅" to "Sunset"
                                            10 -> "💚" to "Aurora"
                                            11 -> "👑" to "Oro"
                                            12 -> "🔘" to "Acero"
                                            13 -> "🌳" to "Bosque"
                                            14 -> "❄️" to "Glaciar"
                                            15 -> "🌊" to "Océano"
                                            16 -> "📡" to "Radar"
                                            17 -> "🎯" to "Retícula"
                                            18 -> "🌀" to "Espiral"
                                            19 -> "☀️" to "Helios"
                                            20 -> "🌪️" to "Vórtice"
                                            21 -> "🪐" to "Órbitas"
                                            22 -> "⭕" to "Aros"
                                            23 -> "🛰️" to "Constel"
                                            24 -> "📐" to "Malla Tri"
                                            25 -> "📈" to "Isométr"
                                            26 -> "💎" to "Rombos"
                                            27 -> "🏁" to "Checkers"
                                            28 -> "〽️" to "Chevron"
                                            29 -> "🧱" to "Muro"
                                            30 -> "⚫" to "Dot Grid"
                                            31 -> "💈" to "Diag Line"
                                            32 -> "〰️" to "Seno Wave"
                                            33 -> "📈" to "Ondas"
                                            34 -> "🎀" to "Cintas"
                                            35 -> "💫" to "Órbita HUD"
                                            36 -> "♾️" to "Infinito"
                                            37 -> "📊" to "Equalizer"
                                            38 -> "🔊" to "Sonido"
                                            39 -> "💦" to "Ripples"
                                            40 -> "🌌" to "Galaxia"
                                            41 -> "💥" to "Supernova"
                                            42 -> "💫" to "Meteoros"
                                            43 -> "🌑" to "Hole"
                                            44 -> "✨" to "Stars"
                                            45 -> "🛰️" to "Satélite"
                                            46 -> "☄️" to "Comet"
                                            47 -> "🌒" to "Eclipse"
                                            48 -> "⚙️" to "Pistón"
                                            49 -> "🏎️" to "Tire"
                                            50 -> "⚙️" to "Gear"
                                            51 -> "🏁" to "Race Flag"
                                            52 -> "🔥" to "Flames"
                                            53 -> "💨" to "Turbo"
                                            54 -> "🛑" to "Redline"
                                            55 -> "💿" to "Brake"
                                            56 -> "🕶️" to "Synth Grid"
                                            57 -> "📺" to "Glitch"
                                            58 -> "📟" to "Matrix"
                                            59 -> "✨" to "NeonWave"
                                            else -> "🖼️" to "Preset $index"
                                        }
                                    Triple(index, emoji, name)
                                }
                            backgroundPresets.chunked(4).forEach { rowPresets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowPresets.forEach { (index, emoji, name) ->
                                        DiyVisualCard(
                                            icon = emoji,
                                            name = name,
                                            isSelected = diyBgPreset == index,
                                            accentColor = accentColor,
                                            onClick = {
                                                gaugeStyleManager.saveDiyBgPresetIndex(index)
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        } else if (diyBgType == 2) {
                            val imagePicker =
                                rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.GetContent()
                                ) { uri: android.net.Uri? ->
                                    if (uri != null) {
                                        gaugeStyleManager.saveDiyBgImageUri(context, uri)
                                        gaugeStyleManager.saveDiyBgType(2)
                                    }
                                }

                            Spacer(Modifier.height(12.dp))
                            DiySectionHint(
                                text =
                                    "Usa imágenes centradas y con zonas oscuras para que la escala respire.",
                                accentColor = accentColor,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = { imagePicker.launch("image/*") },
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = accentColor.copy(alpha = 0.2f)
                                        ),
                                    border = BorderStroke(1.dp, accentColor),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        text =
                                            if (diyBgUri.isEmpty()) "📂 SELECCIONAR IMAGEN..."
                                            else "📸 CAMBIAR IMAGEN...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    )
                                }

                                if (diyBgUri.isNotEmpty()) {
                                    val previewBitmap =
                                        remember(diyBgUri) {
                                            if (diyBgUri.isNotEmpty()) {
                                                try {
                                                    if (diyBgUri.startsWith("/")) {
                                                        val bmp =
                                                            android.graphics.BitmapFactory
                                                                .decodeFile(diyBgUri)
                                                        bmp?.asImageBitmap()
                                                    } else {
                                                        val uri = android.net.Uri.parse(diyBgUri)
                                                        val stream =
                                                            context.contentResolver.openInputStream(
                                                                uri
                                                            )
                                                        val bmp =
                                                            android.graphics.BitmapFactory
                                                                .decodeStream(stream)
                                                        stream?.close()
                                                        bmp?.asImageBitmap()
                                                    }
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            } else null
                                        }
                                    if (previewBitmap != null) {
                                        Box(
                                            modifier =
                                                Modifier.size(48.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, accentColor, CircleShape)
                                        ) {
                                            Image(
                                                bitmap = previewBitmap,
                                                contentDescription = "Preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }

                                    // 🗑️ Delete Image button
                                    Box(
                                        modifier =
                                            Modifier.size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFFF1744).copy(alpha = 0.2f))
                                                .border(
                                                    1.dp,
                                                    Color(0xFFFF1744),
                                                    RoundedCornerShape(12.dp),
                                                )
                                                .clickable { gaugeStyleManager.clearDiyBgImage() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AnimatedNeonGlyph(
                                            glyph = "⌫",
                                            contentDescription = "Eliminar imagen",
                                            tint = Color(0xFFFF1744),
                                            fontSize = 20.sp,
                                            modifier = Modifier.size(24.dp),
                                        )
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
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        DiySectionHeader(icon = "🌀", title = "ANIMACIÓN DE FONDO (10 ANIMACIONES)")
                        DiySectionHint(
                            text =
                                "El movimiento agrega carácter. Si el gauge va en pantalla diaria, menos suele leerse mejor.",
                            accentColor = accentColor,
                        )
                        val animations =
                            listOf(
                                Triple(0, "❌", "Ninguna"),
                                Triple(1, "🔥", "Fuego"),
                                Triple(2, "⚡", "Rayos"),
                                Triple(3, "❄️", "Nieve"),
                                Triple(4, "🌧️", "Lluvia"),
                                Triple(5, "⚙️", "Engranajes"),
                                Triple(6, "🌌", "Galaxia"),
                                Triple(7, "📡", "Radar"),
                                Triple(8, "📟", "Matriz"),
                                Triple(9, "🌀", "Aurora"),
                            )
                        animations.chunked(5).forEach { rowAnims ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                rowAnims.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyAnimation == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyAnimation(index) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        DiySectionHeader(icon = "🔤", title = "TIPOGRAFÍA (10 ESTILOS)")
                        DiySectionHint(
                            text =
                                "La tipografía manda legibilidad: técnica para datos, vintage para estética clásica.",
                            accentColor = accentColor,
                        )
                        val typographies =
                            listOf(
                                Triple(0, "📟", "Monospace"),
                                Triple(1, "🅰️", "Sans Serif"),
                                Triple(2, "✒️", "Serif"),
                                Triple(3, "✍️", "Cursive"),
                                Triple(4, "🤖", "Cyber Bold"),
                                Triple(5, "🏎️", "Tech Italic"),
                                Triple(6, "🚥", "LCD Black"),
                                Triple(7, "📜", "Vintage"),
                                Triple(8, "🪖", "Military"),
                                Triple(9, "📐", "Ultra Thin"),
                            )
                        typographies.chunked(5).forEach { rowTypo ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                rowTypo.forEach { (index, emoji, name) ->
                                    DiyVisualCard(
                                        icon = emoji,
                                        name = name,
                                        isSelected = diyTypography == index,
                                        accentColor = accentColor,
                                        onClick = { gaugeStyleManager.saveDiyTypography(index) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        DiySectionHeader(icon = "🔧", title = "AJUSTE FINO")
                        DiySectionHint(
                            text =
                                "Termina aquí: brillo suficiente para presencia, opacidad suficiente para lectura.",
                            accentColor = accentColor,
                        )

                        Text(
                            text = "Intensidad del Brillo: ${(diyGlowIntensity * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Slider(
                            value = diyGlowIntensity,
                            onValueChange = { gaugeStyleManager.saveDiyGlowIntensity(it) },
                            valueRange = 0f..1f,
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (diyBgType == 2) {
                            Text(
                                text = "Opacidad de Imagen: ${(diyImageOpacity * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Slider(
                                value = diyImageOpacity,
                                onValueChange = { gaugeStyleManager.saveDiyImageOpacity(it) },
                                valueRange = 0f..1f,
                                colors =
                                    SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }


                        if (showQrCodeDialog && qrText.isNotEmpty()) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showQrCodeDialog = false }
                            ) {
                                Card(
                                    colors =
                                        CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier =
                                        Modifier.fillMaxWidth(0.92f)
                                            .border(
                                                1.dp,
                                                accentColor.copy(alpha = 0.3f),
                                                RoundedCornerShape(20.dp),
                                            ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            "Compartir Diseño QR",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        com.elysium369.meet.core.share.QrCodeImage(
                                            text = qrText,
                                            modifier = Modifier.size(240.dp),
                                            backgroundColor = Color.White,
                                            qrColor = Color.Black,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Escanea este código con otro dispositivo Elysium Vanguard para copiar el diseño al instante.",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        if (qrExportWarnings.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            QrBusinessNote(
                                                text = qrExportWarnings.joinToString(" "),
                                                color = Color(0xFFFFD54F),
                                            )
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier.weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0x33FFFFFF))
                                                        .clickable { showQrCodeDialog = false }
                                                        .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "Cerrar",
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Box(
                                                modifier =
                                                    Modifier.weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(accentColor)
                                                        .clickable {
                                                            QrCodeSharing.shareQrAsImage(
                                                                context = context,
                                                                qrText = qrText,
                                                                title = qrShareTitle,
                                                            ).onFailure {
                                                                saveFeedback =
                                                                    "❌ No se pudo compartir QR"
                                                            }
                                                        }
                                                        .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "Compartir",
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showQrScannerDialog) {
                            com.elysium369.meet.core.share.QrScannerOverlay(
                                onResult = { scannedText ->
                                    QrCodeSharing.decodeGaugeQrText(scannedText)
                                        .onSuccess { import ->
                                            qrImportActionInProgress = false
                                            pendingQrImport = import
                                            saveFeedback = null
                                        }
                                        .onFailure { error ->
                                            saveFeedback =
                                                "❌ ${error.message ?: "QR no válido"}"
                                        }
                                },
                                onDismiss = { showQrScannerDialog = false },
                            )
                        }

                        pendingQrImport?.let { import ->
                            GaugeQrImportDialog(
                                import = import,
                                accentColor = accentColor,
                                isActionInProgress = qrImportActionInProgress,
                                onDismiss = {
                                    if (!qrImportActionInProgress) pendingQrImport = null
                                },
                                onApplyToEditor = {
                                    if (qrImportActionInProgress) return@GaugeQrImportDialog
                                    qrImportActionInProgress = true
                                    gaugeStyleManager.importDiyConfig(import.config)
                                    editingGaugeId = null
                                    editingGaugeCreatedAt = null
                                    editingGaugePublished = false
                                    editingGaugeMarketplaceId = null
                                    editingGaugeThumbnailPath = null
                                    saveGaugeName = import.config.name.ifBlank { "Gauge QR" }
                                    qrImportActionInProgress = false
                                    pendingQrImport = null
                                    saveFeedback = "✅ QR aplicado al editor"
                                },
                                onSaveCopy = {
                                    if (qrImportActionInProgress) return@GaugeQrImportDialog
                                    qrImportActionInProgress = true
                                    scope.launch {
                                        runCatching {
                                            val entity =
                                                import.config.toQrSavedGaugeEntity(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    nameOverride = uniqueQrGaugeName(
                                                        context = context,
                                                        preferredName = import.config.name,
                                                    ),
                                                )
                                            saveImportedGauge(context, entity)
                                            gaugeStyleManager.importDiyConfig(
                                                import.config.copy(name = entity.name)
                                            )
                                            editingGaugeId = entity.id
                                            editingGaugeCreatedAt = entity.createdAt
                                            editingGaugePublished = false
                                            editingGaugeMarketplaceId = null
                                            editingGaugeThumbnailPath = null
                                            saveGaugeName = entity.name
                                            pendingQrImport = null
                                            saveFeedback =
                                                "✅ Gauge QR \"${entity.name}\" guardado"
                                        }.onFailure { error ->
                                            saveFeedback =
                                                "❌ No se pudo guardar QR: ${error.message.orEmpty()}"
                                        }
                                        qrImportActionInProgress = false
                                    }
                                },
                            )
                        }

                        // Feedback snackbar
                        if (saveFeedback != null) {
                            LaunchedEffect(saveFeedback) {
                                kotlinx.coroutines.delay(2000)
                                saveFeedback = null
                            }
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF00E676).copy(alpha = 0.15f))
                                        .border(
                                            0.5.dp,
                                            Color(0xFF00E676).copy(alpha = 0.3f),
                                            RoundedCornerShape(10.dp),
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    saveFeedback ?: "",
                                    color = Color(0xFF00E676),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // ── Save Dialog ──
                        if (showSaveDialog) {
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showSaveDialog = false }
                            ) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth(0.9f)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFF0D1117))
                                            .border(
                                                1.dp,
                                                accentColor.copy(alpha = 0.3f),
                                                RoundedCornerShape(20.dp),
                                            )
                                            .padding(20.dp)
                                ) {
                                    Column {
                                        Text(
                                            "💾 Guardar Gauge",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = saveGaugeName,
                                            onValueChange = { saveGaugeName = it },
                                            label = {
                                                Text(
                                                    "Nombre",
                                                    color = Color.White.copy(alpha = 0.5f),
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors =
                                                OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = accentColor,
                                                    unfocusedBorderColor =
                                                        Color.White.copy(alpha = 0.1f),
                                                    cursorColor = accentColor,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor =
                                                        Color.White.copy(alpha = 0.7f),
                                                ),
                                            textStyle =
                                                TextStyle(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier.weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0x33FFFFFF))
                                                        .clickable { showSaveDialog = false }
                                                        .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "Cancelar",
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                )
                                            }
                                            Box(
                                                modifier =
                                                    Modifier.weight(1f)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF00E676))
                                                        .clickable(enabled = !isSavingGauge) {
                                                            if (isSavingGauge) return@clickable
                                                            isSavingGauge = true
                                                            scope.launch {
                                                                runCatching {
                                                                    val targetId =
                                                                        editingGaugeId
                                                                            ?: java.util.UUID
                                                                                .randomUUID()
                                                                                .toString()
                                                                    val isEditing =
                                                                        editingGaugeId != null
                                                                    val now =
                                                                        System.currentTimeMillis()
                                                                    val entity =
                                                                        gaugeStyleManager
                                                                            .exportToSavedEntity(
                                                                                id = targetId,
                                                                                name =
                                                                                    saveGaugeName
                                                                                        .ifBlank {
                                                                                            "Mi Gauge"
                                                                                        },
                                                                            )
                                                                            .copy(
                                                                                createdAt =
                                                                                    editingGaugeCreatedAt
                                                                                        ?: now,
                                                                                updatedAt = now,
                                                                                isPublished =
                                                                                    if (isEditing)
                                                                                        editingGaugePublished
                                                                                    else false,
                                                                                marketplaceId =
                                                                                    if (isEditing)
                                                                                        editingGaugeMarketplaceId
                                                                                    else null,
                                                                                thumbnailPath =
                                                                                    if (isEditing)
                                                                                        editingGaugeThumbnailPath
                                                                                    else null,
                                                                            )
                                                                    withContext(Dispatchers.IO) {
                                                                        val db =
                                                                            androidx.room
                                                                                .Room
                                                                                .databaseBuilder(
                                                                                    context,
                                                                                    com.elysium369
                                                                                        .meet
                                                                                        .data
                                                                                        .local
                                                                                        .MeetDatabase::class
                                                                                        .java,
                                                                                    "meet_database",
                                                                                )
                                                                                .build()
                                                                        try {
                                                                            db.savedGaugeDao()
                                                                                .insert(entity)
                                                                        } finally {
                                                                            db.close()
                                                                        }
                                                                    }
                                                                    editingGaugeId = entity.id
                                                                    editingGaugeCreatedAt =
                                                                        entity.createdAt
                                                                    editingGaugePublished =
                                                                        entity.isPublished
                                                                    editingGaugeMarketplaceId =
                                                                        entity.marketplaceId
                                                                    editingGaugeThumbnailPath =
                                                                        entity.thumbnailPath
                                                                    showSaveDialog = false
                                                                    saveFeedback =
                                                                        if (isEditing)
                                                                            "✅ Gauge \"$saveGaugeName\" actualizado"
                                                                        else
                                                                            "✅ Gauge \"$saveGaugeName\" guardado"
                                                                }.onFailure { error ->
                                                                    saveFeedback =
                                                                        "❌ No se pudo guardar: ${error.message.orEmpty()}"
                                                                }
                                                                isSavingGauge = false
                                                            }
                                                        }
                                                        .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    if (isSavingGauge) "Guardando..." else "💾 Guardar",
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 13.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── My Gauges Dialog ──
                        if (showMyGaugesDialog) {
                            var savedGauges by remember {
                                mutableStateOf<List<SavedGaugeEntity>>(emptyList())
                            }

                            suspend fun loadSavedGaugeRows(): List<SavedGaugeEntity> {
                                return withContext(Dispatchers.IO) {
                                    val db =
                                        androidx.room.Room.databaseBuilder(
                                                context,
                                                com.elysium369.meet.data.local.MeetDatabase::class
                                                    .java,
                                                "meet_database",
                                            )
                                            .build()
                                    try {
                                        db.savedGaugeDao().getAll()
                                    } finally {
                                        db.close()
                                    }
                                }
                            }

                            suspend fun deleteSavedGaugeRow(gauge: SavedGaugeEntity) {
                                withContext(Dispatchers.IO) {
                                    val db =
                                        androidx.room.Room.databaseBuilder(
                                                context,
                                                com.elysium369.meet.data.local.MeetDatabase::class
                                                    .java,
                                                "meet_database",
                                            )
                                            .build()
                                    try {
                                        db.savedGaugeDao().delete(gauge)
                                    } finally {
                                        db.close()
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                savedGauges = loadSavedGaugeRows()
                            }

                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { showMyGaugesDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth(0.92f)
                                            .fillMaxHeight(0.6f)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFF0D1117))
                                            .border(
                                                1.dp,
                                                accentColor.copy(alpha = 0.3f),
                                                RoundedCornerShape(20.dp),
                                            )
                                            .padding(16.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "📂 Mis Gauges (${savedGauges.size})",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black,
                                            )
                                            Box(
                                                modifier =
                                                    Modifier.size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0x33FFFFFF))
                                                        .clickable { showMyGaugesDialog = false },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "✕",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        if (savedGauges.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Column(
                                                    horizontalAlignment =
                                                        Alignment.CenterHorizontally
                                                ) {
                                                    AnimatedNeonGlyph("🎨", contentDescription = null, fontSize = 40.sp)
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        "Aún no tienes gauges guardados",
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontSize = 13.sp,
                                                    )
                                                    Text(
                                                        "Usa 💾 GUARDAR para salvar tu diseño",
                                                        color = Color.White.copy(alpha = 0.3f),
                                                        fontSize = 11.sp,
                                                    )
                                                }
                                            }
                                        } else {
                                            Column(
                                                modifier =
                                                    Modifier.fillMaxSize()
                                                        .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                savedGauges.forEach { gauge ->
                                                    Column(
                                                        modifier =
                                                            Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(Color(0xFF0A0E14))
                                                                .border(
                                                                    0.5.dp,
                                                                    accentColor.copy(alpha = 0.15f),
                                                                    RoundedCornerShape(12.dp),
                                                                )
                                                                .padding(12.dp),
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment =
                                                                Alignment.CenterVertically,
                                                            horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    gauge.name,
                                                                    color = Color.White,
                                                                    fontSize = 14.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                                Text(
                                                                    "Creado ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(gauge.createdAt))}",
                                                                    color =
                                                                        Color.White.copy(
                                                                            alpha = 0.3f
                                                                        ),
                                                                    fontSize = 10.sp,
                                                                )
                                                            }

                                                            if (gauge.isPublished) {
                                                                Text(
                                                                    "🌐",
                                                                    fontSize = 14.sp,
                                                                    modifier =
                                                                        Modifier.padding(
                                                                            start = 8.dp
                                                                        ),
                                                                )
                                                            }
                                                        }

                                                        Spacer(Modifier.height(10.dp))

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            GaugeMiniActionButton(
                                                                text = "EDITAR",
                                                                color = accentColor,
                                                                modifier = Modifier.weight(1f),
                                                                onClick = {
                                                                    gaugeStyleManager
                                                                        .importFromSavedEntity(gauge)
                                                                    editingGaugeId = gauge.id
                                                                    editingGaugeCreatedAt =
                                                                        gauge.createdAt
                                                                    editingGaugePublished =
                                                                        gauge.isPublished
                                                                    editingGaugeMarketplaceId =
                                                                        gauge.marketplaceId
                                                                    editingGaugeThumbnailPath =
                                                                        gauge.thumbnailPath
                                                                    saveGaugeName = gauge.name
                                                                    showMyGaugesDialog = false
                                                                    saveFeedback =
                                                                        "✅ Editando \"${gauge.name}\""
                                                                },
                                                            )
                                                            GaugeMiniActionButton(
                                                                text = "VENDER",
                                                                color = Color(0xFFB388FF),
                                                                modifier = Modifier.weight(1f),
                                                                onClick = {
                                                                    if (navController != null) {
                                                                        gaugeStyleManager
                                                                            .importFromSavedEntity(
                                                                                gauge
                                                                            )
                                                                        showMyGaugesDialog = false
                                                                        navController.navigate(
                                                                            "gauge_marketplace?publishGaugeId=${gauge.id}"
                                                                        )
                                                                        onDismiss()
                                                                    } else {
                                                                        saveFeedback =
                                                                            "🌐 Marketplace no disponible"
                                                                    }
                                                                },
                                                            )
                                                            GaugeMiniActionButton(
                                                                text = "BORRAR",
                                                                color = Color(0xFFFF5252),
                                                                modifier = Modifier.weight(1f),
                                                                enabled =
                                                                    !deletingGaugeIds.contains(
                                                                        gauge.id
                                                                    ),
                                                                onClick = {
                                                                    if (
                                                                        deletingGaugeIds.contains(
                                                                            gauge.id
                                                                        )
                                                                    )
                                                                        return@GaugeMiniActionButton
                                                                    deletingGaugeIds =
                                                                        deletingGaugeIds + gauge.id
                                                                    scope.launch {
                                                                        runCatching {
                                                                            deleteSavedGaugeRow(gauge)
                                                                            savedGauges =
                                                                                loadSavedGaugeRows()
                                                                            if (editingGaugeId ==
                                                                                gauge.id
                                                                            ) {
                                                                                editingGaugeId =
                                                                                    null
                                                                                editingGaugeCreatedAt =
                                                                                    null
                                                                                editingGaugePublished =
                                                                                    false
                                                                                editingGaugeMarketplaceId =
                                                                                    null
                                                                                editingGaugeThumbnailPath =
                                                                                    null
                                                                            }
                                                                            saveFeedback =
                                                                                "🗑 Gauge \"${gauge.name}\" borrado"
                                                                        }.onFailure { error ->
                                                                            saveFeedback =
                                                                                "❌ No se pudo borrar: ${error.message.orEmpty()}"
                                                                        }
                                                                        deletingGaugeIds =
                                                                            deletingGaugeIds -
                                                                                gauge.id
                                                                    }
                                                                },
                                                            )
                                                        }
                                                        if (gauge.marketplaceId != null) {
                                                            Spacer(Modifier.height(6.dp))
                                                            Text(
                                                                "Market ID ${gauge.marketplaceId.take(8)}",
                                                                color =
                                                                    Color.White.copy(alpha = 0.28f),
                                                                fontSize = 9.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    } else {
                        // Render standard color palette categories
                        ThemeColors.FULL_COLOR_PALETTE.forEach { category ->
                            // Category header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp, top = 12.dp),
                            ) {
                                AnimatedNeonGlyph(category.icon, contentDescription = null, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    category.title,
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                )
                            }

                            // Color grid — rows of 6
                            val columns = 6
                            category.colors.chunked(columns).forEach { rowColors ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    rowColors.forEach { entry ->
                                        NeonColorSwatch(
                                            color = entry.color,
                                            isSelected =
                                                entry.color.toArgb() == currentTargetColor.toArgb(),
                                            onClick = {
                                                val newScheme =
                                                    when (selectedTarget) {
                                                        ColorTarget.BEZEL ->
                                                            liveScheme.copy(
                                                                bezelColor = entry.color
                                                            )
                                                        ColorTarget.INTERNAL ->
                                                            liveScheme.copy(
                                                                internalColor = entry.color
                                                            )
                                                        ColorTarget.TEXT ->
                                                            liveScheme.copy(textColor = entry.color)
                                                        ColorTarget.LABEL ->
                                                            liveScheme.copy(
                                                                labelColor = entry.color
                                                            )
                                                        ColorTarget.UNIT ->
                                                            liveScheme.copy(unitColor = entry.color)
                                                        ColorTarget.NEEDLE ->
                                                            liveScheme.copy(
                                                                needleColor = entry.color
                                                            )
                                                        ColorTarget.SPECIAL ->
                                                            liveScheme.copy(
                                                                specialColor = entry.color
                                                            )
                                                    }
                                                liveScheme = newScheme // instant local update
                                                onSchemeChange(newScheme) // persist + notify
                                            },
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

@Composable
private fun GaugeMiniActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color.copy(alpha = if (enabled) 0.13f else 0.05f))
                .border(1.dp, color.copy(alpha = if (enabled) 0.36f else 0.12f), RoundedCornerShape(9.dp))
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color.copy(alpha = if (enabled) 1f else 0.42f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GaugeQrImportDialog(
    import: GaugeQrImport,
    accentColor: Color,
    isActionInProgress: Boolean,
    onDismiss: () -> Unit,
    onApplyToEditor: () -> Unit,
    onSaveCopy: () -> Unit,
) {
    val context = LocalContext.current
    var duplicateGaugeName by remember(import.fingerprint) { mutableStateOf<String?>(null) }

    LaunchedEffect(import.fingerprint) {
        duplicateGaugeName = findDuplicateGaugeName(context, import.fingerprint)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
            shape = RoundedCornerShape(22.dp),
            modifier =
                Modifier.fillMaxWidth(0.92f)
                    .border(1.dp, accentColor.copy(alpha = 0.34f), RoundedCornerShape(22.dp)),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Importar gauge QR",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    import.displayName,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Gauge3DWrapper(
                    glowColor = accentColor,
                    style = GaugeStyleSet.CUSTOM_DIY,
                    modifier = Modifier.size(170.dp),
                ) {
                    GaugeDiyWidget(
                        label = import.config.name.ifBlank { "QR" },
                        value = 65f,
                        minVal = 0f,
                        maxVal = 100f,
                        unit = "%",
                        warningThreshold = 70f,
                        criticalThreshold = 90f,
                        diyConfig =
                            import.config.toQrSavedGaugeEntity(
                                id = "qr-preview",
                                nameOverride = import.config.name,
                            ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.height(10.dp))
                QrBusinessNote(
                    text = "Huella ${import.fingerprint.take(12)} · ${if (import.importedFromLegacyFormat) "Legacy" else "MEET v1"}",
                    color = Color(0xFF00B0FF),
                )
                duplicateGaugeName?.let { name ->
                    Spacer(Modifier.height(6.dp))
                    QrBusinessNote(
                        text = "Ya tienes un gauge igual: $name. Puedes guardarlo como copia nueva si quieres.",
                        color = Color(0xFFFFD54F),
                    )
                }
                if (import.sourceMarketplaceId != null || import.sourcePublished) {
                    Spacer(Modifier.height(6.dp))
                    QrBusinessNote(
                        text = "Origen marketplace detectado; al guardarlo será una copia local privada hasta que la publiques.",
                        color = Color(0xFFB388FF),
                    )
                }
                import.warnings.forEach { warning ->
                    Spacer(Modifier.height(6.dp))
                    QrBusinessNote(text = warning, color = Color(0xFFFFD54F))
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GaugeMiniActionButton(
                        text = "CANCELAR",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                        enabled = !isActionInProgress,
                        onClick = onDismiss,
                    )
                    GaugeMiniActionButton(
                        text = if (isActionInProgress) "..." else "APLICAR",
                        color = accentColor,
                        modifier = Modifier.weight(1f),
                        enabled = !isActionInProgress,
                        onClick = onApplyToEditor,
                    )
                    GaugeMiniActionButton(
                        text = if (isActionInProgress) "..." else "GUARDAR",
                        color = Color(0xFF00E676),
                        modifier = Modifier.weight(1f),
                        enabled = !isActionInProgress,
                        onClick = onSaveCopy,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrBusinessNote(
    text: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.11f))
                .border(0.5.dp, color.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun GaugeConfig.toQrSavedGaugeEntity(
    id: String,
    nameOverride: String? = null,
): SavedGaugeEntity {
    val now = System.currentTimeMillis()
    val resolvedName = nameOverride?.trim()?.takeIf { it.isNotBlank() }
        ?: name.trim().ifBlank { "Gauge QR" }
    return SavedGaugeEntity(
        id = id,
        name = resolvedName,
        bgType = bgType,
        bgPresetIndex = bgPresetIndex,
        bgImageUri = "",
        bezelStyle = bezelStyle,
        needleStyle = needleStyle,
        ticksStyle = ticksStyle,
        accentColor = accentColor,
        accentColor2 = accentColor2,
        glowIntensity = glowIntensity,
        imageOpacity = imageOpacity,
        animationIndex = animationIndex,
        createdAt = now,
        updatedAt = now,
        isPublished = false,
        marketplaceId = null,
        thumbnailPath = null,
        typographyIndex = typographyIndex,
    )
}

private fun SavedGaugeEntity.toQrGaugeConfig(): GaugeConfig {
    return GaugeConfig(
        name = name,
        bgType = bgType,
        bgPresetIndex = bgPresetIndex,
        bezelStyle = bezelStyle,
        needleStyle = needleStyle,
        ticksStyle = ticksStyle,
        accentColor = accentColor,
        accentColor2 = accentColor2,
        glowIntensity = glowIntensity,
        imageOpacity = imageOpacity,
        animationIndex = animationIndex,
        typographyIndex = typographyIndex,
    )
}

private suspend fun saveImportedGauge(
    context: android.content.Context,
    entity: SavedGaugeEntity,
) {
    withContext(Dispatchers.IO) {
        val db =
            androidx.room.Room.databaseBuilder(
                context.applicationContext,
                MeetDatabase::class.java,
                "meet_database",
            ).build()
        try {
            db.savedGaugeDao().insert(entity)
        } finally {
            db.close()
        }
    }
}

private suspend fun uniqueQrGaugeName(
    context: android.content.Context,
    preferredName: String,
): String {
    return withContext(Dispatchers.IO) {
        val db =
            androidx.room.Room.databaseBuilder(
                context.applicationContext,
                MeetDatabase::class.java,
                "meet_database",
            ).build()
        try {
            val existingNames = db.savedGaugeDao().getAll().map { it.name }.toSet()
            val base = preferredName.trim().ifBlank { "Gauge QR" }.take(42)
            if (base !in existingNames) {
                base
            } else {
                var index = 2
                var candidate = "$base QR $index"
                while (candidate in existingNames) {
                    index += 1
                    candidate = "$base QR $index"
                }
                candidate
            }
        } finally {
            db.close()
        }
    }
}

private suspend fun findDuplicateGaugeName(
    context: android.content.Context,
    fingerprint: String,
): String? {
    return withContext(Dispatchers.IO) {
        val db =
            androidx.room.Room.databaseBuilder(
                context.applicationContext,
                MeetDatabase::class.java,
                "meet_database",
            ).build()
        try {
            db.savedGaugeDao()
                .getAll()
                .firstOrNull { saved ->
                    QrCodeSharing.fingerprintFor(saved.toQrGaugeConfig()) == fingerprint
                }
                ?.name
        } finally {
            db.close()
        }
    }
}

@Composable
private fun DiyGuidedHeader(
    steps: List<DiyGuidedStep>,
    currentIndex: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    if (steps.isEmpty()) return
    val boundedIndex = currentIndex.coerceIn(0, steps.lastIndex)
    val step = steps[boundedIndex]

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.16f),
                                Color(0xFF0A0E14),
                                Color(0xFF111827),
                            )
                    )
                )
                .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "GUÍA DIY DE INICIO A FIN",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "PASO ${boundedIndex + 1}/${steps.size}",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = step.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = step.summary,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp),
            )

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.18f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = step.cue,
                    color = accentColor.copy(alpha = 0.86f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                steps.forEachIndexed { index, _ ->
                    val selected = index == boundedIndex
                    Box(
                        modifier =
                            Modifier.weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) accentColor else Color.White.copy(alpha = 0.13f)
                                )
                                .clickable { onSelect(index) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiyGuideNavButton(
                    label = "ANTERIOR",
                    enabled = boundedIndex > 0,
                    accentColor = accentColor,
                    onClick = onPrev,
                )
                DiyGuideNavButton(
                    label = if (boundedIndex == steps.lastIndex) "FINAL" else "SIGUIENTE",
                    enabled = boundedIndex < steps.lastIndex,
                    accentColor = accentColor,
                    onClick = onNext,
                )
            }
        }
    }
}

@Composable
private fun DiyGuideNavButton(
    label: String,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.width(112.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (enabled) accentColor.copy(alpha = 0.16f)
                    else Color.White.copy(alpha = 0.05f)
                )
                .border(
                    1.dp,
                    if (enabled) accentColor.copy(alpha = 0.35f)
                    else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(10.dp),
                )
                .clickable(enabled = enabled) { onClick() }
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.28f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DiyReadinessStrip(
    statusItems: List<Pair<String, String>>,
    accentColor: Color,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        items(statusItems) { item ->
            DiyInsightChip(
                label = item.first,
                value = item.second,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun DiyInsightChip(
    label: String,
    value: String,
    accentColor: Color,
) {
    Box(
        modifier =
            Modifier.widthIn(min = 108.dp, max = 170.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0E14))
                .border(0.5.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = label.uppercase(),
                color = accentColor.copy(alpha = 0.72f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DiySectionHint(
    text: String,
    accentColor: Color,
) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 6.dp)
                .border(0.5.dp, accentColor.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.025f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun DiyQuickPresetCard(
    preset: DiyQuickPreset,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.width(154.dp)
                .height(104.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0A0E14))
                .border(1.dp, preset.color.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = preset.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset.description,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "APLICAR",
                color = preset.color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun diyBackgroundSummary(type: Int, preset: Int, uri: String): String =
    when (type) {
        0 -> "Gradiente base"
        1 -> "Preset ${preset + 1}"
        2 -> if (uri.isBlank()) "Imagen pendiente" else "Imagen propia"
        else -> "Fondo $type"
    }

private fun diyAnimationSummary(index: Int): String {
    val names =
        listOf(
            "Ninguna",
            "Fuego",
            "Rayos",
            "Nieve",
            "Lluvia",
            "Engranajes",
            "Galaxia",
            "Radar",
            "Matriz",
            "Aurora",
        )
    return names.getOrElse(index) { "Animación $index" }
}

// ═══════════════════════════════════════════════════════
// NEON COLOR SWATCH (single circle with glow effect)
// ═══════════════════════════════════════════════════════

@Composable
private fun NeonColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val inf = rememberInfiniteTransition(label = "swatch_${color.toArgb()}")
    val glow by
        inf.animateFloat(
            0.3f,
            0.8f,
            infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "swGlow",
        )

    Box(
        modifier =
            Modifier.size(38.dp)
                .drawBehind {
                    // Neon glow ring behind selected swatch
                    if (isSelected) {
                        drawCircle(
                            color = color.copy(alpha = glow * 0.45f),
                            radius = size.minDimension / 2f + 5.dp.toPx(),
                        )
                        drawCircle(
                            color = color.copy(alpha = glow * 0.2f),
                            radius = size.minDimension / 2f + 9.dp.toPx(),
                        )
                    }
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                color,
                                color.copy(alpha = 0.75f),
                            )
                    )
                )
                .then(
                    if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape)
                    else Modifier.border(1.dp, color.copy(alpha = 0.25f), CircleShape)
                )
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            // White checkmark with shadow
            Text(
                "✓",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) accentColor.copy(alpha = 0.2f) else Color(0x0CFFFFFF))
                .border(
                    1.dp,
                    if (active) accentColor else Color.White.copy(alpha = 0.1f),
                    RoundedCornerShape(12.dp),
                )
                .clickable { onClick() }
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun DiyOptionSelector(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    accentColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            color = accentColor.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEachIndexed { idx, label ->
                val isSelected = idx == selectedIndex
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.15f)
                                else Color(0x0CFFFFFF)
                            )
                            .border(
                                1.dp,
                                if (isSelected) accentColor else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onSelect(idx) }
                            .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
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
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    ) {
        AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0xFF0A0E14))
                .border(
                    width = if (isSelected) 1.5.dp else 0.5.dp,
                    color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { onClick() }
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                name,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
