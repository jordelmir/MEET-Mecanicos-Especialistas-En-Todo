package com.elysium369.meet.safejourney

import android.util.Log
import com.elysium369.meet.presence.PresenceLocation
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SafeJourneyKernel — Singleton authority for shared trip monitoring.
 *
 * Laws:
 * - Check-in reminders at configured intervals
 * - Overdue detection (2x interval with no check-in)
 * - Safety alerts for deviations, excessive speed, prolonged stops
 * - Emergency escalation to contacts
 * - No response != emergency
 */
@Singleton
class SafeJourneyKernel @Inject constructor() {

    private val journeys = mutableMapOf<String, SafeJourney>()
    private val checkIns = mutableMapOf<String, MutableList<CheckIn>>()
    private val safetyAlerts = mutableMapOf<String, MutableList<SafetyAlert>>()

    /** Create a safe journey. */
    fun createJourney(
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
        val journey = SafeJourney(
            journeyId = journeyId,
            principalId = principalId,
            name = name,
            origin = origin,
            destination = destination,
            destinationName = destinationName,
            estimatedArrivalEpochMs = estimatedArrivalEpochMs,
            state = JourneyState.PLANNED,
            createdAtEpochMs = System.currentTimeMillis(),
            sharedWithPrincipalIds = sharedWithPrincipalIds,
            checkInIntervalMs = checkInIntervalMs,
        )
        journeys[journeyId] = journey
        checkIns[journeyId] = mutableListOf()
        safetyAlerts[journeyId] = mutableListOf()

        Log.i("SafeJourneyKernel", "Journey created: $journeyId ($name)")
        return journey
    }

    /** Start a journey. */
    fun startJourney(journeyId: String): SafeJourney? {
        val journey = journeys[journeyId] ?: return null
        if (journey.state != JourneyState.PLANNED) return null
        val updated = journey.copy(
            state = JourneyState.ACTIVE,
            startedAtEpochMs = System.currentTimeMillis(),
            lastCheckInAtEpochMs = System.currentTimeMillis(),
        )
        journeys[journeyId] = updated
        Log.i("SafeJourneyKernel", "Journey started: $journeyId")
        return updated
    }

    /** Complete a journey. */
    fun completeJourney(journeyId: String): SafeJourney? {
        val journey = journeys[journeyId] ?: return null
        if (!journey.state.isActive) return null
        val updated = journey.copy(
            state = JourneyState.COMPLETED,
            completedAtEpochMs = System.currentTimeMillis(),
        )
        journeys[journeyId] = updated
        Log.i("SafeJourneyKernel", "Journey completed: $journeyId")
        return updated
    }

    /** Cancel a journey. */
    fun cancelJourney(journeyId: String): SafeJourney? {
        val journey = journeys[journeyId] ?: return null
        if (journey.state.isTerminal) return null
        val updated = journey.copy(state = JourneyState.CANCELLED)
        journeys[journeyId] = updated
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

        // Update journey
        journeys[journeyId]?.let { j ->
            journeys[journeyId] = j.copy(
                lastCheckInAtEpochMs = System.currentTimeMillis(),
                lastKnownLocation = location,
            )
        }

        Log.i("SafeJourneyKernel", "Check-in recorded: ${checkIn.checkInId} on $journeyId")
        return checkIn
    }

    /** Check for overdue journeys and create alerts. */
    fun checkOverdueJourneys(): List<SafetyAlert> {
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<SafetyAlert>()

        for (journey in journeys.values.filter { it.state == JourneyState.ACTIVE }) {
            if (journey.isOverdue(now)) {
                val alert = SafetyAlert(
                    alertId = UUID.randomUUID().toString(),
                    journeyId = journey.journeyId,
                    principalId = journey.principalId,
                    type = SafetyAlertType.CHECK_IN_MISSED,
                    message = "Journey '${journey.name}' is overdue — no check-in received",
                    location = journey.lastKnownLocation,
                    createdAtEpochMs = now,
                )
                safetyAlerts.getOrPut(journey.journeyId) { mutableListOf() }.add(alert)
                alerts.add(alert)

                // Escalate to MISSED_CHECK_IN state
                journeys[journey.journeyId]?.let { j ->
                    journeys[journey.journeyId] = j.copy(state = JourneyState.MISSED_CHECK_IN)
                }
            }
        }

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

        // Escalate if critical
        if (type.isCritical) {
            journeys[journeyId]?.let { j ->
                journeys[journeyId] = j.copy(state = JourneyState.EMERGENCY)
            }
        }

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
    fun getJourneysForPrincipal(principalId: String): List<SafeJourney> {
        return journeys.values.filter { it.principalId == principalId }
    }

    /** Get active journeys. */
    fun getActiveJourneys(): List<SafeJourney> {
        return journeys.values.filter { it.state.isActive }
    }

    /** Get check-ins for a journey. */
    fun getCheckIns(journeyId: String): List<CheckIn> {
        return checkIns[journeyId] ?: emptyList()
    }

    /** Get safety alerts for a journey. */
    fun getSafetyAlerts(journeyId: String): List<SafetyAlert> {
        return safetyAlerts[journeyId] ?: emptyList()
    }

    /** Cleanup completed journeys older than threshold. */
    fun cleanup(maxAgeMs: Long = 30 * 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        journeys.entries.removeIf { (_, j) ->
            j.state.isTerminal && (j.completedAtEpochMs ?: j.createdAtEpochMs) < cutoff
        }
    }
}
