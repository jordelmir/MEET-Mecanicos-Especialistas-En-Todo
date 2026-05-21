package com.elysium369.meet.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.audio.VoicePlayer
import com.elysium369.meet.core.audio.VoiceRecorder
import com.elysium369.meet.data.local.dao.ChatDao
import com.elysium369.meet.data.local.dao.FleetDao
import com.elysium369.meet.data.local.dao.VehicleDao
import com.elysium369.meet.data.local.entities.*
import com.elysium369.meet.data.supabase.SupabaseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FleetChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val fleetDao: FleetDao,
    private val vehicleDao: VehicleDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val voiceRecorder = VoiceRecorder(context)
    private val voicePlayer = VoicePlayer(context)

    // Current authenticated user ID
    val currentUserId: String
        get() = SupabaseManager.client.auth.currentUserOrNull()?.id ?: "local_owner"

    // Active selected business ID
    private val _selectedBusinessId = MutableStateFlow<String?>(null)
    val selectedBusinessId = _selectedBusinessId.asStateFlow()

    // All businesses for the owner
    val businessProfiles: Flow<List<BusinessProfileEntity>> = flow {
        emitAll(fleetDao.getBusinessProfilesForOwner(currentUserId))
    }.flowOn(Dispatchers.IO)

    // Fleets for the selected business
    val fleetsForActiveBusiness: Flow<List<FleetEntity>> = selectedBusinessId.flatMapLatest { businessId ->
        if (businessId == null) flowOf(emptyList())
        else fleetDao.getFleetsForBusiness(businessId)
    }.flowOn(Dispatchers.IO)

    // Members for the selected business
    val membersForActiveBusiness: Flow<List<FleetMemberEntity>> = selectedBusinessId.flatMapLatest { businessId ->
        if (businessId == null) flowOf(emptyList())
        else fleetDao.getMembersForBusiness(businessId)
    }.flowOn(Dispatchers.IO)

    // Vehicles for the selected business
    val vehiclesForActiveBusiness: Flow<List<VehicleEntity>> = selectedBusinessId.flatMapLatest { businessId ->
        if (businessId == null) flowOf(emptyList())
        else fleetDao.getVehiclesForBusiness(businessId)
    }.flowOn(Dispatchers.IO)

    // Fleets the user belongs to as a Driver
    val fleetsAsDriver: Flow<List<FleetEntity>> = flow {
        emitAll(fleetDao.getFleetsForDriver(currentUserId))
    }.flowOn(Dispatchers.IO)

    // Vehicles assigned to the current user (as a Driver)
    val assignedVehiclesAsDriver: Flow<List<VehicleEntity>> = flow {
        emitAll(fleetDao.getAssignedVehiclesForDriver(currentUserId))
    }.flowOn(Dispatchers.IO)

    // Current active chat partner
    private val _selectedPartner = MutableStateFlow<FleetMemberEntity?>(null)
    val selectedPartner = _selectedPartner.asStateFlow()

    // Recording status
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var activeRecordFile: File? = null
    private var recordStartTime: Long = 0

    // Audio Playback status
    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId = _playingMessageId.asStateFlow()

    private val _audioProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val audioProgress = _audioProgress.asStateFlow()

    private val _audioPositionText = MutableStateFlow("0:00")
    val audioPositionText = _audioPositionText.asStateFlow()

    // Block status
    private val _isPartnerBlocked = MutableStateFlow(false)
    val isPartnerBlocked = _isPartnerBlocked.asStateFlow()

    /**
     * Set active business ID
     */
    fun selectBusiness(businessId: String) {
        _selectedBusinessId.value = businessId
    }

    /**
     * Get recent chat conversations for the current business
     */
    val recentChats: Flow<List<ChatMessageEntity>> = selectedBusinessId.flatMapLatest { businessId ->
        if (businessId == null) flowOf(emptyList())
        else chatDao.getRecentChats(businessId, currentUserId)
    }.flowOn(Dispatchers.IO)

    /**
     * Get chat messages between current user and selected partner
     */
    val messages: Flow<List<ChatMessageEntity>> = combine(selectedBusinessId, selectedPartner) { businessId, partner ->
        Pair(businessId, partner)
    }.flatMapLatest { (businessId, partner) ->
        if (businessId == null || partner == null) flowOf(emptyList())
        else chatDao.getChatHistory(businessId, currentUserId, partner.userId)
    }.flowOn(Dispatchers.IO)

    /**
     * Select active chat partner
     */
    fun selectPartner(partner: FleetMemberEntity?) {
        _selectedPartner.value = partner
        _playingMessageId.value = null
        voicePlayer.stop()
        checkBlockStatus()
    }

    private fun checkBlockStatus() {
        val businessId = _selectedBusinessId.value ?: return
        val partner = _selectedPartner.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val blocked = chatDao.isUserBlockedBy(businessId, currentUserId, partner.userId) > 0
            _isPartnerBlocked.value = blocked
        }
    }

    /**
     * Toggle block state for active partner
     */
    fun toggleBlockActivePartner() {
        val businessId = _selectedBusinessId.value ?: return
        val partner = _selectedPartner.value ?: return
        val currentlyBlocked = _isPartnerBlocked.value

        viewModelScope.launch(Dispatchers.IO) {
            if (currentlyBlocked) {
                chatDao.unblockUser(businessId, currentUserId, partner.userId)
                _isPartnerBlocked.value = false
            } else {
                val blockEntry = ChatBlocklistEntity(
                    id = "${businessId}_${currentUserId}_${partner.userId}",
                    businessId = businessId,
                    blockerUserId = currentUserId,
                    blockedUserId = partner.userId,
                    blockedAt = System.currentTimeMillis()
                )
                chatDao.blockUser(blockEntry)
                _isPartnerBlocked.value = true
            }
        }
    }

    /**
     * Send standard text message
     */
    fun sendTextMessage(text: String) {
        val businessId = _selectedBusinessId.value ?: return
        val partner = _selectedPartner.value ?: return
        if (text.trim().isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            // Check if there is block restriction
            if (chatDao.hasBlockBetween(businessId, currentUserId, partner.userId) > 0) {
                Log.w("FleetChatVM", "Cannot send message: User is blocked or has blocked partner")
                return@launch
            }

            val msg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = partner.userId,
                messageText = text,
                messageType = "TEXT",
                fileLocalPath = null,
                fileRemoteUrl = null,
                durationSeconds = 0,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            chatDao.insertMessage(msg)
            simulateMessageDelivery(msg.id)
        }
    }

    /**
     * Start recording voice note
     */
    fun startRecordingVoice() {
        if (_isRecording.value) return
        activeRecordFile = voiceRecorder.startRecording()
        if (activeRecordFile != null) {
            _isRecording.value = true
            recordStartTime = System.currentTimeMillis()
        }
    }

    /**
     * Stop and send voice note
     */
    fun stopAndSendVoice() {
        if (!_isRecording.value) return
        val file = voiceRecorder.stopRecording()
        _isRecording.value = false
        val durationMs = System.currentTimeMillis() - recordStartTime
        val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)

        val businessId = _selectedBusinessId.value ?: return
        val partner = _selectedPartner.value ?: return

        if (file != null && file.exists()) {
            viewModelScope.launch(Dispatchers.IO) {
                if (chatDao.hasBlockBetween(businessId, currentUserId, partner.userId) > 0) {
                    file.delete()
                    return@launch
                }

                val msg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = businessId,
                    senderId = currentUserId,
                    receiverId = partner.userId,
                    messageText = null,
                    messageType = "AUDIO",
                    fileLocalPath = file.absolutePath,
                    fileRemoteUrl = null,
                    durationSeconds = durationSec,
                    timestamp = System.currentTimeMillis(),
                    status = "PENDING"
                )
                chatDao.insertMessage(msg)
                simulateMessageDelivery(msg.id)
            }
        }
        activeRecordFile = null
    }

    /**
     * Cancel active recording
     */
    fun cancelRecordingVoice() {
        if (!_isRecording.value) return
        voiceRecorder.cancelRecording()
        _isRecording.value = false
        activeRecordFile = null
    }

    /**
     * Play or Pause voice note
     */
    fun togglePlayVoice(message: ChatMessageEntity) {
        val path = message.fileLocalPath ?: message.fileRemoteUrl
        if (path.isNullOrEmpty()) return

        if (_playingMessageId.value == message.id) {
            if (voicePlayer.isPlaying()) {
                voicePlayer.pause()
            } else {
                voicePlayer.resume()
            }
        } else {
            _playingMessageId.value = message.id
            _audioProgress.value = 0f
            _audioPositionText.value = "0:00"

            voicePlayer.play(
                sourcePath = path,
                onProgress = { current, total ->
                    if (total > 0) {
                        _audioProgress.value = current.toFloat() / total.toFloat()
                        val sec = (current / 1000) % 60
                        val min = (current / 1000) / 60
                        _audioPositionText.value = String.format("%d:%02d", min, sec)
                    }
                },
                onComplete = {
                    _playingMessageId.value = null
                    _audioProgress.value = 0f
                    _audioPositionText.value = "0:00"
                },
                onError = { err ->
                    Log.e("FleetChatVM", "Audio play failed: $err")
                    _playingMessageId.value = null
                    _audioProgress.value = 0f
                }
            )
        }
    }

    /**
     * Send a special DTC Alert message to the driver
     */
    fun sendDtcAlertMessage(vehicleName: String, dtcCodes: List<String>) {
        val businessId = _selectedBusinessId.value ?: return
        val partner = _selectedPartner.value ?: return
        if (dtcCodes.isEmpty()) return

        val text = "⚠️ ALERTA DE DIAGNÓSTICO VEHICULAR\nVehículo: $vehicleName\nCódigos de falla activos: ${dtcCodes.joinToString(", ")}\nSe recomienda verificar el estado de inmediato."

        viewModelScope.launch(Dispatchers.IO) {
            val msg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = partner.userId,
                messageText = text,
                messageType = "DTC_ALERT",
                fileLocalPath = null,
                fileRemoteUrl = null,
                durationSeconds = 0,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            chatDao.insertMessage(msg)
            simulateMessageDelivery(msg.id)
        }
    }

    /**
     * Send file or image attachment
     */
    fun sendFileAttachment(file: File, type: String) { // "IMAGE", "PDF", etc.
        val businessId = _selectedBusinessId.value ?: return
        val partner = _selectedPartner.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val msg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = partner.userId,
                messageText = file.name,
                messageType = "FILE",
                fileLocalPath = file.absolutePath,
                fileRemoteUrl = null,
                durationSeconds = 0,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            chatDao.insertMessage(msg)
            simulateMessageDelivery(msg.id)
        }
    }

    /**
     * Create a business profile
     */
    fun createBusinessProfile(name: String, taxId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = BusinessProfileEntity(
                id = "b_${UUID.randomUUID().toString().take(6)}",
                name = name,
                taxId = taxId,
                planType = "PREMIUM_FLEET",
                maxVehicles = 50,
                createdAt = System.currentTimeMillis(),
                ownerUserId = currentUserId
            )
            fleetDao.insertBusinessProfile(profile)
            _selectedBusinessId.value = profile.id
        }
    }

    /**
     * Create a fleet with an invite code
     */
    fun createFleet(name: String, description: String?) {
        val businessId = _selectedBusinessId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val randomCode = "MEET-FLT-${(1000..9999).random()}"
            val fleet = FleetEntity(
                id = "f_${UUID.randomUUID().toString().take(6)}",
                businessId = businessId,
                name = name,
                description = description,
                inviteCode = randomCode,
                createdAt = System.currentTimeMillis()
            )
            fleetDao.insertFleet(fleet)
        }
    }

    /**
     * Join fleet by invite code
     */
    fun joinFleetByCode(inviteCode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val fleet = fleetDao.getFleetByInviteCode(inviteCode.trim().uppercase())
            if (fleet != null) {
                // Add driver as member of the fleet
                val member = FleetMemberEntity(
                    id = "m_${currentUserId}_${fleet.id}",
                    businessId = fleet.businessId,
                    userId = currentUserId,
                    role = "DRIVER",
                    email = "conductor_${currentUserId.take(4)}@meet.com",
                    inviteStatus = "ACCEPTED",
                    joinedAt = System.currentTimeMillis(),
                    fleetId = fleet.id
                )
                fleetDao.insertFleetMember(member)
                launch(Dispatchers.Main) {
                    onResult(true, "Te has unido a la flota ${fleet.name} correctamente.")
                }
            } else {
                launch(Dispatchers.Main) {
                    onResult(false, "Código de invitación no válido o inexistente.")
                }
            }
        }
    }

    /**
     * Assign driver and fleet to vehicle
     */
    fun assignVehicleToDriverAndFleet(vehicleId: String, driverId: String?, fleetId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val vehicle = vehicleDao.getVehicleById(vehicleId)
            if (vehicle != null) {
                val updated = vehicle.copy(
                    assignedDriverId = driverId,
                    fleetId = fleetId,
                    businessId = _selectedBusinessId.value
                )
                vehicleDao.insertVehicle(updated)
            }
        }
    }

    /**
     * Submit DVIR Report
     */
    fun submitDvirReport(
        vehicleId: String,
        brakesOk: Boolean,
        lightsOk: Boolean,
        tiresOk: Boolean,
        fluidsOk: Boolean,
        batteryOk: Boolean,
        onResult: () -> Unit
    ) {
        val businessId = _selectedBusinessId.value ?: "b1"
        viewModelScope.launch(Dispatchers.IO) {
            val vehicle = vehicleDao.getVehicleById(vehicleId)
            val vehicleName = vehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo $vehicleId"
            
            val issues = mutableListOf<String>()
            if (!brakesOk) issues.add("Frenos 🛑")
            if (!lightsOk) issues.add("Luces 💡")
            if (!tiresOk) issues.add("Llantas 🛞")
            if (!fluidsOk) issues.add("Fluidos/Aceite 💧")
            if (!batteryOk) issues.add("Batería ⚡")

            val statusText = if (issues.isEmpty()) {
                "✅ TODO EN ORDEN. Listo para circular."
            } else {
                "⚠️ REPORTÓ FALLAS EN: ${issues.joinToString(", ")}"
            }

            val messageText = "📋 REPORTE DE INSPECCIÓN DIARIA (DVIR)\nVehículo: $vehicleName\nEstado: $statusText\nConductor: Conductor ($currentUserId)"

            // Send chat alert to fleet channel
            // Find business owner/admin to receive it, or just broadcast it
            val msg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                senderId = currentUserId,
                receiverId = "local_owner", // Send to owner
                messageText = messageText,
                messageType = "DTC_ALERT",
                fileLocalPath = null,
                fileRemoteUrl = null,
                durationSeconds = 0,
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
            chatDao.insertMessage(msg)
            simulateMessageDelivery(msg.id)

            launch(Dispatchers.Main) {
                onResult()
            }
        }
    }

    private suspend fun simulateMessageDelivery(msgId: String) {
        // Simulates delivery state updates for the user experience
        kotlinx.coroutines.delay(1000)
        chatDao.updateMessageStatus(msgId, "SENT")
        kotlinx.coroutines.delay(800)
        chatDao.updateMessageStatus(msgId, "DELIVERED")
    }

    override fun onCleared() {
        super.onCleared()
        voicePlayer.stop()
        voiceRecorder.cancelRecording()
    }
}
