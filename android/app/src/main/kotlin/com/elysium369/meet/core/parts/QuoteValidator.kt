package com.elysium369.meet.core.parts

/**
 * Quote Validator — pure, no I/O.
 *
 * Mirrors the TypeScript `validateQuote(...)` in
 * `lib/parts/quote.ts`. Same anti-fraud rules; identical messages.
 * The web form and the Android form refuse the same invalid inputs.
 */

import com.elysium369.meet.core.parts.CompatibilityConfidence

enum class PartCondition {
    NEW_OEM, NEW_AFTERMARKET, USED, REFURBISHED, REBUILT, UNKNOWN;

    companion object {
        fun fromString(value: String?): PartCondition =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class PartAvailability {
    IN_STOCK, SAME_DAY, NEXT_DAY, IMPORT_REQUIRED, UNKNOWN;

    companion object {
        fun fromString(value: String?): PartAvailability =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

data class DraftQuote(
    val partName: String,
    val brand: String,
    val partNumber: String,
    val oemNumber: String? = null,
    val condition: PartCondition,
    val availability: PartAvailability,
    val price: Double,
    val currency: String,
    val includesDelivery: Boolean,
    val deliveryFee: Double,
    val estimatedDeliveryHours: Int,
    val warrantyDays: Int,
    val photoUrls: List<String>,
    val compatibilityConfidence: CompatibilityConfidence,
    val compatibilityNotes: String,
    val expiresInHours: Int,
    val vehicleVin: String? = null,
    val vehicleBrand: String = "",
    val vehicleModel: String = "",
    val vehicleYear: Int? = null,
    val vehicleEngine: String = "",
)

enum class ValidationLevel { OK, WARN, BLOCK }

data class ValidationIssue(val code: String, val message: String, val severity: ValidationLevel)

data class ValidationResult(
    val level: ValidationLevel,
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
)

object QuoteValidator {

    fun buildQuote(form: DraftQuote): DraftQuote = form.copy(
        partName = form.partName.trim(),
        brand = form.brand.trim(),
        partNumber = form.partNumber.trim(),
        oemNumber = form.oemNumber?.trim()?.takeIf { it.isNotEmpty() },
        photoUrls = form.photoUrls.map { it.trim() }.filter { it.isNotEmpty() },
        compatibilityNotes = form.compatibilityNotes.trim(),
        vehicleVin = form.vehicleVin?.trim()?.takeIf { it.isNotEmpty() },
        vehicleBrand = form.vehicleBrand.trim(),
        vehicleModel = form.vehicleModel.trim(),
        vehicleEngine = form.vehicleEngine.trim(),
    )

    fun validate(quote: DraftQuote): ValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        if (quote.partName.length < 3) {
            errors += ValidationIssue(
                code = "PART_NAME_SHORT",
                message = "El nombre de la pieza debe tener al menos 3 caracteres.",
                severity = ValidationLevel.BLOCK,
            )
        }
        if (quote.price <= 0) {
            errors += ValidationIssue(
                code = "PRICE_NON_POSITIVE",
                message = "El precio debe ser mayor que cero.",
                severity = ValidationLevel.BLOCK,
            )
        }
        if (quote.price > 1_000_000) {
            warnings += ValidationIssue(
                code = "PRICE_UNUSUAL",
                message = "El precio es inusualmente alto; verificar con el cliente.",
                severity = ValidationLevel.WARN,
            )
        }
        if (quote.estimatedDeliveryHours < 0) {
            errors += ValidationIssue(
                code = "ETA_NEGATIVE",
                message = "La entrega estimada no puede ser negativa.",
                severity = ValidationLevel.BLOCK,
            )
        }
        if (quote.warrantyDays < 0) {
            errors += ValidationIssue(
                code = "WARRANTY_NEGATIVE",
                message = "La garantía no puede ser negativa.",
                severity = ValidationLevel.BLOCK,
            )
        }
        if (quote.condition == PartCondition.USED || quote.condition == PartCondition.REFURBISHED) {
            if (quote.photoUrls.isEmpty()) {
                errors += ValidationIssue(
                    code = "USED_REQUIRES_PHOTO",
                    message = "Para piezas usadas o reacondicionadas se requiere al menos una foto.",
                    severity = ValidationLevel.BLOCK,
                )
            }
            if (quote.warrantyDays == 0) {
                warnings += ValidationIssue(
                    code = "USED_NO_WARRANTY",
                    message = "Pieza usada sin garantía declarada: algunos clientes podrían pedirla.",
                    severity = ValidationLevel.WARN,
                )
            }
        }
        val vinProvided = !quote.vehicleVin.isNullOrEmpty()
        val validVin = isValidVin(quote.vehicleVin)
        if (vinProvided && !validVin) {
            errors += ValidationIssue(
                code = "INVALID_VIN",
                message = "El VIN recibido no es válido: debe tener exactamente 17 caracteres " +
                    "y no incluir I, O ni Q.",
                severity = ValidationLevel.BLOCK,
            )
        }
        if (quote.compatibilityConfidence == CompatibilityConfidence.EXACT) {
            val hasPartIdentity = !quote.oemNumber.isNullOrEmpty() || quote.partNumber.isNotEmpty()
            val hasVinEvidence = hasPartIdentity && validVin
            val hasClosedTupleEvidence = quote.vehicleBrand.isNotEmpty() &&
                quote.vehicleModel.isNotEmpty() &&
                quote.vehicleYear != null && quote.vehicleYear in 1886..2100 &&
                quote.vehicleEngine.isNotEmpty() &&
                !quote.oemNumber.isNullOrEmpty()

            if (!hasPartIdentity) {
                errors += ValidationIssue(
                    code = "EXACT_REQUIRES_OEM",
                    message = "Para una confianza EXACTA se requiere número OEM o número de parte.",
                    severity = ValidationLevel.BLOCK,
                )
            }
            if (!hasVinEvidence && !hasClosedTupleEvidence) {
                errors += ValidationIssue(
                    code = "EXACT_REQUIRES_VEHICLE_EVIDENCE",
                    message = "EXACT requiere VIN válido + OEM/número de parte, o tupla cerrada " +
                        "marca/modelo/año/motor/OEM.",
                    severity = ValidationLevel.BLOCK,
                )
            }
            if (quote.compatibilityNotes.trim().isEmpty()) {
                warnings += ValidationIssue(
                    code = "EXACT_NO_NOTES",
                    message = "EXACT sin notas de compatibilidad: por favor documenta la verificación.",
                    severity = ValidationLevel.WARN,
                )
            }
        }
        if (isCriticalSafetyPart(quote.partName)) {
            warnings += ValidationIssue(
                code = "CRITICAL_SAFETY_PART",
                message = "Pieza crítica: requiere el checkbox de \"Instalación por técnico " +
                    "calificado\" antes de poder publicar.",
                severity = ValidationLevel.WARN,
            )
        }
        if (quote.availability == PartAvailability.IMPORT_REQUIRED && quote.estimatedDeliveryHours < 24 * 7) {
            warnings += ValidationIssue(
                code = "IMPORT_SHORT_ETA",
                message = "IMPORT_REQUIRED con menos de 1 semana de entrega: " +
                    "verificar el plazo real con el proveedor de despacho.",
                severity = ValidationLevel.WARN,
            )
        }

        val level = when {
            errors.isNotEmpty() -> ValidationLevel.BLOCK
            warnings.isNotEmpty() -> ValidationLevel.WARN
            else -> ValidationLevel.OK
        }
        return ValidationResult(level, errors, warnings)
    }
}
