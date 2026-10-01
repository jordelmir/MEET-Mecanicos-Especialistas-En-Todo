package io.elysium.safety

import java.security.MessageDigest
import java.time.Instant

/** Reusable, platform-free exact custody wire format shared with SQL and TS. */
data class CustodyEventV2(
    val eventId: String,
    val evidenceId: String,
    val previousHash: String?,
    val eventType: String,
    val actor: String,
    val reasonCode: String,
    val serverSha256: String?,
    val occurredAtUtc: String,
) {
    fun canonical(): String {
        val fields = listOf("MEET-SAFETY-CUSTODY-V2", eventId, evidenceId,
            previousHash.orEmpty(), eventType, actor, reasonCode, serverSha256.orEmpty(), occurredAtUtc)
        require(fields.none { it.any { c -> c == '\u001f' || c == '\r' || c == '\n' } }) { "AMBIGUOUS_CUSTODY_FIELD" }
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        require(uuid.matches(eventId) && uuid.matches(evidenceId)) { "INVALID_CUSTODY_ID" }
        require(listOfNotNull(previousHash, serverSha256).all { Regex("[a-f0-9]{64}").matches(it) }) { "INVALID_CUSTODY_HASH" }
        require(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z").matches(occurredAtUtc)) { "INVALID_CUSTODY_TIME" }
        Instant.parse(occurredAtUtc)
        return fields.joinToString("\u001f")
    }

    fun sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(canonical().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
