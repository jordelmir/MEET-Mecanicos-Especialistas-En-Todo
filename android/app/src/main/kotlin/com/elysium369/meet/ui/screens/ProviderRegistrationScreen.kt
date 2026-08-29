package com.elysium369.meet.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.data.local.entities.ProviderProfileEntity
import com.elysium369.meet.ride.domain.RideVerificationPolicy
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.ElysiumSectionIcon
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.HolographicBackgroundShared
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.roundToInt

// ─── Data holder for provider type metadata ─────────────────────────────────

private data class ProviderTypeInfo(
    val type: String,
    val icon: String,
    val label: String,
    val subtitle: String,
    val accentColor: Color,
    val specialtiesPlaceholder: String
)

private val providerTypes = listOf(
    ProviderTypeInfo(
        type = "MECHANIC",
        icon = "🛠️",
        label = "Mecánico",
        subtitle = "Recibe solicitudes de servicio mecánico de clientes",
        accentColor = MeetColors.cyberCyan,
        specialtiesPlaceholder = "Ej: Motor, Frenos, Suspensión, Eléctrico…"
    ),
    ProviderTypeInfo(
        type = "TOW_TRUCK",
        icon = "🚛",
        label = "Gruista",
        subtitle = "Recibe solicitudes de asistencia vial y grúa",
        accentColor = MeetColors.warning,
        specialtiesPlaceholder = "Ej: Grúa plataforma, Auxilio vial, Remolque…"
    ),
    ProviderTypeInfo(
        type = "PARTS_STORE",
        icon = "🧩",
        label = "Repuestera",
        subtitle = "Recibe solicitudes de repuestos y autopartes",
        accentColor = MeetColors.neonGreen,
        specialtiesPlaceholder = "Ej: Motor, Transmisión, Carrocería, Eléctrico…"
    ),
    ProviderTypeInfo(
        type = "RIDE_DRIVER",
        icon = "🚗",
        label = "Chofer de Viajes",
        subtitle = "Ofrece servicios de transporte con la red Elysium Vanguard",
        accentColor = MeetColors.electricBlue,
        specialtiesPlaceholder = "Ej: Sedán, SUV, Van, Premium…"
    )
)

// ─── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderRegistrationScreen(
    viewModel: ObdViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.refreshOwnTrustDecisions()
        viewModel.rideVerificationNotice.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Observe view-model state
    val profiles by viewModel.userProviderProfiles.collectAsState()
    val driverVerification by viewModel.driverVerification.collectAsState()
    val isCloudSession = viewModel.currentUserId != null

    // Registration dialog state
    var showRegistrationDialog by rememberSaveable { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTypeInfo = providerTypes.firstOrNull { it.type == selectedType }

    // Driver onboarding dialog state
    var showDriverOnboarding by rememberSaveable { mutableStateOf(false) }

    // Delete confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<ProviderProfileEntity?>(null) }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Registro de Proveedor",
                        color = MeetColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MeetColors.cyberCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDeep
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HolographicBackgroundShared()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            item {
                ProviderRegistrationHeroCard(
                    isCloudSession = isCloudSession,
                    activeProfileCount = profiles.count { it.isActive }
                )
            }

            // ── Existing Profiles Section ────────────────────────────────
            if (profiles.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Mis Perfiles de Proveedor",
                        icon = "👤"
                    )
                }

                items(profiles, key = { it.profileId }) { profile ->
                    ExistingProfileCard(
                        profile = profile,
                        onToggleActive = { isActive ->
                            viewModel.toggleProviderProfile(profile.profileId, isActive)
                        },
                        onDelete = {
                            profileToDelete = profile
                            showDeleteDialog = true
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // ── Registration Cards Section ───────────────────────────────
            item {
                SectionHeader(
                    title = "Registrarse como Proveedor",
                    icon = "🚀"
                )
            }

            items(providerTypes) { typeInfo ->
                val canonicalType = com.elysium369.meet.core.services.kernel.ProviderType
                    .fromDbValue(typeInfo.type)
                val matchingProfile = profiles.firstOrNull {
                    com.elysium369.meet.core.services.kernel.ProviderType
                        .fromDbValue(it.providerType) == canonicalType
                }
                val alreadyRegistered = when (typeInfo.type) {
                    "MECHANIC", "TOW_TRUCK", "PARTS_STORE" -> matchingProfile != null
                    "RIDE_DRIVER" -> RideVerificationPolicy.grantsAccess(driverVerification?.status)
                    else -> false
                }
                val statusLabel = when {
                    typeInfo.type != "RIDE_DRIVER" && matchingProfile?.verified == true ->
                        "Proveedor verificado"
                    typeInfo.type != "RIDE_DRIVER" && matchingProfile != null ->
                        "Verificación en revisión"
                    typeInfo.type != "RIDE_DRIVER" -> null
                    driverVerification?.status == "PENDING" -> "Verificación en revisión"
                    driverVerification?.status == "APPROVED" -> "Chofer verificado"
                    driverVerification?.status == RideVerificationPolicy.PILOT_APPROVED ->
                        "Expediente local listo; revisión pendiente"
                    driverVerification?.status == "REJECTED" -> "Reintentar verificación"
                    else -> null
                }
                RegistrationTypeCard(
                    info = typeInfo,
                    alreadyRegistered = alreadyRegistered,
                    statusLabel = statusLabel,
                    onClick = {
                        if (typeInfo.type == "RIDE_DRIVER") {
                            showDriverOnboarding = true
                        } else if (!alreadyRegistered) {
                            selectedType = typeInfo.type
                            showRegistrationDialog = true
                        }
                    }
                )
            }
            }
        }
    }

    // ── Registration Dialog ──────────────────────────────────────────────────
    if (showRegistrationDialog && selectedTypeInfo != null) {
        RegistrationFormDialog(
            typeInfo = selectedTypeInfo!!,
            onDismiss = {
                showRegistrationDialog = false
                selectedType = null
            },
            onRegister = { businessName, ownerName, phone, location, specialties, radiusKm, licenseNumber ->
                viewModel.registerAsProvider(
                    providerType = com.elysium369.meet.core.services.kernel.ProviderType.fromDbValue(selectedTypeInfo!!.type).dbValue,
                    businessName = businessName,
                    ownerName = ownerName,
                    phone = phone,
                    location = location,
                    specialties = specialties,
                    radiusKm = radiusKm,
                    licenseNumber = licenseNumber,
                    context = context
                )
                showRegistrationDialog = false
                selectedType = null
            }
        )
    }

    // ── Delete Confirmation Dialog ───────────────────────────────────────────
    if (showDeleteDialog && profileToDelete != null) {
        DeleteConfirmationDialog(
            profile = profileToDelete!!,
            onConfirm = {
                viewModel.deleteProviderProfile(profileToDelete!!.profileId)
                showDeleteDialog = false
                profileToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                profileToDelete = null
            }
        )
    }

    // ── Driver Onboarding Dialog ──────────────────────────────────────────────
    if (showDriverOnboarding) {
        DriverOnboardingDialog(
            existingVerification = driverVerification,
            onDismiss = { showDriverOnboarding = false },
            onSubmit = { name, phone, email, dob, make, model, year, color, plate, seats,
                         licF, licB, cedF, cedB, hoja, marchamo, dekra, seguro,
                         selfie, selfieCed, selfieLic, vehF, vehB, vehI ->
                viewModel.submitDriverVerification(
                    fullName = name, phone = phone, email = email, dateOfBirth = dob,
                    vehicleMake = make, vehicleModel = model, vehicleYear = year,
                    vehicleColor = color, vehiclePlate = plate, vehicleSeats = seats,
                    pathLicenciaFront = licF, pathLicenciaBack = licB,
                    pathCedulaFront = cedF, pathCedulaBack = cedB,
                    pathHojaDelincuencia = hoja, pathMarchamo = marchamo,
                    pathDekra = dekra, pathSeguro = seguro,
                    pathSelfieProfile = selfie, pathSelfieWithCedula = selfieCed,
                    pathSelfieWithLicencia = selfieLic,
                    pathVehicleFront = vehF, pathVehicleBack = vehB,
                    pathVehicleInterior = vehI
                )
                showDriverOnboarding = false
            },
            onDelete = {
                viewModel.deleteDriverVerification()
                showDriverOnboarding = false
            }
        )
    }
}

