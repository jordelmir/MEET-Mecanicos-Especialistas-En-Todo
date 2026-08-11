package com.elysium369.meet.core.obd

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

/**
 * Production trust store for reviewed active-test capability packs.
 * It is intentionally empty until signed packs and their key lifecycle exist.
 * A caller-provided pack id is never self-authenticating.
 */
object ActiveDiagnosticCapabilityRegistry {
    fun authorize(test: ActiveTest): ActiveCapabilityAuthorization =
        ActiveCapabilityAuthorization(
            verified = false,
            reason = if (test.capabilityPackId.isNullOrBlank()) {
                "Falta paquete OEM revisado."
            } else {
                "El paquete ${test.capabilityPackId} no está verificado por el trust store de producción."
            },
        )
}

/**
 * Pure safety policy. The caller must supply monotonic timestamps from the same
 * clock as [TelemetrySample.timestampMonotonicMs].
 */
object ActiveDiagnosticSafetyKernel {
    const val MAX_TELEMETRY_AGE_MS = 2_000L

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

            test.safetyConditions.forEach { condition ->
                add(evaluateCondition(condition, telemetry, nowMonotonicMs))
            }
        }
        return ActiveDiagnosticSafetyDecision(
            allowed = checks.isNotEmpty() && checks.all { it.state == SafetyVerificationState.VERIFIED },
            checks = checks,
        )
    }

    private fun evaluateCondition(
        condition: SafetyCondition,
        telemetry: Map<String, TelemetrySample>,
        nowMonotonicMs: Long,
    ): SafetyCheckResult = when (condition) {
        SafetyCondition.ENGINE_OFF -> numericCheck(
            condition,
            sample(telemetry, "010C", "RPM"),
            nowMonotonicMs,
            predicate = { it <= 100.0 },
            failed = "El motor debe estar apagado.",
        )
        SafetyCondition.ENGINE_RUNNING -> numericCheck(
            condition,
            sample(telemetry, "010C", "RPM"),
            nowMonotonicMs,
            predicate = { it >= 400.0 },
            failed = "El motor debe estar encendido y estable.",
        )
        SafetyCondition.VEHICLE_STATIONARY -> numericCheck(
            condition,
            sample(telemetry, "010D", "SPEED"),
            nowMonotonicMs,
            predicate = { it <= 3.0 },
            failed = "El vehículo debe estar detenido.",
        )
        SafetyCondition.BATTERY_ABOVE_12V -> numericCheck(
            condition,
            sample(telemetry, "0142", "VOLTAGE", "ATRV", "AT RV"),
            nowMonotonicMs,
            predicate = { it >= 12.0 },
            failed = "El voltaje debe ser al menos 12.0 V.",
        )
        SafetyCondition.TRANS_IN_PARK -> SafetyCheckResult(
            condition,
            SafetyVerificationState.UNVERIFIED,
            "No hay evidencia fresca y autoritativa de que la transmisión esté en P.",
        )
    }

    private fun numericCheck(
        condition: SafetyCondition,
        sample: TelemetrySample?,
        nowMonotonicMs: Long,
        predicate: (Double) -> Boolean,
        failed: String,
    ): SafetyCheckResult {
        if (sample == null || !sample.hasRealValue) {
            return SafetyCheckResult(
                condition,
                SafetyVerificationState.UNVERIFIED,
                "${condition.name}: telemetría real no disponible.",
            )
        }
        val age = nowMonotonicMs - sample.timestampMonotonicMs
        if (age < 0L || age > MAX_TELEMETRY_AGE_MS) {
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
        return if (predicate(value)) {
            SafetyCheckResult(condition, SafetyVerificationState.VERIFIED, "${condition.name}: verificado.")
        } else {
            SafetyCheckResult(condition, SafetyVerificationState.FAILED, failed)
        }
    }

    private fun sample(
        telemetry: Map<String, TelemetrySample>,
        vararg aliases: String,
    ): TelemetrySample? = aliases.firstNotNullOfOrNull { telemetry[it] }
}
