package com.elysium369.meet.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elysium369.meet.core.parts.CompatibilityConfidence
import com.elysium369.meet.core.parts.CompatibilityContext
import com.elysium369.meet.core.parts.CompatibilityEngine
import com.elysium369.meet.core.parts.CompatibilityResult
import com.elysium369.meet.core.parts.DraftQuote
import com.elysium369.meet.core.parts.PartAvailability
import com.elysium369.meet.core.parts.PartCondition
import com.elysium369.meet.core.parts.PartPosition
import com.elysium369.meet.core.parts.PartQuoteRanker
import com.elysium369.meet.core.parts.PartRequestPublicationPolicy
import com.elysium369.meet.core.parts.PartSuggestionEngine
import com.elysium369.meet.core.parts.PartSuggestionInput
import com.elysium369.meet.core.parts.PartsMarketplaceContract
import com.elysium369.meet.core.parts.QuotePrimaryTag
import com.elysium369.meet.core.parts.QuoteValidator
import com.elysium369.meet.core.parts.RankablePartQuote
import com.elysium369.meet.core.parts.SuggestionSource
import com.elysium369.meet.core.parts.ValidationLevel
import com.elysium369.meet.core.parts.VehicleFingerprint
import com.elysium369.meet.core.parts.WarningSeverity
import com.elysium369.meet.core.catalog.CanonicalVehiclePartRepository
import com.elysium369.meet.core.catalog.VehicleTechnicalAtlasDescriptors
import com.elysium369.meet.visual3d.domain.G4edAtlas3dCatalog
import com.elysium369.meet.visual3d.domain.G4edAtlas3dRepository
import com.elysium369.meet.visual3d.domain.VehicleTechnicalAtlas3dCatalog
import com.elysium369.meet.visual3d.domain.VehicleTechnicalAtlas3dRepository
import com.elysium369.meet.data.local.entities.PartRequestEntity
import com.elysium369.meet.data.local.entities.PartOfferEntity
import com.elysium369.meet.data.local.entities.RatingEntity
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.knowledge.RepairKnowledgeEvidencePanel
import com.elysium369.meet.ui.knowledge.RepairKnowledgeUiState
import com.elysium369.meet.ui.knowledge.rememberRepairKnowledgeUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object PartColors {
    val darkBackground = Color(0xFF0A0E1A)
    val cardBackground = Color(0xFF121829)
    val cyanAccent = Color(0xFF00E5FF)
    val orangeAccent = Color(0xFFFF6D00)
    val greenAccent = Color(0xFF00E676)
    val redAccent = Color(0xFFFF1744)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF90A4AE)
    val borderSubtle = Color(0xFF1E293B)
}

private data class RankedOfferUi(
    val offer: PartOfferEntity,
    val confidence: CompatibilityConfidence,
    val score: Double,
    val tag: QuotePrimaryTag?
)

private fun Vehicle?.toPartsFingerprint(
    partNumber: String? = null,
    oemPreference: String? = null
): VehicleFingerprint {
    val vehicle = this ?: return VehicleFingerprint(
        partNumber = partNumber?.takeIf { it.isNotBlank() },
        oemNumber = partNumber?.takeIf { oemPreference.equals("OEM", ignoreCase = true) && it.isNotBlank() }
    )
    val engineDetails = listOf(
        vehicle.engine,
        vehicle.engine_tech,
        vehicle.displacement_cc.takeIf { it > 0 }?.let { "${it}cc" }
    ).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { null }
    val transmission = listOf(vehicle.transmission_type, vehicle.transmission_subtype)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { null }

    return VehicleFingerprint(
        brand = vehicle.make,
        model = vehicle.model,
        year = vehicle.year,
        engine = engineDetails,
        transmission = transmission,
        fuel = vehicle.fuel_type.takeIf { it.isNotBlank() },
        vin = vehicle.vin.takeIf { it.isNotBlank() },
        oemNumber = partNumber?.takeIf { oemPreference.equals("OEM", ignoreCase = true) && it.isNotBlank() },
        partNumber = partNumber?.takeIf { it.isNotBlank() }
    )
}

private fun legacyPositionToPartPosition(position: String): PartPosition =
    PartPosition.fromString(PartsMarketplaceContract.positionToV2(position))

private fun requestCompatibilityContext(
    request: PartRequestEntity,
    offeredPartNumber: String? = null
): CompatibilityContext = CompatibilityContext(
    vehicle = VehicleFingerprint(
        oemNumber = request.partNumber?.takeIf { request.oemPreference.equals("OEM", ignoreCase = true) },
        partNumber = offeredPartNumber?.takeIf { it.isNotBlank() } ?: request.partNumber
    ),
    partName = request.partName,
    position = legacyPositionToPartPosition(request.partPosition),
    dtcCodes = listOfNotNull(request.dtcCode)
)

private fun inferOfferConfidence(request: PartRequestEntity, offer: PartOfferEntity): CompatibilityConfidence {
    val requested = request.partNumber?.trim().orEmpty()
    val offered = offer.partNumber.trim()
    return when {
        requested.isNotBlank() && requested.equals(offered, ignoreCase = true) -> CompatibilityConfidence.HIGH
        offered.isNotBlank() && !offered.equals("Por confirmar", ignoreCase = true) -> CompatibilityConfidence.MEDIUM
        else -> CompatibilityConfidence.UNKNOWN
    }
}

