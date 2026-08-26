package com.elysium369.meet.ui.screens.marketos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.elysium369.meet.fuel.domain.OpaqueQrToken
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private data class MarketPalette(val ink: Color, val surface: Color, val accent: Color, val warm: Color)
private val LegalPalette = MarketPalette(Color(0xFF050C18), Color(0xFF0D192B), Color(0xFFD8B464), Color(0xFFFFF7E2))
private val PropertyPalette = MarketPalette(Color(0xFF07110F), Color(0xFF10201B), Color(0xFF41D89B), Color(0xFFF4EFE4))
private val FuelPalette = MarketPalette(Color(0xFF090D12), Color(0xFF141B22), Color(0xFFFFB423), Color(0xFF50D9F5))

@Composable
fun LegalVanguardHub(onBack: () -> Unit, onOpenMessages: () -> Unit) {
    var need by remember { mutableStateOf("") }
    var staged by remember { mutableStateOf(false) }
    MarketHubScaffold("LEGAL VANGUARD", "Confidencialidad antes que distribución", Icons.Default.AccountBalance, LegalPalette, onBack) {
        TruthRibbon("CAAB y DNN se verifican por separado", "Nunca mostramos “verificado” desde una declaración.", LegalPalette)
        SectionTitle("¿Qué pasó?", "No necesitas conocer la materia jurídica.", LegalPalette)
        OutlinedTextField(value = need, onValueChange = { need = it.take(2_000); staged = false }, label = { Text("Describe tu situación") }, supportingText = { Text("La sugerencia de categoría no es un diagnóstico legal.") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        PrimaryAction(if (staged) "TRIAGE PREPARADO · FALTA ENVIAR" else "PREPARAR TRIAGE PRIVADO", Icons.Default.Lock, need.trim().length >= 8, LegalPalette) { staged = true }
        if (staged) StatusCard("Siguiente gate", "Partes mínimas → conflicto → revelación controlada. Ningún expediente fue publicado todavía.", Icons.Default.Security, LegalPalette)
        SectionTitle("Tu ruta segura", "Una sola contratación, con límites visibles.", LegalPalette)
        StepRail(listOf("Triage en lenguaje humano", "Conflict check", "Ofertas con alcance y exclusiones", "Engagement", "Legal Vault y plazos"), LegalPalette)
        SecondaryAction("ABRIR MENSAJES PROTEGIDOS", Icons.Default.Forum, onOpenMessages)
    }
}

@Composable
fun PropertiesHub(onBack: () -> Unit, onOpenLegal: () -> Unit) {
    var operation by remember { mutableStateOf("VENTA") }
    MarketHubScaffold("ELYSIUM PROPERTIES", "La propiedad se demuestra claim por claim", Icons.Default.HomeWork, PropertyPalette, onBack) {
        TruthRibbon("Property Passport", "Titularidad, finca, plano, gravámenes, uso de suelo e inspección mantienen evidencia independiente.", PropertyPalette)
        SectionTitle("Explorar", "La dirección exacta permanece protegida.", PropertyPalette)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("VENTA", "ALQUILER", "PREVENTA").forEachIndexed { index, label ->
                SegmentedButton(selected = operation == label, onClick = { operation = label }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(label, fontSize = 11.sp) }
            }
        }
        StatusCard("$operation · zona aproximada", "Los resultados publicados llegan desde la proyección local autorizada. Sin datos confirmados no se inventan listados.", Icons.Default.Map, PropertyPalette)
        SectionTitle("Pasaporte y cierre", "Los riesgos no caben en un único check.", PropertyPalette)
        ClaimRow("Titular registral", "Pendiente de evidencia del Registro", false, PropertyPalette)
        ClaimRow("Plano catastrado", "Dato no capturado", false, PropertyPalette)
        ClaimRow("Gravámenes", "Desconocido; no significa libre", false, PropertyPalette)
        ClaimRow("Inspección física", "No realizada", false, PropertyPalette)
        PrimaryAction("SOLICITAR DUE DILIGENCE LEGAL", Icons.Default.Balance, true, PropertyPalette, onOpenLegal)
    }
}

@Composable
fun FuelRewardsHub(onBack: () -> Unit) {
    val context = LocalContext.current
    var scanState by remember { mutableStateOf("LISTO PARA ESCANEAR") }
    val options = remember { GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build() }
    val scanner = remember(context) { GmsBarcodeScanning.getClient(context, options) }
    MarketHubScaffold("FUEL REWARDS", "Beneficios sin alterar la tarifa regulada", Icons.Default.LocalGasStation, FuelPalette, onBack) {
        TruthRibbon("Compra ≠ recompensa", "Sólo una compra liquidada y con fuente suficiente puede emitir beneficios.", FuelPalette)
        SectionTitle("Wallet", "Los cupones reales aparecen desde tu proyección local.", FuelPalette)
        StatusCard("SIN BENEFICIOS CONFIRMADOS", "No hay cupones sincronizados en esta vista. Escanea un QR de compra para solicitar validación.", Icons.Default.Wallet, FuelPalette)
        PrimaryAction("ESCANEAR QR SIN PERMISO DE CÁMARA", Icons.Default.QrCodeScanner, true, FuelPalette) {
            scanState = "ABRIENDO SCANNER…"
            scanner.startScan().addOnSuccessListener { barcode ->
                scanState = runCatching {
                    OpaqueQrToken.fromPublicUrl(barcode.rawValue.orEmpty(), setOf("meet.app", "elysium-vanguard.app"))
                    "TOKEN RECIBIDO · VALIDACIÓN DEL SERVIDOR PENDIENTE"
                }.getOrElse { "QR NO CONFIABLE · NO SE APLICÓ BENEFICIO" }
            }.addOnCanceledListener { scanState = "ESCANEO CANCELADO" }
                .addOnFailureListener { scanState = "SCANNER NO DISPONIBLE" }
        }
        Text(scanState, color = FuelPalette.warm, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        SectionTitle("Regla base", "Configurable por campaña y versionada.", FuelPalette)
        StatusCard("₡5.000 → 1 unidad elegible", "₡4.999 = 0 · ₡9.999 = 1 · ₡10.000 = 2. La redención sigue siendo atómica y autoritativa.", Icons.Default.ReceiptLong, FuelPalette)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MarketHubScaffold(title: String, subtitle: String, icon: ImageVector, palette: MarketPalette, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = Color.Transparent, topBar = {
        TopAppBar(title = { Column { Text(title, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text(subtitle, fontSize = 11.sp, color = palette.accent) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }, actions = { Icon(icon, null, tint = palette.accent, modifier = Modifier.padding(end = 18.dp)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.ink.copy(alpha = .96f), titleContentColor = Color.White, navigationIconContentColor = Color.White))
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
