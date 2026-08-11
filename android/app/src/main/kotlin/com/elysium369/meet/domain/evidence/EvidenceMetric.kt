package com.elysium369.meet.domain.evidence

/** A value that cannot be presented without carrying its provenance. */
data class EvidenceMetric<T>(
    val value: T,
    val source: String,
    val datasetId: String? = null,
    val sampleCount: Int? = null,
    val confidenceMethod: String? = null,
    val generatedAt: Long,
) {
    init {
        require(source.isNotBlank()) { "EvidenceMetric source is required" }
        require(sampleCount == null || sampleCount > 0) { "sampleCount must be positive when present" }
    }
}
