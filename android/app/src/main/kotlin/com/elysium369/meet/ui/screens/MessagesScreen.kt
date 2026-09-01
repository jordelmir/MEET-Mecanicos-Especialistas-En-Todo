package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.communications.*
import com.elysium369.meet.ui.CommunicationViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.text.DateFormat
import java.util.Date

private enum class MessagesPane { INBOX, DISCOVER, CONTACTS, CALLS, MESH, SETTINGS, BLOCKED }

@Composable
fun MessagesScreen(
    onBack: () -> Unit,
    serviceVertical: String? = null,
    serviceReferenceId: String? = null,
    serviceTitle: String? = null,
    viewModel: CommunicationViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val selected by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    val privacy by viewModel.privacy.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedContacts.collectAsStateWithLifecycle()
    val searchOutcome by viewModel.searchOutcome.collectAsStateWithLifecycle()
    val searching by viewModel.searchInProgress.collectAsStateWithLifecycle()
    val voiceNoteState by viewModel.voiceNoteState.collectAsStateWithLifecycle()
    var pane by remember { mutableStateOf(MessagesPane.INBOX) }
    val context = LocalContext.current

    val invite = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Únete a Elysium Vanguard para conversar y llamar de forma privada. Descarga oficial: ${BuildConfig.ELYSIUM_DOWNLOAD_URL}",
            )
        }
        context.startActivity(Intent.createChooser(send, "Invitar a Elysium"))
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startCall() else viewModel.microphonePermissionDenied()
    }
    val voiceMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceNote() else viewModel.microphonePermissionDenied()
    }
    val startCall = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCall()
        } else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }
    val startVoiceNote = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startVoiceNote()
        } else voiceMicrophone.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(serviceVertical, serviceReferenceId, serviceTitle) {
        if (!serviceVertical.isNullOrBlank() && !serviceReferenceId.isNullOrBlank()) {
            viewModel.openServiceContext(
                serviceVertical,
                serviceReferenceId,
                serviceTitle?.takeIf(String::isNotBlank) ?: "Conversación del servicio",
            )
        }
    }

    Surface(Modifier.fillMaxSize(), color = MeetColors.backgroundDeep) {
        Column(Modifier.fillMaxSize()) {
            MessagesHeader(
                title = selected?.title ?: pane.title,
                subtitle = selected?.let { "Conversación privada Elysium" }
                    ?: identity?.elysiumId?.let { "@$it · Elysium Communications" }
                    ?: "Elysium Communications",
                onBack = {
                    when {
                        selected != null -> viewModel.selectConversation(null)
                        pane != MessagesPane.INBOX -> pane = MessagesPane.INBOX
                        else -> onBack()
                    }
                },
                onSearch = if (selected == null && pane != MessagesPane.DISCOVER) ({ pane = MessagesPane.DISCOVER }) else null,
                onSettings = if (selected == null && pane != MessagesPane.SETTINGS) ({ pane = MessagesPane.SETTINGS }) else null,
                onCall = selected?.let { startCall },
                onEndCall = if (callState == CallConnectionState.ACTIVE || callState == CallConnectionState.CONNECTING) viewModel::endCall else null,
            )
            notice?.let { HonestBanner(it) }
            when {
                selected != null -> ConversationBody(
                    conversation = selected!!,
                    messages = messages,
                    voiceNoteState = voiceNoteState,
                    onSend = viewModel::sendText,
                    onStartVoice = startVoiceNote,
                    onStopVoice = viewModel::stopAndSendVoiceNote,
                    onCancelVoice = viewModel::cancelVoiceNote,
                )
                pane == MessagesPane.INBOX -> Inbox(conversations, contacts, serviceVertical, { viewModel.selectConversation(it.id) }) { pane = it }
                pane == MessagesPane.DISCOVER -> DiscoverPane(searchOutcome, searching, viewModel::searchContact, viewModel::requestContact, invite)
                pane == MessagesPane.CONTACTS -> ContactsPane(contacts, viewModel::block, invite)
                pane == MessagesPane.CALLS -> CallsPane(conversations) { viewModel.selectConversation(it.id) }
                pane == MessagesPane.MESH -> MeshPane(privacy, viewModel::savePrivacy)
                pane == MessagesPane.SETTINGS -> SettingsPane(identity, privacy, viewModel::saveIdentity, viewModel::savePrivacy) { pane = MessagesPane.BLOCKED }
                pane == MessagesPane.BLOCKED -> BlockedPane(blocked, viewModel::unblock)
            }
        }
    }
}

