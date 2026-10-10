package com.elysium369.meet.ui.screens.provider

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elysium369.meet.data.local.entities.ProviderProfileEntity
import com.elysium369.meet.provider.domain.models.*
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * ══════════════════════════════════════════════════════════════════════
 *  P R O V I D E R   S E R V I C E   &   M A T E R I A L S   C O N F I G
 *  ──────────────────────────────────────────────────────────────
 *  Permite a cualquier oferente o taller definir con exactitud técnica:
 *  - Categoría de especialidad
 *  - Fórmulas de cobro matemáticas (₡/hora, base, km, recargo)
 *  - Políticas de suministro de materiales y repuestos
 *  - Sub-servicios específicos con tiempos y materiales
 *  - Equipamiento certificado y días de garantía
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderServiceCatalogConfigScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
) {
    val profiles by viewModel.userProviderProfiles.collectAsState()
    val principal by viewModel.activePrincipal.collectAsState()
    val actorId = principal.id.takeIf { it.isNotBlank() } ?: viewModel.currentUserId
    val ownProfiles = profiles.filter {
        (it.userId == actorId || (actorId != null && it.userId == principal.id) || (viewModel.currentUserId != null && it.userId == viewModel.currentUserId)) && it.isActive
    }
    var selectedProfileId by rememberSaveable(actorId) { mutableStateOf<String?>(null) }
    var explicitCategory by rememberSaveable(actorId, selectedProfileId) { mutableStateOf<ProviderDomainCategory?>(null) }
    var waitingForProfiles by remember(actorId) { mutableStateOf(true) }
    LaunchedEffect(actorId) {
        if (actorId != null) viewModel.refreshProviderRoles()
        kotlinx.coroutines.delay(5_000L)
        waitingForProfiles = false
    }
    val existingProfile = ownProfiles.firstOrNull { it.profileId == selectedProfileId }
        ?: ownProfiles.singleOrNull()
    val storedCategory = existingProfile?.let { profile ->
        val storedId = runCatching { org.json.JSONObject(profile.specialties).optString("domainCategory") }.getOrNull()
        ProviderCatalogEditPolicy.category(storedId, profile.providerType)
    }
    val initialCategory = explicitCategory ?: storedCategory
    if (existingProfile == null || initialCategory == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Configurar mis servicios") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                )
            },
            containerColor = MeetColors.backgroundDeep,
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        when {
                            actorId == null -> "Inicia sesión para configurar tu perfil de proveedor."
                            ownProfiles.isEmpty() && waitingForProfiles -> "Cargando tus perfiles de proveedor…"
                            ownProfiles.isEmpty() -> "No hay un perfil activo disponible. Registra tu perfil desde Ajustes antes de configurar el catálogo."
                            existingProfile == null -> "Selecciona el perfil cuyo catálogo deseas editar."
                            else -> "Selecciona la categoría de este perfil. No hay una categoría de catálogo registrada."
                        }, color = MeetColors.textPrimary,
                    )
                }
                if (existingProfile == null) {
                    items(ownProfiles, key = { it.profileId }) { profile ->
                        OutlinedButton(onClick = { selectedProfileId = profile.profileId }, modifier = Modifier.fillMaxWidth()) {
                            Text("${profile.businessName} · ${profile.providerType}")
                        }
                    }
                } else {
                    items(ProviderDomainCategory.entries) { category ->
                        OutlinedButton(onClick = { explicitCategory = category }, modifier = Modifier.fillMaxWidth()) { Text(category.title) }
                    }
                }
                if (actorId != null && ownProfiles.isEmpty()) item {
                    OutlinedButton(onClick = { viewModel.refreshProviderRoles() }) { Text("Actualizar perfiles") }
                }
            }
        }
        return
    }
    key(actorId, existingProfile.profileId) {
        ProviderServiceCatalogEditor(viewModel, existingProfile, initialCategory, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderServiceCatalogEditor(
    viewModel: ObdViewModel,
    existingProfile: ProviderProfileEntity,
    initialCategory: ProviderDomainCategory,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable(existingProfile.profileId) { mutableStateOf(initialCategory) }
    var profileData by remember(existingProfile.profileId) {
        mutableStateOf(
            if (runCatching { org.json.JSONObject(existingProfile.specialties).has("domainCategory") }.getOrDefault(false)) {
                ProviderServiceProfileData.fromJsonString(existingProfile.specialties).copy(domainCategory = initialCategory)
            } else ProviderServiceProfileData.defaultTemplateForCategory(initialCategory).copy(
                certifiedEquipment = emptyList(), operatingRadiusKm = existingProfile.radiusKm,
            )
        )
    }
    val businessName = existingProfile.businessName
    val ownerName = existingProfile.ownerName
    val phone = existingProfile.phone

    // Campos editables
    var hourlyRateStr by rememberSaveable { mutableStateOf(profileData.hourlyLaborRateCrc.toString()) }
    var baseFeeStr by rememberSaveable { mutableStateOf(profileData.baseDiagnosticFeeCrc.toString()) }
    var perKmFeeStr by rememberSaveable { mutableStateOf(profileData.ratePerKmCrc.toString()) }
    var warrantyDaysStr by rememberSaveable { mutableStateOf(profileData.warrantyDays.toString()) }
    var warrantyKmStr by rememberSaveable { mutableStateOf(profileData.warrantyKm.toString()) }
    var selectedMaterialPolicy by remember { mutableStateOf(profileData.materialPolicy) }

    var showNewServiceDialog by remember { mutableStateOf(false) }
    var showNewEquipmentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CONFIGURAR MIS SERVICIOS",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Tarifas matemáticas · Materiales · Catálogo de especialidades",
                            fontSize = 11.sp,
                            color = MeetColors.neonGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        enabled = !saving,
                        onClick = {
                            val rates = ProviderCatalogEditPolicy.rates(hourlyRateStr, baseFeeStr, perKmFeeStr, warrantyDaysStr, warrantyKmStr)
                            if (rates == null) {
                                saveMessage = "Ingresa tarifas y garantías válidas, sin valores negativos."
                                return@Button
                            }
                            if (viewModel.currentUserId != existingProfile.userId) {
                                saveMessage = "La sesión cambió. Vuelve a abrir tu perfil antes de guardar."
                                return@Button
                            }
                            val updatedData = profileData.copy(
                                domainCategory = selectedCategory,
                                hourlyLaborRateCrc = rates.hourly,
                                baseDiagnosticFeeCrc = rates.base,
                                ratePerKmCrc = rates.perKm,
                                materialPolicy = selectedMaterialPolicy,
                                warrantyDays = rates.warrantyDays,
                                warrantyKm = rates.warrantyKm,
                            )
                            val draftJson = updatedData.toJsonString()
                            saving = true
                            saveMessage = "Guardando el catálogo de este perfil…"
                            scope.launch {
                                try {
                                    viewModel.updateProviderProfileSpecialties(existingProfile.profileId, draftJson)
                                    val saved = withTimeoutOrNull(6_000L) {
                                        viewModel.userProviderProfiles.first { rows ->
                                            rows.any { row ->
                                                row.profileId == existingProfile.profileId &&
                                                    row.isActive && row.specialties == draftJson
                                            }
                                        }
                                    }
                                    if (saved != null) {
                                        saveMessage = "Catálogo guardado con éxito. Perfil operativo listo para cotizar."
                                        Toast.makeText(context, saveMessage, Toast.LENGTH_SHORT).show()
                                    } else {
                                        saveMessage = "Perfil guardado localmente en este dispositivo."
                                        Toast.makeText(context, saveMessage, Toast.LENGTH_SHORT).show()
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    saveMessage = "No se pudo confirmar el guardado del catálogo."
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Save, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (saving) "Guardando…" else "Guardar", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep)
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF071424), Color(0xFF09031C)))),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Perfil: ${existingProfile.businessName} · ${existingProfile.providerType}", color = MeetColors.textSecondary)
                saveMessage?.let { Text(it, color = MeetColors.textPrimary) }
            }
            // ── SECCIÓN 1: Selección de Categoría Técnica ──
            item {
                Text(
                    text = "1. RAMA TÉCNICA / COMERCIAL",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProviderDomainCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                val newTemplate = ProviderServiceProfileData.defaultTemplateForCategory(cat).copy(
                                    certifiedEquipment = profileData.certifiedEquipment,
                                    operatingRadiusKm = profileData.operatingRadiusKm,
                                    walletBalanceCrc = profileData.walletBalanceCrc,
                                    platformCommissionBps = profileData.platformCommissionBps,
                                    walletTransactions = profileData.walletTransactions,
                                )
                                profileData = newTemplate
                                hourlyRateStr = newTemplate.hourlyLaborRateCrc.toString()
                                baseFeeStr = newTemplate.baseDiagnosticFeeCrc.toString()
                                perKmFeeStr = newTemplate.ratePerKmCrc.toString()
                                warrantyDaysStr = newTemplate.warrantyDays.toString()
                                warrantyKmStr = newTemplate.warrantyKm.toString()
                                selectedMaterialPolicy = newTemplate.materialPolicy
                            },
                            label = {
                                Text("${cat.icon} ${cat.title}", fontSize = 11.sp, fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MeetColors.cyberCyan.copy(alpha = 0.25f),
                                selectedLabelColor = MeetColors.cyberCyan,
                                containerColor = Color.White.copy(alpha = 0.05f),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            // ── SECCIÓN 2: Datos de Identidad Comercial ──
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Identidad Comercial", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Nombre del Negocio o Taller") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MeetColors.cyberCyan
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ownerName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Responsable Técnico") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.cyberCyan
                                )
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Teléfono / WhatsApp") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.cyberCyan
                                )
                            )
                        }
                    }
                }
            }

            // ── SECCIÓN 3: Estructura Matemática de Tarifas ──
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🧮 Estructura Matemática de Tarifas (₡ CRC)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            text = "Estas tarifas se utilizan para calcular cotizaciones automáticas transparentes para los clientes.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = hourlyRateStr,
                                onValueChange = { hourlyRateStr = it },
                                label = { Text("Mano de Obra ₡/Hora") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.neonGreen,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.neonGreen
                                )
                            )
                            OutlinedTextField(
                                value = baseFeeStr,
                                onValueChange = { baseFeeStr = it },
                                label = { Text("Base / Diagnóstico ₡") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.cyberCyan,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.cyberCyan
                                )
                            )
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = perKmFeeStr,
                                onValueChange = { perKmFeeStr = it },
                                label = { Text("Desplazamiento ₡/Km") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.cyberCyan
                                )
                            )
                            OutlinedTextField(
                                value = warrantyDaysStr,
                                onValueChange = { warrantyDaysStr = it },
                                label = { Text("Garantía (Días)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.cyberCyan
                                )
                            )
                        }
                    }
                }
            }

            // ── SECCIÓN 4: Política de Suministro de Materiales y Repuestos ──
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📦 Política de Materiales y Repuestos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        MaterialSupplyPolicy.entries.forEach { policy ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMaterialPolicy = policy }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedMaterialPolicy == policy,
                                    onClick = { selectedMaterialPolicy = policy },
                                    colors = RadioButtonDefaults.colors(selectedColor = MeetColors.neonGreen)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(policy.description, color = Color.White, fontSize = 12.sp, fontWeight = if (selectedMaterialPolicy == policy) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }

            // ── SECCIÓN 5: Catálogo de Sub-Servicios y Materiales Requeridos ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 SERVICIOS OFRECIDOS & MATERIALES (${profileData.subServices.count { it.isEnabled }})",
                        color = MeetColors.cyberCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    TextButton(onClick = { showNewServiceDialog = true }) {
                        Icon(Icons.Default.Add, null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar Servicio", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(profileData.subServices) { subService ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (subService.isEnabled) Color(0xFF0F1E33) else Color(0xFF0A0F1A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (subService.isEnabled) MeetColors.cyberCyan.copy(alpha = 0.5f) else Color.DarkGray
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = subService.isEnabled,
                            onCheckedChange = { checked ->
                                val updatedList = profileData.subServices.map {
                                    if (it.id == subService.id) it.copy(isEnabled = checked) else it
                                }
                                profileData = profileData.copy(subServices = updatedList)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MeetColors.neonGreen)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(subService.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "₡${"%,d".format(subService.suggestedPriceCrc)}",
                                    color = MeetColors.neonGreen,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                            Text(subService.description, color = Color.Gray, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tiempo est: ${subService.estimatedHours}h", color = MeetColors.cyberCyan, fontSize = 10.sp)
                            }
                            if (subService.typicalMaterials.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Materiales: ${subService.typicalMaterials.joinToString(", ")}",
                                    color = Color(0xFFFFB74D),
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ── SECCIÓN 6: Equipamiento y Herramientas Certificadas ──
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("🛠️ Equipamiento & Herramientas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = { showNewEquipmentDialog = true }) {
                                Icon(Icons.Default.AddCircle, null, tint = MeetColors.cyberCyan)
                            }
                        }
                        if (profileData.certifiedEquipment.isEmpty()) {
                            Text("No has registrado equipamiento técnico aún.", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(profileData.certifiedEquipment) { item ->
                                    Surface(
                                        color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(item, color = Color.White, fontSize = 11.sp)
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Eliminar",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(12.dp).clickable {
                                                    val updatedEquip = profileData.certifiedEquipment.filter { it != item }
                                                    profileData = profileData.copy(certifiedEquipment = updatedEquip)
                                                }
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
    }

    // Modal para agregar nuevo sub-servicio personalizado
    if (showNewServiceDialog) {
        var newServiceName by remember { mutableStateOf("") }
        var newServiceDesc by remember { mutableStateOf("") }
        var newServiceHours by remember { mutableStateOf("1.0") }
        var newServicePrice by remember { mutableStateOf("15000") }
        var newServiceMaterials by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showNewServiceDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeetColors.cardBackground,
                border = BorderStroke(1.dp, MeetColors.cyberCyan)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Agregar Servicio al Catálogo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(
                        value = newServiceName,
                        onValueChange = { newServiceName = it },
                        label = { Text("Nombre del Servicio") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newServiceDesc,
                        onValueChange = { newServiceDesc = it },
                        label = { Text("Descripción del Procedimiento") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newServiceHours,
                            onValueChange = { newServiceHours = it },
                            label = { Text("Horas Est.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newServicePrice,
                            onValueChange = { newServicePrice = it },
                            label = { Text("Precio Sugerido ₡") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = newServiceMaterials,
                        onValueChange = { newServiceMaterials = it },
                        label = { Text("Materiales (separados por coma)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showNewServiceDialog = false }) {
                            Text("Cancelar", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                if (newServiceName.isNotBlank()) {
                                    val matsList = newServiceMaterials.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    val newService = OfferedSubService(
                                        id = UUID.randomUUID().toString(),
                                        name = newServiceName,
                                        description = newServiceDesc,
                                        estimatedHours = newServiceHours.toDoubleOrNull() ?: 1.0,
                                        suggestedPriceCrc = newServicePrice.toLongOrNull() ?: 15000L,
                                        typicalMaterials = matsList,
                                        isEnabled = true
                                    )
                                    profileData = profileData.copy(subServices = profileData.subServices + newService)
                                    showNewServiceDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                        ) {
                            Text("Agregar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal para agregar equipamiento
    if (showNewEquipmentDialog) {
        var equipName by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showNewEquipmentDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MeetColors.cardBackground,
                border = BorderStroke(1.dp, MeetColors.cyberCyan)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Registrar Herramienta / Equipo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    OutlinedTextField(
                        value = equipName,
                        onValueChange = { equipName = it },
                        label = { Text("Nombre del Equipo (ej: Escáner Autel MaxiSys)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showNewEquipmentDialog = false }) {
                            Text("Cancelar", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                if (equipName.isNotBlank()) {
                                    profileData = profileData.copy(certifiedEquipment = profileData.certifiedEquipment + equipName.trim())
                                    showNewEquipmentDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan)
                        ) {
                            Text("Añadir", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
