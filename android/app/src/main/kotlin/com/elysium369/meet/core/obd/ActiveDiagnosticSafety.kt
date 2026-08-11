package com.elysium369.meet.core.obd

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Fail-closed lifecycle for commands that can actuate vehicle hardware.
 * UNKNOWN is never equivalent to safe.
 */
enum class ActiveDiagnosticTestPhase {
    IDLE,
    PRECHECK,
    READY,
    ACTIVATION_REQUESTED,
    ACTIVE,
    STOP_REQUESTED,
    STOP_VERIFIED,
    STOP_FAILED,
    ABORTED,
}

enum class SafetyVerificationState {
    VERIFIED,
    UNVERIFIED,
    FAILED,
}

data class SafetyCheckResult(
    val condition: SafetyCondition,
    val state: SafetyVerificationState,
    val reason: String,
)

data class ActiveDiagnosticSafetyDecision(
    val allowed: Boolean,
    val checks: List<SafetyCheckResult>,
) {
    val blockingReasons: List<String>
        get() = checks.filter { it.state != SafetyVerificationState.VERIFIED }.map { it.reason }
}

data class ActiveCapabilityAuthorization(
    val verified: Boolean,
    val reason: String,
)

data class DiagnosticCapabilityContext(
    val manufacturer: String?,
    val modelFamily: String?,
    val year: Int?,
    val market: String?,
    val ecuFamily: String?,
    val ecuAddress: String?,
    val hardwareVersion: String?,
    val softwareVersion: String?,
    val calibrationId: String?,
) {
    companion object {
        val UNKNOWN = DiagnosticCapabilityContext(null, null, null, null, null, null, null, null, null)
    }
}

data class DiagnosticCapabilityApplicability(
    val manufacturer: String,
    val modelFamily: String?,
    val yearFrom: Int?,
    val yearTo: Int?,
    val market: String?,
    val ecuFamily: String,
    val ecuAddress: String,
    val hardwareVersion: String?,
    val softwareVersion: String?,
    val calibrationId: String,
)

data class DiagnosticCapabilityOperation(
    val operationId: String,
    val service: String,
    val requestTemplate: String,
    val positiveResponseService: String,
    val negativeResponsePolicy: String,
    val sessionRequirements: Set<String>,
    val securityRequirements: Set<String>,
    val safetyRequirements: Set<SafetyCondition>,
    val maximumDurationMs: Long,
    val stopRequest: String,
    val stopPositiveResponseService: String,
    val postconditions: Set<String>,
    val sourceAuthority: String,
)

data class SignedDiagnosticCapabilityPack(
    val packId: String,
    val schemaVersion: Int,
    val issuer: String,
    val keyId: String,
    val issuedAt: Long,
    val expiresAt: Long?,
    val revocationVersion: Long,
    val contentHash: String,
    val signature: String,
    val applicability: DiagnosticCapabilityApplicability,
    val operations: List<DiagnosticCapabilityOperation>,
) {
    init {
        require(packId.isNotBlank() && issuer.isNotBlank() && keyId.isNotBlank())
        require(schemaVersion > 0 && issuedAt > 0 && revocationVersion >= 0)
        require(contentHash.matches(Regex("^[0-9a-fA-F]{64}$")))
        require(signature.isNotBlank() && operations.isNotEmpty())
        val canonicalFields = listOf(
            packId, issuer, keyId, applicability.manufacturer, applicability.ecuFamily,
            applicability.ecuAddress, applicability.calibrationId,
        ) + operations.flatMap {
            listOf(it.operationId, it.service, it.requestTemplate, it.stopRequest, it.sourceAuthority)
        }
        require(canonicalFields.none { '|' in it || ';' in it }) {
            "Capability pack contiene delimitadores ambiguos en contenido firmado."
        }
        require(operations.map { it.operationId }.distinct().size == operations.size)
        require(operations.all {
            it.operationId.isNotBlank() && it.maximumDurationMs in 1..300_000 &&
                it.stopRequest.isNotBlank() && it.safetyRequirements.isNotEmpty() &&
                it.postconditions.isNotEmpty() && it.sourceAuthority.isNotBlank()
        })
    }

    fun canonicalContent(): String = listOf(
        packId, schemaVersion, issuer, keyId, issuedAt, expiresAt ?: "", revocationVersion,
        applicability.manufacturer, applicability.modelFamily ?: "", applicability.yearFrom ?: "",
        applicability.yearTo ?: "", applicability.market ?: "", applicability.ecuFamily,
        applicability.ecuAddress, applicability.hardwareVersion ?: "", applicability.softwareVersion ?: "",
        applicability.calibrationId,
        operations.sortedBy { it.operationId }.joinToString(";") { operation ->
            listOf(
                operation.operationId, operation.service, operation.requestTemplate,
                operation.positiveResponseService, operation.negativeResponsePolicy,
                operation.sessionRequirements.sorted().joinToString(","),
                operation.securityRequirements.sorted().joinToString(","),
                operation.safetyRequirements.map { it.name }.sorted().joinToString(","),
                operation.maximumDurationMs, operation.stopRequest,
                operation.stopPositiveResponseService, operation.postconditions.sorted().joinToString(","),
                operation.sourceAuthority,
            ).joinToString("|")
        },
    ).joinToString("|")
}