private val MessagesPane.title: String get() = when (this) {
    MessagesPane.INBOX -> "Mensajes y llamadas"
    MessagesPane.DISCOVER -> "Encontrar personas"
    MessagesPane.CONTACTS -> "Contactos Elysium"
    MessagesPane.CALLS -> "Llamadas"
    MessagesPane.MESH -> "Vanguard Mesh"
    MessagesPane.SETTINGS -> "Privacidad y cuenta"
    MessagesPane.BLOCKED -> "Usuarios bloqueados"
}

@Composable
private fun MessagesHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onSearch: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    onCall: (() -> Unit)?,
    onEndCall: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Color.White) }
        Box(Modifier.size(38.dp).background(MeetColors.cyberCyan.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MeetColors.cyberCyan)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
            Text(subtitle, color = MeetColors.textSecondary, fontSize = 10.sp, maxLines = 1)
        }
        onSearch?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Search, "Buscar", tint = Color.White) } }
        onSettings?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Settings, "Ajustes", tint = Color.White) } }
        if (onEndCall != null) IconButton(onClick = onEndCall) { Icon(Icons.Outlined.CallEnd, "Finalizar", tint = MeetColors.error) }
        else onCall?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Call, "Llamar", tint = MeetColors.neonGreen) } }
    }
}

@Composable
private fun Inbox(
    conversations: List<ConversationSummary>,
    contacts: List<ElysiumContact>,
    requestedVertical: String?,
    onOpen: (ConversationSummary) -> Unit,
    onPane: (MessagesPane) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(4.dp)) }
        if (!requestedVertical.isNullOrBlank()) item { HonestBanner("Habla con el proveedor autorizado sin revelar tu número telefónico.") }
        item {
            Column(Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = .08f), RoundedCornerShape(20.dp)).border(1.dp, MeetColors.cyberCyan.copy(alpha = .28f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                Text("Tu red, bajo tus reglas", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("Mensajes, llamadas, servicios y comunicación cercana. El teléfono es opcional; no usamos SMS para iniciar sesión.", color = MeetColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryAction("Nuevo mensaje", Icons.Outlined.PersonAdd, Modifier.weight(1f)) { onPane(MessagesPane.DISCOVER) }
                    PrimaryAction("Llamar", Icons.Outlined.Call, Modifier.weight(1f)) { onPane(MessagesPane.CALLS) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactAction("Contactos", Icons.Outlined.Contacts, contacts.size.toString(), Modifier.weight(1f)) { onPane(MessagesPane.CONTACTS) }
                CompactAction("Mesh", Icons.Outlined.WifiTethering, "sin internet", Modifier.weight(1f)) { onPane(MessagesPane.MESH) }
                CompactAction("Privacidad", Icons.Outlined.Shield, "control", Modifier.weight(1f)) { onPane(MessagesPane.SETTINGS) }
            }
        }
        item { SectionTitle("Conversaciones", if (conversations.isEmpty()) "sin actividad" else "${conversations.size} activas") }
        if (conversations.isEmpty()) item { EmptyPanel("Busca un ID Elysium, correo o teléfono. Si aún no participa, invítalo con el enlace oficial de GitHub.") }
        else items(conversations, key = { it.id }) { row -> ConversationRow(row) { onOpen(row) } }
        item { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable
private fun DiscoverPane(
    outcome: ContactSearchOutcome?,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onRequest: (ContactSearchResult) -> Unit,
    onInvite: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Habla con quien tú elijas", color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp)
            Text("Búsqueda exacta y privada. No exponemos una lista global de usuarios.", color = MeetColors.textSecondary, fontSize = 12.sp)
        }
        item {
            OutlinedTextField(
                query,
                { query = it.take(320) },
                Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("@ID Elysium, correo o teléfono") },
                supportingText = { Text("Teléfono opcional. Nunca se usa para SMS.") },
                singleLine = true,
            )
        }
        item {
            Button({ onSearch(query) }, Modifier.fillMaxWidth(), enabled = query.isNotBlank() && !searching) {
                if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Search, null)
                Text(if (searching) "Buscando…" else "Buscar de forma exacta", Modifier.padding(start = 8.dp))
            }
        }
        when (outcome) {
            is ContactSearchOutcome.Found -> items(outcome.contacts, key = { it.principalId }) { contact -> SearchResultCard(contact) { onRequest(contact) } }
            is ContactSearchOutcome.NotFound -> item { NotFoundCard(onInvite) }
            ContactSearchOutcome.InvalidQuery -> item { HonestBanner("Escribe un ID válido, correo completo o teléfono con código de país.") }
            ContactSearchOutcome.AuthenticationRequired -> item { HonestBanner("Inicia sesión para consultar el directorio privado. También podrás emparejar en persona por QR.") }
            ContactSearchOutcome.RateLimited -> item { HonestBanner("Pausa de seguridad: se alcanzó el límite de búsquedas. Inténtalo más tarde.") }
            ContactSearchOutcome.ServiceUnavailable -> item { HonestBanner("El directorio no está disponible. No se inventaron resultados.") }
            null -> Unit
        }
        item { SectionTitle("Emparejar sin revelar tu libreta", "en persona") }
        item {
            PairingMethod(Icons.Outlined.QrCode2, "Código QR Elysium", "Tarjeta de identidad firmada y palabras de seguridad.")
            Spacer(Modifier.height(8.dp))
            PairingMethod(Icons.Outlined.WifiTethering, "Encuentro cercano", "Sin anunciar correo ni teléfono; aprobación mutua.")
        }
        item { Text("El escáner y la transferencia Mesh se habilitarán cuando el transporte criptográfico pase verificación física; aquí no se simula un envío.", color = MeetColors.warning, fontSize = 10.sp) }
    }
}

