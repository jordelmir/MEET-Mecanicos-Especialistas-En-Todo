package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════════════════════════════
// COMPONENT LOCATOR — Interactive Engine Diagram & Part Finder
// ═══════════════════════════════════════════════════════════════

/** Normalized position (0f..1f) within the engine diagram canvas */
data class ComponentInfo(
    val id: String,
    val name: String,
    val category: ComponentCategory,
    val description: String,
    val xNorm: Float,
    val yNorm: Float,
    val commonFailures: List<String> = emptyList(),
    val relatedPids: List<String> = emptyList(),
    val relatedDtcs: List<String> = emptyList()
)

enum class ComponentCategory(val label: String, val color: Color) {
    ENGINE("Motor", MeetColors.warning),
    FUEL("Combustible", MeetColors.warning),
    COOLING("Enfriamiento", MeetColors.cyberCyan),
    ELECTRICAL("Eléctrico", MeetColors.electricBlue),
    INTAKE("Admisión", MeetColors.neonGreen),
    EXHAUST("Escape", MeetColors.error),
    SENSORS("Sensores", MeetColors.cyberCyan)
}

@Composable
fun ComponentLocatorScreen(
    navController: NavController
) {
    val components = remember { buildComponentDatabase() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedComponent by remember { mutableStateOf<ComponentInfo?>(null) }
    var selectedCategory by remember { mutableStateOf<ComponentCategory?>(null) }

    val filteredComponents = remember(searchQuery, selectedCategory) {
        components.filter { c ->
            (searchQuery.isBlank() || c.name.contains(searchQuery, ignoreCase = true)) &&
            (selectedCategory == null || c.category == selectedCategory)
        }
    }

    // Pulse animation for selected component
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by pulseAnim.animateFloat(
        initialValue = 8f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "pr"
    )
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.8f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOut), RepeatMode.Reverse),
        label = "pa"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                "Localizador de Componentes",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Search Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, "Search", tint = MeetColors.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) Text("Buscar componente...", color = MeetColors.textMuted, fontSize = 15.sp)
                    inner()
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Category Filter Chips ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ComponentCategory.entries.forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) cat.color.copy(alpha = 0.25f) else Color.Transparent)
                        .border(1.dp, if (isSelected) cat.color else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable { selectedCategory = if (isSelected) null else cat }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(cat.label, color = if (isSelected) cat.color else MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Engine Diagram Canvas ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(16.dp))
        ) {
            val textMeasurer = rememberTextMeasurer()

            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                drawEngineDiagram(this)

                // Draw component markers
                filteredComponents.forEach { comp ->
                    val cx = comp.xNorm * size.width
                    val cy = comp.yNorm * size.height
                    val isSelected = selectedComponent?.id == comp.id
                    val markerColor = comp.category.color

                    if (isSelected) {
                        // Pulsing ring
                        drawCircle(markerColor.copy(alpha = pulseAlpha), radius = pulseRadius, center = Offset(cx, cy))
                    }
                    // Dot
                    drawCircle(Color.Black, radius = 7f, center = Offset(cx, cy))
                    drawCircle(markerColor, radius = 5f, center = Offset(cx, cy))

                    if (isSelected) {
                        // Label
                        val labelResult = textMeasurer.measure(
                            AnnotatedString(comp.name),
                            style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        val lx = (cx - labelResult.size.width / 2).coerceIn(0f, size.width - labelResult.size.width)
                        val ly = (cy - 22f).coerceAtLeast(0f)
                        drawRoundRect(Color.Black.copy(alpha = 0.7f), Offset(lx - 4, ly - 2), Size(labelResult.size.width + 8f, labelResult.size.height + 4f), CornerRadius(4f))
                        drawText(labelResult, topLeft = Offset(lx, ly))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Component List ──
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredComponents, key = { it.id }) { comp ->
                ComponentCard(
                    component = comp,
                    isSelected = selectedComponent?.id == comp.id,
                    onSelect = { selectedComponent = if (selectedComponent?.id == comp.id) null else comp }
                )
            }
        }
    }
}

