package com.elysium369.meet.core.diagnostics

import com.elysium369.meet.core.obd.ObdDataSource
import com.elysium369.meet.core.obd.TelemetryQuality
import com.elysium369.meet.core.obd.TelemetrySample
import com.elysium369.meet.core.parts.PartSuggestionEngine
import com.elysium369.meet.core.parts.PartSuggestionInput
import com.elysium369.meet.core.parts.SuggestionSource
import com.elysium369.meet.core.services.WorkshopServiceCatalog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DiagnosticStatus {
    OPEN,
    NEEDS_DATA,
    TESTING,
    WAITING_REPAIR,
    VALIDATING,
    RESOLVED,
    INCONCLUSIVE,
}

enum class EvidenceSource {
    REAL_OBD,
    FREEZE_FRAME,
    LIVE_PID,
    MODE_06,
    USER_SYMPTOM,
    MANUAL_INPUT,
    MECHANIC_NOTE,
    REPAIR_HISTORY,
    PART_HISTORY,
    LOCAL_KNOWLEDGE,
    EXTERNAL_AI,
    SIMULATED,
    UNKNOWN,
}

enum class EvidenceConfidence {
    VERIFIED,
    STRONG,
    MEDIUM,
    WEAK,
    MISSING,
    CONTRADICTORY,
}

enum class DiagnosticSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class TestStatus {
    NOT_STARTED,
    IN_PROGRESS,
    PASSED,
    FAILED,
    SKIPPED,
    NOT_AVAILABLE,
}

enum class TestDifficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT_ONLY,
}

enum class SafetyLevel {
    NORMAL,
    CAUTION,
    CRITICAL_SYSTEM,
}

enum class PartRecommendationState {
    INVESTIGATE_ONLY,
    MAY_BE_NEEDED,
    CONFIRMED_NEEDED,
    DO_NOT_BUY_YET,
}

enum class DiagnosticConfidenceBand(val label: String) {
    HIGH("Alta confianza"),
    MEDIUM("Media confianza"),
    LOW("Baja confianza"),
    INCONCLUSIVE("Inconcluso"),
}

@Serializable
data class DiagnosticCase(
    val id: String,
    val vehicleId: String?,
    val sessionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val status: DiagnosticStatus,
    val primaryComplaint: String?,
    val dtcCodes: List<String>,
    val symptoms: List<String>,
    val evidenceItems: List<EvidenceItem>,
    val hypotheses: List<Hypothesis>,
    val recommendedTests: List<RecommendedTest>,
    val finalConclusion: String? = null,
    val reportId: String? = null,
)

@Serializable
data class EvidenceItem(
    val id: String,
    val caseId: String,
    val source: EvidenceSource,
    val type: String,
    val label: String,
    val value: String,
    val unit: String? = null,
    val rawPayload: String? = null,
    val confidence: EvidenceConfidence,
    val timestamp: Long,
    val relatedDtc: String? = null,
)

@Serializable
data class Hypothesis(
    val id: String,
    val caseId: String,
    val title: String,
    val componentOrSystem: String,
    val probabilityPercent: Int,
    val severity: DiagnosticSeverity,
    val reasoning: String,
    val supportingEvidence: List<String>,
    val contradictingEvidence: List<String>,
    val requiredTests: List<String>,
    val riskIfIgnored: String,
    val estimatedCostMin: Int,
    val estimatedCostMax: Int,
    val laborTimeMin: Int,
    val laborTimeMax: Int,
    val ruleId: String,
    val relatedComponents3d: List<String> = emptyList(),
)

@Serializable
data class RecommendedTest(
    val id: String,
    val caseId: String,
    val name: String,
    val description: String,
    val toolRequired: String,
    val difficulty: TestDifficulty,
    val safetyLevel: SafetyLevel,
    val expectedResult: String,
    val nextIfPass: String,
    val nextIfFail: String,
    val relatedHypothesisId: String,
    val status: TestStatus,
)

@Serializable
data class CausalTreeNode(
    val id: String,
    val title: String,
    val probabilityPercent: Int? = null,
    val evidence: List<String> = emptyList(),
    val testStatus: TestStatus? = null,
    val actionLabel: String? = null,
    val componentSlugs: List<String> = emptyList(),
    val children: List<CausalTreeNode> = emptyList(),
)

@Serializable
data class DiagnosticCorrelation(
    val dtc: String,
    val symptom: String?,
    val pid: String?,
    val condition: String,
    val meaning: String,
    val confidenceModifier: Int,
)

@Serializable
data class Contradiction(
    val hypothesisId: String,
    val evidence: String,
    val contradictionReason: String,
    val probabilityPenalty: Int,
)

@Serializable
data class ServiceRecommendation(
    val serviceId: String,
    val serviceName: String,
    val reason: String,
    val requiredEvidence: List<String>,
    val urgency: DiagnosticSeverity,
    val estimatedPriceRange: String,
)

@Serializable
data class PartRecommendation(
    val partName: String,
    val category: String,
    val state: PartRecommendationState,
    val reason: String,
    val requiredEvidence: List<String>,
    val riskPart: Boolean,
)

@Serializable
data class DiagnosticConfidenceScore(
    val scorePercent: Int,
    val band: DiagnosticConfidenceBand,
    val positiveFactors: List<String>,
    val negativeFactors: List<String>,
)

@Serializable
data class DiagnosticDecisionLog(
    val caseId: String,
    val timestamp: Long,
    val inputHash: String,
    val ruleIdsUsed: List<String>,
    val aiProviderUsed: String?,
    val outputHash: String,
    val confidence: Int,
    val version: String,
)

@Serializable
data class ExternalAiDiagnosticPayload(
    val caseSummary: String,
    val vehicleContext: String?,
    val evidenceTable: List<String>,
    val hypothesesFromLocalEngine: List<String>,
    val missingData: List<String>,
    val allowedOutputSchema: String,
)

@Serializable
data class AiVehicleResolution(
    val status: String,
    val vehicleId: String? = null,
    val label: String? = null,
)

@Serializable
data class AiSafetyAssessment(
    val risk: String,
    val warnings: List<String>,
    val blockedActions: List<String>,
)

@Serializable
data class ValidatedAiDiagnosticResponse(
    val intent: String,
    val vehicleResolution: AiVehicleResolution,
    val applicability: List<String>,
    val summary: String,
    val assumptions: List<String>,
    val unknowns: List<String>,
    val safety: AiSafetyAssessment,
    val diagnosticPlan: List<String>,
    val measurements: List<String>,
    val procedureId: String?,
    val referenceExamples: List<String>,
    val evidence: List<String>,
    val confidence: DiagnosticConfidenceBand,
    val requiresProfessional: Boolean,
)

@Serializable
data class AiDiagnosticOrchestrationResult(
    val localResult: DiagnosticReasoningResult,
    val externalResponse: ValidatedAiDiagnosticResponse?,
    val usedFallback: Boolean,
    val fallbackReason: String?,
)

@Serializable
data class DiagnosticReasoningResult(
    val case: DiagnosticCase,
    val causalTree: CausalTreeNode,
    val missingData: List<String>,
    val contradictions: List<Contradiction>,
    val confidenceScore: DiagnosticConfidenceScore,
    val serviceRecommendations: List<ServiceRecommendation>,
    val partRecommendations: List<PartRecommendation>,
    val warnings: List<String>,
    val nextBestAction: String,
    val reportSummary: String,
    val aiPayload: ExternalAiDiagnosticPayload,
    val decisionLog: DiagnosticDecisionLog,
)

data class DiagnosticReasoningInput(
    val caseId: String = UUID.randomUUID().toString(),
    val vehicleId: String? = null,
    val sessionId: String? = null,
    val vehicleLabel: String? = null,
    val primaryComplaint: String? = null,
    val dtcCodes: List<String> = emptyList(),
    val obdConnected: Boolean = false,
    val freezeFrame: Map<String, String> = emptyMap(),
    val livePids: Map<String, TelemetrySample> = emptyMap(),
    val mode06: Map<String, String> = emptyMap(),
    val readiness: Map<String, String> = emptyMap(),
    val symptoms: List<String> = emptyList(),
    val repairHistory: List<String> = emptyList(),
    val previousRepairs: List<String> = emptyList(),
    val replacedParts: List<String> = emptyList(),
    val mechanicNotes: List<String> = emptyList(),
    val userNotes: List<String> = emptyList(),
    val completedTests: List<String> = emptyList(),
    val manualMeasurements: Map<String, String> = emptyMap(),
)

data class DiagnosticRule(
    val id: String,
    val dtcPattern: String,
    val system: String,
    val conditionExpression: String,
    val hypothesis: String,
    val component: String,
    val baseProbability: Int,
    val severity: DiagnosticSeverity,
    val requiredEvidence: List<String>,
    val recommendedTests: List<RecommendedTestTemplate>,
    val safetyNotes: List<String>,
    val relatedServices: List<String>,
    val relatedParts: List<String>,
    val riskIfIgnored: String,
    val estimatedCostMin: Int,
    val estimatedCostMax: Int,
    val laborTimeMin: Int,
    val laborTimeMax: Int,
    val symptomHints: List<String> = emptyList(),
    val pids: List<String> = emptyList(),
    val components3d: List<String> = emptyList(),
)

data class RecommendedTestTemplate(
    val id: String,
    val name: String,
    val description: String,
    val toolRequired: String,
    val difficulty: TestDifficulty,
    val safetyLevel: SafetyLevel,
    val expectedResult: String,
    val nextIfPass: String,
    val nextIfFail: String,
)

data class SymptomProfile(
    val symptom: String,
    val probableSystems: List<String>,
    val relatedDtcs: List<String>,
    val relevantPids: List<String>,
    val initialTests: List<String>,
    val suggestedServices: List<String>,
)

