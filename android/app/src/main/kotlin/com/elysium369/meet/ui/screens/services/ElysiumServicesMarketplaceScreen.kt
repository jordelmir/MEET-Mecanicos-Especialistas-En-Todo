package com.elysium369.meet.ui.screens.services

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.elysium369.meet.core.agent.laya.EvairDigiSoulEngine
import com.elysium369.meet.core.agent.laya.LayaDecisionEngine
import com.elysium369.meet.core.agent.laya.LayaQuestion
import com.elysium369.meet.core.agent.laya.LayaSpatialSearchEngine
import com.elysium369.meet.data.local.entities.ServiceBidEntity
import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.core.geo.*
import com.elysium369.meet.core.geo.runtime.CommonMapPanel
import com.elysium369.meet.ride.map.LayaEnhancedPlaceSearchProvider
import com.elysium369.meet.ride.map.RidePlaceSuggestion
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   S E R V I C I O S   &   S U B A S T A   D U A L
 *  ──────────────────────────────────────────────────────────────
 *  Next-gen On-Demand Marketplace with dual-sided matching:
 *  - Mode CLIENT: Request service, live Laya diagnostics, fair price
 *    benchmarking by Vanguard Titan, counter-offer review, live map tracking,
 *    security PIN validation by Aura Sentinel, and two-way rating.
 *  - Mode SPECIALIST: Radar of nearby requests, instant claim or counter-offer
 *    (price, arrival time, warranty, note), PIN entry and completion.
 *  - Laya Spatial Search Engine: hyper-local Costa Rica geocoding and
 *    speech-to-intent natural voice search.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElysiumServicesMarketplaceScreen(
    navController: NavController,
    viewModel: ObdViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isSpecialistMode by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }

    val categories = remember {
        listOf(
            ServiceCategoryItem("Mecánica & OBD", "🔧", "AUTO_MECHANICAL", "Vanguard Titan"),
            ServiceCategoryItem("Grúa & Rescate", "🛞", "AUTO_TOW", "Aura Sentinel"),
            ServiceCategoryItem("Cerrajería", "🔐", "LOCKSMITH", "Aura Sentinel"),
            ServiceCategoryItem("Batería & Arranque", "⚡", "BATTERY", "Vanguard Titan"),
            ServiceCategoryItem("Ferretería", "🔩", "HARDWARE", "Vanguard Titan"),
            ServiceCategoryItem("Lavado & Detailing", "✨", "DETAILING", "Neo Concierge"),
            ServiceCategoryItem("Plomería", "🚰", "PLUMBING", "Vanguard Titan"),
            ServiceCategoryItem("Electricidad", "💡", "ELECTRICAL", "Aura Sentinel")
        )
    }

    val activeCategory = categories[selectedCategoryIndex]

    // Local-first spatial search engine
    val spatialEngine = remember { LayaSpatialSearchEngine() }
    val placeSearchProvider = remember { LayaEnhancedPlaceSearchProvider(spatialEngine = spatialEngine) }
    val layaDecisionEngine = remember { LayaDecisionEngine() }

    // Requests from local Room / ViewModel
    val allRequests by viewModel.serviceRequests.collectAsState()
    val gps by viewModel.currentGpsLocation.collectAsState()

    // Form state (Client Mode)
    var problemInput by rememberSaveable { mutableStateOf("") }
    var locationInput by rememberSaveable { mutableStateOf("San José Centro, Costa Rica") }
    var locationLat by rememberSaveable { mutableDoubleStateOf(9.9333) }
    var locationLon by rememberSaveable { mutableDoubleStateOf(-84.0833) }
    var priceOfferText by rememberSaveable { mutableStateOf("18000") }
    var layaDiagnosticHint by remember { mutableStateOf<String?>(null) }
    var searchSuggestions by remember { mutableStateOf<List<RidePlaceSuggestion>>(emptyList()) }
    var isSearchingPlaces by remember { mutableStateOf(false) }

    // Dialog state
    var counterOfferDialogRequest by remember { mutableStateOf<ServiceRequestEntity?>(null) }
    var ratingDialogRequest by remember { mutableStateOf<ServiceRequestEntity?>(null) }
    var voiceSearchActive by remember { mutableStateOf(false) }
    var selectedViewMode by rememberSaveable { mutableStateOf("LIST") } // "LIST" or "MAP"
    var focusedMapRequest by remember { mutableStateOf<ServiceRequestEntity?>(null) }

    // Auto-evaluate Laya System 1 diagnostic when problem description changes
    LaunchedEffect(problemInput, activeCategory) {
        if (problemInput.trim().length >= 4) {
            val questions = listOf(
                LayaQuestion.Choice(
                    name = "problem_severity",
                    options = listOf("NORMAL", "ELEVATED", "CRITICAL_SAFETY")
                ),
                LayaQuestion.Score(
                    name = "fair_price_index",
                    levels = 5
                )
            )
            val batch = layaDecisionEngine.evaluateSync("${activeCategory.name} - $problemInput", questions)
            val severity = batch.choice("problem_severity")?.value ?: "NORMAL"
            val priceLevel = batch.score("fair_price_index")?.level ?: 2
            val suggestedPrice = 12000 + (priceLevel * 6000)

            layaDiagnosticHint = when (severity) {
                "CRITICAL_SAFETY" -> "⚠️ Diagnóstico Crítico: Se sugiere no rodar el vehículo. Vanguard Titan estima precio justo de mano de obra en ₡${suggestedPrice} CRC."
                "ELEVATED" -> "⚡ Diagnóstico Elevado: Requiere atención técnica pronta. Rango estimado: ₡${suggestedPrice} CRC."
                else -> "✓ Diagnóstico Estándar: Vanguard Titan estima mano de obra justa en ₡${suggestedPrice} CRC."
            }
        } else {
            layaDiagnosticHint = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ELYSIUM SERVICIOS & SUBASTA",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            text = if (isSpecialistMode) "Modo Especialista (Tomar / Contraofertar)" else "Modo Cliente (Solicitar con Escrow)",
                            fontSize = 11.sp,
                            color = if (isSpecialistMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    // Mode Switcher Chip
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable { isSpecialistMode = !isSpecialistMode },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSpecialistMode) MeetColors.neonGreen.copy(alpha = 0.2f) else MeetColors.cyberCyan.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (isSpecialistMode) MeetColors.neonGreen else MeetColors.cyberCyan)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSpecialistMode) Icons.Default.Engineering else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isSpecialistMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isSpecialistMode) "ESPECIALISTA" else "CLIENTE",
                                color = if (isSpecialistMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep)
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // View Mode Switcher: [ 📋 Solicitudes ] | [ 🗺️ Radar & Ruta en Vivo ]
            ViewModeSelector(
                selectedMode = selectedViewMode,
                onSelectMode = { selectedViewMode = it },
                activeAcceptedCount = allRequests.count { it.status == "ACCEPTED" }
            )

            if (selectedViewMode == "MAP") {
                ElysiumServicesLiveMapRadar(
                    allRequests = allRequests,
                    focusedRequest = focusedMapRequest,
                    onSelectRequest = { focusedMapRequest = it },
                    userGps = gps,
                    isSpecialistMode = isSpecialistMode,
                    onCompleteRequest = { req -> ratingDialogRequest = req }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    // ── Section 1: Master Agent Advisory Banner ──
                    item {
                        AgentAdvisoryHeroCard(
                            category = activeCategory,
                            diagnosticHint = layaDiagnosticHint
                        )
                    }

                    // ── Section 2: Spatial Search Bar (Costa Rica First + Voice) ──
                    item {
                        SpatialSearchBarCard(
                            query = locationInput,
                            onQueryChange = { newQuery ->
                                locationInput = newQuery
                                if (newQuery.length >= 2) {
                                    scope.launch {
                                        isSearchingPlaces = true
                                        searchSuggestions = placeSearchProvider.search(
                                            query = newQuery,
                                            biasLatitude = gps?.latitude ?: 9.9333,
                                            biasLongitude = gps?.longitude ?: -84.0833,
                                            limit = 5
                                        )
                                        isSearchingPlaces = false
                                    }
                                } else {
                                    searchSuggestions = emptyList()
                                }
                            },
                            onVoiceClick = { voiceSearchActive = true },
                            suggestions = searchSuggestions,
                            onSelectSuggestion = { suggestion ->
                                locationInput = suggestion.displayLabel
                                locationLat = suggestion.latitude
                                locationLon = suggestion.longitude
                                searchSuggestions = emptyList()
                                Toast.makeText(context, "Ubicación fijada: ${suggestion.primaryLabel}", Toast.LENGTH_SHORT).show()
                            },
                            onSelectCategoryFilter = { catKeyword ->
                                locationInput = catKeyword
                                scope.launch {
                                    isSearchingPlaces = true
                                    searchSuggestions = placeSearchProvider.search(
                                        query = catKeyword,
                                        biasLatitude = gps?.latitude ?: 9.9333,
                                        biasLongitude = gps?.longitude ?: -84.0833,
                                        limit = 6
                                    )
                                    isSearchingPlaces = false
                                }
                            }
                        )
                    }

                    // ── Mode-Specific Flows ──
                    if (!isSpecialistMode) {
                // ══════════════════════════════════════════════
                //  C L I E N T   F L O W
                // ══════════════════════════════════════════════

                // Category selector pills
                item {
                    Text(
                        text = "SELECCIONA EL TIPO DE SERVICIO",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories.indices.toList()) { index ->
                            val cat = categories[index]
                            val isSelected = (index == selectedCategoryIndex)
                            Surface(
                                modifier = Modifier.clickable { selectedCategoryIndex = index },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.22f) else MeetColors.cardBackground,
                                border = BorderStroke(1.dp, if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat.icon, fontSize = 16.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = cat.name,
                                        color = if (isSelected) MeetColors.cyberCyan else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Request Form Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "DETALLE DE LA SOLICITUD",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(10.dp))

                            OutlinedTextField(
                                value = problemInput,
                                onValueChange = { problemInput = it },
                                label = { Text("Describe el problema o necesidad") },
                                placeholder = { Text("Ej: El carro no enciende, hace un clic pero no da marcha") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.cyberCyan,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 3
                            )

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = priceOfferText,
                                    onValueChange = { priceOfferText = it.filter(Char::isDigit) },
                                    label = { Text("Tu Oferta Base (CRC)") },
                                    prefix = { Text("₡ ", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MeetColors.cyberCyan,
                                        unfocusedBorderColor = MeetColors.borderSubtle,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        if (problemInput.isBlank()) {
                                            Toast.makeText(context, "Por favor describe el problema", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val price = priceOfferText.toDoubleOrNull() ?: 18000.0
                                        viewModel.createServiceRequest(
                                            vehicleId = viewModel.selectedVehicle.value?.id ?: "V_COMMERCIAL",
                                            problem = "${activeCategory.name}: $problemInput",
                                            description = problemInput,
                                            location = locationInput,
                                            priority = "MEDIUM",
                                            priceOffer = price,
                                            phone = "8888-8888",
                                            latitude = locationLat,
                                            longitude = locationLon
                                        )
                                        problemInput = ""
                                        Toast.makeText(context, "¡Solicitud publicada en la red con Escrow!", Toast.LENGTH_LONG).show()

                                        // DigiSoul XP
                                        EvairDigiSoulEngine.shared.recordInteraction(
                                            action = "SERVICE_REQUEST_CREATED",
                                            xpGained = 30,
                                            narrative = "Publicamos solicitud de ${activeCategory.name} con respaldo de precio justo."
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(56.dp)
                                        .align(Alignment.CenterVertically),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = MeetColors.backgroundDark)
                                    Spacer(Modifier.width(6.dp))
                                    Text("PUBLICAR ⚡", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Active Requests & Bids Review Section
                item {
                    Text(
                        text = "TUS SOLICITUDES ACTIVAS & CONTRAOFERTAS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                val openClientRequests = allRequests.filter { it.status == "OPEN" || it.status == "ACCEPTED" }
                if (openClientRequests.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle)
                        ) {
                            Text(
                                text = "No tienes solicitudes abiertas en este momento. Publica una arriba para recibir ofertas de especialistas cercanos.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp),
                                lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    items(openClientRequests, key = { it.requestId }) { req ->
                        ClientActiveRequestCard(
                            request = req,
                            viewModel = viewModel,
                            onCompleteAndRate = { ratingDialogRequest = req },
                            onTrackOnMap = {
                                focusedMapRequest = req
                                selectedViewMode = "MAP"
                            }
                        )
                    }
                }

            } else {
                // ══════════════════════════════════════════════
                //  S P E C I A L I S T   F L O W
                // ══════════════════════════════════════════════

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RADAR DE SOLICITUDES EN TU RADIO (15 KM)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MeetColors.neonGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MeetColors.neonGreen)
                        ) {
                            Text(
                                text = "RADAR ACTIVO",
                                color = MeetColors.neonGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val openMarketplaceRequests = allRequests.filter { it.status == "OPEN" }
                if (openMarketplaceRequests.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MeetColors.neonGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "Escaneando solicitudes de clientes en Costa Rica... Las nuevas alertas aparecerán aquí en tiempo real.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                } else {
                    items(openMarketplaceRequests, key = { it.requestId }) { req ->
                        SpecialistRequestItemCard(
                            request = req,
                            userGps = gps,
                            onViewOnMap = {
                                focusedMapRequest = req
                                selectedViewMode = "MAP"
                            },
                            onTakeDirect = {
                                viewModel.placeServiceBid(
                                    requestId = req.requestId,
                                    providerName = "Especialista Certificado MEET",
                                    price = req.priceOffer,
                                    estimatedHours = 1.0,
                                    warrantyDays = 30,
                                    message = "Acepto el precio propuesto por el cliente. En camino con equipo profesional."
                                )
                                Toast.makeText(context, "¡Oferta enviada al cliente por ₡${req.priceOffer}!", Toast.LENGTH_SHORT).show()
                            },
                            onCounterOffer = {
                                counterOfferDialogRequest = req
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
    }

    // ── Dialog: Contraoferta del Especialista ──
    counterOfferDialogRequest?.let { req ->
        SpecialistCounterOfferDialog(
            request = req,
            onDismiss = { counterOfferDialogRequest = null },
            onSubmitCounterOffer = { price, hours, warranty, note ->
                viewModel.placeServiceBid(
                    requestId = req.requestId,
                    providerName = "Especialista Profesional Certificado",
                    price = price,
                    estimatedHours = hours,
                    warrantyDays = warranty,
                    message = note
                )
                counterOfferDialogRequest = null
                Toast.makeText(context, "¡Contraoferta de ₡$price enviada al cliente con éxito!", Toast.LENGTH_LONG).show()
            }
        )
    }

    // ── Dialog: Calificación Bidireccional Estilo Viajes ──
    ratingDialogRequest?.let { req ->
        TwoWayRatingDialog(
            request = req,
            onDismiss = { ratingDialogRequest = null },
            onSubmitRating = { stars, praiseTags, reviewText ->
                viewModel.completeMechanicRequest(req.requestId)
                ratingDialogRequest = null
                Toast.makeText(context, "¡Servicio completado! Calificación registrada ⭐ $stars", Toast.LENGTH_LONG).show()

                // DigiSoul XP & Memory
                EvairDigiSoulEngine.shared.recordInteraction(
                    action = "SERVICE_COMPLETED_RATED",
                    xpGained = 75,
                    narrative = "Servicio completado con éxito con calificación de $stars estrellas. Tags: ${praiseTags.joinToString()}."
                )
            }
        )
    }

    // ── Dialog: Asistente de Búsqueda por Voz ──
    if (voiceSearchActive) {
        VoiceSearchAssistantDialog(
            onDismiss = { voiceSearchActive = false },
            onQueryRecognized = { speech ->
                val parsed = spatialEngine.parseVoiceQuery(speech)
                if (parsed.targetPlaceName != null) {
                    locationInput = parsed.targetPlaceName
                    scope.launch {
                        val results = spatialEngine.search(parsed.targetPlaceName, 9.9333, -84.0833, 1)
                        if (results.isNotEmpty()) {
                            locationLat = results.first().latitude
                            locationLon = results.first().longitude
                        }
                    }
                } else {
                    locationInput = speech
                }
                if (parsed.recognizedIntent == "AUTO_TOW") selectedCategoryIndex = 1
                if (parsed.recognizedIntent == "LOCKSMITH") selectedCategoryIndex = 2
                voiceSearchActive = false
                Toast.makeText(context, "Intención Laya: ${parsed.recognizedIntent} en ${parsed.normalizedQuery}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

data class ServiceCategoryItem(
    val name: String,
    val icon: String,
    val domainKey: String,
    val masterAgentName: String,
)

@Composable
private fun AgentAdvisoryHeroCard(
    category: ServiceCategoryItem,
    diagnosticHint: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091424)),
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(MeetColors.cyberCyan, MeetColors.neonGreen)))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MeetColors.cyberCyan.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, MeetColors.cyberCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(category.icon, fontSize = 22.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MeetColors.neonGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "GUÍA: ${category.masterAgentName.uppercase()}",
                            color = MeetColors.neonGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = diagnosticHint ?: "Vanguard Titan audita precios en tiempo real para evitar fraudes en ${category.name}.",
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SpatialSearchBarCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    suggestions: List<RidePlaceSuggestion>,
    onSelectSuggestion: (RidePlaceSuggestion) -> Unit,
    onSelectCategoryFilter: (String) -> Unit = {},
) {
    val quickPois = remember {
        listOf(
            "🛠️ DEKRA" to "dekra",
            "⛽ Gasolineras" to "gasolinera",
            "🛒 Supermercados" to "automercado",
            "🏥 Hospitales" to "hospital",
            "🔩 Repuestos" to "repuestos",
            "🏬 Malls" to "multiplaza",
            "🏢 COSEVI" to "cosevi"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Lugar (ej: San José, Escazú, La Sabana...)", color = MeetColors.textMuted, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    singleLine = true
                )
                IconButton(onClick = onVoiceClick) {
                    Icon(Icons.Default.Mic, contentDescription = "Búsqueda por voz Laya", tint = MeetColors.neonGreen)
                }
            }

            // Quick POI Discovery Chips (Costa Rica master GIS)
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickPois) { (label, keyword) ->
                    Surface(
                        modifier = Modifier.clickable { onSelectCategoryFilter(keyword) },
                        shape = RoundedCornerShape(8.dp),
                        color = MeetColors.backgroundDark,
                        border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
                    ) {
                        Text(
                            text = label,
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSuggestion(suggestion) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(suggestion.primaryLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(suggestion.secondaryLabel, color = MeetColors.textSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientActiveRequestCard(
    request: ServiceRequestEntity,
    viewModel: ObdViewModel,
    onCompleteAndRate: () -> Unit,
    onTrackOnMap: () -> Unit = {},
) {
    val bids by viewModel.getBidsForRequest(request.requestId).collectAsState(initial = emptyList())
    val isAccepted = request.status == "ACCEPTED"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (isAccepted) MeetColors.neonGreen.copy(alpha = 0.7f) else MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isAccepted) MeetColors.neonGreen.copy(alpha = 0.15f) else MeetColors.cyberCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isAccepted) MeetColors.neonGreen else MeetColors.cyberCyan)
                ) {
                    Text(
                        text = if (isAccepted) "ESPECIALISTA EN CAMINO" else "SUBASTA ACTIVA (${bids.size} OFERTAS)",
                        color = if (isAccepted) MeetColors.neonGreen else MeetColors.cyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "₡${String.format("%,.0f", request.priceOffer)} CRC",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(request.problem, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(request.location, color = MeetColors.textSecondary, fontSize = 11.sp)

            // Live ETA and Route Tracker if accepted
            if (isAccepted) {
                val clientLat = if (request.latitude != 0.0) request.latitude else 9.9333
                val clientLon = if (request.longitude != 0.0) request.longitude else -84.0833
                val specialistOrigin = GeoPoint(clientLat + 0.016, clientLon + 0.014)
                val routeEstimate = remember(request.requestId, clientLat, clientLon) {
                    LayaRouteEngine.calculateRoute(
                        origin = specialistOrigin,
                        destination = GeoPoint(clientLat, clientLon)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF071B26),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⏱️", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "ETA ESTIMADO: ${routeEstimate.etaMinutes} MIN",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "${String.format("%.1f", routeEstimate.distanceKm)} km · ${routeEstimate.trafficLevel.label}",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (routeEstimate.trafficLevel) {
                                    LayaRouteEngine.TrafficLevel.FLUID -> MeetColors.neonGreen.copy(alpha = 0.2f)
                                    LayaRouteEngine.TrafficLevel.MODERATE -> MeetColors.warning.copy(alpha = 0.2f)
                                    else -> MeetColors.error.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = "${routeEstimate.estimatedSpeedKmh.toInt()} km/h",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onTrackOnMap,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("RASTREAR RUTA Y ESPECIALISTA EN MAPA", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                // Security PIN if accepted (Aura Sentinel)
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F261C),
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("PIN DE SEGURIDAD AURA SENTINEL", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text("Proporciona este código al especialista al llegar", color = MeetColors.textSecondary, fontSize = 10.sp)
                        }
                        Text(
                            text = request.requestId.takeLast(4).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCompleteAndRate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.backgroundDark)
                    Spacer(Modifier.width(6.dp))
                    Text("TRABAJO COMPLETADO & CALIFICAR", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            } else {
                // List received counter-offers
                if (bids.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Contraofertas Recibidas:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))

                    bids.forEach { bid ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDark),
                            border = BorderStroke(0.5.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(bid.shopName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("⭐ ${bid.shopRating} · Garantía ${bid.warrantyDays} días", color = MeetColors.textSecondary, fontSize = 10.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₡${String.format("%,.0f", bid.price)} CRC",
                                            color = MeetColors.cyberCyan,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp
                                        )
                                        if (bid.price > request.priceOffer) {
                                            Text(
                                                text = "+₡${String.format("%,.0f", bid.price - request.priceOffer)} contraoferta",
                                                color = MeetColors.warning,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }

                                if (bid.message.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(bid.message, color = MeetColors.textSecondary, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }

                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.acceptBid(request.requestId, bid.bidId)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text("ACEPTAR ESTA OFERTA", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialistRequestItemCard(
    request: ServiceRequestEntity,
    userGps: ObdViewModel.GpsLocationInfo? = null,
    onViewOnMap: () -> Unit = {},
    onTakeDirect: () -> Unit,
    onCounterOffer: () -> Unit,
) {
    val reqLat = if (request.latitude != 0.0) request.latitude else 9.9333
    val reqLon = if (request.longitude != 0.0) request.longitude else -84.0833
    val specialistOrigin = GeoPoint(userGps?.latitude ?: 9.9333, userGps?.longitude ?: -84.0833)
    val reqEstimate = remember(request.requestId, specialistOrigin) {
        LayaRouteEngine.calculateRoute(
            origin = specialistOrigin,
            destination = GeoPoint(reqLat, reqLon)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan)
                ) {
                    Text(
                        text = "SOLICITUD EN TU ZONA",
                        color = MeetColors.cyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "Oferta: ₡${String.format("%,.0f", request.priceOffer)} CRC",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(request.problem, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(request.location, color = MeetColors.textSecondary, fontSize = 11.sp)

            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MeetColors.backgroundDark,
                border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${String.format("%.1f", reqEstimate.distanceKm)} km vial · ETA ~${reqEstimate.etaMinutes} min",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = reqEstimate.trafficLevel.label,
                        color = when (reqEstimate.trafficLevel) {
                            LayaRouteEngine.TrafficLevel.FLUID -> MeetColors.neonGreen
                            LayaRouteEngine.TrafficLevel.MODERATE -> MeetColors.warning
                            else -> MeetColors.error
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onViewOnMap,
                    modifier = Modifier
                        .size(40.dp)
                        .border(1.dp, MeetColors.cyberCyan, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Map, contentDescription = "Ver en Radar", tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                }

                OutlinedButton(
                    onClick = onCounterOffer,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Contraofertar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onTakeDirect,
                    modifier = Modifier.weight(1.1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MeetColors.backgroundDark, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tomar Directo", color = MeetColors.backgroundDark, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun SpecialistCounterOfferDialog(
    request: ServiceRequestEntity,
    onDismiss: () -> Unit,
    onSubmitCounterOffer: (price: Double, hours: Double, warranty: Int, note: String) -> Unit,
) {
    var priceText by remember { mutableStateOf(request.priceOffer.toInt().toString()) }
    var arrivalMinutesText by remember { mutableStateOf("25") }
    var warrantyDaysText by remember { mutableStateOf("30") }
    var technicalNote by remember { mutableStateOf("Servicio con herramientas profesionales certificadas y garantía local.") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF081422)),
            border = BorderStroke(1.dp, MeetColors.cyberCyan)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("ENVIAR CONTRAOFERTA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("Ajusta el precio, tiempo de llegada y condiciones", color = MeetColors.textSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = { Text("Tu Precio Propuesto (CRC)") },
                    prefix = { Text("₡ ", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = arrivalMinutesText,
                        onValueChange = { arrivalMinutesText = it.filter(Char::isDigit) },
                        label = { Text("Llegada (min)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = warrantyDaysText,
                        onValueChange = { warrantyDaysText = it.filter(Char::isDigit) },
                        label = { Text("Garantía (días)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = technicalNote,
                    onValueChange = { technicalNote = it },
                    label = { Text("Nota Técnica para el Cliente") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 2
                )

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull() ?: request.priceOffer
                            val warranty = warrantyDaysText.toIntOrNull() ?: 30
                            onSubmitCounterOffer(price, 1.0, warranty, technicalNote)
                        },
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ENVIAR CONTRAOFERTA", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoWayRatingDialog(
    request: ServiceRequestEntity,
    onDismiss: () -> Unit,
    onSubmitRating: (stars: Int, praiseTags: List<String>, review: String) -> Unit,
) {
    var selectedStars by remember { mutableIntStateOf(5) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var reviewText by remember { mutableStateOf("") }

    val tags = listOf(
        "Puntualidad Impecable",
        "Diagnóstico Preciso",
        "Precio Honesto",
        "Herramienta Profesional",
        "Excelente Trato"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF081422)),
            border = BorderStroke(1.dp, MeetColors.neonGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("CALIFICAR SERVICIO (ESTILO VIAJES)", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("Tu valoración honesta forja la confianza de la red", color = MeetColors.textSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(14.dp))

                // Stars row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 1..5) {
                        IconButton(onClick = { selectedStars = i }) {
                            Icon(
                                imageVector = if (i <= selectedStars) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i estrellas",
                                tint = if (i <= selectedStars) Color(0xFFFFB300) else MeetColors.textMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Praise tags
                Text("Aspectos Destacados:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tags) { tag ->
                        val isSelected = tag in selectedTags
                        Surface(
                            modifier = Modifier.clickable {
                                selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.2f) else MeetColors.backgroundDark,
                            border = BorderStroke(1.dp, if (isSelected) MeetColors.neonGreen else MeetColors.borderSubtle)
                        ) {
                            Text(
                                text = tag,
                                color = if (isSelected) MeetColors.neonGreen else MeetColors.textSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    placeholder = { Text("Comentario adicional opcional...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 2
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSubmitRating(selectedStars, selectedTags.toList(), reviewText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CONFIRMAR & ENVIAR CALIFICACIÓN ⭐", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun VoiceSearchAssistantDialog(
    onDismiss: () -> Unit,
    onQueryRecognized: (String) -> Unit,
) {
    var sampleSpeech by remember { mutableStateOf("Ocupo una grúa en Cartago centro") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF071424)),
            border = BorderStroke(1.dp, MeetColors.neonGreen)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(MeetColors.neonGreen.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, MeetColors.neonGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(32.dp))
                }

                Spacer(Modifier.height(14.dp))
                Text("BÚSQUEDA POR VOZ CON LAYA", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("Di lo que necesitas y Laya ubicará el servicio más cercano", color = MeetColors.textSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(14.dp))

                // Presets for quick voice testing
                listOf(
                    "Ocupo una grúa en Cartago centro",
                    "Taller mecánico cerca de San Pedro",
                    "Cerrajero automotriz en Escazú",
                    "Vamos a La Sabana"
                ).forEach { preset ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable {
                                onQueryRecognized(preset)
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = MeetColors.backgroundDark,
                        border = BorderStroke(0.5.dp, MeetColors.borderSubtle)
                    ) {
                        Text(
                            text = "“$preset”",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                    Text("Cerrar", color = Color.White)
                }
            }
        }
    }
}

/**
 * ── Selector de Modo de Vista (Solicitudes vs Radar en Vivo) ──
 */
@Composable
private fun ViewModeSelector(
    selectedMode: String,
    onSelectMode: (String) -> Unit,
    activeAcceptedCount: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MeetColors.cardBackground,
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isList = selectedMode == "LIST"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMode("LIST") },
                shape = RoundedCornerShape(9.dp),
                color = if (isList) MeetColors.cyberCyan.copy(alpha = 0.22f) else Color.Transparent,
                border = if (isList) BorderStroke(1.dp, MeetColors.cyberCyan) else null
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = if (isList) MeetColors.cyberCyan else MeetColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SOLICITUDES",
                        color = if (isList) MeetColors.cyberCyan else MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isList) FontWeight.Black else FontWeight.Bold
                    )
                }
            }

            val isMap = selectedMode == "MAP"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectMode("MAP") },
                shape = RoundedCornerShape(9.dp),
                color = if (isMap) MeetColors.neonGreen.copy(alpha = 0.22f) else Color.Transparent,
                border = if (isMap) BorderStroke(1.dp, MeetColors.neonGreen) else null
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        tint = if (isMap) MeetColors.neonGreen else MeetColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "RADAR & RUTA EN VIVO",
                        color = if (isMap) MeetColors.neonGreen else MeetColors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isMap) FontWeight.Black else FontWeight.Bold
                    )
                    if (activeAcceptedCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MeetColors.neonGreen
                        ) {
                            Text(
                                text = "$activeAcceptedCount",
                                color = MeetColors.backgroundDark,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ── Radar & Mapa en Vivo con Rutas Reales, ETA Dinámico y Refugios Seguros ──
 */
@Composable
private fun ElysiumServicesLiveMapRadar(
    allRequests: List<ServiceRequestEntity>,
    focusedRequest: ServiceRequestEntity?,
    onSelectRequest: (ServiceRequestEntity) -> Unit,
    userGps: ObdViewModel.GpsLocationInfo?,
    isSpecialistMode: Boolean,
    onCompleteRequest: (ServiceRequestEntity) -> Unit,
) {
    val activeRequest = focusedRequest
        ?: allRequests.firstOrNull { it.status == "ACCEPTED" }
        ?: allRequests.firstOrNull { it.status == "OPEN" }

    val clientLat = if (activeRequest != null && activeRequest.latitude != 0.0) activeRequest.latitude else (userGps?.latitude ?: 9.9333)
    val clientLon = if (activeRequest != null && activeRequest.longitude != 0.0) activeRequest.longitude else (userGps?.longitude ?: -84.0833)
    val clientPoint = GeoPoint(clientLat, clientLon)

    val specialistLat = if (isSpecialistMode && userGps != null) userGps.latitude else (clientLat + 0.016)
    val specialistLon = if (isSpecialistMode && userGps != null) userGps.longitude else (clientLon + 0.014)
    val specialistPoint = GeoPoint(specialistLat, specialistLon)

    val routeEstimate = remember(specialistPoint, clientPoint) {
        LayaRouteEngine.calculateRoute(
            origin = specialistPoint,
            destination = clientPoint
        )
    }

    val companionState = remember(clientPoint, specialistPoint, isSpecialistMode) {
        ElysiumRouteCompanion.evaluateCompanionState(
            currentLatitude = clientPoint.latitude,
            currentLongitude = clientPoint.longitude,
            destinationLatitude = specialistPoint.latitude,
            destinationLongitude = specialistPoint.longitude,
            isClientWaiting = !isSpecialistMode
        )
    }

    var selectedBottomTab by remember { mutableStateOf("STAGING") } // "STAGING" or "CHECKLIST"

    // Build CommonMapState
    val mapMarkers = remember(activeRequest, allRequests, clientPoint, specialistPoint, companionState) {
        val markers = mutableListOf<GeoMarker>()

        // 1. Client / Vehicle marker
        markers.add(
            GeoMarker(
                id = "client_vehicle",
                role = GeoMarkerRole.DESTINATION,
                point = clientPoint,
                label = if (activeRequest != null) activeRequest.problem else "Tu Ubicación",
                subtitle = if (activeRequest != null) "₡${String.format("%,.0f", activeRequest.priceOffer)} CRC" else "Costa Rica",
                isHighlighted = true
            )
        )

        // 2. Specialist marker
        markers.add(
            GeoMarker(
                id = "specialist_live",
                role = GeoMarkerRole.PROVIDER_LIVE,
                point = specialistPoint,
                label = if (isSpecialistMode) "Tu Posición (Especialista)" else "Especialista MEET",
                subtitle = "ETA ~${routeEstimate.etaMinutes} min (${routeEstimate.trafficLevel.label})",
                isHighlighted = true
            )
        )

        // 3. Safe Staging Zones (refugios seguros de Costa Rica)
        companionState.safeStagingZones.take(2).forEachIndexed { idx, zone ->
            markers.add(
                GeoMarker(
                    id = "safe_staging_$idx",
                    role = GeoMarkerRole.STORE_LOCATION,
                    point = GeoPoint(zone.place.latitude, zone.place.longitude),
                    label = "Zona Segura: ${zone.place.name}",
                    subtitle = "${zone.distanceKm} km · ${zone.recommendationReason}"
                )
            )
        }

        // 4. Other open requests in the radar
        allRequests.filter { it.requestId != activeRequest?.requestId && it.status == "OPEN" && it.latitude != 0.0 }.forEach { req ->
            markers.add(
                GeoMarker(
                    id = "req_${req.requestId}",
                    role = GeoMarkerRole.GENERIC_SERVICE,
                    point = GeoPoint(req.latitude, req.longitude),
                    label = "Solicitud: ${req.problem.take(20)}...",
                    subtitle = "₡${String.format("%,.0f", req.priceOffer)} CRC"
                )
            )
        }

        markers
    }

    val mapRoutes = remember(routeEstimate) {
        listOf(routeEstimate.geoRoute)
    }

    val cameraBounds = remember(clientPoint, specialistPoint) {
        GeoBounds.fromPoints(listOf(clientPoint, specialistPoint)) ?: GeoBounds(
            northLat = kotlin.math.max(clientPoint.latitude, specialistPoint.latitude) + 0.02,
            southLat = kotlin.math.min(clientPoint.latitude, specialistPoint.latitude) - 0.02,
            eastLng = kotlin.math.max(clientPoint.longitude, specialistPoint.longitude) + 0.02,
            westLng = kotlin.math.min(clientPoint.longitude, specialistPoint.longitude) - 0.02
        )
    }

    val mapState = remember(mapMarkers, mapRoutes, cameraBounds) {
        CommonMapState(
            markers = mapMarkers,
            routes = mapRoutes,
            cameraIntent = MapCameraIntent.FitBounds(cameraBounds, paddingDp = 64),
            isInteractive = true,
            showRecenterButton = true,
            showTrafficOverlay = true
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Universal MapLibre Engine
        CommonMapPanel(
            state = mapState,
            modifier = Modifier.fillMaxSize(),
            userLocation = GeoPoint(userGps?.latitude ?: clientLat, userGps?.longitude ?: clientLon),
            onMarkerClick = { markerId ->
                if (markerId.startsWith("req_")) {
                    val reqId = markerId.removePrefix("req_")
                    allRequests.firstOrNull { it.requestId == reqId }?.let { onSelectRequest(it) }
                }
            }
        )

        // ── Floating HUD Header (ETA & Traffic & PIN) ──
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEA06111D)),
                border = BorderStroke(1.5.dp, if (companionState.isArrivingSoon) MeetColors.warning else MeetColors.cyberCyan)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏱️", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "ETA: ${routeEstimate.etaMinutes} MIN",
                                    color = if (companionState.isArrivingSoon) MeetColors.warning else MeetColors.cyberCyan,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${String.format("%.1f", routeEstimate.distanceKm)} KM · Velocidad: ${routeEstimate.estimatedSpeedKmh.toInt()} km/h",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Traffic chip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (routeEstimate.trafficLevel) {
                                LayaRouteEngine.TrafficLevel.FLUID -> MeetColors.neonGreen.copy(alpha = 0.2f)
                                LayaRouteEngine.TrafficLevel.MODERATE -> MeetColors.warning.copy(alpha = 0.2f)
                                else -> MeetColors.error.copy(alpha = 0.2f)
                            },
                            border = BorderStroke(1.dp, when (routeEstimate.trafficLevel) {
                                LayaRouteEngine.TrafficLevel.FLUID -> MeetColors.neonGreen
                                LayaRouteEngine.TrafficLevel.MODERATE -> MeetColors.warning
                                else -> MeetColors.error
                            })
                        ) {
                            Text(
                                text = routeEstimate.trafficLevel.label,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (companionState.isArrivingSoon) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MeetColors.warning.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MeetColors.warning)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡", fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "¡LLEGADA INMINENTE! Especialista a menos de 3 minutos del vehículo.",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Consejo táctico: ${routeEstimate.tacticalAdvice}",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    // Security PIN display (Aura Sentinel)
                    if (activeRequest != null && activeRequest.status == "ACCEPTED") {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PIN AURA SENTINEL:",
                                color = MeetColors.neonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MeetColors.neonGreen.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MeetColors.neonGreen)
                            ) {
                                Text(
                                    text = activeRequest.requestId.takeLast(4).uppercase(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick request selector chip row if multiple requests
            if (allRequests.size > 1) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allRequests) { req ->
                        val isSelected = req.requestId == activeRequest?.requestId
                        Surface(
                            modifier = Modifier.clickable { onSelectRequest(req) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MeetColors.cyberCyan else Color(0xCC06111D),
                            border = BorderStroke(1.dp, if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle)
                        ) {
                            Text(
                                text = "${req.problem.take(16)}... (₡${req.priceOffer.toInt()})",
                                color = if (isSelected) MeetColors.backgroundDark else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Floating Bottom Panel (Safe Staging Zones & Safety Checklist) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF2081524)),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Tab Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedBottomTab = "STAGING" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedBottomTab == "STAGING") MeetColors.cyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (selectedBottomTab == "STAGING") BorderStroke(1.dp, MeetColors.cyberCyan) else null
                        ) {
                            Text(
                                text = "🛡️ ZONAS SEGURAS",
                                color = if (selectedBottomTab == "STAGING") MeetColors.cyberCyan else MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedBottomTab = "CHECKLIST" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedBottomTab == "CHECKLIST") MeetColors.neonGreen.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (selectedBottomTab == "CHECKLIST") BorderStroke(1.dp, MeetColors.neonGreen) else null
                        ) {
                            Text(
                                text = "📋 PROTOCOLO AURA",
                                color = if (selectedBottomTab == "CHECKLIST") MeetColors.neonGreen else MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (selectedBottomTab == "STAGING") {
                        Text(
                            text = "Puntos de espera recomendados en caso de inmovilización:",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        companionState.safeStagingZones.take(2).forEach { zone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📍", fontSize = 12.sp)
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${zone.place.name} (${zone.distanceKm} km)",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = zone.recommendationReason,
                                        color = MeetColors.textSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    } else {
                        companionState.safetyChecklist.take(3).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✓", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = item,
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    if (activeRequest != null && activeRequest.status == "ACCEPTED") {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onCompleteRequest(activeRequest) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.backgroundDark, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("COMPLETAR TRABAJO & CALIFICAR", color = MeetColors.backgroundDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
