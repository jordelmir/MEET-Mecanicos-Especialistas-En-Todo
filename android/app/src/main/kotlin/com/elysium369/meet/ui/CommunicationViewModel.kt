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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CommunicationViewModel @Inject constructor(
    private val repository: ElysiumCommunicationRepository,
) : ViewModel() {
    val conversations: StateFlow<List<ConversationSummary>> = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val callState: StateFlow<CallConnectionState> = repository.callState
    val identity: StateFlow<ElysiumIdentityProfile?> = repository.identity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val privacy: StateFlow<CommunicationPrivacySettings> = repository.privacy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommunicationPrivacySettings())
    val contacts: StateFlow<List<ElysiumContact>> = repository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val blockedContacts: StateFlow<List<BlockedContact>> = repository.blockedContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversation: StateFlow<ConversationSummary?> = selectedConversationId
        .flatMapLatest { id -> id?.let(repository::observeConversation) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<DecryptedMessage>> = selectedConversationId
        .flatMapLatest { id -> id?.let(repository::observeMessages) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notice = MutableStateFlow<String?>(null)
    val searchOutcome = MutableStateFlow<ContactSearchOutcome?>(null)
    val searchInProgress = MutableStateFlow(false)

    init {
        viewModelScope.launch { repository.initializeSocialDefaults() }
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

    fun sendText(text: String, onAccepted: () -> Unit) {
        val id = selectedConversationId.value ?: return
        viewModelScope.launch {
            when (repository.sendText(id, text)) {
                is SendMessageOutcome.SentLocally -> {
                    notice.value = "Guardado de forma segura en este dispositivo; transporte remoto pendiente."
                    onAccepted()
                }
                SendMessageOutcome.WaitingForAuthorizedParticipant ->
                    notice.value = "La conversación se habilitará cuando exista otro participante autorizado."
                SendMessageOutcome.EmptyMessage -> Unit
                SendMessageOutcome.ConversationUnavailable ->
                    notice.value = "La conversación ya no está disponible para esta identidad."
            }
        }
    }

    fun startCall() {
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
                is StartCallOutcome.Ready -> "Llamada Elysium conectada."
            }
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