class DiagnosticReasoningEngine(
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val ruleEngine = RuleBasedDiagnosticEngine()
    private val contradictionDetector = ContradictionDetector()

    fun supportedDtcs(): Set<String> = ruleEngine.supportedDtcs()

    fun analyze(input: DiagnosticReasoningInput): DiagnosticReasoningResult {
        val timestamp = now()
        val normalizedDtcs = input.dtcCodes
            .mapNotNull(::normalizeDtc)
            .distinct()
        val evidence = buildEvidence(input, normalizedDtcs, timestamp)
        val caseId = input.caseId

        val ruleEvaluations = if (normalizedDtcs.isEmpty()) {
            symptomOnlyEvaluations(input)
        } else {
            ruleEngine.evaluate(input, normalizedDtcs)
        }

        val provisional = ruleEvaluations
            .map { it.toHypothesis(caseId, evidence, input) }
            .sortedByDescending { it.probabilityPercent }

        val contradictions = contradictionDetector.detect(input, provisional)
        val hypotheses = provisional
            .map { hypothesis ->
                val penalties = contradictions
                    .filter { it.hypothesisId == hypothesis.id }
                    .fold(0) { acc, contradiction -> acc + contradiction.probabilityPenalty }
                if (penalties == 0) {
                    hypothesis
                } else {
                    hypothesis.copy(
                        probabilityPercent = (hypothesis.probabilityPercent - penalties).coerceAtLeast(3),
                        contradictingEvidence = hypothesis.contradictingEvidence +
                            contradictions.filter { it.hypothesisId == hypothesis.id }
                                .map { it.contradictionReason },
                    )
                }
            }
            .sortedByDescending { it.probabilityPercent }

        val recommendedTests = buildRecommendedTests(caseId, hypotheses, input.completedTests)
        val missingData = buildMissingData(input, normalizedDtcs, hypotheses)
        val confidence = scoreConfidence(input, normalizedDtcs, evidence, contradictions, missingData)
        val services = buildServiceRecommendations(normalizedDtcs, hypotheses)
        val parts = buildPartRecommendations(normalizedDtcs, input, hypotheses)
        val warnings = buildWarnings(input, hypotheses)
        val nextBestAction = recommendedTests.firstOrNull { it.status == TestStatus.NOT_STARTED }?.name
            ?: missingData.firstOrNull()
            ?: "Validar reparacion con escaneo OBD y prueba de carretera."

        val status = when {
            normalizedDtcs.isEmpty() -> DiagnosticStatus.NEEDS_DATA
            confidence.band == DiagnosticConfidenceBand.INCONCLUSIVE -> DiagnosticStatus.INCONCLUSIVE
            recommendedTests.any { it.status == TestStatus.NOT_STARTED } -> DiagnosticStatus.TESTING
            else -> DiagnosticStatus.VALIDATING
        }

        val diagnosticCase = DiagnosticCase(
            id = caseId,
            vehicleId = input.vehicleId,
            sessionId = input.sessionId,
            createdAt = timestamp,
            updatedAt = timestamp,
            status = status,
            primaryComplaint = input.primaryComplaint,
            dtcCodes = normalizedDtcs,
            symptoms = input.symptoms,
            evidenceItems = evidence,
            hypotheses = hypotheses,
            recommendedTests = recommendedTests,
            finalConclusion = null,
            reportId = null,
        )

        val tree = buildCausalTree(diagnosticCase)
        val reportSummary = buildReportSummary(
            diagnosticCase = diagnosticCase,
            confidence = confidence,
            missingData = missingData,
            services = services,
            parts = parts,
            warnings = warnings,
            nextBestAction = nextBestAction,
        )
        val payload = buildAiPayload(input, diagnosticCase, missingData)
        val ruleIds = hypotheses.map { it.ruleId }.distinct()
        val inputHash = sha256(
            listOf(
                input.vehicleLabel.orEmpty(),
                normalizedDtcs.joinToString(","),
                input.symptoms.joinToString(","),
                evidence.joinToString("|") { "${it.source}:${it.label}:${it.value}" },
            ).joinToString("#")
        )
        val outputHash = sha256(reportSummary)

        return DiagnosticReasoningResult(
            case = diagnosticCase,
            causalTree = tree,
            missingData = missingData,
            contradictions = contradictions,
            confidenceScore = confidence,
            serviceRecommendations = services,
            partRecommendations = parts,
            warnings = warnings,
            nextBestAction = nextBestAction,
            reportSummary = reportSummary,
            aiPayload = payload,
            decisionLog = DiagnosticDecisionLog(
                caseId = caseId,
                timestamp = timestamp,
                inputHash = inputHash,
                ruleIdsUsed = ruleIds,
                aiProviderUsed = null,
                outputHash = outputHash,
                confidence = confidence.scorePercent,
                version = VERSION,
            ),
        )
    }

    private fun RuleEvaluation.toHypothesis(
        caseId: String,
        evidence: List<EvidenceItem>,
        input: DiagnosticReasoningInput,
    ): Hypothesis {
        val supporting = mutableListOf<String>()
        if (dtc != null) supporting += "DTC $dtc presente"
        supporting += evidence
            .filter { it.relatedDtc == dtc || it.source in setOf(EvidenceSource.LIVE_PID, EvidenceSource.FREEZE_FRAME, EvidenceSource.USER_SYMPTOM) }
            .take(5)
            .map { "${it.label}: ${it.value}" }
        if (input.obdConnected) {
            supporting += "Scanner conectado con evidencia disponible"
        }
        return Hypothesis(
            id = "hyp_${rule.id}",
            caseId = caseId,
            title = rule.hypothesis,
            componentOrSystem = rule.component,
            probabilityPercent = probabilityPercent,
            severity = rule.severity,
            reasoning = reasoning,
            supportingEvidence = supporting.distinct().take(8),
            contradictingEvidence = emptyList(),
            requiredTests = rule.recommendedTests.map { it.name },
            riskIfIgnored = rule.riskIfIgnored,
            estimatedCostMin = rule.estimatedCostMin,
            estimatedCostMax = rule.estimatedCostMax,
            laborTimeMin = rule.laborTimeMin,
            laborTimeMax = rule.laborTimeMax,
            ruleId = rule.id,
            relatedComponents3d = rule.components3d,
        )
    }

    private fun buildEvidence(
        input: DiagnosticReasoningInput,
        normalizedDtcs: List<String>,
        timestamp: Long,
    ): List<EvidenceItem> {
        val out = mutableListOf<EvidenceItem>()
        normalizedDtcs.forEach { dtc ->
            out += EvidenceItem(
                id = "ev_dtc_$dtc",
                caseId = input.caseId,
                source = if (input.obdConnected) EvidenceSource.REAL_OBD else EvidenceSource.MANUAL_INPUT,
                type = "DTC",
                label = "DTC activo",
                value = dtc,
                confidence = if (input.obdConnected) EvidenceConfidence.VERIFIED else EvidenceConfidence.MEDIUM,
                timestamp = timestamp,
                relatedDtc = dtc,
            )
        }
        input.freezeFrame.forEach { (key, value) ->
            out += EvidenceItem(
                id = "ev_ff_${slug(key)}",
                caseId = input.caseId,
                source = EvidenceSource.FREEZE_FRAME,
                type = "FREEZE_FRAME",
                label = key,
                value = value,
                confidence = EvidenceConfidence.STRONG,
                timestamp = timestamp,
                relatedDtc = normalizedDtcs.firstOrNull(),
            )
        }
        input.livePids.values.forEach { sample ->
            out += EvidenceItem(
                id = "ev_pid_${slug(sample.pid)}",
                caseId = input.caseId,
                source = evidenceSourceFor(sample),
                type = "LIVE_PID",
                label = sample.name.ifBlank { sample.pid },
                value = sample.value?.let { trimDouble(it) } ?: sample.displayText(),
                unit = sample.unit.takeIf { it.isNotBlank() },
                rawPayload = sample.rawResponse.takeIf { it.isNotBlank() },
                confidence = confidenceFor(sample),
                timestamp = timestamp,
                relatedDtc = normalizedDtcs.firstOrNull(),
            )
        }
        input.mode06.forEach { (key, value) ->
            out += EvidenceItem(
                id = "ev_m06_${slug(key)}",
                caseId = input.caseId,
                source = EvidenceSource.MODE_06,
                type = "MODE_06",
                label = key,
                value = value,
                confidence = EvidenceConfidence.STRONG,
                timestamp = timestamp,
                relatedDtc = normalizedDtcs.firstOrNull(),
            )
        }
        input.symptoms.forEachIndexed { index, symptom ->
            out += EvidenceItem(
                id = "ev_symptom_$index",
                caseId = input.caseId,
                source = EvidenceSource.USER_SYMPTOM,
                type = "SYMPTOM",
                label = "Sintoma reportado",
                value = symptom,
                confidence = EvidenceConfidence.MEDIUM,
                timestamp = timestamp,
                relatedDtc = normalizedDtcs.firstOrNull(),
            )
        }
        input.repairHistory.forEachIndexed { index, item ->
            out += EvidenceItem(
                id = "ev_history_$index",
                caseId = input.caseId,
                source = EvidenceSource.REPAIR_HISTORY,
                type = "REPAIR_HISTORY",
                label = "Historial",
                value = item,
                confidence = EvidenceConfidence.MEDIUM,
                timestamp = timestamp,
            )
        }
        input.replacedParts.forEachIndexed { index, item ->
            out += EvidenceItem(
                id = "ev_part_history_$index",
                caseId = input.caseId,
                source = EvidenceSource.PART_HISTORY,
                type = "PART_HISTORY",
                label = "Pieza reemplazada",
                value = item,
                confidence = EvidenceConfidence.MEDIUM,
                timestamp = timestamp,
            )
        }
        input.manualMeasurements.forEach { (key, value) ->
            out += EvidenceItem(
                id = "ev_manual_${slug(key)}",
                caseId = input.caseId,
                source = EvidenceSource.MANUAL_INPUT,
                type = "MANUAL_MEASUREMENT",
                label = key,
                value = value,
                confidence = EvidenceConfidence.MEDIUM,
                timestamp = timestamp,
                relatedDtc = normalizedDtcs.firstOrNull(),
            )
        }
        input.mechanicNotes.forEachIndexed { index, note ->
            out += EvidenceItem(
                id = "ev_mechanic_note_$index",
                caseId = input.caseId,
                source = EvidenceSource.MECHANIC_NOTE,
                type = "NOTE",
                label = "Nota mecanico",
                value = AiPayloadSanitizer.sanitizeNote(note),
                confidence = EvidenceConfidence.MEDIUM,
                timestamp = timestamp,
            )
        }
        return out
    }

    private fun buildRecommendedTests(
        caseId: String,
        hypotheses: List<Hypothesis>,
        completedTests: List<String>,
    ): List<RecommendedTest> {
        val completed = completedTests.map { it.normalKey() }.toSet()
        val templates = hypotheses
            .flatMap { hypothesis ->
                val rule = ruleEngine.ruleById(hypothesis.ruleId)
                val sourceTemplates = rule?.recommendedTests ?: hypothesis.requiredTests.mapIndexed { index, name ->
                    RecommendedTestTemplate(
                        id = "${hypothesis.id}_test_$index",
                        name = name,
                        description = "Prueba requerida por ${hypothesis.title}.",
                        toolRequired = "Inspeccion tecnica + OBD si disponible",
                        difficulty = TestDifficulty.EASY,
                        safetyLevel = SafetyLevel.CAUTION,
                        expectedResult = "Resultado documentado antes de concluir.",
                        nextIfPass = "Continuar con la siguiente hipotesis/prueba.",
                        nextIfFail = "Escalar esta hipotesis y pedir servicio compatible.",
                    )
                }
                sourceTemplates.map { hypothesis to it }
            }
            .distinctBy { (_, template) -> template.id }
            .take(12)
        return templates.map { (hypothesis, template) ->
            RecommendedTest(
                id = template.id,
                caseId = caseId,
                name = template.name,
                description = template.description,
                toolRequired = template.toolRequired,
                difficulty = template.difficulty,
                safetyLevel = template.safetyLevel,
                expectedResult = template.expectedResult,
                nextIfPass = template.nextIfPass,
                nextIfFail = template.nextIfFail,
                relatedHypothesisId = hypothesis.id,
                status = if (template.id.normalKey() in completed || template.name.normalKey() in completed) {
                    TestStatus.PASSED
                } else {
                    TestStatus.NOT_STARTED
                },
            )
        }
    }

    private fun buildMissingData(
        input: DiagnosticReasoningInput,
        dtcs: List<String>,
        hypotheses: List<Hypothesis>,
    ): List<String> {
        val missing = linkedSetOf<String>()
        if (dtcs.isNotEmpty() && input.freezeFrame.isEmpty()) missing += "freeze frame del DTC"
        if (!input.obdConnected) missing += "conexion OBD real"
        if (input.livePids.none { it.value.hasRealValue() }) missing += "PIDs en vivo validos"
        if (input.mode06.isEmpty()) missing += "Mode 06 disponible"

        if ("P0230" in dtcs) {
            if (findValue(input, "BATTERY_VOLTAGE", "0142", "VOLTAGE") == null) missing += "voltaje bateria"
            if (!hasManualOrCompleted(input, "pump_voltage_check", "voltaje bomba", "fuel pump voltage")) missing += "voltaje en conector bomba"
            if (!hasManualOrCompleted(input, "fuel_pressure_check", "presion combustible", "fuel pressure")) missing += "presion combustible con manometro"
            if (!hasManualOrCompleted(input, "fuse_check", "fusible")) missing += "resultado fusible bomba"
            if (!hasManualOrCompleted(input, "relay_check", "rele", "relay")) missing += "resultado rele bomba"
            if (!hasManualOrCompleted(input, "ground_voltage_drop", "masa", "ground")) missing += "caida de voltaje en tierra"
            missing += "foto o inspeccion de fusiblera/conector"
        }
        if ("P0171" in dtcs || "P0172" in dtcs || "P0420" in dtcs) {
            if (findValue(input, "LTFT", "LTFT_B1", "LONG_FUEL_TRIM_B1", "0107") == null) missing += "LTFT/STFT fuel trims"
            if (findValue(input, "MAF", "0110") == null && "P0171" in dtcs) missing += "MAF/MAP en vivo"
        }
        if (hypotheses.any { it.severity == DiagnosticSeverity.CRITICAL }) {
            missing += "validacion fisica por tecnico calificado"
        }
        return missing.toList()
    }

    private fun buildServiceRecommendations(
        dtcs: List<String>,
        hypotheses: List<Hypothesis>,
    ): List<ServiceRecommendation> {
        val services = WorkshopServiceCatalog.bestServicesForDtcs(dtcs).take(5)
        val top = hypotheses.firstOrNull()
        return services.map { service ->
            ServiceRecommendation(
                serviceId = service.id,
                serviceName = service.name,
                reason = top?.let { "Relacionado con ${it.title} (${it.probabilityPercent}%)." }
                    ?: "Diagnostico inicial recomendado por sintomas/DTC.",
                requiredEvidence = service.requiredEvidence.map { it.name },
                urgency = top?.severity ?: DiagnosticSeverity.MEDIUM,
                estimatedPriceRange = "${service.basePriceMinCrc}-${service.basePriceMaxCrc} CRC",
            )
        }
    }

    private fun buildPartRecommendations(
        dtcs: List<String>,
        input: DiagnosticReasoningInput,
        hypotheses: List<Hypothesis>,
    ): List<PartRecommendation> {
        if (dtcs.isEmpty()) return emptyList()
        val suggestions = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(
                source = SuggestionSource.DTC,
                dtcCodes = dtcs,
            )
        )
        val completed = input.completedTests.map { it.normalKey() }.toSet()
        val p0230 = "P0230" in dtcs
        val pumpEvidenceComplete = listOf(
            "pump_voltage_check",
            "ground_voltage_drop",
            "fuel_pressure_check",
            "relay_check",
            "fuse_check",
        ).all { it.normalKey() in completed }

        return suggestions.take(8).map { suggestion ->
            val lower = suggestion.partName.lowercase(Locale.ROOT)
            val state = when {
                p0230 && lower.contains("bomba") && pumpEvidenceComplete -> PartRecommendationState.MAY_BE_NEEDED
                p0230 && lower.contains("bomba") && !pumpEvidenceComplete -> PartRecommendationState.DO_NOT_BUY_YET
                p0230 && lower.contains("fusible") -> PartRecommendationState.INVESTIGATE_ONLY
                p0230 && (lower.contains("rele") || lower.contains("relé") || lower.contains("arnes") || lower.contains("arnés")) ->
                    PartRecommendationState.MAY_BE_NEEDED
                suggestion.riskPart -> PartRecommendationState.DO_NOT_BUY_YET
                hypotheses.firstOrNull()?.probabilityPercent.orZero() >= 70 -> PartRecommendationState.MAY_BE_NEEDED
                else -> PartRecommendationState.INVESTIGATE_ONLY
            }
            val requiredEvidence = when (state) {
                PartRecommendationState.DO_NOT_BUY_YET -> listOf(
                    "Prueba critica pendiente antes de comprar",
                    suggestion.disclaimer ?: "Confirmar compatibilidad y causa raiz",
                )
                PartRecommendationState.CONFIRMED_NEEDED -> listOf("Prueba fisica documentada")
                PartRecommendationState.MAY_BE_NEEDED -> listOf("Confirmar por VIN/OEM/foto/conector")
                PartRecommendationState.INVESTIGATE_ONLY -> listOf("Investigar primero, no comprar todavia")
            }
            PartRecommendation(
                partName = suggestion.partName,
                category = suggestion.category,
                state = state,
                reason = suggestion.rationale,
                requiredEvidence = requiredEvidence.distinct(),
                riskPart = suggestion.riskPart,
            )
        }
    }

    private fun buildWarnings(
        input: DiagnosticReasoningInput,
        hypotheses: List<Hypothesis>,
    ): List<String> {
        val warnings = linkedSetOf<String>()
        warnings += "No condenar piezas sin prueba fisica y evidencia suficiente."
        if (!input.obdConnected) warnings += "Scanner desconectado o sin enlace ECU: confianza limitada."
        if (input.livePids.values.any { it.source == ObdDataSource.SIMULATED_DEMO || it.quality == TelemetryQuality.SIMULATED }) {
            warnings += "Datos simulados detectados: no sostienen conclusiones fuertes."
        }
        val criticalTerms = listOf("airbag", "srs", "abs", "freno", "direccion", "alto voltaje", "hybrid", "ev", "combustible", "ecu", "inmovilizador")
        if (hypotheses.any { hypothesis ->
                criticalTerms.any { term ->
                    hypothesis.title.lowercase(Locale.ROOT).contains(term) ||
                        hypothesis.componentOrSystem.lowercase(Locale.ROOT).contains(term)
                }
            }) {
            warnings += "Sistema critico. Requiere tecnico calificado y condiciones de seguridad."
        }
        return warnings.toList()
    }

    private fun buildCausalTree(diagnosticCase: DiagnosticCase): CausalTreeNode {
        val rootTitle = diagnosticCase.dtcCodes.firstOrNull()
            ?: diagnosticCase.primaryComplaint
            ?: diagnosticCase.symptoms.firstOrNull()
            ?: "Caso diagnostico"
        val systemNodes = diagnosticCase.hypotheses
            .groupBy { it.componentOrSystem }
            .map { (system, hypotheses) ->
                CausalTreeNode(
                    id = "system_${slug(system)}",
                    title = system,
                    children = hypotheses.map { hypothesis ->
                        CausalTreeNode(
                            id = hypothesis.id,
                            title = hypothesis.title,
                            probabilityPercent = hypothesis.probabilityPercent,
                            evidence = hypothesis.supportingEvidence.take(3),
                            actionLabel = "Hacer prueba",
                            componentSlugs = hypothesis.relatedComponents3d,
                            children = diagnosticCase.recommendedTests
                                .filter { it.relatedHypothesisId == hypothesis.id }
                                .take(4)
                                .map { test ->
                                    CausalTreeNode(
                                        id = "node_${test.id}",
                                        title = "Prueba: ${test.name}",
                                        evidence = listOf(test.expectedResult),
                                        testStatus = test.status,
                                        actionLabel = if (test.status == TestStatus.PASSED) "Resultado agregado" else "Hacer prueba",
                                    )
                                },
                        )
                    },
                )
            }
        return CausalTreeNode(
            id = "root_${slug(rootTitle)}",
            title = rootTitle,
            evidence = diagnosticCase.evidenceItems.take(3).map { "${it.label}: ${it.value}" },
            children = systemNodes,
        )
    }

    private fun scoreConfidence(
        input: DiagnosticReasoningInput,
        dtcs: List<String>,
        evidence: List<EvidenceItem>,
        contradictions: List<Contradiction>,
        missingData: List<String>,
    ): DiagnosticConfidenceScore {
        var score = 35
        val positives = mutableListOf<String>()
        val negatives = mutableListOf<String>()
        if (dtcs.isNotEmpty()) {
            score += 10
            positives += "DTC activo/seleccionado"
        }
        if (input.freezeFrame.isNotEmpty()) {
            score += 10
            positives += "Freeze frame disponible"
        } else {
            score -= 5
            negatives += "Freeze frame faltante"
        }
        if (input.livePids.values.any { it.hasRealValue() }) {
            score += 12
            positives += "Live PIDs reales validos"
        } else {
            score -= 8
            negatives += "Live PIDs faltantes"
        }
        if (input.mode06.isNotEmpty()) {
            score += 8
            positives += "Mode 06 disponible"
        }
        if (input.completedTests.isNotEmpty()) {
            score += (input.completedTests.size * 4).coerceAtMost(16)
            positives += "Pruebas fisicas agregadas"
        }
        if (input.symptoms.isNotEmpty()) {
            score += 4
            positives += "Sintomas del usuario incluidos"
        }
        if (input.repairHistory.isNotEmpty()) {
            score += 3
            positives += "Historial de reparacion incluido"
        }
        if (!input.obdConnected) {
            score -= 15
            negatives += "Scanner desconectado"
        }
        if (evidence.any { it.source in setOf(EvidenceSource.SIMULATED, EvidenceSource.UNKNOWN) }) {
            score -= 20
            negatives += "Evidencia simulada/desconocida"
        }
        if (contradictions.isNotEmpty()) {
            score -= contradictions.fold(0) { acc, item -> acc + item.probabilityPenalty }.coerceAtMost(25)
            negatives += "Evidencia contradictoria"
        }
        if (missingData.size >= 5) {
            score -= 8
            negatives += "Datos criticos faltantes"
        }
        if (input.vehicleLabel.isNullOrBlank()) {
            score -= 5
            negatives += "Vehiculo sin identificacion completa"
        }
        val normalized = score.coerceIn(5, 90)
        val band = when {
            normalized >= 75 -> DiagnosticConfidenceBand.HIGH
            normalized >= 55 -> DiagnosticConfidenceBand.MEDIUM
            normalized >= 30 -> DiagnosticConfidenceBand.LOW
            else -> DiagnosticConfidenceBand.INCONCLUSIVE
        }
        return DiagnosticConfidenceScore(
            scorePercent = normalized,
            band = band,
            positiveFactors = positives.distinct(),
            negativeFactors = negatives.distinct(),
        )
    }

    private fun buildAiPayload(
        input: DiagnosticReasoningInput,
        diagnosticCase: DiagnosticCase,
        missingData: List<String>,
    ): ExternalAiDiagnosticPayload {
        val notes = (input.userNotes + input.mechanicNotes)
            .map(AiPayloadSanitizer::sanitizeNote)
            .filter { it.isNotBlank() }
        val evidenceRows = diagnosticCase.evidenceItems.map {
            "${it.source} | ${it.confidence} | ${it.label} = ${it.value}"
        } + notes.map { "SANITIZED_NOTE | MEDIUM | nota = $it" }
        return ExternalAiDiagnosticPayload(
            caseSummary = "Caso ${diagnosticCase.id}: ${diagnosticCase.dtcCodes.joinToString().ifBlank { "sin DTC" }}",
            vehicleContext = input.vehicleLabel,
            evidenceTable = evidenceRows,
            hypothesesFromLocalEngine = diagnosticCase.hypotheses.map {
                "${it.probabilityPercent}% ${it.title}; pruebas=${it.requiredTests.joinToString()}"
            },
            missingData = missingData,
            allowedOutputSchema = """
                STRICT_JSON_V2; exact top-level keys only:
                intent="DIAGNOSIS";
                vehicle_resolution={status:"RESOLVED|PARTIAL|UNRESOLVED",vehicle_id?:string,label?:string};
                applicability:string[]; summary:string; assumptions:string[]; unknowns:string[];
                safety={risk:"LOW|MEDIUM|HIGH|CRITICAL",warnings:string[],blocked_actions:string[]};
                diagnostic_plan:string[]; measurements:string[]; procedure_id:string|null;
                reference_examples:string[]; evidence:string[];
                confidence="HIGH|MEDIUM|LOW|INCONCLUSIVE"; requires_professional:boolean.
                Cite only supplied evidence. Never invent measurements, procedures, TSBs, OEM data or commands.
            """.trimIndent(),
        )
    }

    private fun buildReportSummary(
        diagnosticCase: DiagnosticCase,
        confidence: DiagnosticConfidenceScore,
        missingData: List<String>,
        services: List<ServiceRecommendation>,
        parts: List<PartRecommendation>,
        warnings: List<String>,
        nextBestAction: String,
    ): String = buildString {
        appendLine("ANALISIS LOCAL ELYSIUM VANGUARD")
        appendLine("Caso: ${diagnosticCase.id}")
        appendLine("DTCs: ${diagnosticCase.dtcCodes.joinToString().ifBlank { "sin DTC confirmado" }}")
        appendLine("Estado: ${diagnosticCase.status}")
        appendLine("Confianza: ${confidence.band.label} (${confidence.scorePercent}%)")
        appendLine()
        appendLine("Hipotesis priorizadas:")
        diagnosticCase.hypotheses.take(6).forEach { hypothesis ->
            appendLine("- ${hypothesis.probabilityPercent}% ${hypothesis.title}")
            appendLine("  Evidencia: ${hypothesis.supportingEvidence.take(3).joinToString().ifBlank { "pendiente" }}")
            appendLine("  Prueba siguiente: ${hypothesis.requiredTests.firstOrNull() ?: "pendiente"}")
        }
        appendLine()
        appendLine("Pruebas recomendadas:")
        diagnosticCase.recommendedTests.take(8).forEach { test ->
            appendLine("- [${test.status}] ${test.name}: ${test.expectedResult}")
        }
        appendLine()
        appendLine("Datos faltantes criticos:")
        missingData.ifEmpty { listOf("Sin datos criticos pendientes para esta etapa.") }.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Servicios sugeridos:")
        services.take(4).forEach { appendLine("- ${it.serviceName}: ${it.reason}") }
        appendLine()
        appendLine("Repuestos posibles:")
        parts.take(6).forEach { appendLine("- ${it.partName}: ${it.state} (${it.reason})") }
        appendLine()
        appendLine("Advertencias:")
        warnings.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Siguiente mejor accion: $nextBestAction")
        appendLine("Nota: analisis local; no inventa mediciones ni confirma piezas sin pruebas.")
    }.trim()

    private fun symptomOnlyEvaluations(input: DiagnosticReasoningInput): List<RuleEvaluation> {
        val profiles = SymptomBank.match(input.symptoms)
        if (profiles.isEmpty()) return emptyList()
        val primary = profiles.first()
        val tests = primary.initialTests.mapIndexed { index, test ->
            RecommendedTestTemplate(
                id = "symptom_${slug(primary.symptom)}_$index",
                name = test,
                description = "Prueba inicial por sintoma ${primary.symptom}.",
                toolRequired = "Inspeccion tecnica + OBD si disponible",
                difficulty = TestDifficulty.EASY,
                safetyLevel = SafetyLevel.CAUTION,
                expectedResult = "Clasificar si el sistema responde normal o requiere diagnostico profundo.",
                nextIfPass = "Continuar con el siguiente sistema probable.",
                nextIfFail = "Crear DTC/caso especifico y escalar a mecanico.",
            )
        }
        val rules = primary.probableSystems.take(5).mapIndexed { index, system ->
            DiagnosticRule(
                id = "symptom_${slug(primary.symptom)}_$index",
                dtcPattern = "SYMPTOM",
                system = system,
                conditionExpression = "Sintoma reportado sin DTC confirmado",
                hypothesis = "Posible causa en $system",
                component = system,
                baseProbability = (28 - index * 3).coerceAtLeast(10),
                severity = if (primary.symptom.contains("no arranca", ignoreCase = true)) DiagnosticSeverity.HIGH else DiagnosticSeverity.MEDIUM,
                requiredEvidence = listOf("DTC real", "live PIDs", "prueba fisica"),
                recommendedTests = tests,
                safetyNotes = listOf("Diagnostico inicial por sintomas; requiere confirmacion."),
                relatedServices = primary.suggestedServices,
                relatedParts = emptyList(),
                riskIfIgnored = "Puede evolucionar o dejar el vehiculo inmovilizado.",
                estimatedCostMin = 0,
                estimatedCostMax = 45000,
                laborTimeMin = 20,
                laborTimeMax = 60,
                symptomHints = listOf(primary.symptom),
            )
        }
        return rules.map { rule ->
            RuleEvaluation(
                rule = rule,
                dtc = null,
                probabilityPercent = rule.baseProbability,
                reasoning = "Diagnostico inicial por sintoma '${primary.symptom}'. Falta DTC/OBD real para conclusion fuerte.",
            )
        }
    }

    companion object {
        const val VERSION = "diagnostic_reasoning_v2_2026_07_05"
    }
}

