package com.elysium369.meet.core.emissions.domain

/**
 * An immutable, metrologically sound emission or combustion metric.
 * Enforces the invariant that MEASURED requires a physical ECU or external sensor origin.
 */
data class EmissionMetric(
    val id: String,
    val value: Double?,
    val unit: String,
    val origin: EvidenceOrigin,
    val truthClass: TruthClass,
    val lower95: Double? = null,
    val upper95: Double? = null,
    val quality: Double? = null,
    val capturedAtMonotonicMs: Long = System.currentTimeMillis(),
    val lineage: Set<String> = emptySet(),
    val evaluation: Evaluation = Evaluation.INCONCLUSIVE
) {
    init {
        require(
            truthClass != TruthClass.MEASURED ||
                origin == EvidenceOrigin.ECU_DIRECT ||
                origin == EvidenceOrigin.PHYSICAL_GAS_ANALYZER
        ) {
            "Metrological Invariant Violated: MEASURED truth class can only originate from " +
                "ECU_DIRECT or PHYSICAL_GAS_ANALYZER (got origin=$origin for metric '$id')"
        }
    }

    companion object {
        fun unknown(id: String, unit: String = ""): EmissionMetric {
            return EmissionMetric(
                id = id,
                value = null,
                unit = unit,
                origin = EvidenceOrigin.USER_DECLARED,
                truthClass = TruthClass.UNKNOWN,
                evaluation = Evaluation.INCONCLUSIVE
            )
        }

        fun ecuMeasured(id: String, value: Double, unit: String): EmissionMetric {
            return EmissionMetric(
                id = id,
                value = value,
                unit = unit,
                origin = EvidenceOrigin.ECU_DIRECT,
                truthClass = TruthClass.MEASURED,
                quality = 1.0,
                evaluation = Evaluation.NOT_APPLICABLE
            )
        }

        fun derived(id: String, value: Double, unit: String, lineage: Set<String>): EmissionMetric {
            return EmissionMetric(
                id = id,
                value = value,
                unit = unit,
                origin = EvidenceOrigin.PHYSICS_DERIVED,
                truthClass = TruthClass.DERIVED,
                lineage = lineage,
                quality = 0.9,
                evaluation = Evaluation.NOT_APPLICABLE
            )
        }

        fun estimated(
            id: String,
            value: Double,
            unit: String,
            lower95: Double,
            upper95: Double,
            lineage: Set<String>,
            quality: Double = 0.8
        ): EmissionMetric {
            return EmissionMetric(
                id = id,
                value = value,
                unit = unit,
                origin = EvidenceOrigin.MODEL_ESTIMATED,
                truthClass = TruthClass.ESTIMATED,
                lower95 = lower95,
                upper95 = upper95,
                lineage = lineage,
                quality = quality,
                evaluation = Evaluation.INCONCLUSIVE
            )
        }
    }
}
