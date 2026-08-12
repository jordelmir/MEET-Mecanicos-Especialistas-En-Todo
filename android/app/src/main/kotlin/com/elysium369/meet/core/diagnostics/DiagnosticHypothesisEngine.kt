package com.elysium369.meet.core.diagnostics

data class HypothesisEngineDecision(
    val result: DiagnosticReasoningResult,
    val nextTest: RecommendedTest?,
    val decisionReason: String,
    val mode: GuidedDiagnosisMode = GuidedDiagnosisMode.HEURISTIC,
    val calibratedScore: DiagnosticTestDecisionScore? = null,
)

enum class GuidedDiagnosisMode { HEURISTIC, CALIBRATED }

data class GuidedDiagnosisCalibration(
    val priors: List<CalibratedHypothesisPrior>,
    val testCandidates: List<DiagnosticTestDecisionCandidate>,
    val artifact: SignedCalibrationArtifact? = null,
)

/**
 * Evidence-updating façade for guided diagnosis. It never promotes a component
 * replacement from DTC association alone and selects tests by information gain/cost.
 */
class DiagnosticHypothesisEngine(
    private val reasoningEngine: DiagnosticReasoningEngine = DiagnosticReasoningEngine(),
    private val calibrationTrustRegistry: CalibrationTrustRegistry = CalibrationTrustRegistry.DenyAll,
) {
    fun analyze(
        input: DiagnosticReasoningInput,
        calibration: GuidedDiagnosisCalibration? = null,
        availableTools: Set<String> = emptySet(),
    ): HypothesisEngineDecision {
        val result = reasoningEngine.analyze(input)
        val pending = result.case.recommendedTests
            .filter { it.status == TestStatus.NOT_STARTED }
            .filter { test ->
                availableTools.isEmpty() || test.toolRequired.isBlank() ||
                    availableTools.any { it.equals(test.toolRequired, ignoreCase = true) }
            }
        val trustedCalibration = calibration?.let { candidate ->
            val artifact = candidate.artifact
            candidate.takeIf {
                artifact != null && calibrationTrustRegistry.authorize(artifact) &&
                    candidate.testCandidates.all { test ->
                        test.calibrationDatasetId == artifact.datasetId &&
                            test.calibrationDatasetVersion == artifact.version
                    }
            }
        }
        val calibratedScores = trustedCalibration?.testCandidates.orEmpty()
            .filter { candidate ->
                candidate.requiredTools.all { required ->
                    availableTools.any { it.equals(required, ignoreCase = true) }
                }
            }
            .map { candidate -> DiagnosticTestDecisionModel.score(trustedCalibration!!.priors, candidate) }
            .filter(DiagnosticTestDecisionScore::calibrated)
        val bestCalibrated = calibratedScores.maxByOrNull(DiagnosticTestDecisionScore::utility)
        val calibratedTest = bestCalibrated?.let { score -> pending.firstOrNull { it.id == score.testId } }
        if (calibratedTest != null) {
            return HypothesisEngineDecision(
                result = result,
                nextTest = calibratedTest.copy(
                    quantitativeDecisionAvailable = true,
                    expectedInformationGainBits = bestCalibrated.expectedInformationGainBits,
                    calibrationDatasetId = trustedCalibration?.testCandidates
                        ?.firstOrNull { it.testId == calibratedTest.id }?.calibrationDatasetId,
                ),
                decisionReason = bestCalibrated.reason,
                mode = GuidedDiagnosisMode.CALIBRATED,
                calibratedScore = bestCalibrated,
            )
        }
        val nextTest = pending.maxWithOrNull(
                compareBy<RecommendedTest> { it.heuristicPriorityScore - it.executionCostScore / 2 }
                    .thenBy { it.safetyLevel != SafetyLevel.CRITICAL_SYSTEM }
                    .thenBy { -it.executionCostScore },
            )
        return HypothesisEngineDecision(
            result = result,
            nextTest = nextTest,
            decisionReason = nextTest?.selectionRationale
                ?: "No existe una siguiente prueba respaldada; se requieren datos adicionales.",
            mode = GuidedDiagnosisMode.HEURISTIC,
            calibratedScore = null,
        )
    }
}