data class RuleEvaluation(
    val rule: DiagnosticRule,
    val dtc: String?,
    val probabilityPercent: Int,
    val reasoning: String,
)

class RuleBasedDiagnosticEngine {
    private val rules: List<DiagnosticRule> = DiagnosticRuleCatalog.rules
    private val byId = rules.associateBy { it.id }

    fun supportedDtcs(): Set<String> = rules.mapNotNull { normalizeDtc(it.dtcPattern) }.toSet()

    fun ruleById(id: String): DiagnosticRule? = byId[id]

    fun evaluate(input: DiagnosticReasoningInput, dtcs: List<String>): List<RuleEvaluation> {
        return dtcs.flatMap { dtc ->
            rules.filter { rule -> rule.dtcPattern == dtc }.map { rule ->
                val probability = adjustProbability(rule, dtc, input)
                RuleEvaluation(
                    rule = rule,
                    dtc = dtc,
                    probabilityPercent = probability,
                    reasoning = buildReasoning(rule, dtc, input, probability),
                )
            }
        }.ifEmpty {
            dtcs.map { dtc ->
                val rule = DiagnosticRuleCatalog.genericRule(dtc)
                RuleEvaluation(
                    rule = rule,
                    dtc = dtc,
                    probabilityPercent = 18,
                    reasoning = "DTC valido sin regla especifica local. Se requiere escaneo, freeze frame y manual del fabricante.",
                )
            }
        }
    }

