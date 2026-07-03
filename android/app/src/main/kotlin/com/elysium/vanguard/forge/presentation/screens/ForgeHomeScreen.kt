package com.elysium.vanguard.forge.presentation.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.elysium.vanguard.forge.presentation.components.NeonCard
import com.elysium.vanguard.forge.presentation.components.SectionHeader
import com.elysium.vanguard.forge.presentation.components.StatusLed
import com.elysium.vanguard.forge.presentation.components.TechLabel
import com.elysium.vanguard.forge.presentation.state.ForgeHomeEvent
import com.elysium.vanguard.forge.presentation.state.ForgeUiState
import com.elysium.vanguard.forge.presentation.theme.ForgeColors
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeHomeViewModel

/**
 * ForgeHomeScreen — punto de entrada del módulo Forge.
 *
 * Reglas:
 * - Loading / Ready / Empty / Error según UiState.
 * - Sin lógica de negocio — dispara eventos al ViewModel.
 * - Identidad visual ELYSIUM (cards neón, oscuro, monospace).
 */
@Composable
fun ForgeHomeScreen(
    viewModel: ForgeHomeViewModel,
    onNavigate: (destination: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val state = uiState) {
            is ForgeUiState.Loading -> LoadingState()
            is ForgeUiState.Empty -> EmptyState(
                onCreatePart = { onNavigate("forge/part-editor") }
            )
            is ForgeUiState.Error -> ErrorState(state.message)
            is ForgeUiState.Ready -> ReadyContent(
                library = state.library,
                bootstrapReport = state.bootstrapReport,
                onEvent = viewModel::onEvent,
                onNavigate = onNavigate
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = ForgeColors.Primary)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "CARGANDO BIBLIOTECA FORGE",
            color = ForgeColors.Primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun EmptyState(onCreatePart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "VANGUARD FORGE",
            color = ForgeColors.Primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "El laboratorio mecánico universal",
            color = ForgeColors.OnSurface.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(32.dp))
        NeonCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = ForgeColors.Primary,
            onClick = onCreatePart
        ) {
            HomeActionRow(
                icon = Icons.Default.Build,
                title = "Crear primera pieza",
                subtitle = "Empezar desde primitivas o plantilla",
                color = ForgeColors.Primary
            )
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = ForgeColors.Error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            color = ForgeColors.Error,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ReadyContent(
    library: ForgeUiState.ForgeLibrary,
    bootstrapReport: com.elysium.vanguard.forge.data.ForgeArtifactRepository.BootstrapReport?,
    onEvent: (ForgeHomeEvent) -> Unit,
    onNavigate: (String) -> Unit
) {
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
        Header(library = library, bootstrapReport = bootstrapReport)

        Spacer(Modifier.height(16.dp))
        SectionHeader("CREAR")
        HomeGrid(actionTiles.filter { it.section == HomeSection.CREATE }) { event ->
            onEvent(event)
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("SIMULAR")
        HomeGrid(actionTiles.filter { it.section == HomeSection.SIMULATE }) { event ->
            onEvent(event)
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("BIBLIOTECAS")
        HomeGrid(actionTiles.filter { it.section == HomeSection.LIBRARY }) { event ->
            onEvent(event)
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("DIAGNÓSTICO")
        HomeGrid(actionTiles.filter { it.section == HomeSection.DIAGNOSE }) { event ->
            onEvent(event)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Header(
    library: ForgeUiState.ForgeLibrary,
    bootstrapReport: com.elysium.vanguard.forge.data.ForgeArtifactRepository.BootstrapReport?
) {
    val failures = bootstrapReport?.failures.orEmpty()
    val hasFailures = failures.isNotEmpty()
    val isLoaded = library.totalArtifacts > 0

    val ledLabel: String
    val ledColor: Color
    when {
        hasFailures && isLoaded -> {
            ledLabel = "CARGA PARCIAL · ${failures.size} ERROR"
            ledColor = ForgeColors.Warning
        }
        hasFailures && !isLoaded -> {
            ledLabel = "ERROR DE ASSETS · ${failures.size}"
            ledColor = ForgeColors.Error
        }
        isLoaded -> {
            ledLabel = "BIBLIOTECA CARGADA"
            ledColor = ForgeColors.Success
        }
        else -> {
            ledLabel = "SIN DATOS"
            ledColor = ForgeColors.Warning
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "VANGUARD FORGE",
            color = ForgeColors.Primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Crea piezas · Ensambla sistemas · Simula fallas · Repara en 3D",
            color = ForgeColors.OnSurface.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLed(label = ledLabel, color = ledColor)
            Spacer(Modifier.width(12.dp))
            TechLabel("PIEZAS: ${library.partCount}")
            Spacer(Modifier.width(12.dp))
            TechLabel("ENSAMBLES: ${library.assemblyCount}")
            Spacer(Modifier.width(12.dp))
            TechLabel("VEHÍCULOS: ${library.vehicleCount}")
        }
        if (hasFailures) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = failures.joinToString(separator = " · ") { it.take(60) },
                color = ForgeColors.Error.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun HomeGrid(
    tiles: List<HomeTile>,
    onEvent: (ForgeHomeEvent) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(((tiles.size + 1) / 2 * 130).dp),
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tiles) { tile ->
            NeonCard(
                modifier = Modifier.fillMaxWidth().height(118.dp),
                accentColor = tile.color,
                onClick = { onEvent(tile.event) }
            ) {
                HomeActionRow(
                    icon = tile.icon,
                    title = tile.title,
                    subtitle = tile.subtitle,
                    color = tile.color
                )
            }
        }
    }
}

@Composable
private fun HomeActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

// ─────────── Tile model + static catalog ───────────

private enum class HomeSection { CREATE, SIMULATE, LIBRARY, DIAGNOSE }

private data class HomeTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val section: HomeSection,
    val event: ForgeHomeEvent
)

private val actionTiles = listOf(
    HomeTile(
        "Crear pieza", "Primitivas + plantillas",
        Icons.Default.Build, ForgeColors.Primary, HomeSection.CREATE,
        ForgeHomeEvent.OnCreatePart
    ),
    HomeTile(
        "Crear ensamble", "Piezas + joints",
        Icons.Default.Engineering, ForgeColors.Primary, HomeSection.CREATE,
        ForgeHomeEvent.OnCreateAssembly
    ),
    HomeTile(
        "Construir vehículo", "Sistemas completos",
        Icons.Default.DirectionsCar, ForgeColors.Primary, HomeSection.CREATE,
        ForgeHomeEvent.OnCreateVehicle
    ),
    HomeTile(
        "Simulación física", "play / pause / overlays",
        Icons.Default.Memory, ForgeColors.Secondary, HomeSection.SIMULATE,
        ForgeHomeEvent.OnOpenSimulation
    ),
    HomeTile(
        "Simular motor", "IGNITION / START / RPM",
        Icons.Default.PowerSettingsNew, ForgeColors.Secondary, HomeSection.SIMULATE,
        ForgeHomeEvent.OnOpenEngineRuntime
    ),
    HomeTile(
        "Failure Lab", "Inyectar daño",
        Icons.Default.ReportProblem, ForgeColors.Secondary, HomeSection.DIAGNOSE,
        ForgeHomeEvent.OnOpenFailureLab
    ),
    HomeTile(
        "Diagnóstico", "Causas + probabilidad",
        Icons.Default.Warning, ForgeColors.Secondary, HomeSection.DIAGNOSE,
        ForgeHomeEvent.OnOpenDiagnostics
    ),
    HomeTile(
        "Manuales", "Reparación + reemplazo",
        Icons.Default.Inventory, ForgeColors.Accent, HomeSection.LIBRARY,
        ForgeHomeEvent.OnOpenManuals
    ),
    HomeTile(
        "Materiales", "Comparar propiedades",
        Icons.Default.Storage, ForgeColors.Accent, HomeSection.LIBRARY,
        ForgeHomeEvent.OnOpenMaterials
    ),
    HomeTile(
        "Fabricación", "Procesos + máquinas",
        Icons.Default.Plumbing, ForgeColors.Accent, HomeSection.LIBRARY,
        ForgeHomeEvent.OnOpenManufacturing
    ),
    HomeTile(
        "Mis artefactos", "Biblioteca local",
        Icons.Default.LocalShipping, ForgeColors.Tertiary, HomeSection.LIBRARY,
        ForgeHomeEvent.OnOpenMyArtifacts
    ),
    HomeTile(
        "Acerca de Forge", "Versión + diagnósticos",
        Icons.Default.Settings, ForgeColors.Tertiary, HomeSection.LIBRARY,
        ForgeHomeEvent.OnRefresh
    )
)