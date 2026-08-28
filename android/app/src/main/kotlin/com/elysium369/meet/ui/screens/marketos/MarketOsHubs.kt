package com.elysium369.meet.ui.screens.marketos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.fuel.domain.OpaqueQrToken
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private data class MarketPalette(val ink: Color, val surface: Color, val accent: Color, val warm: Color)
private val LegalPalette = MarketPalette(Color(0xFF050C18), Color(0xFF0D192B), Color(0xFFD8B464), Color(0xFFFFF7E2))
private val PropertyPalette = MarketPalette(Color(0xFF07110F), Color(0xFF10201B), Color(0xFF41D89B), Color(0xFFF4EFE4))
private val FuelPalette = MarketPalette(Color(0xFF090D12), Color(0xFF141B22), Color(0xFFFFB423), Color(0xFF50D9F5))

@Composable
fun LegalVanguardHub(
    onBack: () -> Unit,
    onOpenMessages: () -> Unit,
    viewModel: MarketOsViewModel = hiltViewModel(),
) {
    var need by remember { mutableStateOf("") }
    val catalog by viewModel.legalCatalog.collectAsStateWithLifecycle()
    val matters by viewModel.legalMatters.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCommands.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val triage by viewModel.legalTriage.collectAsStateWithLifecycle()
    var legalAiConsent by remember { mutableStateOf(false) }
    var selectedCategory by remember(catalog) { mutableStateOf(catalog.firstOrNull { it.parentCode != null }?.code) }
    MarketHubScaffold("LEGAL VANGUARD", "Confidencialidad antes que distribución", Icons.Default.AccountBalance, LegalPalette, onBack) {
        TruthRibbon("CAAB y DNN se verifican por separado", "Nunca mostramos “verificado” desde una declaración.", LegalPalette)
        SyncRibbon(connection, pending, LegalPalette, viewModel::refreshNow)
        SectionTitle("¿Qué pasó?", "No necesitas conocer la materia jurídica.", LegalPalette)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            catalog.filter { it.parentCode != null }.take(12).forEach { category ->
                FilterChip(
                    selected = selectedCategory == category.code,
                    onClick = { selectedCategory = category.code },
                    label = { Text(category.displayName) },
                )
            }
        }
        OutlinedTextField(value = need, onValueChange = { need = it.take(4_000) }, label = { Text("Cuéntame qué pasó") }, supportingText = { Text("La IA solo sugiere una categoría; tú decides y no constituye asesoría legal.") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        Text("No incluyas nombres de contrapartes ni evidencia sensible aquí; se solicitarán en el gate cifrado.", color = Color.White.copy(alpha = .58f), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = legalAiConsent, onCheckedChange = { legalAiConsent = it })
            Text("Autorizo el análisis remoto con minimización de datos para recibir una sugerencia.", color = Color.White.copy(alpha = .76f), fontSize = 12.sp)
        }
        SecondaryAction("SUGERIR CATEGORÍA CON IA", Icons.Default.AutoAwesome) {
            viewModel.requestLegalTriage(need, legalAiConsent)
        }
        LaunchedEffect(triage?.triageId) {
            triage?.primaryCategoryCode?.let { selectedCategory = it }
        }
        triage?.let { suggestion ->
            StatusCard(
                "Sugerencia IA · ${suggestion.primaryCategoryCode}",
                "Confianza ${(suggestion.confidence * 100).toInt()}% · ${suggestion.urgency} · taxonomía v${suggestion.taxonomyVersion}. Requiere tu confirmación.",
                Icons.Default.AutoAwesome,
                LegalPalette,
            )
        }
        PrimaryAction("CREAR SOLICITUD PRIVADA", Icons.Default.Lock, need.trim().length >= 8 && selectedCategory != null, LegalPalette) {
            viewModel.createLegalMatter(requireNotNull(selectedCategory), need)
        }
        notice?.let { StatusCard("Estado de autoridad", it, Icons.Default.Security, LegalPalette) }
        matters.take(3).forEach { matter ->
            StatusCard(matter.categoryCode.uppercase(), "${matter.state} · ${matter.disclosureLevel} · v${matter.serverVersion}", Icons.Default.Gavel, LegalPalette)
        }
        SectionTitle("Tu ruta segura", "Una sola contratación, con límites visibles.", LegalPalette)
        StepRail(listOf("Triage en lenguaje humano", "Conflict check", "Ofertas con alcance y exclusiones", "Engagement", "Legal Vault y plazos"), LegalPalette)
        SecondaryAction("ABRIR MENSAJES PROTEGIDOS", Icons.Default.Forum, onOpenMessages)
    }
}