    private fun adjustProbability(rule: DiagnosticRule, dtc: String, input: DiagnosticReasoningInput): Int {
        var score = rule.baseProbability
        if (input.obdConnected) score += 4 else score -= 7
        if (input.freezeFrame.isNotEmpty()) score += 4
        if (rule.symptomHints.any { hint -> input.symptoms.any { it.contains(hint, ignoreCase = true) } }) score += 6

        when (dtc) {
            "P0230" -> {
                val battery = findValue(input, "BATTERY_VOLTAGE", "0142", "VOLTAGE")
                val fuelPressure = findValue(input, "FUEL_PRESSURE", "FUEL_RAIL_PRESSURE", "0123")
                val pumpVoltage = findManualNumber(input, "pump_voltage", "voltaje bomba", "fuel pump voltage")
                if (battery != null && battery < 12.0 && rule.id in setOf("p0230_relay_fuse", "p0230_ground_power")) score += 8
                if (pumpVoltage != null && pumpVoltage <= 0.5 && rule.id in setOf("p0230_relay_fuse", "p0230_ground_power", "p0230_harness_connector")) score += 10
                if (pumpVoltage != null && pumpVoltage >= 11.0 && fuelPressure != null && fuelPressure < 25.0 && rule.id == "p0230_fuel_pump") score += 16
                if ((pumpVoltage == null || fuelPressure == null) && rule.id == "p0230_fuel_pump") score -= 6
                if (rule.id == "p0230_pcm_control" && input.completedTests.size < 4) score -= 5
            }
            "P0171" -> {
                val ltft = findValue(input, "LTFT", "LTFT_B1", "LONG_FUEL_TRIM_B1", "0107")
                val stft = findValue(input, "STFT", "STFT_B1", "SHORT_FUEL_TRIM_B1", "0106")
                if (ltft != null && ltft > 15.0 && stft != null && stft > 10.0 && rule.id == "p0171_vacuum_leak") score += 12
                if (ltft != null && ltft > 15.0 && rule.id == "p0171_fuel_pressure") score += 4
            }
            "P0172" -> {
                val ltft = findValue(input, "LTFT", "LTFT_B1")
                if (ltft != null && ltft < -12.0 && rule.id == "p0172_rich_condition") score += 10
            }
            "P0420" -> {
                val ltft = findValue(input, "LTFT", "LTFT_B1", "0107")
                val stft = findValue(input, "STFT", "STFT_B1", "0106")
                val trimsNormal = ltft != null && stft != null && abs(ltft) <= 8.0 && abs(stft) <= 8.0
                if (rule.id == "p0420_catalyst" && !trimsNormal) score -= 12
                if (rule.id == "p0420_exhaust_leak" && !trimsNormal) score += 4
            }
            "P0301" -> {
                if (input.completedTests.any { it.contains("bobina", ignoreCase = true) && it.contains("mueve", ignoreCase = true) } && rule.id == "p0301_coil") {
                    score += 20
                }
            }
        }

        if (input.livePids.values.any { it.source == ObdDataSource.SIMULATED_DEMO || it.quality == TelemetryQuality.SIMULATED }) {
            score = (score * 0.55).roundToInt()
        }
        return score.coerceIn(3, 85)
    }

    private fun buildReasoning(
        rule: DiagnosticRule,
        dtc: String,
        input: DiagnosticReasoningInput,
        probability: Int,
    ): String = buildString {
        append("$dtc apunta a ${rule.system}. ")
        append(rule.conditionExpression)
        append(" Probabilidad local: $probability%. ")
        if (!input.obdConnected) append("Sin OBD real, la conclusion queda limitada. ")
        if (rule.requiredEvidence.isNotEmpty()) append("Falta confirmar: ${rule.requiredEvidence.joinToString()}.")
    }
}

class ContradictionDetector {
    fun detect(
        input: DiagnosticReasoningInput,
        hypotheses: List<Hypothesis>,
    ): List<Contradiction> {
        val out = mutableListOf<Contradiction>()
        val fuelPressure = findValue(input, "FUEL_PRESSURE", "FUEL_RAIL_PRESSURE", "0123")
        val pumpVoltage = findManualNumber(input, "pump_voltage", "voltaje bomba", "fuel pump voltage")
        hypotheses.forEach { hypothesis ->
            val title = hypothesis.title.lowercase(Locale.ROOT)
            if (title.contains("bomba combustible") || title.contains("bomba de combustible")) {
                if (fuelPressure != null && fuelPressure >= 35.0) {
                    out += Contradiction(
                        hypothesisId = hypothesis.id,
                        evidence = "Presion combustible ${trimDouble(fuelPressure)}",
                        contradictionReason = "Presion de combustible normal contradice bomba defectuosa como causa principal.",
                        probabilityPenalty = 18,
                    )
                }
                if (pumpVoltage != null && pumpVoltage >= 11.0 && fuelPressure != null && fuelPressure >= 35.0) {
                    out += Contradiction(
                        hypothesisId = hypothesis.id,
                        evidence = "Voltaje y presion normales",
                        contradictionReason = "Voltaje correcto y presion normal bajan prioridad de reemplazo de bomba.",
                        probabilityPenalty = 12,
                    )
                }
            }
            if (title.contains("catalizador")) {
                val ltft = findValue(input, "LTFT", "LTFT_B1", "0107")
                val stft = findValue(input, "STFT", "STFT_B1", "0106")
                if ((ltft != null && abs(ltft) > 12.0) || (stft != null && abs(stft) > 12.0)) {
                    out += Contradiction(
                        hypothesisId = hypothesis.id,
                        evidence = "Fuel trims fuera de rango",
                        contradictionReason = "Fuel trims anormales pueden causar P0420; no condenar catalizador todavia.",
                        probabilityPenalty = 14,
                    )
                }
            }
        }
        return out
    }
}

