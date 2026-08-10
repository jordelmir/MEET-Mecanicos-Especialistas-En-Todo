package com.elysium369.meet.core.diagnostics

data class HypothesisEngineDecision(
    val result: DiagnosticReasoningResult,
    val nextTest: RecommendedTest?,
    val decisionReason: String,
)

/**
 * Evidence-updating façade for guided diagnosis. It never promotes a component
 * replacement from DTC association alone and selects tests by information gain/cost.
 */
class DiagnosticHypothesisEngine(
    private val reasoningEngine: DiagnosticReasoningEngine = DiagnosticReasoningEngine(),
) {
    fun analyze(input: DiagnosticReasoningInput): HypothesisEngineDecision {
        val result = reasoningEngine.analyze(input)
        val nextTest = result.case.recommendedTests
            .filter { it.status == TestStatus.NOT_STARTED }
            .maxWithOrNull(
                compareBy<RecommendedTest> { it.expectedInformationGain - it.executionCostScore / 2 }
                    .thenBy { it.safetyLevel != SafetyLevel.CRITICAL_SYSTEM }
                    .thenBy { -it.executionCostScore },
            )
        return HypothesisEngineDecision(
            result = result,
            nextTest = nextTest,
            decisionReason = nextTest?.selectionRationale
                ?: "No existe una siguiente prueba respaldada; se requieren datos adicionales.",
        )
    }
}