@Composable
fun PropertiesHub(
    onBack: () -> Unit,
    onOpenLegal: () -> Unit,
    viewModel: MarketOsViewModel = hiltViewModel(),
) {
    var operation by remember { mutableStateOf("VENTA") }
    val listings by viewModel.propertyListings.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCommands.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    MarketHubScaffold("ELYSIUM PROPERTIES", "La propiedad se demuestra claim por claim", Icons.Default.HomeWork, PropertyPalette, onBack) {
        TruthRibbon("Property Passport", "Titularidad, finca, plano, gravámenes, uso de suelo e inspección mantienen evidencia independiente.", PropertyPalette)
        SyncRibbon(connection, pending, PropertyPalette, viewModel::refreshNow)
        SectionTitle("Explorar", "La dirección exacta permanece protegida.", PropertyPalette)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("VENTA", "ALQUILER", "PREVENTA").forEachIndexed { index, label ->
                SegmentedButton(selected = operation == label, onClick = { operation = label }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label, fontSize = 11.sp) }
            }
        }
        val visible = listings.filter { it.operation == operation || (operation == "VENTA" && it.operation == "SALE") || (operation == "ALQUILER" && it.operation == "RENT") || (operation == "PREVENTA" && it.operation == "PRESALE") }
        if (visible.isEmpty()) {
            StatusCard("$operation · sin resultados confirmados", "La proyección local no contiene publicaciones autorizadas para este filtro.", Icons.Default.Map, PropertyPalette)
        } else {
            visible.take(10).forEach { listing ->
                StatusCard(
                    "${listing.propertyTypeCode} · ${listing.approximateZone}",
                    "${listing.currency} ${listing.askingAmountMinor} · ${listing.state} · evidencia: ${listing.trustSummaryJson}",
                    Icons.Default.Map,
                    PropertyPalette,
                )
            }
        }
        SectionTitle("Pasaporte y cierre", "Los riesgos no caben en un único check.", PropertyPalette)
        ClaimRow("Titular registral", "Pendiente de evidencia del Registro", false, PropertyPalette)
        ClaimRow("Plano catastrado", "Dato no capturado", false, PropertyPalette)
        ClaimRow("Gravámenes", "Desconocido; no significa libre", false, PropertyPalette)
        ClaimRow("Inspección física", "No realizada", false, PropertyPalette)
        PrimaryAction("SOLICITAR DUE DILIGENCE LEGAL", Icons.Default.Balance, true, PropertyPalette, onOpenLegal)
    }
}

