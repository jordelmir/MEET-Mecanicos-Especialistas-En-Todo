package com.elysium369.meet.ai

import com.elysium369.meet.domain.visualdiagnostics.DiagnosticComponent
import com.elysium369.meet.domain.visualdiagnostics.EngineType
import javax.inject.Inject

class DiagnosticAiContextBuilder @Inject constructor() {
    fun build(
        vehicleLabel: String?,
        engineType: EngineType,
        component: DiagnosticComponent,
        presentationOnlyDtcCodes: Set<String>,
        livePidValues: Map<String, String>
    ): String {
        val relatedDisplayCodes = component.relatedDtcs.map { it.code }
            .filter { presentationOnlyDtcCodes.contains(it) }
        return buildString {
            appendLine("{")
            appendLine("  \"module\": \"visual_3d_diagnostics\",")
            appendLine("  \"authorityPolicy\": \"EXPLAIN_ONLY_NO_DIAGNOSTIC_AUTHORITY\",")
            appendLine("  \"evidenceWarning\": \"DTC strings are presentation-only; no finding or ECU failure may be inferred from them\",")
            appendLine("  \"vehicle\": \"${vehicleLabel.orEmpty().escapeJson()}\",")
            appendLine("  \"engineType\": \"${engineType.name}\",")
            appendLine("  \"component\": \"${component.name.escapeJson()}\",")
            appendLine("  \"componentId\": \"${component.id}\",")
            appendLine("  \"relatedDtcs\": [${component.relatedDtcs.joinToString { "\"${it.code}\"" }}],")
            appendLine("  \"presentationRelatedDtcCodes\": [${relatedDisplayCodes.joinToString { "\"$it\"" }}],")
            appendLine("  \"livePids\": {${livePidValues.entries.joinToString { "\"${it.key}\": \"${it.value.escapeJson()}\"" }}},")
            appendLine("  \"safety\": [${component.safetyWarnings.joinToString { "\"${it.message.escapeJson()}\"" }}],")
            appendLine("  \"recommendedTests\": [${component.workshopTests.joinToString { "\"${it.title.escapeJson()}: ${it.procedure.escapeJson()}\"" }}],")
            appendLine("  \"repairFlow\": [${component.repairFlow.joinToString { "\"${it.order}. ${it.action.escapeJson()}\"" }}]")
            appendLine("}")
        }
    }

    private fun String.escapeJson(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}

interface DiagnosticAiClient {
    suspend fun ask(context: String): Result<String>
}