class AiDiagnosticOrchestrator(
    private val localEngine: DiagnosticReasoningEngine = DiagnosticReasoningEngine(),
) {
    private companion object {
        val REQUIRED_TOP_LEVEL_KEYS = setOf(
            "intent",
            "vehicle_resolution",
            "applicability",
            "summary",
            "assumptions",
            "unknowns",
            "safety",
            "diagnostic_plan",
            "measurements",
            "procedure_id",
            "reference_examples",
            "evidence",
            "confidence",
            "requires_professional",
        )
        val VEHICLE_KEYS = setOf("status", "vehicle_id", "label")
        val SAFETY_KEYS = setOf("risk", "warnings", "blocked_actions")
        val VEHICLE_STATUSES = setOf("RESOLVED", "PARTIAL", "UNRESOLVED")
        val SAFETY_RISKS = setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
        val FULL_VIN_REGEX = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b", RegexOption.IGNORE_CASE)
        val UNSAFE_COMMAND_REGEX = Regex(
            "\\b(sudo|adb|curl|wget|powershell|cmd\\.exe|bash|sh\\s+-c|rm\\s+-rf)\\b",
            RegexOption.IGNORE_CASE,
        )
        val MEASUREMENT_REGEX = Regex(
            "(-?\\d+(?:[.,]\\d+)?)\\s*(v|voltios?|psi|kpa|bar|rpm|%|a|amperios?|ohmios?)" +
                "(?=\\s|$|[.,;:)])",
            RegexOption.IGNORE_CASE,
        )
        val DEFINITIVE_CLAIM_REGEX = Regex(
            "(?<!no )\\b(confirmad[oa]|definitiv[oa]|garantizad[oa]|sin duda)\\b",
            RegexOption.IGNORE_CASE,
        )
    }

    fun orchestrate(
        input: DiagnosticReasoningInput,
        externalJson: String?,
    ): AiDiagnosticOrchestrationResult {
        val local = localEngine.analyze(input)
        val external = validateExternalJson(externalJson, local)
        return AiDiagnosticOrchestrationResult(
            localResult = local,
            externalResponse = external,
            usedFallback = external == null,
            fallbackReason = if (external == null) "Respuesta externa ausente, invalida o insegura; se usa motor local." else null,
        )
    }

    fun validateExternalJson(
        externalJson: String?,
        localResult: DiagnosticReasoningResult,
    ): ValidatedAiDiagnosticResponse? {
        if (externalJson.isNullOrBlank() || externalJson.length > 20_000) return null
        return try {
            val obj = Json.parseToJsonElement(externalJson) as? JsonObject ?: return null
            if (obj.keys != REQUIRED_TOP_LEVEL_KEYS) return null

            val vehicle = parseVehicleResolution(obj["vehicle_resolution"] as? JsonObject) ?: return null
            val safety = parseSafetyAssessment(obj["safety"] as? JsonObject) ?: return null
            val confidence = obj.requiredString("confidence", maxChars = 20)
                ?.let { value ->
                    DiagnosticConfidenceBand.entries.firstOrNull {
                        it.name == value.uppercase(Locale.ROOT)
                    }
                }
                ?: return null
            val procedureElement = obj["procedure_id"] ?: return null
            val procedureId = when (procedureElement) {
                JsonNull -> null
                is JsonPrimitive -> {
                    if (!procedureElement.isString) return null
                    procedureElement.content.trim().takeIf { it.isNotEmpty() && it.length <= 160 }
                        ?: return null
                }
                else -> return null
            }
            val requiresProfessionalElement = obj["requires_professional"] as? JsonPrimitive ?: return null
            if (requiresProfessionalElement.isString) return null
            val requiresProfessional = requiresProfessionalElement.booleanOrNull ?: return null
            val response = ValidatedAiDiagnosticResponse(
                intent = obj.requiredString("intent", maxChars = 30) ?: return null,
                vehicleResolution = vehicle,
                applicability = obj.strictStringList("applicability", maxItems = 12) ?: return null,
                summary = obj.requiredString("summary", maxChars = 1_600) ?: return null,
                assumptions = obj.strictStringList("assumptions", maxItems = 16) ?: return null,
                unknowns = obj.strictStringList("unknowns", maxItems = 16) ?: return null,
                safety = safety,
                diagnosticPlan = obj.strictStringList("diagnostic_plan", maxItems = 16) ?: return null,
                measurements = obj.strictStringList("measurements", maxItems = 24) ?: return null,
                procedureId = procedureId,
                referenceExamples = obj.strictStringList("reference_examples", maxItems = 12) ?: return null,
                evidence = obj.strictStringList("evidence", maxItems = 24) ?: return null,
                confidence = confidence,
                requiresProfessional = requiresProfessional,
            )
            if (isUnsafeExternalResponse(response, localResult)) null else response
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVehicleResolution(obj: JsonObject?): AiVehicleResolution? {
        if (obj == null || !VEHICLE_KEYS.containsAll(obj.keys) || "status" !in obj) return null
        val status = obj.requiredString("status", maxChars = 20)?.uppercase(Locale.ROOT) ?: return null
        if (status !in VEHICLE_STATUSES) return null
        val vehicleId = if ("vehicle_id" in obj) obj.requiredString("vehicle_id", maxChars = 100) ?: return null else null
        val label = if ("label" in obj) obj.requiredString("label", maxChars = 240) ?: return null else null
        return AiVehicleResolution(status = status, vehicleId = vehicleId, label = label)
    }

    private fun parseSafetyAssessment(obj: JsonObject?): AiSafetyAssessment? {
        if (obj == null || obj.keys != SAFETY_KEYS) return null
        val risk = obj.requiredString("risk", maxChars = 20)?.uppercase(Locale.ROOT) ?: return null
        if (risk !in SAFETY_RISKS) return null
        return AiSafetyAssessment(
            risk = risk,
            warnings = obj.strictStringList("warnings", maxItems = 16) ?: return null,
            blockedActions = obj.strictStringList("blocked_actions", maxItems = 16) ?: return null,
        )
    }

    private fun isUnsafeExternalResponse(
        response: ValidatedAiDiagnosticResponse,
        localResult: DiagnosticReasoningResult,
    ): Boolean {
        if (response.intent != "DIAGNOSIS") return true
        if (confidenceRank(response.confidence) > confidenceRank(localResult.confidenceScore.band)) return true

        val textSegments = listOf(
            response.summary,
            response.vehicleResolution.label.orEmpty(),
        ) + response.applicability + response.assumptions + response.unknowns +
            response.safety.warnings + response.safety.blockedActions + response.diagnosticPlan +
            response.measurements + response.referenceExamples + response.evidence
        val text = textSegments.joinToString(" ").lowercase(Locale.ROOT)
        val assertionText = (
            listOf(response.summary) + response.applicability + response.assumptions +
                response.diagnosticPlan + response.measurements + response.referenceExamples
            ).joinToString(" ").lowercase(Locale.ROOT)

        if (FULL_VIN_REGEX.containsMatchIn(text)) return true
        if (UNSAFE_COMMAND_REGEX.containsMatchIn(text)) return true
        if (DEFINITIVE_CLAIM_REGEX.containsMatchIn(assertionText) &&
            localResult.confidenceScore.band != DiagnosticConfidenceBand.HIGH
        ) return true

        val localEvidence = localResult.case.evidenceItems
        if ("tsb" in text && localEvidence.none { it.containsEvidenceTerm("tsb") }) return true
        if (("manual oem" in text || "boletín oem" in text) &&
            localEvidence.none { it.containsEvidenceTerm("oem") }
        ) return true

        val vehicle = response.vehicleResolution
        if (vehicle.vehicleId != null && vehicle.vehicleId != localResult.case.vehicleId) return true
        if (vehicle.label != null && vehicle.label != localResult.aiPayload.vehicleContext) return true
        if (vehicle.status == "RESOLVED" && vehicle.vehicleId == null && vehicle.label == null) return true

        if (response.unknowns.any { unknown ->
                localResult.missingData.none { local -> semanticallyMatches(unknown, local) }
            }
        ) return true

        val allowedPlans = localResult.case.recommendedTests.flatMap { test ->
            listOf(test.id, test.name, test.description)
        } + localResult.nextBestAction
        if (response.diagnosticPlan.any { plan ->
                allowedPlans.none { local -> semanticallyMatches(plan, local) }
            }
        ) return true

        if (response.measurements.any { !measurementIsGrounded(it, localEvidence) }) return true
        if (MEASUREMENT_REGEX.findAll(text).any { !measurementMatchIsGrounded(it, localEvidence) }) return true

        if (response.evidence.any { reference ->
                localEvidence.none { evidence -> evidence.matchesReference(reference) }
            }
        ) return true

        val localKnowledgeEvidence = localEvidence.filter { it.source == EvidenceSource.LOCAL_KNOWLEDGE }
        if (response.referenceExamples.any { reference ->
                localKnowledgeEvidence.none { evidence -> evidence.matchesReference(reference) }
            }
        ) return true

        if (response.procedureId != null) {
            val knownProcedure = localResult.case.recommendedTests.any { it.id == response.procedureId } ||
                localKnowledgeEvidence.any { it.matchesReference(response.procedureId) }
            if (!knownProcedure) return true
        }

        val requiresProfessionalLocally = localResult.partRecommendations.any { it.riskPart } ||
            localResult.case.recommendedTests.any { it.safetyLevel == SafetyLevel.CRITICAL_SYSTEM }
        if (requiresProfessionalLocally && !response.requiresProfessional) return true
        if (response.safety.risk in setOf("HIGH", "CRITICAL") && !response.requiresProfessional) return true

        val blockedParts = localResult.partRecommendations.filter {
            it.state == PartRecommendationState.DO_NOT_BUY_YET
        }
        if (blockedParts.any { part ->
                response.safety.blockedActions.none { action ->
                    action.normalKey().contains(part.partName.normalKey())
                }
            }
        ) return true

        val claimSegments = listOf(response.summary) + response.applicability +
            response.assumptions + response.diagnosticPlan + response.measurements
        if (blockedParts.any { part ->
                claimSegments.any { segment -> replacementClaim(segment, part.partName) }
            }
        ) return true
        return false
    }

    private fun JsonObject.requiredString(key: String, maxChars: Int): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        if (!value.isString) return null
        return value.content.trim().takeIf { it.isNotEmpty() && it.length <= maxChars }
    }

    private fun JsonObject.strictStringList(
        key: String,
        maxItems: Int,
        maxChars: Int = 600,
    ): List<String>? {
        val array = this[key] as? JsonArray ?: return null
        if (array.size > maxItems) return null
        val result = mutableListOf<String>()
        for (element in array) {
            val item = element as? JsonPrimitive ?: return null
            if (!item.isString) return null
            val normalized = item.content.trim()
            if (normalized.isEmpty() || normalized.length > maxChars) return null
            result += normalized
        }
        return result
    }

    private fun confidenceRank(band: DiagnosticConfidenceBand): Int = when (band) {
        DiagnosticConfidenceBand.HIGH -> 3
        DiagnosticConfidenceBand.MEDIUM -> 2
        DiagnosticConfidenceBand.LOW -> 1
        DiagnosticConfidenceBand.INCONCLUSIVE -> 0
    }

    private fun semanticallyMatches(candidate: String, local: String): Boolean {
        val candidateKey = candidate.normalKey()
        val localKey = local.normalKey()
        if (candidateKey.length < 4 || localKey.length < 4) return false
        return candidateKey == localKey || candidateKey.contains(localKey) || localKey.contains(candidateKey)
    }

    private fun measurementIsGrounded(
        measurement: String,
        evidence: List<EvidenceItem>,
    ): Boolean {
        val normalized = measurement.normalKey()
        if (normalized in setOf("dato_no_capturado", "obd_no_disponible", "pendiente_de_validacion")) return true
        val matches = MEASUREMENT_REGEX.findAll(measurement).toList()
        if (matches.isEmpty() || matches.any { !measurementMatchIsGrounded(it, evidence) }) return false
        return evidence.any { item ->
            val label = item.label.normalKey()
            label.length >= 3 && normalized.contains(label)
        }
    }

    private fun measurementMatchIsGrounded(
        match: MatchResult,
        evidence: List<EvidenceItem>,
    ): Boolean {
        val expectedValue = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return false
        val expectedUnit = canonicalUnit(match.groupValues[2])
        return evidence.any { item ->
            val actualValue = item.value.replace(',', '.').toNumberOrNull()
            actualValue != null && abs(actualValue - expectedValue) < 0.0001 &&
                canonicalUnit(item.unit.orEmpty()) == expectedUnit
        }
    }

    private fun canonicalUnit(raw: String): String = when (raw.trim().lowercase(Locale.ROOT)) {
        "v", "voltio", "voltios" -> "v"
        "a", "amperio", "amperios" -> "a"
        "ohmio", "ohmios" -> "ohm"
        else -> raw.trim().lowercase(Locale.ROOT)
    }

    private fun EvidenceItem.matchesReference(reference: String): Boolean {
        val normalized = reference.normalKey()
        return normalized == id.normalKey() || semanticallyMatches(reference, label)
    }

    private fun EvidenceItem.containsEvidenceTerm(term: String): Boolean =
        listOf(label, value, rawPayload.orEmpty()).any { it.contains(term, ignoreCase = true) }

    private fun replacementClaim(segment: String, partName: String): Boolean {
        val normalized = segment.normalKey()
        val part = partName.normalKey()
        val verbs = listOf("cambiar", "reemplazar", "sustituir", "comprar", "instalar")
        return verbs.any { verb -> normalized.contains(verb) && normalized.contains(part) } &&
            !normalized.startsWith("no_") && !normalized.startsWith("evitar_")
    }
}

object AiPayloadSanitizer {
    private val blockedFragments = listOf(
        "ignore previous",
        "ignora las instrucciones",
        "system:",
        "developer:",
        "api key",
        "secret",
        "confirma aunque falte",
    )

    fun sanitizeNote(note: String, maxChars: Int = 600): String {
        val cleaned = note
            .lineSequence()
            .filterNot { line ->
                val lower = line.lowercase(Locale.ROOT)
                blockedFragments.any { lower.contains(it) }
            }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(maxChars)
    }
}

object SymptomBank {
    private val profiles = listOf(
        SymptomProfile("no arranca", listOf("bateria", "arranque", "combustible", "chispa", "inmovilizador", "CKP/CMP", "fusibles/reles", "compresion"), listOf("P0230", "P0335", "P0340", "P0562"), listOf("BATTERY_VOLTAGE", "RPM", "FUEL_PRESSURE"), listOf("Verificar voltaje bateria", "Escuchar bomba al dar contacto", "Medir presion combustible", "Verificar chispa", "Escanear inmovilizador"), listOf("Emergencia No Arranca", "Diagnostico electrico")),
        SymptomProfile("arranca y se apaga", listOf("combustible", "inmovilizador", "IAC/ETC", "MAF/MAP"), listOf("P0505", "P0101", "P0230"), listOf("RPM", "MAF", "MAP"), listOf("Escanear DTC", "Verificar presion combustible", "Revisar cuerpo aceleracion"), listOf("Diagnostico no arranque")),
        SymptomProfile("falla en frio", listOf("ECT", "inyeccion", "admision"), listOf("P0115", "P0128", "P0171"), listOf("ECT", "STFT", "LTFT"), listOf("Comparar ECT con temperatura ambiente", "Revisar fugas vacio"), listOf("Diagnostico sensores motor")),
        SymptomProfile("falla en caliente", listOf("CKP/CMP", "bobina", "combustible"), listOf("P0340", "P0300", "P0301"), listOf("RPM", "FUEL_PRESSURE"), listOf("Capturar falla en caliente", "Probar CKP/CMP"), listOf("Diagnostico fallas intermitentes")),
        SymptomProfile("vibra", listOf("misfire", "soportes", "mezcla"), listOf("P0300", "P0301", "P0171"), listOf("MISFIRE_COUNT", "STFT", "LTFT"), listOf("Contador misfire", "Inspeccion soportes"), listOf("Diagnostico motor")),
        SymptomProfile("consume mucho combustible", listOf("mezcla rica", "O2", "MAF", "inyectores"), listOf("P0172", "P0130", "P0101"), listOf("STFT", "LTFT", "O2"), listOf("Leer fuel trims", "Revisar sensor O2"), listOf("Diagnostico mezcla rica")),
        SymptomProfile("huele a gasolina", listOf("EVAP", "inyectores", "fuga combustible"), listOf("P0440", "P0455", "P0172"), listOf("FUEL_PRESSURE", "EVAP"), listOf("Inspeccion fugas", "Prueba EVAP"), listOf("Revision fugas combustible")),
        SymptomProfile("pierde potencia", listOf("admision", "escape", "combustible", "encendido"), listOf("P0101", "P0171", "P0420", "P0300"), listOf("MAF", "MAP", "FUEL_PRESSURE"), listOf("Leer MAF/MAP", "Prueba carretera segura"), listOf("Diagnostico perdida potencia")),
        SymptomProfile("se calienta", listOf("enfriamiento", "ventiladores", "termostato"), listOf("P0115", "P0128"), listOf("ECT", "FAN_COMMAND"), listOf("Verificar ECT", "Revisar abanicos"), listOf("Revision calentamiento")),
        SymptomProfile("no cambia marcha", listOf("TCM", "range sensor", "ATF"), listOf("P0700", "P0705"), listOf("GEAR", "VSS"), listOf("Escanear TCM", "Revisar nivel ATF"), listOf("Diagnostico caja automatica")),
        SymptomProfile("golpea al cambiar", listOf("TCM", "ATF", "solenoides"), listOf("P0700", "P0705"), listOf("GEAR", "TFT"), listOf("Revisar ATF", "Escanear TCM"), listOf("Diagnostico transmision")),
        SymptomProfile("luz ABS", listOf("ABS", "sensores rueda", "cableado"), listOf("C0035", "C0040"), listOf("WHEEL_SPEED"), listOf("Escaner ABS", "Inspeccion cableado rueda"), listOf("Revision ABS")),
        SymptomProfile("luz bateria", listOf("alternador", "bateria", "cableado"), listOf("P0562"), listOf("BATTERY_VOLTAGE"), listOf("Medir voltaje KOEO/carga", "Prueba alternador"), listOf("Revision alternador")),
        SymptomProfile("luz aceite", listOf("presion aceite", "sensor", "motor"), listOf("P0520"), listOf("OIL_PRESSURE"), listOf("Medir presion aceite real", "No conducir si hay baja presion"), listOf("Revision presion aceite")),
        SymptomProfile("humo blanco", listOf("refrigerante", "empaque culata", "condensacion"), listOf("P0300"), listOf("ECT"), listOf("Revisar coolant", "Prueba compresion"), listOf("Revision motor")),
        SymptomProfile("humo azul", listOf("aceite", "sellos", "anillos"), listOf("P0420"), listOf("O2", "CAT_TEMP"), listOf("Revisar consumo aceite", "Prueba compresion"), listOf("Revision consumo aceite")),
        SymptomProfile("humo negro", listOf("mezcla rica", "MAF", "inyectores"), listOf("P0172", "P0101"), listOf("STFT", "LTFT", "MAF"), listOf("Leer fuel trims", "Revisar MAF"), listOf("Diagnostico mezcla rica")),
        SymptomProfile("ralenti inestable", listOf("vacio", "IAC/ETC", "MAF", "misfire"), listOf("P0505", "P0171", "P0300"), listOf("RPM", "STFT", "MAF"), listOf("Inspeccion fugas", "Revisar IAC/ETC"), listOf("Diagnostico ralenti")),
        SymptomProfile("se apaga al frenar", listOf("vacio", "booster", "IAC", "carga electrica"), listOf("P0505", "P0171", "P0562"), listOf("RPM", "MAP", "BATTERY_VOLTAGE"), listOf("Probar booster", "Leer MAP/RPM"), listOf("Diagnostico ralenti")),
        SymptomProfile("jalonea", listOf("encendido", "combustible", "transmision"), listOf("P0300", "P0171", "P0700"), listOf("MISFIRE_COUNT", "FUEL_PRESSURE"), listOf("Contador misfire", "Prueba carretera segura"), listOf("Diagnostico jaloneo")),
        SymptomProfile("tarda en encender", listOf("combustible", "bateria", "CKP", "inyeccion"), listOf("P0230", "P0562", "P0340"), listOf("BATTERY_VOLTAGE", "FUEL_PRESSURE", "RPM"), listOf("Medir presion residual", "Probar bateria"), listOf("Diagnostico arranque prolongado")),
        SymptomProfile("ventiladores no activan", listOf("ventilador", "rele", "ECT", "ECU"), listOf("P0115", "P0480"), listOf("ECT", "FAN_COMMAND"), listOf("Activar abanico con scanner", "Revisar rele/fusible"), listOf("Revision abanicos")),
    )

    fun match(symptoms: List<String>): List<SymptomProfile> {
        val text = symptoms.joinToString(" ").lowercase(Locale.ROOT)
        return profiles.filter { profile -> text.contains(profile.symptom.lowercase(Locale.ROOT)) }
    }

    fun all(): List<SymptomProfile> = profiles
}

private object DiagnosticRuleCatalog {
    val rules: List<DiagnosticRule> = listOf(
        rule("p0230_relay_fuse", "P0230", "Circuito primario bomba combustible", "DTC P0230 con no-start prioriza alimentacion primaria antes de bomba.", "Circuito rele/fusible bomba combustible", "rele/fusible bomba", 38, DiagnosticSeverity.HIGH, listOf("fusible", "rele", "voltaje bomba"), p0230Tests(), listOf("Combustible: evitar chispas y pruebas inseguras."), listOf("electrical_electronic_revision_reles"), listOf("rele bomba", "fusible bomba"), "Puede causar no arranque e inmovilizar el vehiculo.", 3000, 35000, 20, 70, listOf("no arranca"), listOf("BATTERY_VOLTAGE"), listOf("fuel_pump_relay", "fuel_pump_fuse")),
        rule("p0230_ground_power", "P0230", "Alimentacion/masa bomba combustible", "Sin medicion de tierra/voltaje no se puede condenar bomba.", "Tierra o alimentacion defectuosa", "masa/alimentacion bomba", 24, DiagnosticSeverity.HIGH, listOf("voltaje bateria", "caida tierra"), p0230Tests(), listOf("Usar multimetro/back-probe seguro."), listOf("electrical_electronic_revision_tierras_masas"), listOf("arnes", "terminales"), "Falla intermitente puede apagar motor o impedir arranque.", 5000, 50000, 30, 90, listOf("no arranca"), listOf("BATTERY_VOLTAGE"), listOf("fuel_pump_harness", "fuel_pump_ground")),
        rule("p0230_harness_connector", "P0230", "Arnes/conector bomba", "Conector sulfatado o cable abierto dispara circuito primario.", "Conector/arnes sulfatado o abierto", "arnes/conector", 17, DiagnosticSeverity.MEDIUM, listOf("continuidad", "inspeccion conector"), p0230Tests(), listOf("Desconectar bateria si se manipula arnes."), listOf("electrical_electronic_reparacion_conectores"), listOf("conector", "terminales"), "Puede volver la falla intermitente y dificil de reproducir.", 3000, 45000, 30, 100, listOf("no arranca"), listOf("BATTERY_VOLTAGE"), listOf("fuel_pump_connector")),
        rule("p0230_fuel_pump", "P0230", "Bomba combustible", "La bomba solo sube si hay voltaje/tierra correctos y presion baja.", "Bomba combustible defectuosa", "bomba combustible", 14, DiagnosticSeverity.HIGH, listOf("voltaje bomba correcto", "presion combustible baja"), p0230Tests(), listOf("No comprar bomba antes de voltaje/tierra/presion."), listOf("engine_prueba_presion_combustible"), listOf("bomba combustible"), "No-start persistente y posible dano por mezcla pobre.", 50000, 250000, 60, 180, listOf("no arranca"), listOf("FUEL_PRESSURE"), listOf("fuel_pump_assembly")),
        rule("p0230_pcm_control", "P0230", "PCM/control bomba", "PCM es ultima opcion despues de rele, fusible, arnes y bomba.", "PCM/control de bomba", "PCM/driver bomba", 7, DiagnosticSeverity.CRITICAL, listOf("senal comando PCM", "diagrama electrico"), p0230Tests(), listOf("Programacion ECU requiere tecnico calificado."), listOf("diagnostic_diagnostico_avanzado_con_freeze_frame"), listOf("PCM"), "Puede inmovilizar el vehiculo si se diagnostica mal.", 80000, 500000, 90, 240, emptyList(), listOf("BATTERY_VOLTAGE"), listOf("pcm")),
        rule("p0171_vacuum_leak", "P0171", "Mezcla pobre banco 1", "LTFT/STFT altos priorizan aire no medido.", "Fuga de vacio o admision", "admision/vacio", 34, DiagnosticSeverity.MEDIUM, listOf("LTFT/STFT", "prueba humo"), trimTests(), emptyList(), listOf("engine_revision_mezcla_rica_pobre"), listOf("manguera vacio"), "Mezcla pobre puede danar catalizador o causar misfire.", 5000, 70000, 30, 90, listOf("ralenti inestable"), listOf("LTFT", "STFT", "MAF"), listOf("intake_hose", "vacuum_line")),
        rule("p0171_maf", "P0171", "Medicion aire", "MAF bajo o contaminado puede simular mezcla pobre.", "MAF sucio/defectuoso o fuga admision", "sensor MAF/admisión", 24, DiagnosticSeverity.MEDIUM, listOf("MAF/MAP", "fuel trims"), trimTests(), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("MAF"), "Puede aumentar consumo y contaminar catalizador.", 12000, 90000, 20, 70, listOf("pierde potencia"), listOf("MAF", "MAP"), listOf("maf_sensor")),
        rule("p0171_fuel_pressure", "P0171", "Combustible pobre", "Presion baja puede causar mezcla pobre real.", "Presion de combustible baja", "combustible", 16, DiagnosticSeverity.HIGH, listOf("presion combustible"), fuelPressureTests(), emptyList(), listOf("engine_prueba_presion_combustible"), listOf("filtro/bomba"), "Riesgo de misfire y calentamiento catalizador.", 25000, 250000, 45, 140, listOf("pierde potencia"), listOf("FUEL_PRESSURE"), listOf("fuel_pressure_regulator", "fuel_pump_assembly")),
        rule("p0172_rich_condition", "P0172", "Mezcla rica banco 1", "Fuel trims negativos indican exceso de combustible o aire mal medido.", "Mezcla rica por inyector/MAF/purga EVAP", "inyeccion/MAF/EVAP", 34, DiagnosticSeverity.MEDIUM, listOf("LTFT/STFT", "O2", "MAF"), trimTests(), emptyList(), listOf("engine_revision_mezcla_rica_pobre"), listOf("MAF", "inyector"), "Puede danar catalizador y aumentar consumo.", 8000, 180000, 45, 140, listOf("humo negro"), listOf("LTFT", "STFT", "O2"), listOf("injector", "maf_sensor")),
        rule("p0300_random_misfire", "P0300", "Misfire aleatorio", "Misfire aleatorio requiere chispa, combustible, aire y compresion.", "Misfire multiple/aleatorio", "encendido/combustion", 35, DiagnosticSeverity.HIGH, listOf("contador misfire", "bujias/bobinas"), misfireTests(), listOf("No conducir si catalizador parpadea."), listOf("engine_revision_bobinas_bujias"), listOf("bujias", "bobinas"), "Puede danar catalizador rapidamente.", 15000, 250000, 40, 180, listOf("vibra", "jalonea"), listOf("MISFIRE_COUNT"), listOf("spark_plugs", "ignition_coils")),
        rule("p0301_spark", "P0301", "Misfire cilindro 1", "Primero probar bujia/bobina antes de inyector o compresion.", "Bujia cilindro 1 desgastada", "encendido cilindro 1", 32, DiagnosticSeverity.HIGH, listOf("bujia", "misfire cilindro 1"), misfireTests(), listOf("Catalizador en riesgo si parpadea MIL."), listOf("engine_revision_bobinas_bujias"), listOf("bujia"), "Puede danar catalizador y bobina.", 5000, 40000, 25, 80, listOf("vibra"), listOf("MISFIRE_COUNT"), listOf("spark_plug_cyl_1")),
        rule("p0301_coil", "P0301", "Misfire cilindro 1", "Si al intercambiar bobina se mueve el fallo, la bobina sube fuerte.", "Bobina cilindro 1 defectuosa", "bobina cilindro 1", 25, DiagnosticSeverity.HIGH, listOf("swap bobina"), misfireTests(), listOf("Apagar motor si misfire severo."), listOf("engine_revision_bobinas_bujias"), listOf("bobina"), "Puede danar catalizador rapidamente.", 12000, 90000, 25, 80, listOf("vibra"), listOf("MISFIRE_COUNT"), listOf("ignition_coil_cyl_1")),
        rule("p0420_catalyst", "P0420", "Eficiencia catalizador", "P0420 solo no condena catalizador; primero fuel trims, O2 y fugas.", "Catalizador degradado", "catalizador", 34, DiagnosticSeverity.MEDIUM, listOf("fuel trims normales", "O2 upstream/downstream", "fugas escape"), catalystTests(), emptyList(), listOf("diagnostic_diagnostico_avanzado_con_freeze_frame"), listOf("catalizador"), "Puede fallar emisiones; alto costo si se reemplaza sin causa raiz.", 120000, 650000, 70, 180, listOf("pierde potencia"), listOf("LTFT", "STFT", "O2"), listOf("catalytic_converter")),
        rule("p0420_o2", "P0420", "Sensores O2/escape", "Sensor O2 lento o fuga pueden imitar catalizador.", "Sensor O2 lento o fuga escape", "O2/escape", 26, DiagnosticSeverity.MEDIUM, listOf("respuesta O2", "inspeccion escape"), catalystTests(), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("sensor O2", "junta escape"), "Puede causar diagnostico falso de catalizador.", 15000, 160000, 45, 120, emptyList(), listOf("O2"), listOf("oxygen_sensor_downstream")),
        rule("p0101_maf_range", "P0101", "MAF rango/rendimiento", "MAF fuera de rango exige revisar admision y conectores antes de reemplazo.", "MAF sucio/fuga admision/conector", "sensor MAF", 36, DiagnosticSeverity.MEDIUM, listOf("MAF", "MAP", "fugas admision"), sensorTests("MAF"), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("MAF"), "Puede causar mezcla incorrecta y perdida potencia.", 8000, 90000, 25, 90, listOf("pierde potencia"), listOf("MAF", "MAP"), listOf("maf_sensor")),
        rule("p0115_ect", "P0115", "Sensor temperatura refrigerante", "ECT invalido altera mezcla, abanicos y arranque.", "Sensor ECT/cableado", "ECT/cooling", 36, DiagnosticSeverity.MEDIUM, listOf("ECT ambiente", "resistencia sensor"), sensorTests("ECT"), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("sensor ECT"), "Puede causar sobrecalentamiento o mezcla erronea.", 8000, 65000, 25, 80, listOf("se calienta"), listOf("ECT"), listOf("ect_sensor")),
        rule("p0128_thermostat", "P0128", "Temperatura bajo regulacion", "Motor no alcanza temperatura: validar ECT real antes de termostato.", "Termostato abierto o ECT erroneo", "cooling/termostato", 38, DiagnosticSeverity.MEDIUM, listOf("ECT real", "temperatura mangueras"), sensorTests("ECT"), emptyList(), listOf("engine_revision_calentamiento"), listOf("termostato", "ECT"), "Consumo alto y desgaste por temperatura baja.", 12000, 90000, 45, 120, listOf("falla en frio"), listOf("ECT"), listOf("thermostat", "ect_sensor")),
        rule("p0130_o2_circuit", "P0130", "Sensor O2 circuito banco 1 sensor 1", "O2 circuito puede ser sensor, cableado o fuga.", "Circuito sensor O2 B1S1", "O2/cableado", 34, DiagnosticSeverity.MEDIUM, listOf("voltaje O2", "heater", "cableado"), sensorTests("O2"), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("sensor O2"), "Mezcla puede quedar inestable y danar catalizador.", 15000, 120000, 40, 110, emptyList(), listOf("O2"), listOf("oxygen_sensor_upstream")),
        rule("p0133_o2_slow", "P0133", "Sensor O2 respuesta lenta", "Respuesta lenta no siempre es sensor; revisar fugas y mezcla.", "O2 lento / fuga escape / mezcla", "O2/escape", 32, DiagnosticSeverity.MEDIUM, listOf("grafica O2", "fuel trims"), sensorTests("O2"), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("sensor O2"), "Puede aumentar consumo y fallar emisiones.", 15000, 120000, 40, 110, emptyList(), listOf("O2", "LTFT"), listOf("oxygen_sensor_upstream")),
        rule("p0440_evap", "P0440", "EVAP", "EVAP generico: iniciar por tapa, mangueras y prueba humo.", "Fuga/sistema EVAP", "EVAP", 30, DiagnosticSeverity.LOW, listOf("tapa combustible", "prueba humo EVAP"), evapTests(), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("tapa combustible", "manguera EVAP"), "Emisiones y olor combustible.", 3000, 90000, 20, 90, listOf("huele a gasolina"), listOf("EVAP"), listOf("gas_cap", "evap_hose")),
        rule("p0455_evap_large", "P0455", "EVAP fuga grande", "Fuga grande EVAP suele ser tapa/manguera desconectada.", "Fuga grande EVAP", "EVAP", 34, DiagnosticSeverity.LOW, listOf("tapa combustible", "prueba humo"), evapTests(), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("tapa combustible"), "Olor combustible y emisiones.", 3000, 90000, 20, 90, listOf("huele a gasolina"), listOf("EVAP"), listOf("gas_cap", "evap_hose")),
        rule("p0700_tcm", "P0700", "Control transmision", "P0700 solo indica que TCM tiene codigos; leer TCM antes de reparar.", "TCM solicita MIL", "TCM/transmision", 28, DiagnosticSeverity.HIGH, listOf("codigos TCM"), transmissionTests(), listOf("No reparar caja sin codigos TCM."), listOf("transmission_diagnostico_caja_automatica"), listOf("sensor/solenoide segun TCM"), "Puede afectar cambios y seguridad de manejo.", 25000, 400000, 60, 220, listOf("no cambia marcha"), listOf("GEAR", "TFT"), listOf("tcm")),
        rule("p0705_range", "P0705", "Sensor rango transmision", "Sensor range/neutral safety afecta arranque y cambios.", "Sensor rango PRNDL/cableado", "range sensor", 34, DiagnosticSeverity.HIGH, listOf("posicion PRNDL", "continuidad"), transmissionTests(), listOf("Vehiculo puede arrancar en marcha si se manipula mal."), listOf("transmission_revision_sensores_velocidad"), listOf("sensor rango"), "Riesgo de no arranque o cambio incorrecto.", 25000, 180000, 60, 150, listOf("no cambia marcha"), listOf("GEAR"), listOf("transmission_range_sensor")),
        rule("p1709_range", "P1709", "Switch embrague/rango", "P1709 requiere identificar fabricante y circuito exacto.", "Switch rango/embrague/cableado", "range/clutch switch", 26, DiagnosticSeverity.MEDIUM, listOf("manual local", "continuidad switch"), transmissionTests(), listOf("Validar aplicacion por fabricante."), listOf("transmission_diagnostico_caja_automatica"), listOf("switch"), "Puede impedir arranque o cambios correctos.", 15000, 120000, 45, 120, emptyList(), listOf("GEAR"), listOf("clutch_switch")),
        rule("p0562_low_voltage", "P0562", "Voltaje sistema bajo", "Bajo voltaje puede generar falsos DTCs; corregir carga primero.", "Bateria/alternador/cableado bajo voltaje", "sistema carga", 42, DiagnosticSeverity.HIGH, listOf("voltaje KOEO", "voltaje carga", "caidas cable"), chargingTests(), listOf("No seguir diagnostico fino con voltaje bajo."), listOf("electrical_electronic_revision_alternador"), listOf("bateria", "alternador"), "Puede apagar modulos y causar no arranque.", 20000, 180000, 30, 120, listOf("luz bateria"), listOf("BATTERY_VOLTAGE"), listOf("battery", "alternator")),
        rule("p0505_idle", "P0505", "Control ralenti", "Ralentí inestable requiere IAC/ETC, fugas y carbon antes de piezas.", "IAC/ETC sucio o fuga vacio", "ralenti/admision", 34, DiagnosticSeverity.MEDIUM, listOf("RPM", "IAC/ETC", "fugas"), idleTests(), emptyList(), listOf("engine_revision_sistema_admision_escape"), listOf("IAC", "cuerpo aceleracion"), "Puede apagarse en alto o al frenar.", 12000, 130000, 35, 120, listOf("ralenti inestable", "se apaga al frenar"), listOf("RPM", "MAP"), listOf("throttle_body")),
        rule("p0401_egr", "P0401", "Flujo EGR insuficiente", "EGR bajo flujo puede ser carbon, valvula, solenoide o conducto.", "EGR flujo insuficiente", "EGR", 32, DiagnosticSeverity.MEDIUM, listOf("comando EGR", "MAP al activar EGR"), egrTests(), emptyList(), listOf("diagnostic_diagnostico_de_sensores"), listOf("EGR"), "Puede causar NOx alto y ping/detonacion.", 15000, 160000, 45, 140, emptyList(), listOf("EGR", "MAP"), listOf("egr_valve")),
        rule("p0340_cmp", "P0340", "Sensor arbol levas", "CMP sin senal requiere alimentacion, tierra, senal y correlacion CKP.", "Sensor CMP/cableado/sincronizacion", "CMP/CKP", 36, DiagnosticSeverity.HIGH, listOf("senal CMP", "alimentacion/tierra", "sincronizacion"), sensorTests("CMP"), listOf("No arrancar repetidamente si hay riesgo de faja/cadena."), listOf("engine_revision_sensores_motor"), listOf("sensor CMP"), "Puede causar no arranque o apagado.", 18000, 180000, 45, 140, listOf("no arranca", "tarda en encender"), listOf("RPM"), listOf("camshaft_position_sensor")),
    )

    fun genericRule(dtc: String): DiagnosticRule = rule(
        id = "generic_${slug(dtc)}",
        dtc = dtc,
        system = "Sistema relacionado",
        condition = "No hay regla local especifica para este codigo.",
        hypothesis = "Causa pendiente de identificar para $dtc",
        component = "desconocido",
        probability = 18,
        severity = DiagnosticSeverity.MEDIUM,
        requiredEvidence = listOf("freeze frame", "live PIDs", "manual local"),
        tests = listOf(
            RecommendedTestTemplate(
                id = "generic_scan",
                name = "Escaneo completo y freeze frame",
                description = "Capturar DTCs confirmados, pendientes, permanentes y freeze frame.",
                toolRequired = "OBD-II scanner",
                difficulty = TestDifficulty.EASY,
                safetyLevel = SafetyLevel.NORMAL,
                expectedResult = "Lista completa de DTCs y contexto.",
                nextIfPass = "Aplicar regla especifica o manual local.",
                nextIfFail = "Revisar conexion OBD.",
            )
        ),
        safetyNotes = listOf("No emitir conclusion fuerte sin datos."),
        services = listOf("diagnostic_diagnostico_obd_ii_basico"),
        parts = emptyList(),
        risk = "Riesgo desconocido hasta completar diagnostico.",
        costMin = 0,
        costMax = 50000,
        laborMin = 30,
        laborMax = 90,
    )

    private fun rule(
        id: String,
        dtc: String,
        system: String,
        condition: String,
        hypothesis: String,
        component: String,
        probability: Int,
        severity: DiagnosticSeverity,
        requiredEvidence: List<String>,
        tests: List<RecommendedTestTemplate>,
        safetyNotes: List<String>,
        services: List<String>,
        parts: List<String>,
        risk: String,
        costMin: Int,
        costMax: Int,
        laborMin: Int,
        laborMax: Int,
        symptoms: List<String> = emptyList(),
        pids: List<String> = emptyList(),
        components3d: List<String> = emptyList(),
    ) = DiagnosticRule(
        id = id,
        dtcPattern = dtc,
        system = system,
        conditionExpression = condition,
        hypothesis = hypothesis,
        component = component,
        baseProbability = probability,
        severity = severity,
        requiredEvidence = requiredEvidence,
        recommendedTests = tests,
        safetyNotes = safetyNotes,
        relatedServices = services,
        relatedParts = parts,
        riskIfIgnored = risk,
        estimatedCostMin = costMin,
        estimatedCostMax = costMax,
        laborTimeMin = laborMin,
        laborTimeMax = laborMax,
        symptomHints = symptoms,
        pids = pids,
        components3d = components3d,
    )
}

private fun p0230Tests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("fuse_check", "Verificar fusible bomba", "Comprobar continuidad y alimentacion del fusible de bomba.", "Multimetro/probador fusibles", TestDifficulty.EASY, SafetyLevel.CAUTION, "Fusible con continuidad y alimentacion correcta.", "Continuar con rele/voltaje.", "Corregir fusible/corto antes de comprar bomba."),
    RecommendedTestTemplate("relay_check", "Verificar rele bomba", "Activar o intercambiar rele compatible y medir salida.", "Multimetro/probador rele", TestDifficulty.EASY, SafetyLevel.CAUTION, "Rele conmuta y entrega voltaje.", "Medir voltaje en conector bomba.", "Reparar rele/control."),
    RecommendedTestTemplate("pump_voltage_check", "Medir voltaje en conector bomba", "Back-probe seguro durante KOEO/crank segun manual.", "Multimetro/back probes", TestDifficulty.MEDIUM, SafetyLevel.CRITICAL_SYSTEM, "Voltaje cercano a bateria cuando se comanda bomba.", "Medir presion combustible.", "Seguir circuito alimentacion/arnes."),
    RecommendedTestTemplate("ground_voltage_drop", "Medir tierra/caida de voltaje", "Verificar masa de bomba bajo carga.", "Multimetro", TestDifficulty.MEDIUM, SafetyLevel.CRITICAL_SYSTEM, "Caida de voltaje baja y continuidad estable.", "Continuar con presion.", "Reparar tierra/arnes."),
    RecommendedTestTemplate("fuel_pressure_check", "Medir presion combustible", "Usar manometro compatible y seguridad contra fugas.", "Manometro combustible", TestDifficulty.MEDIUM, SafetyLevel.CRITICAL_SYSTEM, "Presion dentro de especificacion del vehiculo.", "No comprar bomba; buscar otra causa.", "Con voltaje/tierra correctos, bomba/filtro/regulador suben prioridad."),
)