/**
 * Production trust store for reviewed active-test capability packs.
 * It is intentionally empty until signed packs and their key lifecycle exist.
 * A caller-provided pack id is never self-authenticating.
 */
object ActiveDiagnosticCapabilityRegistry {
    private val packs = ConcurrentHashMap<String, SignedDiagnosticCapabilityPack>()
    private val trustedKeys = ConcurrentHashMap<String, ByteArray>()
    private val revokedPacks = ConcurrentHashMap.newKeySet<String>()

    fun loadTrustSnapshot(
        publicKeysById: Map<String, ByteArray>,
        candidatePacks: Collection<SignedDiagnosticCapabilityPack>,
        revokedPackIds: Set<String>,
    ) {
        trustedKeys.clear()
        trustedKeys.putAll(publicKeysById.mapValues { it.value.copyOf() })
        revokedPacks.clear()
        revokedPacks.addAll(revokedPackIds)
        packs.clear()
        candidatePacks.forEach { pack -> if (verifyPack(pack).verified) packs[pack.packId] = pack }
    }

    fun authorize(
        test: ActiveTest,
        context: DiagnosticCapabilityContext = DiagnosticCapabilityContext.UNKNOWN,
        nowMs: Long = System.currentTimeMillis(),
    ): ActiveCapabilityAuthorization {
        val packId = test.capabilityPackId
            ?: return denied("Falta paquete OEM firmado y revisado.")
        if (packId in revokedPacks) return denied("El paquete $packId fue revocado.")
        val pack = packs[packId] ?: return denied("El paquete $packId no está verificado por el trust store.")
        if (pack.expiresAt?.let { nowMs >= it } == true) return denied("El paquete $packId está vencido.")
        verifyPack(pack).takeIf { !it.verified }?.let { return it }
        val operation = pack.operations.singleOrNull { it.operationId == test.id }
            ?: return denied("La operación ${test.id} no pertenece al paquete firmado.")
        if (!matches(pack.applicability, context)) return denied("Aplicabilidad de vehículo/ECU/calibración no confirmada.")
        if (!operation.requestTemplate.equals(test.startCommand, true) ||
            !operation.stopRequest.equals(test.stopCommand, true) ||
            !pack.applicability.ecuAddress.equals(test.targetAddress, true)
        ) return denied("El comando o destino no coincide con el contenido firmado.")
        if (test.durationMs > operation.maximumDurationMs) return denied("Duración superior al máximo firmado.")
        if (!test.safetyConditions.containsAll(operation.safetyRequirements)) {
            return denied("La prueba omite precondiciones de seguridad firmadas.")
        }
        if (test.safetyEvidenceRequirements.map { it.condition }.toSet() != operation.safetyRequirements) {
            return denied("La prueba no aporta requisitos de evidencia para cada precondición firmada.")
        }
        return ActiveCapabilityAuthorization(true, "Paquete y aplicabilidad verificados.")
    }

    private fun verifyPack(pack: SignedDiagnosticCapabilityPack): ActiveCapabilityAuthorization {
        val publicKeyBytes = trustedKeys[pack.keyId] ?: return denied("Key ID ${pack.keyId} no confiable.")
        val canonical = pack.canonicalContent().toByteArray(Charsets.UTF_8)
        val actualHash = MessageDigest.getInstance("SHA-256").digest(canonical).toHex()
        if (!actualHash.equals(pack.contentHash, true)) return denied("Content hash del paquete no coincide.")
        val verified = runCatching {
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyBytes))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(pack.contentHash.lowercase().toByteArray(Charsets.US_ASCII))
                verify(Base64.getDecoder().decode(pack.signature))
            }
        }.getOrDefault(false)
        return if (verified) ActiveCapabilityAuthorization(true, "Firma ECDSA verificada.")
        else denied("Firma del paquete inválida.")
    }

    private fun matches(
        expected: DiagnosticCapabilityApplicability,
        actual: DiagnosticCapabilityContext,
    ): Boolean =
        actual.manufacturer?.equals(expected.manufacturer, true) == true &&
            actual.ecuFamily?.equals(expected.ecuFamily, true) == true &&
            actual.ecuAddress?.equals(expected.ecuAddress, true) == true &&
            actual.calibrationId?.equals(expected.calibrationId, true) == true &&
            (expected.modelFamily == null || actual.modelFamily?.equals(expected.modelFamily, true) == true) &&
            (expected.market == null || actual.market?.equals(expected.market, true) == true) &&
            (expected.hardwareVersion == null || actual.hardwareVersion == expected.hardwareVersion) &&
            (expected.softwareVersion == null || actual.softwareVersion == expected.softwareVersion) &&
            actual.year?.let { year ->
                (expected.yearFrom == null || year >= expected.yearFrom) &&
                    (expected.yearTo == null || year <= expected.yearTo)
            } == true

    private fun denied(reason: String) = ActiveCapabilityAuthorization(false, reason)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/**
 * Pure safety policy. The caller must supply monotonic timestamps from the same
 * clock as [TelemetrySample.timestampMonotonicMs].
 */
