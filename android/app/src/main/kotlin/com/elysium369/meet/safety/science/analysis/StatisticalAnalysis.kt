package com.elysium369.meet.safety.science.analysis

import kotlinx.serialization.Serializable
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════
// §39 — Statistical Analysis
//
// Every statistic MUST register: dataset, population, sample,
// denominator, missingness, confidence interval, effect size,
// model, assumptions, and limitations.
//
// No showing "97% probability" without opening
// "¿Cómo calculaste ese 97%?"
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class StatisticalAnalysis(
    val id: String,

    val claimId: String?,
    val researchRunId: String?,

    val dataset: DatasetDescriptor,
    val population: PopulationDescriptor,
    val model: StatisticalModel,

    val result: StatisticalResult,

    val assumptions: List<String>,
    val limitations: List<String>,
    val biasNotes: List<String>,

    val methodologyVersion: String = "safety-science-v1",
)

@Serializable
data class DatasetDescriptor(
    val datasetId: String,
    val datasetVersion: String,
    val totalRecords: Long,
    val filteredRecords: Long,
    val missingDataFields: List<String> = emptyList(),
    val missingnessRate: Double? = null,
    val datasetHash: String,
)

@Serializable
data class PopulationDescriptor(
    val description: String,
    val totalPopulation: Long?,
    val sampleSize: Long,
    val samplingMethod: SamplingMethod,
    val denominator: Long?,
    val denominatorDescription: String?,
)

@Serializable
enum class SamplingMethod {
    CENSUS,
    RANDOM,
    STRATIFIED,
    CONVENIENCE,
    PURPOSIVE,
    SNOWBALL,
    EXHAUSTIVE_RECORDS,
    UNKNOWN,
}

@Serializable
data class StatisticalModel(
    val name: String,
    val formula: String?,
    val parameters: Map<String, String> = emptyMap(),
    val softwareVersion: String?,
)

@Serializable
data class StatisticalResult(
    val pointEstimate: Double?,
    val confidenceInterval: ConfidenceInterval?,
    val effectSize: EffectSize?,
    val pValue: Double?,
    val significanceLevel: Double?,
    val interpretation: String,
)

@Serializable
data class ConfidenceInterval(
    val lower: Double,
    val upper: Double,
    val level: Double, // e.g. 0.95
)

@Serializable
data class EffectSize(
    val value: Double,
    val measure: String, // "Cohen's d", "Odds Ratio", "Relative Risk", etc.
    val interpretation: String,
)

// ═══════════════════════════════════════════════════════════════════
// §70 — Separated Confidence Metrics
//
// No "one giant confidence number".
// Each dimension is tracked independently.
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class EvidenceQualityProfile(
    val claimId: String,

    val evidenceStrength: QualityDimension,
    val sourceIndependence: QualityDimension,
    val methodologicalRigor: QualityDimension,
    val causalConfidence: QualityDimension,
    val replicationStatus: QualityDimension,
    val legalStatus: QualityDimension,

    val methodologyVersion: String = "safety-science-v1",
)

@Serializable
data class QualityDimension(
    val level: QualityLevel,
    val justification: String,
    val evidenceIds: List<String> = emptyList(),
)

@Serializable
enum class QualityLevel {
    NOT_ASSESSED,
    NONE,
    WEAK,
    MODERATE,
    STRONG,
    VERY_STRONG,
}

// ═══════════════════════════════════════════════════════════════════
// §71 — Scientific Metrics (for dashboard)
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class ScientificMetrics(
    val totalEvidence: Long = 0,
    val verifiedEvidence: Long = 0,
    val corroboratedEvidence: Long = 0,
    val independentSources: Long = 0,
    val contradictedClaims: Long = 0,
    val unresolvedClaims: Long = 0,
    val alternativeHypotheses: Long = 0,
    val replicatedFindings: Long = 0,
    val peerReviewedFindings: Long = 0,
    val withdrawnFindings: Long = 0,
    val researchPackages: Long = 0,
    // NEVER: "truthScore" — that does not exist.
)