private fun trimTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("fuel_trim_check", "Leer LTFT/STFT", "Registrar trims en ralenti y 2500 rpm.", "Scanner live data", TestDifficulty.EASY, SafetyLevel.NORMAL, "Trims cercanos a rango normal generico.", "Buscar otras causas.", "Priorizar fugas/MAF/combustible."),
    RecommendedTestTemplate("smoke_test", "Prueba de humo admision", "Buscar aire no medido en mangueras/juntas.", "Maquina humo", TestDifficulty.MEDIUM, SafetyLevel.NORMAL, "Sin fugas visibles.", "Verificar MAF/combustible.", "Reparar fuga antes de piezas."),
)

private fun fuelPressureTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("fuel_pressure_check", "Medir presion combustible", "Comparar presion con especificacion del vehiculo.", "Manometro combustible", TestDifficulty.MEDIUM, SafetyLevel.CRITICAL_SYSTEM, "Presion estable en rango.", "Buscar admision/sensores.", "Investigar filtro/regulador/bomba."),
)

private fun misfireTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("spark_plug_inspection", "Inspeccionar bujia", "Revisar desgaste, gap y contaminacion.", "Llave bujias/calibrador", TestDifficulty.EASY, SafetyLevel.NORMAL, "Bujia dentro de especificacion.", "Intercambiar bobina.", "Corregir bujia/gap."),
    RecommendedTestTemplate("coil_swap_test", "Intercambiar bobina", "Mover bobina a otro cilindro y observar si se mueve el misfire.", "Scanner + herramienta manual", TestDifficulty.EASY, SafetyLevel.NORMAL, "Misfire no se mueve.", "Probar inyector/compresion.", "Bobina probable, confirmar pieza."),
    RecommendedTestTemplate("compression_test", "Prueba compresion", "Medir compresion cilindros comparativa.", "Compresometro", TestDifficulty.HARD, SafetyLevel.CAUTION, "Compresion pareja.", "Buscar inyector/sensores.", "Diagnostico mecanico interno."),
)

