package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome

import com.elysium369.meet.ui.components.AnimatedNeonGlyph

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.elysium369.meet.core.billing.GaugeBillingManager
import com.elysium369.meet.core.billing.GooglePlayPurchaseVerifier
import com.elysium369.meet.core.billing.PlayBillingCatalog
import com.elysium369.meet.core.monetization.MonetizationPolicy
import com.elysium369.meet.data.local.entities.GaugeConfig
import com.elysium369.meet.data.local.entities.SavedGaugeEntity
import com.elysium369.meet.data.supabase.GaugeListing
import com.elysium369.meet.data.supabase.GaugePriceTiers
import com.elysium369.meet.ui.GaugeMarketTab
import com.elysium369.meet.ui.GaugeMarketplaceViewModel
import com.elysium369.meet.ui.components.gauges.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

// ═══════════════════════════════════════════════════════
// GAUGE MARKETPLACE SCREEN
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaugeMarketplaceScreen(
    navController: NavController,
    gaugeStyleManager: GaugeStyleManager,
    initialPublishGaugeId: String? = null,
    viewModel: GaugeMarketplaceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val billingManager = remember(appContext) { GaugeBillingManager(appContext) }
    val purchaseVerifier = remember(appContext) { GooglePlayPurchaseVerifier(appContext) }
    val isBillingConnected by billingManager.isConnected.collectAsState()
    val isBillingProcessing by billingManager.isProcessing.collectAsState()
    var selectedListing by remember { mutableStateOf<GaugeListing?>(null) }
    var showPreviewSheet by remember { mutableStateOf(false) }
    var purchaseTarget by remember { mutableStateOf<GaugeListing?>(null) }
    var purchaseVerificationListingId by remember { mutableStateOf<String?>(null) }
    var showPublishDialog by remember { mutableStateOf(initialPublishGaugeId != null) }
    var publishSourceGauge by remember { mutableStateOf<SavedGaugeEntity?>(null) }

    val inf = rememberInfiniteTransition(label = "market")
    val headerGlow by inf.animateFloat(
        0.3f, 0.9f,
        infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "headerGlow"
    )
    val accentCyan = Color(0xFF00FFCC)
    val accentPurple = Color(0xFF7C4DFF)
    val isPurchaseFlowBusy =
        isBillingProcessing ||
            purchaseTarget != null ||
            purchaseVerificationListingId != null ||
            uiState.purchaseRecordingListingId != null

    DisposableEffect(billingManager) {
        if (MonetizationPolicy.PAYWALLS_ENABLED) {
            billingManager.onPurchaseCompleted = { purchaseToken, productId ->
                val listing = purchaseTarget
                purchaseTarget = null
                if (listing != null) {
                    purchaseVerificationListingId = listing.id
                    scope.launch {
                        try {
                            val verification = purchaseVerifier.verify(
                                productId = productId,
                                productType = PlayBillingCatalog.productType(productId),
                                purchaseToken = purchaseToken
                            )
                            if (verification.isSuccess) {
                                viewModel.recordPurchase(listing, purchaseToken)
                                snackbarHostState.showSnackbar("Compra verificada por Google Play. El gauge quedo registrado en tu cuenta.")
                            } else {
                                snackbarHostState.showSnackbar("Google Play confirmo el pago, pero el backend no pudo verificarlo: ${verification.exceptionOrNull()?.message.orEmpty()}")
                            }
                        } finally {
                            purchaseVerificationListingId = null
                        }
                    }
                }
            }
            billingManager.onPurchaseError = { message ->
                purchaseTarget = null
                purchaseVerificationListingId = null
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                }
            }
            billingManager.connect()
            onDispose {
                billingManager.onPurchaseCompleted = null
                billingManager.onPurchaseError = null
                billingManager.disconnect()
            }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    LaunchedEffect(showPreviewSheet, selectedListing?.id) {
        if (showPreviewSheet) {
            viewModel.ensureOwnership(selectedListing?.id)
            viewModel.ensureReviews(selectedListing?.id)
        }
    }

    LaunchedEffect(initialPublishGaugeId) {
        val id = initialPublishGaugeId?.takeIf { it.isNotBlank() }
        if (id == null) return@LaunchedEffect
        if (id == "draft") {
            publishSourceGauge = null
            showPublishDialog = true
            return@LaunchedEffect
        }
        publishSourceGauge = withContext(Dispatchers.IO) {
            val db = androidx.room.Room.databaseBuilder(
                appContext,
                com.elysium369.meet.data.local.MeetDatabase::class.java,
                "meet_database"
            ).build()
            try {
                db.savedGaugeDao().getById(id)
            } finally {
                db.close()
            }
        }
        showPublishDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050810),
                        Color(0xFF0A0E1A),
                        Color(0xFF060812)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ══════════════════════════════════════
            // HEADER
            // ══════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Animated gradient header bar
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    accentCyan.copy(alpha = headerGlow * 0.08f),
                                    accentPurple.copy(alpha = headerGlow * 0.05f),
                                    accentCyan.copy(alpha = headerGlow * 0.08f)
                                )
                            )
                        )
                        // Bottom glow line
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accentCyan.copy(alpha = headerGlow * 0.5f),
                                    accentPurple.copy(alpha = headerGlow * 0.4f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2f
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.backOrHome() }) {
                            AnimatedNeonIcon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            "🎨 Gauge Market",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "Crear nuevo gauge" action button at the top
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentCyan.copy(alpha = 0.15f))
                                .border(1.5.dp, accentCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable {
                                    publishSourceGauge = null
                                    showPublishDialog = true
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🚀", fontSize = 11.sp)
                                Text(
                                    "Crear",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        // Earnings badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF00C853).copy(alpha = 0.15f),
                                            Color(0xFF00E676).copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "💎 ${formatUsdFromCents(uiState.creatorEarningsCents)}",
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ══════════════════════════════════════
            // TABS
            // ══════════════════════════════════════
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GaugeMarketTab.entries.size) { index ->
                    val tab = GaugeMarketTab.entries[index]
                    val isSelected = tab == uiState.selectedTab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) Brush.horizontalGradient(
                                    colors = listOf(
                                        accentCyan.copy(alpha = 0.2f),
                                        accentPurple.copy(alpha = 0.15f)
                                    )
                                ) else Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0x15FFFFFF),
                                        Color(0x0AFFFFFF)
                                    )
                                )
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(accentCyan.copy(alpha = 0.5f), accentPurple.copy(alpha = 0.4f))
                                    ),
                                    RoundedCornerShape(16.dp)
                                ) else Modifier
                            )
                            .clickable { viewModel.selectTab(tab) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            tab.label,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ══════════════════════════════════════
            // CONTENT
            // ══════════════════════════════════════
            if (uiState.isLoading) {
                // Loading shimmer
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = accentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Cargando marketplace...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (uiState.listings.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(48.dp)
                    ) {
                        AnimatedNeonGlyph("🎨", contentDescription = null, fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            when (uiState.selectedTab) {
                                GaugeMarketTab.POPULAR -> "Aún no hay gauges públicos"
                                GaugeMarketTab.RECENT -> "Aún no hay gauges nuevos"
                                GaugeMarketTab.MY_SALES -> "Todavía no has publicado gauges"
                                GaugeMarketTab.MY_PURCHASES -> if (MonetizationPolicy.LOCAL_FULL_ACCESS) {
                                    "Acceso local liberado"
                                } else {
                                    "Todavía no hay compras registradas"
                                }
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (uiState.selectedTab) {
                                GaugeMarketTab.POPULAR -> "Cuando existan publicaciones activas en Supabase aparecerán aquí."
                                GaugeMarketTab.RECENT -> "Los gauges más recientes aparecerán aquí cuando se publiquen."
                                GaugeMarketTab.MY_SALES -> "Publica desde el editor DIY para ver tus diseños listados aquí."
                                GaugeMarketTab.MY_PURCHASES -> if (MonetizationPolicy.LOCAL_FULL_ACCESS) {
                                    "Todos los gauges se pueden abrir y aplicar desde Populares o Recientes sin pasar por Google Play Billing."
                                } else {
                                    "Las compras confirmadas por Google Play y registradas en Supabase aparecerán aquí."
                                }
                            },
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Grid of listing cards
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.listings) { listing ->
                        GaugeListingCard(
                            listing = listing,
                            accentColor = accentCyan,
                            onClick = {
                                selectedListing = listing
                                showPreviewSheet = true
                            }
                        )
                    }
                }
            }
        }



        // ══════════════════════════════════════
        // Preview Sheet
        // ══════════════════════════════════════
        if (showPreviewSheet && selectedListing != null) {
            val listing = selectedListing!!
            val listingId = listing.id
            val isOwned = MonetizationPolicy.LOCAL_FULL_ACCESS || when {
                uiState.selectedTab == GaugeMarketTab.MY_SALES -> true
                listingId.isNullOrBlank() -> false
                else -> uiState.ownershipByListingId[listingId] == true
            }
            GaugePreviewSheet(
                listing = listing,
                isOwned = isOwned,
                isMonetizationUnlocked = MonetizationPolicy.LOCAL_FULL_ACCESS,
                reviews = listingId?.let { uiState.reviewsByListingId[it] }.orEmpty(),
                isLoadingReviews = !listingId.isNullOrBlank() && !uiState.reviewsByListingId.containsKey(listingId),
                isPurchaseInProgress = isPurchaseFlowBusy,
                onDismiss = {
                    showPreviewSheet = false
                    selectedListing = null
                },
                onBuy = { listingToBuy ->
                    if (isPurchaseFlowBusy) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Ya hay una compra en proceso. Espera la confirmación antes de tocar de nuevo.")
                        }
                        return@GaugePreviewSheet
                    }
                    if (MonetizationPolicy.LOCAL_FULL_ACCESS) {
                        scope.launch {
                            snackbarHostState.showSnackbar(MonetizationPolicy.ACCESS_MESSAGE)
                        }
                        return@GaugePreviewSheet
                    }
                    val buyListingId = listingToBuy.id
                    if (buyListingId.isNullOrBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("La publicación no tiene un identificador válido.")
                        }
                        return@GaugePreviewSheet
                    }
                    if (uiState.ownershipByListingId[buyListingId] == true) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Este gauge ya está registrado en tus compras.")
                        }
                        return@GaugePreviewSheet
                    }
                    if (activity == null) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No se pudo iniciar Google Play Billing en este contexto.")
                        }
                        return@GaugePreviewSheet
                    }
                    if (!isBillingConnected) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Google Play Billing no está disponible todavía en este dispositivo/build.")
                        }
                        return@GaugePreviewSheet
                    }
                    purchaseTarget = listingToBuy
                    scope.launch {
                        billingManager.launchPurchaseFlow(activity, listingToBuy.price_tier)
                    }
                },
                onApply = { config ->
                    // Apply config to the DIY editor
                    gaugeStyleManager.importDiyConfig(config)
                    showPreviewSheet = false
                    navController.backOrHome()
                }
            )
        }

        if (showPublishDialog) {
            val diyTrigger = GaugeStyleManager.diyUpdateTrigger
            val draftConfig = remember(diyTrigger, publishSourceGauge) {
                publishSourceGauge?.toGaugeConfig() ?: gaugeStyleManager.exportDiyConfig()
            }
            GaugePublishDialog(
                sourceGauge = publishSourceGauge,
                draftConfig = draftConfig,
                isPublishing = uiState.isPublishing,
                accentColor = accentCyan,
                onDismiss = {
                    if (!uiState.isPublishing) {
                        showPublishDialog = false
                        publishSourceGauge = null
                    }
                },
                onPublish = { name, description, priceTier, category, tags, acceptedTerms ->
                    val sourceId = publishSourceGauge?.id
                    viewModel.publishGauge(
                        config = draftConfig,
                        name = name,
                        description = description,
                        priceTier = priceTier,
                        saleCategory = category,
                        tags = tags,
                        publishedFromSavedGaugeId = sourceId,
                        sellerTermsAccepted = acceptedTerms
                    ) { result ->
                        scope.launch {
                            result
                                .onSuccess { listingId ->
                                    if (sourceId != null) {
                                        markSavedGaugeAsPublished(appContext, sourceId, listingId)
                                    }
                                    snackbarHostState.showSnackbar("Gauge publicado en venta: ${GaugePriceTiers.displayPrice(priceTier)}")
                                    showPublishDialog = false
                                    publishSourceGauge = null
                                    viewModel.selectTab(GaugeMarketTab.MY_SALES)
                                }
                                .onFailure { error ->
                                    snackbarHostState.showSnackbar(error.message ?: "No se pudo publicar el gauge.")
                                }
                        }
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        )
    }
}

@Composable
private fun GaugePublishDialog(
    sourceGauge: SavedGaugeEntity?,
    draftConfig: GaugeConfig,
    isPublishing: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit,
    onPublish: (
        name: String,
        description: String,
        priceTier: Int,
        category: String,
        tags: String,
        acceptedTerms: Boolean
    ) -> Unit
) {
    val categories = listOf("performance", "luxury", "racing", "diagnostic", "weather", "custom")
    var name by remember(sourceGauge?.id, draftConfig.name) {
        mutableStateOf(sourceGauge?.name ?: draftConfig.name.ifBlank { "Gauge MEET" })
    }
    var description by remember(sourceGauge?.id) { mutableStateOf("Diseño de gauge personalizado creado con MEET DIY Editor.") }
    var priceTier by remember(sourceGauge?.id) { mutableIntStateOf(1) }
    var category by remember(sourceGauge?.id) { mutableStateOf(if (sourceGauge != null) "custom" else "performance") }
    var tags by remember(sourceGauge?.id) { mutableStateOf("") }
    var acceptedTerms by remember(sourceGauge?.id) { mutableStateOf(false) }
    val isNameValid = name.trim().length >= 3
    val isDescriptionValid = description.trim().length >= 3
    val canPublish = isNameValid && isDescriptionValid && acceptedTerms && !isPublishing
    val split = GaugePriceTiers.calculateSplit(priceTier)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0E14),
        title = {
            Text(
                text = "Publicar gauge",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF050810))
                        .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val previewGauge = remember(draftConfig, name) {
                        SavedGaugeEntity(
                            id = sourceGauge?.id ?: "publish-preview",
                            name = name,
                            bgType = draftConfig.bgType,
                            bgPresetIndex = draftConfig.bgPresetIndex,
                            bgImageUri = sourceGauge?.bgImageUri.orEmpty(),
                            bezelStyle = draftConfig.bezelStyle,
                            needleStyle = draftConfig.needleStyle,
                            ticksStyle = draftConfig.ticksStyle,
                            accentColor = draftConfig.accentColor,
                            accentColor2 = draftConfig.accentColor2,
                            glowIntensity = draftConfig.glowIntensity,
                            imageOpacity = draftConfig.imageOpacity,
                            animationIndex = draftConfig.animationIndex,
                            createdAt = sourceGauge?.createdAt ?: 0L,
                            updatedAt = sourceGauge?.updatedAt ?: 0L,
                            typographyIndex = draftConfig.typographyIndex
                        )
                    }
                    Gauge3DWrapper(
                        glowColor = Color(previewGauge.accentColor),
                        style = GaugeStyleSet.CUSTOM_DIY,
                        modifier = Modifier.size(132.dp)
                    ) {
                        GaugeDiyWidget(
                            label = previewGauge.name,
                            value = 68f,
                            minVal = 0f,
                            maxVal = 100f,
                            unit = "%",
                            warningThreshold = 72f,
                            criticalThreshold = 90f,
                            diyConfig = previewGauge,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = !isNameValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isNameValid) {
                    Text(
                        "El nombre debe tener al menos 3 caracteres.",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(240) },
                    label = { Text("Descripción de venta") },
                    minLines = 2,
                    maxLines = 3,
                    isError = !isDescriptionValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isDescriptionValid) {
                    Text(
                        "La descripción debe tener al menos 3 caracteres.",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Text("Categoría", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories.size) { index ->
                        val option = categories[index]
                        val selected = option == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                                .border(
                                    1.dp,
                                    if (selected) accentColor.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { category = option }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                option.uppercase(),
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it.take(96) },
                    label = { Text("Tags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Precio ${GaugePriceTiers.displayPrice(priceTier)}",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Slider(
                    value = priceTier.toFloat(),
                    onValueChange = { priceTier = it.toInt().coerceIn(1, 10) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Producto Google Play: ${GaugePriceTiers.productId(priceTier)}",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Creador: ${formatUsdFromCents(split.first)} · Plataforma: ${formatUsdFromCents(split.second)}",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acceptedTerms,
                        onCheckedChange = { acceptedTerms = it },
                        colors = CheckboxDefaults.colors(checkedColor = accentColor)
                    )
                    Text(
                        "Acepto vender este gauge bajo las reglas del marketplace.",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!canPublish) {
                    val missingRequirements = mutableListOf<String>()
                    if (!isNameValid) missingRequirements.add("Nombre de al menos 3 caracteres")
                    if (!isDescriptionValid) missingRequirements.add("Descripción de al menos 3 caracteres")
                    if (!acceptedTerms) missingRequirements.add("Aceptar las condiciones de venta")
                    
                    Text(
                        text = "Falta: " + missingRequirements.joinToString(" · "),
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canPublish,
                onClick = {
                    onPublish(name, description, priceTier, category, tags, acceptedTerms)
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Text("Publicar", fontWeight = FontWeight.Black)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isPublishing, onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.65f))
            }
        }
    )
}

// ═══════════════════════════════════════════════════════
// LISTING CARD
// ═══════════════════════════════════════════════════════

@Composable
private fun GaugeListingCard(
    listing: GaugeListing,
    accentColor: Color,
    onClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "card_${listing.id}")
    val cardGlow by inf.animateFloat(
        0.0f, 0.15f,
        infiniteRepeatable(tween(2500), RepeatMode.Reverse),
        label = "cardGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1219),
                        Color(0xFF0A0E16)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = cardGlow + 0.08f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val previewGauge = remember(listing.config_json, listing.name) {
                listing.toPreviewGaugeEntity()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF080C14)),
                contentAlignment = Alignment.Center
            ) {
                if (previewGauge != null) {
                    Gauge3DWrapper(
                        glowColor = Color(previewGauge.accentColor),
                        style = GaugeStyleSet.CUSTOM_DIY,
                        modifier = Modifier.size(122.dp)
                    ) {
                        GaugeDiyWidget(
                            label = previewGauge.name,
                            value = 68f,
                            minVal = 0f,
                            maxVal = 100f,
                            unit = "%",
                            warningThreshold = 72f,
                            criticalThreshold = 90f,
                            diyConfig = previewGauge,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    AnimatedNeonGlyph("🎨", contentDescription = null, fontSize = 40.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Name
            Text(
                listing.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Creator
            Text(
                "por ${listing.creator_name}",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // Bottom row: price + rating + sales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Price badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(0.5.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        GaugePriceTiers.displayPrice(listing.price_tier),
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Rating
                    if (listing.review_count > 0) {
                        AnimatedNeonGlyph("⭐", contentDescription = null, fontSize = 10.sp)
                        Text(
                            " %.1f".format(listing.avg_rating),
                            color = Color(0xFFFFD600),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    // Sales
                    Text(
                        "${listing.total_sales}🛒",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun formatUsdFromCents(cents: Int): String {
    val dollars = cents / 100
    val remainder = cents % 100
    return "$${dollars}.${"%02d".format(remainder)}"
}

private fun SavedGaugeEntity.toGaugeConfig(): GaugeConfig {
    return GaugeConfig(
        name = name,
        bgType = bgType,
        bgPresetIndex = bgPresetIndex,
        bezelStyle = bezelStyle,
        needleStyle = needleStyle,
        ticksStyle = ticksStyle,
        accentColor = accentColor,
        accentColor2 = accentColor2,
        glowIntensity = glowIntensity,
        imageOpacity = imageOpacity,
        animationIndex = animationIndex,
        typographyIndex = typographyIndex,
        bgImageUri = bgImageUri
    )
}

private suspend fun markSavedGaugeAsPublished(
    context: android.content.Context,
    savedGaugeId: String,
    marketplaceId: String
) {
    withContext(Dispatchers.IO) {
        val db = androidx.room.Room.databaseBuilder(
            context,
            com.elysium369.meet.data.local.MeetDatabase::class.java,
            "meet_database"
        ).build()
        try {
            db.savedGaugeDao().markAsPublished(savedGaugeId, marketplaceId, System.currentTimeMillis())
        } finally {
            db.close()
        }
    }
}

private fun GaugeListing.toPreviewGaugeEntity(): SavedGaugeEntity? {
    val json = Json { ignoreUnknownKeys = true }
    val config = runCatching { json.decodeFromString<GaugeConfig>(config_json) }.getOrNull() ?: return null
    return SavedGaugeEntity(
        id = id ?: "preview",
        name = config.name.ifBlank { name },
        bgType = config.bgType,
        bgPresetIndex = config.bgPresetIndex,
        bgImageUri = config.bgImageUri,
        bezelStyle = config.bezelStyle,
        needleStyle = config.needleStyle,
        ticksStyle = config.ticksStyle,
        accentColor = config.accentColor,
        accentColor2 = config.accentColor2,
        glowIntensity = config.glowIntensity,
        imageOpacity = config.imageOpacity,
        animationIndex = config.animationIndex,
        createdAt = 0L,
        updatedAt = 0L,
        typographyIndex = config.typographyIndex
    )
}
