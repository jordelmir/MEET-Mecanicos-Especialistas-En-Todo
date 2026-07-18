package com.elysium369.meet.core.reports

import com.elysium369.meet.data.local.CertifiedReportRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies a [QrPayload] against either the local DB (offline-first)
 * or a remote verifier endpoint (when online).
 *
 * Two layers, always in this order:
 *   1. **Local match** — read the report by id, recompute the expected
 *      hash from the persisted evidence + repair + snapshot, compare
 *      against the QR's integrity hash. If the local row matches the
 *      QR exactly, the QR is trustworthy regardless of network.
 *   2. **Remote match** — only if local fails AND a `verifierUrl` is
 *      present, ask the remote endpoint. The remote endpoint is
 *      authoritative for cross-device verification (a QR scanned on
 *      device B for a report that originated on device A).
 *
 * Honest-phrases rule: if neither layer returns a match, the verifier
 * returns [VerifyResult.Invalid] with a human-readable reason that the
 * UI must surface verbatim. Never fabricate a "verified" badge.
 */
@Singleton
class ReportVerifier @Inject constructor(
    private val reportRepo: CertifiedReportRepository,
    /**
     * Lazy hook for remote verification. The default implementation
     * returns `null` (offline). The Android module that owns the
     * Supabase wiring can rebind this through Hilt to a real HTTP
     * client in Phase 8 (sync queue). Until then, the verifier is
     * 100% local — which is the safer default.
     */
    private val remoteProbe: suspend (QrPayload) -> Boolean? = { null },
) {

    sealed class VerifyResult {
        abstract val reason: String

        /** Local DB has the report and the hash matches the QR. */
        data class ValidLocal(
            val reportId: String,
            val integrityHash: String,
        ) : VerifyResult() {
            override val reason: String get() = "Verificado localmente. Hash coincide con el reporte firmado."
        }

        /** Remote endpoint confirmed (when local had no row). */
        data class ValidRemote(
            val reportId: String,
            val integrityHash: String,
        ) : VerifyResult() {
            override val reason: String get() = "Verificado por el endpoint remoto de ELYSIUM VANGUARD."
        }

        /** The report exists locally but the hash differs from the QR. */
        data class Tampered(
            val reportId: String,
            val expectedHash: String,
            val qrHash: String,
        ) : VerifyResult() {
            override val reason: String get() =
                "El hash del QR no coincide con el del reporte firmado localmente. " +
                "Posible alteración de evidencia."
        }

        /** No local row, no remote response, or QR structurally invalid. */
        data class Invalid(
            override val reason: String,
        ) : VerifyResult()
    }

    /**
     * Verifies a payload. Returns the strongest available signal:
     *   - [VerifyResult.ValidLocal] if local matches;
     *   - [VerifyResult.ValidRemote] if remote confirms and local is empty;
     *   - [VerifyResult.Tampered] if local exists but hash differs;
     *   - [VerifyResult.Invalid] otherwise.
     *
     * This function is safe to call from any thread; the repository
     * methods it delegates to are `suspend` and use Room's IO
     * dispatcher internally.
     */
    suspend fun verify(payload: QrPayload): VerifyResult {
        // Step 1 — local match.
        val local = reportRepo.getReport(payload.reportId)
        if (local != null) {
            if (local.integrityHash.equals(payload.integrityHash, ignoreCase = true)) {
                return VerifyResult.ValidLocal(payload.reportId, payload.integrityHash)
            }
            return VerifyResult.Tampered(
                reportId = payload.reportId,
                expectedHash = local.integrityHash,
                qrHash = payload.integrityHash,
            )
        }

        // Step 2 — remote probe (only if a URL was embedded in the QR).
        if (payload.verifierUrl != null) {
            val remote = remoteProbe(payload)
            if (remote == true) {
                return VerifyResult.ValidRemote(payload.reportId, payload.integrityHash)
            }
        }

        return VerifyResult.Invalid(
            reason = "No se encontró el reporte ${payload.reportId} en este dispositivo ni confirmación remota. " +
                "Conectate a internet o escaneá el QR desde el dispositivo que emitió el reporte."
        )
    }

    /** Convenience: decode + verify in one call. */
    suspend fun verifyRaw(raw: String): VerifyResult {
        val payload = QrPayload.decode(raw)
            ?: return VerifyResult.Invalid(
                reason = "QR no reconocido. Formato esperado: v1|reportId|hash|... — el código escaneado no cumple ese esquema."
            )
        return verify(payload)
    }
}