package com.elysium.nexus.ui.connect

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.core.device.DeviceTemplate
import com.elysium.nexus.core.device.InstalledIrProfile
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCommandBinding
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.AndroidIrTransmitter
import com.elysium.nexus.fabric.infrared.CursorState
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrRuntimeDiagnostics
import com.elysium.nexus.fabric.infrared.IrDiagnosticEvent
import com.elysium.nexus.fabric.infrared.IrTransmitResult
import com.elysium.nexus.fabric.infrared.PagedIrProbeEngine
import com.elysium.nexus.fabric.infrared.ProbeCursor
import com.elysium.nexus.fabric.infrared.database.IrCatalogDatabaseManager
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository
import com.elysium.nexus.fabric.ranking.CandidatePager
import com.elysium.nexus.ui.help.HelpCard
import com.elysium.nexus.ui.responsive.ResponsiveContainer
import com.elysium.nexus.ui.theme.ElysiumColors
import com.elysium.nexus.ui.theme.NeonCard
import com.elysium.nexus.ui.theme.NeonChip
import com.elysium.nexus.ui.theme.NeonFab
import com.elysium.nexus.ui.theme.NeonHeroCard
import com.elysium.nexus.ui.theme.NeonStatusPill
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ElysiumNexus.IrProbe"

/** PTG-01 §30: schema version written when NO manifest could be read (fail-closed marker, never a hardcoded version). */
private const val INSTALLED_CATALOG_SCHEMA_UNKNOWN = 0

// ═══════════════════════════════════════════════════════════════════════════
// §23 Complete Probe State Machine
// ═══════════════════════════════════════════════════════════════════════════

sealed interface ProbeUiState {
    data object LoadingCatalog : ProbeUiState
    data class Ready(val probeEngine: ProbeCursor) : ProbeUiState
    data object Exhausted : ProbeUiState
    data object NoCompatibleCandidates : ProbeUiState
    data class Transmitting(
        val candidateId: String,
        val codeSetId: String,
        val action: IrAction,
        val attemptId: String
    ) : ProbeUiState
    data class AwaitingConfirmation(
        val candidateId: String,
        val codeSetId: String,
        val attemptId: String,
        val action: IrAction,
        val result: IrTransmitResult
    ) : ProbeUiState
    data class VerifyingSecondaryAction(
        val candidateId: String,
        val codeSetId: String,
        val primaryAttemptId: String,
        val secondaryAction: IrAction,
        val verifiedActions: Set<IrAction>
    ) : ProbeUiState
    data class ChallengeConfirmation(
        val candidateId: String,
        val codeSetId: String,
        val attemptId: String,
        val action: IrAction
    ) : ProbeUiState
    data object SavingProfile : ProbeUiState
    data class Completed(val profile: InstalledIrProfile) : ProbeUiState
    data class Error(val message: String) : ProbeUiState
    /** P0.3: State after process death when candidate identity cannot be verified. */
    data class RecoveryRequired(val reason: String) : ProbeUiState
}

// ═══════════════════════════════════════════════════════════════════════════
// §23 Probe Attempt — authoritatively tracked
// ═══════════════════════════════════════════════════════════════════════════