private fun catalystTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("fuel_trim_check", "Confirmar fuel trims normales", "Leer LTFT/STFT antes de juzgar catalizador.", "Scanner live data", TestDifficulty.EASY, SafetyLevel.NORMAL, "Fuel trims normales.", "Comparar O2 upstream/downstream.", "Corregir mezcla antes de catalizador."),
    RecommendedTestTemplate("o2_waveform_check", "Comparar O2 upstream/downstream", "Observar actividad de sensores O2.", "Scanner/osciloscopio", TestDifficulty.MEDIUM, SafetyLevel.NORMAL, "Downstream estable comparado con upstream.", "Inspeccionar catalizador/fugas.", "Investigar sensor/fuga/catalizador."),
    RecommendedTestTemplate("exhaust_leak_check", "Inspeccionar fuga escape", "Buscar fugas antes de catalizador.", "Inspeccion/humo", TestDifficulty.MEDIUM, SafetyLevel.CAUTION, "Sin fugas pre-cat.", "Continuar evaluacion.", "Reparar fuga antes de catalizador."),
)

private fun sensorTests(sensor: String): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("${slug(sensor)}_signal_check", "Verificar senal $sensor", "Comparar lectura live con condicion fisica esperada.", "Scanner/multimetro", TestDifficulty.MEDIUM, SafetyLevel.NORMAL, "Senal plausible y estable.", "Buscar causas relacionadas.", "Revisar cableado/sensor."),
    RecommendedTestTemplate("${slug(sensor)}_wiring_check", "Verificar alimentacion/tierra $sensor", "Medir referencia, tierra y continuidad.", "Multimetro", TestDifficulty.MEDIUM, SafetyLevel.CAUTION, "Alimentacion y tierra correctas.", "Evaluar sensor.", "Reparar cableado antes de sensor."),
)