object ActiveDiagnosticSafetyKernel {
    fun evaluate(
        test: ActiveTest,
        telemetry: Map<String, TelemetrySample>,
        nowMonotonicMs: Long,
        capabilityAuthorization: ActiveCapabilityAuthorization =
            ActiveDiagnosticCapabilityRegistry.authorize(test),
    ): ActiveDiagnosticSafetyDecision {
        val checks = buildList {
            if (!capabilityAuthorization.verified || test.targetAddress.isNullOrBlank()) {
                add(
                    SafetyCheckResult(
                        SafetyCondition.VEHICLE_STATIONARY,
                        SafetyVerificationState.UNVERIFIED,
                        "Prueba bloqueada: ${capabilityAuthorization.reason} Dirección ECU verificada requerida.",
                    ),
                )
            }

            if (test.safetyEvidenceRequirements.isEmpty()) {
                add(
                    SafetyCheckResult(
                        SafetyCondition.VEHICLE_STATIONARY,
                        SafetyVerificationState.UNVERIFIED,
                        "Prueba bloqueada: el capability pack no definió frescura/calidad/fuente por señal.",
                    ),
                )
            }
            test.safetyEvidenceRequirements.forEach { requirement ->
                add(evaluateRequirement(requirement, telemetry, nowMonotonicMs))
            }
        }
        return ActiveDiagnosticSafetyDecision(
            allowed = checks.isNotEmpty() && checks.all { it.state == SafetyVerificationState.VERIFIED },
            checks = checks,
        )
    }

    private fun evaluateRequirement(
        requirement: SafetyEvidenceRequirement,
        telemetry: Map<String, TelemetrySample>,
        nowMonotonicMs: Long,
    ): SafetyCheckResult {
        val condition = requirement.condition
        val sample = sample(telemetry, *requirement.signalAliases.toTypedArray())
        if (sample == null || !sample.hasRealValue) {
            return SafetyCheckResult(
                condition,
                SafetyVerificationState.UNVERIFIED,
                "${condition.name}: telemetría real no disponible.",
            )
        }
        val age = nowMonotonicMs - sample.timestampMonotonicMs
        if (age < 0L || age > requirement.maxAgeMs) {
            return SafetyCheckResult(
                condition,
                SafetyVerificationState.UNVERIFIED,
                "${condition.name}: telemetría vencida (${age.coerceAtLeast(0L)} ms).",
            )
        }
        val value = sample.value ?: return SafetyCheckResult(
            condition,
            SafetyVerificationState.UNVERIFIED,
            "${condition.name}: lectura sin valor.",
        )
        if (sample.quality !in requirement.acceptedQualities || sample.source !in requirement.acceptedSources) {
            return SafetyCheckResult(
                condition,
                SafetyVerificationState.UNVERIFIED,
                "${condition.name}: calidad o fuente no autorizada por el capability pack.",
            )
        }
        val threshold = requirement.threshold
        val passed = when (requirement.predicate) {
            SafetySignalPredicate.ENGINE_STOPPED -> value <= (threshold ?: 100.0)
            SafetySignalPredicate.ENGINE_RUNNING -> value >= (threshold ?: 400.0)
            SafetySignalPredicate.VEHICLE_STATIONARY -> value <= (threshold ?: 0.0)
            SafetySignalPredicate.BATTERY_MINIMUM -> threshold != null && value >= threshold
            SafetySignalPredicate.TRANSMISSION_IN_PARK -> threshold != null && value == threshold
        }
        return if (passed) {
            SafetyCheckResult(condition, SafetyVerificationState.VERIFIED, "${condition.name}: verificado.")
        } else {
            SafetyCheckResult(condition, SafetyVerificationState.FAILED, "${condition.name}: precondición no satisfecha.")
        }
    }

    private fun sample(
        telemetry: Map<String, TelemetrySample>,
        vararg aliases: String,
    ): TelemetrySample? = aliases.firstNotNullOfOrNull { telemetry[it] }
}
