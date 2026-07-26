package com.elysium369.meet.core.parts

import com.elysium369.meet.core.knowledge.graph.RepairKnowledgeBundle

/**
 * Part Suggestion Engine — pure, no I/O.
 *
 * Speaks the same language as the TypeScript
 * `lib/parts/part-suggestion.ts`. P0230 specifically verifies relay, fuse and
 * harness before listing the fuel pump LAST with a verbatim disclaimer.
 * Position never means a part is confirmed bad.
 *
 * Identical ordering to the web side.
 */

data class PartSuggestion(
    val partName: String,
    val category: String,
    val position: PartPosition,
    val priority: Int,
    val rationale: String,
    val disclaimer: String? = null,
    val riskPart: Boolean = false,
    val canonicalKey: String? = null,
    val evidenceState: PartSuggestionEvidenceState = PartSuggestionEvidenceState.INFORMATIONAL,
    val requestAllowed: Boolean = false,
    val missingEvidence: List<String> = emptyList(),
)

enum class PartSuggestionEvidenceState {
    INFORMATIONAL,
    REQUIRES_TESTS,
    REPLACEMENT_CONFIRMED,
    PURCHASE_VERIFIED
}

enum class SuggestionSource { DTC, FROM_3D_COMPONENT, WORK_ORDER, MAINTENANCE_ALERT, PREPURCHASE }

data class PartSuggestionInput(
    val source: SuggestionSource,
    val dtcCodes: List<String> = emptyList(),
    val componentSlug: String? = null,
    val workOrderHint: String? = null,
)

object PartSuggestionEngine {

    private val P0230_SUGGESTIONS: List<PartSuggestion> = listOf(
        PartSuggestion(
            partName = "Relé de bomba de combustible",
            category = "ELECTRICAL",
            position = PartPosition.FUSE_BOX,
            priority = 1,
            rationale = "Verificar el relé y su zócalo antes de atribuir P0230 a la bomba.",
            canonicalKey = "fuel_pump_relay",
        ),
        PartSuggestion(
            partName = "Fusible circuito bomba",
            category = "ELECTRICAL",
            position = PartPosition.FUSE_BOX,
            priority = 2,
            rationale = "Verificar continuidad del fusible y descartar un corto aguas abajo antes de reemplazarlo.",
            canonicalKey = "fuel_pump_fuse",
        ),
        PartSuggestion(
            partName = "Arnés eléctrico / terminales de bomba",
            category = "ELECTRICAL",
            position = PartPosition.ENGINE,
            priority = 3,
            rationale = "Inspeccionar conector y arnés; documentar alimentación, tierra y caída de voltaje bajo carga.",
            canonicalKey = "fuel_pump_harness",
        ),
        PartSuggestion(
            partName = "Bomba de combustible",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 99,
            rationale = "Considerar reemplazo sólo después de verificar batería, fusible, relé, " +
                "arnés, conector, alimentación, tierra, presión y corriente.",
            disclaimer = "No reemplazar la bomba sin confirmar antes: alimentación, " +
                "tierra, relé/fusible y presión con manómetro.",
            riskPart = true,
            canonicalKey = "fuel_pump",
        ),
    )

    private val P0420_SUGGESTIONS: List<PartSuggestion> = listOf(
        PartSuggestion(
            partName = "Sensor de oxígeno aguas abajo",
            category = "EXHAUST",
            position = PartPosition.EXHAUST,
            priority = 1,
            rationale = "Evaluar señal, calentador y cableado del sensor. No sustituirlo sólo por el DTC.",
        ),
        PartSuggestion(
            partName = "Junta de escape",
            category = "EXHAUST",
            position = PartPosition.EXHAUST,
            priority = 2,
            rationale = "Inspeccionar fugas de escape antes del catalizador y confirmar con una prueba física.",
        ),
        PartSuggestion(
            partName = "Catalizador",
            category = "EXHAUST",
            position = PartPosition.CENTER,
            priority = 99,
            rationale = "Considerar reemplazo sólo tras descartar fugas, mezcla incorrecta, " +
                "fallos de encendido y sensores.",
            disclaimer = "Pieza de alto costo; confirmar antes de reemplazar.",
            riskPart = true,
        ),
    )

    private val P0300_SUGGESTIONS: List<PartSuggestion> = listOf(
        PartSuggestion(
            partName = "Bujía",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 1,
            rationale = "Inspeccionar estado, luz y patrón de desgaste; P0300 no confirma una bujía defectuosa.",
        ),
        PartSuggestion(
            partName = "Bobina de encendido",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 2,
            rationale = "Confirmar la bobina mediante intercambio controlado, señal o prueba equivalente antes de reemplazar.",
        ),
        PartSuggestion(
            partName = "Inyector",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 3,
            rationale = "Confirmar balance, control eléctrico y estanqueidad del inyector antes de reemplazar.",
        ),
    )

