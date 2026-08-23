package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.communications.ConversationSummary
import com.elysium369.meet.communications.CallConnectionState
import com.elysium369.meet.communications.DecryptedMessage
import com.elysium369.meet.communications.ElysiumCommunicationRepository
import com.elysium369.meet.communications.SendMessageOutcome
import com.elysium369.meet.communications.StartCallOutcome
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

    private val selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversation: StateFlow<ConversationSummary?> = selectedConversationId
        .flatMapLatest { id -> id?.let(repository::observeConversation) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<DecryptedMessage>> = selectedConversationId
        .flatMapLatest { id -> id?.let(repository::observeMessages) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notice = MutableStateFlow<String?>(null)

    fun selectConversation(id: String?) {
        selectedConversationId.value = id
        notice.value = null
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
