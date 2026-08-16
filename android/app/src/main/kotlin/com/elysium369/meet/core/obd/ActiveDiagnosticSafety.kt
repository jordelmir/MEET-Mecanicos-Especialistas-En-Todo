package com.elysium369.meet.core.obd

import android.content.Context
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.math.BigDecimal
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
        require(operations.map { it.operationId }.distinct().size == operations.size)
        require(operations.all {
            it.operationId.isNotBlank() && it.maximumDurationMs in 1..300_000 &&
                it.stopRequest.isNotBlank() && it.safetyRequirements.isNotEmpty() &&
                it.postconditions.isNotEmpty() && it.sourceAuthority.isNotBlank()
        })
    }

    fun canonicalBytes(): ByteArray = CanonicalJson.encode(
        mapOf(
            "applicability" to mapOf(
                "calibrationId" to applicability.calibrationId,
                "ecuAddress" to applicability.ecuAddress,
                "ecuFamily" to applicability.ecuFamily,
                "hardwareVersion" to applicability.hardwareVersion,
                "manufacturer" to applicability.manufacturer,
                "market" to applicability.market,
                "modelFamily" to applicability.modelFamily,
                "softwareVersion" to applicability.softwareVersion,
                "yearFrom" to applicability.yearFrom,
                "yearTo" to applicability.yearTo,
            ),
            "expiresAt" to expiresAt,
            "issuedAt" to issuedAt,
            "issuer" to issuer,
            "keyId" to keyId,
            "operations" to operations.sortedBy { it.operationId }.map { operation ->
                mapOf(
                    "maximumDurationMs" to operation.maximumDurationMs,
                    "negativeResponsePolicy" to operation.negativeResponsePolicy,
                    "operationId" to operation.operationId,
                    "positiveResponseService" to operation.positiveResponseService,
                    "postconditions" to operation.postconditions.sorted(),
                    "requestTemplate" to operation.requestTemplate,
                    "safetyRequirements" to operation.safetyRequirements.map { it.name }.sorted(),
                    "securityRequirements" to operation.securityRequirements.sorted(),
                    "service" to operation.service,
                    "sessionRequirements" to operation.sessionRequirements.sorted(),
                    "sourceAuthority" to operation.sourceAuthority,
                    "stopPositiveResponseService" to operation.stopPositiveResponseService,
                    "stopRequest" to operation.stopRequest,
                )
            },
            "packId" to packId,
            "revocationVersion" to revocationVersion,
            "schemaVersion" to schemaVersion,
        ),
    )
}

data class CapabilityTrustManifest(
    val trustVersion: Long,
    val issuedAt: Long,
    val expiresAt: Long,
    val trustedIssuerKeysBase64: Map<String, String>,
    val revokedIssuerKeys: Set<String>,
    val revokedPackIds: Set<String>,
    val minimumPackSchema: Int,
    val minimumAppVersion: String,
    val rootKeyId: String,
    val signatureBase64: String,
) {
    fun canonicalBytes(): ByteArray = CanonicalJson.encode(
        mapOf(
            "expiresAt" to expiresAt,
            "issuedAt" to issuedAt,
            "minimumAppVersion" to minimumAppVersion,
            "minimumPackSchema" to minimumPackSchema,
            "revokedIssuerKeys" to revokedIssuerKeys.sorted(),
            "revokedPackIds" to revokedPackIds.sorted(),
            "rootKeyId" to rootKeyId,
            "trustVersion" to trustVersion,
            "trustedIssuerKeysBase64" to trustedIssuerKeysBase64.toSortedMap(),
        ),
    )
}

interface CapabilityTrustStateStore {
    fun highestAcceptedTrustVersion(): Long
    fun persistHighestAcceptedTrustVersion(version: Long)
}

internal class AndroidCapabilityTrustStateStore(context: Context) : CapabilityTrustStateStore {
    private val preferences = context.getSharedPreferences("meet_capability_trust", Context.MODE_PRIVATE)
    override fun highestAcceptedTrustVersion(): Long = preferences.getLong("highestAcceptedTrustVersion", 0L)
    override fun persistHighestAcceptedTrustVersion(version: Long) {
        check(preferences.edit().putLong("highestAcceptedTrustVersion", version).commit())
    }
}

/** Versioned JCS-compatible restricted profile: sorted keys, finite numbers, strings, arrays and null. */
internal object CanonicalJson {
    fun encode(value: Any?): ByteArray = render(value).toByteArray(Charsets.UTF_8)

