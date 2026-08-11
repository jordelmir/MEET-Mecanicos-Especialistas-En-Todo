package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.data.local.entities.DiagnosticExchangeEntity
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import java.security.MessageDigest

data class DiagnosticIntegrityViolation(
    val entityId: String,
    val sequence: Long,
    val reason: String,
)

data class DiagnosticIntegrityVerification(
    val valid: Boolean,
    val verifiedLeafCount: Int,
    val calculatedMerkleRoot: String,
    val violations: List<DiagnosticIntegrityViolation>,
)

/** Byte-stable authority used by persistence, replay, export and conformance checks. */
object DiagnosticEvidenceIntegrity {
    const val CANONICALIZATION_VERSION = "diagnostic-exchange-chain-v1"

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun canonicalHash(vararg fields: String): String = sha256Hex(
        fields.joinToString(separator = "") { value -> "${value.length}:$value" }
            .toByteArray(Charsets.UTF_8),
    )

    fun exchangeHash(exchange: DiagnosticExchangeEntity): String = canonicalHash(
        exchange.sessionId,
        exchange.sessionSequence.toString(),
        exchange.timestampMs.toString(),
        exchange.requestScope,
        exchange.requestAddress.orEmpty(),
        exchange.responseAddress.orEmpty(),
        exchange.service,
        exchange.decodedOutcome,
        exchange.rawRequestHash,
        exchange.rawResponseHash,
        exchange.previousExchangeHash,
        exchange.parserVersion,
    )

    fun observationHash(observation: DiagnosticObservationEntity): String = canonicalHash(
        observation.findingId,
        observation.sessionId,
        observation.sessionSequence.toString(),
        observation.observedAt.toString(),
        observation.observationState,
        observation.semantics,
        observation.statusByte?.toString().orEmpty(),
        observation.sourceService,
        observation.exchangeId.orEmpty(),
        observation.rawPayloadHash,
        observation.previousObservationHash,
    )

    fun merkleRootSha256(hashes: List<String>): String {
        if (hashes.isEmpty()) return sha256Hex(ByteArray(0))
        var level = hashes.map(String::lowercase)
        while (level.size > 1) {
            level = level.chunked(2).map { pair ->
                canonicalHash(pair[0], pair.getOrElse(1) { pair[0] })
            }
        }
        return level.single()
    }

    fun verifyExchanges(exchanges: List<DiagnosticExchangeEntity>): DiagnosticIntegrityVerification {
        val ordered = exchanges.sortedWith(
            compareBy<DiagnosticExchangeEntity> { it.sessionId }
                .thenBy { it.sessionSequence }
                .thenBy { it.id },
        )
        val violations = mutableListOf<DiagnosticIntegrityViolation>()
        ordered.groupBy { it.sessionId }.values.forEach { sessionExchanges ->
            var priorHash = ""
            var priorSequence = 0L
            sessionExchanges.forEach { exchange ->
                if (exchange.sessionSequence <= priorSequence) {
                    violations += exchange.violation("Secuencia no monotónica")
                }
                if (exchange.previousExchangeHash != priorHash) {
                    violations += exchange.violation("Enlace hash previo inválido")
                }
                if (sha256Hex(exchange.rawRequest.toByteArray(Charsets.UTF_8)) != exchange.rawRequestHash ||
                    sha256Hex(exchange.rawResponse.toByteArray(Charsets.UTF_8)) != exchange.rawResponseHash
                ) {
                    violations += exchange.violation("Hash de evidencia raw inválido")
                }
                if (exchangeHash(exchange) != exchange.exchangeHash) {
                    violations += exchange.violation("Hash canónico del intercambio inválido")
                }
                priorSequence = exchange.sessionSequence
                priorHash = exchange.exchangeHash
            }
        }
        return DiagnosticIntegrityVerification(
            valid = violations.isEmpty(),
            verifiedLeafCount = ordered.size,
            calculatedMerkleRoot = merkleRootSha256(ordered.map { it.exchangeHash }),
            violations = violations,
        )
    }

    fun verifyObservations(observations: List<DiagnosticObservationEntity>): DiagnosticIntegrityVerification {
        val ordered = observations.sortedWith(
            compareBy<DiagnosticObservationEntity> { it.sessionId }
                .thenBy { it.sessionSequence }
                .thenBy { it.observedAt }
                .thenBy { it.id },
        )
        val violations = mutableListOf<DiagnosticIntegrityViolation>()
        ordered.groupBy { it.sessionId }.values.forEach { sessionObservations ->
            var priorSequence = 0L
            sessionObservations.forEach { observation ->
                if (observation.sessionSequence <= priorSequence) {
                    violations += DiagnosticIntegrityViolation(
                        observation.id,
                        observation.sessionSequence,
                        "Secuencia de observación no monotónica",
                    )
                }
                priorSequence = observation.sessionSequence
            }
        }
        ordered.groupBy { it.findingId }.values.forEach { findingObservations ->
            var priorHash = ""
            findingObservations.sortedWith(
                compareBy<DiagnosticObservationEntity> { it.observedAt }
                    .thenBy { it.sessionSequence }
                    .thenBy { it.id },
            ).forEach { observation ->
                if (observation.previousObservationHash != priorHash) {
                    violations += DiagnosticIntegrityViolation(
                        observation.id,
                        observation.sessionSequence,
                        "Enlace hash previo de observación inválido",
                    )
                }
                if (observationHash(observation) != observation.observationHash) {
                    violations += DiagnosticIntegrityViolation(
                        observation.id,
                        observation.sessionSequence,
                        "Hash canónico de observación inválido",
                    )
                }
                priorHash = observation.observationHash
            }
        }
        return DiagnosticIntegrityVerification(
            valid = violations.isEmpty(),
            verifiedLeafCount = ordered.size,
            calculatedMerkleRoot = merkleRootSha256(ordered.map { it.observationHash }),
            violations = violations,
        )
    }

    private fun DiagnosticExchangeEntity.violation(reason: String) =
        DiagnosticIntegrityViolation(id, sessionSequence, reason)

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
