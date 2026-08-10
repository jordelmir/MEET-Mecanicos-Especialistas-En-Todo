package com.elysium369.meet.ui.models

import com.elysium369.meet.core.diagnostics.DiagnosticSpatialProjection

enum class FindingObservationState { OBSERVED_NOW, NOT_VERIFIED_THIS_SCAN, HISTORICAL, VERIFIED_RESOLVED }
enum class FindingCoverageState { AUTHORITATIVE, PARTIAL, NOT_COVERED, UNKNOWN }

data class DiagnosticFindingUiModel(
    val findingId: String,
    val stableIdentity: String,
    val displayCode: String,
    val rawDtcIdentity: String,
    val module: String,
    val moduleAddress: String,
    val observationState: FindingObservationState,
    val status: String,
    val severity: String,
    val urgency: String,
    val lastSeenAt: Long,
    val evidenceStrength: String,
    val coverageState: FindingCoverageState,
    val definitionVerification: String,
    val spatialProjection: DiagnosticSpatialProjection? = null,
)