private fun rankOffersForRequest(
    request: PartRequestEntity,
    offers: List<PartOfferEntity>
): List<RankedOfferUi> {
    val confidenceById = offers.associate { it.offerId to inferOfferConfidence(request, it) }
    val offerById = offers.associateBy { it.offerId }
    val ranked = PartQuoteRanker.rankQuotes(
        offers.map { offer ->
            RankablePartQuote(
                id = offer.offerId,
                price = offer.price,
                warrantyDays = offer.warrantyDays,
                estimatedDeliveryHours = ((offer.etaMinutes + 59) / 60).coerceAtLeast(1),
                compatibilityConfidence = confidenceById[offer.offerId] ?: CompatibilityConfidence.UNKNOWN,
                ratingAvg = 0.0
            )
        }
    )

    return ranked.mapNotNull { rankedQuote ->
        offerById[rankedQuote.id]?.let { offer ->
            RankedOfferUi(
                offer = offer,
                confidence = confidenceById[offer.offerId] ?: CompatibilityConfidence.UNKNOWN,
                score = rankedQuote.compositeScore,
                tag = rankedQuote.primaryTag
            )
        }
    }
}

private fun quoteTagLabel(tag: QuotePrimaryTag?): String? = when (tag) {
    QuotePrimaryTag.BEST_COMPAT -> "MEJOR COMPAT."
    QuotePrimaryTag.CHEAPEST -> "MEJOR PRECIO"
    QuotePrimaryTag.FASTEST -> "MAS RAPIDA"
    QuotePrimaryTag.TOP_RATED -> "TOP REPUESTERA"
    null -> null
}

private fun legacyConditionToPartCondition(condition: String): PartCondition = when (condition.uppercase()) {
    "OEM" -> PartCondition.NEW_OEM
    "USED", "USED_TESTED" -> PartCondition.USED
    "REMAN" -> PartCondition.REFURBISHED
    "REBUILT" -> PartCondition.REBUILT
    "NEW" -> PartCondition.NEW_AFTERMARKET
    else -> PartCondition.UNKNOWN
}

private fun compatibilityColor(confidence: CompatibilityConfidence): Color = when (confidence) {
    CompatibilityConfidence.EXACT, CompatibilityConfidence.HIGH -> PartColors.greenAccent
    CompatibilityConfidence.MEDIUM -> PartColors.cyanAccent
    CompatibilityConfidence.LOW -> PartColors.orangeAccent
    CompatibilityConfidence.UNKNOWN -> PartColors.textSecondary
}

private fun compatibilityNotesFor(result: CompatibilityResult): String = buildString {
    append(CompatibilityEngine.describeVerdict(result))
    result.warnings.take(3).forEach { warning ->
        append(" [${warning.severity}: ${warning.message}]")
    }
    result.requiredConfirmations.take(2).forEach { confirmation ->
        append(" [Confirmar: $confirmation]")
    }
}

