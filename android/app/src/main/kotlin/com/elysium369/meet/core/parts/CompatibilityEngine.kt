package com.elysium369.meet.core.parts

/**
 * Compatibility Engine — pure, no I/O.
 *
 * Speaks the same language as the TypeScript `lib/parts/compatibility.ts`
 * so the web wizard and the Android client produce identical verdicts
 * for the same input. Anti-fraud posture is preserved on both sides:
 *   - EXACT is REFUSED unless VIN + (OEM | part number) is present, or
 *     a closed (brand + model + year + engine + OEM) tuple is present.
 *   - P0230 + fuel-pump request ALWAYS carries a BLOCK warning.
 *   - Critical-safety parts (brakes, steering, airbag, fuel system,
 *     high-voltage) ALWAYS trigger an install-by-qualified-tech warning.
 *   - EXACT is downgraded to MEDIUM whenever any BLOCK warning is
 *     present in the result.
 */

enum class CompatibilityConfidence {
    EXACT, HIGH, MEDIUM, LOW, UNKNOWN;

    companion object {
        fun fromString(value: String?): CompatibilityConfidence =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class PartPosition(val label: String) {
    FRONT_RIGHT("FRONT_RIGHT"),
    FRONT_LEFT("FRONT_LEFT"),
    REAR_RIGHT("REAR_RIGHT"),
    REAR_LEFT("REAR_LEFT"),
    CENTER("CENTER"),
    ENGINE("ENGINE"),
    TRANSMISSION("TRANSMISSION"),
    ELECTRICAL("ELECTRICAL"),
    BODY("BODY"),
    INTERIOR("INTERIOR"),
    NOT_APPLICABLE("NOT_APPLICABLE"),
    FUSE_BOX("FUSE_BOX"),
    EXHAUST("EXHAUST");

    companion object {
        fun fromString(value: String?): PartPosition =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NOT_APPLICABLE
    }
}

data class VehicleFingerprint(
    val brand: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val engine: String? = null,
    val transmission: String? = null,
    val fuel: String? = null,
    val vin: String? = null,
    val oemNumber: String? = null,
    val partNumber: String? = null,
)

data class CompatibilityContext(
    val vehicle: VehicleFingerprint,
    val partName: String,
    val category: String? = null,
    val position: PartPosition = PartPosition.NOT_APPLICABLE,
    val dtcCodes: List<String> = emptyList(),
    val from3DComponentSlug: String? = null,
    val photoUrls: List<String> = emptyList(),
)

enum class WarningSeverity { INFO, WARN, BLOCK }

data class CompatibilityWarning(
    val code: String,
    val message: String,
    val severity: WarningSeverity,
)

data class CompatibilityResult(
    val confidence: CompatibilityConfidence,
    val warnings: List<CompatibilityWarning>,
    val requiredConfirmations: List<String>,
    val crossReferenceNumbers: List<String>,
    val recommendedQuestions: List<String>,
    val rationale: List<String>,
)

/* -------------------------------------------------------------------------- */
/*                            Critical-safety keywords                         */
/* -------------------------------------------------------------------------- */
/**
 * Mirrors the TypeScript `SAFETY_KEYWORDS` array in
 * `lib/parts/compatibility.ts`. Identical list, identical case, identical
 * substring matching. Add a new keyword to BOTH files in the same PR.
 */
val SAFETY_KEYWORDS: List<String> = listOf(
    // braking
    "brake", "freno", "pastilla", "pad", "caliper", "disco", "rotor",
    "drum", "tambor", "abs", "cilindro maestro", "master cylinder",
    // steering / suspension
    "steering", "direccion", "suspension", "shock", "amortiguador",
    "strut", "ball joint", "rotula", "tie rod", "terminal",
    // fuel
    "fuel pump", "bomba combustible", "bomba de combustible", "inyector", "injector",
    "fuel rail", "fuel line", "manguera combustible",
    // airbag
    "airbag", "bolsa de aire", "pretensioner", "pretensor",
    // high voltage (hybrids / EV)
    "hybrid battery", "high voltage", "alto voltaje", "hv cable",
    "alta tension", "alta tensión", "bateria alta", "bateria hibrida",
)

fun isCriticalSafetyPart(name: String): Boolean {
    val lower = name.lowercase()
    return SAFETY_KEYWORDS.any { lower.contains(it) }
}

private fun looksLikeFuelPump(partName: String): Boolean {
    val l = partName.lowercase()
    return l.contains("bomba de combustible") ||
        l.contains("bomba combustible") ||
        l.contains("fuel pump") ||
        l.contains("bomba gasolina") ||
        l.contains("gasoline pump")
}

/* -------------------------------------------------------------------------- */
/*                                  Engine                                    */
/* -------------------------------------------------------------------------- */

object CompatibilityEngine {

    private val SAFETY_INSTALL_MESSAGE =
        "Instalación recomendada por técnico calificado. Una pieza incompatible " +
            "puede causar falla mecánica, eléctrica o de seguridad."

    private val P0230_PUMP_MESSAGE =
        "No reemplazar bomba de combustible sin confirmar antes: alimentación " +
            "eléctrica, tierra, integridad de relé y fusible, y presión de " +
            "combustible con manómetro. P0230 también se dispara por fallos en " +
            "cableado o relé, no solo por la bomba."

    private fun noVinWarning(whatWeHave: String) = CompatibilityWarning(
        code = "NO_VIN",
        message = "No se recibió VIN. Continuamos con $whatWeHave. " +
            "Para subir la confianza a EXACT, proporciona el VIN de 17 caracteres.",
        severity = WarningSeverity.WARN,
    )

    private val NO_OEM_WARNING = CompatibilityWarning(
        code = "NO_OEM",
        message = "No se recibió número OEM ni número de parte. La app no puede confirmar " +
            "compatibilidad exacta. Recomendamos adjuntar foto de la pieza, del " +
            "conector o de la caja de fusibles.",
        severity = WarningSeverity.WARN,
    )

    private val NO_PHOTO_WARNING = CompatibilityWarning(
        code = "NO_PHOTO_EVIDENCE",
        message = "Sin foto del repuesto viejo. Recomendamos adjuntar al menos una foto " +
            "para que la repuestera pueda validar referencia y conector.",
        severity = WarningSeverity.INFO,
    )

    private val PART_NAME_AMBIGUOUS_WARNING = CompatibilityWarning(
        code = "PART_NAME_AMBIGUOUS",
        message = "El nombre de la pieza es genérico. Para reducir ambigüedad indica " +
            "posición exacta, OEM o número de parte original.",
        severity = WarningSeverity.WARN,
    )

    private data class TierEvidence(
        val hasVin: Boolean,
        val hasOem: Boolean,
        val hasPartNumber: Boolean,
        val hasBrand: Boolean,
        val hasModel: Boolean,
        val hasYear: Boolean,
        val hasEngine: Boolean,
        val hasTransmission: Boolean,
        val hasPosition: Boolean,
        val hasPhotoEvidence: Boolean,
    )

    private fun collectEvidence(ctx: CompatibilityContext): TierEvidence {
        val v = ctx.vehicle
        return TierEvidence(
            hasVin = !v.vin.isNullOrBlank() && v.vin.trim().length >= 11,
            hasOem = !v.oemNumber.isNullOrBlank(),
            hasPartNumber = !v.partNumber.isNullOrBlank(),
            hasBrand = !v.brand.isNullOrBlank(),
            hasModel = !v.model.isNullOrBlank(),
            hasYear = v.year != null,
            hasEngine = !v.engine.isNullOrBlank(),
            hasTransmission = !v.transmission.isNullOrBlank(),
            hasPosition = ctx.position != PartPosition.NOT_APPLICABLE,
            hasPhotoEvidence = ctx.photoUrls.isNotEmpty(),
        )
    }

    private data class TierResult(
        val confidence: CompatibilityConfidence,
        val requiredConfirmations: List<String>,
        val rationale: List<String>,
    )

    private fun pickTier(ctx: CompatibilityContext, e: TierEvidence): TierResult {
        val rationale = mutableListOf<String>()
        val required = mutableListOf<String>()

        // EXACT: VIN + (OEM | partNumber) OR closed (brand+model+year+engine+OEM).
        if (e.hasVin && (e.hasOem || e.hasPartNumber)) {
            rationale.add("VIN + número de parte disponibles: tupla cerrada.")
            return TierResult(CompatibilityConfidence.EXACT, emptyList(), rationale)
        }
        if (e.hasBrand && e.hasModel && e.hasYear && e.hasEngine && e.hasOem) {
            rationale.add("marca + modelo + año + motorización + OEM: tupla cerrada sin VIN.")
            return TierResult(CompatibilityConfidence.EXACT, emptyList(), rationale)
        }

        // HIGH: brand+model+year+OEM OR brand+model+engine+position+OEM.
        if (e.hasBrand && e.hasModel && e.hasYear && e.hasOem) {
            rationale.add("marca + modelo + año + OEM.")
            return TierResult(
                CompatibilityConfidence.HIGH,
                if (e.hasVin) emptyList() else listOf("Confirmar VIN (17 caracteres) en placa o tarjeta de propiedad."),
                rationale,
            )
        }
        if (e.hasBrand && e.hasModel && e.hasEngine && e.hasPosition && e.hasOem) {
            rationale.add("marca + modelo + motorización + posición + OEM.")
            return TierResult(
                CompatibilityConfidence.HIGH,
                listOf("Confirmar año del vehículo."),
                rationale,
            )
        }

        // MEDIUM: brand+model with engine or year, OR brand+model with photo.
        if (e.hasBrand && e.hasModel && (e.hasEngine || e.hasYear)) {
            rationale.add("marca + modelo + motor o año.")
            val req = mutableListOf<String>()
            req.add(if (e.hasOem) "Confirmar que el OEM coincida con la pieza instalada." else "Adjuntar número OEM o foto legible de la etiqueta de la pieza.")
            req.add(if (e.hasVin) "Confirmar VIN para subir a EXACT." else "Adjuntar VIN.")
            return TierResult(CompatibilityConfidence.MEDIUM, req, rationale)
        }
        if (e.hasBrand && e.hasModel && e.hasPhotoEvidence) {
            rationale.add("marca + modelo + foto de la pieza.")
            return TierResult(
                CompatibilityConfidence.MEDIUM,
                if (e.hasOem) listOf("Confirmar OEM.") else listOf("Adjuntar número OEM."),
                rationale,
            )
        }

        // LOW: just brand+model OR just partName+position.
        if (e.hasBrand && e.hasModel) {
            rationale.add("solo marca + modelo. Año y motor desconocidos.")
            return TierResult(
                CompatibilityConfidence.LOW,
                listOf(
                    "Confirmar año del vehículo.",
                    "Confirmar motorización (cilindrada, transmisión, combustible).",
                    "Adjuntar número OEM o foto de la pieza.",
                ),
                rationale,
            )
        }
        if (e.hasPosition && ctx.partName.trim().length >= 4) {
            rationale.add("solo posición + nombre genérico de pieza.")
            return TierResult(
                CompatibilityConfidence.LOW,
                listOf(
                    "Seleccionar vehículo activo (marca + modelo + año + motor).",
                    "Adjuntar número OEM o foto legible.",
                ),
                rationale,
            )
        }

        rationale.add("contexto insuficiente para emitir un veredicto significativo.")
        return TierResult(
            CompatibilityConfidence.UNKNOWN,
            listOf(
                "Seleccionar vehículo activo (marca + modelo + año + motor).",
                "Adjuntar número OEM o foto de la pieza o conector.",
            ),
            rationale,
        )
    }

    fun evaluate(ctx: CompatibilityContext): CompatibilityResult {
        val evidence = collectEvidence(ctx)
        val tier = pickTier(ctx, evidence)
        val warnings = mutableListOf<CompatibilityWarning>()
        val questions = mutableListOf<String>()
        val crossRefs = mutableListOf<String>()

        // 1) Critical safety surface.
        if (isCriticalSafetyPart(ctx.partName)) {
            warnings.add(
                CompatibilityWarning(
                    code = "CRITICAL_SAFETY_PART",
                    severity = WarningSeverity.WARN,
                    message = SAFETY_INSTALL_MESSAGE,
                ),
            )
            if (ctx.partName.lowercase().contains("freno") ||
                ctx.partName.lowercase().contains("brake")
            ) {
                questions.add("¿La pieza es específica para el eje (delantero/trasero) de mi vehículo?")
            }
        }

        // 2) P0230 + fuel pump case.
        val dtcs = ctx.dtcCodes.map { it.uppercase() }
        val isP0230 = dtcs.contains("P0230")
        val isFuelPump = looksLikeFuelPump(ctx.partName)
        if (isP0230 && isFuelPump) {
            warnings.add(
                CompatibilityWarning(
                    code = "DTC_P0230_PUMP_REQUIRES_CONFIRMATION",
                    severity = WarningSeverity.BLOCK,
                    message = P0230_PUMP_MESSAGE,
                ),
            )
            questions.add(
                "¿Confirma que la pieza es específicamente la bomba de combustible y " +
                    "no el relé o el fusible? P0230 suele ser del circuito, no de la bomba.",
            )
            questions.add(
                "¿Pueden verificar voltaje en el relé y en el conector de la bomba " +
                    "antes de cerrar la venta?",
            )
        } else if (isFuelPump) {
            warnings.add(
                CompatibilityWarning(
                    code = "CRITICAL_SAFETY_PART",
                    severity = WarningSeverity.WARN,
                    message = SAFETY_INSTALL_MESSAGE,
                ),
            )
        }

        // 3) Tier-driven prompts.
        if (!evidence.hasVin) {
            val whatWeHave = if (!ctx.vehicle.brand.isNullOrBlank() && !ctx.vehicle.model.isNullOrBlank()) {
                "${ctx.vehicle.brand} ${ctx.vehicle.model}"
            } else {
                "datos parciales del vehículo"
            }
            warnings.add(noVinWarning(whatWeHave))
        }
        if (!evidence.hasOem) warnings.add(NO_OEM_WARNING)
        if (!evidence.hasPhotoEvidence) warnings.add(NO_PHOTO_WARNING)

        // 4) Demote EXACT -> MEDIUM if any BLOCK.
        val hasBlock = warnings.any { it.severity == WarningSeverity.BLOCK }
        var finalConfidence = tier.confidence
        if (hasBlock && finalConfidence == CompatibilityConfidence.EXACT) {
            finalConfidence = CompatibilityConfidence.MEDIUM
        }

        // 5) Ambiguous generic names.
        val genericNames = setOf("parte", "repuesto", "pieza", "repuesto genérico")
        if (ctx.partName.trim().length < 4 || genericNames.contains(ctx.partName.trim().lowercase())) {
            warnings.add(PART_NAME_AMBIGUOUS_WARNING)
        }

        // 6) OEM-derived cross references.
        ctx.vehicle.oemNumber?.takeIf { it.isNotBlank() }?.let { crossRefs.add(it) }
        ctx.vehicle.partNumber?.takeIf { it.isNotBlank() && it != ctx.vehicle.oemNumber }?.let { crossRefs.add(it) }

        return CompatibilityResult(
            confidence = finalConfidence,
            warnings = warnings,
            requiredConfirmations = tier.requiredConfirmations,
            crossReferenceNumbers = crossRefs,
            recommendedQuestions = questions,
            rationale = tier.rationale,
        )
    }

    fun describeVerdict(result: CompatibilityResult): String = when (result.confidence) {
        CompatibilityConfidence.EXACT ->
            "Compatibilidad EXACTA según los datos aportados. Sigue requiriendo confirmación del proveedor."
        CompatibilityConfidence.HIGH ->
            "Compatibilidad probable; requiere confirmar por VIN, OEM o foto del conector."
        CompatibilityConfidence.MEDIUM ->
            "Compatibilidad probable; faltan datos para subir a HIGH."
        CompatibilityConfidence.LOW ->
            "Compatibilidad estimada baja; se necesita más contexto del vehículo."
        CompatibilityConfidence.UNKNOWN ->
            "Sin información suficiente para emitir un veredicto."
    }
}