@Composable
private fun SearchResultCard(contact: ContactSearchResult, onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(18.dp)).border(1.dp, MeetColors.neonGreen.copy(alpha = .28f), RoundedCornerShape(18.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(contact.displayName)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(contact.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                Text("@${contact.elysiumId}", color = MeetColors.cyberCyan, fontSize = 12.sp)
                Text("${contact.matchedMedium.name.lowercase()} · ${contact.aliasProofState.name.lowercase()}", color = MeetColors.textSecondary, fontSize = 9.sp)
            }
            Icon(Icons.Outlined.DoneAll, "Encontrado", tint = MeetColors.neonGreen)
        }
        Button(onRequest, Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(Icons.Outlined.ChatBubbleOutline, null)
            Text("Enviar solicitud de conversación", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun NotFoundCard(onInvite: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(18.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.PersonAdd, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(40.dp))
        Text("No encontramos una cuenta visible", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("Puede que no use Elysium o haya desactivado esa búsqueda.", color = MeetColors.textSecondary, fontSize = 11.sp)
        OutlinedButton(onInvite, Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(Icons.Outlined.Share, null)
            Text("Compartir descarga oficial de GitHub", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ContactsPane(contacts: List<ElysiumContact>, onBlock: (ElysiumContact) -> Unit, onInvite: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedButton(onInvite, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Share, null); Text("Invitar a Elysium", Modifier.padding(start = 6.dp)) } }
        if (contacts.isEmpty()) item { EmptyPanel("No hay contactos todavía. Busca a alguien o comparte el enlace oficial.") }
        else items(contacts, key = { it.principalId }) { contact ->
            Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(contact.displayName)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(contact.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(contact.elysiumId?.let { "@$it · ${contact.relationshipState.lowercase()}" } ?: contact.relationshipState.lowercase(), color = MeetColors.textSecondary, fontSize = 11.sp)
                }
                if (!contact.isBlocked) IconButton({ onBlock(contact) }) { Icon(Icons.Outlined.Block, "Bloquear", tint = MeetColors.warning) }
            }
        }
    }
}

@Composable
private fun CallsPane(conversations: List<ConversationSummary>, onOpen: (ConversationSummary) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HonestBanner("Las llamadas usan infraestructura Elysium. Si el servidor de voz no está configurado, se bloquean sin abrir el marcador externo.") }
        item { SectionTitle("Iniciar una llamada", "elige un chat") }
        if (conversations.isEmpty()) item { EmptyPanel("Crea una conversación y espera aceptación antes de llamar.") }
        else items(conversations, key = { it.id }) { row -> ConversationRow(row) { onOpen(row) } }
    }
}

@Composable
private fun MeshPane(privacy: CommunicationPrivacySettings, onSave: (CommunicationPrivacySettings) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = .08f), RoundedCornerShape(20.dp)).border(1.dp, MeetColors.cyberCyan.copy(alpha = .3f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                Icon(Icons.Outlined.WifiTethering, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(42.dp))
                Text("Vanguard Mesh", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Descubrimiento cercano, entrega directa y store-carry-forward sin Internet. Participación siempre voluntaria.", color = MeetColors.textSecondary, fontSize = 12.sp)
            }
        }
        item { ToggleRow("Visible para solicitudes cercanas", "No anuncia correo ni teléfono.", privacy.meshDiscoverability != "OFF") { onSave(privacy.copy(meshDiscoverability = if (it) "NEARBY_REQUESTS" else "OFF")) } }
        item { ToggleRow("Ayudar como relevo cifrado", "El relevo no puede leer el contenido.", privacy.relayParticipation != "OFF") { onSave(privacy.copy(relayParticipation = if (it) "CONTACTS_ONLY" else "OFF")) } }
        item { ToggleRow("Relevar solo mientras carga", "Reduce el impacto en batería.", privacy.relayOnlyWhileCharging) { onSave(privacy.copy(relayOnlyWhileCharging = it)) } }
        item { HonestBanner("Preferencias persistidas. BLE/Wi‑Fi y voz local todavía requieren verificación física antes de declararse operativos.") }
        item { Text("Capas previstas\n\n• BLE: descubrimiento y señalización\n• Wi‑Fi Aware/Direct: datos y voz local\n• Custodia cifrada con TTL y límite de saltos\n• Reconciliación al recuperar Internet", color = MeetColors.textSecondary, fontSize = 12.sp) }
    }
}

