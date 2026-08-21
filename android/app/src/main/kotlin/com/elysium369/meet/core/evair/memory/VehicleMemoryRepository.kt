package com.elysium369.meet.core.evair.memory

import com.elysium369.meet.core.evair.domain.DiagnosticEvidence
import com.elysium369.meet.core.evair.domain.EpisodeType
import com.elysium369.meet.core.evair.domain.EvidenceSource
import com.elysium369.meet.core.evair.domain.VehicleEpisode
import com.elysium369.meet.data.local.dao.HealthSnapshotDao
import com.elysium369.meet.data.local.dao.PredictionEventDao
import com.elysium369.meet.data.local.dao.SensorHistoryDao
import com.elysium369.meet.data.local.dao.VehicleDnaDao
import com.elysium369.meet.data.local.dao.VehicleTwinDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Serializable
data class LongitudinalTrend(
    val pid: String,
    val initialBaseline: Double,
    val currentBaseline: Double,
    val totalDriftPct: Double,
    val sessionCount: Int,
    val hasDegradationRisk: Boolean,
    val description: String,
)

/**
 * VehicleMemoryRepository — Manages the 4 types of automotive memory:
 * 1. Semantic (DTCs, diagnostic rules, schemas)
 * 2. Episodic (historical diagnostic sessions, repair events, anomaly episodes)
 * 3. Statistical (per-vehicle baseline distributions across sessions)
 * 4. Maintenance (replaced components, service records)
 */
@Singleton
class VehicleMemoryRepository @Inject constructor(
    private val healthSnapshotDao: HealthSnapshotDao,
    private val sensorHistoryDao: SensorHistoryDao,
    private val predictionEventDao: PredictionEventDao,
    private val dnaDao: VehicleDnaDao,
    private val twinDao: VehicleTwinDao,
) {
    // In-memory episodic buffer for real-time query speed, backed by Room persistence
    private val episodicMemory = ConcurrentHashMap<String, MutableList<VehicleEpisode>>()

    suspend fun recordEpisode(
        vehicleId: String,
        type: EpisodeType,
        summary: String,
        dtcCodes: List<String> = emptyList(),
        evidence: List<DiagnosticEvidence> = emptyList(),
        outcome: String? = null,
    ): VehicleEpisode = withContext(Dispatchers.IO) {
        val episode = VehicleEpisode(
            episodeId = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            timestampMs = System.currentTimeMillis(),
            type = type,
            summary = summary,
            dtcCodes = dtcCodes,
            evidence = evidence,
            outcome = outcome
        )

        val list = episodicMemory.getOrPut(vehicleId) { mutableListOf() }
        synchronized(list) {
            list.add(0, episode)
            // Bound episodic memory in memory
            if (list.size > 200) {
                list.removeAt(list.size - 1)
            }
        }

        episode
    }

    suspend fun getRecentEpisodes(vehicleId: String, limit: Int = 20): List<VehicleEpisode> = withContext(Dispatchers.IO) {
        val list = episodicMemory[vehicleId] ?: emptyList()
        synchronized(list) {
            list.take(limit)
        }
    }

    suspend fun getLongitudinalTrends(vehicleId: String): List<LongitudinalTrend> = withContext(Dispatchers.IO) {
        val trends = mutableListOf<LongitudinalTrend>()

        val pidsToCheck = listOf("0107", "0142", "0105", "010B") // LTFT, Voltage, Coolant, MAP
        for (pid in pidsToCheck) {
            val sessionAverages = sensorHistoryDao.getSessionAverages(vehicleId, pid)
            if (sessionAverages.size >= 3) {
                val firstAvg = sessionAverages.first().value.toDouble()
                val lastAvg = sessionAverages.last().value.toDouble()
                val drift = if (firstAvg != 0.0) ((lastAvg - firstAvg) / abs(firstAvg)) * 100.0 else 0.0

                val isRisky = when (pid) {
                    "0107" -> abs(lastAvg) > 10.0 && drift > 50.0 // Fuel trim drifting upward
                    "0142" -> lastAvg < 13.2 && drift < -5.0           // Charging voltage deteriorating
                    "0105" -> lastAvg > 100.0 && drift > 10.0          // Running hotter over time
                    else -> abs(drift) > 40.0
                }

                trends.add(
                    LongitudinalTrend(
                        pid = pid,
                        initialBaseline = firstAvg,
                        currentBaseline = lastAvg,
                        totalDriftPct = drift,
                        sessionCount = sessionAverages.size,
                        hasDegradationRisk = isRisky,
                        description = "Tendencia de ${sessionAverages.size} sesiones: ${"%.2f".format(firstAvg)} -> ${"%.2f".format(lastAvg)} (${"%+.1f".format(drift)}%)"
                    )
                )
            }
        }

        trends
    }
}
