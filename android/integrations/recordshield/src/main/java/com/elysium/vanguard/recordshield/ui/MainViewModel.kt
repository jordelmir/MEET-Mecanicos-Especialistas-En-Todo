package com.elysium.vanguard.recordshield.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import com.elysium.vanguard.recordshield.data.cloud.CloudStorageManager
import com.elysium.vanguard.recordshield.data.remote.DeviceAlreadyRegisteredException
import com.elysium.vanguard.recordshield.data.remote.DeviceRegistrationClient
import com.elysium.vanguard.recordshield.domain.model.EvidenceChunk
import com.elysium.vanguard.recordshield.domain.model.Recording
import com.elysium.vanguard.recordshield.domain.repository.ChunkRepository
import com.elysium.vanguard.recordshield.domain.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * ============================================================================
 * MainViewModel — Shared state for the entire app
 * ============================================================================
 *
 * Why a single ViewModel instead of per-screen: The app has deep state
 * dependencies between screens (PIN verification gates gallery, gallery
 * leads to player, recording state affects home). A shared ViewModel
 * prevents redundant data loading and ensures consistent state.
 * ============================================================================
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val chunkRepository: ChunkRepository,
    private val secureStorage: SecureStorage,
    private val cloudStorageManager: CloudStorageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ========================================================================
    // STATE
    // ========================================================================

    val recordings: StateFlow<List<Recording>> = recordingRepository
        .observeAllRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingUploadCount: StateFlow<Int> = chunkRepository
        .observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isPinVerified = MutableStateFlow(false)
    val isPinVerified: StateFlow<Boolean> = _isPinVerified

    private val _isDeviceRegistered = MutableStateFlow(false)
    val isDeviceRegistered: StateFlow<Boolean> = _isDeviceRegistered

    private val _selectedRecordingChunks = MutableStateFlow<List<EvidenceChunk>>(emptyList())
    val selectedRecordingChunks: StateFlow<List<EvidenceChunk>> = _selectedRecordingChunks

    private val _registrationError = MutableStateFlow<String?>(null)
    val registrationError: StateFlow<String?> = _registrationError

    init {
        _isDeviceRegistered.value = secureStorage.isDeviceRegistered
        // If the user provided a key via request, we can set it here or via a dedicated call.
        // For now, I'll ensure the property is accessible.
    }

    fun setVercelApiKey(key: String) {
        secureStorage.vercelApiKey = key
    }

    // ========================================================================
    // PIN OPERATIONS
    // ========================================================================

    fun isPinSet(): Boolean = secureStorage.isPinSet()

    fun verifyPin(pin: String): Boolean {
        val result = secureStorage.verifyPin(pin)
        if (result) {
            _isPinVerified.value = true
        }
        return result
    }

    fun setPin(pin: String) {
        secureStorage.setPin(pin)
        _isPinVerified.value = true
    }

    fun resetPinVerification() {
        _isPinVerified.value = false
    }

    // ========================================================================
    // GALLERY OPERATIONS
    // ========================================================================

    fun loadChunksForRecording(recordingId: String) {
        viewModelScope.launch {
            _selectedRecordingChunks.value =
                chunkRepository.getChunksForRecording(recordingId)
        }
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording.id)
            // Also clean up local files
            val chunksDir = java.io.File(
                context.filesDir,
                "recordings/${recording.id}"
            )
            if (chunksDir.exists()) {
                chunksDir.deleteRecursively()
            }
        }
    }

    // ========================================================================
    // DEVICE REGISTRATION
    // ========================================================================

    fun registerDevice(
        fingerprint: String,
        alias: String,
        baseUrl: String
    ) {
        viewModelScope.launch {
            try {
                val client = DeviceRegistrationClient(baseUrl)
                val result = client.registerDevice(fingerprint, alias)

                // Store credentials securely
                secureStorage.deviceId = result.deviceId
                secureStorage.deviceToken = result.apiToken
                secureStorage.apiBaseUrl = baseUrl
                secureStorage.isDeviceRegistered = true
                secureStorage.deviceAlias = alias

                _isDeviceRegistered.value = true
                _registrationError.value = null

                client.close()
            } catch (e: DeviceAlreadyRegisteredException) {
                secureStorage.deviceId = e.existingDeviceId
                _registrationError.value = "Device already registered. Contact admin for token reset."
            } catch (e: Exception) {
                _registrationError.value = e.message
            }
        }
    }

    // ========================================================================
    // CONSENT
    // ========================================================================

    fun isConsentGiven(): Boolean = secureStorage.isConsentGiven

    fun setConsentGiven() {
        secureStorage.isConsentGiven = true
    }

    // ========================================================================
    // CREDENTIALS
    // ========================================================================

    fun getDeviceId(): String? = secureStorage.deviceId
    fun getDeviceToken(): String? = secureStorage.deviceToken
    fun getCloudStorageManager(): CloudStorageManager = cloudStorageManager
}
