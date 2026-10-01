package com.elysium.nexus.ui.wifi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.fabric.tv.adb.AdbAuthorization
import com.elysium.nexus.fabric.tv.adb.AdbAuthorizationStore
import com.elysium.nexus.fabric.tv.adb.AdbWirelessClient
import com.elysium.nexus.fabric.tv.adb.AndroidTvAdbAdapter
import com.elysium.nexus.fabric.tv.adb.SharedPrefsAdbAuthorizationStore
import com.elysium.nexus.fabric.tv.adb.resolveIdentity
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonSectionHeader
import com.elysium.nexus.ui.theme.NeonStatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CONTROL UNIVERSAL · WI-FI — control real de
 * Android TV / Google TV / Fire TV por ADB sobre
 * la red local.
 *
 * - Descubrimiento: mDNS `_adb._tcp` + barrido de la
 *   subred (puerto 5555) + entrada manual de IP.
 * - Emparejamiento: la llave RSA se PERSISTE
 *   ([AdbAuthorizationStore]); la TV muestra el dialog
 *   estándar "Allow USB debugging" UNA sola vez, en el
 *   primer contacto.
 * - Control: `input keyevent` — teclado de navegación,
 *   volumen, canales y transporte.
 */
@Composable
fun WifiUniversalScreen(
    onBack: () -> Unit,
    store: AdbAuthorizationStore? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authorizationStore: AdbAuthorizationStore = remember {
        store ?: SharedPrefsAdbAuthorizationStore.of(context)
    }

    var identity by remember { mutableStateOf<AdbAuthorization?>(null) }
    var devices by remember { mutableStateOf<List<DiscoveredTv>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var connectingIp by remember { mutableStateOf<String?>(null) }
    var activeIp by remember { mutableStateOf<String?>(null) }
    var model by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("") }
    var showManualIp by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    fun sendKey(event: String) {
        val ip = activeIp ?: return
        val auth = identity ?: return
        scope.launch {
            statusText = "Enviando $event…"
            try {
                withContext(Dispatchers.IO) {
                    val client = AdbWirelessClient(ip, 5555)
                    client.connect(auth, authorizationTimeoutMs = 30_000)
                    client.shell("input keyevent $event", auth)
                    client.disconnect()
                }
                statusText = "Listo"
            } catch (e: Exception) {
                statusText = "Sin respuesta: ${e.message}"
                activeIp = null
                model = null
            }
        }
    }

    suspend fun scan() {
        scanning = true
        devices = emptyList()
        statusText = "Buscando dispositivos en la red…"
        try {
            val found = withContext(Dispatchers.IO) {
                val adapter = AndroidTvAdbAdapter(ip = "0.0.0.0", context = context)
                val mdns = runCatching { adapter.discover(timeoutMs = 2_000) }
                    .getOrElse { emptyList() }
                val sweep = subnetAdbHosts(context)
                (mdns.map { it.ipAddress } + sweep).distinct().map { DiscoveredTv(it) }
            }
            devices = found
            statusText = if (found.isEmpty()) "No se encontraron TVs · añade la IP manualmente" else ""
        } catch (e: Exception) {
            statusText = "Error de escaneo: ${e.message}"
        } finally {
            scanning = false
        }
    }

    LaunchedEffect(Unit) {
        identity = withContext(Dispatchers.IO) { authorizationStore.resolveIdentity() }
        scan()
    }

    fun connect(ip: String) {
        val auth = identity ?: return
        connectingIp = ip
        statusText = "Conectando a $ip… acepta \"Allow USB debugging\" en la TV la primera vez"
        scope.launch {
            try {
                val foundModel = withContext(Dispatchers.IO) {
                    val client = AdbWirelessClient(ip, 5555)
                    client.connect(auth, authorizationTimeoutMs = 90_000)
                    val m = client.shell("getprop ro.product.model", auth, timeoutMs = 10_000)
                        .trim().ifBlank { null }
                    client.disconnect()
                    m
                }
                activeIp = ip
                model = foundModel
                statusText = if (foundModel != null) "Conectado · $foundModel" else "Conectado"
                AdbTvMemory(context).setLastTv(ip)
            } catch (e: Exception) {
                statusText = "Fallo al conectar: ${e.message}"
            } finally {
                connectingIp = null
            }
        }
    }

    LaunchedEffect(identity) {
        val remembered = identity?.let { AdbTvMemory(context).lastTvIp() }
        if (remembered != null && activeIp == null && connectingIp == null) {
            connect(remembered)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("← Atrás") }
            if (scanning) {
                NeonChip(label = "Escaneando…", onClick = { })
            } else {
                TextButton(onClick = { scope.launch { scan() } }) { Text("↻ Rescanear") }
            }
        }

        NeonSectionHeader(text = "CONTROL UNIVERSAL · WI-FI")

        if (activeIp != null) {
            Keypad(
                model = model,
                onKey = ::sendKey,
                onDisconnect = {
                    activeIp = null
                    model = null
                    statusText = "Desconectado"
                }
            )
        } else {
            if (devices.isEmpty() && !scanning && statusText.isNotBlank()) {
                NeonCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = ElysiumColors.OnSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (devices.isNotEmpty()) {
                NeonSectionHeader(text = "Dispositivos encontrados (${devices.size})")
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(devices, key = { it.ip }) { tv ->
                        NeonCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = connectingIp == null) { connect(tv.ip) },
                            accent = if (connectingIp == tv.ip) ElysiumColors.NeonOrange else ElysiumColors.NeonCyan,
                            cornerRadius = 16.dp,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ElysiumColors.NeonCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "TV",
                                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
                                        color = ElysiumColors.NeonCyan
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tv.ip,
                                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                                        color = ElysiumColors.OnSurface
                                    )
                                    Text(
                                        if (connectingIp == tv.ip) "Esperando autorización en la TV…" else "Android TV · ADB · 5555",
                                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                        color = ElysiumColors.OnSurfaceVariant
                                    )
                                }
                                NeonStatusPill(
                                    label = if (connectingIp == tv.ip) "…" else "Conectar",
                                    color = if (connectingIp == tv.ip) ElysiumColors.NeonOrange else ElysiumColors.NeonCyan
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (showManualIp) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("IP del TV (ej. 192.168.1.50)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { if (manualIp.isNotBlank()) connect(manualIp.trim()) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("Conectar") }
            } else {
                TextButton(
                    onClick = { showManualIp = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("+ Añadir IP manualmente") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (statusText.isNotBlank() && (devices.isNotEmpty() || showManualIp)) {
                Text(
                    text = statusText,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = ElysiumColors.OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        DisposableEffect(Unit) { onDispose { } }
    }
}

private data class DiscoveredTv(val ip: String)

/**
 * Barrido de la subred local (puerto 5555, ADB legacy)
 * con probing paralelo — mismo patrón que el
 * [com.elysium.nexus.core.transport.mac.FastHostScanner].
 */
private suspend fun subnetAdbHosts(context: android.content.Context): List<String> =
    coroutineScope {
        val wifiIp = wifiIpAddress(context) ?: return@coroutineScope emptyList()
        val prefix = wifiIp.substringBeforeLast(".")
        val candidates = (1..254).map { "$prefix.$it" }
        candidates
            .map { ip ->
                async(Dispatchers.IO) {
                    ip to com.elysium.nexus.core.transport.mac.FastHostScanner.isPortOpen(ip, 5555, 400)
                }
            }
            .awaitAll()
            .filter { it.second }
            .map { it.first }
    }

private fun wifiIpAddress(context: android.content.Context): String? {
    return try {
        val manager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val link = manager.getLinkProperties(manager.activeNetwork) ?: return null
        link.linkAddresses
            .firstOrNull { it.address is java.net.Inet4Address }
            ?.address?.hostAddress
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun Keypad(
    model: String?,
    onKey: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        NeonCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "CONECTADO",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp),
                        color = ElysiumColors.NeonGreen
                    )
                    Text(
                        model ?: "Android TV",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = ElysiumColors.OnSurfaceVariant
                    )
                }
                NeonStatusPill(label = "ADB", color = ElysiumColors.NeonGreen)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Teclas del dispositivo
        KeyRow(onKey = onKey, keys = listOf(KeyDef("", null), KeyDef("▲", "19"), KeyDef("", null)))
        KeyRow(
            onKey = onKey,
            keys = listOf(
                KeyDef("◀", "21"),
                KeyDef("OK", "23", accent = ElysiumColors.NeonMagenta, big = true),
                KeyDef("▶", "22")
            )
        )
        KeyRow(onKey = onKey, keys = listOf(KeyDef("", null), KeyDef("▼", "20"), KeyDef("", null)))
        Spacer(modifier = Modifier.height(10.dp))

        KeyRow(onKey = onKey, keys = listOf(KeyDef("〽 Vol−", "24"), KeyDef("🔇", "164"), KeyDef("Vol+ 〽", "25")))
        KeyRow(onKey = onKey, keys = listOf(KeyDef("⏻", "26"), KeyDef("⌂", "3"), KeyDef("≡", "82")))
        Spacer(modifier = Modifier.height(10.dp))

        KeyRow(onKey = onKey, keys = listOf(KeyDef("⏮", "88"), KeyDef("⏯", "85"), KeyDef("⏭", "87")))
        KeyRow(onKey = onKey, keys = listOf(KeyDef("Channel −", "402"), KeyDef("⏹", "86"), KeyDef("Channel +", "403")))
        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Desconectar", color = ElysiumColors.NeonMagenta)
        }
    }
}

private data class KeyDef(
    val label: String,
    val keyCode: String?,
    val accent: Color = ElysiumColors.NeonCyan,
    val big: Boolean = false
)

@Composable
private fun KeyRow(
    onKey: (String) -> Unit,
    keys: List<KeyDef>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        keys.forEach { k ->
            Box(
                modifier = Modifier
                    .weight(if (k.big) 1.4f else 1f)
                    .height(if (k.big) 74.dp else 58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (k.label.isBlank()) Color.Transparent
                        else k.accent.copy(alpha = 0.18f)
                    )
                    .clickable(enabled = k.keyCode != null) { k.keyCode?.let(onKey) },
                contentAlignment = Alignment.Center
            ) {
                if (k.label.isNotBlank()) {
                    Text(
                        k.label,
                        style = TextStyle(
                            fontSize = (if (k.big) 15 else 14).sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = k.accent
                    )
                }
            }
        }
    }
}