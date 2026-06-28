package com.elysium369.meet.ui.screens.chat

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.data.local.entities.*
import com.elysium369.meet.ui.FleetChatViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.PhantomSectionHeader
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetChatListScreen(
    navController: NavController,
    viewModel: FleetChatViewModel,
    businessId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Active selected business ID
    LaunchedEffect(businessId) {
        if (businessId.isNotEmpty()) {
            viewModel.selectBusiness(businessId)
        }
    }

    val selectedBizId by viewModel.selectedBusinessId.collectAsState()
    val businessProfiles by viewModel.businessProfiles.collectAsState(initial = emptyList())
    val fleets by viewModel.fleetsForActiveBusiness.collectAsState(initial = emptyList())
    val members by viewModel.membersForActiveBusiness.collectAsState(initial = emptyList())
    val vehicles by viewModel.vehiclesForActiveBusiness.collectAsState(initial = emptyList())
    val recentChats by viewModel.recentChats.collectAsState(initial = emptyList())

    // Tabs state
    var selectedTab by remember { mutableStateOf(0) }

    // Dialog & Form states
    var showCreateBusinessDialog by remember { mutableStateOf(false) }
    var showCreateFleetDialog by remember { mutableStateOf(false) }
    var showAssignVehicleDialog by remember { mutableStateOf<VehicleEntity?>(null) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            EliteTopAppBar(
                title = "Ecosistema de Flotas",
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs navigation
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MeetColors.backgroundDark,
                contentColor = MeetColors.electricBlue,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MeetColors.electricBlue
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Panel", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Vehículos", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Mensajería / DVIR", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> PanelTabContent(
                    fleets = fleets,
                    businessProfiles = businessProfiles,
                    selectedBizId = selectedBizId,
                    onSelectBusiness = { viewModel.selectBusiness(it) },
                    onCreateBusiness = { showCreateBusinessDialog = true },
                    onCreateFleet = { showCreateFleetDialog = true },
                    onJoinFleet = { code ->
                        viewModel.joinFleetByCode(code) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onCopyCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "Código copiado: $code", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> VehiclesTabContent(
                    vehicles = vehicles,
                    members = members,
                    fleets = fleets,
                    onAssignClick = { showAssignVehicleDialog = it },
                    onDiagnoseClick = { code ->
                        navController.navigate("ai/$code")
                    }
                )
                2 -> MessagingTabContent(
                    recentChats = recentChats,
                    vehicles = vehicles,
                    viewModel = viewModel,
                    navController = navController,
                    onSubmitDvir = { vId, b, l, t, f, bat ->
                        viewModel.submitDvirReport(vId, b, l, t, f, bat) {
                            Toast.makeText(context, "Reporte DVIR enviado con éxito al panel", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // CREATE BUSINESS DIALOG
    if (showCreateBusinessDialog) {
        var bizName by remember { mutableStateOf("") }
        var taxId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateBusinessDialog = false },
            title = { Text("Registrar Empresa de Flota", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Configura un perfil administrativo de flotillas:", color = MeetColors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = bizName,
                        onValueChange = { bizName = it },
                        label = { Text("Nombre de la Empresa") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.electricBlue, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = taxId,
                        onValueChange = { taxId = it },
                        label = { Text("Identificación Fiscal / RFC (Opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.electricBlue, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                EliteButton(
                    text = "Registrar",
                    onClick = {
                        if (bizName.isNotBlank()) {
                            viewModel.createBusinessProfile(bizName, taxId.takeIf { it.isNotBlank() })
                            showCreateBusinessDialog = false
                            Toast.makeText(context, "Empresa registrada con éxito", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateBusinessDialog = false }) {
                    Text("Cancelar", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.backgroundDark
        )
    }

    // CREATE FLEET DIALOG
    if (showCreateFleetDialog) {
        var fleetName by remember { mutableStateOf("") }
        var fleetDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFleetDialog = false },
            title = { Text("Crear Nueva Flota", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Las flotas dividen a tu empresa en sub-rutas o grupos específicos de trabajo:", color = MeetColors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = fleetName,
                        onValueChange = { fleetName = it },
                        label = { Text("Nombre de la Flota") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.electricBlue, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = fleetDesc,
                        onValueChange = { fleetDesc = it },
                        label = { Text("Descripción del Trabajo / Ruta") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.electricBlue, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                EliteButton(
                    text = "Crear",
                    onClick = {
                        if (fleetName.isNotBlank()) {
                            viewModel.createFleet(fleetName, fleetDesc.takeIf { it.isNotBlank() })
                            showCreateFleetDialog = false
                            Toast.makeText(context, "Flota creada exitosamente", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showCreateFleetDialog = false }) {
                    Text("Cancelar", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.backgroundDark
        )
    }

    // ASSIGN VEHICLE DIALOG
    if (showAssignVehicleDialog != null) {
        val vehicle = showAssignVehicleDialog!!
        var selectedDriverId by remember { mutableStateOf<String?>(vehicle.assignedDriverId) }
        var selectedFleetId by remember { mutableStateOf<String?>(vehicle.fleetId) }
        
        AlertDialog(
            onDismissRequest = { showAssignVehicleDialog = null },
            title = { Text("Asignar ${vehicle.make} ${vehicle.model}", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Asigna un conductor responsable y agrégalo a una flota operativa:", color = MeetColors.textSecondary, fontSize = 12.sp)
                    
                    // Driver Selector
                    Text("Conductor Responsable:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        members.forEach { driver ->
                            val isSelected = selectedDriverId == driver.userId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MeetColors.electricBlue else MeetColors.backgroundDeep)
                                    .clickable { selectedDriverId = if (isSelected) null else driver.userId }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    driver.userId,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Fleet Selector
                    Text("Flota Operativa:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        fleets.forEach { fleet ->
                            val isSelected = selectedFleetId == fleet.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MeetColors.electricBlue.copy(alpha = 0.2f) else MeetColors.backgroundDeep)
                                    .clickable { selectedFleetId = if (isSelected) null else fleet.id }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedFleetId = if (isSelected) null else fleet.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = MeetColors.electricBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(fleet.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(fleet.description ?: "", color = MeetColors.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                EliteButton(
                    text = "Guardar Asignación",
                    onClick = {
                        viewModel.assignVehicleToDriverAndFleet(vehicle.id, selectedDriverId, selectedFleetId)
                        showAssignVehicleDialog = null
                        Toast.makeText(context, "Asignación actualizada con éxito", Toast.LENGTH_SHORT).show()
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAssignVehicleDialog = null }) {
                    Text("Cancelar", color = MeetColors.textSecondary)
                }
            },
            containerColor = MeetColors.backgroundDark
        )
    }
}

// ══════════════════════════════════════════════════════
// TAB CONTENT: PANEL / CONTROL
// ══════════════════════════════════════════════════════
@Composable
fun PanelTabContent(
    fleets: List<FleetEntity>,
    businessProfiles: List<BusinessProfileEntity>,
    selectedBizId: String?,
    onSelectBusiness: (String) -> Unit,
    onCreateBusiness: () -> Unit,
    onCreateFleet: () -> Unit,
    onJoinFleet: (String) -> Unit,
    onCopyCode: (String) -> Unit
) {
    var inviteCodeInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Business Profile Selection
        item {
            EliteCard(
                glowColor = MeetColors.electricBlue.copy(alpha = 0.1f),
                backgroundColor = MeetColors.cardBackground
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Perfil Corporativo / Empresa", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (businessProfiles.isEmpty()) {
                        Text(
                            "No tienes empresas de flotas configuradas. Registra una para comenzar a administrar choferes y vehículos.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        EliteButton(
                            text = "Registrar Empresa",
                            onClick = onCreateBusiness,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val activeBiz = businessProfiles.find { it.id == selectedBizId }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(activeBiz?.name ?: "Seleccione Empresa", color = MeetColors.electricBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("RFC: ${activeBiz?.taxId ?: "No Registrado"}", color = MeetColors.textSecondary, fontSize = 11.sp)
                                Text("Plan: ${activeBiz?.planType ?: "Básico"}", color = MeetColors.cyberCyan, fontSize = 11.sp)
                            }
                            IconButton(onClick = onCreateBusiness) {
                                AnimatedNeonIcon(Icons.Default.AddBusiness, contentDescription = "Añadir negocio", tint = MeetColors.electricBlue)
                            }
                        }
                    }
                }
            }
        }

        // Fleets List & Creation
        if (selectedBizId != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhantomSectionHeader(label = "FLOTAS ACTIVAS (${fleets.size})", accentColor = MeetColors.electricBlue)
                    IconButton(onClick = onCreateFleet) {
                        AnimatedNeonIcon(Icons.Default.Add, contentDescription = "Añadir Flota", tint = MeetColors.electricBlue)
                    }
                }
            }

            if (fleets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No has creado sub-flotas en esta empresa.", color = MeetColors.textSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                items(fleets) { fleet ->
                    EliteCard(
                        glowColor = MeetColors.cyberCyan.copy(alpha = 0.1f),
                        backgroundColor = MeetColors.cardBackground
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fleet.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(fleet.description ?: "Sin descripción", color = MeetColors.textSecondary, fontSize = 11.sp)
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Código de Invitación", color = MeetColors.textSecondary, fontSize = 9.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MeetColors.backgroundDeep)
                                        .clickable { onCopyCode(fleet.inviteCode) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        fleet.inviteCode,
                                        color = MeetColors.cyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AnimatedNeonIcon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MeetColors.cyberCyan, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Driver Section: Join Fleet
        item {
            PhantomSectionHeader(label = "SECCIÓN PARA CONDUCTORES", accentColor = MeetColors.cyberCyan)
            Spacer(modifier = Modifier.height(4.dp))
            EliteCard(
                glowColor = MeetColors.cyberCyan.copy(alpha = 0.1f),
                backgroundColor = MeetColors.cardBackground
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("¿Eres conductor? Únete a una Flota", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Pide el código de invitación a tu administrador para vincular tu cuenta.", color = MeetColors.textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inviteCodeInput,
                            onValueChange = { inviteCodeInput = it },
                            placeholder = { Text("Ej: EVG-METRO", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.electricBlue,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Button(
                            onClick = {
                                if (inviteCodeInput.isNotBlank()) {
                                    onJoinFleet(inviteCodeInput.trim().uppercase())
                                    inviteCodeInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan)
                        ) {
                            Text("Unirse", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// TAB CONTENT: VEHICLES
// ══════════════════════════════════════════════════════
@Composable
fun VehiclesTabContent(
    vehicles: List<VehicleEntity>,
    members: List<FleetMemberEntity>,
    fleets: List<FleetEntity>,
    onAssignClick: (VehicleEntity) -> Unit,
    onDiagnoseClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (vehicles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay vehículos registrados. Presiona el botón superior de refresco para generar los datos de prueba.", color = MeetColors.textSecondary, fontSize = 13.sp)
                }
            }
        } else {
            items(vehicles) { vehicle ->
                val assignedDriver = members.find { it.userId == vehicle.assignedDriverId }
                val assignedFleet = fleets.find { it.id == vehicle.fleetId }

                // Simulated active DTC codes for presentation (specifically Kenworth has engine faults)
                val hasDtcFaults = vehicle.make == "Kenworth"
                val faultCodes = if (hasDtcFaults) listOf("P0301", "P0420") else emptyList()

                EliteCard(
                    glowColor = if (hasDtcFaults) MeetColors.error.copy(alpha = 0.2f) else MeetColors.electricBlue.copy(alpha = 0.1f),
                    backgroundColor = MeetColors.cardBackground
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text("Placas: ${vehicle.plate} | Odiómetro: ${vehicle.odometerKm} km", color = MeetColors.textSecondary, fontSize = 11.sp)
                            }
                            
                            IconButton(onClick = { onAssignClick(vehicle) }) {
                                AnimatedNeonIcon(Icons.Default.AssignmentInd, contentDescription = "Asignar", tint = MeetColors.electricBlue)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MeetColors.backgroundDeep)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Conductor Asignado", color = MeetColors.textSecondary, fontSize = 10.sp)
                                Text(assignedDriver?.userId ?: "Sin asignar ⚠️", color = if (assignedDriver != null) Color.White else MeetColors.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Flota Operativa", color = MeetColors.textSecondary, fontSize = 10.sp)
                                Text(assignedFleet?.name ?: "Sin flota ⚠️", color = if (assignedFleet != null) MeetColors.cyberCyan else MeetColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (hasDtcFaults) {
                            Spacer(modifier = Modifier.height(12.dp))
                            EliteCard(
                                glowColor = MeetColors.error.copy(alpha = 0.3f),
                                backgroundColor = MeetColors.error.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("⚠️ CÓDIGOS DE FALLA ACTIVOS", color = MeetColors.error, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("P0301 (Falla Cilindro 1) & P0420 (Eficiencia Catalizador)", color = Color.White, fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { onDiagnoseClick("P0301") },
                                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Consultar IA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// TAB CONTENT: MESSAGING
// ══════════════════════════════════════════════════════
@Composable
fun MessagingTabContent(
    recentChats: List<ChatMessageEntity>,
    vehicles: List<VehicleEntity>,
    viewModel: FleetChatViewModel,
    navController: NavController,
    onSubmitDvir: (String, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search conversations bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar chofer o reporte...", color = MeetColors.textSecondary) },
            leadingIcon = { AnimatedNeonIcon(Icons.Default.Search, contentDescription = null, tint = MeetColors.textSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MeetColors.electricBlue,
                unfocusedBorderColor = MeetColors.backgroundDark,
                focusedContainerColor = MeetColors.backgroundDeep,
                unfocusedContainerColor = MeetColors.backgroundDeep,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhantomSectionHeader(label = "CHATS ACTIVOS", accentColor = MeetColors.electricBlue)
            TextButton(onClick = { showNewChatDialog = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedNeonIcon(Icons.Default.Email, contentDescription = null, tint = MeetColors.electricBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuevo Chat", color = MeetColors.electricBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (recentChats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay chats iniciados. Selecciona un chofer.", color = MeetColors.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    EliteButton(
                        text = "Iniciar Chat",
                        onClick = { showNewChatDialog = true }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                val filteredChats = recentChats.filter {
                    searchQuery.isEmpty() || (it.messageText?.contains(searchQuery, ignoreCase = true) == true)
                }

                items(filteredChats) { chat ->
                    val partnerId = if (chat.senderId == viewModel.currentUserId) chat.receiverId else chat.senderId
                    ChatRowItem(
                        message = chat,
                        partnerId = partnerId,
                        onClick = {
                            val dummyPartner = FleetMemberEntity(
                                id = partnerId,
                                businessId = chat.businessId,
                                userId = partnerId,
                                role = "CONDUCTOR",
                                email = "${partnerId}@meet.com",
                                inviteStatus = "ACCEPTED",
                                joinedAt = chat.timestamp
                            )
                            viewModel.selectPartner(dummyPartner)
                            navController.navigate("fleet_chat_detail")
                        }
                    )
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewChatDialog(
            viewModel = viewModel,
            onDismiss = { showNewChatDialog = false },
            onSelectPartner = { partner ->
                viewModel.selectPartner(partner)
                showNewChatDialog = false
                navController.navigate("fleet_chat_detail")
            }
        )
    }
}

// ══════════════════════════════════════════════════════

@Composable
fun ChatRowItem(
    message: ChatMessageEntity,
    partnerId: String,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }
    
    EliteCard(
        glowColor = MeetColors.electricBlue.copy(alpha = 0.05f),
        backgroundColor = MeetColors.cardBackground,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(MeetColors.electricBlue, MeetColors.cyberCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = partnerId.take(2).uppercase(),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = partnerId,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = formattedTime,
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (message.messageType) {
                        "DTC_ALERT" -> "⚠️ Reporte de Diagnóstico / Inspección"
                        "AUDIO" -> "🎙️ Nota de voz"
                        "FILE" -> "📁 Archivo adjunto"
                        else -> message.messageText ?: ""
                    },
                    color = MeetColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun NewChatDialog(
    viewModel: FleetChatViewModel,
    onDismiss: () -> Unit,
    onSelectPartner: (FleetMemberEntity) -> Unit
) {
    val members by viewModel.membersForActiveBusiness.collectAsState(initial = emptyList())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Iniciar Conversación", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Selecciona un miembro de la flota para chatear:", color = MeetColors.textSecondary, fontSize = 12.sp)
                
                if (members.isEmpty()) {
                    Text("No hay conductores o miembros registrados en esta empresa.", color = MeetColors.textSecondary, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 250.dp)
                    ) {
                        items(members) { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MeetColors.backgroundDeep)
                                    .clickable { onSelectPartner(member) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MeetColors.electricBlue.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedNeonIcon(Icons.Default.Person, contentDescription = null, tint = MeetColors.electricBlue, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(member.userId, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(member.email, color = MeetColors.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = MeetColors.electricBlue)
            }
        },
        containerColor = MeetColors.backgroundDark
    )
}