@Composable
private fun CompatibilityResultPanel(
    result: CompatibilityResult,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val hasBlock = result.warnings.any { it.severity == WarningSeverity.BLOCK }
    val borderColor = if (hasBlock) PartColors.redAccent else accentColor
    Surface(
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Compatibilidad ${result.confidence}",
                color = compatibilityColor(result.confidence),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = CompatibilityEngine.describeVerdict(result),
                color = PartColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            result.warnings.take(3).forEach { warning ->
                Text(
                    text = "${warning.severity}: ${warning.message}",
                    color = if (warning.severity == WarningSeverity.BLOCK) PartColors.redAccent else Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            result.requiredConfirmations.take(2).forEach { confirmation ->
                Text(
                    text = "Confirmar: $confirmation",
                    color = PartColors.orangeAccent,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun QuoteValidationPanel(
    validation: com.elysium369.meet.core.parts.ValidationResult,
    modifier: Modifier = Modifier
) {
    if (validation.level == ValidationLevel.OK) return

    val color = if (validation.level == ValidationLevel.BLOCK) PartColors.redAccent else PartColors.orangeAccent
    Surface(
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (validation.level == ValidationLevel.BLOCK) "Cotizacion bloqueada" else "Advertencias de cotizacion",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            (validation.errors + validation.warnings).take(4).forEach { issue ->
                Text(
                    text = issue.message,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartRequestScreen(
    viewModel: ObdViewModel,
    prefilledVehicleInfo: String? = null,
    prefilledAtlasPartId: String? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var isStoreMode by remember { mutableStateOf(false) }
    val allRequests by viewModel.partRequests.collectAsState()
    val openRequests by viewModel.openPartRequests.collectAsState()
    val isStoreRegistered by viewModel.isPartsStore.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    var showRegistrationScreen by remember { mutableStateOf(false) }

    var showRatingDialog by remember { mutableStateOf(false) }
    var ratingTargetId by remember { mutableStateOf("") }
    var ratingTargetType by remember { mutableStateOf("STORE") }

    if (showRegistrationScreen) {
        ProviderRegistrationScreen(
            viewModel = viewModel,
            onNavigateBack = { showRegistrationScreen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isStoreMode) "🧩 MODO REPUESTERA" else "📦 PEDIR REPUESTOS",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = PartColors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = PartColors.cyanAccent)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (isStoreMode) "Repuestera" else "Cliente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStoreMode) PartColors.orangeAccent else PartColors.cyanAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isStoreMode,
                            onCheckedChange = { isStoreMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PartColors.orangeAccent,
                                checkedTrackColor = PartColors.orangeAccent.copy(alpha = 0.3f),
                                uncheckedThumbColor = PartColors.cyanAccent,
                                uncheckedTrackColor = PartColors.cyanAccent.copy(alpha = 0.3f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PartColors.darkBackground)
            )
        },
        containerColor = PartColors.darkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isStoreMode) {
                if (isStoreRegistered) {
                    StoreWorkspaceView(
                        openRequests = openRequests,
                        viewModel = viewModel,
                        context = context,
                        onCompleteService = { requestId, targetId ->
                            // Parts delivered
                            ratingTargetId = targetId
                            ratingTargetType = "CLIENT"
                            showRatingDialog = true
                        }
                    )
                } else {
                    // Blocked View - Requires Parts Store Registration
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🧩", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "MODO REPUESTERA EXCLUSIVO",
                            color = PartColors.greenAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Para recibir solicitudes de repuestos de otros usuarios y enviar cotizaciones de autopartes, debes registrarte como repuestera verificado en MEET.",
                            color = PartColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showRegistrationScreen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PartColors.greenAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("REGISTRAR MI TIENDA DE REPUESTOS", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Client Workspace - filter to only show their own requests
                val userVehicleIds = vehicles.map { it.id }
                val clientRequests = allRequests.filter { it.vehicleId in userVehicleIds }
                
                ClientWorkspaceView(
                    viewModel = viewModel,
                    allRequests = clientRequests,
                    prefilledVehicleInfo = prefilledVehicleInfo,
                    prefilledAtlasPartId = prefilledAtlasPartId,
                    context = context,
                    onCompleteService = { requestId, storeId ->
                        ratingTargetId = storeId ?: "store"
                        ratingTargetType = "STORE"
                        showRatingDialog = true
                    }
                )
            }

            if (showRatingDialog) {
                RatingSubmissionDialog(
                    targetType = ratingTargetType,
                    targetId = ratingTargetId,
                    onDismiss = { showRatingDialog = false },
                    onSubmit = { stars, comment ->
                        viewModel.submitRating(
                            targetType = ratingTargetType,
                            targetId = ratingTargetId,
                            sourceName = if (isStoreMode) "Repuestera" else "Cliente",
                            stars = stars,
                            comment = comment
                        )
                        showRatingDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ClientWorkspaceView(
    viewModel: ObdViewModel,
    allRequests: List<PartRequestEntity>,
    prefilledVehicleInfo: String?,
    prefilledAtlasPartId: String?,
    context: Context,
    onCompleteService: (String, String?) -> Unit
) {
    val autoVehicleInfo = remember { viewModel.buildVehicleInfoForRequest() }
    val vehicleInfoToUse = prefilledVehicleInfo ?: autoVehicleInfo
    val canonicalPart = remember(context, prefilledAtlasPartId) {
        prefilledAtlasPartId?.let { canonicalId ->
            runCatching { CanonicalVehiclePartRepository(context).find(canonicalId) }
                .getOrNull()
                ?.takeIf { it.element.commerce.directlySellable }
        }
    }
    val atlasElement = canonicalPart?.element
    val atlasManifest = remember(context, canonicalPart) {
        canonicalPart?.let { part ->
            runCatching {
                if (part.element.canonicalId.startsWith("g4ed-")) {
                    G4edAtlas3dRepository(context).manifest(part.element.visual.packId)
                } else {
                    val domainId = requireNotNull(
                        VehicleTechnicalAtlasDescriptors
                            .forCanonicalId(part.element.canonicalId),
                    ).domainId
                    VehicleTechnicalAtlas3dRepository(context)
                        .manifest(domainId, part.element.visual.packId)
                }
            }.getOrNull()
        }
    }
    val atlasBinding = remember(atlasElement, atlasManifest) {
        if (atlasElement == null || atlasManifest == null) {
            null
        } else {
            if (atlasElement.canonicalId.startsWith("g4ed-")) {
                G4edAtlas3dCatalog.bindingFor(atlasElement, atlasManifest)
            } else {
                VehicleTechnicalAtlas3dCatalog.bindingFor(atlasElement, atlasManifest)
            }
        }
    }

    var partName by remember(atlasElement) { mutableStateOf(atlasElement?.nameOriginal.orEmpty()) }
    var partNumber by remember { mutableStateOf("") }
    var partCategory by remember(atlasElement) { mutableStateOf(atlasElement?.systemId.orEmpty()) }
    var sourceContext by remember(atlasElement) {
        mutableStateOf(if (atlasElement == null) "MANUAL" else "FROM_3D_COMPONENT")
    }
    var quantity by remember { mutableStateOf(1) }
    var partPosition by remember { mutableStateOf("N/A") }
    var oemPreference by remember { mutableStateOf("ANY") }
    var locationName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+506 ") }
    var customerNotes by remember(atlasElement) {
        mutableStateOf(
            atlasElement?.let {
                "Referencia canónica MEET: ${it.canonicalId}. " +
                    "Atlas: ${canonicalPart?.atlasDisplayName}. " +
                    "Reconstrucción 3D no dimensional; confirmar VIN, OEM, foto, conector y medidas."
            }.orEmpty(),
        )
    }
    var latText by remember { mutableStateOf("9.9281") }
    var lngText by remember { mutableStateOf("-84.0907") }

    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val activeDtcCodes by viewModel.activeDtcs.collectAsState()
    val repairKnowledgeState by rememberRepairKnowledgeUiState(
        vehicle = selectedVehicle,
        dtcs = activeDtcCodes
    )
    val repairBundle = (repairKnowledgeState as? RepairKnowledgeUiState.Ready)?.bundle
    LaunchedEffect(activeDtcCodes.joinToString()) {
        if (activeDtcCodes.isNotEmpty() && sourceContext == "MANUAL") {
            sourceContext = "FROM_DTC"
        }
    }
    val partSuggestions = remember(activeDtcCodes, repairBundle) {
        val input = PartSuggestionInput(
            source = SuggestionSource.DTC,
            dtcCodes = activeDtcCodes
        )
        repairBundle?.let { PartSuggestionEngine.suggestParts(input, it) }
            ?: PartSuggestionEngine.suggestParts(input)
    }
    val compatibilityResult = remember(selectedVehicle, partName, partNumber, partPosition, oemPreference, activeDtcCodes) {
        if (partName.isBlank()) {
            null
        } else {
            CompatibilityEngine.evaluate(
                CompatibilityContext(
                    vehicle = selectedVehicle.toPartsFingerprint(partNumber, oemPreference),
                    partName = partName,
                    position = legacyPositionToPartPosition(partPosition),
                    dtcCodes = activeDtcCodes
                )
            )
        }
    }
    val selectedGraphSuggestion = partSuggestions.firstOrNull {
        it.partName.equals(partName.trim(), ignoreCase = true)
    }
    val publicationDecision = PartRequestPublicationPolicy.evaluate(
        partName = partName,
        vehiclePresent = selectedVehicle != null,
        contactPresent = phone.isNotBlank(),
        graphEvidenceRequired = sourceContext in setOf("FROM_DTC", "FROM_3D_COMPONENT"),
        compatibility = compatibilityResult,
        suggestion = selectedGraphSuggestion,
        knowledge = repairBundle,
        canonicalReferenceId = atlasElement?.canonicalId,
    )
    val canPublishPartRequest = publicationDecision.allowed

    LaunchedEffect(Unit) {
        viewModel.detectCurrentLocation(context)
    }

    LaunchedEffect(currentGps) {
        currentGps?.let { gps ->
            latText = gps.latitude.toString()
            lngText = gps.longitude.toString()
            locationName = gps.addressName
            phone = "${gps.dialingPrefix} "
        }
    }

    val myRequests = remember(allRequests, selectedVehicle) {
        val vehicleId = selectedVehicle?.id ?: ""
        allRequests.filter { it.vehicleId == vehicleId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                border = BorderStroke(1.dp, PartColors.cyanAccent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚗 DETALLES DEL VEHÍCULO",
                        fontWeight = FontWeight.Bold,
                        color = PartColors.cyanAccent,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = vehicleInfoToUse,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (atlasElement != null && atlasManifest != null && atlasBinding != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                    border = BorderStroke(1.dp, PartColors.greenAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "SHOWROOM 3D DE REFERENCIA",
                            color = PartColors.greenAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                        )
                        Text(
                            "${atlasElement.nameOriginal} · ${atlasElement.canonicalId}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        G4edPartViewer(atlasManifest, atlasBinding)
                        Text(
                            "Compare esta reconstrucción con fotos reales del vendedor. La similitud visual no confirma compatibilidad exacta.",
                            color = PartColors.orangeAccent,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        }

        if (partSuggestions.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                    border = BorderStroke(1.dp, PartColors.cyanAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SUGERENCIAS POR DTC ACTIVO",
                            color = PartColors.cyanAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                        partSuggestions.take(5).forEach { suggestion ->
                            Surface(
                                color = if (suggestion.riskPart) {
                                    PartColors.redAccent.copy(alpha = 0.08f)
                                } else {
                                    Color(0xFF1E293B)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (suggestion.riskPart) PartColors.redAccent.copy(alpha = 0.4f) else PartColors.borderSubtle
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        partName = suggestion.partName
                                        partCategory = suggestion.category
                                        sourceContext = "FROM_DTC"
                                        partPosition = PartsMarketplaceContract.positionToLegacy(suggestion.position.name)
                                        val extraNotes = listOfNotNull(
                                            suggestion.rationale,
                                            suggestion.disclaimer
                                        ).joinToString(" ")
                                        if (extraNotes.isNotBlank() && !customerNotes.contains(extraNotes)) {
                                            customerNotes = listOf(customerNotes, extraNotes)
                                                .filter { it.isNotBlank() }
                                                .joinToString("\n")
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = suggestion.partName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (suggestion.riskPart) {
                                            Text(
                                                text = "RIESGO",
                                                color = PartColors.redAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                    Text(
                                        text = suggestion.rationale,
                                        color = PartColors.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                    suggestion.disclaimer?.let {
                                        Text(
                                            text = it,
                                            color = PartColors.orangeAccent,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            RepairKnowledgeEvidencePanel(
                state = repairKnowledgeState,
                accentColor = PartColors.cyanAccent
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                border = BorderStroke(1.dp, PartColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧩 PEDIR REPUESTO A LA RED TÉCNICA",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PartRequestStepHeader(
                        step = 1,
                        title = "Identificar pieza",
                        subtitle = "Nombre, categoría, número opcional, DTC u origen 3D/mecánico"
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Origen de solicitud:",
                        color = PartColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        listOf(
                            "MANUAL" to "Manual",
                            "FROM_DTC" to "DTC",
                            "FROM_3D_COMPONENT" to "3D",
                            "FROM_MECHANIC_WORK_ORDER" to "Mecánico"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = sourceContext == value,
                                onClick = { sourceContext = value },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PartColors.cyanAccent,
                                    selectedLabelColor = Color.Black,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = partName,
                        onValueChange = { partName = it },
                        label = { Text("Nombre de la pieza requerida") },
                        placeholder = { Text("Ej. Pastillas de freno delanteras / Sensor MAP") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = partCategory,
                        onValueChange = { partCategory = it },
                        label = { Text("Categoría técnica") },
                        placeholder = { Text("Ej. ELECTRICAL / ENGINE / BRAKES") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = partNumber,
                        onValueChange = { partNumber = it },
                        label = { Text("Número de parte (Opcional)") },
                        placeholder = { Text("Ej. 37880-PLC-004") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Quantity stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unidades requeridas:",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = { if (quantity > 1) quantity-- },
                                modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos", tint = Color.White)
                            }
                            Text(
                                text = quantity.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            IconButton(
                                onClick = { if (quantity < 10) quantity++ },
                                modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Más", tint = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    PartRequestStepHeader(
                        step = 2,
                        title = "Compatibilidad",
                        subtitle = "Vehículo activo, VIN si existe, posición, OEM y advertencias"
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Part position grid
                    Text(
                        text = "Posición de la pieza en el vehículo:",
                        color = PartColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val positions = listOf(
                            listOf("DELANTERA_DERECHA" to "Delantera Der.", "DELANTERA_IZQUIERDA" to "Delantera Izq."),
                            listOf("TRASERA_DERECHA" to "Trasera Der.", "TRASERA_IZQUIERDA" to "Trasera Izq."),
                            listOf("CENTRAL" to "Central", "N/A" to "N/A / Motor / Otro")
                        )
                        positions.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { (value, label) ->
                                    FilterChip(
                                        selected = partPosition == value,
                                        onClick = { partPosition = value },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PartColors.cyanAccent,
                                            selectedLabelColor = Color.Black,
                                            labelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // OEM Preference
                    Text(
                        text = "Preferencia de fabricante:",
                        color = PartColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        listOf("ANY" to "Cualquiera", "OEM" to "Original (OEM)", "AFTERMARKET" to "Genérico").forEach { (value, label) ->
                            FilterChip(
                                selected = oemPreference == value,
                                onClick = { oemPreference = value },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PartColors.cyanAccent,
                                    selectedLabelColor = Color.Black,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    compatibilityResult?.let {
                        CompatibilityResultPanel(
                            result = it,
                            accentColor = PartColors.cyanAccent,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    PartRequestStepHeader(
                        step = 3,
                        title = "Entrega",
                        subtitle = "Dirección aproximada, pickup o delivery, contacto y urgencia"
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        label = { Text("📍 Lugar de entrega / Taller / Casa") },
                        placeholder = { Text("Ej. Taller El Centauro, Escazú") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitud") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PartColors.cyanAccent,
                                unfocusedBorderColor = PartColors.borderSubtle,
                                focusedLabelColor = PartColors.cyanAccent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lngText,
                            onValueChange = { lngText = it },
                            label = { Text("Longitud") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PartColors.cyanAccent,
                                unfocusedBorderColor = PartColors.borderSubtle,
                                focusedLabelColor = PartColors.cyanAccent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("📱 Teléfono / WhatsApp") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    PartRequestStepHeader(
                        step = 4,
                        title = "Publicar solicitud",
                        subtitle = "Resumen, notas, advertencias y envío a repuesteras"
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customerNotes,
                        onValueChange = { customerNotes = it },
                        label = { Text("Notas de compatibilidad / Detalles") },
                        placeholder = { Text("Ej. Motor 2.0L automático de 4 puertas") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    PartRequestSummaryPanel(
                        sourceContext = sourceContext,
                        partCategory = partCategory,
                        dtcCodes = activeDtcCodes,
                        partName = partName,
                        partNumber = partNumber,
                        oemPreference = oemPreference,
                        partPosition = partPosition,
                        compatibilityResult = compatibilityResult
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (!canPublishPartRequest) {
                                Toast.makeText(
                                    context,
                                    "Solicitud bloqueada: complete evidencia y compatibilidad antes de publicar.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            val parsedLat = latText.toDoubleOrNull() ?: 0.0
                            val parsedLng = lngText.toDoubleOrNull() ?: 0.0
                            val vehicleId = selectedVehicle?.id ?: "demo_vehicle"
                            
                            viewModel.createPartRequest(
                                serviceRequestId = null,
                                vehicleId = vehicleId,
                                dtcCode = viewModel.activeDtcs.value.firstOrNull(),
                                partName = partName,
                                partNumber = partNumber.takeIf { it.isNotBlank() },
                                quantity = quantity,
                                oemPreference = oemPreference,
                                deliveryLocation = locationName,
                                urgencyMinutes = 60, // Default hidden ETA
                                customerNotes = buildPartRequestNotes(
                                    sourceContext = sourceContext,
                                    category = partCategory,
                                    notes = customerNotes,
                                    compatibilityResult = compatibilityResult,
                                    dtcCodes = activeDtcCodes
                                ),
                                partPosition = partPosition,
                                phone = phone,
                                latitude = parsedLat,
                                longitude = parsedLng
                            )

                            Toast.makeText(context, "✅ Solicitud de repuesto publicada en la red", Toast.LENGTH_SHORT).show()
                            partName = ""
                            partNumber = ""
                            partCategory = ""
                            customerNotes = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PartColors.cyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canPublishPartRequest
                    ) {
                        Text(
                            text = "🧩 ENVIAR SOLICITUD A RED DE REPUESTERAS",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                    if (!canPublishPartRequest && partName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = publicationDecision.reasons.joinToString(" "),
                            color = PartColors.redAccent,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        if (myRequests.isNotEmpty()) {
            item {
                Text(
                    text = "MIS SOLICITUDES DE REPUESTOS",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(myRequests) { req ->
                ClientRequestCard(
                    request = req,
                    viewModel = viewModel,
                    context = context,
                    onComplete = { storeId -> onCompleteService(req.requestId, storeId) }
                )
            }
        }
    }
}

@Composable
private fun PartRequestStepHeader(
    step: Int,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = PartColors.cyanAccent.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, PartColors.cyanAccent.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = step.toString(),
                color = PartColors.cyanAccent,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(Locale.getDefault()),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
            Text(
                text = subtitle,
                color = PartColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun PartRequestSummaryPanel(
    sourceContext: String,
    partCategory: String,
    dtcCodes: List<String>,
    partName: String,
    partNumber: String,
    oemPreference: String,
    partPosition: String,
    compatibilityResult: CompatibilityResult?
) {
    val hasBlock = compatibilityResult?.warnings?.any { it.severity == WarningSeverity.BLOCK } == true
    Surface(
        color = if (hasBlock) PartColors.redAccent.copy(alpha = 0.08f) else Color(0xFF0F172A),
        border = BorderStroke(
            1.dp,
            if (hasBlock) PartColors.redAccent.copy(alpha = 0.45f) else PartColors.cyanAccent.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "RESUMEN ANTES DE PUBLICAR",
                color = if (hasBlock) PartColors.redAccent else PartColors.cyanAccent,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )
            Text(
                text = listOf(
                    "Origen: ${sourceLabel(sourceContext)}",
                    partCategory.takeIf { it.isNotBlank() }?.let { "Categoría: $it" },
                    dtcCodes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "DTC: "),
                    partName.takeIf { it.isNotBlank() }?.let { "Pieza: $it" },
                    partNumber.takeIf { it.isNotBlank() }?.let { "N/Parte: $it" },
                    "Preferencia: $oemPreference",
                    "Posición: $partPosition"
                ).filterNotNull().joinToString("\n"),
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            compatibilityResult?.let { result ->
                Text(
                    text = "Confianza: ${result.confidence}. ${CompatibilityEngine.describeVerdict(result)}",
                    color = compatibilityColor(result.confidence),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                result.warnings.take(2).forEach { warning ->
                    Text(
                        text = "${warning.severity}: ${warning.message}",
                        color = if (warning.severity == WarningSeverity.BLOCK) PartColors.redAccent else PartColors.orangeAccent,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

private fun sourceLabel(sourceContext: String): String = when (sourceContext) {
    "FROM_DTC" -> "DTC"
    "FROM_3D_COMPONENT" -> "Pieza 3D"
    "FROM_MECHANIC_WORK_ORDER" -> "Orden de mecánico"
    "FROM_MAINTENANCE_ALERT" -> "Alerta mantenimiento"
    "FROM_PREPURCHASE_INSPECTION" -> "Peritaje"
    else -> "Manual"
}

private fun buildPartRequestNotes(
    sourceContext: String,
    category: String,
    notes: String,
    compatibilityResult: CompatibilityResult?,
    dtcCodes: List<String>
): String = buildString {
    appendLine("[MEET_PART_MARKETPLACE]")
    appendLine("source_context=$sourceContext")
    if (category.isNotBlank()) appendLine("category=$category")
    if (dtcCodes.isNotEmpty()) appendLine("dtc_codes=${dtcCodes.joinToString()}")
    compatibilityResult?.let { result ->
        appendLine("compatibility_confidence=${result.confidence}")
        result.requiredConfirmations.take(3).forEach { appendLine("required_confirmation=$it") }
        result.warnings.take(3).forEach { appendLine("warning=${it.severity}:${it.code}:${it.message}") }
    }
    appendLine("[/MEET_PART_MARKETPLACE]")
    if (notes.isNotBlank()) {
        appendLine()
        append(notes.trim())
    }
}.trim()

@Composable
private fun ClientRequestCard(
    request: PartRequestEntity,
    viewModel: ObdViewModel,
    context: Context,
    onComplete: (String?) -> Unit
) {
    val offersFlow = remember(request.requestId) { viewModel.getPartOffersForRequest(request.requestId) }
    val offers by offersFlow.collectAsState(initial = emptyList())
    val rankedOffers = remember(request, offers) { rankOffersForRequest(request, offers) }

    Card(
        colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
        border = BorderStroke(1.dp, if (request.status == "OPEN") PartColors.orangeAccent.copy(alpha = 0.3f) else PartColors.cyanAccent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (request.status == "OPEN") PartColors.orangeAccent.copy(alpha = 0.15f) else PartColors.cyanAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (request.status == "OPEN") "BUSCANDO OFERTAS" else "COMPRA REALIZADA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (request.status == "OPEN") PartColors.orangeAccent else PartColors.cyanAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "${request.quantity} unidades",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = request.partName,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp
            )

            val cleanNotes = request.customerNotes.substringBefore(" [Posición:")
            val embeddedPositionText = request.customerNotes.substringAfter("[Posición: ").substringBefore("]")
            val positionText = request.partPosition
                .takeIf { it.isNotBlank() && it != "N/A" }
                ?: embeddedPositionText

            if (positionText.isNotBlank() && positionText != request.customerNotes) {
                Text(
                    text = "Posición: $positionText",
                    color = PartColors.cyanAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (cleanNotes.isNotBlank()) {
                Text(
                    text = "Detalles: $cleanNotes",
                    color = PartColors.textSecondary,
                    fontSize = 13.sp
                )
            }
            if (request.partNumber != null) {
                Text(
                    text = "N/Parte: ${request.partNumber}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
            if (request.phone.isNotBlank()) {
                Text(
                    text = "Contacto: ${request.phone}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "📥 Ofertas recibidas de repuesteras (${rankedOffers.size}):",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )

            if (rankedOffers.isEmpty()) {
                Text(
                    text = "Esperando ofertas de repuestos locales...",
                    color = PartColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                rankedOffers.forEach { ranked ->
                    val offer = ranked.offer
                    val isSelected = request.acceptedOfferId == offer.offerId
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, if (isSelected) PartColors.greenAccent else PartColors.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = offer.storeName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = String.format("₡%,.0f CRC", offer.price),
                                    fontWeight = FontWeight.Black,
                                    color = PartColors.greenAccent,
                                    fontSize = 14.sp
                                )
                            }
                            quoteTagLabel(ranked.tag)?.let { tag ->
                                Surface(
                                    color = compatibilityColor(ranked.confidence).copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = "$tag · ${ranked.confidence} · ${(ranked.score * 100).toInt()} pts",
                                        color = compatibilityColor(ranked.confidence),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Marca: ${offer.brand} | Condición: ${offer.condition}",
                                color = PartColors.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "ETA: ${offer.etaMinutes} min | Garantía: ${offer.warrantyDays} días",
                                color = PartColors.textSecondary,
                                fontSize = 12.sp
                            )
                            if (offer.message.isNotBlank()) {
                                Text(
                                    text = "\"${offer.message}\"",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (request.status == "OPEN") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.acceptPartOffer(request.requestId, offer.offerId, context)
                                        onComplete(offer.storeId)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PartColors.cyanAccent),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🤝 ELEGIR Y COMPRAR ESTE REPUESTO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else if (isSelected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "✅ OFERTA ELEGIDA Y COMPRADA",
                                    color = PartColors.greenAccent,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreWorkspaceView(
    openRequests: List<PartRequestEntity>,
    viewModel: ObdViewModel,
    context: Context,
    onCompleteService: (String, String) -> Unit
) {
    var storeName by remember { mutableStateOf("Repuestos El Atlántico") }
    var storePhone by remember { mutableStateOf("+506 7777 7777") }

    val storeId = "store_101"

    var offeringRequestId by remember { mutableStateOf<String?>(null) }
    var brand by remember { mutableStateOf("") }
    var partNumberOffer by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("NEW") }
    var priceOfferCrc by remember { androidx.compose.runtime.mutableFloatStateOf(15000.0f) }
    var warrantyDays by remember { androidx.compose.runtime.mutableFloatStateOf(30.0f) }
    var offerMessage by remember { mutableStateOf("") }
    var safetyInstallConfirmed by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                border = BorderStroke(1.dp, PartColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧩 CONFIGURAR MI REPUESTERA",
                        color = PartColors.orangeAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Nombre de la Distribuidora / Repuestera") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.orangeAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.orangeAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = storePhone,
                        onValueChange = { storePhone = it },
                        label = { Text("Teléfono / WhatsApp") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.orangeAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.orangeAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Text(
                text = "📥 PEDIDOS DE REPUESTOS DISPONIBLES EN COSTA RICA",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
        }

        if (openRequests.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No hay solicitudes de repuestos abiertas actualmente.",
                        color = PartColors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(openRequests) { req ->
                val isOffering = offeringRequestId == req.requestId

                val cleanNotes = req.customerNotes.substringBefore(" [Posición:")
                val embeddedPositionText = req.customerNotes.substringAfter("[Posición: ").substringBefore("]")
                val positionText = req.partPosition
                    .takeIf { it.isNotBlank() && it != "N/A" }
                    ?: embeddedPositionText

                Card(
                    colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                    border = BorderStroke(1.dp, if (isOffering) PartColors.orangeAccent else PartColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = req.partName,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${req.quantity} piezas",
                                color = PartColors.orangeAccent,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }

                        if (positionText.isNotBlank() && positionText != req.customerNotes) {
                            Text(
                                text = "Lado/Posición: $positionText",
                                color = PartColors.cyanAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (cleanNotes.isNotBlank()) {
                            Text(
                                text = "Detalles: $cleanNotes",
                                color = PartColors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                        if (req.partNumber != null) {
                            Text(
                                text = "Código/N/Parte: ${req.partNumber}",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "Compatibilidad OEM: ${req.oemPreference}",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.openWaze(context, req.latitude, req.longitude) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🌐 Abrir Waze", color = Color.White, fontSize = 10.sp)
                            }
                            Button(
                                onClick = { viewModel.shareLocationViaWhatsApp(context, req.latitude, req.longitude, req.partName) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📱 WhatsApp", color = Color.White, fontSize = 10.sp)
                            }
                        }

                        if (!isOffering) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    offeringRequestId = req.requestId
                                    brand = ""
                                    partNumberOffer = req.partNumber ?: ""
                                    condition = "NEW"
                                    priceOfferCrc = 15000.0f
                                    warrantyDays = 30f
                                    offerMessage = ""
                                    safetyInstallConfirmed = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PartColors.orangeAccent),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("OFERTA COMO REPUESTERA · ELYSIUM", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val compatibilityResult = remember(req, partNumberOffer) {
                                CompatibilityEngine.evaluate(
                                    requestCompatibilityContext(
                                        request = req,
                                        offeredPartNumber = partNumberOffer
                                    )
                                )
                            }
                            val compatibilityNotes = remember(compatibilityResult) {
                                compatibilityNotesFor(compatibilityResult)
                            }
                            val quoteValidation = remember(req, brand, partNumberOffer, condition, priceOfferCrc, warrantyDays, compatibilityResult, compatibilityNotes) {
                                QuoteValidator.validate(
                                    QuoteValidator.buildQuote(
                                        DraftQuote(
                                            partName = req.partName,
                                            brand = brand,
                                            partNumber = partNumberOffer,
                                            oemNumber = req.partNumber?.takeIf { req.oemPreference.equals("OEM", ignoreCase = true) },
                                            condition = legacyConditionToPartCondition(condition),
                                            availability = PartAvailability.SAME_DAY,
                                            price = priceOfferCrc.toDouble(),
                                            currency = "CRC",
                                            includesDelivery = false,
                                            deliveryFee = 0.0,
                                            estimatedDeliveryHours = 1,
                                            warrantyDays = warrantyDays.toInt(),
                                            photoUrls = emptyList(),
                                            compatibilityConfidence = compatibilityResult.confidence,
                                            compatibilityNotes = compatibilityNotes,
                                            expiresInHours = 24
                                        )
                                    )
                                )
                            }
                            val requiresSafetyConfirmation = compatibilityResult.warnings.any {
                                it.code == "CRITICAL_SAFETY_PART" || it.severity == WarningSeverity.BLOCK
                            }
                            val canSendQuote = brand.isNotBlank() &&
                                quoteValidation.level != ValidationLevel.BLOCK &&
                                (!requiresSafetyConfirmation || safetyInstallConfirmed)

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = PartColors.orangeAccent.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "💵 COTIZACIÓN DE REPUESTO",
                                fontWeight = FontWeight.Bold,
                                color = PartColors.orangeAccent,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("Marca del repuesto ofrecido") },
                                placeholder = { Text("Ej. Denso / Bosch / OEM Honda") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PartColors.orangeAccent,
                                    unfocusedBorderColor = PartColors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = partNumberOffer,
                                onValueChange = { partNumberOffer = it },
                                label = { Text("Número de parte ofrecido") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PartColors.orangeAccent,
                                    unfocusedBorderColor = PartColors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Condición:",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("NEW" to "Nuevo", "OEM" to "Original Usado", "REMAN" to "Reconstruido").forEach { (value, label) ->
                                    FilterChip(
                                        selected = condition == value,
                                        onClick = { condition = value },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PartColors.orangeAccent,
                                            selectedLabelColor = Color.White,
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = String.format("Precio: ₡%,.0f CRC", priceOfferCrc),
                                fontWeight = FontWeight.Black,
                                color = PartColors.greenAccent,
                                fontSize = 16.sp
                            )
                            Slider(
                                value = priceOfferCrc,
                                onValueChange = { priceOfferCrc = it },
                                valueRange = 1000f..150000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = PartColors.orangeAccent,
                                    activeTrackColor = PartColors.orangeAccent
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Garantía: ${warrantyDays.toInt()} días",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = warrantyDays,
                                onValueChange = { warrantyDays = it },
                                valueRange = 0f..360f,
                                steps = 11,
                                colors = SliderDefaults.colors(
                                    thumbColor = PartColors.orangeAccent,
                                    activeTrackColor = PartColors.orangeAccent
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = offerMessage,
                                onValueChange = { offerMessage = it },
                                label = { Text("Comentarios o detalles de entrega") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PartColors.orangeAccent,
                                    unfocusedBorderColor = PartColors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            CompatibilityResultPanel(
                                result = compatibilityResult,
                                accentColor = PartColors.orangeAccent
                            )

                            QuoteValidationPanel(
                                validation = quoteValidation,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            if (requiresSafetyConfirmation) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = safetyInstallConfirmed,
                                        onCheckedChange = { safetyInstallConfirmed = it },
                                        colors = CheckboxDefaults.colors(checkedColor = PartColors.orangeAccent)
                                    )
                                    Text(
                                        text = "Confirmo que esta pieza requiere verificación e instalación por técnico calificado.",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { offeringRequestId = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancelar", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        viewModel.placePartOffer(
                                            partRequestId = req.requestId,
                                            storeName = storeName,
                                            brand = brand,
                                            partNumber = partNumberOffer,
                                            condition = condition,
                                            price = priceOfferCrc.toDouble(),
                                            deliveryFee = 0.0,
                                            etaMinutes = 60,
                                            warrantyDays = warrantyDays.toInt(),
                                            message = offerMessage,
                                            compatibilityConfidence = compatibilityResult.confidence.name,
                                            compatibilityNotes = compatibilityNotes
                                        )
                                        Toast.makeText(context, "✅ Oferta enviada al cliente", Toast.LENGTH_SHORT).show()
                                        offeringRequestId = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PartColors.orangeAccent),
                                    modifier = Modifier.weight(1f),
                                    enabled = canSendQuote
                                ) {
                                    Text("Enviar", color = Color.White, fontWeight = FontWeight.Bold)
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
private fun RatingSubmissionDialog(
    targetType: String,
    targetId: String,
    onDismiss: () -> Unit,
    onSubmit: (Double, String) -> Unit
) {
    var ratingStars by remember { mutableStateOf(5.0) }
    var commentText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
            border = BorderStroke(1.dp, PartColors.cyanAccent.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⭐ CALIFICAR PIEZA / SERVICIO",
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¿Cómo calificarías este repuesto y entrega?",
                    color = PartColors.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..5) {
                        val isFilled = ratingStars >= i.toDouble()
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Estrella $i",
                            tint = if (isFilled) PartColors.cyanAccent else Color.Gray,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { ratingStars = i.toDouble() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Comentarios adicionales") },
                    placeholder = { Text("Ej. Excelente calidad y rápido.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PartColors.cyanAccent,
                        unfocusedBorderColor = PartColors.borderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Omitir", color = Color.White)
                    }
                    Button(
                        onClick = { onSubmit(ratingStars, commentText) },
                        colors = ButtonDefaults.buttonColors(containerColor = PartColors.cyanAccent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enviar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
