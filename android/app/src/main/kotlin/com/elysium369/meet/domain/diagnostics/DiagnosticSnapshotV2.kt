package com.elysium369.meet.domain.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticSnapshotParameterV2(
    val parameterId: String,
    val rawValue: String,
    val capturedAtMs: Long,
    val quality: String,
    val freshness: String,
    val canonicalSiUnit: String? = null,
    val displayUnit: String? = null,
    val source: String,
    val formulaVersion: String,
    val inputExchangeIds: List<String>,
)

@Serializable
data class DiagnosticSnapshotPayloadV2(
    val schemaVersion: Int = 2,
    val associationMethod: String,
    val parameters: List<DiagnosticSnapshotParameterV2>,
)
