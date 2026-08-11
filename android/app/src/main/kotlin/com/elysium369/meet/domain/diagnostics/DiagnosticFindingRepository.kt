package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.data.local.dao.DiagnosticEvidenceDao
import com.elysium369.meet.data.local.dao.DiagnosticFindingDao
import com.elysium369.meet.data.local.entities.DiagnosticFindingEntity
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

enum class FindingResolutionState {
    OPEN,
    NOT_VERIFIED_THIS_SCAN,
    VERIFIED_RESOLVED,
}

data class ProjectedFindingState(
    val state: FindingResolutionState,
    val latestObservation: DiagnosticObservationEntity?,
    val resolvedAtMs: Long?,
)

data class CanonicalDiagnosticFinding(
    val identity: DiagnosticFindingEntity,
    val timeline: List<DiagnosticObservationEntity>,
    val projection: ProjectedFindingState,
)

/** UI-safe projection sourced only from canonical finding observations. */
data class DiagnosticFindingSummary(
    val id: String,
    val code: String,
    val rawIdentity: String,
    val moduleIdentity: String,
    val status: String,
    val description: String,
    val severity: String,
    val occurrenceCount: Int,
    val lastSeenAt: Long,
)

fun CanonicalDiagnosticFinding.toSummary(): DiagnosticFindingSummary {
    val observedTimeline = timeline.filter { it.observationState == "OBSERVED" }
    val latestObservedSemantics = observedTimeline.maxWithOrNull(
        compareBy<DiagnosticObservationEntity> { it.observedAt }
            .thenBy { it.sessionSequence }
            .thenBy { it.id },
    )?.semantics.orEmpty()
    val status = when {
        projection.state == FindingResolutionState.VERIFIED_RESOLVED -> "VERIFIED_RESOLVED"
        "PERMANENT" in latestObservedSemantics -> "PERMANENT"
        "PENDING" in latestObservedSemantics -> "PENDING"
        "HISTORY" in latestObservedSemantics || "INTERMITTENT" in latestObservedSemantics -> "HISTORY"
        else -> "ACTIVE"
    }
    return DiagnosticFindingSummary(
        id = identity.id,
        code = identity.displayCode,
        rawIdentity = identity.rawDtcIdentity,
        moduleIdentity = identity.ecuEndpointId,
        status = status,
        description = "Definición técnica pendiente de validación para este vehículo",
        severity = "NO_CALIBRADA",
        occurrenceCount = observedTimeline.size,
        lastSeenAt = timeline.maxOfOrNull { it.observedAt } ?: identity.createdAtMs,
    )
}

/** Deterministic projection rebuildable from the append-only timeline. */
object FindingStateProjector {
    fun project(timeline: List<DiagnosticObservationEntity>): ProjectedFindingState {
        val latest = timeline.maxWithOrNull(
            compareBy<DiagnosticObservationEntity> { it.observedAt }
                .thenBy { it.sessionSequence }
                .thenBy { it.id },
        )
        return when (latest?.observationState) {
            "VERIFIED_RESOLVED" -> ProjectedFindingState(
                FindingResolutionState.VERIFIED_RESOLVED,
                latest,
                latest.observedAt,
            )
            "NOT_OBSERVED", "NOT_OBSERVED_LAST_SCAN", "VERIFIED_ABSENT" -> ProjectedFindingState(
                FindingResolutionState.NOT_VERIFIED_THIS_SCAN,
                latest,
                null,
            )
            else -> ProjectedFindingState(FindingResolutionState.OPEN, latest, null)
        }
    }
}