@Composable
private fun SettingsPane(
    identity: ElysiumIdentityProfile?,
    privacy: CommunicationPrivacySettings,
    onSaveIdentity: (String, String, String, String?) -> Unit,
    onSavePrivacy: (CommunicationPrivacySettings) -> Unit,
    onBlocked: () -> Unit,
) {
    var id by remember(identity?.principalId, identity?.elysiumId) { mutableStateOf(identity?.elysiumId.orEmpty()) }
    var name by remember(identity?.principalId, identity?.displayName) { mutableStateOf(identity?.displayName.orEmpty()) }
    var about by remember(identity?.principalId, identity?.about) { mutableStateOf(identity?.about.orEmpty()) }
    var phone by remember(identity?.principalId, identity?.phone) { mutableStateOf(identity?.phone.orEmpty()) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Identidad Elysium", identity?.identityState?.replace('_', ' ') ?: "cargando") }
        item { OutlinedTextField(id, { id = it.take(32) }, Modifier.fillMaxWidth(), label = { Text("ID Elysium") }, leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) }, prefix = { Text("@") }, singleLine = true) }
        item { OutlinedTextField(name, { name = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Nombre visible") }, singleLine = true) }
        item { OutlinedTextField(about, { about = it.take(280) }, Modifier.fillMaxWidth(), label = { Text("Acerca de ti") }, maxLines = 3) }
        item { OutlinedTextField(phone, { phone = it.take(20) }, Modifier.fillMaxWidth(), label = { Text("Teléfono opcional") }, leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) }, supportingText = { Text("Solo búsqueda exacta si la habilitas. Sin SMS.") }, singleLine = true) }
        item { Button({ onSaveIdentity(id, name, about, phone.takeIf(String::isNotBlank)) }, Modifier.fillMaxWidth()) { Text("Guardar identidad") } }
        item { SectionTitle("Quién puede encontrarte", "tú decides") }
        item { ChoiceRow("Por ID Elysium", privacy.findByElysiumId) { onSavePrivacy(privacy.copy(findByElysiumId = cycle(it, listOf("EVERYONE", "NOBODY")))) } }
        item { ChoiceRow("Por correo", privacy.findByEmail) { onSavePrivacy(privacy.copy(findByEmail = cycle(it, visibilityChoices))) } }
        item { ChoiceRow("Por teléfono", privacy.findByPhone) { onSavePrivacy(privacy.copy(findByPhone = cycle(it, visibilityChoices))) } }
        item { SectionTitle("Presencia y conversaciones", "controles") }
        item { ChoiceRow("Última vez", privacy.lastActiveVisibility) { onSavePrivacy(privacy.copy(lastActiveVisibility = cycle(it, profileChoices))) } }
        item { ChoiceRow("Quién puede llamarte", privacy.callPermission) { onSavePrivacy(privacy.copy(callPermission = cycle(it, profileChoices))) } }
        item { ToggleRow("Confirmaciones de lectura", "Controla el visto.", privacy.readReceiptsEnabled) { onSavePrivacy(privacy.copy(readReceiptsEnabled = it)) } }
        item { ToggleRow("Indicador escribiendo", "Controla si otros ven cuando redactas.", privacy.typingIndicatorsEnabled) { onSavePrivacy(privacy.copy(typingIndicatorsEnabled = it)) } }
        item { OutlinedButton(onBlocked, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Block, null); Text("Administrar bloqueados", Modifier.padding(start = 8.dp)) } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private val visibilityChoices = listOf("EVERYONE", "CONTACTS", "NOBODY")
private val profileChoices = listOf("EVERYONE", "CONTACTS", "CONTACTS_EXCEPT", "NOBODY")
private fun cycle(current: String, values: List<String>): String {
    val index = values.indexOf(current).takeIf { it >= 0 } ?: 0
    return values[(index + 1) % values.size]
}

@Composable
private fun BlockedPane(blocked: List<BlockedContact>, onUnblock: (BlockedContact) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Los bloqueados no pueden encontrarte, solicitar conversación ni llamarte.", color = MeetColors.textSecondary, fontSize = 12.sp) }
        if (blocked.isEmpty()) item { EmptyPanel("No has bloqueado a nadie.") }
        else items(blocked, key = { it.principalId }) { contact ->
            Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Block, null, tint = MeetColors.error)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(contact.displayName, color = Color.White, fontWeight = FontWeight.Bold); Text(contact.syncState.lowercase(), color = MeetColors.textSecondary, fontSize = 10.sp) }
                OutlinedButton({ onUnblock(contact) }) { Text("Desbloquear") }
            }
        }
    }
}