data class ProbeAttempt(
    val attemptId: String = UUID.randomUUID().toString(),
    val candidateId: String,
    val codeSetId: String,
    val signalId: String,
    val action: IrAction,
    val transmittedAtMs: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════════════════════
// §4/§24 Multi-action verification actions
// ═══════════════════════════════════════════════════════════════════════════

private val VERIFICATION_ACTIONS = listOf(
    IrAction.VOLUME_UP,
    IrAction.VOLUME_DOWN,
    IrAction.MUTE
)

/**
 * Phase A — multi-key universal sweep. The candidate pool is the UNION of
 * these probe keys and the auto-scan transmits every key a candidate
 * exposes, in this order (POWER last: it toggles the TV state, the most
 * visible response). A TV reachable only via MUTE or POWER_TOGGLE is not
 * lost from the sweep.
 */
private val SWEEP_PROBE_KEYS = listOf(
    IrAction.VOLUME_UP,
    IrAction.MUTE,
    IrAction.POWER_TOGGLE
)

/** Phase A — pauses inside the auto-scan slot: between keys and after the last key. */
private const val SWEEP_KEY_GAP_MS = 900L
private const val SWEEP_SLOT_TAIL_MS = 1_500L

@Composable
fun IrConnectFlow(
    template: DeviceTemplate,
    onBack: () -> Unit,
    onProfileInstalled: (InstalledIrProfile) -> Unit,
    onTryOther: () -> Unit,
    irTransmitter: AndroidIrTransmitter,
    hasIrBlaster: Boolean,
    modifier: Modifier = Modifier,
    viewModel: IrProbeViewModel = viewModel()
) {
    // P0.3: ViewModel-backed state survives process death via SavedStateHandle
    var step by remember { mutableStateOf(viewModel.step) }
    var showHelp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // RC-10: restart token. "Start Over" (RecoveryRequired) increments this so
    // the catalog-loading LaunchedEffect re-runs with a fresh session instead
    // of hanging forever on LoadingCatalog (template key alone never changes).
    var sweepRestartToken by remember { mutableIntStateOf(0) }

    var probeUiState by remember { mutableStateOf<ProbeUiState>(ProbeUiState.LoadingCatalog) }
    var currentResult by remember { mutableStateOf<IrTransmitResult?>(null) }
    var currentAttempt by remember { mutableStateOf<ProbeAttempt?>(null) }
    var currentJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var verifiedActions by remember { mutableStateOf<Set<IrAction>>(viewModel.verifiedActions) }
    var autoScanJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isAutoScanning by remember { mutableStateOf(viewModel.isAutoScanning) }
    var lastProbedCandidate by remember { mutableStateOf<IrCodeSet?>(null) }
    // Phase A: the sweep key that actually worked / is being transmitted.
    var lastProbedAction by remember { mutableStateOf<IrAction?>(null) }

    // P0.3: Sync local state → ViewModel on every change
    LaunchedEffect(step) { viewModel.step = step }
    LaunchedEffect(verifiedActions) { viewModel.setVerifiedActions(verifiedActions) }
    LaunchedEffect(isAutoScanning) { viewModel.isAutoScanning = isAutoScanning }

    // §6 Pass targetModel for real ranking
    val targetModel = template.model

    // "Control Universal" is the first card: sweep ALL production-approved
    // TV code sets in the catalog so every tap tests a new candidate.
    val isUniversalSweep = template.id == "tv-universal-generic"

    // Async SQLite Candidate Loading + P1-EVIDENCE: penalty/evidence data
    LaunchedEffect(template, sweepRestartToken) {
        probeUiState = ProbeUiState.LoadingCatalog
        val repo = IrCatalogRepository.getInstance(context)
        val profileRepo = InstalledIrProfileRepository(context)

        // P1-EVIDENCE: Load penalty and evidence data from Room
        val penaltyMap = mutableMapOf<String, Int>()
        val successMap = mutableMapOf<String, Int>()
        val failMap = mutableMapOf<String, Int>()
        try {
            val db = com.elysium.nexus.fabric.profile.db.ElysiumUserDatabase.getInstance(context)
            val penalties = db.profileDao().getTopPenalties(100)
            for (p in penalties) {
                penaltyMap[p.codeSetId] = p.penaltyScore
            }
            val evidenceCounts = db.profileDao().getEvidenceCountsByCodeSet("VOLUME_UP")
            for (row in evidenceCounts) {
                if (row.successCount > 0) successMap[row.codeSetId] = row.successCount
                if (row.failCount > 0) failMap[row.codeSetId] = row.failCount
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load penalty/evidence data: ${e.message}")
        }

        val manifestHash = try {
            IrCatalogDatabaseManager.getInstance(context).catalogDatabaseHash()
        } catch (e: Exception) {
            Log.w(TAG, "Manifest hash unavailable: ${e.message}"); null
        }

        // V0.6.2 PR3 Phase 14: build the correct ProbeCursor for the sweep mode
        val engine: ProbeCursor = if (isUniversalSweep) {
            // Phase 14: bounded-memory paged probe — no limit=400
            // Phase A: multi-key pool — UNION of VOLUME_UP/MUTE/POWER_TOGGLE
            val totalCount = repo.getCandidateCountForActions("TV", SWEEP_PROBE_KEYS)
            if (totalCount == 0) {
                IrRuntimeDiagnostics.warn(IrDiagnosticEvent.CatalogError("zero_candidates_for_TV_sweep_keys"))
                probeUiState = ProbeUiState.NoCompatibleCandidates
                return@LaunchedEffect
            }
            val pager = CandidatePager(
                pageSize = 50,
                maxCachedPages = 4,
                totalCount = totalCount,
                pageLoader = { from, count ->
                    repo.getCandidatePageForActions("TV", SWEEP_PROBE_KEYS, from, count)
                }
            )
            PagedIrProbeEngine(
                pager,
                targetModel,
                penaltyMap,
                successMap,
                failMap,
                probeKeys = SWEEP_PROBE_KEYS
            )
        } else {
            // Brand search: bounded 200, eager load — still correct
            val candidates = repo.getCandidatesForBrand(
                brand = template.brand,
                deviceType = "TV",
                action = IrAction.VOLUME_UP
            )
            if (candidates.isEmpty()) {
                IrRuntimeDiagnostics.warn(IrDiagnosticEvent.CatalogError("zero_candidates_for_brand_${template.brand}"))
                probeUiState = ProbeUiState.NoCompatibleCandidates
                return@LaunchedEffect
            }
            IrProbeEngine(
                rawCandidates = candidates,
                targetModel = targetModel,
                penaltyMap = penaltyMap,
                successMap = successMap,
                failMap = failMap
            )
        }

        // PHASE 3: ensure a durable Room session exists BEFORE any transmission
        viewModel.ensureSession(
            brand = template.brand,
            deviceType = "TV",
            targetModel = targetModel,
            catalogHash = manifestHash
        )

        // V0.6.2 PR3 Phase 12: process-death recovery — restore from SavedStateHandle first,
        // then from Room. Guard: catalog hash mismatch → never restore silently.
        val savedSession = viewModel.getSessionId()?.let { sid ->
            val entity = viewModel.restoreSession(sid)
            if (entity != null && manifestHash != null && entity.catalogHashAtStart != null &&
                manifestHash != entity.catalogHashAtStart
            ) {
                // ULT.21: the SAME hash guard the Room path has. If the
                // catalog changed since that session started, the saved
                // candidate identity is stale → discard, start fresh.
                Log.w(TAG, "ULT.21: Discarding SavedStateHandle session — catalog hash changed (was ${entity.catalogHashAtStart}, now $manifestHash)")
                viewModel.resetSessionIdentity()
                null
            } else {
                entity
            }
        }

        // V0.6.3 Phase 15: if no SavedStateHandle session, try Room latest active.
        // Use .let to return null on catalog hash mismatch (not .also which always returns the entity).
        val restoredSession = savedSession ?: viewModel.findLatestActiveSession(
            template.brand, "TV"
        )?.let { entity ->
            // Phase 12: guard — catalog changed since that session was created → discard (return null)
            if (manifestHash != null && entity.catalogHashAtStart != null && manifestHash != entity.catalogHashAtStart) {
                Log.w(TAG, "Phase 12: discarding stale session — catalog hash changed (was ${entity.catalogHashAtStart}, now $manifestHash)")
                null
            } else {
                viewModel.restoreSession(entity.sessionId)
                entity
            }
        }

        val restoredIndex = restoredSession?.currentCandidateIndex ?: 0
        val restoredId = restoredSession?.currentCandidateId

        val initialized = viewModel.initializeEngine(
            engine = engine,
            restoreCandidateIndex = restoredIndex,
            restoreCandidateId = restoredId
        )

        if (initialized) {
            val readyEngine = viewModel.probeUiState.value.let {
                (it as? ProbeUiState.Ready)?.probeEngine
            }
            if (readyEngine != null) {
                probeUiState = ProbeUiState.Ready(readyEngine)
                // V0.6.3 Phase 1: structured diagnostics
                IrRuntimeDiagnostics.event(IrDiagnosticEvent.CatalogReady(manifestHash, readyEngine.totalCandidates))
                IrRuntimeDiagnostics.event(IrDiagnosticEvent.CandidateReady(
                    candidateId = readyEngine.currentCandidate()?.id ?: "none",
                    index = readyEngine.currentProbeNumber,
                    total = readyEngine.totalCandidates,
                    engineType = if (isUniversalSweep) "Paged" else "Eager"
                ))
                if (restoredSession != null) {
                    Log.d(TAG, "Phase 12: Restored probe session. candidateIndex=$restoredIndex candidateId=$restoredId step=${restoredSession.status}")
                    IrRuntimeDiagnostics.event(IrDiagnosticEvent.RestoreSession(
                        sessionId = restoredSession.sessionId,
                        candidateIndex = restoredIndex,
                        catalogHashMatch = true
                    ))
                } else {
                    Log.d(TAG, "Loaded ${readyEngine.totalCandidates} candidates for brand=${template.brand} (universal=$isUniversalSweep), targetModel=$targetModel, penalties=${penaltyMap.size}, evidence=${successMap.size}")
                }
            }
        } else {
            val failedState = viewModel.probeUiState.value
            if (failedState is ProbeUiState.RecoveryRequired) {
                // ULT.21: never show the dead-end technical screen. The
                // stale session means the catalog changed or the saved
                // position no longer resolves; the honest behavior is an
                // automatic start-over with a clear DIAG trail.
                IrRuntimeDiagnostics.warn(IrDiagnosticEvent.CatalogError(
                    "stale_session_auto_restart: ${failedState.reason}"
                ))
                Log.w(TAG, "ULT.21: RecoveryRequired (${failedState.reason}) — auto restarting sweep from scratch")
                viewModel.completeSession(null)
                viewModel.resetSessionIdentity()
                probeUiState = ProbeUiState.LoadingCatalog
                sweepRestartToken++
            } else {
                probeUiState = failedState
            }
        }
    }

    val activeEngine = (probeUiState as? ProbeUiState.Ready)?.probeEngine

    fun sendTestAction(candidate: IrCodeSet, action: IrAction) {
        // §24 Cancel any in-flight transmission before starting a new one
        currentJob?.cancel()
        currentResult = null

        // P0.2: Use selectedCommands as single authority
        val selectedBinding = candidate.selectedCommands[action]
        val signal = selectedBinding?.signal ?: candidate.commands[action] ?: run {
            // V0.6.3 Phase 11: Signal missing — report to user, don't silently swallow
            IrRuntimeDiagnostics.warn(IrDiagnosticEvent.BindingMissing(candidate.id, action))
            Log.w(TAG, "sendTestAction: no signal for action=$action on candidate=${candidate.id}")
            probeUiState = ProbeUiState.Error("No signal available for ${action.name} on candidate ${candidate.id.take(8)}")
            return
        }
        IrRuntimeDiagnostics.event(IrDiagnosticEvent.BindingSelected(
            candidateId = candidate.id,
            action = action,
            signalId = selectedBinding?.signalId ?: candidate.commandSignalIds[action] ?: "unknown",
            fromSelectedCommands = selectedBinding != null
        ))
        val encodeResult = IrProtocol.encode(signal)
        when (encodeResult) {
            is com.elysium.nexus.fabric.infrared.EncodeResult.Success -> {
                IrRuntimeDiagnostics.event(IrDiagnosticEvent.EncodeSuccess(
                    protocol = (signal as? com.elysium.nexus.core.device.IrSignal.Encoded)?.protocol
                        ?: com.elysium.nexus.fabric.infrared.IrProtocol.Raw,
                    carrierHz = encodeResult.waveform.carrierHz,
                    patternSize = encodeResult.waveform.pattern.size
                ))
                val attempt = ProbeAttempt(
                    candidateId = candidate.id,
                    codeSetId = candidate.id,
                    signalId = selectedBinding?.signalId
                        ?: candidate.commandSignalIds[action]
                        ?: "",
                    action = action
                )
                currentAttempt = attempt

                // V0.6.2 PR3: physical truth on every attempt
                val physicalSha = selectedBinding?.physicalSha256 ?: ""
                val carrierHz = encodeResult.waveform.carrierHz
                val catalogBuildId = try {
                    IrCatalogDatabaseManager.getInstance(context).currentCatalogMetadata()?.catalogBuildId
                } catch (_: Exception) { null }

                // P0.3: Persist probe state to Room for process death recovery
                val engine = activeEngine
                if (engine != null) {
                    viewModel.updateProbeState(
                        candidateIndex = engine.currentProbeNumber - 1,
                        candidateId = candidate.id,
                        actionKey = action.name,
                        signalId = attempt.signalId,
                        physicalSha256 = physicalSha,
                        attemptId = attempt.attemptId
                    )
                }
                // Phase 13: durable attempt trail with full evidence metadata
                viewModel.persistAttempt(attempt, physicalSha, carrierHz, catalogBuildId)

                currentJob = scope.launch {
                    IrRuntimeDiagnostics.event(IrDiagnosticEvent.TxStart(candidate.id, action, carrierHz))
                    val txStart = System.currentTimeMillis()
                    val result = irTransmitter.transmit(encodeResult.waveform)
                    val txDuration = System.currentTimeMillis() - txStart
                    // Phase 13: record outcome with duration
                    viewModel.updateAttemptStatus(
                        attempt.attemptId,
                        if (result is IrTransmitResult.Success) "TRANSMIT_ACCEPTED" else "TRANSMIT_FAILED",
                        txDuration
                    )
                    if (result is IrTransmitResult.Success) {
                        IrRuntimeDiagnostics.event(IrDiagnosticEvent.TxAccepted(txDuration))
                    } else {
                        IrRuntimeDiagnostics.warn(IrDiagnosticEvent.TxFailed(result.toString()))
                    }
                    // §24 Only accept result if attemptId still matches (race guard)
                    if (currentAttempt?.attemptId == attempt.attemptId) {
                        currentResult = result
                    }
                }
            }
            // V0.6.3 Phase 11: All encode errors are visible to the user
            is com.elysium.nexus.fabric.infrared.EncodeResult.UnsupportedProtocol -> {
                IrRuntimeDiagnostics.warn(IrDiagnosticEvent.EncodeFailed("unsupported=${encodeResult.protocol.name}"))
                Log.e(TAG, "sendTestAction: unsupported protocol ${encodeResult.protocol}")
                probeUiState = ProbeUiState.Error("Protocolo ${encodeResult.protocol.displayName} no soportado")
            }
            is com.elysium.nexus.fabric.infrared.EncodeResult.InvalidParameters -> {
                IrRuntimeDiagnostics.warn(IrDiagnosticEvent.EncodeFailed(encodeResult.reason))
                Log.e(TAG, "sendTestAction: invalid parameters: ${encodeResult.reason}")
                probeUiState = ProbeUiState.Error("Parámetros inválidos: ${encodeResult.reason}")
            }
        }
    }

    // §38 Auto-sweep: for each candidate, transmit every probe key it exposes
    // (Phase A multi-key: VOLUME_UP → MUTE → POWER_TOGGLE), pause, advance.
    // Every stop leaves the engine positioned on the LAST transmitted
    // candidate (never a stuck state).
    fun startAutoScan(engine: ProbeCursor) {
        if (isAutoScanning) return
        isAutoScanning = true
        currentResult = null
        autoScanJob?.cancel()
        autoScanJob = scope.launch {
            // RC-11: nextCandidate() returns the candidate it advanced past and
            // transparently loads the following page when the current one ends;
            // it returns null ONLY when the whole sweep is exhausted. Unlike
            // currentCandidate() (which is null at the end of every page and
            // used to stall the sweep on the page boundary), this advances
            // through ALL pages to the final candidate.
            while (isActive) {
                val candidate = engine.nextCandidate() ?: break
                lastProbedCandidate = candidate
                // Phase A: transmit the probe keys the candidate exposes, in
                // sweep order, with a gap so the TV OSD can react per key.
                val keysToSend = SWEEP_PROBE_KEYS.filter { it in candidate.commands }
                if (keysToSend.isEmpty()) continue
                for (key in keysToSend) {
                    if (!isActive) break
                    lastProbedAction = key
                    sendTestAction(candidate, key)
                    delay(if (key == keysToSend.last()) SWEEP_SLOT_TAIL_MS else SWEEP_KEY_GAP_MS)
                }
            }
            isAutoScanning = false
            autoScanJob = null
            // P1-EVIDENCE: log the sweep outcome (exhausted or cancelled).
            if (engine.currentProbeNumber > 0) {
                IrRuntimeDiagnostics.event(
                    IrDiagnosticEvent.CandidateExhausted(
                        totalTested = engine.currentProbeNumber
                    )
                )
            }
            // RC-13: an exhausted sweep has no winner — close its Room session
            // and drop the session identity, otherwise the NEXT brand flow
            // reuses the stale session ID and fails identity recovery
            // ("Expected=<last sweep candidate>" on a different engine).
            if (engine.state == CursorState.EXHAUSTED) {
                viewModel.completeSession(null)
                viewModel.resetSessionIdentity()
            }
        }
    }

    fun stopAutoScan() {
        isAutoScanning = false
        autoScanJob?.cancel()
        autoScanJob = null
    }

    // P1-EVIDENCE: Record when a candidate is rejected by the user
    fun recordCandidateRejection(candidate: com.elysium.nexus.core.device.IrCodeSet, actionKey: String) {
        scope.launch {
            try {
                val profileRepo = InstalledIrProfileRepository(context)
                profileRepo.penalizeCandidate(
                    codeSetId = candidate.id,
                    reason = "user_rejected_$actionKey"
                )
                profileRepo.recordCompatibilityEvidence(
                    codeSetId = candidate.id,
                    brand = candidate.brand,
                    deviceType = "TV",
                    actionKey = actionKey,
                    success = false,
                    source = "local_probe_rejection"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record rejection evidence: ${e.message}")
            }
        }
    }

    // P1-EVIDENCE: Record when a candidate is confirmed by the user
    fun recordCandidateConfirmation(candidate: com.elysium.nexus.core.device.IrCodeSet, actionKey: String) {
        scope.launch {
            try {
                val profileRepo = InstalledIrProfileRepository(context)
                profileRepo.recordCompatibilityEvidence(
                    codeSetId = candidate.id,
                    brand = candidate.brand,
                    deviceType = "TV",
                    actionKey = actionKey,
                    success = true,
                    source = "local_probe_confirmation"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record confirmation evidence: ${e.message}")
            }
        }
    }

    /** P0.1: Read a top-level key from ir_catalog.manifest.json without org.json dependency. */
    fun readManifestKey(key: String): String {
        return try {
            val text = context.assets.open("ir/ir_catalog.manifest.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val pattern = "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            pattern.find(text)?.groupValues?.getOrElse(1) { "unknown" } ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun readCatalogHash(): String = readManifestKey("canonicalContentSha256")

    /** PTG-01 §31: catalog identity comes from the verified build's metadata. */
    fun readCatalogBuildId(): String =
        com.elysium.nexus.fabric.infrared.database.IrCatalogDatabaseManager.getInstance(context)
            .currentCatalogMetadata()?.catalogBuildId
            ?: readManifestKey("catalogBuildId")
            ?: "unknown"

    /** PTG-01 §30: schema version is READ from the manifest — never hardcoded. */
    fun readCatalogSchemaVersion(): Int? =
        com.elysium.nexus.fabric.infrared.database.IrCatalogDatabaseManager.getInstance(context)
            .currentCatalogMetadata()?.schemaVersion
            ?: readManifestKey("schemaVersion").toIntOrNull()

    fun buildAndPersistInstalledProfile(winnerCandidate: com.elysium.nexus.core.device.IrCodeSet, verifiedActions: Set<IrAction>): InstalledIrProfile? {
        val bindings = mutableMapOf<IrAction, IrCommandBinding>()

        // P0.2: Use selectedCommands as single authority
        for ((action, selectedBinding) in winnerCandidate.selectedCommands) {
            val fp = IrProbeEngine.fingerprintSignal(selectedBinding.signal)
            bindings[action] = IrCommandBinding(
                signalId = selectedBinding.signalId,
                physicalFingerprint = fp,
                sourceId = selectedBinding.sourceRevisionId.ifBlank {
                    winnerCandidate.provenance.commitSha ?: "catalog-legacy"
                },
                action = action
            )
        }

        // Fallback: if selectedCommands is empty, use legacy fields (deprecated path)
        if (bindings.isEmpty()) {
            for ((action, signal) in winnerCandidate.commands) {
                val realSignalId = winnerCandidate.commandSignalIds[action]
                    ?: winnerCandidate.commandBindings.firstOrNull { it.action == action }?.signalId
                    ?: continue

                val fp = IrProbeEngine.fingerprintSignal(signal)
                val sourceRevision = winnerCandidate.commandBindings
                    .firstOrNull { it.action == action }
                    ?.sourceRevisionId
                    ?: winnerCandidate.provenance.commitSha
                    ?: "catalog-legacy"
                bindings[action] = IrCommandBinding(
                    signalId = realSignalId,
                    physicalFingerprint = fp,
                    sourceId = sourceRevision,
                    action = action
                )
            }
        }

        if (bindings.isEmpty()) {
            Log.e(TAG, "CRITICAL: Winner ${winnerCandidate.id} has ZERO real bindings. Refusing to save.")
            return null
        }

        val status = when {
            verifiedActions.size >= 3 -> VerificationStatus.SESSION_VERIFIED
            verifiedActions.size >= 2 -> VerificationStatus.PARTIALLY_VERIFIED
            verifiedActions.isNotEmpty() -> VerificationStatus.PARTIALLY_VERIFIED
            else -> VerificationStatus.UNVERIFIED
        }

        val profile = InstalledIrProfile(
            displayName = "${winnerCandidate.brand} Remote (${winnerCandidate.id.take(8)})",
            brand = winnerCandidate.brand,
            deviceType = template.category.name,
            model = winnerCandidate.modelPatterns.firstOrNull(),
            remoteModel = winnerCandidate.remoteModels.firstOrNull(),
            codeSetId = winnerCandidate.id,
            sourceRevision = winnerCandidate.commandBindings.firstOrNull()?.sourceRevisionId
                ?: winnerCandidate.provenance.commitSha
                ?: "catalog-legacy",
            catalogSchemaVersionAtInstall = readCatalogSchemaVersion()
                ?: INSTALLED_CATALOG_SCHEMA_UNKNOWN,
            catalogCanonicalHashAtInstall = readCatalogHash(),
            catalogBuildIdAtInstall = readCatalogBuildId(),
            commands = bindings,
            verifiedActions = verifiedActions,
            verificationStatus = status
        )

        val profileRepo = InstalledIrProfileRepository(context)
        val result = profileRepo.saveProfile(profile, verifiedActions)
        return when (result) {
            is com.elysium.nexus.fabric.profile.SaveProfileResult.Saved -> {
                Log.d(TAG, "Installed profile ${profile.id} with ${bindings.size} bindings, verified=$verifiedActions")
                profile
            }
            is com.elysium.nexus.fabric.profile.SaveProfileResult.ValidationFailure -> {
                Log.e(TAG, "Profile validation failed: ${result.reason}")
                null
            }
            is com.elysium.nexus.fabric.profile.SaveProfileResult.StorageFailure -> {
                Log.e(TAG, "Profile storage failed: ${result.cause.message}")
                null
            }
        }
    }

    ResponsiveContainer(modifier = modifier) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = info.sidePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NeonChip(
                    label = "Atrás",
                    onClick = onBack,
                    accent = ElysiumColors.NeonPurple,
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElysiumColors.NeonPurple.copy(alpha = 0.6f))
                        .clickable { showHelp = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = "Ayuda", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            NeonHeroCard(
                title = "${template.brand} ${template.model}",
                subtitle = template.blurbEs,
                accent = ElysiumColors.NeonCyan,
                modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 4.dp),
                statusChips = {
                    NeonStatusPill(label = "Paso ${step.number} de ${IrStep.entries.size}", color = ElysiumColors.NeonOrange)
                    activeEngine?.let { engine ->
                        NeonStatusPill(label = "Probe ${engine.currentProbeNumber}/${engine.totalCandidates}", color = ElysiumColors.NeonPurple)
                    }
                }
            )

            StepIndicator(currentStep = step, modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding, vertical = 8.dp))

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = info.sidePadding)) {
                when (val uiState = probeUiState) {
                    is ProbeUiState.LoadingCatalog -> {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ElysiumColors.NeonCyan)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Cargando catálogo SQLite...", style = TextStyle(fontSize = 14.sp, color = ElysiumColors.OnSurfaceVariant))
                            }
                        }
                    }
                    is ProbeUiState.Ready -> {
                        val engine = uiState.probeEngine
                        AnimatedContent(
                            targetState = step,
                            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.95f)) },
                            label = "ir_step"
                        ) { currentStep ->
                            when (currentStep) {
                                IrStep.ORIENT -> OrientStep(
                                    onContinue = {
                                        step = IrStep.TEST
                                        val candidate = engine.currentCandidate() ?: return@OrientStep
                                        sendTestAction(candidate, IrAction.VOLUME_UP)
                                    },
                                    hasIrBlaster = hasIrBlaster
                                )
                                IrStep.TEST -> TestStep(
                                    template = template,
                                    probeEngine = engine,
                                    lastResult = currentResult,
                                    isAutoScanning = isAutoScanning,
                                    currentAction = lastProbedAction ?: IrAction.VOLUME_UP,
                                    onSendTest = {
                                        if (isAutoScanning) {
                                            // During sweep the user only stops; taps are confirmations.
                                            return@TestStep
                                        }
                                        val candidate = engine.currentCandidate() ?: return@TestStep
                                        sendTestAction(candidate, IrAction.VOLUME_UP)
                                    },
                                    onDidWork = {
                                        // §38 P0-3 Challenge confirmation: re-transmit to prevent
                                        // wrong candidate selection from timing race.
                                        val winner = lastProbedCandidate ?: engine.currentCandidate()
                                        if (winner != null) {
                                            stopAutoScan()
                                            engine.selectById(winner.id)
                                            // Phase A: re-transmit the SAME key that worked
                                            // (VOLUME_UP, MUTE or POWER_TOGGLE), not a hardcoded one.
                                            val confirmKey = lastProbedAction ?: IrAction.VOLUME_UP
                                            step = IrStep.CHALLENGE
                                            sendTestAction(winner, confirmKey)
                                        }
                                    },
                                    onNextCandidate = {
                                        if (isAutoScanning) return@TestStep
                                        // P1-EVIDENCE: Record rejection before advancing
                                        val rejected = engine.currentCandidate()
                                        if (rejected != null) recordCandidateRejection(rejected, lastProbedAction?.name ?: "VOLUME_UP")
                                        // V0.6.3 Phase 4: Atomic advance — nextCandidate() must complete
                                        // before currentCandidate() is read. Single coroutine, no race.
                                        scope.launch {
                                            engine.nextCandidate()
                                            lastProbedCandidate = engine.currentCandidate()
                                            currentResult = null
                                            val candidate = engine.currentCandidate() ?: return@launch
                                            sendTestAction(candidate, IrAction.VOLUME_UP)
                                        }
                                    },
                                    onStartAutoScan = { startAutoScan(engine) },
                                    onStopAutoScan = { stopAutoScan() },
                                    hasIrBlaster = hasIrBlaster
                                )
                                IrStep.CHALLENGE -> ChallengeStep(
                                    lastResult = currentResult,
                                    action = lastProbedAction ?: IrAction.VOLUME_UP,
                                    onDidWork = {
                                        // P0-1: Challenge confirmed — the sweep key verified twice.
                                        // Phase 13: record evidence with confirmed timestamp
                                        val winner = engine.currentCandidate()
                                        if (winner != null) {
                                            val confirmKey = lastProbedAction ?: IrAction.VOLUME_UP
                                            verifiedActions = setOf(confirmKey)
                                            recordCandidateConfirmation(winner, confirmKey.name)
                                            // Phase 13: confirm the attempt as proven
                                            currentAttempt?.let { att ->
                                                viewModel.confirmAttempt(att.attemptId, "USER_CHALLENGE")
                                            }
                                            if (IrAction.VOLUME_DOWN in winner.commands) {
                                                step = IrStep.VERIFY_SECONDARY
                                                sendTestAction(winner, IrAction.VOLUME_DOWN)
                                            } else {
                                                step = IrStep.SAVE
                                            }
                                        }
                                    },
                                    onNo = {
                                        // P1-EVIDENCE: Record rejection on challenge failure
                                        val rejected = engine.currentCandidate()
                                        if (rejected != null) recordCandidateRejection(rejected, lastProbedAction?.name ?: "VOLUME_UP")
                                        // V0.6.3 Phase 4: Atomic advance — nextCandidate() must complete
                                        // before currentCandidate() is read. Single coroutine, no race.
                                        scope.launch {
                                            engine.nextCandidate()
                                            currentResult = null
                                            currentAttempt = null
                                            verifiedActions = emptySet()
                                            lastProbedAction = null
                                            step = IrStep.TEST
                                            val candidate = engine.currentCandidate() ?: return@launch
                                            sendTestAction(candidate, IrAction.VOLUME_UP)
                                        }
                                    }
                                )
                                IrStep.VERIFY_SECONDARY -> VerifyActionStep(
                                    actionLabel = "VOLUME_DOWN",
                                    action = IrAction.VOLUME_DOWN,
                                    lastResult = currentResult,
                                    currentAttempt = currentAttempt,
                                    onSendTest = {
                                        val candidate = engine.currentCandidate() ?: return@VerifyActionStep
                                        sendTestAction(candidate, IrAction.VOLUME_DOWN)
                                    },
                                    onDidWork = {
                                        // §24 VOLUME_DOWN verified. Now verify MUTE.
                                        verifiedActions = verifiedActions + IrAction.VOLUME_DOWN
                                        val candidate = engine.currentCandidate()
                                        if (candidate != null && IrAction.MUTE in candidate.commands) {
                                            step = IrStep.VERIFY_TERTIARY
                                            sendTestAction(candidate, IrAction.MUTE)
                                        } else {
                                            step = IrStep.SAVE
                                        }
                                    },
                                    onSkip = {
                                        // §24 User says VOLUME_DOWN didn't work, skip to MUTE or SAVE
                                        val candidate = engine.currentCandidate()
                                        if (candidate != null && IrAction.MUTE in candidate.commands) {
                                            step = IrStep.VERIFY_TERTIARY
                                            sendTestAction(candidate, IrAction.MUTE)
                                        } else {
                                            step = IrStep.SAVE
                                        }
                                    }
                                )
                                IrStep.VERIFY_TERTIARY -> VerifyActionStep(
                                    actionLabel = "MUTE",
                                    action = IrAction.MUTE,
                                    lastResult = currentResult,
                                    currentAttempt = currentAttempt,
                                    onSendTest = {
                                        val candidate = engine.currentCandidate() ?: return@VerifyActionStep
                                        sendTestAction(candidate, IrAction.MUTE)
                                    },
                                    onDidWork = {
                                        // §24 All 3 actions verified
                                        verifiedActions = verifiedActions + IrAction.MUTE
                                        step = IrStep.SAVE
                                    },
                                    onSkip = {
                                        step = IrStep.SAVE
                                    }
                                )
                                IrStep.SAVE -> SaveStep(
                                    template = template,
                                    verifiedActions = verifiedActions,
                                    onSave = {
                                        val winner = engine.currentCandidate()
                                        if (winner != null) {
                                            // §24 Use actually verified actions, not assumed
                                            val profile = buildAndPersistInstalledProfile(winner, verifiedActions)
                                            if (profile != null) {
                                                // RC-13: a winning session must close with its winner —
                                                // never leave it ACTIVE for a later brand flow to restore.
                                                viewModel.completeSession(winner.id)
                                                viewModel.resetSessionIdentity()
                                                probeUiState = ProbeUiState.Completed(profile)
                                                onProfileInstalled(profile)
                                            } else {
                                                onTryOther()
                                            }
                                        } else {
                                            onTryOther()
                                        }
                                    },
                                    onLearnInstead = onTryOther
                                )
                            }
                        }
                    }
                    is ProbeUiState.Exhausted -> {
                        NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonOrange, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Candidatos agotados", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
                                Text("Se probaron todos los candidatos. Puedes buscar otro modelo o marca.", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant)
                            }
                        }
                    }
                    is ProbeUiState.NoCompatibleCandidates -> {
                        NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonOrange, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Sin candidatos compatibles", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
                                Text("El catálogo no contiene códigos IR para ${template.brand}. Opciones:", style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant)
                                NeonChip(label = "Buscar modelo exacto del TV", onClick = onTryOther, accent = ElysiumColors.NeonCyan, modifier = Modifier.fillMaxWidth())
                                NeonChip(label = "Buscar modelo del control", onClick = onTryOther, accent = ElysiumColors.NeonCyan, modifier = Modifier.fillMaxWidth())
                                NeonChip(label = "Importar perfil", onClick = onTryOther, accent = ElysiumColors.NeonCyan, modifier = Modifier.fillMaxWidth())
                                NeonChip(label = "Aprender mediante receptor IR", onClick = onTryOther, accent = ElysiumColors.NeonCyan, modifier = Modifier.fillMaxWidth())
                                NeonChip(label = "Volver y elegir otra marca", onClick = onBack, accent = ElysiumColors.NeonPurple, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    is ProbeUiState.Transmitting, is ProbeUiState.AwaitingConfirmation,
                    is ProbeUiState.VerifyingSecondaryAction, is ProbeUiState.ChallengeConfirmation,
                    is ProbeUiState.SavingProfile, is ProbeUiState.Completed -> {
                        // States handled by navigation, not rendered here
                    }
                    is ProbeUiState.Error -> {
                        Text("Error: ${uiState.message}", color = Color.Red)
                    }
                    is ProbeUiState.RecoveryRequired -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Session Recovery Failed",
                                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                color = Color.Red
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.reason,
                                style = TextStyle(fontSize = 14.sp),
                                color = ElysiumColors.OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            NeonFab(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Start Over"
                                    )
                                },
                                onClick = {
                                    viewModel.step = IrStep.ORIENT
                                    viewModel.setVerifiedActions(emptySet())
                                    step = IrStep.ORIENT
                                    verifiedActions = emptySet()
                                    probeUiState = ProbeUiState.LoadingCatalog
                                    // RC-10: force the LaunchedEffect to re-run fresh
                                    sweepRestartToken++
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showHelp) {
        HelpCard(
            title = "Ayuda — Probar ${template.brand}",
            whatIsThis = "Esta pantalla busca un perfil de control IR probando señales del catálogo.",
            howToUse = listOf(
                "Paso 1: Asegúrate de que la TV esté encendida.",
                "Paso 2: Apunta el teléfono al sensor IR. La señal se envía automáticamente.",
                "Paso 3: Si la TV reacciona (sube el volumen, silencia o se apaga/enciende), toca 'Sí'. Si no, toca 'Probar siguiente'."
            ),
            tip = "El barrido universal prueba cada candidato con Volumen, Mute y Encendido/Apagado — si la TV responde a cualquiera, el candidato se verifica y se confirman VOLUME_UP, VOLUME_DOWN y MUTE del mismo codeSet.",
            onDismiss = { showHelp = false }
        )
    }
}

