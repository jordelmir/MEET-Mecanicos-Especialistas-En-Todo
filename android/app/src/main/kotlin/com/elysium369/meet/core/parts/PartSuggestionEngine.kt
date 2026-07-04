package com.elysium369.meet.core.parts

/**
 * Part Suggestion Engine — pure, no I/O.
 *
 * Speaks the same language as the TypeScript
 * `lib/parts/part-suggestion.ts`. P0230 specifically:
 *   1. relay / fuse / harness first
 *   2. fuel-pressure sensor next
 *   3. fuel pump LAST, marked riskPart with a verbatim disclaimer
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
)

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
            rationale = "P0230 commonly points to the relay circuit. Cheap to swap.",
        ),
        PartSuggestion(
            partName = "Fusible circuito bomba",
            category = "ELECTRICAL",
            position = PartPosition.FUSE_BOX,
            priority = 2,
            rationale = "Blown fuse accounts for a meaningful slice of P0230 cases.",
        ),
        PartSuggestion(
            partName = "Arnés eléctrico / terminales de bomba",
            category = "ELECTRICAL",
            position = PartPosition.ENGINE,
            priority = 3,
            rationale = "Corroded connectors or broken harness wires trigger P0230.",
        ),
        PartSuggestion(
            partName = "Sensor de presión de combustible",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 4,
            rationale = "Verify FTP sensor is reporting before condemning the pump.",
        ),
        PartSuggestion(
            partName = "Bomba de combustible",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 99,
            rationale = "Final-tier part. Replace only AFTER voltage, ground, relay and " +
                "fuel pressure have been verified with a gauge.",
            disclaimer = "No reemplazar la bomba sin confirmar antes: alimentación, " +
                "tierra, relé/fusible y presión con manómetro.",
            riskPart = true,
        ),
    )

    private val P0420_SUGGESTIONS: List<PartSuggestion> = listOf(
        PartSuggestion(
            partName = "Sensor de oxígeno aguas abajo",
            category = "EXHAUST",
            position = PartPosition.EXHAUST,
            priority = 1,
            rationale = "Downstream O2 sensor drift causes P0420 in older vehicles. Cheap " +
                "first attempt.",
        ),
        PartSuggestion(
            partName = "Junta de escape",
            category = "EXHAUST",
            position = PartPosition.EXHAUST,
            priority = 2,
            rationale = "Exhaust leak ahead of the cat triggers the same code.",
        ),
        PartSuggestion(
            partName = "Catalizador",
            category = "EXHAUST",
            position = PartPosition.CENTER,
            priority = 99,
            rationale = "Last. Confirm wiring and O2 sensors first; cats are expensive.",
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
            rationale = "Worn spark plugs are the most common P0300 cause.",
        ),
        PartSuggestion(
            partName = "Bobina de encendido",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 2,
            rationale = "Failing coil triggers random misfires.",
        ),
        PartSuggestion(
            partName = "Inyector",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 3,
            rationale = "Last: clogged or leaking injector requires diagnostic time.",
        ),
    )

    private val P0171_SUGGESTIONS: List<PartSuggestion> = listOf(
        PartSuggestion(
            partName = "Tapa del depósito de gasolina",
            category = "ENGINE",
            position = PartPosition.NOT_APPLICABLE,
            priority = 1,
            rationale = "Loose / bad gas cap produces P0171 in many vehicles.",
        ),
        PartSuggestion(
            partName = "Manguera de vacío",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 2,
            rationale = "Unmetered air from a cracked hose skews the fuel trim.",
        ),
        PartSuggestion(
            partName = "Sensor MAF",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 3,
            rationale = "Dirty MAF reads low; clean before replacing.",
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
        ),
        "fuel_pump_assembly" to PartSuggestion(
            partName = "Bomba de combustible",
            category = "ENGINE",
            position = PartPosition.ENGINE,
            priority = 99,
            rationale = "Selected from the 3D engine viewer.",
            disclaimer = "Antes de ordenar, verifica alimentación, tierra, relé y presión con manómetro.",
            riskPart = true,
        ),
        "abs_module" to PartSuggestion(
            partName = "Módulo ABS",
            category = "BRAKES",
            position = PartPosition.NOT_APPLICABLE,
            priority = 1,
            rationale = "Selected from the 3D chassis viewer.",
            disclaimer = "Pieza safety-critical: instalación por técnico calificado.",
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
}
