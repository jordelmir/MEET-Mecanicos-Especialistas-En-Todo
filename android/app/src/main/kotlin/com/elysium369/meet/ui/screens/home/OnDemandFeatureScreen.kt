package com.elysium369.meet.ui.screens.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus

/** Google Play delivers optional feature code; no APK sideloading or executable downloads. */
@Composable
fun OnDemandFeatureScreen(module: String, activityClass: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { SplitInstallManagerFactory.create(context) }
    var installed by remember(module) { mutableStateOf(module in manager.installedModules) }
    var downloading by remember(module) { mutableStateOf(false) }
    var progress by remember(module) { mutableStateOf(0f) }
    var message by remember(module) { mutableStateOf<String?>(null) }

    DisposableEffect(manager, module) {
        val listener = SplitInstallStateUpdatedListener { state ->
            if (module !in state.moduleNames()) return@SplitInstallStateUpdatedListener
            when (state.status()) {
                SplitInstallSessionStatus.DOWNLOADING -> {
                    downloading = true
                    if (state.totalBytesToDownload() > 0L) {
                        progress = state.bytesDownloaded().toFloat() / state.totalBytesToDownload().toFloat()
                    }
                }
                SplitInstallSessionStatus.INSTALLED -> {
                    com.google.android.play.core.splitinstall.SplitInstallHelper.updateAppInfo(context)
                    installed = true
                    downloading = false
                    message = "Función instalada. Ya puedes abrirla."
                }
                SplitInstallSessionStatus.FAILED, SplitInstallSessionStatus.CANCELED -> {
                    downloading = false
                    message = "No se completó la descarga. Comprueba la conexión, el espacio y Google Play; vuelve a intentarlo."
                }
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    downloading = false
                    message = "Google Play requiere confirmar esta descarga."
                }
            }
        }
        manager.registerListener(listener)
        onDispose { manager.unregisterListener(listener) }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Volver a Elysium") }
        Text(title)
        Text("Esta función se descarga dentro de Elysium desde Google Play cuando decides usarla.")
        message?.let { Text(it) }
        if (downloading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = !downloading,
            onClick = {
                if (installed || module in manager.installedModules) {
                    runCatching {
                        context.startActivity(Intent().setClassName(context.packageName, activityClass))
                    }.onFailure { message = "La función está instalada pero no se pudo abrir. Reinicia Elysium e inténtalo otra vez." }
                } else {
                    downloading = true
                    message = null
                    manager.startInstall(SplitInstallRequest.newBuilder().addModule(module).build())
                        .addOnFailureListener {
                            downloading = false
                            message = "Google Play no pudo iniciar la descarga. Se requiere la versión instalada desde Play."
                        }
                }
            },
        ) { Text(if (installed) "Abrir $title" else if (downloading) "Descargando…" else "Descargar $title") }
    }
}
