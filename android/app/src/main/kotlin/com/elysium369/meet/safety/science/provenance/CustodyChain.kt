package com.elysium369.meet.safety.science.provenance

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Canonicalized custody event — the atomic unit of the integrity chain.
 *
 * Canonicalization format:
 * ```
 * ELYSIUM-SAFETY-CUSTODY-V1 FS eventId FS evidenceId FS action FS actorId
 *   FS occurredAt FS previousEventHash FS contentHash
 * ```
 * where FS = U+001F (ASCII Unit Separator).
 */
data class CanonicalCustodyEvent(
    val eventId: UUID,
    val evidenceId: UUID,
    val action: String,
    val actorId: String,
    val occurredAt: Instant,
    val previousEventHash: String?,
    val contentHash: String,
    val eventHash: String,
)

// ── Canonicalization ────────────────────────────────────────────────

private const val CUSTODY_VERSION = "ELYSIUM-SAFETY-CUSTODY-V1"
private const val FIELD_SEPARATOR = "\u001F"

fun canonicalString(event: CanonicalCustodyEvent): String =
    listOf(
        CUSTODY_VERSION,
        event.eventId,
        event.evidenceId,
        event.action,
        event.actorId,
        event.occurredAt,
        event.previousEventHash ?: "",
        event.contentHash,
    ).joinToString(FIELD_SEPARATOR)

fun computeEventHash(event: CanonicalCustodyEvent): String =
    sha256(canonicalString(event))

// ── Verifier ────────────────────────────────────────────────────────

/**
 * Independent chain verifier — pure Kotlin, no Android dependencies.
 *
 * Walks the custody chain and checks:
 * 1. Each event's `previousEventHash` matches the prior event's `eventHash`.
 * 2. Each event's `eventHash` matches the re-computed canonical hash.
 *
 * A single tampered byte anywhere in the chain causes verification to fail.
 */
class CustodyVerifier {

    fun verify(events: List<CanonicalCustodyEvent>): VerificationResult {
        if (events.isEmpty()) return VerificationResult.Valid

        var previous: String? = null

        for (event in events) {
            // Check chain linkage
            if (event.previousEventHash != previous) {
                return VerificationResult.Invalid(
                    event.eventId,
                    "PREVIOUS_HASH_MISMATCH",
                )
            }

            // Check event integrity
            val expected = computeEventHash(event)
            if (expected != event.eventHash) {
                return VerificationResult.Invalid(
                    event.eventId,
                    "EVENT_HASH_MISMATCH",
                )
            }

            previous = event.eventHash
        }

        return VerificationResult.Valid
    }
}

sealed interface VerificationResult {
    data object Valid : VerificationResult

    data class Invalid(
        val failedEventId: UUID,
        val reason: String,
    ) : VerificationResult
}

// ── SHA-256 utility (shared by custody + Merkle) ────────────────────

internal fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
