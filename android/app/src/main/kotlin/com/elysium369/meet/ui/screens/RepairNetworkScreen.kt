package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.data.local.MechanicalKnowledgeBundle
import com.elysium369.meet.ui.RepairNetworkViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.neonGlow
import org.json.JSONArray
import org.json.JSONObject

import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.data.local.entities.ServiceBidEntity
import com.elysium369.meet.data.local.entities.PartOfferEntity
import com.elysium369.meet.data.local.entities.PartRequestEntity
import kotlinx.coroutines.flow.Flow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import com.elysium369.meet.core.parts.PartSuggestionEngine
import com.elysium369.meet.core.parts.PartSuggestionInput
import com.elysium369.meet.core.parts.PartRequestPublicationPolicy
import com.elysium369.meet.core.parts.SuggestionSource
import com.elysium369.meet.ui.knowledge.RepairKnowledgeEvidencePanel
import com.elysium369.meet.ui.knowledge.RepairKnowledgeUiState
import com.elysium369.meet.ui.knowledge.rememberRepairKnowledgeUiState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RepairNetworkScreen(
    navController: NavController,
    viewModel: RepairNetworkViewModel,
    obdViewModel: ObdViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val knowledgeBundle by viewModel.knowledgeBundle.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Marketplace live state
    val serviceRequests by obdViewModel.serviceRequests.collectAsState()
    val predictionEvents by obdViewModel.predictionEvents.collectAsState()
    val selectedVehicle by obdViewModel.selectedVehicle.collectAsState()
    val activeDtcs by obdViewModel.activeDtcs.collectAsState()
    val repairKnowledgeState by rememberRepairKnowledgeUiState(
        vehicle = selectedVehicle,
        dtcs = activeDtcs
    )
    val repairBundle = (repairKnowledgeState as? RepairKnowledgeUiState.Ready)?.bundle
    val partRequests by obdViewModel.partRequests.collectAsState()
    val vehicles by obdViewModel.vehicles.collectAsState()

    // Derived states: filter by user's own vehicles to prevent viewing other users' requests, and sort active (OPEN) first
    val sortedServiceRequests by remember {
        derivedStateOf {
            val userVehicleIds = vehicles.map { it.id }
            serviceRequests
                .filter { it.vehicleId in userVehicleIds }
                .sortedWith(compareBy<ServiceRequestEntity> { if (it.status == "OPEN") 0 else 1 }.thenByDescending { it.createdAt })
        }
    }
    val sortedPartRequests by remember {
        derivedStateOf {
            val userVehicleIds = vehicles.map { it.id }
            partRequests
                .filter { it.vehicleId in userVehicleIds }
                .sortedWith(compareBy<PartRequestEntity> { if (it.status == "OPEN") 0 else 1 }.thenByDescending { it.createdAt })
        }
    }
    
    // Local filter display state
    var showFilters by remember { mutableStateOf(false) }
    val makeFilter by viewModel.makeFilter.collectAsState()
    val modelFilter by viewModel.modelFilter.collectAsState()
    val dtcFilter by viewModel.dtcFilter.collectAsState()
    val countryFilter by viewModel.countryFilter.collectAsState()
    val sortByFilter by viewModel.sortByFilter.collectAsState()
    val onlyVerifiedFilter by viewModel.onlyVerifiedFilter.collectAsState()

    // Dialog state for Marketplace
    var showCreateDialog by remember { mutableStateOf(false) }
    var problemInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("San José, Costa Rica") }
    var priorityInput by remember { mutableStateOf("MEDIUM") }
    
    var showPartRequestDialog by remember { mutableStateOf(false) }
    var partServiceRequestId by remember { mutableStateOf<String?>(null) }
    var partVehicleId by remember { mutableStateOf<String?>(null) }
    var partDtcCode by remember { mutableStateOf<String?>(null) }
    var partNameInput by remember { mutableStateOf("") }
    var partNumberInput by remember { mutableStateOf("") }
    var partQuantityInput by remember { mutableStateOf("1") }
    var partPreferenceInput by remember { mutableStateOf("ANY") }
    var partUrgencyInput by remember { mutableStateOf("40") }
    var partNotesInput by remember { mutableStateOf("") }
    var partDeliveryInput by remember { mutableStateOf("San José, Costa Rica") }
    
    var showPartOfferDialog by remember { mutableStateOf<String?>(null) }
    var offerStoreName by remember { mutableStateOf("") }
    var offerBrand by remember { mutableStateOf("") }
    var offerPartNumber by remember { mutableStateOf("") }
    var offerCondition by remember { mutableStateOf("NEW") }
    var offerPrice by remember { mutableStateOf("") }
    var offerDeliveryFee by remember { mutableStateOf("0") }
    var offerEta by remember { mutableStateOf("40") }
    var offerWarranty by remember { mutableStateOf("30") }
    var offerMessage by remember { mutableStateOf("") }

    // Service Contract confirmation dialog state
    var showAcceptConfirmDialog by remember { mutableStateOf(false) }
    var pendingAcceptRequestId by remember { mutableStateOf<String?>(null) }
    var pendingAcceptBidId by remember { mutableStateOf<String?>(null) }
    var pendingAcceptShopName by remember { mutableStateOf("") }
    var pendingAcceptPrice by remember { mutableStateOf(0.0) }
    var pendingAcceptWarranty by remember { mutableStateOf(0) }
    var pendingAcceptHours by remember { mutableStateOf(0.0) }
    var pendingAcceptMessage by remember { mutableStateOf("") }

    // Part purchase confirmation dialog state
    var showPartConfirmDialog by remember { mutableStateOf(false) }
    var pendingPartRequestId by remember { mutableStateOf<String?>(null) }
    var pendingPartOfferId by remember { mutableStateOf<String?>(null) }
    var pendingPartStoreName by remember { mutableStateOf("") }
    var pendingPartName by remember { mutableStateOf("") }
    var pendingPartPrice by remember { mutableStateOf(0.0) }
    var pendingPartEta by remember { mutableStateOf(0) }
    var pendingPartWarranty by remember { mutableStateOf(0) }
    var pendingPartBrand by remember { mutableStateOf("") }


    var showRegistrationScreen by remember { mutableStateOf(false) }

    if (showRegistrationScreen) {
        ProviderRegistrationScreen(
            viewModel = obdViewModel,
            onNavigateBack = { showRegistrationScreen = false }
        )
        return
    }

    LaunchedEffect(Unit) {
        obdViewModel.voiceFeedbackManager.speak(
            es = "Red de Reparaciones y Talleres Certificados activa. Conecta con mecánicos especializados y repuestos con compatibilidad verificada por VIN.",
            en = "Repair Network and Certified Workshops active. Connect with specialized mechanics and VIN-verified compatible parts."
        )
    }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "Elysium Vanguard REPAIR NETWORK\nStackOverflow Mecánico",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    IconButton(onClick = { navController.navigate("messages?serviceVertical=repair") }) {
                        AnimatedNeonIcon(
                            Icons.Default.Chat,
                            contentDescription = "Mensajes del servicio",
                            tint = MeetColors.cyberCyan,
                        )
                    }
                    IconButton(onClick = {
                        obdViewModel.voiceFeedbackManager.speak(
                            es = "Red Mecánica Elysium: Consultoría de fallas DTC, cotizaciones de talleres y compra de repuestos en tiempo real.",
                            en = "Elysium Mechanical Network: DTC troubleshooting, workshop quotes and parts marketplace in real time."
                        )
                    }) {
                        AnimatedNeonIcon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Voz Asistente",
                            tint = MeetColors.neonGreen
                        )
                    }
                    IconButton(onClick = { showRegistrationScreen = true }) {
                        AnimatedNeonIcon(
                            Icons.Default.Badge,
                            contentDescription = "Registro Proveedor",
                            tint = MeetColors.warning
                        )
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        AnimatedNeonIcon(
                            Icons.Default.FilterList,
                            contentDescription = "Filtros",
                            tint = if (showFilters) MeetColors.neonGreen else Color.White
                        )
                    }
                    IconButton(onClick = { navController.navigate("workshop_dashboard") }) {
                        AnimatedNeonIcon(
                            Icons.Default.CarRepair,
                            contentDescription = "Taller",
                            tint = MeetColors.cyberCyan
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MeetColors.backgroundDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .border(1.dp, MeetColors.neonGreen, RoundedCornerShape(12.dp))
                    .neonGlow(MeetColors.neonGreen, RoundedCornerShape(12.dp), minElevation = 4f, maxElevation = 12f)
            ) {
                AnimatedNeonIcon(Icons.Default.Add, contentDescription = "Nueva Solicitud", tint = MeetColors.neonGreen)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sticky Search Input Header
            stickyHeader {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Buscar DTC, Síntomas, Repuestos o Marcas...", color = MeetColors.textSecondary) },
                    leadingIcon = { AnimatedNeonIcon(Icons.Default.Search, contentDescription = "Buscar", tint = MeetColors.neonGreen) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                AnimatedNeonIcon(Icons.Default.Clear, contentDescription = "Limpiar", tint = MeetColors.textSecondary)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeetColors.backgroundDark)
                        .padding(bottom = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.neonGreen,
                        unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                        focusedContainerColor = MeetColors.backgroundDeep,
                        unfocusedContainerColor = MeetColors.backgroundDeep
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Workflow Guide + 3 inline service buttons
            item {
                RepairNetworkWorkflowGuide(
                    currentQuery = searchQuery,
                    onQuickSearch = { viewModel.setSearchQuery(it) },
                    onOpenMechanic = { navController.navigate("mechanic_service") },
                    onOpenTowTruck = { navController.navigate("tow_truck_service") },
                    onOpenParts = { navController.navigate("part_request") },
                    onOpenRide = { navController.navigate("ride_service") },
                    onOpenDekra = { navController.navigate("dekra_concierge") },
                    onOpenTheoryExam = { navController.navigate("theory_exam_preparation") },
                    onOpenUniversalServices = { navController.navigate("universal_services") },
                    onOpenCommunityCases = { navController.navigate("community_cases") }
                )
            }

            item {
                MobilitySafetyBridgeCard(
                    vehicleLabel = selectedVehicle?.let {
                        "${it.make} ${it.model} ${it.year}"
                    },
                    activeDtcCodes = activeDtcs,
                    onOpenRide = { navController.navigate("ride_service") },
                    onOpenGarage = { navController.navigate("garage") },
                )
            }

            // Knowledge Panel (conditional)
            if (searchQuery.isNotBlank() || dtcFilter.isNotBlank()) {
                item {
                    RepairKnowledgePanel(bundle = knowledgeBundle)
                }
            }

            // Advanced Filters Panel (Expandable)
            if (showFilters) {
                item {
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.cyberCyan,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "FILTROS AVANZADOS DE BÚSQUEDA",
                                color = MeetColors.cyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = makeFilter,
                                    onValueChange = { viewModel.setMakeFilter(it) },
                                    label = { Text("Marca", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                                )
                                OutlinedTextField(
                                    value = modelFilter,
                                    onValueChange = { viewModel.setModelFilter(it) },
                                    label = { Text("Modelo", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = dtcFilter,
                                    onValueChange = { viewModel.setDtcFilter(it) },
                                    label = { Text("Código DTC", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                                )
                                OutlinedTextField(
                                    value = countryFilter,
                                    onValueChange = { viewModel.setCountryFilter(it) },
                                    label = { Text("País", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                    modifier = Modifier.weight(1f),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                                )
                            }

                            // Order & Verified filters
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = onlyVerifiedFilter,
                                        onCheckedChange = { viewModel.setOnlyVerifiedFilter(it) },
                                        colors = CheckboxDefaults.colors(checkedColor = MeetColors.cyberCyan)
                                    )
                                    Text("Solo Verificados", color = Color.White, fontSize = 12.sp)
                                }
                                
                                // Simple sort selector
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                                        .clickable {
                                            val nextSort = if (sortByFilter == "votes") "success_rate" else if (sortByFilter == "success_rate") "date" else "votes"
                                            viewModel.setSortByFilter(nextSort)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ORDEN: " + when(sortByFilter) {
                                            "success_rate" -> "ÉXITO"
                                            "date" -> "FECHA"
                                            else -> "VOTOS"
                                        },
                                        color = MeetColors.cyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── AI Health Detected Issues ──
            if (predictionEvents.isNotEmpty()) {
                item {
                    Text(
                        text = "ALERTAS DE SALUD IA ACTIVAS",
                        color = Color(0xFFFF4D4D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                items(predictionEvents.take(2)) { event ->
                    EliteCard(
                        glowColor = Color(0xFFFF4D4D),
                        borderColor = Color(0xFFFF4D4D).copy(alpha = 0.2f),
                        backgroundColor = MeetColors.cardBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFFF4D4D).copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedNeonIcon(Icons.Default.Warning, "Alerta", tint = Color(0xFFFF4D4D), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    event.message,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Prioridad: ${event.severity} | Fallo estimado en ~${event.estimatedDays} días",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            EliteButton(
                                text = "COTIZAR",
                                onClick = {
                                    problemInput = "Reemplazo de pastillas / sensor"
                                    descInput = "Alerta AI detectada: ${event.message}"
                                    priorityInput = "HIGH"
                                    showCreateDialog = true
                                },
                                color = MeetColors.neonGreen,
                                modifier = Modifier.width(90.dp)
                            )
                        }
                    }
                }
            }

            // ── Service Requests List ──
            item {
                Text(
                    text = "MIS SOLICITUDES DE SERVICIO",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (sortedServiceRequests.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No tienes solicitudes activas.\nPublica una necesidad para empezar a recibir cotizaciones.",
                            color = MeetColors.textMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(sortedServiceRequests) { req ->
                    val bidsFlow = remember(req.requestId) { obdViewModel.getBidsForRequest(req.requestId) }
                    val bids by bidsFlow.collectAsState(initial = emptyList<ServiceBidEntity>())

                    // Dynamically determine type (Tow Truck vs Mechanic) based on typed service or problem text
                    val isTowTruckType = req.problem.lowercase().contains("grúa") || req.problem.lowercase().contains("grua") || req.problem.lowercase().contains("remolque") || req.description.lowercase().contains("grúa")
                    val typeLabel = if (isTowTruckType) "🚛 SERVICIO DE GRÚA VIP" else "🛠️ MECÁNICO / TALLER VIP"
                    val typeColor = if (isTowTruckType) MeetColors.warning else MeetColors.neonGreen

                    EliteCard(
                        glowColor = if (req.status == "ACCEPTED") MeetColors.cyberCyan else MeetColors.electricBlue,
                        borderColor = MeetColors.borderSubtle,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header with Problem name and Priority badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        req.problem.uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        typeLabel,
                                        color = typeColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                val badgeColor = when (req.priority) {
                                    "EMERGENCY" -> MeetColors.error
                                    "HIGH" -> MeetColors.warning
                                    else -> MeetColors.cyberCyan
                                }
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = req.priority,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                req.description,
                                color = MeetColors.textSecondary,
                                fontSize = 13.sp
                            )

                            if (!req.autoDtcCode.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("DTC VINCULADO: ", color = MeetColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(req.autoDtcCode, color = MeetColors.error, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Status and Actions
                            if (req.status == "ACCEPTED") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MeetColors.cyberCyan.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                        .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        "⚡ SERVICIO ACEPTADO POR PROVEEDOR",
                                        color = MeetColors.cyberCyan,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Mecánico/Grúa: ${req.assignedMechanicName ?: "Especialista Asignado"}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    if (!req.assignedMechanicPhone.isNullOrBlank()) {
                                        Text(
                                            "Teléfono: ${req.assignedMechanicPhone}",
                                            color = MeetColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    // Call & Navigate Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        EliteButton(
                                            text = "📞 LLAMAR PROVEEDOR",
                                            onClick = {
                                                val phoneNum = req.assignedMechanicPhone
                                                if (!phoneNum.isNullOrBlank()) {
                                                    val dialIntent = android.content.Intent(
                                                        android.content.Intent.ACTION_DIAL,
                                                        android.net.Uri.parse("tel:${phoneNum.replace("-", "").replace(" ", "")}")
                                                    )
                                                    context.startActivity(dialIntent)
                                                } else {
                                                    android.widget.Toast.makeText(context, "Teléfono no disponible", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            color = MeetColors.neonGreen,
                                            modifier = Modifier.weight(1f)
                                        )
                                        EliteButton(
                                            text = "🗺️ WAZE / RUTA",
                                            onClick = {
                                                val wazeUri = "waze://?q=${req.location}&navigate=yes"
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(wazeUri)).apply {
                                                    setPackage("com.waze")
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val mapsIntent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse("geo:0,0?q=${req.location}")
                                                    )
                                                    context.startActivity(mapsIntent)
                                                }
                                            },
                                            color = MeetColors.cyberCyan,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        EliteButton(
                                            text = "✅ COMPLETAR",
                                            onClick = {
                                                obdViewModel.completeMechanicRequest(req.requestId)
                                                android.widget.Toast.makeText(context, "🎉 ¡Servicio finalizado con éxito!", android.widget.Toast.LENGTH_LONG).show()
                                            },
                                            color = MeetColors.electricBlue,
                                            modifier = Modifier.weight(1f)
                                        )
                                        EliteButton(
                                            text = "❌ CANCELAR",
                                            onClick = {
                                                obdViewModel.cancelMechanicRequest(req.requestId)
                                                android.widget.Toast.makeText(context, "Servicio cancelado y reabierto", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            color = Color(0xFFFF4D4D),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else if (req.status == "COMPLETED") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MeetColors.neonGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("✔️ SERVICIO COMPLETADO CON ÉXITO", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                // ── Open Request: Show Bids list ──
                                Text(
                                    "OFERTAS RECIBIDAS (${bids.size})",
                                    color = MeetColors.neonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))

                                if (bids.isEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MeetColors.cyberCyan, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Buscando proveedores y cotizaciones en tiempo real... 📡",
                                            color = MeetColors.textMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    bids.forEach { bid ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MeetColors.cardBackground)
                                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(bid.shopName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Spacer(Modifier.width(6.dp))
                                                    AnimatedNeonIcon(Icons.Default.Star, "Rating", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                                                    Text(" ${bid.shopRating}", color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Text("Tiempo: ${bid.estimatedHours}h | Garantía: ${bid.warrantyDays} días", color = MeetColors.textSecondary, fontSize = 11.sp)
                                                Text(bid.message, color = MeetColors.textMuted, fontSize = 11.sp)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    "¢${String.format("%,.0f", bid.price)}",
                                                    color = MeetColors.neonGreen,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                EliteButton(
                                                    text = "ACEPTAR",
                                                    onClick = {
                                                        // Set pending contract details and show confirmation dialog
                                                        pendingAcceptRequestId = req.requestId
                                                        pendingAcceptBidId = bid.bidId
                                                        pendingAcceptShopName = bid.shopName
                                                        pendingAcceptPrice = bid.price
                                                        pendingAcceptWarranty = bid.warrantyDays
                                                        pendingAcceptHours = bid.estimatedHours
                                                        pendingAcceptMessage = bid.message
                                                        showAcceptConfirmDialog = true
                                                    },
                                                    color = MeetColors.neonGreen,
                                                    modifier = Modifier.width(80.dp).height(28.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            EliteButton(
                                text = "SOLICITAR REPUESTO PARA ESTE CASO",
                                onClick = {
                                    partServiceRequestId = req.requestId
                                    partVehicleId = req.vehicleId
                                    partDtcCode = req.autoDtcCode ?: activeDtcs.firstOrNull()
                                    partNameInput = suggestPartNameForDtc(req.autoDtcCode ?: activeDtcs.firstOrNull(), req.problem)
                                    partNumberInput = ""
                                    partQuantityInput = "1"
                                    partPreferenceInput = "ANY"
                                    partUrgencyInput = "40"
                                    partDeliveryInput = req.location
                                    partNotesInput = buildString {
                                        append("Caso de servicio: ${req.problem}. ")
                                        if (!req.autoDtcCode.isNullOrBlank()) append("DTC asociado: ${req.autoDtcCode}. ")
                                        append(req.description.take(160))
                                    }
                                    showPartRequestDialog = true
                                },
                                color = MeetColors.cyberCyan,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── Spare Parts Bidding Section ──
            item {
                Text(
                    text = "SUBASTA DE REPUESTOS Y REPUESTERAS",
                    color = MeetColors.neonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            if (sortedPartRequests.isEmpty()) {
                item {
                    EliteCard(
                        glowColor = MeetColors.cyberCyan,
                        borderColor = MeetColors.borderSubtle,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Aún no hay piezas en subasta",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Cuando el mecánico confirme una pieza, publica la solicitud. Las repuesteras competirán ofreciendo sus mejores precios.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            } else {
                items(sortedPartRequests) { partReq ->
                    val offersFlow = remember(partReq.requestId) { obdViewModel.getPartOffersForRequest(partReq.requestId) }
                    val offers by offersFlow.collectAsState(initial = emptyList<PartOfferEntity>())

                    EliteCard(
                        glowColor = if (partReq.status == "ACCEPTED") MeetColors.neonGreen else MeetColors.electricBlue,
                        borderColor = MeetColors.borderSubtle,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(partReq.partName.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "🧩 Cantidad: ${partReq.quantity} und · Preferencia: ${partReq.oemPreference} · Lado/Posición: ${partReq.partPosition}",
                                        color = MeetColors.cyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    if (!partReq.dtcCode.isNullOrBlank()) {
                                        Text("DTC asociado: ${partReq.dtcCode}", color = Color(0xFFFFB300), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (partReq.status == "ACCEPTED") MeetColors.neonGreen.copy(alpha = 0.14f) else MeetColors.electricBlue.copy(alpha = 0.14f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(partReq.status, color = if (partReq.status == "ACCEPTED") MeetColors.neonGreen else MeetColors.electricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(partReq.customerNotes, color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedNeonIcon(Icons.Default.LocationOn, "Entrega", tint = MeetColors.textMuted, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(partReq.deliveryLocation, color = MeetColors.textMuted, fontSize = 11.sp)
                            }

                            HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.4f))

                            if (partReq.status == "ACCEPTED") {
                                // ── Active Parts Delivery Contract UI Dashboard ──
                                val acceptedOffer = offers.firstOrNull { it.status == "ACCEPTED" }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MeetColors.neonGreen.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                        .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MeetColors.neonGreen, CircleShape)
                                                .neonGlow(MeetColors.neonGreen, CircleShape, minElevation = 2f, maxElevation = 6f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "COTIZACIÓN ACEPTADA - PREPARANDO PEDIDO 📦",
                                            color = MeetColors.neonGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tienda: ${acceptedOffer?.storeName ?: "NO DISPONIBLE"}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Repuesto: ${acceptedOffer?.brand ?: "NO INFORMADA"} (${acceptedOffer?.partNumber ?: "NO DISPONIBLE"})",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Garantía: ${acceptedOffer?.warrantyDays?.let { "$it días" } ?: "NO INFORMADA"} | ETA: ${acceptedOffer?.etaMinutes?.let { "$it minutos" } ?: "NO DISPONIBLE"}",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        EliteButton(
                                            text = "📞 CONTACTAR TIENDA",
                                            onClick = {
                                                android.widget.Toast.makeText(context, "Canal de soporte seguro activado con la repuestera", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            color = MeetColors.neonGreen,
                                            modifier = Modifier.weight(1f)
                                        )
                                        EliteButton(
                                            text = "🗺️ WAZE / RUTA",
                                            onClick = {
                                                val wazeUri = "waze://?q=${partReq.deliveryLocation}&navigate=yes"
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(wazeUri)).apply {
                                                    setPackage("com.waze")
                                                }
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    val mapsIntent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse("geo:0,0?q=${partReq.deliveryLocation}")
                                                    )
                                                    context.startActivity(mapsIntent)
                                                }
                                            },
                                            color = MeetColors.cyberCyan,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    val currentOffer = acceptedOffer
                                    if (currentOffer != null && currentOffer.offerId.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        EliteButton(
                                            text = "📦 MARCAR COMO RECIBIDO",
                                            onClick = {
                                                obdViewModel.confirmPartReceipt(partReq.requestId, currentOffer.offerId, context)
                                            },
                                            color = MeetColors.electricBlue,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            } else {
                                Text("OFERTAS DE REPUESTERAS (${offers.size})", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (offers.isEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MeetColors.cyberCyan, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Buscando repuestos y cotizaciones de tiendas... 📡",
                                            color = MeetColors.textMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    offers.forEach { offer ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MeetColors.cardBackground)
                                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(offer.storeName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("${offer.brand} · ${offer.partNumber} · ${offer.condition}", color = MeetColors.textSecondary, fontSize = 11.sp)
                                                Text("Entrega ${offer.etaMinutes} min · Garantía ${offer.warrantyDays} días", color = MeetColors.textMuted, fontSize = 11.sp)
                                                if (offer.message.isNotBlank()) Text(offer.message, color = MeetColors.textMuted, fontSize = 11.sp)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("¢${String.format("%,.0f", offer.price + offer.deliveryFee)}", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                                Spacer(Modifier.height(4.dp))
                                                if (partReq.status == "OPEN") {
                                                    EliteButton(
                                                        text = "ACEPTAR",
                                                        onClick = {
                                                            // Trigger Part purchase confirmation dialog
                                                            pendingPartRequestId = partReq.requestId
                                                            pendingPartOfferId = offer.offerId
                                                            pendingPartStoreName = offer.storeName
                                                            pendingPartName = partReq.partName
                                                            pendingPartPrice = offer.price + offer.deliveryFee
                                                            pendingPartEta = offer.etaMinutes
                                                            pendingPartWarranty = offer.warrantyDays
                                                            pendingPartBrand = offer.brand
                                                            showPartConfirmDialog = true
                                                        },
                                                        color = MeetColors.neonGreen,
                                                        modifier = Modifier.width(84.dp)
                                                    )
                                                } else if (offer.status == "ACCEPTED") {
                                                    Text("ACEPTADA", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            EliteButton(
                                text = "OFERTAR COMO REPUESTERA",
                                onClick = {
                                    showPartOfferDialog = partReq.requestId
                                    offerStoreName = ""
                                    offerBrand = ""
                                    offerPartNumber = partReq.partNumber.orEmpty()
                                    offerCondition = "NEW"
                                    offerPrice = ""
                                    offerDeliveryFee = "0"
                                    offerEta = partReq.urgencyMinutes.toString()
                                    offerWarranty = "30"
                                    offerMessage = ""
                                },
                                color = MeetColors.electricBlue,
                                modifier = Modifier.fillMaxWidth(),
                                isEnabled = partReq.status == "OPEN"
                            )
                        }
                    }
                }
            }
        }

        // ── Smart Service Request Composer V2 ──
        if (showCreateDialog) {
            com.elysium369.meet.ui.screens.serviceos.SmartServiceRequestComposerDialog(
                activeVehicle = selectedVehicle,
                activeDtcs = activeDtcs,
                onDismiss = { showCreateDialog = false },
                onSubmit = { request ->
                    obdViewModel.createServiceRequestV2(request)
                    showCreateDialog = false
                }
            )
        }

        if (showPartRequestDialog) {
            val effectivePartDtc = partDtcCode ?: activeDtcs.firstOrNull()
            val dialogSuggestions = remember(effectivePartDtc, repairBundle) {
                val input = PartSuggestionInput(
                    source = SuggestionSource.DTC,
                    dtcCodes = listOfNotNull(effectivePartDtc)
                )
                repairBundle?.let { PartSuggestionEngine.suggestParts(input, it) }
                    ?: PartSuggestionEngine.suggestParts(input)
            }
            val selectedDialogSuggestion = dialogSuggestions.firstOrNull {
                it.partName.equals(partNameInput.trim(), ignoreCase = true)
            }
            val dialogPublicationDecision = PartRequestPublicationPolicy.evaluate(
                partName = partNameInput,
                vehiclePresent = !(partVehicleId ?: selectedVehicle?.id).isNullOrBlank(),
                contactPresent = true,
                graphEvidenceRequired = effectivePartDtc != null,
                compatibility = null,
                suggestion = selectedDialogSuggestion,
                knowledge = repairBundle
            )
            AlertDialog(
                onDismissRequest = { showPartRequestDialog = false },
                containerColor = MeetColors.backgroundDeep,
                title = { Text("Solicitar Repuesto a Repuesteras", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Publica pieza, vehículo, urgencia y punto de entrega. Las ofertas se comparan por precio total, tiempo de llegada y garantía.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        RepairKnowledgeEvidencePanel(
                            state = repairKnowledgeState,
                            accentColor = MeetColors.cyberCyan
                        )
                        OutlinedTextField(
                            value = partNameInput,
                            onValueChange = { partNameInput = it },
                            label = { Text("Pieza requerida", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                        )
                        OutlinedTextField(
                            value = partNumberInput,
                            onValueChange = { partNumberInput = it },
                            label = { Text("Número de parte / OEM (opcional)", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = partQuantityInput,
                                onValueChange = { partQuantityInput = it },
                                label = { Text("Cantidad", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                            )
                            OutlinedTextField(
                                value = partUrgencyInput,
                                onValueChange = { partUrgencyInput = it },
                                label = { Text("ETA máx. min", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ANY" to "Cualquiera", "OEM" to "OEM", "AFTERMARKET" to "Aftermarket").forEach { (value, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (partPreferenceInput == value) MeetColors.cyberCyan.copy(alpha = 0.14f) else MeetColors.backgroundDark)
                                        .border(1.dp, if (partPreferenceInput == value) MeetColors.cyberCyan else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { partPreferenceInput = value }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (partPreferenceInput == value) Color.White else MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = partDeliveryInput,
                            onValueChange = { partDeliveryInput = it },
                            label = { Text("Entrega en", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                        )
                        OutlinedTextField(
                            value = partNotesInput,
                            onValueChange = { partNotesInput = it },
                            label = { Text("Notas para compatibilidad", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val vehicleId = partVehicleId ?: selectedVehicle?.id
                            if (
                                !vehicleId.isNullOrBlank() &&
                                partNameInput.isNotBlank() &&
                                dialogPublicationDecision.allowed
                            ) {
                                obdViewModel.createPartRequest(
                                    serviceRequestId = partServiceRequestId,
                                    vehicleId = vehicleId,
                                    dtcCode = partDtcCode ?: activeDtcs.firstOrNull(),
                                    partName = partNameInput,
                                    partNumber = partNumberInput,
                                    quantity = partQuantityInput.toIntOrNull() ?: 1,
                                    oemPreference = partPreferenceInput,
                                    deliveryLocation = partDeliveryInput,
                                    urgencyMinutes = partUrgencyInput.toIntOrNull() ?: 40,
                                    customerNotes = partNotesInput
                                )
                                showPartRequestDialog = false
                                partNameInput = ""
                                partNumberInput = ""
                                partNotesInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                        enabled =
                            !(partVehicleId ?: selectedVehicle?.id).isNullOrBlank() &&
                                partNameInput.isNotBlank() &&
                                dialogPublicationDecision.allowed
                    ) {
                        Text("PUBLICAR PIEZA", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPartRequestDialog = false }) {
                        Text("CANCELAR", color = MeetColors.textSecondary)
                    }
                }
            )
        }

        showPartOfferDialog?.let { requestId ->
            AlertDialog(
                onDismissRequest = { showPartOfferDialog = null },
                containerColor = MeetColors.backgroundDeep,
                title = { Text("Oferta de Repuestera", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Oferta completa: tienda, marca, número de parte, condición, precio, costo de entrega, ETA y garantía.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = offerStoreName,
                            onValueChange = { offerStoreName = it },
                            label = { Text("Nombre de repuestera", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        OutlinedTextField(
                            value = offerBrand,
                            onValueChange = { offerBrand = it },
                            label = { Text("Marca del repuesto", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        OutlinedTextField(
                            value = offerPartNumber,
                            onValueChange = { offerPartNumber = it },
                            label = { Text("Número de parte", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("NEW" to "Nuevo", "USED" to "Usado", "REFURBISHED" to "Reconstruido").forEach { (value, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (offerCondition == value) MeetColors.neonGreen.copy(alpha = 0.14f) else MeetColors.backgroundDark)
                                        .border(1.dp, if (offerCondition == value) MeetColors.neonGreen else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .clickable { offerCondition = value }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (offerCondition == value) Color.White else MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = offerPrice,
                                onValueChange = { offerPrice = it },
                                label = { Text("Precio ¢", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                            OutlinedTextField(
                                value = offerDeliveryFee,
                                onValueChange = { offerDeliveryFee = it },
                                label = { Text("Envío ¢", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = offerEta,
                                onValueChange = { offerEta = it },
                                label = { Text("ETA min", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                            OutlinedTextField(
                                value = offerWarranty,
                                onValueChange = { offerWarranty = it },
                                label = { Text("Garantía días", color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                            )
                        }
                        OutlinedTextField(
                            value = offerMessage,
                            onValueChange = { offerMessage = it },
                            label = { Text("Mensaje / compatibilidad", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.neonGreen)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val price = offerPrice.toDoubleOrNull() ?: 0.0
                            if (offerStoreName.isNotBlank() && price > 0.0) {
                                obdViewModel.placePartOffer(
                                    partRequestId = requestId,
                                    storeName = offerStoreName,
                                    brand = offerBrand,
                                    partNumber = offerPartNumber,
                                    condition = offerCondition,
                                    price = price,
                                    deliveryFee = offerDeliveryFee.toDoubleOrNull() ?: 0.0,
                                    etaMinutes = offerEta.toIntOrNull() ?: 40,
                                    warrantyDays = offerWarranty.toIntOrNull() ?: 30,
                                    message = offerMessage
                                )
                                showPartOfferDialog = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                    ) {
                        Text("ENVIAR OFERTA", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPartOfferDialog = null }) {
                        Text("CANCELAR", color = MeetColors.textSecondary)
                    }
                }
            )
        }

        // ── VIP Service Contract Confirmation Dialog ──
        if (showAcceptConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showAcceptConfirmDialog = false },
                containerColor = MeetColors.backgroundDeep,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedNeonIcon(Icons.Default.Star, "VIP", tint = Color(0xFFFFB300), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Contratación de Servicio VIP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Por favor confirma los detalles de la oferta antes de formalizar la contratación:",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeetColors.cardBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Proveedor:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(pendingAcceptShopName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Costo Acordado:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text("¢${String.format("%,.0f", pendingAcceptPrice)}", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Tiempo Estimado:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text("${pendingAcceptHours} horas", color = Color.White, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Garantía de Trabajo:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text("${pendingAcceptWarranty} días de cobertura", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (pendingAcceptMessage.isNotBlank()) {
                                Column {
                                    Text("Mensaje del Técnico:", color = MeetColors.textMuted, fontSize = 11.sp)
                                    Text(pendingAcceptMessage, color = MeetColors.textSecondary, fontSize = 11.sp, style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                                }
                            }
                        }

                        // Idempotency / Exclusivity info box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeetColors.cyberCyan.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "🔒 Asignación Exclusiva: Al confirmar, este caso quedará cerrado para otros talleres. Solo tú y el mecánico verán este contrato.",
                                color = MeetColors.cyberCyan,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val reqId = pendingAcceptRequestId
                            val bidId = pendingAcceptBidId
                            if (reqId != null && bidId != null) {
                                obdViewModel.acceptBid(reqId, bidId, context)
                            }
                            showAcceptConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                    ) {
                        Text("AUTORIZAR CONTRATACIÓN VIP", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAcceptConfirmDialog = false }) {
                        Text("CANCELAR", color = MeetColors.textSecondary)
                    }
                }
            )
        }

        // ── VIP Part Purchase Confirmation Dialog ──
        if (showPartConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showPartConfirmDialog = false },
                containerColor = MeetColors.backgroundDeep,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedNeonIcon(Icons.Default.Warning, "Compra", tint = MeetColors.cyberCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirmar Compra de Repuesto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Por favor confirma la orden de compra para esta refacción compatible:",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeetColors.cardBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Repuesto:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(pendingPartName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Proveedor:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(pendingPartStoreName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Marca / Fabricante:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text(pendingPartBrand, color = Color.White, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Precio Total (Envío Inc.):", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text("¢${String.format("%,.0f", pendingPartPrice)}", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("ETA de Envío:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text("${pendingPartEta} minutos aprox.", color = Color.White, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Garantía Tienda:", color = MeetColors.textMuted, fontSize = 11.sp)
                                Text("${pendingPartWarranty} días de garantía", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeetColors.neonGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "📦 Compra Protegida: El pedido será despachado de inmediato. Podrás llamar al repartidor y abrir Waze para guiar su llegada.",
                                color = MeetColors.neonGreen,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val reqId = pendingPartRequestId
                            val offerId = pendingPartOfferId
                            if (reqId != null && offerId != null) {
                                obdViewModel.acceptPartOffer(reqId, offerId, context)
                            }
                            showPartConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan)
                    ) {
                        Text("AUTORIZAR COMPRA VIP", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPartConfirmDialog = false }) {
                        Text("CANCELAR", color = MeetColors.textSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun MobilitySafetyBridgeCard(
    vehicleLabel: String?,
    activeDtcCodes: List<String>,
    onOpenRide: () -> Unit,
    onOpenGarage: () -> Unit,
) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.electricBlue,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedNeonIcon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MeetColors.electricBlue,
                )
                Column {
                    Text(
                        "MOVILIDAD + CONFIANZA MECÁNICA",
                        color = MeetColors.electricBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                    )
                    Text(
                        vehicleLabel ?: "No hay vehículo seleccionado",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }
            Text(
                text = when {
                    vehicleLabel == null ->
                        "Selecciona un vehículo para preparar DTC, mantenimiento y evidencia antes de compartirlos voluntariamente en un viaje."
                    activeDtcCodes.isEmpty() ->
                        "No hay DTC activos capturados. Esto no certifica que el vehículo sea seguro; confirma con inspección física."
                    else ->
                        "${activeDtcCodes.size} DTC activo(s) capturado(s): ${activeDtcCodes.take(3).joinToString()}. Revísalos antes de ofrecer transporte."
                },
                color = if (activeDtcCodes.isEmpty()) MeetColors.textSecondary else MeetColors.warning,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenGarage,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan),
                ) {
                    Text("GARAGE", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onOpenRide,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                ) {
                    Text("VIAJES", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RepairNetworkWorkflowGuide(
    currentQuery: String,
    onQuickSearch: (String) -> Unit,
    onOpenMechanic: () -> Unit,
    onOpenTowTruck: () -> Unit,
    onOpenParts: () -> Unit,
    onOpenRide: () -> Unit,
    onOpenDekra: () -> Unit,
    onOpenTheoryExam: () -> Unit,
    onOpenUniversalServices: () -> Unit,
    onOpenCommunityCases: () -> Unit
) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.neonGreen,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "SOLICITUD BIEN ARMADA = OFERTAS MEJORES",
                color = MeetColors.neonGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                "Describe síntoma, cuándo ocurre, si el vehículo arranca o se mueve, luces presentes, DTCs activos y si necesitas grúa o visita. Los talleres responden mejor cuando el caso llega triageado.",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionPill("Fuga de aceite", isActive = currentQuery == "fuga de aceite", modifier = Modifier.weight(1f)) { onQuickSearch("fuga de aceite") }
                QuickActionPill("Alternador", isActive = currentQuery == "alternador no carga", modifier = Modifier.weight(1f)) { onQuickSearch("alternador no carga") }
                QuickActionPill("Arranque lento", isActive = currentQuery == "arranque lento", modifier = Modifier.weight(1f)) { onQuickSearch("arranque lento") }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "SERVICIOS DE LA RED",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )

            RepairNetworkQuickActionCard(
                title = "Te llevamos a DEKRA",
                subtitle = "Cita · prechequeo · custodia · traslado · resultado",
                icon = "✓",
                accentColor = Color(0xFF20D5C6),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenDekra,
            )

            RepairNetworkQuickActionCard(
                title = "Academia Examen Teórico",
                subtitle = "Auto + moto 2026 · lecciones · repaso · simulacro",
                icon = "🎓",
                accentColor = Color(0xFFFFC857),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenTheoryExam,
            )

            // Grid of 4 actions (2 rows of 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RepairNetworkQuickActionCard(
                    title = "Red Mecánica",
                    subtitle = "Triage y visitas",
                    icon = "🛠️",
                    accentColor = MeetColors.neonGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenMechanic
                )
                RepairNetworkQuickActionCard(
                    title = "Servicio Grúa",
                    subtitle = "Asistencia vial",
                    icon = "🚛",
                    accentColor = MeetColors.warning,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTowTruck
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RepairNetworkQuickActionCard(
                    title = "Repuestos",
                    subtitle = "Cotizaciones",
                    icon = "🧩",
                    accentColor = MeetColors.cyberCyan,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenParts
                )
                RepairNetworkQuickActionCard(
                    title = "Viajes / Ride",
                    subtitle = "Chofer particular",
                    icon = "🚕",
                    accentColor = MeetColors.electricBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenRide
                )
            }

            // 5th full-width action
            RepairNetworkQuickActionCard(
                title = "Servicios Elysium",
                subtitle = "A domicilio · remoto · híbrido · ofertas y contratos",
                icon = "✦",
                accentColor = Color(0xFFC85CFF),
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenUniversalServices
            )

            RepairNetworkQuickActionCard(
                title = "Casos Comunitarios",
                subtitle = "StackOverflow de problemas mecánicos",
                icon = "📚",
                accentColor = MeetColors.hotMagenta,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenCommunityCases
            )
        }
    }
}

@Composable
private fun QuickActionPill(text: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isActive) MeetColors.neonGreen.copy(alpha = 0.16f) else MeetColors.backgroundDark)
            .border(1.dp, if (isActive) MeetColors.neonGreen else MeetColors.borderSubtle, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isActive) Color.White else MeetColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RepairKnowledgePanel(bundle: MechanicalKnowledgeBundle) {
    val cards = buildList {
        bundle.symptomGuides.firstOrNull()?.let {
            add(Triple("GUÍA POR SÍNTOMA", it.title, firstPayloadHint(it.payloadJson, listOf("first_checks", "diagnostic_tree"))))
        }
        bundle.procedures.firstOrNull()?.let {
            add(Triple("PROCEDIMIENTO", it.title, firstPayloadHint(it.payloadJson, listOf("removal_steps", "installation_steps", "post_install_tests"))))
        }
        bundle.rebuildGuides.firstOrNull()?.let {
            add(Triple("RECONSTRUCCIÓN", it.componentId.replace('_', ' ').uppercase(), firstPayloadHint(it.payloadJson, listOf("bench_tests", "failure_signatures", "rebuild_steps"))))
        }
        bundle.trenchKnowledge.firstOrNull()?.let {
            add(Triple("TACTICA DE TALLER", it.title, firstPayloadHint(it.payloadJson, listOf("escalation_ladder", "thread_repair_options"))))
        }
        bundle.chemicals.firstOrNull()?.let {
            add(Triple("QUÍMICA AUTOMOTRIZ", it.name, firstPayloadHint(it.payloadJson, listOf("use_cases", "safe_materials", "unsafe_materials"))))
        }
        bundle.matrixLinks.firstOrNull()?.let {
            add(Triple("MATRIZ DTC + COMPONENTE", it.componentName ?: (it.dtcCode ?: "Sin componente"), firstMatrixHint(it.layerDiagnosticsJson, it.layerTrenchKnowledgeJson)))
        }
    }

    if (cards.isEmpty()) return

    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.cyberCyan,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "INTELIGENCIA OFFLINE Elysium Vanguard",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            cards.forEach { (label, title, hint) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeetColors.backgroundDark, RoundedCornerShape(10.dp))
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(label, color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (hint.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(hint, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

private fun suggestPartNameForDtc(dtcCode: String?, problem: String): String {
    val problemText = problem.lowercase()
    val dtcSuggestion = dtcCode
        ?.takeIf { it.isNotBlank() }
        ?.let { code ->
            PartSuggestionEngine.suggestParts(
                PartSuggestionInput(
                    source = SuggestionSource.DTC,
                    dtcCodes = listOf(code)
                )
            ).firstOrNull { !it.partName.startsWith("Diagnóstico de") }
        }
    if (dtcSuggestion != null) return dtcSuggestion.partName

    return when {
        dtcCode?.startsWith("P030") == true || problemText.contains("buj") || problemText.contains("misfire") ->
            "Bujía / bobina de encendido compatible"
        dtcCode in setOf("P0171", "P0174") || problemText.contains("mezcla") ->
            "Manguera de vacío / ducto de admisión / sensor de carga según equipamiento"
        dtcCode in setOf("P0420", "P0430") || problemText.contains("catalizador") ->
            "Sensor de oxígeno o catalizador compatible"
        problemText.contains("freno") || problemText.contains("pastilla") ->
            "Pastillas de freno compatibles"
        problemText.contains("alternador") || problemText.contains("bater") ->
            "Alternador / batería compatible"
        else -> "Repuesto requerido según diagnóstico"
    }
}

private fun firstMatrixHint(diagnosticsJson: String, trenchJson: String): String {
    val diag = firstPayloadHint(diagnosticsJson, listOf("diagnostic_steps", "confirmation_tests", "related_symptom_guides"))
    if (diag.isNotBlank()) return diag
    return firstPayloadHint(trenchJson, listOf("common_shop_notes", "related_trench_knowledge"))
}

private fun firstPayloadHint(payloadJson: String, keys: List<String>): String {
    return runCatching {
        val obj = JSONObject(payloadJson)
        keys.firstNotNullOfOrNull { key ->
            when (val value = obj.opt(key)) {
                is JSONArray -> value.optString(0).takeIf { it.isNotBlank() }
                is String -> value.takeIf { it.isNotBlank() }
                is JSONObject -> value.keys().asSequence().firstOrNull()?.let { childKey ->
                    value.opt(childKey)?.toString()?.takeIf { it.isNotBlank() }?.let { "$childKey: $it" }
                }
                else -> null
            }
        } ?: ""
    }.getOrDefault("")
}

@Composable
private fun RepairNetworkQuickActionCard(
    title: String,
    subtitle: String,
    icon: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    EliteCard(
        glowColor = accentColor,
        borderColor = accentColor.copy(alpha = 0.25f),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(18.dp),
        enableHolo3D = true,
        onClick = onClick,
        modifier = modifier.height(86.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 22.sp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MeetColors.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
