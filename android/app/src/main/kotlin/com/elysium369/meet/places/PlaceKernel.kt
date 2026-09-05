package com.elysium369.meet.places

import android.util.Log
import com.elysium369.meet.presence.PresenceLocation
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlaceKernel — Singleton authority for geofencing and place observations.
 *
 * Laws:
 * - Dwell time required before triggering events
 * - Hysteresis prevents rapid in/out cycling
 * - Cooldown between notifications
 * - Privacy zones never share precise location
 * - User-defined rules respected
 */
@Singleton
class PlaceKernel @Inject constructor() {

    private val places = ConcurrentHashMap<String, Place>()
    private val observations = ConcurrentHashMap<String, MutableList<PlaceObservation>>()
    private val lastEvents = ConcurrentHashMap<String, ConcurrentHashMap<String, PlaceEvent>>()
    private val lastNotificationMs = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    /** Register a place. */
    fun registerPlace(place: Place): Place {
        places[place.placeId] = place
        observations[place.placeId] = mutableListOf()
        lastEvents[place.placeId] = ConcurrentHashMap()
        lastNotificationMs[place.placeId] = ConcurrentHashMap()
        Log.i("PlaceKernel", "Place registered: ${place.placeId} (${place.name})")
        return place
    }

    /** Update a place. */
    fun updatePlace(placeId: String, update: (Place) -> Place): Place? {
        val existing = places[placeId] ?: return null
        val updated = update(existing)
        places[placeId] = updated
        return updated
    }

    /** Delete a place. */
    fun deletePlace(placeId: String): Boolean {
        return places.remove(placeId) != null
    }

    /** Get all places for a principal. */
    fun getPlacesForPrincipal(principalId: String): List<Place> {
        return places.values.filter { it.principalId == principalId }
    }

    /** Get a place by ID. */
    fun getPlace(placeId: String): Place? = places[placeId]

    /** Process a location update and check for geofence transitions. */
    fun processLocationUpdate(
        principalId: String,
        location: PresenceLocation,
        timestampEpochMs: Long,
    ): List<PlaceEventNotification> {
        val notifications = mutableListOf<PlaceEventNotification>()
        val principalPlaces = getPlacesForPrincipal(principalId)

        for (place in principalPlaces) {
            val wasInside = lastEvents[place.placeId]?.get(principalId) == PlaceEvent.ENTERED ||
                lastEvents[place.placeId]?.get(principalId) == PlaceEvent.DWELLING
            val isInside = PlacePolicy.isInsideBoundary(location, place)

            val observation = PlaceObservation(
                placeId = place.placeId,
                principalId = principalId,
                event = if (isInside) PlaceEvent.ENTERED else PlaceEvent.EXITED,
                location = location,
                observedAtEpochMs = timestampEpochMs,
                accuracyMeters = location.accuracyMeters,
            )
            observations[place.placeId]?.add(observation)

            when {
                !wasInside && isInside -> {
                    // Entered
                    lastEvents[place.placeId]?.put(principalId, PlaceEvent.ENTERED)
                    Log.i("PlaceKernel", "$principalId entered ${place.name}")
                }
                wasInside && !isInside -> {
                    // Exited
                    lastEvents[place.placeId]?.put(principalId, PlaceEvent.EXITED)
                    val dwellMs = timestampEpochMs - (observations[place.placeId]
                        ?.lastOrNull { it.event == PlaceEvent.ENTERED }?.observedAtEpochMs ?: timestampEpochMs)

                    // Check rules for notifications
                    for (rule in place.rules) {
                        if (rule.eventType == PlaceEvent.EXITED) {
                            val lastNotif = lastNotificationMs[place.placeId]?.get(rule.ruleId)
                            if (PlacePolicy.shouldNotify(rule, lastNotif, timestampEpochMs)) {
                                notifications.add(PlaceEventNotification(
                                    placeId = place.placeId,
                                    placeName = place.name,
                                    eventType = PlaceEvent.EXITED,
                                    principalId = principalId,
                                    principalName = principalId,
                                    location = location,
                                    timestampEpochMs = timestampEpochMs,
                                    message = "${principalId} left ${place.name}",
                                ))
                                lastNotificationMs[place.placeId]?.put(rule.ruleId, timestampEpochMs)
                            }
                        }
                    }

                    Log.i("PlaceKernel", "$principalId left ${place.name}")
                }
            }
        }

        return notifications
    }

    /** Get observations for a place. */
    fun getObservations(placeId: String): List<PlaceObservation> {
        return observations[placeId]?.toList() ?: emptyList()
    }

    /** Get last known event for a principal at a place. */
    fun getLastEvent(placeId: String, principalId: String): PlaceEvent? {
        return lastEvents[placeId]?.get(principalId)
    }

    /** Cleanup old observations. */
    fun cleanup(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        observations.values.forEach { list ->
            list.removeAll { it.observedAtEpochMs < cutoff }
        }
    }
}
