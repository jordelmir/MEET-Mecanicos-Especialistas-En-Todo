package com.elysium.nexus.databases.compatibility

import com.elysium.nexus.core.compat.CompatibilityResult
import com.elysium.nexus.core.compat.CompatibilityStatus

/**
 * The Room-backed production implementation of
 * [CompatibilityRepository].
 *
 * The repository delegates every method to the
 * [CompatibilityDao]. The conversion from
 * [CompatibilityResult] (domain) to [CompatibilityEntity]
 * (persistence) is in [toEntity] / [toResult]; the
 * conversion is total and free of side effects.
 *
 * The class is open for testing (a test could subclass
 * and override the DAO), but the standard test path
 * uses [InMemoryCompatibilityRepository] because the
 * Room database requires a real `Context` to open.
 */
class RoomCompatibilityRepository(
    private val dao: CompatibilityDao
) : CompatibilityRepository {

    override suspend fun add(record: CompatibilityResult) {
        dao.insert(toEntity(record))
    }

    override suspend fun byDevice(deviceId: String): List<CompatibilityResult> =
        dao.byDevice(deviceId).map { toResult(it) }

    override suspend fun byTarget(targetPlatform: String): List<CompatibilityResult> =
        dao.byTarget(targetPlatform).map { toResult(it) }

    override suspend fun byStatus(status: CompatibilityStatus): List<CompatibilityResult> =
        dao.all()
            .filter { it.status == status }
            .map { toResult(it) }

    override suspend fun latest(
        deviceId: String,
        targetPlatform: String
    ): CompatibilityResult? = dao.latest(deviceId, targetPlatform)?.let { toResult(it) }

    override suspend fun all(): List<CompatibilityResult> =
        dao.all().map { toResult(it) }

    override suspend fun count(): Int = dao.count()

    override suspend fun statusBreakdown(): Map<CompatibilityStatus, Int> {
        val out = mutableMapOf<CompatibilityStatus, Int>()
        for (status in CompatibilityStatus.values()) {
            out[status] = 0
        }
        for (entity in dao.all()) {
            out[entity.status] = (out[entity.status] ?: 0) + 1
        }
        return out
    }

    /**
     * Convert a [CompatibilityResult] to its persistence
     * shape. The lists are joined with `;`; the
     * [CompatibilityStatus] is stored by name.
     */
    private fun toEntity(record: CompatibilityResult): CompatibilityEntity =
        CompatibilityEntity(
            id = 0L, // auto-generate
            deviceId = record.deviceId,
            deviceModel = record.deviceModel,
            androidVersion = record.androidVersion,
            oemFirmware = record.oemFirmware,
            transport = record.transport,
            targetPlatform = record.targetPlatform,
            targetOsFirmware = record.targetOsFirmware,
            game = record.game,
            capabilitiesTested = record.capabilitiesTested.joinToString(";"),
            capabilitiesPassed = record.capabilitiesPassed.joinToString(";"),
            capabilitiesFailed = record.capabilitiesFailed.joinToString(";"),
            latencyP50Ns = record.latencyP50Ns,
            latencyP95Ns = record.latencyP95Ns,
            tester = record.tester,
            date = record.date,
            evidence = record.evidence,
            confidence = record.confidence,
            status = record.status
        )

    /**
     * Convert a [CompatibilityEntity] to its domain
     * shape. The inverse of [toEntity]. Empty joined
     * strings become empty lists.
     */
    private fun toResult(entity: CompatibilityEntity): CompatibilityResult =
        CompatibilityResult(
            deviceId = entity.deviceId,
            deviceModel = entity.deviceModel,
            androidVersion = entity.androidVersion,
            oemFirmware = entity.oemFirmware,
            transport = entity.transport,
            targetPlatform = entity.targetPlatform,
            targetOsFirmware = entity.targetOsFirmware,
            game = entity.game,
            capabilitiesTested = entity.capabilitiesTested.split(";").filter { it.isNotEmpty() },
            capabilitiesPassed = entity.capabilitiesPassed.split(";").filter { it.isNotEmpty() },
            capabilitiesFailed = entity.capabilitiesFailed.split(";").filter { it.isNotEmpty() },
            latencyP50Ns = entity.latencyP50Ns,
            latencyP95Ns = entity.latencyP95Ns,
            tester = entity.tester,
            date = entity.date,
            evidence = entity.evidence,
            confidence = entity.confidence,
            status = entity.status
        )
}