@Composable
private fun ProviderRegistrationHeroCard(
    isCloudSession: Boolean,
    activeProfileCount: Int
) {
    EliteCard(
        glowColor = MeetColors.neonGreen,
        borderColor = MeetColors.neonGreen.copy(alpha = 0.28f),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(18.dp),
        enableHolo3D = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MeetColors.neonGreen.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    ElysiumSectionIcon(
                        key = "provider_registration",
                        contentDescription = "Registro proveedor",
                        tint = MeetColors.neonGreen,
                        size = 30.dp,
                        fallbackGlyph = "ID"
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Centro de proveedores",
                        color = MeetColors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Activa perfiles por servicio dentro de la APK",
                        color = MeetColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderHeroChip(
                    text = if (isCloudSession) "Cloud listo" else "Modo local",
                    color = if (isCloudSession) MeetColors.neonGreen else MeetColors.warning,
                    modifier = Modifier.weight(1f)
                )
                ProviderHeroChip(
                    text = "$activeProfileCount activos",
                    color = MeetColors.cyberCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProviderHeroChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Section Header ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(text = icon, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = MeetColors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Existing Profile Card ───────────────────────────────────────────────────

@Composable
private fun ExistingProfileCard(
    profile: ProviderProfileEntity,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val typeInfo = providerTypes.firstOrNull { it.type == profile.providerType }
    val accentColor = typeInfo?.accentColor ?: MeetColors.cyberCyan
    val icon = typeInfo?.icon ?: "📦"
    val typeLabel = typeInfo?.label ?: profile.providerType

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = accentColor.copy(alpha = 0.15f),
                spotColor = accentColor.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.5f),
                    MeetColors.borderSubtle
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row: icon + type + verified badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = typeLabel,
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (profile.verified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MeetColors.neonGreen.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Verificado",
                                        tint = MeetColors.neonGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Verificado",
                                        color = MeetColors.neonGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = profile.businessName,
                        color = MeetColors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row: rating + jobs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating stars
                RatingStars(rating = profile.rating, accentColor = accentColor)

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = String.format("%.1f", profile.rating),
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeetColors.electricBlue.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WorkHistory,
                            contentDescription = null,
                            tint = MeetColors.electricBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${profile.totalJobs} trabajos",
                            color = MeetColors.electricBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MeetColors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = profile.location.ifEmpty { "Ubicación no definida" },
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(color = MeetColors.borderSubtle)

            Spacer(modifier = Modifier.height(10.dp))

            // Action row: toggle + delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Active toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (profile.isActive) "Activo" else "Inactivo",
                        color = if (profile.isActive) MeetColors.neonGreen else MeetColors.textMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = profile.isActive,
                        onCheckedChange = onToggleActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MeetColors.neonGreen,
                            checkedTrackColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                            uncheckedThumbColor = MeetColors.textMuted,
                            uncheckedTrackColor = MeetColors.borderSubtle
                        )
                    )
                }

                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Eliminar perfil",
                        tint = Color(0xFFEF5350)
                    )
                }
            }
        }
    }
}

