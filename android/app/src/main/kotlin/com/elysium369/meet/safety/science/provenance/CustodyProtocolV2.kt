package com.elysium369.meet.safety.science.provenance

import java.security.MessageDigest

// ═══════════════════════════════════════════════════════════════════
// BLOQUE 2 — CANONICAL CRYPTOGRAPHIC PROTOCOL
//
// ONE protocol. ONE byte format.
// SAFETY-CUSTODY-V2 replaces both:
//   - ELYSIUM-SAFETY-CUSTODY-V1 (new scientific code)
//   - MEET-SAFETY-CUSTODY-V1 (existing evidence code)
//
// Identical computation in:
//   - Kotlin (this file)
//   - TypeScript (packages/elysium-safety-core)
//   - PostgreSQL (Supabase function)
//
// Parity fixture:
//   tests/fixtures/safety-custody-v2/
//     canonical-input.json
//     expected-event-hash.txt
//     expected-chain-root.txt
//     expected-merkle-root.txt
// ═══════════════════════════════════════════════════════════════════

object CustodyProtocolV2 {

    const val PROTOCOL_VERSION = "SAFETY-CUSTODY-V2"

    /**
     * Canonical event hash.
     *
     * Input: deterministic concatenation of fields.
     * Output: SHA-256 hex lowercase.
     *
     * Byte format:
     * ```
     * SAFETY-CUSTODY-V2\n
     * event_id:<uuid>\n
     * event_type:<type>\n
     * actor_id:<uuid>\n
     * timestamp:<iso8601_utc>\n
     * payload_hash:<sha256_hex>\n
     * previous_hash:<sha256_hex_or_GENESIS>\n
     * ```
     *
     * All fields: UTF-8, no BOM, no trailing whitespace per field.
     * UUIDs: lowercase with dashes.
     * Timestamps: ISO-8601 UTC with Z suffix, no fractional seconds.
     * Hashes: lowercase hex.
     */
    fun computeEventHash(
        eventId: String,
        eventType: String,
        actorId: String,
        timestampUtc: String,
        payloadHash: String,
        previousHash: String,
    ): String {
        val canonical = buildString {
            append("$PROTOCOL_VERSION\n")
            append("event_id:${eventId.lowercase()}\n")
            append("event_type:$eventType\n")
            append("actor_id:${actorId.lowercase()}\n")
            append("timestamp:$timestampUtc\n")
            append("payload_hash:${payloadHash.lowercase()}\n")
            append("previous_hash:${previousHash.lowercase()}\n")
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    /**
     * Chain root hash: hash of all event hashes concatenated.
     *
     * Byte format:
     * ```
     * SAFETY-CUSTODY-V2-CHAIN\n
     * count:<N>\n
     * <event_hash_1>\n
     * <event_hash_2>\n
     * ...
     * <event_hash_N>\n
     * ```
     */
    fun computeChainRoot(eventHashes: List<String>): String {
        val canonical = buildString {
            append("$PROTOCOL_VERSION-CHAIN\n")
            append("count:${eventHashes.size}\n")
            eventHashes.forEach { hash ->
                append("${hash.lowercase()}\n")
            }
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    /**
     * Payload hash: SHA-256 of canonical JSON payload bytes.
     *
     * The payload MUST be serialized with:
     * - sorted keys
     * - no trailing commas
     * - no BOM
     * - UTF-8
     * - no pretty printing (compact)
     *
     * This ensures byte-for-byte parity across runtimes.
     */
    fun computePayloadHash(payloadBytes: ByteArray): String {
        return sha256Hex(payloadBytes)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
