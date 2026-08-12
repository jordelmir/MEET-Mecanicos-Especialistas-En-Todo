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
    const val EXCHANGE_CHAIN_V1 = "diagnostic-exchange-chain-v1"
    const val EXCHANGE_CHAIN_V2 = "diagnostic-exchange-chain-v2"
    const val OBSERVATION_CHAIN_V1 = "diagnostic-observation-chain-v1"
    const val OBSERVATION_CHAIN_V2 = "diagnostic-observation-chain-v2"
    const val CANONICALIZATION_VERSION = EXCHANGE_CHAIN_V2

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun canonicalHash(vararg fields: String): String = sha256Hex(
        fields.joinToString(separator = "") { value -> "${value.length}:$value" }
            .toByteArray(Charsets.UTF_8),
    )

    /** v2 length-prefixes UTF-8 bytes, not UTF-16 code units. */
    fun canonicalHashV2(vararg fields: String): String = sha256Hex(
        fields.fold(ByteArray(0)) { accumulator, value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            accumulator + "${bytes.size}:".toByteArray(Charsets.US_ASCII) + bytes
        },
    )

    fun exchangeHash(exchange: DiagnosticExchangeEntity): String = when (exchange.canonicalizationVersion) {
        EXCHANGE_CHAIN_V1 -> canonicalHash(
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
        EXCHANGE_CHAIN_V2 -> canonicalHashV2(
            EXCHANGE_CHAIN_V2,
            exchange.sessionId,
            exchange.sessionSequence.toString(),
            exchange.elapsedRealtimeNanos.toString(),
            exchange.timestampMs.toString(),
            exchange.transport,
            exchange.applicationProtocol,
            exchange.requestScope,
            exchange.requestAddress.orEmpty(),
            exchange.responseAddress.orEmpty(),
            exchange.service,
            exchange.rawRequestHash,
            exchange.rawResponseHash,
            exchange.decodedOutcome,
            exchange.latencyMs?.toString().orEmpty(),
            exchange.retryCount.toString(),
            exchange.negativeResponseCode?.toString().orEmpty(),
            exchange.adapterConfiguration,
            exchange.parserVersion,
            exchange.previousExchangeHash,
            exchange.rawPayloadBlobId.orEmpty(),
        )
        else -> ""
    }

    fun observationHash(observation: DiagnosticObservationEntity): String = when (observation.canonicalizationVersion) {
        OBSERVATION_CHAIN_V1 -> canonicalHash(
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
        OBSERVATION_CHAIN_V2 -> canonicalHashV2(
            OBSERVATION_CHAIN_V2,
            observation.findingId,
            observation.findingSequence.toString(),
            observation.sessionId,
            observation.sessionSequence.toString(),
            observation.elapsedRealtimeNanos.toString(),
            observation.observedAt.toString(),
            observation.observationState,
            observation.semantics,
            observation.statusByte?.toString().orEmpty(),
            observation.sourceService,
            observation.exchangeId.orEmpty(),
            observation.rawPayloadHash,
            observation.previousObservationHash,
        )
        else -> ""
    }

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

    fun verifySession(
        expectedSessionId: String,
        exchanges: List<DiagnosticExchangeEntity>,
    ): DiagnosticIntegrityVerification {
        require(expectedSessionId.isNotBlank())
        val wrongScope = exchanges.filter { it.sessionId != expectedSessionId }
        if (wrongScope.isNotEmpty()) {
            return DiagnosticIntegrityVerification(
                valid = false,
                verifiedLeafCount = 0,
                calculatedMerkleRoot = merkleRootSha256(emptyList()),
                violations = wrongScope.map {
                    DiagnosticIntegrityViolation(it.id, it.sessionSequence, "Intercambio fuera de la sesión esperada")
                },
            )
        }
        val ordered = exchanges.sortedWith(
            compareBy<DiagnosticExchangeEntity> { it.sessionSequence }
                .thenBy { it.id },
        )
        val violations = mutableListOf<DiagnosticIntegrityViolation>()
        var priorHash = ""
        var priorSequence = 0L
        ordered.forEach { exchange ->
                if (exchange.sessionSequence <= priorSequence) {
                    violations += exchange.violation("Secuencia no monotónica")
                }
                if (exchange.previousExchangeHash != priorHash) {
                    violations += exchange.violation("Enlace hash previo inválido")
                }
                if (exchange.rawPayloadBlobId == null &&
                    (sha256Hex(exchange.rawRequest.toByteArray(Charsets.UTF_8)) != exchange.rawRequestHash ||
                        sha256Hex(exchange.rawResponse.toByteArray(Charsets.UTF_8)) != exchange.rawResponseHash)
                ) {
                    violations += exchange.violation("Hash de evidencia raw inválido")
                }
                if (exchange.canonicalizationVersion !in setOf(EXCHANGE_CHAIN_V1, EXCHANGE_CHAIN_V2)) {
                    violations += exchange.violation("Versión canónica de intercambio no soportada")
                }
                if (exchangeHash(exchange) != exchange.exchangeHash) {
                    violations += exchange.violation("Hash canónico del intercambio inválido")
                }
                priorSequence = exchange.sessionSequence
                priorHash = exchange.exchangeHash
        }
        return DiagnosticIntegrityVerification(
            valid = violations.isEmpty(),
            verifiedLeafCount = ordered.size,
            calculatedMerkleRoot = merkleRootSha256(ordered.map { it.exchangeHash }),
            violations = violations,
        )
    }

    fun verifySessions(exchanges: List<DiagnosticExchangeEntity>): Map<String, DiagnosticIntegrityVerification> =
        exchanges.groupBy(DiagnosticExchangeEntity::sessionId)
            .toSortedMap()
            .mapValues { (sessionId, scoped) -> verifySession(sessionId, scoped) }

    @Deprecated("Use verifySession or verifySessions so Merkle roots cannot cross session scope")
    fun verifyExchanges(exchanges: List<DiagnosticExchangeEntity>): DiagnosticIntegrityVerification {
        val sessions = exchanges.map { it.sessionId }.distinct()
        if (sessions.size == 1) return verifySession(sessions.single(), exchanges)
        return DiagnosticIntegrityVerification(
            valid = false,
            verifiedLeafCount = 0,
            calculatedMerkleRoot = merkleRootSha256(emptyList()),
            violations = listOf(
                DiagnosticIntegrityViolation("MULTI_SESSION_INPUT", 0, "Se requiere verificación separada por sessionId"),
            ),
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
                compareBy<DiagnosticObservationEntity> { it.findingSequence }
                    .thenBy { it.observedAt }
                    .thenBy { it.sessionSequence }
                    .thenBy { it.id },
            ).forEach { observation ->
                if (observation.canonicalizationVersion == OBSERVATION_CHAIN_V2 && observation.findingSequence <= 0L) {
                    violations += DiagnosticIntegrityViolation(
                        observation.id,
                        observation.findingSequence,
                        "Secuencia causal por finding ausente",
                    )
                }
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