interface DiagnosticFindingRepository {
    fun observeOpenFindings(vehicleId: String): Flow<List<CanonicalDiagnosticFinding>>
    fun observeResolvedFindings(vehicleId: String): Flow<List<CanonicalDiagnosticFinding>>
    fun observeFinding(findingId: String): Flow<CanonicalDiagnosticFinding?>
    fun observeTimeline(findingId: String): Flow<List<DiagnosticObservationEntity>>
    suspend fun getVehicleFindings(vehicleId: String): List<CanonicalDiagnosticFinding>
    suspend fun getFindingSnapshot(findingId: String): CanonicalDiagnosticFinding?
    suspend fun getIdentityByStableKey(
        vehicleId: String,
        ecuEndpointId: String,
        namespace: String,
        rawDtcIdentity: String,
    ): DiagnosticFindingEntity?
    suspend fun insertIdentity(finding: DiagnosticFindingEntity): Long
    suspend fun rebuildProjection(findingId: String): ProjectedFindingState?
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoomDiagnosticFindingRepository @Inject constructor(
    private val findingDao: DiagnosticFindingDao,
    private val evidenceDao: DiagnosticEvidenceDao,
) : DiagnosticFindingRepository {
    override fun observeOpenFindings(vehicleId: String): Flow<List<CanonicalDiagnosticFinding>> =
        observeVehicle(vehicleId).map { findings ->
            findings.filter { it.projection.state != FindingResolutionState.VERIFIED_RESOLVED }
        }

    override fun observeResolvedFindings(vehicleId: String): Flow<List<CanonicalDiagnosticFinding>> =
        observeVehicle(vehicleId).map { findings ->
            findings.filter { it.projection.state == FindingResolutionState.VERIFIED_RESOLVED }
        }

    override fun observeFinding(findingId: String): Flow<CanonicalDiagnosticFinding?> =
        combine(
            findingDao.observeById(findingId),
            evidenceDao.observeFindingTimeline(findingId),
        ) { finding, timeline ->
            finding?.let { CanonicalDiagnosticFinding(it, timeline, FindingStateProjector.project(timeline)) }
        }

    override fun observeTimeline(findingId: String): Flow<List<DiagnosticObservationEntity>> =
        evidenceDao.observeFindingTimeline(findingId)

    override suspend fun getVehicleFindings(vehicleId: String): List<CanonicalDiagnosticFinding> =
        findingDao.getForVehicle(vehicleId).map { finding ->
            val timeline = evidenceDao.getFindingTimeline(finding.id)
            CanonicalDiagnosticFinding(
                identity = finding,
                timeline = timeline,
                projection = FindingStateProjector.project(timeline),
            )
        }

    override suspend fun getFindingSnapshot(findingId: String): CanonicalDiagnosticFinding? {
        val finding = findingDao.getById(findingId) ?: return null
        val timeline = evidenceDao.getFindingTimeline(findingId)
        return CanonicalDiagnosticFinding(
            identity = finding,
            timeline = timeline,
            projection = FindingStateProjector.project(timeline),
        )
    }

    override suspend fun getIdentityByStableKey(
        vehicleId: String,
        ecuEndpointId: String,
        namespace: String,
        rawDtcIdentity: String,
    ): DiagnosticFindingEntity? = findingDao.getByStableIdentity(
        vehicleId = vehicleId,
        ecuEndpointId = ecuEndpointId,
        namespace = namespace,
        rawDtcIdentity = rawDtcIdentity,
    )

    override suspend fun insertIdentity(finding: DiagnosticFindingEntity): Long =
        findingDao.insertFinding(finding)

    override suspend fun rebuildProjection(findingId: String): ProjectedFindingState? {
        val finding = findingDao.getById(findingId) ?: return null
        val projection = FindingStateProjector.project(evidenceDao.getFindingTimeline(finding.id))
        findingDao.updateProjection(
            findingId = finding.id,
            state = projection.state.name,
            resolvedAtMs = projection.resolvedAtMs,
        )
        return projection
    }

    private fun observeVehicle(vehicleId: String): Flow<List<CanonicalDiagnosticFinding>> =
        findingDao.observeForVehicle(vehicleId).flatMapLatest { findings ->
            if (findings.isEmpty()) return@flatMapLatest flowOf(emptyList())
            combine(findings.map { finding ->
                evidenceDao.observeFindingTimeline(finding.id).map { timeline ->
                    CanonicalDiagnosticFinding(
                        identity = finding,
                        timeline = timeline,
                        projection = FindingStateProjector.project(timeline),
                    )
                }
            }) { it.toList() }
        }
}
