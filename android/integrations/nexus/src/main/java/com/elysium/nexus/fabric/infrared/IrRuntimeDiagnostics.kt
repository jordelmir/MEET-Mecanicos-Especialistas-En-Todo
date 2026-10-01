package com.elysium.nexus.fabric.infrared

import android.util.Log
import com.elysium.nexus.core.device.IrAction

private const val TAG = "ElysiumNexus.IrDiag"

/**
 * V0.6.3 Phase 1: Structured IR Runtime Diagnostics.
 *
 * Every step of the IR pipeline emits a typed [IrDiagnosticEvent].
 * Events are logged to both logcat (debug) and [FileLog] (for MagicOS
 * encrypted logcat). This replaces ad-hoc Log.d/w/e calls with a
 * single structured telemetry surface.
 *
 * ## Event taxonomy
 *
 * - CATALOG_*: catalog loading and readiness
 * - CANDIDATE_*: candidate selection and ranking
 * - BINDING_*: command binding resolution
 * - PROTOCOL_*: protocol codec resolution
 * - ENCODE_*: signal encoding
 * - TX_*: hardware transmission
 * - RESTORE_*: process-death recovery
 *
 * ## Usage
 *
 * ```kotlin
 * IrRuntimeDiagnostics.event(IrDiagnosticEvent.CatalogReady(hash, count))
 * ```
 */
object IrRuntimeDiagnostics {

    @Volatile
    private var enabled: Boolean = false

    fun initialize(isDebug: Boolean) {
        enabled = isDebug
    }

    fun event(e: IrDiagnosticEvent) {
        val line = e.toLogLine()
        Log.d(TAG, line)
        FileLog.d("DIAG $line")
    }

    fun warn(e: IrDiagnosticEvent) {
        val line = e.toLogLine()
        Log.w(TAG, line)
        FileLog.d("DIAG_W $line")
    }
}

/**
 * Sealed hierarchy of all IR pipeline diagnostic events.
 * Each event carries only PII-free, structurally safe data.
 */
sealed interface IrDiagnosticEvent {

    fun toLogLine(): String

    // ── Catalog ───────────────────────────────────────

    data class CatalogReady(
        val manifestHash: String?,
        val totalCandidates: Int
    ) : IrDiagnosticEvent {
        override fun toLogLine() = "CATALOG_READY hash=${manifestHash?.take(12) ?: "null"} candidates=$totalCandidates"
    }

    data class CatalogError(val reason: String) : IrDiagnosticEvent {
        override fun toLogLine() = "CATALOG_ERROR reason=$reason"
    }

    // ── Candidate ─────────────────────────────────────

    data class CandidateReady(
        val candidateId: String,
        val index: Int,
        val total: Int,
        val engineType: String
    ) : IrDiagnosticEvent {
        override fun toLogLine() = "CANDIDATE_READY id=${candidateId.take(8)} index=$index/$total engine=$engineType"
    }

    data class CandidateExhausted(val totalTested: Int) : IrDiagnosticEvent {
        override fun toLogLine() = "CANDIDATE_EXHAUSTED tested=$totalTested"
    }

    // ── Binding ───────────────────────────────────────

    data class BindingSelected(
        val candidateId: String,
        val action: IrAction,
        val signalId: String,
        val fromSelectedCommands: Boolean
    ) : IrDiagnosticEvent {
        override fun toLogLine() = "BINDING_SELECTED id=${candidateId.take(8)} action=$action signalId=${signalId.take(12)} fromSelected=$fromSelectedCommands"
    }

    data class BindingMissing(val candidateId: String, val action: IrAction) : IrDiagnosticEvent {
        override fun toLogLine() = "BINDING_MISSING id=${candidateId.take(8)} action=$action"
    }

    // ── Protocol ──────────────────────────────────────

    data class ProtocolResolved(
        val protocol: IrProtocol,
        val variantId: String?,
        val carrierHz: Int
    ) : IrDiagnosticEvent {
        override fun toLogLine() = "PROTOCOL_RESOLVED protocol=${protocol.name} variant=$variantId carrier=$carrierHz"
    }

    data class ProtocolUnsupported(val originalName: String) : IrDiagnosticEvent {
        override fun toLogLine() = "PROTOCOL_UNSUPPORTED name=$originalName"
    }

    data class VariantUnsupported(
        val requested: String,
        val available: List<String>
    ) : IrDiagnosticEvent {
        override fun toLogLine() = "VARIANT_UNSUPPORTED requested=$requested available=${available.joinToString()}"
    }

    // ── Encode ────────────────────────────────────────

    data class EncodeSuccess(
        val protocol: IrProtocol,
        val carrierHz: Int,
        val patternSize: Int
    ) : IrDiagnosticEvent {
        override fun toLogLine() = "ENCODE_SUCCESS protocol=${protocol.name} carrier=$carrierHz slices=$patternSize"
    }

    data class EncodeFailed(val reason: String) : IrDiagnosticEvent {
        override fun toLogLine() = "ENCODE_FAILED reason=$reason"
    }

    // ── Transmit ──────────────────────────────────────

    data class TxStart(
        val candidateId: String,
        val action: IrAction,
        val carrierHz: Int
    ) : IrDiagnosticEvent {
        override fun toLogLine() = "TX_START id=${candidateId.take(8)} action=$action carrier=$carrierHz"
    }

    data class TxAccepted(val durationMs: Long) : IrDiagnosticEvent {
        override fun toLogLine() = "TX_ACCEPTED durationMs=$durationMs"
    }

    data class TxFailed(val error: String) : IrDiagnosticEvent {
        override fun toLogLine() = "TX_FAILED error=$error"
    }

    // ── Restore ───────────────────────────────────────

    data class RestoreSession(
        val sessionId: String,
        val candidateIndex: Int,
        val catalogHashMatch: Boolean
    ) : IrDiagnosticEvent {
        override fun toLogLine() = "RESTORE_SESSION id=${sessionId.take(8)} index=$candidateIndex hashMatch=$catalogHashMatch"
    }

    data class RestoreDiscarded(val reason: String) : IrDiagnosticEvent {
        override fun toLogLine() = "RESTORE_DISCARDED reason=$reason"
    }
}