internal enum class IrStep(val number: Int, val labelEn: String, val labelEs: String) {
    ORIENT(1, "Aim", "Apuntar"),
    TEST(2, "Test", "Probar"),
    CHALLENGE(3, "Verify", "Re-verificar"),
    VERIFY_SECONDARY(4, "Down", "Bajar"),
    VERIFY_TERTIARY(5, "Mute", "Mute"),
    SAVE(6, "Save", "Guardar");

    companion object {
        /**
         * P0.4: Explicit transition table.
         * Each entry: (fromStep, event) → nextStep.
         * null nextStep means terminal (save complete or abort).
         */
        fun transition(from: IrStep, event: String): IrStep? = when (from to event) {
            ORIENT to "continue" -> TEST
            TEST to "did_work" -> CHALLENGE
            TEST to "next_candidate" -> TEST
            TEST to "exhausted" -> null
            CHALLENGE to "confirmed" -> VERIFY_SECONDARY
            CHALLENGE to "failed" -> TEST
            VERIFY_SECONDARY to "did_work" -> VERIFY_TERTIARY
            VERIFY_SECONDARY to "skip" -> VERIFY_TERTIARY
            VERIFY_TERTIARY to "did_work" -> SAVE
            VERIFY_TERTIARY to "skip" -> SAVE
            else -> null
        }
    }
}

