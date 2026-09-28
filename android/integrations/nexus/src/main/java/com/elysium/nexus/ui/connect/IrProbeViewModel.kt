package com.elysium.nexus.ui.connect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.CursorInitResult
import com.elysium.nexus.fabric.infrared.ProbeCursor
import com.elysium.nexus.fabric.infrared.ProbeRestoreDecision
import com.elysium.nexus.fabric.infrared.ProbeRestoreResolver
import com.elysium.nexus.fabric.profile.db.ElysiumUserDatabase
import com.elysium.nexus.fabric.profile.db.ProbeAttemptEntity
import com.elysium.nexus.fabric.profile.db.ProbeSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * P0.3: IrProbeViewModel — Survives process death via SavedStateHandle + Room.
 *
 * SavedStateHandle holds step/verifiedActions/isAutoScanning (lightweight, fast).
 * Room holds full probe session state (candidate index, signalId, attemptId, etc.).
 *
 * On restoration after process death:
 * 1. SavedStateHandle restores step + verifiedActions + isAutoScanning
 * 2. Room restores candidateIndex, currentCandidateId, lastSignalId, etc.
 * 3. Engine is reconstructed and repositioned to saved candidate index
 * 4. Candidate identity is verified (same codeSetId at same index)
 * 5. If verification fails → RECOVERY_REQUIRED (never silently select candidate 0)
 */
class IrProbeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val TAG = "IrProbeViewModel"

    // ═══════════════════════════════════════════════════════════════════
    // Persisted state via SavedStateHandle (survives process death)
    // ═══════════════════════════════════════════════════════════════════

    internal var step: IrStep
        get() = savedStateHandle["step"] ?: IrStep.ORIENT
        set(value) { savedStateHandle["step"] = value }

    val verifiedActions: Set<IrAction>
        get() = try {
            val json = savedStateHandle.get<String>("verifiedActions") ?: "[]"
            parseActionSet(json)
        } catch (e: Exception) { emptySet() }
    fun setVerifiedActions(value: Set<IrAction>) {
        savedStateHandle["verifiedActions"] = serializeActionSet(value)
    }

    var isAutoScanning: Boolean
        get() = savedStateHandle["isAutoScanning"] ?: false
        set(value) { savedStateHandle["isAutoScanning"] = value }

    // ═══════════════════════════════════════════════════════════════════
    // Transient state (reconstructed from Room after process death)
    // ═══════════════════════════════════════════════════════════════════

    private val _probeUiState = MutableStateFlow<ProbeUiState>(ProbeUiState.LoadingCatalog)
    val probeUiState: StateFlow<ProbeUiState> = _probeUiState.asStateFlow()

    private val _currentAttempt = MutableStateFlow<ProbeAttempt?>(null)
    val currentAttempt: StateFlow<ProbeAttempt?> = _currentAttempt.asStateFlow()

    private var probeEngine: ProbeCursor? = null
    private var sessionId: String? = null
    private var lastPersistedIndex: Int = 0
    private var lastPersistedCandidateId: String? = null
    private var lastPersistedActionKey: String? = null
    private var lastPersistedSignalId: String? = null
    private var lastPersistedPhysicalSha256: String? = null
    private var lastPersistedAttemptId: String? = null

    // PHASE 3: session meta cached so updateProbeState can lazily create
    // the Room session row even if ensureSession was never called.
    private var sessionBrand: String = ""
    private var sessionDeviceType: String = ""
    private var sessionTargetModel: String? = null
    private var sessionCatalogHash: String? = null

    // ═══════════════════════════════════════════════════════════════════
    // Room persistence
    // ═══════════════════════════════════════════════════════════════════

    private val db = ElysiumUserDatabase.getInstance(application)

    /**
     * PHASE 3: Create the Room session row (idempotent). sessionId is
     * persisted BOTH in the field and in SavedStateHandle so that after
     * process death the flow can look the durable session up again.
     */
    fun ensureSession(
        brand: String,
        deviceType: String,
        targetModel: String?,
        catalogHash: String?
    ) {
        sessionBrand = brand
        sessionDeviceType = deviceType
        sessionTargetModel = targetModel
        sessionCatalogHash = catalogHash
        // Idempotent: if a session already exists (field OR SavedStateHandle
        // after process death), never create a second one — the durable
        // restore identity must be preserved.
        val existing = getSessionId()
        if (existing != null) {
            if (sessionId == null) sessionId = existing
            return
        }

        val sid = UUID.randomUUID().toString()
        sessionId = sid
        savedStateHandle["probeSessionId"] = sid

        viewModelScope.launch {
            val entity = ProbeSessionEntity(
                sessionId = sid,
                brand = brand,
                deviceType = deviceType,
                targetModel = targetModel,
                startedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = null,
                status = step.name,
                winnerCodeSetId = null,
                currentCandidateIndex = 0,
                currentCandidateId = null,
                currentActionKey = null,
                lastSignalId = null,
                lastPhysicalSha256 = null,
                lastAttemptId = null,
                catalogHashAtStart = catalogHash,
                verifiedActionKeys = serializeActionSet(verifiedActions)
            )
            db.profileDao().insertProbeSession(entity)
            android.util.Log.i(TAG, "PHASE3: probe session $sid created in Room")
        }
    }

    /**
     * Create or update probe session in Room.
     */
    fun persistSession(
        brand: String,
        deviceType: String,
        targetModel: String?,
        candidateIndex: Int,
        candidateId: String?,
        actionKey: String?,
        signalId: String?,
        physicalSha256: String?,
        attemptId: String?,
        catalogHash: String?
    ) {
        val sid = sessionId ?: UUID.randomUUID().toString().also { sessionId = it }
        lastPersistedIndex = candidateIndex
        lastPersistedCandidateId = candidateId
        lastPersistedActionKey = actionKey
        lastPersistedSignalId = signalId
        lastPersistedPhysicalSha256 = physicalSha256
        lastPersistedAttemptId = attemptId

        viewModelScope.launch {
            val entity = ProbeSessionEntity(
                sessionId = sid,
                brand = brand,
                deviceType = deviceType,
                targetModel = targetModel,
                startedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = null,
                status = step.name,
                winnerCodeSetId = null,
                currentCandidateIndex = candidateIndex,
                currentCandidateId = candidateId,
                currentActionKey = actionKey,
                lastSignalId = signalId,
                lastPhysicalSha256 = physicalSha256,
                lastAttemptId = attemptId,
                catalogHashAtStart = catalogHash,
                verifiedActionKeys = serializeActionSet(verifiedActions)
            )
            db.profileDao().insertProbeSession(entity)
        }
    }

    /**
     * Update just the transient probe state in Room (no full re-insert).
     * PHASE 3: lazily creates the session row if it does not exist yet
     * (covers flows that transmit before ensureSession was called).
     */
    fun updateProbeState(
        candidateIndex: Int,
        candidateId: String?,
        actionKey: String?,
        signalId: String?,
        physicalSha256: String?,
        attemptId: String?
    ) {
        if (getSessionId() == null) {
            ensureSession(
                brand = sessionBrand.ifBlank { "unknown" },
                deviceType = sessionDeviceType.ifBlank { "unknown" },
                targetModel = sessionTargetModel,
                catalogHash = sessionCatalogHash
            )
        }
        val sid = getSessionId() ?: return
        lastPersistedIndex = candidateIndex
        lastPersistedCandidateId = candidateId
        lastPersistedActionKey = actionKey
        lastPersistedSignalId = signalId
        lastPersistedPhysicalSha256 = physicalSha256
        lastPersistedAttemptId = attemptId

        viewModelScope.launch {
            db.profileDao().updateProbeSessionState(
                sessionId = sid,
                candidateIndex = candidateIndex,
                candidateId = candidateId,
                actionKey = actionKey,
                signalId = signalId,
                physicalSha256 = physicalSha256,
                attemptId = attemptId,
                verifiedActionKeys = serializeActionSet(verifiedActions)
            )
        }
    }

    /**
     * Restore probe session from Room after process death.
     * Returns the session if found, null otherwise.
     */
    suspend fun restoreSession(sessionId: String): ProbeSessionEntity? {
        val entity = db.profileDao().getProbeSession(sessionId) ?: return null
        this.sessionId = sessionId
        savedStateHandle["probeSessionId"] = sessionId
        this.lastPersistedIndex = entity.currentCandidateIndex
        this.lastPersistedCandidateId = entity.currentCandidateId
        this.lastPersistedActionKey = entity.currentActionKey
        this.lastPersistedSignalId = entity.lastSignalId
        this.lastPersistedPhysicalSha256 = entity.lastPhysicalSha256
        this.lastPersistedAttemptId = entity.lastAttemptId

        // Restore step from session status
        val restoredStep = try {
            IrStep.valueOf(entity.status)
        } catch (e: Exception) {
            IrStep.ORIENT
        }
        step = restoredStep

        // Restore verified actions
        setVerifiedActions(parseActionSet(entity.verifiedActionKeys))

        return entity
    }

    /**
     * V0.6.3 Phase 2+3: Initialize the probe engine — accepts any [ProbeCursor]
     * implementation (eager [IrProbeEngine] for brand search, [PagedIrProbeEngine]
     * for universal sweep). Calls [ProbeCursor.initialize] to load page 0 before
     * exposing the engine to the UI. Process-death restore verifies identity via
     * [ProbeRestoreResolver] and never silently picks candidate 0.
     */
    suspend fun initializeEngine(
        engine: ProbeCursor,
        restoreCandidateIndex: Int = 0,
        restoreCandidateId: String? = null
    ): Boolean {
        // V0.6.3 Phase 3: Initialize engine FIRST — this loads page 0 for PagedIrProbeEngine
        when (val initResult = engine.initialize()) {
            is CursorInitResult.Ready -> { /* engine ready, currentCandidate != null */ }
            is CursorInitResult.NoCandidates -> {
                _probeUiState.value = ProbeUiState.NoCompatibleCandidates
                return false
            }
            is CursorInitResult.Error -> {
                _probeUiState.value = ProbeUiState.Error("Engine init failed: ${initResult.reason}")
                return false
            }
        }

        // Reposition to saved candidate (process-death restore with identity guard)
        when (
            val decision = ProbeRestoreResolver.resolve(
                engine = engine,
                restoreCandidateIndex = restoreCandidateIndex,
                restoreCandidateId = restoreCandidateId
            )
        ) {
            is ProbeRestoreDecision.Ready -> { /* safe to resume at the resolved candidate */ }
            is ProbeRestoreDecision.RecoveryRequired -> {
                _probeUiState.value = ProbeUiState.RecoveryRequired(
                    reason = "Candidate identity mismatch after process death. " +
                        "Expected=${decision.expectedId}, Found=${decision.foundId}"
                )
                return false
            }
        }

        probeEngine = engine
        _probeUiState.value = ProbeUiState.Ready(engine)
        return true
    }

    fun currentCandidate(): IrCodeSet? = probeEngine?.currentCandidate()
    suspend fun nextCandidate(): IrCodeSet? = probeEngine?.nextCandidate()
    fun selectById(id: String): Boolean = probeEngine?.selectById(id) ?: false

    fun recordSuccess(action: IrAction) {
        val current = verifiedActions.toMutableSet()
        current.add(action)
        setVerifiedActions(current)
    }

    fun isActionVerified(action: IrAction): Boolean = action in verifiedActions
    fun getCurrentAttempt(): ProbeAttempt? = _currentAttempt.value
    fun setCurrentAttempt(attempt: ProbeAttempt?) { _currentAttempt.value = attempt }
    fun setProbeUiState(state: ProbeUiState) { _probeUiState.value = state }
    fun hasRestorableState(): Boolean = savedStateHandle.get<String>("step") != null
    fun getSessionId(): String? = sessionId ?: savedStateHandle.get<String>("probeSessionId")
    fun getCandidateIndex(): Int = lastPersistedIndex
    fun getCandidateId(): String? = lastPersistedCandidateId

    /**
     * PHASE 3: Persist an individual probe attempt (durable attempt trail:
     * every transmission is recorded against the session, CASCADE-deleted
     * with the session). No-op if no session exists yet.
     */
    fun persistAttempt(
        attempt: ProbeAttempt,
        physicalSha256: String?,
        carrierHz: Int?,
        catalogBuildId: String?
    ) {
        val sid = getSessionId() ?: return
        viewModelScope.launch {
            db.profileDao().insertProbeAttempt(
                ProbeAttemptEntity(
                    attemptId = attempt.attemptId,
                    sessionId = sid,
                    candidateId = attempt.candidateId,
                    codeSetId = attempt.codeSetId,
                    signalId = attempt.signalId,
                    actionKey = attempt.action.name,
                    transmittedAtEpochMs = attempt.transmittedAtMs,
                    result = "CREATED",
                    transmitDurationMs = 0L,
                    physicalSha256 = physicalSha256,
                    carrierHz = carrierHz,
                    catalogBuildId = catalogBuildId,
                    confirmedAtEpochMs = null,
                    confirmedBy = null
                )
            )
        }
    }

    // V0.6.2 PR3 Phase 13 — Attempt evidence lifecycle

    fun updateAttemptStatus(attemptId: String, result: String, durationMs: Long) {
        viewModelScope.launch {
            db.profileDao().updateAttemptResult(attemptId, result, durationMs)
        }
    }

    fun confirmAttempt(attemptId: String, confirmedBy: String) {
        viewModelScope.launch {
            db.profileDao().updateAttemptConfirmation(
                attemptId = attemptId,
                result = "CONFIRMED",
                confirmedAtMs = System.currentTimeMillis(),
                confirmedBy = confirmedBy
            )
        }
    }

    // V0.6.2 PR3 Phase 12 — real process-death recovery: find latest active session

    /**
     * Find the latest active (non-completed) probe session for this brand/device
     * in Room. Returns the session entity if found and its catalog hash matches
     * the current catalog, null otherwise. Callers must verify catalogHashAtStart
     * matches the current catalog hash — restoring against a different catalog
     * is forbidden.
     */
    suspend fun findLatestActiveSession(brand: String, deviceType: String): ProbeSessionEntity? {
        return try {
            db.profileDao().getLatestActiveProbeSession(brand, deviceType)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Mark session complete in Room.
     */
    fun completeSession(winnerCodeSetId: String?) {
        val sid = sessionId ?: return
        viewModelScope.launch {
            db.profileDao().completeProbeSession(
                sessionId = sid,
                status = "COMPLETED",
                completedAtMs = System.currentTimeMillis(),
                winnerCodeSetId = winnerCodeSetId
            )
        }
    }

    /**
     * RC-13: reset the durable session identity AFTER a session is completed
     * (e.g. the universal sweep exhausted without a winner). Without this,
     * the ViewModel keeps the completed session's ID in the field AND in
     * SavedStateHandle; navigating to another brand reuses it via
     * [ensureSession]'s idempotent path and the identity guard fails with
     * "Session Recovery Failed" (Expected=<candidate of the previous sweep>).
     */
    fun resetSessionIdentity() {
        sessionId = null
        savedStateHandle["probeSessionId"] = null
    }

    // ═══════════════════════════════════════════════════════════════════
    // Serialization helpers
    // ═══════════════════════════════════════════════════════════════════

    private fun serializeActionSet(actions: Set<IrAction>): String {
        return actions.joinToString(",") { it.name }
    }

    private fun parseActionSet(json: String): Set<IrAction> {
        if (json.isBlank() || json == "[]") return emptySet()
        return json.split(",").mapNotNull {
            try { IrAction.valueOf(it.trim()) } catch (e: Exception) { null }
        }.toSet()
    }
}