@Composable
private fun ConversationBody(
    conversation: ConversationSummary,
    messages: List<DecryptedMessage>,
    voiceNoteState: VoiceNoteRecordingState,
    onSend: (String, String?, () -> Unit) -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: (String?) -> Unit,
    onCancelVoice: () -> Unit,
) {
    var text by rememberSaveable(conversation.id) { mutableStateOf("") }
    var search by rememberSaveable(conversation.id) { mutableStateOf("") }
    var replyToEventId by rememberSaveable(conversation.id) { mutableStateOf<String?>(null) }
    val visibleMessages = remember(messages, search) {
        if (search.isBlank()) messages else messages.filter { it.body.contains(search.trim(), ignoreCase = true) }
    }
    val recordingThisConversation = voiceNoteState is VoiceNoteRecordingState.Recording &&
        voiceNoteState.conversationId == conversation.id
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = .08f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
            Text(if ((conversation.participantCount ?: 0) < 2) "Esperando participante autorizado · envío bloqueado" else "Cifrado local activo · transporte remoto sujeto a configuración", color = MeetColors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(160) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Buscar en esta conversación") },
            singleLine = true,
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            items(visibleMessages, key = { it.id }) { message ->
                MessageBubble(message) { replyToEventId = message.id }
            }
            if (messages.isEmpty()) item { EmptyPanel("Escribe cuando la otra persona esté autorizada.") }
            else if (visibleMessages.isEmpty()) item { EmptyPanel("No hay mensajes que coincidan con la búsqueda.") }
        }
        replyToEventId?.let { replyId ->
            val replied = messages.firstOrNull { it.id == replyId }
            Row(Modifier.fillMaxWidth().background(MeetColors.cyberCyan.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Respondiendo a: ${replied?.body?.take(80) ?: "mensaje"}", color = MeetColors.cyberCyan, fontSize = 10.sp, modifier = Modifier.weight(1f))
                IconButton({ replyToEventId = null }) { Icon(Icons.Outlined.Close, "Cancelar respuesta") }
            }
        }
        Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recordingThisConversation) {
                IconButton({ onStopVoice(replyToEventId); replyToEventId = null }) {
                    Icon(Icons.Outlined.StopCircle, "Detener y guardar nota", tint = MeetColors.error)
                }
                IconButton(onCancelVoice) { Icon(Icons.Outlined.Delete, "Descartar nota", tint = MeetColors.warning) }
            } else {
                IconButton(onStartVoice) { Icon(Icons.Outlined.Mic, "Grabar nota de voz", tint = MeetColors.neonGreen) }
            }
            OutlinedTextField(text, { text = it.take(4000) }, Modifier.weight(1f), placeholder = { Text("Escribe un mensaje") }, maxLines = 4)
            IconButton({
                onSend(text, replyToEventId) {
                    text = ""
                    replyToEventId = null
                }
            }, enabled = text.isNotBlank() && !recordingThisConversation) { Icon(Icons.Outlined.Send, "Enviar", tint = MeetColors.cyberCyan) }
        }
    }
}