@Composable
private fun StepIndicator(currentStep: IrStep, modifier: Modifier = Modifier) {
    val steps = IrStep.entries.toTypedArray()
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { idx, s ->
            val isActive = s == currentStep
            val isPassed = s.number < currentStep.number
            val color = when {
                isActive -> ElysiumColors.NeonCyan
                isPassed -> ElysiumColors.NeonGreen
                else -> ElysiumColors.OnSurfaceVariant.copy(alpha = 0.4f)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("${s.number}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = color)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(s.labelEs, style = TextStyle(fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal), color = color)
            }
            if (idx < steps.size - 1) {
                Box(modifier = Modifier.weight(1f).height(2.dp).padding(horizontal = 4.dp).background(if (isPassed) ElysiumColors.NeonGreen else ElysiumColors.OnSurfaceVariant.copy(alpha = 0.2f)))
            }
        }
    }
}

@Composable
private fun OrientStep(onContinue: () -> Unit, hasIrBlaster: Boolean) {
    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonCyan, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Preparación de Sondeo IR", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text(
                if (hasIrBlaster) "El emisor IR enviará códigos de prueba. Mantén el teléfono apuntado al sensor IR."
                else "Este teléfono no tiene emisor IR. Puedes probar con un receptor externo USB.",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(label = "Comenzar Prueba", onClick = onContinue, accent = ElysiumColors.NeonCyan, icon = { Icon(Icons.Filled.Bolt, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TestStep(
    template: DeviceTemplate,
    probeEngine: ProbeCursor,
    lastResult: IrTransmitResult?,
    isAutoScanning: Boolean,
    currentAction: IrAction,
    onSendTest: () -> Unit,
    onDidWork: () -> Unit,
    onNextCandidate: () -> Unit,
    onStartAutoScan: () -> Unit,
    onStopAutoScan: () -> Unit,
    hasIrBlaster: Boolean
) {
    val currentCand = probeEngine.currentCandidate()
    val canConfirm = lastResult is IrTransmitResult.Success && currentCand != null

    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonPurple, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (isAutoScanning) "Barrido Automático Activo — Candidato ${probeEngine.currentProbeNumber} de ${probeEngine.totalCandidates}"
                else "Prueba de ${currentAction.name} — Candidato ${probeEngine.currentProbeNumber} de ${probeEngine.totalCandidates}",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface
            )
            Text(
                "Perfil: ${currentCand?.brand ?: template.brand} (${currentCand?.id?.take(12) ?: "?"})\n" +
                "Acción: ${currentAction.name}\n" +
                "Verificación: ${currentCand?.verification ?: VerificationStatus.UNVERIFIED}",
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NeonFab(icon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(36.dp)) }, onClick = onSendTest, accent = ElysiumColors.NeonCyan, fabSize = 80.dp)
            }
            lastResult?.let { res ->
                val resultText = when (res) {
                    is IrTransmitResult.Success -> "Transmitido: ${res.carrierHz} Hz"
                    is IrTransmitResult.NoEmitter -> "Sin emisor IR"
                    is IrTransmitResult.PermissionDenied -> "Permiso denegado"
                    is IrTransmitResult.UnsupportedCarrier -> "Frecuencia no soportada"
                    is IrTransmitResult.InvalidPattern -> "Patrón inválido"
                    is IrTransmitResult.Busy -> "Emisor ocupado"
                    is IrTransmitResult.PlatformFailure -> "Error Android"
                }
                val color = if (res is IrTransmitResult.Success) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
                NeonStatusPill(label = resultText, color = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isAutoScanning) {
                // §38 While sweeping: one big action — stop and confirm.
                NeonChip(label = "¡Funcionó! Detener barrido", onClick = onDidWork, accent = ElysiumColors.NeonGreen, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
                NeonChip(label = "Pausar barrido", onClick = onStopAutoScan, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Pause, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
            } else {
                NeonChip(label = "▶ Barrido automático (probar todas las marcas)", onClick = onStartAutoScan, accent = ElysiumColors.NeonCyan, icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip(label = "Sí, funcionó", onClick = { if (canConfirm) onDidWork() }, accent = if (canConfirm) ElysiumColors.NeonGreen else Color.Gray, active = canConfirm, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.weight(1f))
                    if (probeEngine.hasMore) {
                        NeonChip(label = "No / Siguiente", onClick = onNextCandidate, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeStep(
    lastResult: IrTransmitResult?,
    action: IrAction,
    onDidWork: () -> Unit,
    onNo: () -> Unit
) {
    val canConfirm = lastResult is IrTransmitResult.Success

    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonCyan, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Re-verificación de señal", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text(
                "Se reenvió ${action.name} para confirmar que este candidato funciona. ¿La TV reaccionó?",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NeonFab(icon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(36.dp)) }, onClick = {}, accent = ElysiumColors.NeonCyan, fabSize = 80.dp)
            }
            lastResult?.let { res ->
                val resultText = when (res) {
                    is IrTransmitResult.Success -> "Re-transmitido: ${res.carrierHz} Hz"
                    is IrTransmitResult.NoEmitter -> "Sin emisor IR"
                    is IrTransmitResult.PermissionDenied -> "Permiso denegado"
                    is IrTransmitResult.UnsupportedCarrier -> "Frecuencia no soportada"
                    is IrTransmitResult.InvalidPattern -> "Patrón inválido"
                    is IrTransmitResult.Busy -> "Emisor ocupado"
                    is IrTransmitResult.PlatformFailure -> "Error Android"
                }
                val color = if (res is IrTransmitResult.Success) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
                NeonStatusPill(label = resultText, color = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip(label = "Sí, funcionó", onClick = { if (canConfirm) onDidWork() }, accent = if (canConfirm) ElysiumColors.NeonGreen else Color.Gray, active = canConfirm, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.weight(1f))
                NeonChip(label = "No, siguiente", onClick = onNo, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * §24 Verification step for secondary/tertiary actions (VOLUME_DOWN, MUTE).
 * Transmits the action, shows result, asks user to confirm.
 */
@Composable
private fun VerifyActionStep(
    actionLabel: String,
    action: IrAction,
    lastResult: IrTransmitResult?,
    currentAttempt: ProbeAttempt?,
    onSendTest: () -> Unit,
    onDidWork: () -> Unit,
    onSkip: () -> Unit
) {
    val canConfirm = lastResult is IrTransmitResult.Success && currentAttempt != null && currentAttempt.action == action

    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonCyan, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Verificar $actionLabel", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text(
                "Acción: $actionLabel\n¿La TV reaccionó a esta acción?",
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NeonFab(icon = { Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(36.dp)) }, onClick = onSendTest, accent = ElysiumColors.NeonCyan, fabSize = 80.dp)
            }
            lastResult?.let { res ->
                val resultText = when (res) {
                    is IrTransmitResult.Success -> "Transmitido: ${res.carrierHz} Hz"
                    is IrTransmitResult.NoEmitter -> "Sin emisor IR"
                    is IrTransmitResult.PermissionDenied -> "Permiso denegado"
                    is IrTransmitResult.UnsupportedCarrier -> "Frecuencia no soportada"
                    is IrTransmitResult.InvalidPattern -> "Patrón inválido"
                    is IrTransmitResult.Busy -> "Emisor ocupado"
                    is IrTransmitResult.PlatformFailure -> "Error Android"
                }
                val color = if (res is IrTransmitResult.Success) ElysiumColors.NeonGreen else ElysiumColors.NeonOrange
                NeonStatusPill(label = resultText, color = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip(label = "Sí, funcionó", onClick = { if (canConfirm) onDidWork() }, accent = if (canConfirm) ElysiumColors.NeonGreen else Color.Gray, active = canConfirm, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.weight(1f))
                NeonChip(label = "Saltar", onClick = onSkip, accent = ElysiumColors.NeonOrange, icon = { Icon(Icons.Filled.Refresh, contentDescription = null) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SaveStep(template: DeviceTemplate, verifiedActions: Set<IrAction>, onSave: () -> Unit, onLearnInstead: () -> Unit) {
    val verifiedCount = verifiedActions.size
    val statusText = when {
        verifiedCount >= 3 -> "3 acciones verificadas (VOLUME_UP + VOLUME_DOWN + MUTE)"
        verifiedCount >= 2 -> "2 acciones verificadas"
        verifiedCount == 1 -> "1 acción verificada (VOLUME_UP)"
        else -> "Sin verificación"
    }
    NeonCard(modifier = Modifier.fillMaxWidth(), accent = ElysiumColors.NeonGreen, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Perfil Encontrado", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = ElysiumColors.OnSurface)
            Text(statusText, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), color = ElysiumColors.OnSurfaceVariant)
            Text("Se guardará el perfil completo con las acciones verificadas.", style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = ElysiumColors.OnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            NeonChip(label = "Instalar Control Remoto", onClick = onSave, accent = ElysiumColors.NeonGreen, icon = { Icon(Icons.Filled.Check, contentDescription = null) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
