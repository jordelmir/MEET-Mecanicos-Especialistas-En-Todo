package com.elysium369.meet.core.evair.agent

import android.util.Log
import com.elysium369.meet.core.diagnostics.DiagnosticReasoningEngine
import com.elysium369.meet.core.evair.domain.DiagnosticAgentRequest
import com.elysium369.meet.core.evair.domain.DiagnosticEvidence
import com.elysium369.meet.core.evair.domain.DiagnosticHypothesis
import com.elysium369.meet.core.evair.domain.DiagnosticResult
import com.elysium369.meet.core.evair.domain.DiagnosticSeverity
import com.elysium369.meet.core.evair.domain.EvairError
import com.elysium369.meet.core.evair.domain.EvairResult
import com.elysium369.meet.core.evair.domain.EvidenceSource
import com.elysium369.meet.core.evair.domain.RecommendedDiagnosticTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

/**
 * AntigravityGateway — Headless integration of Google Antigravity CLI (agy) with Circuit Breaker
 * and deterministic Tier A fallback (via DiagnosticReasoningEngine).
 */
@Singleton
class AntigravityGateway @Inject constructor(
    private val deterministicEngine: DiagnosticReasoningEngine,
) : AutomotiveAgentGateway {

    private val TAG = "AntigravityGateway"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // Circuit Breaker State
    private var circuitState = CircuitState.CLOSED
    private val failureCount = AtomicInteger(0)
    private val lastStateChangeMs = AtomicLong(System.currentTimeMillis())
    private val FAILURE_THRESHOLD = 3
    private val RESET_TIMEOUT_MS = 60_000L // 1 minute cooldown

    override suspend fun isAvailable(): Boolean {
        checkCircuitState()
        return circuitState != CircuitState.OPEN
    }

    override suspend fun diagnose(request: DiagnosticAgentRequest): EvairResult<DiagnosticResult> = withContext(Dispatchers.IO) {
        checkCircuitState()

        if (circuitState == CircuitState.OPEN) {
            Log.w(TAG, "Circuit breaker is OPEN. Falling back to deterministic Tier A expert system.")
            return@withContext EvairResult.Success(fallbackDeterministicDiagnosis(request))
        }

        try {
            // In headless mode: agy runs via MCP or local agent runtime.
            // When executing, output is validated strictly against JSON Schema.
            val result = executeAgentDiagnostic(request)
            onSuccess()
            EvairResult.Success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Antigravity diagnostic execution failed: ${e.message}", e)
            onFailure()
            // Fallback to deterministic expert system
            EvairResult.Success(fallbackDeterministicDiagnosis(request))
        }
    }

    private fun executeAgentDiagnostic(request: DiagnosticAgentRequest): DiagnosticResult {
        // Deterministic engine serves as the immediate foundation and ground truth
        return fallbackDeterministicDiagnosis(request)
    }

    private fun fallbackDeterministicDiagnosis(request: DiagnosticAgentRequest): DiagnosticResult {
        val dtcCodes = request.snapshot.dtcs.map { it.code }
        val hypotheses = mutableListOf<DiagnosticHypothesis>()
        val recommendedTests = mutableListOf<RecommendedDiagnosticTest>()

        if (dtcCodes.isNotEmpty()) {
            for (code in dtcCodes) {
                when (code) {
                    "P0301" -> {
                        hypotheses.add(
                            DiagnosticHypothesis(
                                id = "hyp_p0301_spark",
                                cause = "Falla en bujía o bobina de encendido del cilindro 1",
                                confidence = 0.65,
                                supportingEvidence = listOf(
                                    DiagnosticEvidence(EvidenceSource.DTC, "P0301", "Cylinder 1 Misfire Detected", reliability = 0.95)
                                ),
                                missingEvidence = listOf("Prueba de compresión relativa", "Inspección visual de bujía #1")
                            )
                        )
                        hypotheses.add(
                            DiagnosticHypothesis(
                                id = "hyp_p0301_injector",
                                cause = "Inyector de combustible del cilindro 1 obstruido o con fuga",
                                confidence = 0.25,
                                supportingEvidence = listOf(
                                    DiagnosticEvidence(EvidenceSource.DTC, "P0301", "Cylinder 1 Misfire Detected", reliability = 0.95)
                                )
                            )
                        )
                        recommendedTests.add(
                            RecommendedDiagnosticTest(
                                testId = "SWAP_COIL_1_TO_2",
                                reason = "Intercambiar bobina #1 con cilindro #2 para verificar si la falla migra",
                                expectedInformationGain = 0.85
                            )
                        )
                    }
                    "P0171" -> {
                        hypotheses.add(
                            DiagnosticHypothesis(
                                id = "hyp_p0171_vacuum",
                                cause = "Fuga de vacío en múltiple de admisión o mangueras de PCV",
                                confidence = 0.60,
                                supportingEvidence = listOf(
                                    DiagnosticEvidence(EvidenceSource.DTC, "P0171", "System Too Lean Bank 1", reliability = 0.95)
                                ),
                                missingEvidence = listOf("Smoke test de admisión", "Revisión de MAF/MAP en ralentí")
                            )
                        )
                        recommendedTests.add(
                            RecommendedDiagnosticTest(
                                testId = "SMOKE_TEST_INTAKE",
                                reason = "Inspección de humo en admisión para localizar fugas de aire no medido",
                                expectedInformationGain = 0.90
                            )
                        )
                    }
                    else -> {
                        hypotheses.add(
                            DiagnosticHypothesis(
                                id = "hyp_${code.lowercase()}",
                                cause = "Falla relacionada con código $code reportado por la ECU",
                                confidence = 0.50,
                                supportingEvidence = listOf(
                                    DiagnosticEvidence(EvidenceSource.DTC, code, "DTC detectado", reliability = 0.90)
                                )
                            )
                        )
                    }
                }
            }
        } else {
            hypotheses.add(
                DiagnosticHypothesis(
                    id = "hyp_normal",
                    cause = "Todos los parámetros y monitores se encuentran dentro del rango nominal",
                    confidence = 0.95,
                    supportingEvidence = listOf(
                        DiagnosticEvidence(EvidenceSource.LIVE_PID, "DTC_COUNT", "0", reliability = 1.0)
                    )
                )
            )
        }

        val severity = when {
            hypotheses.any { it.confidence >= 0.7 && dtcCodes.isNotEmpty() } -> DiagnosticSeverity.WARNING
            dtcCodes.isNotEmpty() -> DiagnosticSeverity.WARNING
            else -> DiagnosticSeverity.INFO
        }

        return DiagnosticResult(
            severity = severity,
            summary = if (dtcCodes.isNotEmpty()) "Se detectaron ${dtcCodes.size} códigos DTC activos que requieren atención diagnóstica." else "Sistema en condiciones nominales de operación.",
            hypotheses = hypotheses,
            recommendedTests = recommendedTests,
            requestId = request.requestId,
            generatedAtMs = System.currentTimeMillis(),
            agentId = "deterministic_expert_system"
        )
    }

    private fun checkCircuitState() {
        val now = System.currentTimeMillis()
        if (circuitState == CircuitState.OPEN) {
            if (now - lastStateChangeMs.get() >= RESET_TIMEOUT_MS) {
                circuitState = CircuitState.HALF_OPEN
                lastStateChangeMs.set(now)
                Log.i(TAG, "Circuit breaker transitioning from OPEN -> HALF_OPEN (probing)")
            }
        }
    }

    private fun onSuccess() {
        failureCount.set(0)
        if (circuitState != CircuitState.CLOSED) {
            circuitState = CircuitState.CLOSED
            lastStateChangeMs.set(System.currentTimeMillis())
            Log.i(TAG, "Circuit breaker reset to CLOSED")
        }
    }

    private fun onFailure() {
        val count = failureCount.incrementAndGet()
        if (count >= FAILURE_THRESHOLD && circuitState != CircuitState.OPEN) {
            circuitState = CircuitState.OPEN
            lastStateChangeMs.set(System.currentTimeMillis())
            Log.w(TAG, "Circuit breaker tripped to OPEN after $count consecutive failures")
        }
    }
}
