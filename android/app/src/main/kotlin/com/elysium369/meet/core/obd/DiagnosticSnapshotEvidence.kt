package com.elysium369.meet.core.obd

enum class DiagnosticSnapshotSource {
    SAE_MODE_02,
    UDS_SNAPSHOT,
    UDS_EXTENDED_DATA,
    OEM,
    LIVE_CAPTURE,
}

enum class DiagnosticValueOrigin {
    MEASURED,
    DERIVED,
    ESTIMATED,
    USER_CONFIGURED,
    INFERRED,
}

data class SnapshotParameter(
    val pidOrDid: String,
    val value: Double?,
    val unit: String?,
    val origin: DiagnosticValueOrigin,
    val confidence: Double,
    val formulaVersion: String? = null,
    val inputExchangeIds: List<String> = emptyList(),
)

data class FindingDiagnosticSnapshot(
    val id: String,
    val findingId: String,
    val moduleIdentity: String,
    val capturedAtMs: Long,
    val source: DiagnosticSnapshotSource,
    val parameters: List<SnapshotParameter>,
    val rawExchangeIds: List<String>,
)