    private val P0171_SUGGESTIONS: List<PartSuggestion> = listOf(
        PartSuggestion(
            partName = "Manguera de vacío",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 1,
            rationale = "Buscar entrada de aire no medida mediante inspección y prueba de humo cuando corresponda.",
        ),
        PartSuggestion(
            partName = "Ducto o junta de admisión",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 2,
            rationale = "Inspeccionar grietas, uniones y sellos; no reemplazar sin localizar la fuga.",
        ),
        PartSuggestion(
            partName = "Sensor de carga de motor (MAF o MAP según equipamiento)",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 3,
            rationale = "Confirmar qué sensor equipa el vehículo y contrastar su señal antes de intervenir.",
        ),
    )

    private fun suggestionsFor(code: String): List<PartSuggestion> = when (code) {
        "P0230" -> P0230_SUGGESTIONS
        "P0420" -> P0420_SUGGESTIONS
        "P0300" -> P0300_SUGGESTIONS
        "P0171" -> P0171_SUGGESTIONS
        else -> listOf(
            PartSuggestion(
                partName = "Diagnóstico de $code",
                category = "ENGINE",
                position = PartPosition.NOT_APPLICABLE,
                priority = 50,
                rationale = "DTC no tenemos un mapa específico en esta versión. La app " +
                    "mostrará las piezas críticas de seguridad como advertencia.",
            ),
        )
    }

    private val COMPONENT_TO_SUGGESTION: Map<String, PartSuggestion> = mapOf(
        "fuel_pump_relay" to PartSuggestion(
            partName = "Relé de bomba de combustible",
            category = "ELECTRICAL",
            position = PartPosition.FUSE_BOX,
            priority = 1,
            rationale = "Selected from the 3D engine viewer.",
            canonicalKey = "fuel_pump_relay",
        ),
        "fuel_pump_assembly" to PartSuggestion(
            partName = "Bomba de combustible",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 99,
            rationale = "Selected from the 3D engine viewer.",
            disclaimer = "Antes de ordenar, verifica alimentación, tierra, relé y presión con manómetro.",
            riskPart = true,
            canonicalKey = "fuel_pump",
        ),
        "abs_module" to PartSuggestion(
            partName = "Módulo ABS",
            category = "BRAKES",
            position = PartPosition.NOT_APPLICABLE,
            priority = 1,
            rationale = "Selected from the 3D chassis viewer.",
            disclaimer = "Pieza safety-critical: instalación por técnico calificado.",
            canonicalKey = "abs_module",
        ),
    )

    fun suggestParts(input: PartSuggestionInput): List<PartSuggestion> {
        val out = mutableListOf<PartSuggestion>()

        if (input.source == SuggestionSource.DTC) {
            for (code in input.dtcCodes) {
                val upper = code.uppercase().trim()
                out.addAll(suggestionsFor(upper))
            }
        }
        if (input.source == SuggestionSource.FROM_3D_COMPONENT && input.componentSlug != null) {
            COMPONENT_TO_SUGGESTION[input.componentSlug]?.let { out.add(it) }
        }
        if (input.source == SuggestionSource.WORK_ORDER && !input.workOrderHint.isNullOrEmpty()) {
            out.add(
                PartSuggestion(
                    partName = input.workOrderHint,
                    category = "ENGINE",
                    position = PartPosition.NOT_APPLICABLE,
                    priority = 1,
                    rationale = "Pre-llenado por la orden de trabajo del mecánico.",
                ),
            )
        }

        return out.sortedWith(Comparator { a, b ->
            val ap = if (a.riskPart) 1000 else a.priority
            val bp = if (b.riskPart) 1000 else b.priority
            ap - bp
        })
    }

    /**
     * Applies the evidence gate produced by the structured graph.
     *
     * A legacy DTC/name mapping remains educational. It can never open a part request on its
     * own. Only the exact canonical component selected by a graph bundle with verified purchase
     * evidence becomes actionable.
     */
    fun suggestParts(
        input: PartSuggestionInput,
        knowledge: RepairKnowledgeBundle
    ): List<PartSuggestion> {
        val gate = knowledge.partGate
        val missing = buildList {
            addAll(gate.missingEvidence.map { it.name })
            addAll(gate.missingRequirements)
            if (!gate.replacementAllowed) addAll(gate.requiredTests)
        }.distinct().sorted()

        return suggestParts(input).map { suggestion ->
            val isGateTarget =
                !suggestion.canonicalKey.isNullOrBlank() &&
                    suggestion.canonicalKey == gate.componentCanonicalKey
            when {
                isGateTarget && gate.purchaseAllowed &&
                    gate.purchaseCompatibility == CompatibilityConfidence.EXACT ->
                    suggestion.copy(
                        evidenceState = PartSuggestionEvidenceState.PURCHASE_VERIFIED,
                        requestAllowed = true,
                        missingEvidence = emptyList()
                    )
                isGateTarget && gate.replacementAllowed ->
                    suggestion.copy(
                        evidenceState = PartSuggestionEvidenceState.REPLACEMENT_CONFIRMED,
                        requestAllowed = false,
                        missingEvidence = missing
                    )
                isGateTarget ->
                    suggestion.copy(
                        evidenceState = PartSuggestionEvidenceState.REQUIRES_TESTS,
                        requestAllowed = false,
                        missingEvidence = missing
                    )
                else -> suggestion.copy(
                    evidenceState = PartSuggestionEvidenceState.INFORMATIONAL,
                    requestAllowed = false
                )
            }
        }
    }
}
