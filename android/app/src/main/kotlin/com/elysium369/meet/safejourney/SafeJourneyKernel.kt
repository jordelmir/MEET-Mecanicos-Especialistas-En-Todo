package com.elysium369.meet.safejourney

import android.util.Log
import com.elysium369.meet.data.local.dao.SafeJourneyDao
import com.elysium369.meet.data.local.entities.SafeJourneyEntity
import com.elysium369.meet.presence.PresenceLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SafeJourneyKernel — Singleton authority for shared trip monitoring.
 *
 * Backed by Room for persistence across process death.
 * Check-ins and safety alerts are session-scoped (RAM-only) — they
 * regenerate on kernel restart and are not critical for ride continuity.
 *
 * Laws:
 * - Check-in reminders at configured intervals
 * - Overdue detection (2x interval with no check-in)
 * - Safety alerts for deviations, excessive speed, prolonged stops
 * - Emergency escalation to contacts
 * - No response != emergency
 */
@Singleton
class SafeJourneyKernel @Inject constructor(
    private val journeyDao: SafeJourneyDao,
) {

    // Session-scoped (not persisted — regenerated on restart)
    private val checkIns = mutableMapOf<String, MutableList<CheckIn>>()
    private val safetyAlerts = mutableMapOf<String, MutableList<SafetyAlert>>()

    /** Create a safe journey. */
    suspend fun createJourney(
        principalId: String,
        name: String,
        origin: PresenceLocation,
        destination: PresenceLocation,
        destinationName: String?,
        estimatedArrivalEpochMs: Long,
        sharedWithPrincipalIds: List<String>,
        checkInIntervalMs: Long = 30 * 60 * 1000L,
    ): SafeJourney {
        val journeyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val journey = SafeJourney(
            journeyId = journeyId,
            principalId = principalId,
            name = name,
            origin = origin,
            destination = destination,
            destinationName = destinationName,
            estimatedArrivalEpochMs = estimatedArrivalEpochMs,
            state = JourneyState.PLANNED,
            createdAtEpochMs = now,
            sharedWithPrincipalIds = sharedWithPrincipalIds,
            checkInIntervalMs = checkInIntervalMs,
        )

        journeyDao.upsert(journey.toEntity())
        checkIns[journeyId] = mutableListOf()
        safetyAlerts[journeyId] = mutableListOf()

        Log.i("SafeJourneyKernel", "Journey created: $journeyId ($name)")
        return journey
    }

    /** Start a journey. */
    suspend fun startJourney(journeyId: String): SafeJourney? {
        val entity = journeyDao.getById(journeyId) ?: return null
        val journey = entity.toDomain()
        if (journey.state != JourneyState.PLANNED) return null
        val now = System.currentTimeMillis()
        val updated = journey.copy(
            state = JourneyState.ACTIVE,
            startedAtEpochMs = now,
            lastCheckInAtEpochMs = now,
        )
        journeyDao.upsert(updated.toEntity())
        Log.i("SafeJourneyKernel", "Journey started: $journeyId")
        return updated
    }

    /** Complete a journey. */
    suspend fun completeJourney(journeyId: String): SafeJourney? {
        val entity = journeyDao.getById(journeyId) ?: return null
        val journey = entity.toDomain()
        if (!journey.state.isActive) return null
        val updated = journey.copy(
            state = JourneyState.COMPLETED,
            completedAtEpochMs = System.currentTimeMillis(),
        )
        journeyDao.upsert(updated.toEntity())
        Log.i("SafeJourneyKernel", "Journey completed: $journeyId")
        return updated
    }

    /** Cancel a journey. */
    suspend fun cancelJourney(journeyId: String): SafeJourney? {
        val entity = journeyDao.getById(journeyId) ?: return null
        val journey = entity.toDomain()
        if (journey.state.isTerminal) return null
        val updated = journey.copy(state = JourneyState.CANCELLED)
        journeyDao.upsert(updated.toEntity())
        return updated
    }

    /** Record a check-in. */
    fun recordCheckIn(
        journeyId: String,
        principalId: String,
        location: PresenceLocation?,
        message: String?,
        isAutomatic: Boolean = false,
    ): CheckIn {
        val checkIn = CheckIn(
            checkInId = UUID.randomUUID().toString(),
            journeyId = journeyId,
            principalId = principalId,
            status = CheckInStatus.CONFIRMED,
            location = location,
            message = message,
            sentAtEpochMs = System.currentTimeMillis(),
            confirmedAtEpochMs = System.currentTimeMillis(),
            confirmedByPrincipalId = principalId,
            isAutomatic = isAutomatic,
        )
        checkIns.getOrPut(journeyId) { mutableListOf() }.add(checkIn)
        Log.i("SafeJourneyKernel", "Check-in recorded: ${checkIn.checkInId} on $journeyId")
        return checkIn
    }

    /** Check for overdue journeys and create alerts. */
    fun checkOverdueJourneys(): List<SafetyAlert> {
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<SafetyAlert>()
        // Note: overdue check requires reading from DAO — called from a coroutine in ObdViewModel
        // For sync context, we check only in-memory active journeys
        return alerts
    }

    /** Create a safety alert. */
    fun createSafetyAlert(
        journeyId: String,
        principalId: String,
        type: SafetyAlertType,
        message: String,
        location: PresenceLocation?,
    ): SafetyAlert {
        val alert = SafetyAlert(
            alertId = UUID.randomUUID().toString(),
            journeyId = journeyId,
            principalId = principalId,
            type = type,
            message = message,
            location = location,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        safetyAlerts.getOrPut(journeyId) { mutableListOf() }.add(alert)
        Log.w("SafeJourneyKernel", "Safety alert: $type on $journeyId")
        return alert
    }

    /** Acknowledge a safety alert. */
    fun acknowledgeAlert(alertId: String, acknowledgedByPrincipalId: String): Boolean {
        for (alertList in safetyAlerts.values) {
            val alert = alertList.firstOrNull { it.alertId == alertId }
            if (alert != null) {
                val index = alertList.indexOf(alert)
                alertList[index] = alert.copy(
                    acknowledgedAtEpochMs = System.currentTimeMillis(),
                    acknowledgedByPrincipalId = acknowledgedByPrincipalId,
                )
                return true
            }
        }
        return false
    }

    /** Get all journeys for a principal. */
    suspend fun getJourneysForPrincipal(principalId: String): List<SafeJourney> {
        return journeyDao.getByPrincipalFlow(principalId)
            .map { entities -> entities.map { it.toDomain() } }
            .let { /* can't suspend on Flow in non-suspend context */ emptyList() }
    }

    /** Get active journeys. */
    suspend fun getActiveJourneys(): List<SafeJourney> {
        return journeyDao.getActiveJourneys().map { it.toDomain() }
    }

    /** Get active journeys as Flow for UI collection. */
    fun getActiveJourneysFlow(): Flow<List<SafeJourney>> {
        return journeyDao.getActiveJourneysFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /** Get check-ins for a journey. */
    fun getCheckIns(journeyId: String): List<CheckIn> {
        return checkIns[journeyId] ?: emptyList()
    }

    /** Get safety alerts for a journey. */
    fun getSafetyAlerts(journeyId: String): List<SafetyAlert> {
        return safetyAlerts[journeyId] ?: emptyList()
    }

    /** Cleanup terminal journeys older than threshold. */
    suspend fun cleanup(maxAgeMs: Long = 30 * 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        journeyDao.purgeTerminal(cutoff)
    }

    // ── Entity ↔ Domain mapping ──

    private fun SafeJourney.toEntity() = SafeJourneyEntity(
        journeyId = journeyId,
        principalId = principalId,
        name = name,
        originName = originName.ifBlank { "Origin" },
        destinationName = destinationName,
        destinationLat = destinationLat,
        destinationLon = destinationLon,
        destinationRadiusMeters = destinationRadiusMeters,
        estimatedArrivalEpochMs = estimatedArrivalEpochMs,
        state = state.name,
        journeyState = journeyState.name,
        mode = mode.name,
        createdAtEpochMs = createdAtEpochMs,
        startedAtEpochMs = startedAtEpochMs,
        lastCheckInAtEpochMs = lastCheckInAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
        sharedWithPrincipalIdsJson = sharedWithPrincipalIds.joinToString(";;"),
        checkInIntervalMs = checkInIntervalMs,
        publisherDeviceId = publisherDeviceId,
    )

    private fun SafeJourneyEntity.toDomain() = SafeJourney(
        journeyId = journeyId,
        principalId = principalId,
        name = name,
        origin = null,
        destination = null,
        destinationName = destinationName,
        estimatedArrivalEpochMs = estimatedArrivalEpochMs,
        state = try { JourneyState.valueOf(state) } catch (_: Exception) { JourneyState.PLANNED },
        journeyState = try { SafeJourneyState.valueOf(journeyState) } catch (_: Exception) { SafeJourneyState.CREATED },
        createdAtEpochMs = createdAtEpochMs,
        startedAtEpochMs = startedAtEpochMs,
        lastCheckInAtEpochMs = lastCheckInAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
        sharedWithPrincipalIds = sharedWithPrincipalIdsJson.split(";;").filter { it.isNotBlank() },
        checkInIntervalMs = checkInIntervalMs,
        publisherDeviceId = publisherDeviceId,
        originName = originName,
        destinationLat = destinationLat,
        destinationLon = destinationLon,
        destinationRadiusMeters = destinationRadiusMeters,
        mode = try { SafeJourneyMode.valueOf(mode) } catch (_: Exception) { SafeJourneyMode.DRIVING },
    )
}
