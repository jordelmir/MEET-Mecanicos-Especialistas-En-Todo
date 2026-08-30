package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════════
// GUÍA PRO DE QUÍMICOS & HERRAMIENTAS
// ═══════════════════════════════════════════

private data class ProProduct(
    val id: String,
    val name: String,
    val brand: String,
    val category: String,
    val useCase: String,
    val proTip: String,
    val application: String,
    val systems: List<String>,
    val safetyWarnings: List<String>,
    val rating: Float
)

private val productCategories = listOf(
    "🧪 Todos", "🔥 Soldadura", "⚡ Eléctricos", "🛢️ Lubricantes",
    "🧴 Limpiadores", "💧 Refrigerantes", "🔋 Aditivos", "🛡️ Selladores", "⚙️ Herramientas Pro"
)

private val categoryColors = mapOf(
    "🔥 Soldadura" to Color(0xFFFF6B35),
    "⚡ Eléctricos" to Color(0xFF00E5FF),
    "🛢️ Lubricantes" to Color(0xFFFFD700),
    "🧴 Limpiadores" to Color(0xFF76FF03),
    "💧 Refrigerantes" to Color(0xFF40C4FF),
    "🔋 Aditivos" to Color(0xFFE040FB),
    "🛡️ Selladores" to Color(0xFFFF5252),
    "⚙️ Herramientas Pro" to Color(0xFFB0BEC5)
)

