package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.core.diagnostics.DiagnosticHypothesisEngine
import com.elysium369.meet.core.diagnostics.DiagnosticReasoningInput
import com.elysium369.meet.core.diagnostics.DiagnosticSpatialFindingContext
import com.elysium369.meet.core.diagnostics.DiagnosticSpatialProjection
import com.elysium369.meet.core.diagnostics.DtcSpatialResolver
import com.elysium369.meet.core.diagnostics.HypothesisEngineDecision
import com.elysium369.meet.core.obd.ClearDtcResult
import com.elysium369.meet.core.obd.ClearVerificationPlan
import com.elysium369.meet.core.obd.DiagnosticAcquisitionEngine
import com.elysium369.meet.core.obd.DiagnosticMemoryEngine
import com.elysium369.meet.core.obd.DiagnosticScanMode
import com.elysium369.meet.core.obd.DtcScanReport
import com.elysium369.meet.data.local.dao.DiagnosticEvidenceDao
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import com.elysium369.meet.data.local.entities.FindingDiagnosticSnapshotEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RunDiagnosticScan @Inject constructor(private val acquisition: DiagnosticAcquisitionEngine) {
    suspend operator fun invoke(mode: DiagnosticScanMode): DtcScanReport = acquisition.scan(mode)
}

class ClearDiagnosticMemory @Inject constructor(private val memory: DiagnosticMemoryEngine) {
    suspend operator fun invoke(plan: ClearVerificationPlan): ClearDtcResult = memory.clear(plan)
}

class ObserveDiagnosticFindings @Inject constructor(private val repository: DiagnosticFindingRepository) {
    operator fun invoke(vehicleId: String): Flow<List<CanonicalDiagnosticFinding>> =
        repository.observeOpenFindings(vehicleId)
}

data class FindingEvidenceStreams(
    val observations: Flow<List<DiagnosticObservationEntity>>,
    val snapshots: Flow<List<FindingDiagnosticSnapshotEntity>>,
)

class GetFindingEvidence @Inject constructor(private val evidenceDao: DiagnosticEvidenceDao) {
    operator fun invoke(findingId: String): FindingEvidenceStreams = FindingEvidenceStreams(
        observations = evidenceDao.observeFindingTimeline(findingId),
        snapshots = evidenceDao.observeFindingSnapshots(findingId),
    )
}

class OpenFindingSpatialProjection @Inject constructor() {
    operator fun invoke(context: DiagnosticSpatialFindingContext): DiagnosticSpatialProjection =
        DtcSpatialResolver.resolve(context)
}

class RunGuidedDiagnosticTest @Inject constructor() {
    private val engine = DiagnosticHypothesisEngine()
    operator fun invoke(input: DiagnosticReasoningInput): HypothesisEngineDecision = engine.analyze(input)
}
