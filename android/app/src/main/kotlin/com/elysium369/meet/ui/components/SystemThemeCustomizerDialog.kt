package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.elysium.theme.*
import kotlinx.coroutines.launch
import com.elysium369.meet.ui.theme.ThemeColors

// ═══════════════════════════════════════════════════════
// CUSTOMIZER TARGETS FOR SYSTEM THEME
// ═══════════════════════════════════════════════════════

private enum class SystemColorTarget(val label: String, val icon: String) {
    PRIMARY("Primario", "⚡"),
    SECONDARY("Secundario", "🔮"),
    TERTIARY("Terciario", "🚗"),
    QUATERNARY("Cuaternario", "🔬")
}

// ═══════════════════════════════════════════════════════
// MAIN SYSTEM THEME CUSTOMIZER DIALOG
// ═══════════════════════════════════════════════════════

@Composable
fun SystemThemeCustomizerDialog(
    onDismiss: () -> Unit,
    route: String = "home",
    initialScope: ThemeScope = ThemeScope.GLOBAL,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storedConfig by ElysiumThemeRepository.config.collectAsState()
    val persistenceFailure by ElysiumThemeRepository.failure.collectAsState()
    var selectedTarget by remember { mutableStateOf(SystemColorTarget.PRIMARY) }
    var scope by remember(route, initialScope) { mutableStateOf(initialScope) }
    val domain = ElysiumPaletteResolver.domain(route)
    val scopeTarget = when (scope) {
        ThemeScope.GLOBAL -> "global"
        ThemeScope.DOMAIN -> domain
        ThemeScope.ROUTE -> route
    }
    var draft by remember(route, initialScope) {
        mutableStateOf(ElysiumThemeRepository.config.value.overrideFor(initialScope,
            when (initialScope) {
                ThemeScope.GLOBAL -> "global"
                ThemeScope.DOMAIN -> domain
                ThemeScope.ROUTE -> route
            }))
    }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    val previewConfig = storedConfig.withOverride(scope, scopeTarget, draft)
    // An ancestor editor displays that ancestor's palette, without descendant overrides.
    val resolved = when (scope) {
        ThemeScope.GLOBAL -> ElysiumPaletteResolver.resolve(previewConfig.copy(domains = emptyMap(), routes = emptyMap()), domain, route)
        ThemeScope.DOMAIN -> ElysiumPaletteResolver.resolve(previewConfig.copy(routes = emptyMap()), domain, route)
        ThemeScope.ROUTE -> ElysiumPaletteResolver.resolve(previewConfig, domain, route)
    }
    fun color(index: Int) = Color(resolved.channel(index)!!.toInt())
    val currentTargetColor = color(selectedTarget.ordinal)
    val cancel = {
        if (!saving) {
            ElysiumThemeRepository.preview(null)
            onDismiss()
        }
    }
    fun edit(next: ElysiumPaletteOverride) {
        if (saving) return
        draft = next
        dirty = true
        saveError = null
    }
    fun selectScope(next: ThemeScope, copyPalette: Boolean = false) {
        if (!saving && (!dirty || copyPalette)) {
            scope = next
            val target = when (next) {
                ThemeScope.GLOBAL -> "global"
                ThemeScope.DOMAIN -> domain
                ThemeScope.ROUTE -> route
            }
            draft = if (copyPalette) resolved else storedConfig.overrideFor(next, target)
            dirty = copyPalette
            saveError = null
        }
    }
    LaunchedEffect(storedConfig, scope, scopeTarget, dirty) {
        if (!dirty) draft = storedConfig.overrideFor(scope, scopeTarget)
    }
    LaunchedEffect(previewConfig) { ElysiumThemeRepository.preview(previewConfig) }
    DisposableEffect(Unit) {
        ElysiumThemeRepository.initialize(context)
        onDispose { ElysiumThemeRepository.preview(null) }
    }

    val inf = rememberInfiniteTransition(label = "systemCustomizer")
    val borderGlow by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "systemBorderGlow"
    )

    Dialog(
        onDismissRequest = { cancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MeetColors.backgroundDark,
                            MeetColors.backgroundDeep,
                            MeetColors.backgroundDeep
                        )
                    )
                )
                .border(
                    1.5.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            currentTargetColor.copy(alpha = borderGlow * 0.6f),
                            currentTargetColor.copy(alpha = borderGlow * 0.15f),
                            currentTargetColor.copy(alpha = borderGlow * 0.4f)
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
                        "Identidad visual",
                        modifier = Modifier.weight(1f),
                        color = MeetColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Reset defaults
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22FF1744))
                                .clickable {
                                    edit(ElysiumPaletteOverride())
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AnimatedNeonGlyph(
                                    glyph = "↻",
                                    contentDescription = "Restablecer",
                                    tint = Color(0xFFFF1744),
                                    fontSize = 14.sp,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "Restablecer",
                                    color = Color(0xFFFF1744),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        // Close
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable(enabled = !saving) { cancel() },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedNeonGlyph(
                                glyph = "✕",
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                fontSize = 16.sp,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Text(
                    "Personaliza los colores primarios, secundarios y acentos del sistema Elysium Vanguard AI OS.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontFamily = FontFamily.SansSerif
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ThemeScope.entries.forEach { candidate ->
                        FilterChip(
                            selected = scope == candidate,
                            onClick = { selectScope(candidate) },
                            enabled = !saving && (!dirty || scope == candidate),
                            label = { Text(when (candidate) {
                                ThemeScope.GLOBAL -> "Global"
                                ThemeScope.DOMAIN -> "Dominio"
                                ThemeScope.ROUTE -> "Pantalla"
                            }, fontSize = 11.sp) },
                        )
                    }
                }
                Text(
                    when (scope) {
                        ThemeScope.GLOBAL -> "Toda la aplicación; respeta excepciones de dominio y pantalla."
                        ThemeScope.DOMAIN -> "Dominio: $domain; respeta excepciones de pantalla."
                        ThemeScope.ROUTE -> "Solo esta pantalla: $route"
                    },
                    color = MeetColors.textSecondary, fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                if (dirty) Text(
                    "Borrador sin guardar. Guarda o cancela antes de cambiar de alcance.",
                    color = MeetColors.textMuted, fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))

                // ══════════════════════════════════════
                // REAL-TIME PREVIEW WINDOW (Masculine & Premium)
                // ══════════════════════════════════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MeetColors.cardBackground)
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Title preview
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "ELYSIUM",
                                color = color(0),
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "VANGUARD",
                                color = color(1),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        
                        // Button appearance preview
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Primary Accent Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color(0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("IR AL SCANNER", color = MeetColors.backgroundDeep, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                            
                            // Secondary Accent Outlined Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, color(0).copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("CONECTAR", color = color(0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Navigation appearance preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.backgroundDark)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FakeNavItem("Inicio", color(0), isSelected = true)
                            FakeNavItem("Scanner", color(2), isSelected = false)
                            FakeNavItem("DTCs", MeetColors.error, isSelected = false)
                            FakeNavItem("Garage", color(1), isSelected = false)
                            FakeNavItem("PRO", color(3), isSelected = false)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ══════════════════════════════════════
                // TARGET TABS (Acento Primario, Secundario...)
                // ══════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SystemColorTarget.entries.forEach { target ->
                        val isSelected = target == selectedTarget
                        val targetColor = when (target) {
                            SystemColorTarget.PRIMARY -> color(0)
                            SystemColorTarget.SECONDARY -> color(1)
                            SystemColorTarget.TERTIARY -> color(2)
                            SystemColorTarget.QUATERNARY -> color(3)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) targetColor.copy(alpha = 0.2f)
                                    else Color(0x15FFFFFF)
                                )
                                .then(
                                    if (isSelected) Modifier.border(
                                        1.dp,
                                        targetColor.copy(alpha = 0.6f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    else Modifier
                                )
                                .clickable { selectedTarget = target }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedNeonGlyph(target.icon, contentDescription = null, fontSize = 14.sp)
                                Text(
                                    target.label,
                                    color = if (isSelected) targetColor else Color.White.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(enabled = !saving, onClick = { edit(draft.withChannel(selectedTarget.ordinal, null)) }) {
                        Text(if (scope == ThemeScope.GLOBAL) "Predeterminado" else "Heredar canal", fontSize = 11.sp)
                    }
                    TextButton(enabled = !saving, onClick = {
                        edit(draft.withChannel(selectedTarget.ordinal, ElysiumPaletteResolver.defaults.channel(selectedTarget.ordinal)))
                    }) { Text("Restablecer canal", fontSize = 11.sp) }
                }
                // Divider line
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .background(currentTargetColor.copy(alpha = 0.15f))
                )

                Spacer(Modifier.height(4.dp))

                // ══════════════════════════════════════
                // COLOR PRESENTS GRID (scrollable)
                // ══════════════════════════════════════
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                ) {
                    ThemeColors.FULL_COLOR_PALETTE.forEach { category ->
                        // Category Label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp, top = 10.dp)
                        ) {
                            AnimatedNeonGlyph(category.icon, contentDescription = null, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                category.title,
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        }

                        // Swatches grid (rows of 6)
                        val columns = ((LocalConfiguration.current.screenWidthDp * 0.92f - 32f) / 46f).toInt().coerceIn(2, 6)
                        category.colors.chunked(columns).forEach { rowColors ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowColors.forEach { entry ->
                                    NeonSystemSwatch(
                                        color = entry.color,
                                        isSelected = entry.color.toArgb() == currentTargetColor.toArgb(),
                                        onClick = {
                                            if (!saving) edit(draft.withChannel(
                                                selectedTarget.ordinal,
                                                entry.color.toArgb().toLong() and 0xFFFFFFFFL,
                                            ))
                                        }
                                    )
                                }
                                // Spacer padding
                                repeat(columns - rowColors.size) {
                                    Spacer(Modifier.size(38.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(enabled = !saving, onClick = { selectScope(ThemeScope.DOMAIN, copyPalette = true) }) {
                        Text("Aplicar al dominio", fontSize = 10.sp)
                    }
                    TextButton(enabled = !saving, onClick = { selectScope(ThemeScope.GLOBAL, copyPalette = true) }) {
                        Text("Aplicar a global", fontSize = 10.sp)
                    }
                }
                (saveError ?: persistenceFailure)?.let { message ->
                    Text(message, color = MeetColors.error, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !saving, onClick = { cancel() }) { Text("Cancelar") }
                    Button(enabled = !saving, onClick = {
                        saving = true
                        coroutineScope.launch {
                            if (ElysiumThemeRepository.save(scope, scopeTarget, draft)) {
                                ElysiumThemeRepository.preview(null)
                                onDismiss()
                            } else {
                                saveError = ElysiumThemeRepository.failure.value ?: "No se pudo guardar el tema. Inténtalo otra vez."
                                saving = false
                            }
                        }
                    }) { Text(if (saving) "Guardando…" else "Guardar alcance") }
                }
            }
        }
    }
}

@Composable
private fun FakeNavItem(label: String, color: Color, isSelected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isSelected) color else MeetColors.textMuted)
        )
        Text(
            label,
            color = if (isSelected) color else MeetColors.textMuted,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NeonSystemSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "sysSwatch_${color.toArgb()}")
    val glow by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sysSwGlow"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .drawBehind {
                if (isSelected) {
                    drawCircle(
                        color = color.copy(alpha = glow * 0.45f),
                        radius = size.minDimension / 2f + 4.dp.toPx()
                    )
                    drawCircle(
                        color = color.copy(alpha = glow * 0.2f),
                        radius = size.minDimension / 2f + 8.dp.toPx()
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
                else Modifier.border(1.dp, color.copy(alpha = 0.2f), CircleShape)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Text(
                "✓",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