@Composable
fun FuelRewardsHub(
    onBack: () -> Unit,
    viewModel: MarketOsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var scanState by remember { mutableStateOf("LISTO PARA ESCANEAR") }
    val coupons by viewModel.fuelCoupons.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCommands.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val options = remember { GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build() }
    val scanner = remember(context) { GmsBarcodeScanning.getClient(context, options) }
    MarketHubScaffold("FUEL REWARDS", "Beneficios sin alterar la tarifa regulada", Icons.Default.LocalGasStation, FuelPalette, onBack) {
        TruthRibbon("Compra ≠ recompensa", "Sólo una compra liquidada y con fuente suficiente puede emitir beneficios.", FuelPalette)
        SyncRibbon(connection, pending, FuelPalette, viewModel::refreshNow)
        SectionTitle("Wallet", "Los cupones reales aparecen desde tu proyección local.", FuelPalette)
        if (coupons.isEmpty()) {
            StatusCard("SIN BENEFICIOS CONFIRMADOS", "No hay cupones sincronizados. Escanear un QR nunca aplica beneficios sin confirmación del servidor.", Icons.Default.Wallet, FuelPalette)
        } else {
            coupons.take(10).forEach { coupon ->
                StatusCard(coupon.benefitTitle, "${coupon.state} · vence ${java.time.Instant.ofEpochMilli(coupon.expiresAtEpochMs)} · v${coupon.serverVersion}", Icons.Default.Wallet, FuelPalette)
            }
        }
        PrimaryAction("ESCANEAR QR SIN PERMISO DE CÁMARA", Icons.Default.QrCodeScanner, true, FuelPalette) {
            scanState = "ABRIENDO SCANNER…"
            scanner.startScan().addOnSuccessListener { barcode ->
                scanState = runCatching {
                    val token = OpaqueQrToken.fromPublicUrl(barcode.rawValue.orEmpty(), setOf("meet.app", "elysium-vanguard.app"))
                    viewModel.claimFuelPurchase(token.value)
                    "SOLICITUD EN COLA · EL SERVIDOR DECIDIRÁ SI LA COMPRA CALIFICA"
                }.getOrElse { "QR NO CONFIABLE · NO SE APLICÓ BENEFICIO" }
            }.addOnCanceledListener { scanState = "ESCANEO CANCELADO" }
                .addOnFailureListener { scanState = "SCANNER NO DISPONIBLE" }
        }
        Text(scanState, color = FuelPalette.warm, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SectionTitle("Regla base", "Configurable por campaña y versionada.", FuelPalette)
        StatusCard("₡5.000 → 1 unidad elegible", "₡4.999 = 0 · ₡9.999 = 1 · ₡10.000 = 2. La redención sigue siendo atómica y autoritativa.", Icons.AutoMirrored.Filled.ReceiptLong, FuelPalette)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MarketHubScaffold(title: String, subtitle: String, icon: ImageVector, palette: MarketPalette, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(title = { Column { Text(title, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text(subtitle, fontSize = 11.sp, color = palette.accent) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }, actions = { Icon(icon, null, tint = palette.accent, modifier = Modifier.padding(end = 18.dp)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.ink.copy(alpha = .96f), titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.ink, palette.surface, palette.ink))).padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable private fun TruthRibbon(title: String, body: String, p: MarketPalette) = Surface(color = p.accent.copy(alpha = .12f), shape = RoundedCornerShape(2.dp), border = BorderStroke(1.dp, p.accent.copy(alpha = .55f))) {
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.VerifiedUser, null, tint = p.accent); Column { Text(title.uppercase(), color = p.warm, fontWeight = FontWeight.Black, letterSpacing = .6.sp); Text(body, color = Color.White.copy(alpha = .74f), fontSize = 13.sp) } }
}
@Composable private fun SectionTitle(title: String, subtitle: String, p: MarketPalette) = Column { Text(title, color = p.warm, fontSize = 23.sp, fontWeight = FontWeight.Black); Text(subtitle, color = p.accent.copy(alpha = .86f), fontSize = 13.sp) }
@Composable private fun StatusCard(title: String, body: String, icon: ImageVector, p: MarketPalette) = Surface(color = p.surface.copy(alpha = .92f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { Icon(icon, null, tint = p.accent); Column { Text(title, color = p.warm, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(body, color = Color.White.copy(alpha = .7f), fontSize = 13.sp) } }
}
@Composable private fun PrimaryAction(label: String, icon: ImageVector, enabled: Boolean, p: MarketPalette, onClick: () -> Unit) = Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.ink), shape = RoundedCornerShape(4.dp)) { Icon(icon, null); Spacer(Modifier.width(10.dp)); Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp) }
@Composable private fun SecondaryAction(label: String, icon: ImageVector, onClick: () -> Unit) = OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Icon(icon, null); Spacer(Modifier.width(8.dp)); Text(label) }
@Composable private fun StepRail(steps: List<String>, p: MarketPalette) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { steps.forEachIndexed { index, step -> Row(verticalAlignment = Alignment.CenterVertically) { Surface(color = p.accent, shape = RoundedCornerShape(50)) { Text("${index + 1}", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = p.ink, fontWeight = FontWeight.Black) }; Spacer(Modifier.width(12.dp)); Text(step, color = Color.White.copy(alpha = .86f)) } } }
@Composable private fun ClaimRow(label: String, detail: String, verified: Boolean, p: MarketPalette) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (verified) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (verified) p.accent else Color.White.copy(alpha = .35f)); Spacer(Modifier.width(12.dp)); Column { Text(label, color = p.warm, fontWeight = FontWeight.Bold); Text(detail, color = Color.White.copy(alpha = .58f), fontSize = 12.sp) } }

@Composable private fun SyncRibbon(state: MarketConnectionState, pending: Int, p: MarketPalette, onRefresh: () -> Unit) =
    Surface(color = Color.Black.copy(alpha = .18f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudSync, null, tint = p.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.name.replace('_', ' '), color = p.warm, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("$pending operaciones esperando autoridad", color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
            }
            TextButton(onClick = onRefresh) { Text("ACTUALIZAR", color = p.accent) }
        }
    }
