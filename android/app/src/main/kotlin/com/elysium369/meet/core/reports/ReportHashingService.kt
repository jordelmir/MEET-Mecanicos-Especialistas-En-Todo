package com.elysium369.meet.core.reports

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports Hashing Service — injectable façade over the pure [HashEngine]
 * and the `computeHash` of [DiagnosticSnapshot].
 *
 * Two parallel surfaces share this service so the same Hilt graph can:
 *   1. Sign a [DraftReport] (the per-vehicle signed-report chain).
 *   2. Sign a [DiagnosticSnapshot] (the per-session snapshot chain).
 *
 * PARITY WITH TYPESCRIPT
 * ──────────────────────────────────────────────────────────────────────
 * The snapshot path is byte-exact with `lib/reports/hash.ts#canonicalSnapshotString`.
 * The draft-report path is byte-exact with `lib/reports/hash.ts#hashReport`.
 *
 * The reference golden value lives at:
 *   tests/parity/fixtures/snapshot-p0230.json
 *     expectedHash = "71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e"
 *
 * Calling [p0230ParityDemo] from the APK should reproduce that hash. If it
 * doesn't, the canonical serialization has drifted between Kotlin and TS.
 *
 * This class is intentionally `@Singleton` and stateless; the only state
 * is the immutability of [P0230_EXPECTED_HASH]. That makes it safe to
 * inject anywhere without ordering hazards.
 */
@Singleton
class ReportHashingService @Inject constructor() {

    /**
     * Canonical P0230 expected hash. Mirrored from
     *   tests/parity/fixtures/snapshot-p0230.json#expectedHash.
     *
     * Updating this value without updating the TS fixture is a hard
     * break of cross-runtime parity — the CI parity verifier will catch
     * it but reviewers should not let it pass silently.
     */
    val p0230ExpectedHash: String = "71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e"

    /**
     * Reference Hyundai Accent Verna 2005 P0230 snapshot — mirrors
     * `tests/parity/fixtures/snapshot-p0230.json`. Used as the round-trip
     * parity demonstration visible from the UI.
     */
    fun buildP0230ReferenceSnapshot(): DiagnosticSnapshot = DiagnosticSnapshot(
        id = "snap-p0230-reference",
        vehicleId = "v-accent-verna-2005",
        sessionId = "s-2026-07-04-001",
        createdAtMs = 1700000000000L,
        dtcsActive = listOf("P0230", "P1709"),
        dtcsPending = emptyList(),
        dtcsPermanent = emptyList(),
        freezeFramePidValues = mapOf("RPM" to 850.0, "ECT" to 88.0),
        readiness = mapOf("Misfire" to true, "Fuel" to true),
        ecuVoltage = 14.1,
        rpm = 850.0,
        coolantTempC = 88.0,
        speedKph = 0.0,
        engineLoadPct = null,
        fuelTrimStft = 0.5,
        fuelTrimLtft = -1.2,
        provenance = DiagnosticProvenance.Real,
        notes = "Round-trip P0230 reference | source: tests/parity/fixtures/snapshot-p0230.json | parity demo 2026-07-04",
    )

    data class ParityResult(
        val computedHash: String,
        val expectedHash: String,
        val match: Boolean,
        val canonical: String,
    ) {
        val summary: String get() = if (match) "MATCH ✓ byte-exact with TS" else "MISMATCH ✗ parity drift"
    }

    /**
     * Builds the P0230 reference snapshot, signs it, and compares against
     * [p0230ExpectedHash]. Returns a structured [ParityResult].
     *
     * Designed for UI display: every field is meant to be rendered, not
     * thrown. The status is the user's at-a-glance proof that the Kotlin
     * runtime produces the same SHA-256 as the TypeScript web app.
     */
    fun p0230ParityDemo(): ParityResult {
        val snap = buildP0230ReferenceSnapshot()
        val canonical = canonicalSnapshotPreview(snap)
        val computed = snap.hashSha256
        return ParityResult(
            computedHash = computed,
            expectedHash = p0230ExpectedHash,
            match = computed.equals(p0230ExpectedHash, ignoreCase = true),
            canonical = canonical,
        )
    }

