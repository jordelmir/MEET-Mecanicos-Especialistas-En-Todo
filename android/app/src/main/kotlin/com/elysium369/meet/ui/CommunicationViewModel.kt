package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.communications.ConversationSummary
import com.elysium369.meet.communications.CallConnectionState
import com.elysium369.meet.communications.DecryptedMessage
import com.elysium369.meet.communications.ElysiumCommunicationRepository
import com.elysium369.meet.communications.SendMessageOutcome
import com.elysium369.meet.communications.StartCallOutcome
import com.elysium369.meet.communications.BlockedContact
import com.elysium369.meet.communications.CommunicationPrivacySettings
import com.elysium369.meet.communications.ContactRequestOutcome
import com.elysium369.meet.communications.ContactSearchOutcome
import com.elysium369.meet.communications.ContactSearchResult
import com.elysium369.meet.communications.ElysiumContact
import com.elysium369.meet.communications.ElysiumIdentityProfile
import com.elysium369.meet.communications.VoiceNoteRecorder
import com.elysium369.meet.communications.VoiceNoteRecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val repository: ElysiumCommunicationRepository,
    private val voiceNoteRecorder: VoiceNoteRecorder,
) : ViewModel() {
    val conversations: StateFlow<List<ConversationSummary>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val callState: StateFlow<CallConnectionState> = repository.callState
    val incomingCall = repository.incomingCall
    val identity: StateFlow<ElysiumIdentityProfile?> = repository.identity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val privacy: StateFlow<CommunicationPrivacySettings> = repository.privacy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommunicationPrivacySettings())
    val contacts: StateFlow<List<ElysiumContact>> = repository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val blockedContacts: StateFlow<List<BlockedContact>> = repository.blockedContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val voiceNoteState: StateFlow<VoiceNoteRecordingState> = voiceNoteRecorder.state

    private val selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversation: StateFlow<ConversationSummary?> = selectedConversationId
        .flatMapLatest { id -> id?.let(repository::observeConversation) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<DecryptedMessage>> = selectedConversationId
        .flatMapLatest { id -> id?.let(repository::observeMessages) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nearbyStatus = repository.nearbyStatus
    val nearbyPeers = repository.nearbyPeers
    val nearbyInvitations = repository.nearbyInvitations
    fun startNearby() { repository.startNearby() }
    fun nearbyPermissionDenied() { notice.value="Permite dispositivos cercanos para conectar los teléfonos." }
    fun stopNearby() { repository.stopNearby() }
    fun connectNearby(id:String) { repository.connectNearby(id) }
    fun answerNearby(id:String,accept:Boolean) { repository.answerNearby(id,accept) }
    suspend fun refreshNearby() { repository.flushNearby() }

    override fun onCleared() { repository.stopNearby(); super.onCleared() }

    val onlineStatus = repository.onlineStatus
    val pairingQr = MutableStateFlow<String?>(null)

    suspend fun refreshOnline() { repository.synchronizeOnline() }

    fun createPairingQr() {
        viewModelScope.launch {
            pairingQr.value = runCatching { repository.issuePairingQr() }.getOrElse {
                notice.value = "No se pudo crear el QR. Inicia sesión y registra tu identidad; requiere servidor disponible."
                null
            }
        }
    }

    fun respondToRequest(accept: Boolean) {
        val id = selectedConversationId.value ?: return
        viewModelScope.launch {
            notice.value = if (repository.respondToRequest(id,accept)) {
                if (accept) "Conversación aceptada en el servidor." else "Solicitud rechazada en el servidor."
            } else "No se pudo confirmar la respuesta con el servidor."
        }
    }

    fun openContact(contact:ElysiumContact) {
        viewModelScope.launch {
            val id=repository.contactConversation(contact.principalId)
            if(id!=null) selectedConversationId.value=id
            else notice.value="No hay conversación aceptada disponible. Sincroniza o vincula el contacto mediante QR."
        }
    }

    fun dismissPairingQr() { pairingQr.value = null }

    fun scanPairingQr(value: String) {
        viewModelScope.launch {
            runCatching { repository.consumePairingQr(value) }.onSuccess { id ->
                selectedConversationId.value = id
                notice.value = "Vínculo confirmado por el servidor."
            }.onFailure { notice.value = "QR no válido, vencido o servidor no disponible. No se confirmó el vínculo." }
        }
    }

    val notice = MutableStateFlow<String?>(null)
    val searchOutcome = MutableStateFlow<ContactSearchOutcome?>(null)
    val searchInProgress = MutableStateFlow(false)

    init {
        viewModelScope.launch { repository.initializeSocialDefaults() }
        viewModelScope.launch {
            var previousOwner:String?=null
            repository.identity.collect { profile ->
                if(previousOwner!=null && previousOwner!=profile.principalId) { selectedConversationId.value=null;imageTarget=null;voiceNoteRecorder.cancel() }
                previousOwner=profile.principalId
            }
        }
    }

    fun selectConversation(id: String?) {
        selectedConversationId.value = id
        notice.value = null
    }

    fun searchContact(query: String) {
        viewModelScope.launch {
            searchInProgress.value = true
            searchOutcome.value = repository.searchContact(query)
            searchInProgress.value = false
        }
    }

    fun clearSearch() {
        searchOutcome.value = null
    }

    fun requestContact(contact: ContactSearchResult) {
        viewModelScope.launch {
            when (val outcome = repository.requestContact(contact)) {
                is ContactRequestOutcome.Created -> {
                    selectedConversationId.value = outcome.conversationId
                    notice.value = "Solicitud enviada. Los mensajes se habilitan cuando la otra persona acepta."
                }
                ContactRequestOutcome.AuthenticationRequired -> notice.value = "Inicia sesión para enviar una solicitud de conversación."
                ContactRequestOutcome.Blocked -> notice.value = "Este contacto está bloqueado."
                ContactRequestOutcome.ServiceUnavailable -> notice.value = "No se pudo crear la solicitud en el servidor. Inténtalo de nuevo."
            }
        }
    }

    fun saveIdentity(elysiumId: String, displayName: String, about: String, phone: String?) {
        viewModelScope.launch {
            notice.value = runCatching { repository.saveIdentity(elysiumId, displayName, about, phone) }
                .fold(
                    onSuccess = { synced ->
                        if (synced) "Identidad Elysium guardada y sincronizada." else "Identidad guardada en este dispositivo. Inicia sesión para publicarla."
                    },
                    onFailure = { error ->
                        when (error.message) {
                            "INVALID_ELYSIUM_ID" -> "El ID debe tener 3–32 caracteres: letras, números, punto, guion o guion bajo."
                            "INVALID_DISPLAY_NAME" -> "El nombre visible es obligatorio."
                            else -> "No se pudo guardar la identidad."
                        }
                    },
                )
        }
    }

    fun savePrivacy(settings: CommunicationPrivacySettings) {
        viewModelScope.launch {
            val synced = repository.savePrivacy(settings)
            notice.value = if (synced) "Privacidad sincronizada." else "Privacidad aplicada localmente; sincronización pendiente."
        }
    }

    fun block(contact: ElysiumContact) {
        viewModelScope.launch {
            val synced = repository.blockContact(contact)
            notice.value = if (synced) "${contact.displayName} fue bloqueado en todos tus dispositivos." else "${contact.displayName} fue bloqueado en este dispositivo."
        }
    }

    fun unblock(contact: BlockedContact) {
        viewModelScope.launch {
            val success = repository.unblockContact(contact)
            notice.value = if (success) "${contact.displayName} fue desbloqueado." else "No se pudo confirmar el desbloqueo con el servidor."
        }
    }

    fun openServiceContext(vertical: String, referenceId: String, title: String) {
        viewModelScope.launch {
            val id = repository.ensureServiceConversation(vertical, referenceId, title)
            selectedConversationId.value = id
        }
    }

    fun sendText(text: String, replyToEventId: String?, onAccepted: () -> Unit) {
        val id = selectedConversationId.value ?: return
        viewModelScope.launch {
            when (repository.sendText(id, text, replyToEventId)) {
                is SendMessageOutcome.SentLocally -> {
                    notice.value = "Guardado en la cola de envío. La marca del mensaje mostrará la confirmación del servidor."
                    onAccepted()
                    repository.flushNearby()
                }
                SendMessageOutcome.WaitingForAuthorizedParticipant ->
                    notice.value = "La conversación se habilitará cuando exista otro participante autorizado."
                SendMessageOutcome.EmptyMessage -> Unit
                SendMessageOutcome.ConversationUnavailable ->
                    notice.value = "La conversación ya no está disponible para esta identidad."
            }
        }
    }

    private var imageTarget: Pair<String,String>? = null
    fun beginImageSelection(): Boolean {
        val conversation=selectedConversationId.value ?: return false
        val owner=identity.value?.principalId ?: return false
        imageTarget=conversation to owner
        return true
    }
    fun sendImage(uri: android.net.Uri) {
        val target=imageTarget ?: return
        imageTarget=null
        if(selectedConversationId.value!=target.first || identity.value?.principalId!=target.second) {
            notice.value="Cambió la cuenta o conversación; selecciona la imagen nuevamente."; return
        }
        viewModelScope.launch {
            val outcome=runCatching { repository.sendImage(target.first,uri,target.second) }.getOrNull()
            notice.value=if(outcome is SendMessageOutcome.SentLocally) "Imagen cifrada en la cola de envío." else "No se pudo preparar la imagen. Selecciona una imagen de hasta 12 MiB en una conversación aceptada."
        }
    }

    fun startVoiceNote() {
        if(callState.value in setOf(CallConnectionState.ACTIVE,CallConnectionState.CONNECTING,CallConnectionState.RINGING)) { notice.value="Finaliza la llamada antes de grabar una nota.";return }
        val id = selectedConversationId.value ?: return
        if (!voiceNoteRecorder.start(id)) notice.value = "No se pudo iniciar la grabación."
    }

    fun stopAndSendVoiceNote(replyToEventId: String? = null) {
        val draft = voiceNoteRecorder.stop() ?: run {
            notice.value = "No se obtuvo una nota de voz válida."
            return
        }
        viewModelScope.launch {
            val outcome=runCatching { repository.sendVoiceNote(draft.conversationId,draft,replyToEventId) }.getOrElse {
                notice.value="No se pudo preparar la nota de voz. Conservé el archivo; intenta una grabación más corta.";return@launch
            }
            when (outcome) {
                is SendMessageOutcome.SentLocally ->
                    notice.value = "Nota de voz cifrada en la cola de envío; consulta la confirmación de entrega."
                SendMessageOutcome.WaitingForAuthorizedParticipant -> {
                    draft.file.delete()
                    notice.value = "Espera a un participante autorizado antes de enviar audio."
                }
                SendMessageOutcome.ConversationUnavailable -> {
                    draft.file.delete()
                    notice.value = "La conversación ya no está disponible."
                }
                SendMessageOutcome.EmptyMessage -> Unit
            }
        }
    }

    fun cancelVoiceNote() {
        voiceNoteRecorder.cancel()
        notice.value = "Grabación descartada."
    }

    fun startCall() {
        if(voiceNoteState.value is VoiceNoteRecordingState.Recording) { notice.value="Detén la nota de voz antes de llamar.";return }
        val id = selectedConversationId.value ?: return
        viewModelScope.launch {
            val outcome = repository.startAudioCall(id)
            notice.value = when (outcome) {
                StartCallOutcome.WaitingForAuthorizedParticipant ->
                    "No se puede llamar hasta que exista otro participante autorizado."
                StartCallOutcome.ServerTransportNotConfigured ->
                    "Las llamadas Elysium están protegidas: el servidor de voz todavía no está configurado. No se abrió el marcador externo."
                StartCallOutcome.AuthenticationRequired ->
                    "Inicia sesión para recibir una autorización de llamada de corta duración."
                StartCallOutcome.InsecureEndpointRejected ->
                    "La llamada fue bloqueada porque el servidor no usa una conexión segura."
                is StartCallOutcome.Failed ->
                    "No se pudo establecer la llamada Elysium (${outcome.safeCode})."
                is StartCallOutcome.Ready -> "Conectando el audio de Elysium."
                is StartCallOutcome.Ringing -> "Llamando; esperando que la otra persona acepte."
            }
        }
    }

    fun answerCall(accept:Boolean) {
        if(accept && voiceNoteState.value is VoiceNoteRecordingState.Recording) { notice.value="Detén la nota de voz antes de aceptar la llamada.";return }
        viewModelScope.launch {
            val confirmed=runCatching { repository.answerCall(accept) }.getOrDefault(false)
            notice.value=if(!confirmed) "No se pudo confirmar la respuesta de llamada." else if(accept) "Conectando audio." else "Llamada rechazada."
        }
    }

    fun endCall() {
        viewModelScope.launch {
            repository.endCall()
            notice.value = "Llamada finalizada."
        }
    }

    fun microphonePermissionDenied() {
        notice.value = "El micrófono es necesario para una llamada de voz. No se enviaron datos."
    }
}