private fun evapTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("gas_cap_check", "Revisar tapa combustible", "Inspeccionar sello y cierre.", "Inspeccion visual", TestDifficulty.EASY, SafetyLevel.NORMAL, "Tapa sella correctamente.", "Prueba humo EVAP.", "Reemplazar/ajustar tapa si aplica."),
    RecommendedTestTemplate("evap_smoke_test", "Prueba humo EVAP", "Buscar fugas en mangueras/canister.", "Maquina humo EVAP", TestDifficulty.MEDIUM, SafetyLevel.CAUTION, "Sin fugas.", "Revisar valvulas EVAP.", "Reparar fuga."),
)

private fun transmissionTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("tcm_scan", "Leer codigos TCM", "Escanear modulo transmision, no solo motor.", "Scanner con TCM", TestDifficulty.MEDIUM, SafetyLevel.CAUTION, "TCM entrega codigos especificos.", "Seguir codigo TCM.", "Revisar acceso/comunicacion TCM."),
    RecommendedTestTemplate("atf_level_check", "Revisar nivel/estado ATF", "Verificar ATF segun procedimiento del fabricante.", "Herramienta ATF", TestDifficulty.MEDIUM, SafetyLevel.CAUTION, "Nivel y estado correctos.", "Pruebas electricas/sensores.", "Corregir ATF si aplica."),
)

private fun chargingTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("battery_voltage_check", "Medir voltaje bateria", "Medir KOEO y con motor encendido.", "Multimetro", TestDifficulty.EASY, SafetyLevel.NORMAL, "Voltaje estable en rango.", "Continuar diagnostico DTC.", "Reparar bateria/carga primero."),
    RecommendedTestTemplate("alternator_output_check", "Probar alternador", "Medir salida y caida en cables.", "Multimetro/pinza", TestDifficulty.MEDIUM, SafetyLevel.CAUTION, "Carga correcta bajo demanda.", "Buscar consumo parasitario.", "Reparar alternador/cableado."),
)

private fun idleTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("idle_air_check", "Revisar control ralenti/admisión", "Inspeccionar IAC/ETC, carbon y fugas.", "Scanner/inspeccion", TestDifficulty.MEDIUM, SafetyLevel.NORMAL, "RPM responde al comando.", "Buscar sensores.", "Limpiar/reparar control ralenti."),
)

private fun egrTests(): List<RecommendedTestTemplate> = listOf(
    RecommendedTestTemplate("egr_command_check", "Activar EGR y observar MAP", "Comandar EGR si el vehiculo lo permite.", "Scanner bidireccional", TestDifficulty.MEDIUM, SafetyLevel.CAUTION, "MAP cambia al activar EGR.", "Revisar sensores.", "Limpiar/reparar EGR/conducto."),
)

private fun evidenceSourceFor(sample: TelemetrySample): EvidenceSource = when (sample.source) {
    ObdDataSource.REAL_OBD -> EvidenceSource.LIVE_PID
    ObdDataSource.OFFLINE_KNOWLEDGE -> EvidenceSource.LOCAL_KNOWLEDGE
    ObdDataSource.SIMULATED_DEMO -> EvidenceSource.SIMULATED
    ObdDataSource.MANUAL_INPUT -> EvidenceSource.MANUAL_INPUT
    ObdDataSource.NO_REAL_OBD -> EvidenceSource.UNKNOWN
}

private fun confidenceFor(sample: TelemetrySample): EvidenceConfidence = when {
    sample.source == ObdDataSource.SIMULATED_DEMO || sample.quality == TelemetryQuality.SIMULATED -> EvidenceConfidence.WEAK
    sample.quality == TelemetryQuality.VALID && sample.value != null -> EvidenceConfidence.VERIFIED
    sample.quality == TelemetryQuality.STALE -> EvidenceConfidence.MEDIUM
    sample.quality in setOf(TelemetryQuality.TIMEOUT, TelemetryQuality.UNSUPPORTED, TelemetryQuality.PARSE_ERROR) -> EvidenceConfidence.MISSING
    sample.quality == TelemetryQuality.OUT_OF_RANGE -> EvidenceConfidence.STRONG
    else -> EvidenceConfidence.WEAK
}

private fun TelemetrySample.hasRealValue(): Boolean =
    source == ObdDataSource.REAL_OBD && quality == TelemetryQuality.VALID && value != null

private fun findValue(input: DiagnosticReasoningInput, vararg aliases: String): Double? {
    val keys = aliases.map { it.normalKey() }.toSet()
    input.livePids.forEach { (key, sample) ->
        if (key.normalKey() in keys || sample.pid.normalKey() in keys || sample.name.normalKey() in keys) {
            if (sample.hasRealValue()) return sample.value
        }
    }
    return findManualNumber(input, *aliases)
}

private fun findManualNumber(input: DiagnosticReasoningInput, vararg aliases: String): Double? {
    val keys = aliases.map { it.normalKey() }
    input.manualMeasurements.forEach { (key, value) ->
        if (keys.any { key.normalKey().contains(it) || it.contains(key.normalKey()) }) {
            value.toNumberOrNull()?.let { return it }
        }
    }
    return null
}

private fun hasManualOrCompleted(input: DiagnosticReasoningInput, vararg aliases: String): Boolean {
    val keys = aliases.map { it.normalKey() }
    return input.completedTests.any { completed ->
        val normal = completed.normalKey()
        keys.any { normal.contains(it) || it.contains(normal) }
    } || input.manualMeasurements.keys.any { key ->
        val normal = key.normalKey()
        keys.any { normal.contains(it) || it.contains(normal) }
    }
}

private fun normalizeDtc(raw: String): String? {
    val upper = raw.trim().uppercase(Locale.ROOT)
    return if (Regex("^[PBCU][0-3][0-9A-F]{3}$").matches(upper)) upper else null
}

private fun String.toNumberOrNull(): Double? =
    Regex("-?\\d+(\\.\\d+)?").find(this)?.value?.toDoubleOrNull()

private fun String.normalKey(): String =
    lowercase(Locale.ROOT)
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ñ", "n")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

private fun slug(value: String): String = value.normalKey().ifBlank { "item" }

private fun trimDouble(value: Double): String =
    if (abs(value - value.roundToInt()) < 0.0001) value.roundToInt().toString() else "%.2f".format(Locale.US, value)

private fun Int?.orZero(): Int = this ?: 0

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
