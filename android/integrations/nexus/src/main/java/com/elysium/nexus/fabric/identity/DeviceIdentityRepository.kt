package com.elysium.nexus.fabric.identity

import com.elysium.nexus.fabric.profile.db.DeviceIdentityEntity
import com.elysium.nexus.fabric.profile.db.DeviceIdentityHistoryEntity
import com.elysium.nexus.fabric.profile.db.InstalledProfileDao
import org.json.JSONArray
import org.json.JSONObject

/**
 * V06-P9: Device identity repository — persists the identity graph
 * and its observation history. JVM-testable via [IdentityDaoSeam].
 */
interface IdentityDaoSeam {
    suspend fun upsertDeviceIdentity(entity: DeviceIdentityEntity)
    suspend fun getDeviceIdentity(stableId: String): DeviceIdentityEntity?
    suspend fun getAllDeviceIdentities(): List<DeviceIdentityEntity>
    suspend fun insertIdentityHistory(entity: DeviceIdentityHistoryEntity)
    suspend fun getIdentityHistory(stableId: String): List<DeviceIdentityHistoryEntity>
}

/** Room adapter over the real DAO. */
class RoomIdentityDaoSeam(
    private val dao: InstalledProfileDao
) : IdentityDaoSeam {
    override suspend fun upsertDeviceIdentity(entity: DeviceIdentityEntity) =
        dao.upsertDeviceIdentity(entity)

    override suspend fun getDeviceIdentity(stableId: String) =
        dao.getDeviceIdentity(stableId)

    override suspend fun getAllDeviceIdentities() = dao.getAllDeviceIdentities()

    override suspend fun insertIdentityHistory(entity: DeviceIdentityHistoryEntity) =
        dao.insertIdentityHistory(entity)

    override suspend fun getIdentityHistory(stableId: String) =
        dao.getIdentityHistory(stableId)
}

/**
 * V06-P9: IdentityRepository — durable peer identity store.
 *
 * Stores the canonical [PeerIdentity] per stableId plus an append-only
 * observation history (source, hints, ip for context — never identity).
 */
class DeviceIdentityRepository(
    private val daoSeam: IdentityDaoSeam
) {

    suspend fun remember(
        identity: PeerIdentity,
        observation: PeerObservation,
        observedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        daoSeam.upsertDeviceIdentity(
            DeviceIdentityEntity(
                stableId = identity.stableId,
                label = identity.label,
                manufacturer = identity.manufacturer,
                model = identity.model,
                matchType = identity.matchTypeName(),
                evidenceJson = encodeEvidence(identity.evidence),
                compositeFallback = identity.compositeFallback,
                lastSeenEpochMs = observedAtEpochMs
            )
        )
        daoSeam.insertIdentityHistory(
            DeviceIdentityHistoryEntity(
                stableId = identity.stableId,
                observedAtEpochMs = observedAtEpochMs,
                source = observation.source,
                manufacturer = observation.manufacturer,
                model = observation.model,
                ipAddress = observation.ipAddress
            )
        )
    }

    suspend fun get(stableId: String): PeerIdentity? {
        val entity = daoSeam.getDeviceIdentity(stableId) ?: return null
        return PeerIdentity(
            stableId = entity.stableId,
            label = entity.label,
            manufacturer = entity.manufacturer,
            model = entity.model,
            evidence = decodeEvidence(entity.evidenceJson),
            compositeFallback = entity.compositeFallback
        )
    }

    suspend fun all(): List<PeerIdentity> =
        daoSeam.getAllDeviceIdentities().map {
            PeerIdentity(
                stableId = it.stableId,
                label = it.label,
                manufacturer = it.manufacturer,
                model = it.model,
                evidence = decodeEvidence(it.evidenceJson),
                compositeFallback = it.compositeFallback
            )
        }

    suspend fun history(stableId: String): List<DeviceIdentityHistoryEntity> =
        daoSeam.getIdentityHistory(stableId)

    companion object {
        private fun encodeEvidence(evidence: List<PeerIdentityEvidence>): String {
            val arr = JSONArray()
            for (e in evidence) {
                arr.put(JSONObject().put("kind", e.kind.name).put("value", e.value))
            }
            return arr.toString()
        }

        private fun decodeEvidence(json: String): List<PeerIdentityEvidence> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val kind = runCatching {
                            IdentityEvidenceKind.valueOf(obj.getString("kind"))
                        }.getOrNull() ?: continue
                        add(PeerIdentityEvidence(kind = kind, value = obj.getString("value")))
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun PeerIdentity.matchTypeName(): String =
            if (compositeFallback) "DETERMINISTIC_COMPOSITE"
            else "STABLE_EVIDENCE"
    }
}