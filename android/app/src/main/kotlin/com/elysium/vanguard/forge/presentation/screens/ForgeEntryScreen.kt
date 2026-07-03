package com.elysium.vanguard.forge.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.elysium.vanguard.forge.presentation.navigation.ForgeNavGraph
import com.elysium.vanguard.forge.presentation.theme.ForgeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Pantalla wrapper del módulo Forge con chrome profesional:
 *  - Status bar ELYSIUM gradient
 *  - Header con logo + versión + provenance badge
 *  - Sub-NavHost que monta ForgeNavGraph
 *  - Back button funcional
 *
 * Es la única superficie que el MainActivity de MEET conoce — no expone
 * rutas internas a MainActivity, encapsulando el módulo.
 */
@Composable
fun ForgeEntryScreen(
    onClose: () -> Unit,
    parentNavController: androidx.navigation.NavController? = null,
    forgeBuildVersion: String = "0.1.0",
    provenanceMode: ForgeProvenanceMode = ForgeProvenanceMode.OFFLINE
) {
    val nestedNav = rememberNavController()
    val context = LocalContext.current
    val repo = remember { com.elysium.vanguard.forge.data.ForgeArtifactRepository.shared }

    // Bootstrap de seeds desde assets. Se ejecuta una sola vez al entrar al módulo.
    // El VM no recibe Context; el repo se inicializa aquí, en la capa de UI.
    LaunchedEffect(Unit) {
        android.util.Log.i("ForgeEntry", "Bootstrap starting...")
        try {
            val report = repo.bootstrapFromAssets(context)
            android.util.Log.i(
                "ForgeEntry",
                "Bootstrap done: total=${report.totalLoaded} mats=${report.materialsLoaded} " +
                    "procs=${report.processesLoaded} parts=${report.partsLoaded} " +
                    "assemblies=${report.assembliesLoaded} failures=${report.failures.size}"
            )
            if (report.failures.isNotEmpty()) {
                report.failures.forEach { android.util.Log.e("ForgeEntry", "Bootstrap failure: $it") }
            }
            // Cargar parts creados por el usuario en sesiones previas desde
            // filesDir/forge_user_parts.json. Merge sobre los seeds.
            val loaded = com.elysium.vanguard.forge.data.ForgeArtifactRepository
                .loadUserPartsFromDisk(context)
            android.util.Log.i("ForgeEntry", "User parts loaded from disk: $loaded")
        } catch (e: Throwable) {
            android.util.Log.e("ForgeEntry", "Bootstrap crashed", e)
        }
    }

    // Persistencia: al salir del módulo (ON_STOP, background, finish) volcamos
    // los parts del usuario a disco para que sobrevivan al cierre del proceso.
    // Sin esto, "kill process" pierde todos los cambios (limitación V0 que este
    // commit cierra).
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        com.elysium.vanguard.forge.data.ForgeArtifactRepository
                            .saveUserPartsToDisk(context)
                        android.util.Log.i("ForgeEntry", "User parts flushed to disk on ON_STOP")
                    } catch (e: Throwable) {
                        android.util.Log.e("ForgeEntry", "Failed to flush to disk", e)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ForgeColors.Background,
                        ForgeColors.Surface,
                        ForgeColors.Background
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            ForgeTopBar(
                version = forgeBuildVersion,
                provenance = provenanceMode,
                onClose = onClose
            )
            ForgeNavGraph(navController = nestedNav)
        }
    }
}

/**
 * Header profesional del módulo Forge.
 */
@Composable
private fun ForgeTopBar(
    version: String,
    provenance: ForgeProvenanceMode,
    onClose: () -> Unit
) {
    val provenanceColor = when (provenance) {
        ForgeProvenanceMode.REAL -> Color(0xFF00FFA3)
        ForgeProvenanceMode.OFFLINE -> Color(0xFF00E5FF)
        ForgeProvenanceMode.SIMULATED -> Color(0xFFFFB300)
        ForgeProvenanceMode.SIN_ENLACE -> Color(0xFFFF5252)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        ForgeColors.Surface,
                        ForgeColors.Background.copy(alpha = 0.0f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ForgeColors.Surface.copy(alpha = 0.6f))
                    .border(1.dp, ForgeColors.Primary.copy(alpha = 0.3f), CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Cerrar Forge",
                    tint = ForgeColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Logo + version
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VANGUARD FORGE",
                    color = ForgeColors.Primary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "v$version",
                    color = ForgeColors.OnSurface.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }

            // Provenance badge
            ProvenanceBadgeCompact(label = provenance.label, color = provenanceColor)
        }
    }
}

@Composable
private fun ProvenanceBadgeCompact(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Provenance visible para el usuario del módulo Forge.
 *
 * Refleja la fuente de los datos que Forge muestra:
 *  - REAL: datos del enlace OBD activo (lecturas físicas reales)
 *  - OFFLINE: biblioteca local sin enlace (cálculos sobre datos cacheados)
 *  - SIMULATED: simulador educativo propio (sin garantías de equivalencia con realidad)
 *  - SIN_ENLACE: no hay forma de verificar (sin OBD, sin cache, sin simulación)
 */
enum class ForgeProvenanceMode(val label: String) {
    REAL("REAL"),
    OFFLINE("OFFLINE"),
    SIMULATED("SIMULATED"),
    SIN_ENLACE("SIN ENLACE")
}