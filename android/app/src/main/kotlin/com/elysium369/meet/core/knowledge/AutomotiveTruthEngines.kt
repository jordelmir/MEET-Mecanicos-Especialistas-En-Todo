package com.elysium369.meet.core.knowledge

class MeasurementSpecValidator {
    fun validate(spec: MeasurementSpecification): List<KnowledgeValidationIssue> {
        val issues = mutableListOf<KnowledgeValidationIssue>()

        if (!spec.hasNumericValue()) {
            issues += issue(
                spec,
                "MEASUREMENT_VALUE_MISSING",
                "La especificacion no contiene valor minimo, nominal ni maximo."
            )
        }
        if (spec.unitCode.isBlank()) {
            issues += issue(spec, "MEASUREMENT_UNIT_MISSING", "Falta la unidad de medida.")
        }
        if (spec.measurementCondition.isBlank()) {
            issues += issue(
                spec,
                "MEASUREMENT_CONDITION_MISSING",
                "Falta la condicion de medicion."
            )
        }
        if (spec.requiredInstrument.isBlank()) {
            issues += issue(
                spec,
                "MEASUREMENT_INSTRUMENT_MISSING",
                "Falta el instrumento requerido."
            )
        }
        if (spec.tolerance.isBlank()) {
            issues += issue(spec, "MEASUREMENT_TOLERANCE_MISSING", "Falta la tolerancia.")
        }
        if (spec.verificationStatus == MeasurementVerificationStatus.VERIFIED &&
            spec.sourceClaimId.isNullOrBlank()
        ) {
            issues += issue(
                spec,
                "VERIFIED_MEASUREMENT_SOURCE_MISSING",
                "Un valor VERIFIED requiere un claim de fuente revisado."
            )
        }
        if (spec.minimumValue != null && spec.maximumValue != null &&
            spec.minimumValue > spec.maximumValue
        ) {
            issues += issue(
                spec,
                "MEASUREMENT_RANGE_INVALID",
                "El valor minimo no puede superar el maximo."
            )
        }
        return issues
    }

    fun displayDisclaimer(spec: MeasurementSpecification): String? =
        if (spec.verificationStatus == MeasurementVerificationStatus.VERIFIED &&
            validate(spec).none { it.severity == KnowledgeIssueSeverity.BLOCKING }
        ) {
            null
        } else {
            "Valor pendiente de validacion documental. No utilizar como especificacion de reparacion definitiva."
        }

    private fun issue(
        spec: MeasurementSpecification,
        code: String,
        message: String
    ) = KnowledgeValidationIssue(
        code = code,
        message = message,
        severity = KnowledgeIssueSeverity.BLOCKING,
        recordId = spec.measurementId
    )
}

class AutomotiveApplicabilityResolver {
    fun resolve(
        claim: TechnicalClaim,
        evidence: ApplicabilityEvidence = ApplicabilityEvidence()
    ): ApplicabilityResolution {
        if (claim.applicability == ApplicabilityStatus.REFERENCE_VEHICLE_ONLY ||
            claim.scopeType == KnowledgeScopeType.REFERENCE_VEHICLE
        ) {
            return ApplicabilityResolution(
                effectiveStatus = ApplicabilityStatus.REFERENCE_VEHICLE_ONLY,
                canUseAsVehicleFact = false,
                missingEvidence = emptyList(),
                explanation = "Vehiculo de referencia. No transferir equipamiento ni especificaciones al vehiculo objetivo."
            )
        }

        if (claim.applicability in setOf(
                ApplicabilityStatus.ABSENT_DOCUMENTED,
                ApplicabilityStatus.NOT_APPLICABLE_ARCHITECTURE,
                ApplicabilityStatus.UNKNOWN_INSUFFICIENT_EVIDENCE,
                ApplicabilityStatus.AFTERMARKET_POSSIBLE
            )
        ) {
            return ApplicabilityResolution(
                effectiveStatus = claim.applicability,
                canUseAsVehicleFact = false,
                missingEvidence = claim.evidenceRequired.filterNot(evidence.physicalEvidenceIds::contains),
                explanation = explanationFor(claim.applicability)
            )
        }

        val missing = buildList {
            if (claim.requiresVinConfirmation && !evidence.vinConfirmed) add("VIN")
            if (claim.requiresOemConfirmation && !evidence.oemConfirmed) add("OEM")
            if (claim.requiresVisualConfirmation && !evidence.visualConfirmed) add("CONFIRMACION_VISUAL")
            addAll(claim.evidenceRequired.filterNot(evidence.physicalEvidenceIds::contains))
        }.distinct()

        if (missing.isNotEmpty()) {
            return ApplicabilityResolution(
                effectiveStatus = ApplicabilityStatus.VERIFY_PHYSICALLY,
                canUseAsVehicleFact = false,
                missingEvidence = missing,
                explanation = "Aplicabilidad probable; requiere confirmar ${missing.joinToString(", ")}."
            )
        }

        val usable = claim.applicability in setOf(
            ApplicabilityStatus.PRESENT_DOCUMENTED,
            ApplicabilityStatus.PRESENT_USER_VERIFIED,
            ApplicabilityStatus.AFTERMARKET_INSTALLED
        )
        return ApplicabilityResolution(
            effectiveStatus = claim.applicability,
            canUseAsVehicleFact = usable,
            missingEvidence = emptyList(),
            explanation = explanationFor(claim.applicability)
        )
    }