    private fun render(value: Any?): String = when (value) {
        null -> "null"
        is String -> value.asJsonString()
        is Double -> value.canonicalFiniteNumber()
        is Float -> value.toDouble().canonicalFiniteNumber()
        is Byte, is Short, is Int, is Long -> value.toString()
        is Boolean -> value.toString()
        is Map<*, *> -> value.entries
            .map { (key, item) -> require(key is String); key to item }
            .sortedBy { it.first }
            .joinToString(prefix = "{", postfix = "}") { (key, item) ->
                "${key.asJsonString()}:${render(item)}"
            }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { render(it) }
        else -> error("Unsupported canonical JSON type: ${value::class.java.name}")
    }

    private fun String.asJsonString(): String = buildString(length + 2) {
        append('"')
        this@asJsonString.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private fun Double.canonicalFiniteNumber(): String {
        require(isFinite()) { "Canonical JSON rejects non-finite numbers" }
        if (this == 0.0) return "0"
        return BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
    }
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
    @Volatile private var minimumPackSchema = Int.MAX_VALUE

    internal fun installTrustedManifest(
        manifest: CapabilityTrustManifest,
        offlineRootPublicKey: ByteArray,
        stateStore: CapabilityTrustStateStore,
        candidatePacks: Collection<SignedDiagnosticCapabilityPack>,
        appVersion: String,
        nowMs: Long = System.currentTimeMillis(),
    ): ActiveCapabilityAuthorization {
        if (manifest.trustVersion <= stateStore.highestAcceptedTrustVersion()) {
            return denied("Rollback/replay de trust manifest rechazado.")
        }
        if (nowMs !in manifest.issuedAt until manifest.expiresAt) return denied("Trust manifest fuera de vigencia.")
        if (compareVersions(appVersion, manifest.minimumAppVersion) < 0) return denied("Versión de MEET inferior al mínimo del trust manifest.")
        if (MessageDigest.getInstance("SHA-256").digest(offlineRootPublicKey).toHex() != manifest.rootKeyId.lowercase()) {
            return denied("La root pública no coincide con rootKeyId.")
        }
        val trustDigest = domainSeparatedHash("MEET-CAPABILITY-TRUST-V1", manifest.canonicalBytes())
        if (!verifyEcdsa(offlineRootPublicKey, trustDigest, manifest.signatureBase64)) {
            return denied("Firma de Capability Trust Root inválida.")
        }
        val decodedKeys = runCatching {
            manifest.trustedIssuerKeysBase64
                .filterKeys { it !in manifest.revokedIssuerKeys }
                .mapValues { Base64.getDecoder().decode(it.value) }
        }.getOrElse { return denied("Trust manifest contiene una clave inválida.") }
        trustedKeys.clear()
        trustedKeys.putAll(decodedKeys.mapValues { it.value.copyOf() })
        revokedPacks.clear()
        revokedPacks.addAll(manifest.revokedPackIds)
        minimumPackSchema = manifest.minimumPackSchema
        packs.clear()
        candidatePacks.forEach { pack -> if (verifyPack(pack).verified) packs[pack.packId] = pack }
        stateStore.persistHighestAcceptedTrustVersion(manifest.trustVersion)
        return ActiveCapabilityAuthorization(true, "Capability Trust Root verificado e instalado.")
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
        if (pack.schemaVersion < minimumPackSchema) return denied("Schema de capability pack obsoleto.")
        val publicKeyBytes = trustedKeys[pack.keyId] ?: return denied("Key ID ${pack.keyId} no confiable.")
        val digest = domainSeparatedHash("MEET-CAPABILITY-PACK-V2", pack.canonicalBytes())
        val actualHash = digest.toHex()
        if (!actualHash.equals(pack.contentHash, true)) return denied("Content hash del paquete no coincide.")
        val verified = verifyEcdsa(publicKeyBytes, digest, pack.signature)
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

    private fun compareVersions(left: String, right: String): Int {
        val lhs = left.split('.').map { it.toIntOrNull() ?: 0 }
        val rhs = right.split('.').map { it.toIntOrNull() ?: 0 }
        return (0 until maxOf(lhs.size, rhs.size))
            .firstNotNullOfOrNull { index ->
                (lhs.getOrElse(index) { 0 } - rhs.getOrElse(index) { 0 }).takeIf { it != 0 }
            } ?: 0
    }

    private fun domainSeparatedHash(domain: String, canonical: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(
            domain.toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + canonical,
        )

    private fun verifyEcdsa(publicKeyBytes: ByteArray, digest: ByteArray, signatureBase64: String): Boolean =
        runCatching {
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyBytes))
            Signature.getInstance("NONEwithECDSA").run {
                initVerify(publicKey)
                update(digest)
                verify(Base64.getDecoder().decode(signatureBase64))
            }
        }.getOrDefault(false)

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