@Composable
private fun ComponentCard(
    component: ComponentInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) component.category.color else MeetColors.borderSubtle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) component.category.color.copy(alpha = 0.08f) else MeetColors.cardBackground)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .then(if (isSelected) Modifier.neonGlow(component.category.color, RoundedCornerShape(12.dp), 2f, 8f) else Modifier)
            .clickable { onSelect() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(component.category.color, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                component.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                component.category.label, color = component.category.color, fontSize = 11.sp
            )
        }

        if (isSelected) {
            Spacer(Modifier.height(8.dp))
            Text(component.description, color = MeetColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)

            if (component.commonFailures.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Fallas Comunes:", color = MeetColors.warning, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                component.commonFailures.forEach { failure ->
                    Text("• $failure", color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }

            if (component.relatedDtcs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DTCs:", color = MeetColors.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    component.relatedDtcs.forEach { dtc ->
                        Text(
                            dtc, color = MeetColors.error, fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MeetColors.error.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            if (component.relatedPids.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PIDs:", color = MeetColors.electricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    component.relatedPids.forEach { pid ->
                        Text(
                            pid, color = MeetColors.electricBlue, fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MeetColors.electricBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Engine Diagram Drawing ──

private fun drawEngineDiagram(scope: DrawScope) {
    val w = scope.size.width
    val h = scope.size.height
    val gridColor = MeetColors.cardBackground
    val outlineColor = MeetColors.cardBackgroundLighter

    // Grid
    for (i in 0..20) {
        val x = w * i / 20f
        scope.drawLine(gridColor, Offset(x, 0f), Offset(x, h), 0.5f)
    }
    for (i in 0..12) {
        val y = h * i / 12f
        scope.drawLine(gridColor, Offset(0f, y), Offset(w, y), 0.5f)
    }

    // Engine block outline
    val blockPath = Path().apply {
        moveTo(w * 0.15f, h * 0.2f)
        lineTo(w * 0.65f, h * 0.2f)
        lineTo(w * 0.65f, h * 0.85f)
        lineTo(w * 0.15f, h * 0.85f)
        close()
    }
    scope.drawPath(blockPath, outlineColor, style = Stroke(2f))

    // Cylinder bores (inline-4)
    for (i in 0..3) {
        val cx = w * (0.25f + i * 0.1f)
        scope.drawCircle(outlineColor.copy(alpha = 0.6f), radius = w * 0.035f, center = Offset(cx, h * 0.45f), style = Stroke(1.5f))
    }

    // Valve cover
    scope.drawRoundRect(
        outlineColor.copy(alpha = 0.4f),
        Offset(w * 0.17f, h * 0.22f), Size(w * 0.46f, h * 0.12f),
        CornerRadius(4f), style = Stroke(1f)
    )

    // Intake manifold (right side)
    val intakePath = Path().apply {
        moveTo(w * 0.65f, h * 0.3f)
        cubicTo(w * 0.78f, h * 0.25f, w * 0.85f, h * 0.3f, w * 0.88f, h * 0.38f)
        lineTo(w * 0.88f, h * 0.58f)
        cubicTo(w * 0.85f, h * 0.65f, w * 0.78f, h * 0.7f, w * 0.65f, h * 0.65f)
    }
    scope.drawPath(intakePath, MeetColors.neonGreen.copy(alpha = 0.3f), style = Stroke(1.5f))

    // Exhaust manifold (left side)
    val exhaustPath = Path().apply {
        moveTo(w * 0.15f, h * 0.35f)
        cubicTo(w * 0.08f, h * 0.4f, w * 0.05f, h * 0.5f, w * 0.04f, h * 0.6f)
        lineTo(w * 0.04f, h * 0.85f)
        lineTo(w * 0.08f, h * 0.92f)
    }
    scope.drawPath(exhaustPath, MeetColors.error.copy(alpha = 0.3f), style = Stroke(1.5f))

    // Oil pan
    scope.drawRoundRect(
        outlineColor.copy(alpha = 0.3f),
        Offset(w * 0.2f, h * 0.85f), Size(w * 0.4f, h * 0.1f),
        CornerRadius(6f), style = Stroke(1f)
    )

    // Timing chain area
    scope.drawRoundRect(
        outlineColor.copy(alpha = 0.35f),
        Offset(w * 0.12f, h * 0.2f), Size(w * 0.06f, h * 0.65f),
        CornerRadius(3f), style = Stroke(1f)
    )
}

// ── Component Database ──

private fun buildComponentDatabase(): List<ComponentInfo> = listOf(
    // ENGINE
    ComponentInfo("spark_plugs", "Bujías", ComponentCategory.ENGINE, "Generan la chispa que inicia la combustión. Se ubican en la parte superior del bloque, una por cilindro.", 0.3f, 0.28f,
        commonFailures = listOf("Electrodo desgastado", "Fouling por mezcla rica", "Gap incorrecto"),
        relatedPids = listOf("010C", "010E"), relatedDtcs = listOf("P0300", "P0301")),
    ComponentInfo("ignition_coils", "Bobinas de Encendido", ComponentCategory.ELECTRICAL, "Transforman 12V del sistema eléctrico a ~40,000V para las bujías. Montadas sobre cada bujía en sistemas COP.", 0.35f, 0.24f,
        commonFailures = listOf("Cortocircuito interno", "Grietas en la bota aislante"),
        relatedPids = listOf("010E"), relatedDtcs = listOf("P0351", "P0352")),
    ComponentInfo("injectors", "Inyectores", ComponentCategory.FUEL, "Atomizan combustible a alta presión dentro de cada cilindro. Controlados por la PCM con pulsos eléctricos.", 0.4f, 0.38f,
        commonFailures = listOf("Obstrucción por depósitos", "Goteo (no sella)"),
        relatedPids = listOf("0106", "0107"), relatedDtcs = listOf("P0201", "P0202")),
    ComponentInfo("throttle_body", "Cuerpo de Aceleración", ComponentCategory.INTAKE, "Controla la cantidad de aire que entra al motor. En sistemas electrónicos, la mariposa se mueve por motor DC.", 0.78f, 0.32f,
        commonFailures = listOf("Carbón acumulado en mariposa", "Motor de mariposa fallido"),
        relatedPids = listOf("0111"), relatedDtcs = listOf("P0121", "P2135")),
    ComponentInfo("maf_sensor", "Sensor MAF", ComponentCategory.SENSORS, "Mide el flujo másico de aire entrante. Ubicado entre el filtro de aire y el cuerpo de aceleración.", 0.88f, 0.28f,
        commonFailures = listOf("Filamento contaminado", "Cortocircuito"),
        relatedPids = listOf("0110"), relatedDtcs = listOf("P0100", "P0102")),
    ComponentInfo("map_sensor", "Sensor MAP", ComponentCategory.SENSORS, "Mide la presión absoluta del múltiple de admisión. Usado para calcular carga del motor.", 0.72f, 0.42f,
        commonFailures = listOf("Manguera de vacío rota", "Sensor dañado"),
        relatedPids = listOf("010B"), relatedDtcs = listOf("P0105", "P0107")),
    ComponentInfo("o2_upstream", "Sensor O2 (Pre-Cat)", ComponentCategory.SENSORS, "Mide oxígeno residual en el escape ANTES del catalizador. La PCM usa esta señal para ajustar la mezcla aire-combustible.", 0.08f, 0.5f,
        commonFailures = listOf("Respuesta lenta (envejecido)", "Calentador abierto"),
        relatedPids = listOf("0114", "0134"), relatedDtcs = listOf("P0130", "P0135")),
    ComponentInfo("o2_downstream", "Sensor O2 (Post-Cat)", ComponentCategory.SENSORS, "Mide eficiencia del catalizador comparando O2 antes y después. Señal estable = catalizador funcional.", 0.06f, 0.75f,
        commonFailures = listOf("Contaminación por anticongelante", "Cable dañado"),
        relatedPids = listOf("0115"), relatedDtcs = listOf("P0136", "P0141")),
    ComponentInfo("catalytic_conv", "Catalizador", ComponentCategory.EXHAUST, "Convierte gases nocivos (CO, HC, NOx) en agua y CO2. Se deteriora por sobrecalentamiento o mezcla rica prolongada.", 0.07f, 0.62f,
        commonFailures = listOf("Eficiencia baja (envejecido)", "Sustrato fundido/obstruido"),
        relatedDtcs = listOf("P0420", "P0430")),
    ComponentInfo("coolant_temp", "Sensor Temp. Refrigerante (ECT)", ComponentCategory.COOLING, "Mide temperatura del refrigerante del motor. Afecta inyección, tiempo de encendido y ventilador.", 0.55f, 0.3f,
        commonFailures = listOf("Lectura errática", "Cortocircuito a tierra"),
        relatedPids = listOf("0105"), relatedDtcs = listOf("P0115", "P0117")),
    ComponentInfo("thermostat", "Termostato", ComponentCategory.COOLING, "Válvula que regula flujo de refrigerante. Cerrado en frío (calentamiento rápido), abierto en caliente.", 0.5f, 0.22f,
        commonFailures = listOf("Atascado abierto (no calienta)", "Atascado cerrado (sobrecalienta)"),
        relatedPids = listOf("0105"), relatedDtcs = listOf("P0128")),
    ComponentInfo("water_pump", "Bomba de Agua", ComponentCategory.COOLING, "Circula refrigerante por el bloque, cabeza y radiador. Accionada por banda o eléctrica.", 0.13f, 0.52f,
        commonFailures = listOf("Fuga por sello", "Impeller corroído", "Rodamiento ruidoso")),
    ComponentInfo("alternator", "Alternador", ComponentCategory.ELECTRICAL, "Genera electricidad para recargar la batería y alimentar el sistema eléctrico. Accionado por banda serpentina.", 0.2f, 0.7f,
        commonFailures = listOf("Diodos rectificadores quemados", "Regulador de voltaje fallido"),
        relatedPids = listOf("0142"), relatedDtcs = listOf("P0562", "P0563")),
    ComponentInfo("crankshaft_sensor", "Sensor Cigüeñal (CKP)", ComponentCategory.SENSORS, "Mide la posición y velocidad de rotación del cigüeñal. Señal crítica para sincronización de inyección y chispa.", 0.35f, 0.88f,
        commonFailures = listOf("Gap incorrecto", "Cable dañado por calor"),
        relatedPids = listOf("010C"), relatedDtcs = listOf("P0335", "P0336")),
    ComponentInfo("camshaft_sensor", "Sensor Árbol de Levas (CMP)", ComponentCategory.SENSORS, "Identifica la posición del árbol de levas para sincronización secuencial de inyectores.", 0.18f, 0.28f,
        commonFailures = listOf("Señal intermitente", "Contaminación por aceite"),
        relatedDtcs = listOf("P0340", "P0341")),
    ComponentInfo("egr_valve", "Válvula EGR", ComponentCategory.EXHAUST, "Recircula gases de escape hacia la admisión para reducir NOx. Se obstruye con carbón frecuentemente.", 0.62f, 0.55f,
        commonFailures = listOf("Obstrucción por carbón", "Diafragma roto"),
        relatedDtcs = listOf("P0401", "P0402")),
    ComponentInfo("evap_purge", "Válvula Purga EVAP", ComponentCategory.FUEL, "Controla el flujo de vapores de combustible del canister de carbón hacia el motor para quemarlos.", 0.7f, 0.6f,
        commonFailures = listOf("Atascada abierta/cerrada", "Fuga en conector"),
        relatedDtcs = listOf("P0441", "P0446")),
    ComponentInfo("fuel_pump", "Bomba de Gasolina", ComponentCategory.FUEL, "Suministra combustible a presión desde el tanque. Ubicada dentro del tanque en la mayoría de vehículos modernos.", 0.85f, 0.88f,
        commonFailures = listOf("Presión baja", "Ruido excesivo"),
        relatedPids = listOf("010A"), relatedDtcs = listOf("P0230", "P0087")),
    ComponentInfo("iat_sensor", "Sensor Temp. Aire (IAT)", ComponentCategory.SENSORS, "Mide la temperatura del aire de admisión. Usado por la PCM para corregir densidad del aire.", 0.82f, 0.4f,
        commonFailures = listOf("Lectura alta falsa", "Circuito abierto"),
        relatedPids = listOf("010F"), relatedDtcs = listOf("P0110", "P0112")),
    ComponentInfo("oil_pan", "Cárter de Aceite", ComponentCategory.ENGINE, "Reservorio inferior de aceite del motor. Contiene el sensor de nivel/presión de aceite.", 0.4f, 0.92f,
        commonFailures = listOf("Fuga por empaque", "Tapón de drenaje dañado"))
)

