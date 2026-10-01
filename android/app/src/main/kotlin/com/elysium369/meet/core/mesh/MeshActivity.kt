package com.elysium369.meet.core.mesh

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/** Internal foreground-only diagnostics and opt-in controls. No private content without reviewed E2EE. */
class MeshActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var db: MeshDatabase
    private lateinit var repository: MeshRepository
    private lateinit var coordinator: MeshCoordinator
    private lateinit var status: TextView
    private lateinit var peers: LinearLayout
    private lateinit var selection: Spinner
    private lateinit var passphrase: EditText
    private var ble: AndroidBleDiscoveryTransport? = null
    private var direct: AndroidWifiDirectTransport? = null
    private var aware: AndroidWifiAwareTransport? = null
    private var lan: AndroidLanDiscoveryTransport? = null
    private var startJob: Job? = null
    private var stopJob: Job? = null
    private var acceptedJob: Job? = null
    private var pendingPermissionStart = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val owner = intent.getStringExtra(EXTRA_OWNER)?.takeIf { it.isNotBlank() && it.length <= 128 }
        if (owner == null) { finish(); return }
        db = MeshDatabase.open(this)
        repository = MeshRepository(db, owner)
        coordinator = MeshCoordinator(scope, repository)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 32) }
        val scroll = ScrollView(this).apply { addView(content) }; setContentView(scroll)
        content.addView(TextView(this).apply { text = "Mesh nativo · participación voluntaria"; textSize = 23f })
        content.addView(TextView(this).apply { text = "Descubrimiento por radios Android. El cifrado de contenido y el relevo están bloqueados hasta revisar el protocolo, emparejar claves y verificarlo en dispositivos físicos. Un enlace no prueba entrega ni validez de evidencia." })
        selection = Spinner(this).apply { adapter = ArrayAdapter(this@MeshActivity, android.R.layout.simple_spinner_dropdown_item, listOf("BLE · señales pequeñas", "Wi-Fi Direct · datos", "Wi-Fi Aware · datos", "LAN · misma red local")) }; content.addView(selection)
        passphrase = EditText(this).apply { hint = "Wi-Fi Aware: frase idéntica en ambos teléfonos (8–63 caracteres)"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }; content.addView(passphrase)
        content.addView(Button(this).apply { text = "Activar descubrimiento seleccionado"; setOnClickListener { requestStart() } })
        content.addView(Button(this).apply { text = "Apagar radios Mesh"; setOnClickListener { startJob?.cancel(); scope.launch { coordinator.stop(); acceptedJob?.cancel() } } })
        content.addView(Button(this).apply { text = "Eliminar custodia local y observaciones"; setOnClickListener {
            android.app.AlertDialog.Builder(this@MeshActivity).setMessage("¿Eliminar los datos Mesh locales de esta cuenta? No elimina los reportes Safety ni los mensajes de otros sistemas.").setNegativeButton("Conservar", null).setPositiveButton("Eliminar") { _, _ -> scope.launch { coordinator.stop(); repository.clear(); status.text = "Datos Mesh locales eliminados" } }.show()
        } })
        status = TextView(this).apply { text = "Mesh apagado"; setPadding(0, 24, 0, 24) }; content.addView(status)
        content.addView(TextView(this).apply { text = "Teléfonos observados (sin identidad verificada)" })
        peers = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(peers)
        scope.launch { coordinator.state.collectLatest { state ->
            status.text = state.detail
            peers.removeAllViews()
            state.peers.forEach { peer -> peers.addView(Button(this@MeshActivity).apply {
                text = "${peer.bearer} · ${peer.rotatingId.take(12)} · probar enlace"
                setOnClickListener { scope.launch { connect(peer) } }
            }) }
        } }
        scope.launch { repository.heldCount.collectLatest { count -> title = "Mesh · $count en custodia local" } }
    }
    private fun permissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_ADVERTISE) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES) else add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
    }.toTypedArray()
    private fun requestStart() {
        val missing = permissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { pendingPermissionStart = true; requestPermissions(missing.toTypedArray(), REQUEST) } else startSelected()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST || !pendingPermissionStart) return
        pendingPermissionStart = false
        if (this.permissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) startSelected() else status.text = "Permisos no concedidos; Mesh permanece apagado"
    }
    private fun startSelected() {
        startJob?.cancel()
        startJob = scope.launch {
            stopJob?.join()
            coordinator.stop(); acceptedJob?.cancel(); clearTransports()
            try {
                when (selection.selectedItemPosition) {
                    0 -> {
                        val radio = AndroidBleDiscoveryTransport(this@MeshActivity); ble = radio
                        acceptedJob = scope.launch { radio.incomingLinks.collectLatest { coordinator.attach(it) } }
                        coordinator.start(radio)
                    }
                    1 -> {
                        val radio = AndroidWifiDirectTransport(this@MeshActivity, scope); direct = radio
                        acceptedJob = scope.launch { radio.lan.incomingLinks.collectLatest { coordinator.attach(it) } }
                        coordinator.start(radio)
                    }
                    2 -> {
                        val radio = AndroidWifiAwareTransport(this@MeshActivity, scope); aware = radio
                        radio.setLinkPassphrase(passphrase.text.toString())
                        acceptedJob = scope.launch { radio.lan.incomingLinks.collectLatest { coordinator.attach(it) } }
                        coordinator.start(radio)
                    }
                    else -> {
                        val radio = AndroidLanDiscoveryTransport(this@MeshActivity, scope); lan = radio
                        acceptedJob = scope.launch { radio.lan.incomingLinks.collectLatest { coordinator.attach(it) } }
                        coordinator.start(radio)
                    }
                }
            } catch (e: Exception) { if (e is CancellationException) throw e; status.text = "No disponible: ${e.message?.take(100)}" }
        }
    }
    private suspend fun connect(peer: MeshPeerAdvertisement) {
        try {
            when (peer.bearer) {
                MeshBearer.BLE -> ble?.let { coordinator.connect(peer, it) }
                MeshBearer.LAN -> lan?.let { coordinator.connect(peer, it) }
                MeshBearer.WIFI_AWARE -> aware?.let { coordinator.attach(it.establish(peer)) }
                MeshBearer.WIFI_DIRECT -> direct?.let { radio ->
                    val link = radio.establish(peer)
                    if (link == null) status.text = "Grupo Wi-Fi Direct formado; esperando socket del otro teléfono" else coordinator.attach(link)
                }
            }
        } catch (e: Exception) { if (e is CancellationException) throw e; status.text = "Enlace no completado: ${e.message?.take(100)}" }
    }
    private suspend fun clearTransports() {
        ble?.stop(); direct?.stop(); aware?.stop(); lan?.stop()
        ble = null; direct = null; aware = null; lan = null
    }
    override fun onStop() {
        pendingPermissionStart = false; startJob?.cancel()
        if (::coordinator.isInitialized) stopJob = scope.launch { coordinator.stop(); acceptedJob?.cancel(); clearTransports() }
        super.onStop()
    }
    override fun onDestroy() {
        if (::coordinator.isInitialized) scope.launch {
            try { stopJob?.join(); coordinator.stop(); acceptedJob?.cancel(); clearTransports(); withContext(Dispatchers.IO) { db.close() } }
            finally { scope.cancel() }
        } else scope.cancel()
        super.onDestroy()
    }
    companion object { const val EXTRA_OWNER = "mesh_owner"; private const val REQUEST = 3901 }
}
