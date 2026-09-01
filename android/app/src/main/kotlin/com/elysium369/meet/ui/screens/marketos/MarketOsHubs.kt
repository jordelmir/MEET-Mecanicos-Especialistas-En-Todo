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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.fuel.domain.OpaqueQrToken
import com.elysium369.meet.observability.MeetTelemetry
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
    val timeline by viewModel.legalTimeline.collectAsStateWithLifecycle()
    val cases by viewModel.legalCases.collectAsStateWithLifecycle()
    val evidence by viewModel.legalEvidence.collectAsStateWithLifecycle()
    var journalEntry by rememberSaveable { mutableStateOf("") }
    var caseTitle by rememberSaveable { mutableStateOf("") }
    var legalAiConsent by remember { mutableStateOf(false) }
    var selectedCategory by remember(catalog) { mutableStateOf(catalog.firstOrNull { it.parentCode != null }?.code) }
    val evidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attachLegalEvidence(uri, "DOCUMENT", timeline.firstOrNull()?.eventId)
    }
    MarketHubScaffold("LEGAL VANGUARD", "Confidencialidad antes que distribución", Icons.Default.AccountBalance, LegalPalette, onBack) {
        TruthRibbon("CAAB y DNN se verifican por separado", "Nunca mostramos “verificado” desde una declaración.", LegalPalette)
        SyncRibbon(connection, pending, LegalPalette, viewModel::refreshNow)
        SectionTitle("Diario probatorio", "Local, cifrado y separado de la contratación jurídica.", LegalPalette)
        OutlinedTextField(
            value = journalEntry,
            onValueChange = { journalEntry = it.take(20_000) },
            label = { Text("¿Qué ocurrió?") },
            supportingText = { Text("Se guarda como declaración; adjuntar evidencia no la convierte automáticamente en verificada.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryAction("GUARDAR EN MI LÍNEA DE TIEMPO", Icons.Default.HistoryEdu, journalEntry.trim().length >= 3, LegalPalette) {
            viewModel.recordLegalJournal(journalEntry)
            journalEntry = ""
        }
        if (timeline.isEmpty()) {
            StatusCard("SIN ENTRADAS", "Tu línea de tiempo local aún no contiene hechos declarados.", Icons.Default.Timeline, LegalPalette)
        } else {
            timeline.take(3).forEach { event ->
                StatusCard(
                    event.eventType.replace('_', ' '),
                    "${event.truthState} · ${java.time.Instant.ofEpochMilli(event.occurredAtEpochMs)}\n${event.narrative}",
                    Icons.Default.Timeline,
                    LegalPalette,
                )
            }
        }
        SecondaryAction("ADJUNTAR ORIGINAL AL ÚLTIMO SUCESO", Icons.Default.AttachFile) {
            evidencePicker.launch(arrayOf("image/*", "video/*", "audio/*", "application/pdf", "text/*"))
        }
        if (evidence.isNotEmpty()) {
            StatusCard(
                "${evidence.size} ORIGINALES/DERIVADOS PRESERVADOS",
                "SHA-256 registra integridad técnica; no declara admisibilidad ni prueba por sí solo la narración.",
                Icons.Default.Fingerprint,
                LegalPalette,
            )
        }
        SectionTitle("Casos", "Agrupa sucesos sin duplicar ni alterar el registro diario.", LegalPalette)
        OutlinedTextField(
            value = caseTitle,
            onValueChange = { caseTitle = it.take(240) },
            label = { Text("Nombre del caso") },
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryAction("CREAR CASO LOCAL", Icons.Default.FolderSpecial, caseTitle.trim().length >= 3, LegalPalette) {
            viewModel.createLegalCase(caseTitle)
            caseTitle = ""
        }
        cases.take(3).forEach { legalCase ->
            StatusCard(
                legalCase.title,
                "${legalCase.state} · actualizado ${java.time.Instant.ofEpochMilli(legalCase.updatedAtEpochMs)}",
                Icons.Default.Folder,
                LegalPalette,
            )
        }
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
    onOpenMessages: () -> Unit = {},
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
        SecondaryAction("ABRIR MENSAJES DE PROPIEDADES", Icons.Default.Forum, onOpenMessages)
    }
}

@Composable
fun FuelRewardsHub(
    onBack: () -> Unit,
    onOpenMessages: () -> Unit = {},
    viewModel: MarketOsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var scanState by remember { mutableStateOf("LISTO PARA ESCANEAR") }
    val coupons by viewModel.fuelCoupons.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCommands.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    val transactions by viewModel.fuelTransactions.collectAsStateWithLifecycle()
    val confirmedRewards by viewModel.confirmedFuelRewards.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var purchaseAmount by rememberSaveable { mutableStateOf("") }
    var purchaseLiters by rememberSaveable { mutableStateOf("") }
    var stationId by rememberSaveable { mutableStateOf("") }
    var odometerKm by rememberSaveable { mutableStateOf("") }
    MarketHubScaffold("FUEL REWARDS", "Beneficios sin alterar la tarifa regulada", Icons.Default.LocalGasStation, FuelPalette, onBack) {
        TruthRibbon("Compra ≠ recompensa", "Sólo una compra liquidada y con fuente suficiente puede emitir beneficios.", FuelPalette)
        SyncRibbon(connection, pending, FuelPalette, viewModel::refreshNow)
        SectionTitle("Registrar carga", "La compra queda declarada; el servidor decide cualquier recompensa.", FuelPalette)
        OutlinedTextField(purchaseAmount, { purchaseAmount = it.take(16) }, label = { Text("Monto CRC") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(purchaseLiters, { purchaseLiters = it.take(12) }, label = { Text("Litros") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(stationId, { stationId = it.take(120) }, label = { Text("Estación (opcional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(odometerKm, { odometerKm = it.take(16) }, label = { Text("Kilometraje (opcional)") }, supportingText = { Text("Sin dos lecturas válidas, MEET no inventa consumo real.") }, modifier = Modifier.fillMaxWidth())
        PrimaryAction("GUARDAR COMPRA DECLARADA", Icons.Default.LocalGasStation, purchaseAmount.isNotBlank() && purchaseLiters.isNotBlank(), FuelPalette) {
            viewModel.recordFuelPurchase(purchaseAmount, purchaseLiters, stationId, odometerKm)
            purchaseAmount = ""
            purchaseLiters = ""
            stationId = ""
            odometerKm = ""
        }
        StatusCard(
            "OCR DE RECIBO · SIN EJECUTAR",
            "Una foto futura se tratará como extracción no verificada hasta revisión; nunca acreditará puntos por sí sola.",
            Icons.Default.DocumentScanner,
            FuelPalette,
        )
        notice?.let { StatusCard("Estado", it, Icons.Default.Info, FuelPalette) }
        SectionTitle("Wallet", "Los cupones reales aparecen desde tu proyección local.", FuelPalette)
        val confirmedBalance = confirmedRewards.firstOrNull()?.balanceAfterUnits
        StatusCard(
            "SALDO CONFIRMADO",
            confirmedBalance?.let { "$it unidades · autoridad del servidor" }
                ?: "Sin saldo confirmado por el servidor.",
            Icons.Default.AccountBalanceWallet,
            FuelPalette,
        )
        if (coupons.isEmpty()) {
            StatusCard("SIN BENEFICIOS CONFIRMADOS", "No hay cupones sincronizados. Escanear un QR nunca aplica beneficios sin confirmación del servidor.", Icons.Default.Wallet, FuelPalette)
        } else {
            coupons.take(10).forEach { coupon ->
                StatusCard(coupon.benefitTitle, "${coupon.state} · vence ${java.time.Instant.ofEpochMilli(coupon.expiresAtEpochMs)} · v${coupon.serverVersion}", Icons.Default.Wallet, FuelPalette)
            }
        }
        PrimaryAction("ESCANEAR QR SIN PERMISO DE CÁMARA", Icons.Default.QrCodeScanner, true, FuelPalette) {
            scanState = "ABRIENDO SCANNER…"
            val scanner = runCatching {
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .enableAutoZoom()
                    .build()
                GmsBarcodeScanning.getClient(context, options)
            }.onFailure {
                MeetTelemetry.recordError(
                    name = "fuel.scanner.unavailable",
                    failureCode = "SCANNER_INIT_FAILED",
                    attributes = mapOf("vertical" to "fuel", "operation" to "scan_qr"),
                )
            }.getOrNull()
            if (scanner == null) {
                scanState = "SCANNER NO DISPONIBLE · FUEL REWARDS SIGUE ACTIVO"
                return@PrimaryAction
            }
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
        SectionTitle("Historial de combustible", "Transacciones locales con estado de verdad visible.", FuelPalette)
        if (transactions.isEmpty()) {
            StatusCard("SIN COMPRAS REGISTRADAS", "No hay transacciones locales para calcular consumo.", Icons.AutoMirrored.Filled.ReceiptLong, FuelPalette)
        } else {
            transactions.take(5).forEach { purchase ->
                val consumption = com.elysium369.meet.fuel.domain.FuelConsumptionPolicy.calculate(
                    purchase.volumeMilliLiters,
                    purchase.distanceSincePreviousMeters,
                )
                StatusCard(
                    "${purchase.currency} ${decimalMinor(purchase.amountMinor, 2)}",
                    "${decimalMinor(purchase.volumeMilliLiters, 3)} L · ${purchase.truthState} · " +
                        (consumption.litersPer100Km?.let { "$it L/100 km derivados de kilometraje registrado" }
                            ?: "consumo desconocido: falta distancia válida") + " · recompensa no inferida",
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    FuelPalette,
                )
            }
        }
        SecondaryAction("ABRIR MENSAJES DE FUEL", Icons.Default.Forum, onOpenMessages)
        SectionTitle("Regla base", "Configurable por campaña y versionada.", FuelPalette)
        StatusCard("₡5.000 → 1 unidad elegible", "₡4.999 = 0 · ₡9.999 = 1 · ₡10.000 = 2. La redención sigue siendo atómica y autoritativa.", Icons.AutoMirrored.Filled.ReceiptLong, FuelPalette)
    }
}

private fun decimalMinor(value: Long, scale: Int): String =
    java.math.BigDecimal(value).movePointLeft(scale).stripTrailingZeros().toPlainString()

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
