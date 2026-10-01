package com.elysium369.meet.ui.screens.services

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.screens.UniversalServicesScreen

data class ServicesOfferDraft(val priceCrc: Long, val hours: Double, val warrantyDays: Int, val note: String)
val LocalServiceOfferDraft = staticCompositionLocalOf<(ServicesOfferDraft) -> Unit> { {} }

val LocalServiceOnlineAction = staticCompositionLocalOf<(String) -> Unit> { {} }

/** A detailed catalog draft is intent only; publishing remains in the authenticated online flow. */
data class ServicesRequestDraft(
    val definitionId: String, val title: String, val description: String,
    val location: String, val priceCrc: Long, val modality: String,
    val latitude: Double? = null, val longitude: Double? = null,
)

@Composable
fun ServicesExperienceScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    onMessages: () -> Unit,
    onActive: () -> Unit,
    onHistory: () -> Unit,
    onProviderConfig: () -> Unit,
    onServiceMessages: (String) -> Unit,
    initialPane: Int = 0,
    initialRequestDraft: ServicesRequestDraft? = null,
    initialHistory: Boolean = false,
) {
    val principal by viewModel.activePrincipal.collectAsState()
    val actor = principal?.id
    var pane by rememberSaveable(actor) { mutableIntStateOf(initialPane.coerceIn(0, 2)) }
    var draft by remember(actor, initialRequestDraft) { mutableStateOf(initialRequestDraft) }
    var offerDraft by remember(actor) { mutableStateOf<ServicesOfferDraft?>(null) }
    var authorityNotice by remember(actor) { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalServiceOnlineAction provides { message -> authorityNotice = message; pane = 1 },
        LocalServiceOfferDraft provides { value -> offerDraft = value; pane = 1; authorityNotice = "Contraoferta conservada. Selecciona su solicitud en línea y revisa los datos antes de enviarla." }) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Catálogo y oficios", "En línea", "Cotización avanzada").forEachIndexed { index, label ->
                FilterChip(modifier = Modifier.weight(1f), selected = pane == index,
                    onClick = { pane = index }, label = { Text(label) })
            }
        }
        authorityNotice?.let { Text(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
        if (pane != 1) Text("Los registros anteriores son historial local. Publicación, propuestas y cierre se confirman en En línea.", modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (pane) {
                0 -> UniversalServicesScreen(viewModel, onBack, onMessages, onActive, onHistory,
                    onProviderConfig, onOpenAdvancedMarketplace = { pane = 1 },
                    onPrepareRequest = { draft = it; pane = 1 })
                1 -> UnifiedServicesScreen(viewModel, onBack, onMessages, onProviderConfig,
                    onAdvanced = { pane = 2 }, onServiceMessages = onServiceMessages,
                    requestDraft = draft, onDraftConsumed = { draft = null },
                    offerDraft = offerDraft, onOfferDraftConsumed = { offerDraft = null },
                    initialHistory = initialHistory)
                else -> ElysiumServicesMarketplaceScreen(navController, viewModel, onPrepareRequest = { draft = it; pane = 1 })
            }
        }
    }
}
}