private val allProducts = listOf(
    // ── SOLDADURA ──
    ProProduct("s1", "Alambre MIG ER70S-6 (0.035\")", "Lincoln Electric", "🔥 Soldadura",
        "Reparación de chasis, soportes de motor, brackets de escape. El más versátil para acero al carbón en taller mecánico.",
        "Usa gas 75% Argón / 25% CO2 para mejor penetración en acero grueso. Para lámina de carrocería usa 100% CO2 con voltaje bajo.",
        "Ajusta velocidad de alambre según espesor: 3mm→300ipm, 5mm→350ipm. Limpia la pieza con amoladora antes de soldar.",
        listOf("Chasis", "Escape", "Soportes Motor"), listOf("Careta autodark mín. shade 10", "Guantes de cuero", "Ventilación obligatoria"), 4.8f),
    ProProduct("s2", "Alambre TIG ER4043 Aluminio", "Hobart", "🔥 Soldadura",
        "Reparación de radiadores de aluminio, carcasas de transmisión, intercoolers y piezas de motor de aleación ligera.",
        "Pre-calienta la pieza de aluminio a 150°C con pistola de calor. Usa corriente AC con balance de limpieza al 70%. Electrodo de tungsteno puro (verde).",
        "Limpia con cepillo de acero inoxidable (nunca de acero normal). Prepara bisel en V para piezas >3mm.",
        listOf("Radiador", "Transmisión", "Intercooler"), listOf("Careta TIG shade 12+", "Guantes TIG largos", "Gas argón 100%"), 4.5f),
    ProProduct("s3", "Electrodos E6013 (3/32\")", "ESAB", "🔥 Soldadura",
        "Reparaciones generales, soldadura de posición, uniones no críticas. Fácil de usar para principiantes.",
        "Ideal para soldaduras en todas las posiciones. El arco es suave y fácil de controlar. Seca los electrodos a 100°C si están húmedos.",
        "Amperaje: 60-90A para 3/32\". Ángulo de arrastre 10-15°. Velocidad constante.",
        listOf("General", "Brackets", "Bisagras"), listOf("Careta shade 10", "Guantes", "Manga larga"), 4.2f),
    ProProduct("s4", "Metabo Amoladora Angular 125mm (W 9-125)", "Metabo", "🔥 Soldadura",
        "Preparación de superficies antes de soldar, corte de metal, desbaste de cordones de soldadura, limpieza de óxido.",
        "Usa disco de desbaste para preparar biseles. Disco de corte para seccionar tubos de escape. Disco flap para acabado fino.",
        "RPM: 11,000. Usa disco según material: acero→óxido aluminio, inox→zirconio, aluminio→carburo de silicio.",
        listOf("Preparación", "Corte", "Desbaste"), listOf("Lentes de seguridad ANSI Z87+", "Protección auditiva", "Guantes anti-vibración"), 4.9f),

    // ── ELÉCTRICOS ──
    ProProduct("e1", "Limpiador de Contactos QD", "CRC", "⚡ Eléctricos",
        "Limpieza de conectores OBD-II, sensores (MAF, O2, TPS), terminales de relés, fusibles con corrosión.",
        "Aplica en el conector desconectado y deja secar 30 segundos antes de reconectar. No apliques con el circuito energizado.",
        "Rocía directamente en pins del conector. Usa cepillo antiestático para corrosión severa. Seca con aire comprimido.",
        listOf("Sensores", "Conectores", "ECU", "OBD Port"), listOf("Ventilación — vapores inflamables", "No inhalar", "Lejos de fuentes de ignición"), 4.7f),
    ProProduct("e2", "Grasa Dieléctrica", "Permatex", "⚡ Eléctricos",
        "Protección de conectores eléctricos contra humedad y corrosión. Esencial para sensores O2, bobinas COP, y terminales de batería.",
        "Aplica una capa FINA en el interior del conector hembra antes de ensamblar. NO la apliques en los pins macho — puede interferir con el contacto eléctrico.",
        "Usa un palillo o pincel pequeño. Cubre solo las paredes del conector, no los contactos metálicos.",
        listOf("Conectores", "Bobinas", "O2 Sensors", "Batería"), listOf("No tóxica", "Lávate las manos después"), 4.8f),
    ProProduct("e3", "Protector de Terminales de Batería", "CRC", "⚡ Eléctricos",
        "Previene corrosión en bornes de batería. Esencial después de limpiar terminales con bicarbonato o limpiador especial.",
        "Limpia primero con cepillo de batería + bicarbonato. Seca bien. Reconecta y aprieta a 8-10 Nm. LUEGO aplica el spray rojo sobre los bornes.",
        "Rocía una capa uniforme cubriendo borne, terminal y primeros 2cm del cable. Reaplicar cada 6 meses.",
        listOf("Batería 12V", "Arranque", "Alternador"), listOf("Spray presurizado", "No rociar cerca de llamas"), 4.4f),
    ProProduct("e4", "Cinta Aislante Super 33+", "3M", "⚡ Eléctricos",
        "Aislamiento profesional de empalmes, reparación de arneses, protección de cables expuestos. Resistente hasta 221°F (105°C).",
        "Estira la cinta al 50% mientras la aplicas — esto activa el adhesivo y sella mejor. Mínimo 3 capas solapadas al 50%.",
        "Envuelve en espiral con 50% de traslape. Para zonas de calor (escape), usa termocontraíble en su lugar.",
        listOf("Arneses", "Cables", "Empalmes"), listOf("Material no tóxico", "Rated 600V"), 4.6f),

    // ── LUBRICANTES ──
    ProProduct("l1", "WD-40 Specialist Penetrante", "WD-40", "🛢️ Lubricantes",
        "Afloja birlos oxidados de escape, múltiples, sensores O2 atascados, pernos de suspensión congelados por corrosión.",
        "Aplica la noche anterior al trabajo. La gravedad y el tiempo hacen la magia. Para birlos de escape, calienta con soplete DESPUÉS de aplicar (el penetrante ya se habrá evaporado).",
        "Aplica generosamente en la unión roscada. Espera mínimo 15 minutos. Repite si es necesario. Golpea con martillo para generar vibración.",
        listOf("Escape", "Suspensión", "Frenos", "Motor"), listOf("Inflamable", "Ventilación", "No aplicar en superficies calientes"), 4.7f),
    ProProduct("l2", "Grasa Blanca de Litio", "WD-40 Specialist", "🛢️ Lubricantes",
        "Lubricación de bisagras de puertas, mecanismos de seguros, correderas de asiento, cables de freno de mano.",
        "La grasa de litio resiste el agua mejor que la grasa estándar. Ideal para componentes expuestos. No usar en frenos o embrague.",
        "Aplica en spray o con espátula. Para cables, inyecta en la funda desde arriba y deja que la gravedad distribuya.",
        listOf("Puertas", "Cerraduras", "Cables", "Mecanismos"), listOf("No tóxica", "Evitar contacto con ojos"), 4.5f),
    ProProduct("l3", "Anti-Seize Cobre para Bujías", "Permatex", "🛢️ Lubricantes",
        "Previene que bujías, birlos de escape y sensores O2 se suelden por calor al cilindro/múltiple. ESENCIAL en motores de aluminio.",
        "Aplica SOLO en las roscas, NUNCA en el electrodo. Una capa fina. Reduce el torque de apriete un 20% cuando uses anti-seize.",
        "Dedo limpio: aplica una gota en la punta del dedo y pasa por las roscas. O usa pincel desechable.",
        listOf("Bujías", "Escape", "Sensores O2", "Frenos"), listOf("Contiene cobre — no ingerir", "Lavar manos"), 4.9f),
    ProProduct("l4", "Lubricante de Silicona para Sellos", "CRC", "🛢️ Lubricantes",
        "Lubricación de O-rings, sellos de goma, bujes de goma, juntas tóricas de sistemas de combustible y refrigeración.",
        "La silicona NO daña los sellos de goma como lo hacen los lubricantes base petróleo. Úsala siempre al instalar O-rings nuevos.",
        "Aplica en spray o con dedo limpio sobre el O-ring antes de instalar. Facilita el montaje y previene cortes del sello.",
        listOf("O-rings", "Sellos", "Sistema Combustible", "A/C"), listOf("No tóxica", "Segura para plásticos y gomas"), 4.6f),

    // ── LIMPIADORES ──
    ProProduct("c1", "Limpiador de Frenos Brakleen", "CRC", "🧴 Limpiadores",
        "Desengrase de componentes de freno, limpieza de superficies antes de sellado, desengrase rápido de piezas metálicas.",
        "También sirve como desengrasante universal rápido. Ideal para limpiar la superficie del volante del embrague antes de instalar clutch nuevo.",
        "Rocía abundantemente sobre la pieza. Deja drenar los residuos. No requiere enjuague. Seca al aire en segundos.",
        listOf("Frenos", "Embrague", "General"), listOf("MUY inflamable", "Ventilación obligatoria", "No inhalar vapores", "Guantes de nitrilo"), 4.8f),
    ProProduct("c2", "Limpiador de Sensor MAF", "CRC", "🧴 Limpiadores",
        "Limpieza del sensor de flujo de aire masivo (MAF). Restaura lecturas correctas y elimina P0101/P0102/P0103.",
        "NUNCA toques el elemento sensor con los dedos ni con trapo. Solo spray a 15cm de distancia. 5-6 aplicaciones son suficientes.",
        "Desconecta conector eléctrico → retira sensor → rocía elemento caliente y frío → seca 10 min → reinstala.",
        listOf("MAF Sensor", "Admisión"), listOf("No usar limpiadores genéricos — dañan el sensor", "Seca completamente antes de instalar"), 4.9f),
    ProProduct("c3", "Limpiador de Cuerpo de Aceleración", "CRC", "🧴 Limpiadores",
        "Limpieza del cuerpo de aceleración (throttle body), válvula IAC, y conductos de admisión con depósitos de carbón.",
        "Desconecta conector del TPS/motor de mariposa antes de limpiar. En autos con acelerador electrónico, no fuerces la mariposa — puede dañar el motor DC.",
        "Rocía dentro del cuerpo con la mariposa abierta. Usa trapo de microfibra para frotar depósitos. Repite hasta que quede limpio.",
        listOf("Cuerpo Aceleración", "IAC", "Admisión"), listOf("Inflamable", "Vapores tóxicos", "Usar en área ventilada"), 4.7f),

    // ── REFRIGERANTES ──
    ProProduct("r1", "Refrigerante OAT (Dex-Cool Compatible)", "Prestone", "💧 Refrigerantes",
        "Para GM, Chrysler y vehículos que usan refrigerante naranja/rojo. Vida útil extendida de 5 años / 240,000 km.",
        "NUNCA mezcles OAT con IAT (verde). La mezcla genera gel que tapa el radiador y sobrecalienta el motor. Si no sabes cuál tiene, haz flush completo.",
        "Mezcla 50/50 con agua destilada (NUNCA agua de la llave — los minerales causan corrosión). Verifica protección con refractómetro.",
        listOf("Radiador", "Motor", "Calefacción"), listOf("Tóxico — peligroso para mascotas y niños", "No ingerir", "Lavar derrames inmediatamente"), 4.6f),
    ProProduct("r2", "Refrigerante IAT Verde Convencional", "Zerex", "💧 Refrigerantes",
        "Para vehículos clásicos y japoneses que especifican refrigerante verde. Contiene silicatos para protección de aluminio.",
        "Cambiar cada 2 años / 50,000 km. Los silicatos se degradan con el tiempo y dejan de proteger. Ideal para motores con mucho aluminio (Toyota, Honda).",
        "Flush con agua destilada antes de rellenar. Purga aire del sistema abriendo la válvula de purga o el sensor ECT.",
        listOf("Radiador", "Motor", "Bomba de Agua"), listOf("Tóxico", "Mantener alejado de mascotas"), 4.4f),

    // ── ADITIVOS ──
    ProProduct("a1", "Tratamiento de Combustible", "Techron (Chevron)", "🔋 Aditivos",
        "Limpieza profunda de inyectores, válvulas de admisión y cámara de combustión. El más recomendado por ingenieros automotrices.",
        "Usa cada 5,000 km o cuando sientas pérdida de potencia/ralentí irregular. Agrega al tanque lleno. Un solo bote trata hasta 80 litros.",
        "Agrega al tanque ANTES de llenar gasolina para mejor mezcla. Maneja normalmente — el tratamiento trabaja mientras conduces.",
        listOf("Inyectores", "Válvulas", "Combustión"), listOf("Inflamable", "No ingerir"), 4.8f),
    ProProduct("a2", "Sea Foam Motor Treatment", "Sea Foam", "🔋 Aditivos",
        "Triple función: limpia inyectores (por tanque), descarboniza (por admisión), estabiliza combustible almacenado.",
        "Para descarbonizar: aspira Sea Foam por la manguera de vacío del booster con motor a 2000 RPM. Apaga 10 min. Arranca y espera la humareda blanca.",
        "En tanque: 1oz por galón. Por admisión: 1/3 del bote aspirado lentamente. En aceite: agregar 500 km antes del cambio.",
        listOf("Inyectores", "EGR", "Admisión", "Cámara Combustión"), listOf("Inflamable", "Genera mucho humo al descarbonizar — hacer al aire libre"), 4.7f),
    ProProduct("a3", "Regenerador de DPF Diesel", "Liqui Moly", "🔋 Aditivos",
        "Reduce la temperatura de quemado del hollín en el DPF, facilitando regeneraciones pasivas y activas.",
        "Agrega cada 5,000 km preventivamente en diesels con DPF. Si ya tienes la luz DPF encendida, agrega y maneja 30 min a velocidad de carretera (RPM altas).",
        "Agregar al tanque antes de llenar diesel. El aditivo baja el punto de ignición del hollín de 600°C a 450°C.",
        listOf("DPF", "Escape Diesel", "Motor Diesel"), listOf("Solo para motores diesel", "No usar en gasolina"), 4.5f),

    // ── SELLADORES ──
    ProProduct("se1", "Silicón RTV Negro Ultra Black", "Permatex", "🛡️ Selladores",
        "Formador de juntas universal. Resiste hasta 500°F (260°C). Flexible, resistente al aceite, refrigerante y transmisión.",
        "Deja curar 24 horas antes de agregar fluidos. Para mejor adhesión, limpia ambas superficies con limpiador de frenos. Aplica cordón de 3mm.",
        "Aplica cordón continuo alrededor de toda la superficie de sellado. Ensambla en los primeros 10 minutos.",
        listOf("Carter", "Tapa Válvulas", "Bomba de Agua", "Transmisión"), listOf("Genera vapores de ácido acético al curar", "Ventilar"), 4.9f),
    ProProduct("se2", "Fijador de Roscas Azul 242", "Loctite", "🛡️ Selladores",
        "Fijación media para tornillería que necesita mantenimiento futuro. Previene aflojamiento por vibración.",
        "AZUL = desmontable con herramienta manual. ROJO = permanente (necesita calor para aflojar). Para birlos de llanta NUNCA uses Loctite.",
        "Una gota en las primeras 3-4 roscas del birlo. Ensambla y aprieta al torque especificado. Cura en 24h, fuerza inicial en 10 min.",
        listOf("Tornillería General", "Soportes", "Accesorios"), listOf("Irritante de piel", "Guantes de nitrilo"), 4.7f),
    ProProduct("se3", "Fijador de Roscas Rojo 271", "Loctite", "🛡️ Selladores",
        "Fijación permanente. Para birlos de volante, pernos de cigüeñal, birlos de cabezal que no deben aflojarse jamás.",
        "Para remover: calentar a 250°C con soplete antes de intentar aflojar. Sin calor es casi imposible. NO uses en piezas que necesiten mantenimiento.",
        "Igual que el azul: una gota en las roscas. Ensambla y aprieta al torque. Cura completa en 24h.",
        listOf("Volante Motor", "Cigüeñal", "Cabezal"), listOf("Adhesión permanente", "Requiere calor para remover"), 4.6f),

    // ── HERRAMIENTAS PRO ──
    ProProduct("h1", "Multímetro Automotriz con Duty Cycle", "Fluke 88V", "⚙️ Herramientas Pro",
        "Medición de voltaje, corriente, resistencia, frecuencia, duty cycle y RPM. Esencial para diagnóstico de inyectores, sensores y actuadores.",
        "Para probar inyectores: mide duty cycle (%) con motor en ralentí. Debe ser ~2-4ms. Si un inyector tiene duty cycle muy diferente, está fallando.",
        "Selecciona función Hz/Duty. Conecta puntas al conector del inyector. Compara valores entre cilindros.",
        listOf("Inyectores", "Sensores", "Bobinas", "Alternador"), listOf("No medir alta tensión de bobinas con multímetro estándar"), 5.0f),
    ProProduct("h2", "Compresímetro/Probador de Compresión", "OTC 5606", "⚙️ Herramientas Pro",
        "Mide la compresión de cada cilindro para diagnosticar desgaste de anillos, válvulas quemadas o junta de cabezal dañada.",
        "Especificación general: L4 = 150-180 PSI, V6/V8 = 130-170 PSI. La diferencia entre cilindros no debe superar el 10%. Si un cilindro está 20%+ abajo, haz prueba húmeda (aceite).",
        "Motor a temp. de operación → retira todas las bujías → desconecta inyectores → WOT → cranking 5 seg por cilindro.",
        listOf("Cilindros", "Junta Cabezal", "Anillos", "Válvulas"), listOf("Motor caliente — cuidado con quemaduras", "Desconectar inyectores antes"), 4.8f),
    ProProduct("h3", "Pistola de Calor Industrial 1500W", "Metabo", "⚙️ Herramientas Pro",
        "Remoción de adhesivos, retiro de calcomanías, aflojamiento de componentes pegados con Loctite rojo, termocontraíble.",
        "Para Loctite rojo: apunta a 250°C por 2-3 minutos en el birlo. Para termocontraíble: 150°C con movimiento circular constante.",
        "Ajusta temperatura según aplicación. Mantén 5-8cm de distancia. Mueve constantemente para evitar daño por calor localizado.",
        listOf("Loctite Removal", "Adhesivos", "Termocontraíble"), listOf("Riesgo de quemaduras", "No apuntar a personas", "Superficie caliente después de uso"), 4.7f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProChemicalGuideScreen(navController: NavController) {
    var selectedCategory by remember { mutableStateOf("🧪 Todos") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedProduct by remember { mutableStateOf<String?>(null) }

    val filteredProducts = remember(selectedCategory, searchQuery) {
        allProducts.filter { product ->
            val matchesCategory = selectedCategory == "🧪 Todos" || product.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                product.name.contains(searchQuery, ignoreCase = true) ||
                product.brand.contains(searchQuery, ignoreCase = true) ||
                product.useCase.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.backOrHome() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MeetColors.cardBackground)
                    .border(1.dp, MeetColors.borderSubtle, CircleShape)
            ) {
                AnimatedNeonIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Guía Pro de Químicos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Productos especializados para taller", color = MeetColors.textSecondary, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.cyberCyan.copy(alpha = 0.1f))
                    .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("${allProducts.size} productos", color = MeetColors.cyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Category Tabs ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            productCategories.forEach { cat ->
                val isSelected = selectedCategory == cat
                val catColor = categoryColors[cat] ?: MeetColors.electricBlue
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) catColor.copy(alpha = 0.2f) else MeetColors.cardBackground)
                        .border(1.dp, if (isSelected) catColor else MeetColors.borderSubtle, RoundedCornerShape(10.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(cat, color = if (isSelected) catColor else MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Search ──
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar producto, marca o uso...", color = MeetColors.textMuted, fontSize = 13.sp) },
            leadingIcon = { AnimatedNeonIcon(Icons.Default.Search, null, tint = MeetColors.cyberCyan) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MeetColors.borderSubtle,
                focusedBorderColor = MeetColors.cyberCyan,
                unfocusedContainerColor = MeetColors.cardBackground,
                focusedContainerColor = MeetColors.cardBackground,
                cursorColor = MeetColors.cyberCyan
            ),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
            singleLine = true
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "${filteredProducts.size} productos encontrados",
            color = MeetColors.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        // ── Product List ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredProducts, key = { it.id }) { product ->
                val isExpanded = expandedProduct == product.id
                ProductCard(
                    product = product,
                    isExpanded = isExpanded,
                    onToggle = { expandedProduct = if (isExpanded) null else product.id }
                )
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ProductCard(product: ProProduct, isExpanded: Boolean, onToggle: () -> Unit) {
    val catColor = categoryColors[product.category] ?: MeetColors.electricBlue

    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(14.dp)
        ) {
            // Header: name + brand + rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = if (isExpanded) 3 else 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(product.brand, color = catColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                // Rating
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★", color = Color(0xFFFFD700), fontSize = 12.sp)
                    Spacer(Modifier.width(2.dp))
                    Text("${product.rating}", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Category + Systems tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(catColor.copy(alpha = 0.15f))
                        .border(1.dp, catColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(product.category, color = catColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                product.systems.take(3).forEach { sys ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MeetColors.electricBlue.copy(alpha = 0.08f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(sys, color = MeetColors.electricBlue.copy(alpha = 0.7f), fontSize = 8.sp)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Use case (collapsed preview)
            Text(
                product.useCase,
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                maxLines = if (isExpanded) 10 else 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            // Expanded detail
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))

                    // Pro Tip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cyberCyan.copy(alpha = 0.08f))
                            .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("💡 TIP PRO", color = MeetColors.cyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(4.dp))
                            Text(product.proTip, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Application method
                    Text("📋 APLICACIÓN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(product.application, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)

                    Spacer(Modifier.height(10.dp))

                    // Safety warnings
                    if (product.safetyWarnings.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.warning.copy(alpha = 0.08f))
                                .border(1.dp, MeetColors.warning.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("⚠️ SEGURIDAD", color = MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(4.dp))
                                product.safetyWarnings.forEach { warning ->
                                    Text("• $warning", color = MeetColors.warning.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Compatible systems
                    Text("🔧 SISTEMAS COMPATIBLES", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        product.systems.forEach { sys ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MeetColors.neonGreen.copy(alpha = 0.1f))
                                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(sys, color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