@Composable
private fun MessageBubble(message: DecryptedMessage, onReply: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.fillMaxWidth(.82f).background(if (message.isMine) MeetColors.electricBlue.copy(alpha = .28f) else MeetColors.cardBackground, RoundedCornerShape(16.dp)).padding(12.dp)) {
            message.replyToEventId?.let { Text("↪ Respuesta", color = MeetColors.cyberCyan, fontSize = 9.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.eventType == "VOICE_NOTE" && message.localMediaPath != null) {
                    IconButton({ playVoiceNote(message.localMediaPath) }) {
                        Icon(Icons.Outlined.PlayCircle, "Reproducir nota de voz", tint = MeetColors.neonGreen)
                    }
                }
                Text(message.body, color = if (message.decryptionFailed) MeetColors.warning else Color.White, modifier = Modifier.weight(1f))
                IconButton(onReply) { Icon(Icons.Outlined.Reply, "Responder", tint = MeetColors.cyberCyan) }
            }
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAtEpochMs)) + " · " + message.deliveryState, color = MeetColors.textSecondary, fontSize = 9.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
        }
    }
}

private fun playVoiceNote(path: String) {
    runCatching {
        android.media.MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener { it.release() }
            setOnErrorListener { player, _, _ -> player.release(); true }
            prepare()
            start()
        }
    }
}

@Composable
private fun ConversationRow(row: ConversationSummary, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).background(MeetColors.cardBackground, RoundedCornerShape(16.dp)).border(1.dp, MeetColors.cyberCyan.copy(alpha = .18f), RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ContactAvatar(row.title)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(row.serviceVertical?.let { "Servicio · ${it.replace('_', ' ')}" } ?: "${row.requestState.name.lowercase()} · conversación privada", color = MeetColors.textSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = MeetColors.cyberCyan)
    }
}

@Composable private fun ContactAvatar(name: String) {
    Box(Modifier.size(42.dp).background(MeetColors.cyberCyan.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "E", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
    }
}

@Composable private fun PrimaryAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.height(48.dp)) { Icon(icon, null); Text(label, Modifier.padding(start = 6.dp), maxLines = 1, fontSize = 12.sp) }
}

@Composable private fun CompactAction(label: String, icon: ImageVector, detail: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).background(MeetColors.cardBackground, RoundedCornerShape(15.dp)).padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MeetColors.cyberCyan)
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
        Text(detail, color = MeetColors.textSecondary, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable private fun PairingMethod(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MeetColors.cyberCyan, modifier = Modifier.size(30.dp))
        Column(Modifier.padding(start = 12.dp)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(detail, color = MeetColors.textSecondary, fontSize = 10.sp) }
    }
}

@Composable private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MeetColors.cardBackground, RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(detail, color = MeetColors.textSecondary, fontSize = 10.sp) }
        Switch(checked, onChange)
    }
}

@Composable private fun ChoiceRow(title: String, value: String, onCycle: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCycle(value) }.background(MeetColors.cardBackground, RoundedCornerShape(15.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value.replace('_', ' ').lowercase(), color = MeetColors.cyberCyan, fontSize = 11.sp)
    }
}

@Composable private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(detail, color = MeetColors.textSecondary, fontSize = 10.sp)
    }
}

@Composable private fun EmptyPanel(message: String) {
    Box(Modifier.fillMaxWidth().background(MeetColors.cardBackground.copy(alpha = .72f), RoundedCornerShape(16.dp)).padding(22.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MeetColors.textSecondary, fontSize = 12.sp)
    }
}

@Composable private fun HonestBanner(message: String) {
    Row(Modifier.fillMaxWidth().background(MeetColors.warning.copy(alpha = .12f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