    private fun explanationFor(status: ApplicabilityStatus): String = when (status) {
        ApplicabilityStatus.ABSENT_DOCUMENTED -> "Ausencia documentada para el alcance indicado."
        ApplicabilityStatus.NOT_APPLICABLE_ARCHITECTURE -> "No aplica a esta arquitectura vehicular."
        ApplicabilityStatus.UNKNOWN_INSUFFICIENT_EVIDENCE -> "Evidencia insuficiente; no asumir presencia ni ausencia."
        ApplicabilityStatus.AFTERMARKET_POSSIBLE -> "Posible solo como instalacion aftermarket; requiere inspeccion fisica."
        ApplicabilityStatus.PRESENT_CONDITIONAL -> "Presencia condicionada a variante o mercado."
        ApplicabilityStatus.VERIFY_PHYSICALLY -> "Requiere prueba fisica."
        else -> "Aplicabilidad evaluada con la evidencia disponible."
    }
}

class KnowledgeConflictDetector {
    fun detect(claims: List<TechnicalClaim>): List<KnowledgeConflict> = claims
        .groupBy { Triple(it.subjectId, it.predicate, it.vehicleScopeId) }
        .mapNotNull { (key, scopedClaims) ->
            val hasPresent = scopedClaims.any { it.applicability.isPresent() }
            val hasAbsent = scopedClaims.any { it.applicability.isAbsent() }
            if (!hasPresent || !hasAbsent) return@mapNotNull null

            KnowledgeConflict(
                conflictId = "conflict_${key.first}_${key.second}_${key.third}"
                    .replace(Regex("[^A-Za-z0-9_]"), "_"),
                claimIds = scopedClaims.map { it.claimId }.sorted(),
                reason = "Claims de presencia y ausencia coexisten para el mismo alcance."
            )
        }

    private fun ApplicabilityStatus.isPresent(): Boolean = this in setOf(
        ApplicabilityStatus.PRESENT_DOCUMENTED,
        ApplicabilityStatus.PRESENT_CONDITIONAL,
        ApplicabilityStatus.PRESENT_USER_VERIFIED,
        ApplicabilityStatus.AFTERMARKET_INSTALLED
    )

    private fun ApplicabilityStatus.isAbsent(): Boolean = this in setOf(
        ApplicabilityStatus.ABSENT_DOCUMENTED,
        ApplicabilityStatus.NOT_APPLICABLE_ARCHITECTURE
    )
}

data class DiagnosticClaimRequest(
    val dtcCode: String,
    val componentName: String,
    val requiredEvidence: Set<String>,
    val completedEvidence: Set<String>,
    val confidence: ConfidenceLevel
)

data class DiagnosticClaimDecision(
    val statement: String,
    val replacementAllowed: Boolean,
    val missingEvidence: List<String>,
    val confidence: ConfidenceLevel
)

class DiagnosticTruthEngine {
    fun evaluate(request: DiagnosticClaimRequest): DiagnosticClaimDecision {
        val missing = (request.requiredEvidence - request.completedEvidence).sorted()
        val sufficientConfidence = request.confidence in setOf(
            ConfidenceLevel.VERIFIED,
            ConfidenceLevel.HIGH
        )
        val replacementAllowed = request.requiredEvidence.isNotEmpty() &&
            missing.isEmpty() &&
            sufficientConfidence

        val statement = if (replacementAllowed) {
            "La evidencia reunida es compatible con una falla en ${request.componentName}. " +
                "Documente la prueba fisica antes del reemplazo."
        } else {
            "${request.dtcCode} abre la hipotesis de ${request.componentName}; no confirma la pieza danada. " +
                "Requiere prueba fisica."
        }

        return DiagnosticClaimDecision(
            statement = statement,
            replacementAllowed = replacementAllowed,
            missingEvidence = missing,
            confidence = request.confidence
        )
    }
}
