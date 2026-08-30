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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════
// FORO TÉCNICO Elysium Vanguard — Comunidad Mecánica
// ═══════════════════════════════════════

private data class ForumThread(
    val id: String,
    val title: String,
    val author: String,
    val authorLevel: String, // "maestro", "tecnico", "aprendiz"
    val vehicle: String,
    val dtcTags: List<String>,
    val category: String,
    val description: String,
    val views: Int,
    val answers: Int,
    val votes: Int,
    val timeAgo: String,
    val isSolved: Boolean,
    val bestAnswer: String? = null,
    val answersList: List<ForumAnswer> = emptyList()
)

private data class ForumAnswer(
    val author: String,
    val level: String,
    val text: String,
    val votes: Int,
    val isBest: Boolean = false,
    val timeAgo: String
)

private val forumCategories = listOf(
    "🔥 Trending", "⚙️ Motor", "⚡ Eléctrico", "🛢️ Diesel",
    "🔋 EV/Híbrido", "📡 OBD-II", "🔧 Herramientas", "💡 Tips Pro"
)

private val mockThreads = listOf(
    ForumThread(
        id = "1", title = "Motor tiembla en ralentí después de cambio de bujías — P0300",
        author = "Carlos_MX", authorLevel = "maestro", vehicle = "Toyota Corolla 2018 L4",
        dtcTags = listOf("P0300", "P0301"), category = "⚙️ Motor",
        description = "Cambié las 4 bujías NGK Iridium y ahora el motor vibra más que antes. Las bujías anteriores eran Denso. ¿Será el gap incorrecto o problema de bobinas?",
        views = 342, answers = 7, votes = 15, timeAgo = "hace 2h", isSolved = true,
        bestAnswer = "El gap de las NGK para ese motor debe ser 0.044\" (1.1mm). Si usaste las que vienen pre-gapped a 0.032\", ese es tu problema. También verifica que no hayas dañado las puntas de las bobinas COP al reinstalar.",
        answersList = listOf(
            ForumAnswer("MecánicoPro_GT", "maestro", "El gap de las NGK para ese motor debe ser 0.044\" (1.1mm). Si usaste las que vienen pre-gapped a 0.032\", ese es tu problema. También verifica que no hayas dañado las puntas de las bobinas COP al reinstalar.", 12, true, "hace 1h"),
            ForumAnswer("TallerJuárez", "tecnico", "Revisa también el torque de apriete. Si las apretaste de más, puedes haber dañado la rosca del cabezal y pierde compresión en ese cilindro.", 5, false, "hace 45min")
        )
    ),
    ForumThread(
        id = "2", title = "Check Engine intermitente — P0171 y P0174 en ambos bancos",
        author = "Roberto_Diag", authorLevel = "tecnico", vehicle = "Ford F-150 2020 V8 5.0L",
        dtcTags = listOf("P0171", "P0174"), category = "⚙️ Motor",
        description = "El check engine aparece y desaparece. Mezcla pobre en ambos bancos. Ya revisé el MAF y parece limpio. ¿Fuga de vacío o bomba de combustible?",
        views = 218, answers = 4, votes = 8, timeAgo = "hace 5h", isSolved = false,
        answersList = listOf(
            ForumAnswer("DiagPro_USA", "maestro", "P0171+P0174 simultáneo = casi siempre fuga de vacío post-MAF o sensor MAF contaminado. Haz prueba de humo en el sistema de admisión.", 6, false, "hace 3h")
        )
    ),
    ForumThread(
        id = "3", title = "DPF regeneración forzada no completa — luz amarilla persiste",
        author = "DieselMaster", authorLevel = "maestro", vehicle = "VW Amarok 2021 TDI V6",
        dtcTags = listOf("P2463", "P246B"), category = "🛢️ Diesel",
        description = "Intenté regeneración forzada con VCDS y se detiene al 60%. La temperatura de escape no sube de 550°C. ¿Inyector piloto o problema del catalizador DOC?",
        views = 156, answers = 3, votes = 11, timeAgo = "hace 1d", isSolved = true,
        bestAnswer = "Si la temp no sube de 550°C, el DOC está tapado. El DOC necesita estar funcional para que el DPF alcance los 600°C+ de regeneración. Mide la caída de presión diferencial entre pre y post DOC.",
        answersList = listOf(
            ForumAnswer("TurboDiesel_AR", "maestro", "Si la temp no sube de 550°C, el DOC está tapado. El DOC necesita estar funcional para que el DPF alcance los 600°C+ de regeneración. Mide la caída de presión diferencial entre pre y post DOC.", 9, true, "hace 18h")
        )
    ),
    ForumThread(
        id = "4", title = "Pérdida de potencia a 3000 RPM — sin códigos de error",
        author = "HondaTech", authorLevel = "tecnico", vehicle = "Honda Civic 2019 L4 1.5T",
        dtcTags = emptyList(), category = "⚙️ Motor",
        description = "El carro pierde fuerza justo a 3000 RPM como si cortara la inyección. No tira ningún DTC. ¿Posible wastegate o sensor de boost?",
        views = 89, answers = 2, votes = 4, timeAgo = "hace 3h", isSolved = false,
        answersList = listOf(
            ForumAnswer("BoostMaster", "tecnico", "Revisa con escáner el PID de presión de boost en tiempo real. Si cae exactamente a 3K RPM, puede ser la válvula wastegate electrónica que se queda abierta. También revisa el intercooler por fugas.", 3, false, "hace 2h")
        )
    ),
    ForumThread(
        id = "5", title = "Error P0420 después de cambiar catalizador aftermarket",
        author = "Novato_Diag", authorLevel = "aprendiz", vehicle = "Nissan Sentra 2017 L4",
        dtcTags = listOf("P0420"), category = "⚙️ Motor",
        description = "Cambié el catalizador por uno universal y a los 200 km regresó el P0420. ¿El catalizador aftermarket no sirve o es problema del sensor O2?",
        views = 421, answers = 6, votes = 19, timeAgo = "hace 6h", isSolved = true,
        bestAnswer = "Los catalizadores universales rara vez tienen suficiente carga de metales preciosos (Pt/Pd/Rh) para cumplir con la eficiencia >95% que espera la ECU. Necesitas uno OEM o CARB-compliant. El sensor downstream está leyendo correctamente — el cat simplemente no convierte lo suficiente.",
        answersList = listOf(
            ForumAnswer("EmissionsPro", "maestro", "Los catalizadores universales rara vez tienen suficiente carga de metales preciosos (Pt/Pd/Rh) para cumplir con la eficiencia >95% que espera la ECU. Necesitas uno OEM o CARB-compliant. El sensor downstream está leyendo correctamente — el cat simplemente no convierte lo suficiente.", 14, true, "hace 4h")
        )
    ),
    ForumThread(
        id = "6", title = "Batería HV degrada rápido en frío — ¿Normal o defecto?",
        author = "EV_Fanatic", authorLevel = "tecnico", vehicle = "Toyota Prius 2018 Hybrid",
        dtcTags = listOf("P0A80"), category = "🔋 EV/Híbrido",
        description = "En invierno la batería de alto voltaje baja de 80% a 30% en 20 minutos. En verano funciona normal. ¿Es degradación normal o módulos defectuosos?",
        views = 167, answers = 3, votes = 7, timeAgo = "hace 12h", isSolved = false,
        answersList = listOf(
            ForumAnswer("HybridTech_JP", "maestro", "Es parcialmente normal — las celdas NiMH pierden eficiencia con frío. Pero una caída de 50% en 20 min sugiere 1-2 módulos con resistencia interna alta. Usa Dr. Prius o Techstream para ver voltaje por módulo.", 5, false, "hace 8h")
        )
    ),
    ForumThread(
        id = "7", title = "ELM327 no conecta con protocolo ISO 9141 — VW Golf 2003",
        author = "OBD_Beginner", authorLevel = "aprendiz", vehicle = "VW Golf 2003 L4",
        dtcTags = emptyList(), category = "📡 OBD-II",
        description = "Mi ELM327 Bluetooth no logra inicializar con el Golf 2003. Probé con Torque y con Elysium Vanguard app. ¿Es incompatible o necesito configuración especial?",
        views = 534, answers = 8, votes = 22, timeAgo = "hace 2d", isSolved = true,
        bestAnswer = "Los VW de esa era usan KWP2000 (protocolo 4 o 5 en el ELM327). Envía 'ATSP5' manualmente antes de conectar. Si aún falla, verifica que tu ELM327 no sea un clon barato — los clones no soportan protocolos lentos como ISO 9141/KWP2000.",
        answersList = listOf(
            ForumAnswer("OBD_Expert", "maestro", "Los VW de esa era usan KWP2000 (protocolo 4 o 5 en el ELM327). Envía 'ATSP5' manualmente antes de conectar. Si aún falla, verifica que tu ELM327 no sea un clon barato — los clones no soportan protocolos lentos como ISO 9141/KWP2000.", 18, true, "hace 1d")
        )
    ),
    ForumThread(
        id = "8", title = "Inyector #3 falla intermitente — misfire solo en frío",
        author = "CruzeOwner", authorLevel = "aprendiz", vehicle = "Chevrolet Cruze 2016 L4 1.4T",
        dtcTags = listOf("P0303", "P0300"), category = "⚙️ Motor",
        description = "Al arrancar en frío el cilindro 3 falla por unos 30 segundos y luego se estabiliza. Ya cambié bujía y bobina. ¿Inyector o compresión?",
        views = 203, answers = 5, votes = 9, timeAgo = "hace 8h", isSolved = false,
        answersList = listOf(
            ForumAnswer("InjectorPro", "tecnico", "Si solo falla en frío y se estabiliza, es muy probable que el inyector #3 tenga un sello O-ring dañado que gotea cuando está frío. Al calentarse, el metal se expande y sella. Prueba con limpieza ultrasónica primero.", 7, false, "hace 6h")
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicalForumScreen(navController: NavController) {
    var selectedCategory by remember { mutableStateOf("🔥 Trending") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedThread by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredThreads = remember(selectedCategory, searchQuery) {
        mockThreads.filter { thread ->
            val matchesCategory = selectedCategory == "🔥 Trending" || thread.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                thread.title.contains(searchQuery, ignoreCase = true) ||
                thread.dtcTags.any { it.contains(searchQuery, ignoreCase = true) } ||
                thread.vehicle.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MeetColors.backgroundDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .border(1.dp, MeetColors.neonGreen, RoundedCornerShape(12.dp))
                    .neonGlow(MeetColors.neonGreen, RoundedCornerShape(12.dp), minElevation = 4f, maxElevation = 12f)
            ) {
                AnimatedNeonIcon(Icons.Default.Add, "Crear hilo", tint = MeetColors.neonGreen)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
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
                    Text("Foro Técnico Elysium Vanguard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Comunidad de mecánicos expertos", color = MeetColors.textSecondary, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.neonGreen.copy(alpha = 0.1f))
                        .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("2.4K mecánicos", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                forumCategories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MeetColors.electricBlue.copy(alpha = 0.2f) else MeetColors.cardBackground)
                            .border(1.dp, if (isSelected) MeetColors.electricBlue else MeetColors.borderSubtle, RoundedCornerShape(10.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(cat, color = if (isSelected) MeetColors.electricBlue else MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Search ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar por DTC, síntoma, marca...", color = MeetColors.textMuted, fontSize = 13.sp) },
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

            Spacer(Modifier.height(8.dp))

            // ── Thread count ──
            Text(
                "${filteredThreads.size} hilos encontrados",
                color = MeetColors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // ── Thread List ──
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredThreads, key = { it.id }) { thread ->
                    val isExpanded = expandedThread == thread.id
                    ThreadCard(
                        thread = thread,
                        isExpanded = isExpanded,
                        onToggle = { expandedThread = if (isExpanded) null else thread.id }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // ── Create Thread Dialog ──
    if (showCreateDialog) {
        CreateThreadDialog(onDismiss = { showCreateDialog = false })
    }
}

@Composable
private fun ThreadCard(thread: ForumThread, isExpanded: Boolean, onToggle: () -> Unit) {
    val levelColor = when (thread.authorLevel) {
        "maestro" -> Color(0xFFFFD700)
        "tecnico" -> MeetColors.cyberCyan
        else -> MeetColors.textMuted
    }
    val levelLabel = when (thread.authorLevel) {
        "maestro" -> "⭐ Maestro"
        "tecnico" -> "🔧 Técnico"
        else -> "📚 Aprendiz"
    }

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
            // Header: solved + title
            Row(verticalAlignment = Alignment.Top) {
                if (thread.isSolved) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 8.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MeetColors.neonGreen.copy(alpha = 0.2f))
                            .border(1.dp, MeetColors.neonGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedNeonIcon(Icons.Default.Check, null, tint = MeetColors.neonGreen, modifier = Modifier.size(12.dp))
                    }
                }
                Text(
                    thread.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = if (isExpanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Author + Vehicle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(thread.author, color = MeetColors.textSecondary, fontSize = 11.sp)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(levelColor.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(levelLabel, color = levelColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MeetColors.electricBlue.copy(alpha = 0.1f))
                        .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(thread.vehicle, color = MeetColors.electricBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(6.dp))

            // DTC Tags
            if (thread.dtcTags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    thread.dtcTags.forEach { dtc ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MeetColors.warning.copy(alpha = 0.15f))
                                .border(1.dp, MeetColors.warning.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(dtc, color = MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBadge("👁 ${thread.views}", MeetColors.textMuted)
                    StatBadge("💬 ${thread.answers}", MeetColors.cyberCyan)
                    StatBadge("▲ ${thread.votes}", MeetColors.neonGreen)
                }
                Text(thread.timeAgo, color = MeetColors.textMuted, fontSize = 10.sp)
            }

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 0.5.dp)
                    Spacer(Modifier.height(10.dp))

                    // Full description
                    Text(thread.description, color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)

                    Spacer(Modifier.height(12.dp))

                    // Answers
                    if (thread.answersList.isNotEmpty()) {
                        Text("💬 ${thread.answersList.size} Respuestas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))

                        thread.answersList.forEach { answer ->
                            AnswerCard(answer)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerCard(answer: ForumAnswer) {
    val borderColor = if (answer.isBest) MeetColors.neonGreen else MeetColors.borderSubtle
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MeetColors.backgroundDark)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (answer.isBest) {
                        AnimatedNeonIcon(Icons.Default.Check, null, tint = MeetColors.neonGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("MEJOR RESPUESTA", color = MeetColors.neonGreen, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(answer.author, color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("▲ ${answer.votes}", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(answer.text, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(answer.timeAgo, color = MeetColors.textMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun StatBadge(text: String, color: Color) {
    Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateThreadDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var dtcInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MeetColors.backgroundDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Crear Nuevo Hilo", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Título del problema", color = MeetColors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MeetColors.borderSubtle, focusedBorderColor = MeetColors.cyberCyan,
                        cursorColor = MeetColors.cyberCyan
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descripción detallada", color = MeetColors.textMuted) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MeetColors.borderSubtle, focusedBorderColor = MeetColors.cyberCyan,
                        cursorColor = MeetColors.cyberCyan
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                )
                OutlinedTextField(
                    value = vehicle, onValueChange = { vehicle = it },
                    label = { Text("Vehículo (Marca Modelo Año)", color = MeetColors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MeetColors.borderSubtle, focusedBorderColor = MeetColors.cyberCyan,
                        cursorColor = MeetColors.cyberCyan
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dtcInput, onValueChange = { dtcInput = it },
                    label = { Text("Códigos DTC (separados por coma)", color = MeetColors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MeetColors.borderSubtle, focusedBorderColor = MeetColors.warning,
                        cursorColor = MeetColors.warning
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MeetColors.warning, fontFamily = FontFamily.Monospace),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("🚀 Publicar", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MeetColors.textSecondary)
            }
        }
    )
}