    /**
     * Builds a [DraftReport] from the given pieces, signs it, and
     * compares against the expected hash if supplied.
     *
     * @param expectedHash optional TS-side or previously-signed expected
     *        hash, lower- or upper-case hex.
     */
    data class ReportSignResult(
        val hash: String,
        val canonical: String,
        val expectedHash: String?,
        val match: Boolean?,
    )

    fun signDraftReport(
        vehicleId: String,
        userId: String,
        reportType: String,
        title: String,
        odometerKm: Long?,
        vin: String?,
        plate: String?,
        privacyRedactVin: Boolean,
        privacyRedactPlate: Boolean,
        privacyRedactLocation: Boolean,
        privacyPublicShare: Boolean,
        snapshotHash: String?,
        evidenceHashes: List<String>,
        repairActionHashes: List<String>,
        peritajeHash: String?,
        previousHash: String?,
        notes: String,
        expectedHash: String? = null,
    ): ReportSignResult {
        val draft = DraftReport(
            vehicleId = vehicleId,
            userId = userId,
            reportType = reportType,
            title = title,
            odometerKm = odometerKm,
            vin = vin,
            plate = plate,
            privacyRedactVin = privacyRedactVin,
            privacyRedactPlate = privacyRedactPlate,
            privacyRedactLocation = privacyRedactLocation,
            privacyPublicShare = privacyPublicShare,
            snapshotHash = snapshotHash,
            evidenceHashes = evidenceHashes,
            repairActionHashes = repairActionHashes,
            peritajeHash = peritajeHash,
            previousHash = previousHash,
            notes = notes,
        )
        val hash = HashEngine.hashReport(draft)
        val canonical = HashEngine.canonicalReportString(draft)
        val match = expectedHash?.let { hash.equals(it, ignoreCase = true) }
        return ReportSignResult(
            hash = hash,
            canonical = canonical,
            expectedHash = expectedHash,
            match = match,
        )
    }

    /**
     * Convenience used by tests + the demo UI to inspect the canonical
     * serialization before hashing. Mirrors `DiagnosticSnapshot.computeHash`
     * without forcing callers to construct a HashEngine peer.
     */
    fun canonicalSnapshotPreview(snap: DiagnosticSnapshot): String = buildString {
        append(snap.vehicleId).append('|')
        append(snap.sessionId ?: "").append('|')
        append(snap.createdAtMs).append('|')
        append(snap.dtcsActive.sorted().joinToString(",")).append('|')
        append(snap.dtcsPending.sorted().joinToString(",")).append('|')
        append(snap.dtcsPermanent.sorted().joinToString(",")).append('|')
        append(snap.freezeFramePidValues.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }).append('|')
        append(snap.readiness.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }).append('|')
        append(snap.ecuVoltage ?: "null").append('|')
        append(snap.rpm ?: "null").append('|')
        append(snap.coolantTempC ?: "null").append('|')
        append(snap.speedKph ?: "null").append('|')
        append(snap.engineLoadPct ?: "null").append('|')
        append(snap.fuelTrimStft ?: "null").append('|')
        append(snap.fuelTrimLtft ?: "null")
    }

    /**
     * Build a tiny dev-time chain (3 reports) so the UI can render the
     * [HashEngine.verifyChain] path with a visible ok/break signal.
     */
    fun demoReportChainOk(): HashEngine.ChainResult {
        val first = HashEngine.ChainReport(
            id = "demo-r1",
            generatedAt = 100L,
            integrityHash = "hash-aaaa",
            previousHash = null,
        )
        val second = HashEngine.ChainReport(
            id = "demo-r2",
            generatedAt = 200L,
            integrityHash = "hash-bbbb",
            previousHash = "hash-aaaa",
        )
        val third = HashEngine.ChainReport(
            id = "demo-r3",
            generatedAt = 300L,
            integrityHash = "hash-cccc",
            previousHash = "hash-bbbb",
        )
        return HashEngine.verifyChain(listOf(third, first, second))
    }

    fun demoReportChainBroken(): HashEngine.ChainResult {
        return HashEngine.verifyChain(
            listOf(
                HashEngine.ChainReport("bad-1", 100L, "hash-aaaa", null),
                HashEngine.ChainReport("bad-2", 200L, "hash-bbbb", previousHash = "HACKED"),
            )
        )
    }
}