// ─── Rating Stars Helper ─────────────────────────────────────────────────────

@Composable
private fun RatingStars(rating: Double, accentColor: Color) {
    Row {
        for (i in 1..5) {
            val starColor = when {
                i <= rating.toInt() -> accentColor
                i - 1 < rating && rating < i.toDouble() -> accentColor.copy(alpha = 0.5f)
                else -> MeetColors.borderSubtle
            }
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─── Registration Type Card ──────────────────────────────────────────────────

@Composable
private fun RegistrationTypeCard(
    info: ProviderTypeInfo,
    alreadyRegistered: Boolean,
    statusLabel: String? = null,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_glow_${info.type}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha_${info.type}"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (alreadyRegistered) 4.dp else 12.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = info.accentColor.copy(alpha = 0.1f),
                spotColor = info.accentColor.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alreadyRegistered)
                MeetColors.cardBackground.copy(alpha = 0.6f)
            else
                MeetColors.cardBackground
        ),
        border = BorderStroke(
            width = if (alreadyRegistered) 1.dp else 1.5.dp,
            brush = if (alreadyRegistered) {
                Brush.linearGradient(
                    colors = listOf(
                        MeetColors.neonGreen.copy(alpha = 0.4f),
                        MeetColors.neonGreen.copy(alpha = 0.15f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        info.accentColor.copy(alpha = glowAlpha + 0.3f),
                        info.accentColor.copy(alpha = glowAlpha)
                    )
                )
            }
        )
    ) {
        Box {
            // Subtle gradient overlay
            if (!alreadyRegistered) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    info.accentColor.copy(alpha = 0.04f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (alreadyRegistered)
                                MeetColors.neonGreen.copy(alpha = 0.1f)
                            else
                                info.accentColor.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = info.icon, fontSize = 30.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (alreadyRegistered) {
                    // Already registered state
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ya registrado como ${info.label}",
                            color = MeetColors.neonGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tu perfil está activo y visible para clientes",
                        color = MeetColors.textMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Not registered — call to action
                    Text(
                        text = "Registrarse como ${info.label}",
                        color = info.accentColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = info.subtitle,
                        color = MeetColors.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    if (statusLabel != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = info.accentColor.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, info.accentColor.copy(alpha = 0.24f))
                        ) {
                            Text(
                                text = statusLabel,
                                color = info.accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = info.accentColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AppRegistration,
                                contentDescription = null,
                                tint = info.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (statusLabel == null) "Toca para registrarte" else "Toca para ver estado",
                                color = info.accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Registration Form Dialog ────────────────────────────────────────────────

@Composable
private fun RegistrationFormDialog(
    typeInfo: ProviderTypeInfo,
    onDismiss: () -> Unit,
    onRegister: (
        businessName: String,
        ownerName: String,
        phone: String,
        location: String,
        specialties: String,
        radiusKm: Double,
        licenseNumber: String
    ) -> Unit
) {
    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var specialties by remember { mutableStateOf("") }
    var radiusKm by remember { mutableFloatStateOf(25f) }
    var licenseNumber by remember { mutableStateOf("") }

    val isFormValid = businessName.isNotBlank() &&
            ownerName.isNotBlank() &&
            phone.isNotBlank() &&
            location.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = typeInfo.icon, fontSize = 36.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Registro como ${typeInfo.label}",
                    color = typeInfo.accentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Business Name
                FormField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = "Nombre del negocio *",
                    placeholder = "Ej: Taller Automotriz García",
                    accentColor = typeInfo.accentColor,
                    leadingIcon = Icons.Filled.Business
                )

                // Owner Name
                FormField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = "Responsable / Dueño *",
                    placeholder = "Nombre completo",
                    accentColor = typeInfo.accentColor,
                    leadingIcon = Icons.Filled.Person
                )

                // Phone
                FormField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Teléfono *",
                    placeholder = "+52 55 1234 5678",
                    accentColor = typeInfo.accentColor,
                    leadingIcon = Icons.Filled.Phone,
                    keyboardType = KeyboardType.Phone
                )

                // Location
                FormField(
                    value = location,
                    onValueChange = { location = it },
                    label = "Ubicación / Dirección *",
                    placeholder = "Ciudad, Estado o dirección",
                    accentColor = typeInfo.accentColor,
                    leadingIcon = Icons.Filled.LocationOn
                )

                // Specialties
                FormField(
                    value = specialties,
                    onValueChange = { specialties = it },
                    label = "Especialidades",
                    placeholder = typeInfo.specialtiesPlaceholder,
                    accentColor = typeInfo.accentColor,
                    leadingIcon = Icons.Filled.Build
                )

                // Radius Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Radio de cobertura",
                            color = MeetColors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = typeInfo.accentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${radiusKm.roundToInt()} km",
                                color = typeInfo.accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = radiusKm,
                        onValueChange = { radiusKm = it },
                        valueRange = 5f..100f,
                        steps = 18, // 5 km increments → (100-5)/5 - 1 = 18 steps
                        colors = SliderDefaults.colors(
                            thumbColor = typeInfo.accentColor,
                            activeTrackColor = typeInfo.accentColor,
                            inactiveTrackColor = MeetColors.borderSubtle
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5 km", color = MeetColors.textMuted, fontSize = 11.sp)
                        Text("100 km", color = MeetColors.textMuted, fontSize = 11.sp)
                    }
                }

                // License number (optional)
                FormField(
                    value = licenseNumber,
                    onValueChange = { licenseNumber = it },
                    label = "N° Licencia / Patente (opcional)",
                    placeholder = "Número de licencia o patente",
                    accentColor = typeInfo.accentColor,
                    leadingIcon = Icons.Filled.Badge
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRegister(
                        businessName.trim(),
                        ownerName.trim(),
                        phone.trim(),
                        location.trim(),
                        specialties.trim(),
                        radiusKm.toDouble(),
                        licenseNumber.trim()
                    )
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = typeInfo.accentColor,
                    contentColor = MeetColors.backgroundDark,
                    disabledContainerColor = typeInfo.accentColor.copy(alpha = 0.25f),
                    disabledContentColor = MeetColors.textMuted
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.HowToReg,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REGISTRARME COMO ${typeInfo.label.uppercase()}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancelar",
                    color = MeetColors.textMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

// ─── Reusable Form Field ─────────────────────────────────────────────────────

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    accentColor: Color,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(text = label, fontSize = 13.sp)
        },
        placeholder = {
            Text(text = placeholder, fontSize = 13.sp, color = MeetColors.textMuted)
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MeetColors.textPrimary,
            unfocusedTextColor = MeetColors.textPrimary,
            cursorColor = accentColor,
            focusedBorderColor = accentColor,
            unfocusedBorderColor = MeetColors.borderSubtle,
            focusedLabelColor = accentColor,
            unfocusedLabelColor = MeetColors.textSecondary,
            focusedContainerColor = MeetColors.cardBackground,
            unfocusedContainerColor = MeetColors.cardBackground
        )
    )
}

// ─── Delete Confirmation Dialog ──────────────────────────────────────────────

@Composable
private fun DeleteConfirmationDialog(
    profile: ProviderProfileEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF5350).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        title = {
            Text(
                text = "¿Eliminar perfil?",
                color = MeetColors.textPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Se eliminará tu perfil de \"${profile.businessName}\" de forma permanente. " +
                        "Los clientes ya no podrán encontrarte como proveedor de este tipo.",
                color = MeetColors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF5350)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sí, eliminar",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancelar",
                    color = MeetColors.textMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

// ─── Driver Onboarding Dialog (6-Step Wizard) ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverOnboardingDialog(
    existingVerification: com.elysium369.meet.data.local.entities.DriverVerificationEntity?,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String, phone: String, email: String, dob: String,
        make: String, model: String, year: Int, color: String, plate: String,
        seats: Int,
        licF: String, licB: String, cedF: String, cedB: String, hoja: String,
        marchamo: String, dekra: String, seguro: String,
        selfie: String, selfieCed: String, selfieLic: String,
        vehF: String, vehB: String, vehI: String
    ) -> Unit,
    onDelete: () -> Unit
) {
    val accent = MeetColors.electricBlue

    // If a verification already exists, show status instead of form
    if (existingVerification != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MeetColors.backgroundDark)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (existingVerification.status) {
                            "APPROVED" -> {
                                Text("✅", fontSize = 56.sp)
                                Text(
                                    "CHOFER VERIFICADO",
                                    color = MeetColors.neonGreen,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Tu cuenta de chofer está aprobada. Ya puedes recibir solicitudes de viaje.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MeetColors.neonGreen.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = buildString {
                                            append("Aprobado el ")
                                            append(
                                                java.text.SimpleDateFormat(
                                                    "dd/MM/yyyy HH:mm",
                                                    java.util.Locale.getDefault(),
                                                ).format(java.util.Date(existingVerification.approvedAt ?: 0)),
                                            )
                                        },
                                        color = MeetColors.neonGreen,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            "PENDING", RideVerificationPolicy.PILOT_APPROVED -> {
                                Text("⏳", fontSize = 56.sp)
                                Text(
                                    "VERIFICACIÓN EN REVISIÓN",
                                    color = MeetColors.warning,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Tu solicitud fue recibida y está siendo revisada. Te notificaremos cuando sea aprobada.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MeetColors.warning.copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Documentos recibidos: ✔️",
                                            color = MeetColors.warning,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${existingVerification.fullName} • ${existingVerification.vehicleMake} ${existingVerification.vehicleModel} ${existingVerification.vehicleYear}",
                                            color = MeetColors.textMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            "REJECTED" -> {
                                Text("❌", fontSize = 56.sp)
                                Text(
                                    "SOLICITUD RECHAZADA",
                                    color = Color(0xFFEF5350),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    existingVerification.rejectionReason ?: "Tu solicitud fue rechazada. Revisa tus documentos e intenta de nuevo.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                                Button(
                                    onClick = onDelete,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF5350),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("🔄 ELIMINAR Y REINTENTAR", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar", color = MeetColors.textMuted, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        return
    }

    // ── 6-Step Onboarding Form ───────────────────────────────────────────────

    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val totalSteps = 6

    // Step 1: Personal Info
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }

    // Step-by-step document path states
    var pathMarchamo by rememberSaveable { mutableStateOf("") }
    var pathDekra by rememberSaveable { mutableStateOf("") }
    var pathSeguro by rememberSaveable { mutableStateOf("") }
    var pathLicF by rememberSaveable { mutableStateOf("") }
    var pathLicB by rememberSaveable { mutableStateOf("") }
    var pathCedF by rememberSaveable { mutableStateOf("") }
    var pathCedB by rememberSaveable { mutableStateOf("") }
    var pathHoja by rememberSaveable { mutableStateOf("") }
    var pathSelfie by rememberSaveable { mutableStateOf("") }
    var pathSelfieCed by rememberSaveable { mutableStateOf("") }
    var pathSelfieLic by rememberSaveable { mutableStateOf("") }
    var pathVehF by rememberSaveable { mutableStateOf("") }
    var pathVehB by rememberSaveable { mutableStateOf("") }
    var pathVehI by rememberSaveable { mutableStateOf("") }

    // Step 2: Vehicle Info
    var vMake by rememberSaveable { mutableStateOf("") }
    var vModel by rememberSaveable { mutableStateOf("") }
    var vYear by rememberSaveable { mutableStateOf("") }
    var vColor by rememberSaveable { mutableStateOf("") }
    var vPlate by rememberSaveable { mutableStateOf("") }
    var vSeats by rememberSaveable { mutableStateOf("") }

    val launchVerificationPhoto = rememberVerificationPhotoCapture { documentType, path ->
        when (documentType) {
            "MARCHAMO" -> pathMarchamo = path
            "DEKRA" -> pathDekra = path
            "SEGURO" -> pathSeguro = path
            "LICENCIA_FRONT" -> pathLicF = path
            "LICENCIA_BACK" -> pathLicB = path
            "CEDULA_FRONT" -> pathCedF = path
            "CEDULA_BACK" -> pathCedB = path
            "HOJA" -> pathHoja = path
            "SELFIE_PROFILE" -> pathSelfie = path
            "SELFIE_WITH_CEDULA" -> pathSelfieCed = path
            "SELFIE_WITH_LICENCIA" -> pathSelfieLic = path
            "VEHICLE_FRONT" -> pathVehF = path
            "VEHICLE_BACK" -> pathVehB = path
            "VEHICLE_INT" -> pathVehI = path
        }
    }
    var captureGuideType by rememberSaveable { mutableStateOf<String?>(null) }

    val triggerPhotoCapture = { docType: String -> captureGuideType = docType }

    val stepTitles = listOf(
        "👤 Información Personal",
        "🚗 Información del Vehículo",
        "📄 Documentos del Vehículo",
        "📋 Documentos Legales",
        "🤳 Verificación Biométrica",
        "📸 Fotos del Vehículo"
    )

    val canAdvance = when (currentStep) {
        0 -> fullName.isNotBlank() && phone.isNotBlank() && email.isNotBlank() && dob.isNotBlank()
        1 -> vMake.isNotBlank() && vModel.isNotBlank() && vYear.isNotBlank() &&
            vColor.isNotBlank() && vPlate.isNotBlank() &&
            (vSeats.toIntOrNull() in 1..16)
        2 -> pathMarchamo.isNotBlank() && pathDekra.isNotBlank() && pathSeguro.isNotBlank()
        3 -> pathLicF.isNotBlank() && pathLicB.isNotBlank() && pathCedF.isNotBlank() && pathCedB.isNotBlank() && pathHoja.isNotBlank()
        4 -> pathSelfie.isNotBlank() && pathSelfieCed.isNotBlank() && pathSelfieLic.isNotBlank()
        5 -> pathVehF.isNotBlank() && pathVehB.isNotBlank() && pathVehI.isNotBlank()
        else -> false
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MeetColors.backgroundDark,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Registro de Chofer",
                                color = MeetColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Paso ${currentStep + 1} de $totalSteps",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = accent)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep)
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Step Indicator ────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 0 until totalSteps) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            i < currentStep -> MeetColors.neonGreen
                                            i == currentStep -> accent
                                            else -> MeetColors.borderSubtle
                                        }
                                    )
                            )
                        }
                    }

                    // ── Step Title ────────────────────────────────────────────────
                    Text(
                        text = stepTitles[currentStep],
                        color = accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    // ── Step Content ──────────────────────────────────────────────
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MeetColors.electricBlue.copy(alpha = 0.10f),
                                border = BorderStroke(
                                    1.dp,
                                    MeetColors.electricBlue.copy(alpha = 0.35f),
                                ),
                            ) {
                                Text(
                                    text = "Tu expediente se enviará a revisión. El modo chofer se habilita únicamente después de una aprobación remota.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }

                        when (currentStep) {
                            0 -> {
                                item {
                                    Text("Completa tus datos personales:", color = MeetColors.textSecondary, fontSize = 13.sp)
                                }
                                item { OnboardField(fullName, { fullName = it }, "Nombre Completo", "Juan Pérez García", accent, Icons.Filled.Person) }
                                item { OnboardField(phone, { phone = it }, "Teléfono", "+506 8888-8888", accent, Icons.Filled.Phone, KeyboardType.Phone) }
                                item { OnboardField(email, { email = it }, "Correo Electrónico", "chofer@email.com", accent, Icons.Filled.Email, KeyboardType.Email) }
                                item { OnboardField(dob, { dob = it }, "Fecha de Nacimiento", "1990-01-15", accent, Icons.Filled.CalendarMonth) }
                            }
                            1 -> {
                                item {
                                    Text("Información del vehículo que usarás:", color = MeetColors.textSecondary, fontSize = 13.sp)
                                }
                                item { OnboardField(vMake, { vMake = it }, "Marca", "Toyota", accent, Icons.Filled.DirectionsCar) }
                                item { OnboardField(vModel, { vModel = it }, "Modelo", "Corolla", accent, Icons.Filled.DriveEta) }
                                item { OnboardField(vYear, { vYear = it }, "Año", "2022", accent, Icons.Filled.CalendarMonth, KeyboardType.Number) }
                                item { OnboardField(vColor, { vColor = it }, "Color", "Blanco", accent, Icons.Filled.Palette) }
                                item { OnboardField(vPlate, { vPlate = it }, "Número de Placa", "ABC-123", accent, Icons.Filled.Pin) }
                                item { OnboardField(vSeats, { vSeats = it }, "Asientos habilitados", "4", accent, Icons.Filled.EventSeat, KeyboardType.Number) }
                            }
                            2 -> {
                                item {
                                    Text("Adjunta los documentos de tu vehículo:", color = MeetColors.textSecondary, fontSize = 13.sp)
                                }
                                item {
                                    DocCaptureButton("📜 Marchamo", "Derecho de circulación vigente", "Toma una foto del documento", pathMarchamo.isNotBlank()) {
                                        triggerPhotoCapture("MARCHAMO")
                                    }
                                }
                                item {
                                    DocCaptureButton("🔧 DEKRA / RTV", "Revisión Técnica Vehicular vigente", "Toma una foto del resultado", pathDekra.isNotBlank()) {
                                        triggerPhotoCapture("DEKRA")
                                    }
                                }
                                item {
                                    DocCaptureButton("🛡️ Seguro Vehicular", "Póliza de seguro vigente", "Toma una foto de la póliza", pathSeguro.isNotBlank()) {
                                        triggerPhotoCapture("SEGURO")
                                    }
                                }
                            }
                            3 -> {
                                item {
                                    Text("Adjunta tus documentos legales personales:", color = MeetColors.textSecondary, fontSize = 13.sp)
                                }
                                item {
                                    DocCaptureButton("🪪 Licencia de Conducir — Frente", "Lado frontal de tu licencia vigente", "Foto clara y sin reflejos", pathLicF.isNotBlank()) {
                                        triggerPhotoCapture("LICENCIA_FRONT")
                                    }
                                }
                                item {
                                    DocCaptureButton("🪪 Licencia de Conducir — Reverso", "Lado trasero de tu licencia", "Foto clara y sin reflejos", pathLicB.isNotBlank()) {
                                        triggerPhotoCapture("LICENCIA_BACK")
                                    }
                                }
                                item {
                                    DocCaptureButton("🆔 Cédula de Identidad — Frente", "Cédula/DNI lado frontal", "Debe verse tu nombre y foto", pathCedF.isNotBlank()) {
                                        triggerPhotoCapture("CEDULA_FRONT")
                                    }
                                }
                                item {
                                    DocCaptureButton("🆔 Cédula de Identidad — Reverso", "Cédula/DNI lado trasero", "Debe verse el código y firma", pathCedB.isNotBlank()) {
                                        triggerPhotoCapture("CEDULA_BACK")
                                    }
                                }
                                item {
                                    DocCaptureButton("📋 Hoja de Delincuencia", "Antecedentes penales limpios", "Documento vigente (menos de 3 meses)", pathHoja.isNotBlank()) {
                                        triggerPhotoCapture("HOJA")
                                    }
                                }
                            }
                            4 -> {
                                item {
                                    Text("Verificación biométrica facial — Tómate selfies con tus documentos:", color = MeetColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                                }
                                item {
                                    DocCaptureButton("📸 Foto de Perfil", "Selfie frontal con buena iluminación", "Rostro visible, sin lentes de sol", pathSelfie.isNotBlank()) {
                                        triggerPhotoCapture("SELFIE_PROFILE")
                                    }
                                }
                                item {
                                    DocCaptureButton("🤳 Selfie con Cédula al Lado de la Cara", "Sostén tu cédula junto a tu rostro", "Ambos deben verse claros en la misma foto", pathSelfieCed.isNotBlank()) {
                                        triggerPhotoCapture("SELFIE_WITH_CEDULA")
                                    }
                                }
                                item {
                                    DocCaptureButton("🤳 Selfie con Licencia al Lado de la Cara", "Sostén tu licencia junto a tu rostro", "Ambos deben verse claros en la misma foto", pathSelfieLic.isNotBlank()) {
                                        triggerPhotoCapture("SELFIE_WITH_LICENCIA")
                                    }
                                }
                            }
                            5 -> {
                                item {
                                    Text("Toma fotos de tu vehículo desde distintos ángulos:", color = MeetColors.textSecondary, fontSize = 13.sp)
                                }
                                item {
                                    DocCaptureButton("🚗 Foto Frontal del Vehículo", "Vista delantera completa", "Placa visible, buena iluminación", pathVehF.isNotBlank()) {
                                        triggerPhotoCapture("VEHICLE_FRONT")
                                    }
                                }
                                item {
                                    DocCaptureButton("🚗 Foto Trasera del Vehículo", "Vista trasera completa", "Placa trasera visible", pathVehB.isNotBlank()) {
                                        triggerPhotoCapture("VEHICLE_BACK")
                                    }
                                }
                                item {
                                    DocCaptureButton("🪑 Foto del Interior", "Asientos, tablero y estado general", "Debe verse limpio y en buen estado", pathVehI.isNotBlank()) {
                                        triggerPhotoCapture("VEHICLE_INT")
                                    }
                                }
                            }
                        }
                    }
                }


                // ── Navigation Buttons ────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeetColors.backgroundDeep)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
                        ) {
                            Text("◀ ANTERIOR", color = accent, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (currentStep < totalSteps - 1) {
                        Button(
                            onClick = { currentStep++ },
                            enabled = canAdvance,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.White,
                                disabledContainerColor = accent.copy(alpha = 0.2f),
                                disabledContentColor = MeetColors.textMuted
                            )
                        ) {
                            Text("SIGUIENTE ▶", fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Button(
                            onClick = {
                                onSubmit(
                                    fullName, phone, email, dob,
                                    vMake, vModel, vYear.toIntOrNull() ?: 0, vColor, vPlate,
                                    vSeats.toIntOrNull() ?: 0,
                                    pathLicF, pathLicB, pathCedF, pathCedB, pathHoja,
                                    pathMarchamo, pathDekra, pathSeguro,
                                    pathSelfie, pathSelfieCed, pathSelfieLic,
                                    pathVehF, pathVehB, pathVehI
                                )
                            },
                            enabled = canAdvance,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.neonGreen,
                                contentColor = Color.Black,
                                disabledContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                                disabledContentColor = MeetColors.textMuted
                            )
                        ) {
                            Text("🚀 ENVIAR SOLICITUD", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    }
                }

                // Overlay Guide Dialog
                if (captureGuideType != null) {
                    CaptureGuideOverlay(
                        documentType = captureGuideType!!,
                        onDismiss = { captureGuideType = null },
                        onProceed = {
                            captureGuideType?.let { documentType ->
                                captureGuideType = null
                                launchVerificationPhoto("driver", documentType)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── Onboarding Text Field Helper ────────────────────────────────────────────

@Composable
private fun OnboardField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    accentColor: Color,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text(placeholder, fontSize = 13.sp, color = MeetColors.textMuted) },
        leadingIcon = {
            Icon(leadingIcon, null, tint = accentColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MeetColors.textPrimary,
            unfocusedTextColor = MeetColors.textPrimary,
            cursorColor = accentColor,
            focusedBorderColor = accentColor,
            unfocusedBorderColor = MeetColors.borderSubtle,
            focusedLabelColor = accentColor,
            unfocusedLabelColor = MeetColors.textSecondary,
            focusedContainerColor = MeetColors.cardBackground,
            unfocusedContainerColor = MeetColors.cardBackground
        )
    )
}

// ─── Document Capture Button ─────────────────────────────────────────────────

@Composable
private fun DocCaptureButton(
    label: String,
    description: String,
    hint: String,
    isCaptured: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured) Color(0xFF0D2818) else MeetColors.cardBackground
        ),
        border = BorderStroke(
            1.dp,
            if (isCaptured) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.borderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = if (isCaptured) MeetColors.neonGreen else MeetColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(description, color = MeetColors.textSecondary, fontSize = 12.sp)
                if (!isCaptured) {
                    Text(hint, color = MeetColors.textMuted, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isCaptured) {
                Icon(Icons.Filled.CheckCircle, null, tint = MeetColors.neonGreen, modifier = Modifier.size(28.dp))
            } else {
                Icon(Icons.Filled.CameraAlt, null, tint = MeetColors.electricBlue, modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ─── Capture Guide Dialog with Cyber Scanner Animation ────────────────────────

private @Composable
fun CaptureGuideOverlay(
    documentType: String,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    val accent = MeetColors.cyberCyan
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.dp, MeetColors.borderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val title = when (documentType) {
                    "CEDULA_FRONT" -> "🆔 Cédula de Identidad (Frente)"
                    "CEDULA_BACK" -> "🆔 Cédula de Identidad (Reverso)"
                    "LICENCIA_FRONT" -> "🪪 Licencia de Conducir (Frente)"
                    "LICENCIA_BACK" -> "🪪 Licencia de Conducir (Reverso)"
                    "SELFIE_PROFILE" -> "📸 Foto de Perfil (Selfie)"
                    "SELFIE_WITH_CEDULA" -> "🤳 Selfie con Cédula al Lado del Rostro"
                    "SELFIE_WITH_LICENCIA" -> "🤳 Selfie con Licencia al Lado del Rostro"
                    "MARCHAMO" -> "📜 Foto de Marchamo"
                    "DEKRA" -> "🔧 Foto de DEKRA / RTV"
                    "SEGURO" -> "🛡️ Foto de Seguro Vehicular"
                    "HOJA" -> "📋 Hoja de Delincuencia"
                    "VEHICLE_FRONT" -> "🚗 Foto Frontal del Vehículo"
                    "VEHICLE_BACK" -> "🚗 Foto Trasera del Vehículo"
                    "VEHICLE_INT" -> "🪑 Foto Interior del Vehículo"
                    else -> "Toma de Foto"
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                // Interactive Scanner Animation
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeetColors.cardBackground)
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Draw corner guidelines
                        val strokeW = 3.dp.toPx()
                        val cornerLen = 20.dp.toPx()

                        // Top-left
                        drawPath(Path().apply {
                            moveTo(12.dp.toPx(), 12.dp.toPx() + cornerLen)
                            lineTo(12.dp.toPx(), 12.dp.toPx())
                            lineTo(12.dp.toPx() + cornerLen, 12.dp.toPx())
                        }, accent, style = Stroke(strokeW))

                        // Top-right
                        drawPath(Path().apply {
                            moveTo(w - 12.dp.toPx() - cornerLen, 12.dp.toPx())
                            lineTo(w - 12.dp.toPx(), 12.dp.toPx())
                            lineTo(w - 12.dp.toPx(), 12.dp.toPx() + cornerLen)
                        }, accent, style = Stroke(strokeW))

                        // Bottom-left
                        drawPath(Path().apply {
                            moveTo(12.dp.toPx(), h - 12.dp.toPx() - cornerLen)
                            lineTo(12.dp.toPx(), h - 12.dp.toPx())
                            lineTo(12.dp.toPx() + cornerLen, h - 12.dp.toPx())
                        }, accent, style = Stroke(strokeW))

                        // Bottom-right
                        drawPath(Path().apply {
                            moveTo(w - 12.dp.toPx() - cornerLen, h - 12.dp.toPx())
                            lineTo(w - 12.dp.toPx(), h - 12.dp.toPx())
                            lineTo(w - 12.dp.toPx(), h - 12.dp.toPx() - cornerLen)
                        }, accent, style = Stroke(strokeW))

                        // Draw silhouettes
                        when (documentType) {
                            "CEDULA_FRONT", "CEDULA_BACK", "LICENCIA_FRONT", "LICENCIA_BACK", "MARCHAMO", "DEKRA", "SEGURO", "HOJA" -> {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(30.dp.toPx(), 50.dp.toPx()),
                                    size = Size(w - 60.dp.toPx(), h - 100.dp.toPx()),
                                    cornerRadius = CornerRadius(6.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.2f),
                                    start = Offset(42.dp.toPx(), 70.dp.toPx()),
                                    end = Offset(w - 55.dp.toPx(), 70.dp.toPx()),
                                    strokeWidth = 3.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.2f),
                                    start = Offset(42.dp.toPx(), 85.dp.toPx()),
                                    end = Offset(w - 85.dp.toPx(), 85.dp.toPx()),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                            "SELFIE_PROFILE" -> {
                                drawOval(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(w/2 - 30.dp.toPx(), h/2 - 45.dp.toPx()),
                                    size = Size(60.dp.toPx(), 80.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawPath(Path().apply {
                                    moveTo(w/2 - 45.dp.toPx(), h - 25.dp.toPx())
                                    quadraticBezierTo(w/2, h - 60.dp.toPx(), w/2 + 45.dp.toPx(), h - 25.dp.toPx())
                                }, Color.White.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
                            }
                            "SELFIE_WITH_CEDULA", "SELFIE_WITH_LICENCIA" -> {
                                drawOval(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(w/2 - 40.dp.toPx(), h/2 - 40.dp.toPx()),
                                    size = Size(50.dp.toPx(), 70.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.25f),
                                    topLeft = Offset(w/2 + 15.dp.toPx(), h/2 - 5.dp.toPx()),
                                    size = Size(35.dp.toPx(), 22.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx()),
                                    style = Stroke(1.5f.dp.toPx())
                                )
                            }
                            "VEHICLE_FRONT", "VEHICLE_BACK" -> {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(25.dp.toPx(), 60.dp.toPx()),
                                    size = Size(w - 50.dp.toPx(), h - 110.dp.toPx()),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawCircle(Color.White.copy(alpha = 0.15f), 10.dp.toPx(), Offset(50.dp.toPx(), h - 50.dp.toPx()))
                                drawCircle(Color.White.copy(alpha = 0.15f), 10.dp.toPx(), Offset(w - 50.dp.toPx(), h - 50.dp.toPx()))
                            }
                            "VEHICLE_INT" -> {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.15f),
                                    radius = 30.dp.toPx(),
                                    center = Offset(w/2, h/2),
                                    style = Stroke(3.dp.toPx())
                                )
                            }
                        }

                        // Scanning green bar
                        val scanY = 16.dp.toPx() + (h - 32.dp.toPx()) * scanLineProgress
                        drawLine(
                            color = MeetColors.neonGreen,
                            start = Offset(16.dp.toPx(), scanY),
                            end = Offset(w - 16.dp.toPx(), scanY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Guidelines
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val guidelines = when (documentType) {
                        "CEDULA_FRONT", "CEDULA_BACK", "LICENCIA_FRONT", "LICENCIA_BACK", "MARCHAMO", "DEKRA", "SEGURO", "HOJA" -> listOf(
                            "Coloca el documento sobre una superficie plana y oscura.",
                            "Evita los reflejos directos de luz y destellos de flash.",
                            "Asegúrate de que todo el texto sea legible y no esté borroso."
                        )
                        "SELFIE_PROFILE" -> listOf(
                            "Busca un entorno con buena iluminación frontal.",
                            "Mira fijamente a la cámara con una expresión neutra.",
                            "Quítate gorras, lentes oscuros y mascarillas."
                        )
                        "SELFIE_WITH_CEDULA", "SELFIE_WITH_LICENCIA" -> listOf(
                            "Sostén tu documento al lado de tu cara sin cubrir tu rostro.",
                            "Asegúrate de no tapar tus ojos, boca u orejas con el documento.",
                            "Tanto tu cara como el texto de la identificación deben ser nítidos."
                        )
                        "VEHICLE_FRONT", "VEHICLE_BACK" -> listOf(
                            "Captura el vehículo completo a una distancia adecuada.",
                            "Asegúrate de que las placas sean perfectamente visibles.",
                            "Toma la foto a la luz del día o con buena iluminación."
                        )
                        else -> listOf(
                            "Busca un lugar iluminado de frente.",
                            "Sostén firmemente el celular para evitar fotos borrosas.",
                            "Verifica que el elemento principal esté enfocado."
                        )
                    }

                    guidelines.forEach { tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💡 ", fontSize = 13.sp)
                            Text(
                                text = tip,
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = MeetColors.textMuted)
                    }
                    Button(
                        onClick = onProceed,
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("ABRIR CÁMARA 📸", color = MeetColors.backgroundDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
