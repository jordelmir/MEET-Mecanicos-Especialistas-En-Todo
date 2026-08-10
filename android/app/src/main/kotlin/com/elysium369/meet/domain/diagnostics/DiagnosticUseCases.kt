package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.core.diagnostics.DiagnosticHypothesisEngine
import com.elysium369.meet.core.diagnostics.DiagnosticReasoningInput
import com.elysium369.meet.core.diagnostics.DiagnosticSpatialFindingContext
import com.elysium369.meet.core.diagnostics.DiagnosticSpatialProjection
import com.elysium369.meet.core.diagnostics.DtcSpatialResolver
import com.elysium369.meet.core.diagnostics.HypothesisEngineDecision
import com.elysium369.meet.core.obd.ClearDtcResult
import com.elysium369.meet.core.obd.ClearVerificationPlan
import com.elysium369.meet.core.obd.DiagnosticScanMode
import com.elysium369.meet.core.obd.DtcScanReport
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.data.local.dao.DiagnosticEvidenceDao
import com.elysium369.meet.data.local.dao.DtcDao
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import com.elysium369.meet.data.local.entities.DtcEventEntity
import com.elysium369.meet.data.local.entities.FindingDiagnosticSnapshotEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RunDiagnosticScan @Inject constructor(private val session: ObdSession) {
    suspend operator fun invoke(mode: DiagnosticScanMode): DtcScanReport = session.readProfessionalDtcScan(mode)
}

class ClearDiagnosticMemory @Inject constructor(private val session: ObdSession) {
    suspend operator fun invoke(plan: ClearVerificationPlan): ClearDtcResult = session.clearDtcs(plan)
}

class ObserveDiagnosticFindings @Inject constructor(private val dtcDao: DtcDao) {
    operator fun invoke(vehicleId: String): Flow<List<DtcEventEntity>> = dtcDao.getUnresolvedDtcsForVehicle(vehicleId)
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
