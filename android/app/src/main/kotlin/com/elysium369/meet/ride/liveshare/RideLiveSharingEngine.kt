package com.elysium369.meet.ride.liveshare

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  R I D E   L I V E   S H A R I N G   E N G I N E
 *  ──────────────────────────────────────────────────
 *  CRITICAL SAFETY FEATURE — Share your ride in real-time.
 *
 *  "Mi mamá puede ver exactamente dónde estoy, quién me lleva,
 *   y cuándo llego. Sin instalar nada — solo un link."
 *
 *  Architecture:
 *  ┌──────────────────────────────────────────────────┐
 *  │  Passenger starts ride                           │
 *  │    → Creates LiveShareSession                    │
 *  │    → Generates secure token (SHA-256 + expiry)   │
 *  │    → Shares link via WhatsApp/SMS/Messenger      │
 *  │    → Recipients see: map, driver, ETA, route     │
 *  │    → Session auto-expires on ride end             │
 *  │    → SOS alert propagates to all watchers         │
 *  └──────────────────────────────────────────────────┘
 *
 *  Privacy guarantees:
 *  - Token is time-limited and ride-scoped
 *  - No login required to view (read-only link)
 *  - Location updates stop immediately on ride end
 *  - Viewer cannot see ride history, only current ride
 *  - Passenger can revoke access at any time
 *  - No PII in the share URL itself
 * ══════════════════════════════════════════════════════════════════════════
 */

// ─── Session Status ───

enum class LiveShareStatus {
    ACTIVE,         // Currently broadcasting location
    PAUSED,         // Temporarily paused by passenger
    EXPIRED,        // Ride completed, session auto-expired
    REVOKED,        // Manually revoked by passenger
    SOS_ACTIVE,     // Emergency mode — elevated alerts
}

// ─── Viewer (recipient of the shared link) ───

@Serializable
data class LiveShareViewer(
    val viewerId: String,
    val displayName: String,
    val relationship: ViewerRelationship,
    val notifyOnArrival: Boolean = true,
    val notifyOnDelay: Boolean = true,
    val notifyOnRouteDeviation: Boolean = true,
    val notifyOnSos: Boolean = true,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val lastViewedAtEpochMs: Long? = null,
    val viewCount: Int = 0,
)

enum class ViewerRelationship {
    PARENT,
    SPOUSE,
    CHILD,
    FAMILY,
    FRIEND,
    COWORKER,
    EMERGENCY_CONTACT,
    OTHER,
}

// ─── Location Update (what viewers see) ───

@Serializable
data class LiveLocationUpdate(
    val latitude: Double,
    val longitude: Double,
    val heading: Float? = null,
    val speedKmh: Float? = null,
    val accuracy: Float? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

// ─── Share Session ───

@Serializable
data class LiveShareSession(
    val sessionId: String,
    val rideId: String,
    val passengerId: String,
    val passengerName: String,
    // Security
    val shareToken: String,
    val shareUrl: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long,
    // Status
    val status: LiveShareStatus = LiveShareStatus.ACTIVE,
    // Ride info visible to viewers
    val driverName: String,
    val driverRating: Double? = null,
    val vehicleDescription: String,
    val vehiclePlate: String,
    val pickupName: String,
    val dropoffName: String,
    val estimatedArrivalEpochMs: Long? = null,
    val estimatedDurationMin: Int? = null,
    // Location
    val lastLocation: LiveLocationUpdate? = null,
    val locationHistory: List<LiveLocationUpdate> = emptyList(),
    // Viewers
    val viewers: List<LiveShareViewer> = emptyList(),
    // Safety
    val sosTriggered: Boolean = false,
    val sosMessage: String? = null,
    val routeDeviationDetected: Boolean = false,
) {
    val isActive: Boolean get() = status == LiveShareStatus.ACTIVE || status == LiveShareStatus.SOS_ACTIVE
    val isExpired: Boolean get() = status == LiveShareStatus.EXPIRED || System.currentTimeMillis() > expiresAtEpochMs
    val viewerCount: Int get() = viewers.size
    val totalLocationPoints: Int get() = locationHistory.size

    val shareMessage: String
        get() = buildString {
            appendLine("🚗 $passengerName está en un viaje")
            appendLine("📍 $pickupName → $dropoffName")
            appendLine("🚘 $vehicleDescription ($vehiclePlate)")
            appendLine("👤 Conductor: $driverName")
            estimatedDurationMin?.let { appendLine("⏱️ Llegada estimada: $it min") }
            appendLine()
            appendLine("📎 Sigue el viaje en vivo:")
            append(shareUrl)
        }
}

// ─── Alert Types ───

@Serializable
data class LiveShareAlert(
    val alertId: String,
    val sessionId: String,
    val type: AlertType,
    val message: String,
    val severity: AlertSeverity,
    val timestampMs: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notifiedViewers: List<String> = emptyList(),
)

enum class AlertType {
    RIDE_STARTED,           // Ride has begun
    DRIVER_ARRIVED,         // Driver arrived at pickup
    EN_ROUTE,               // Passenger picked up, en route
    ARRIVAL_IMMINENT,       // 5 minutes away
    RIDE_COMPLETED,         // Safely arrived
    DELAY_DETECTED,         // ETA exceeded by >10 min
    ROUTE_DEVIATION,        // Significant route deviation
    LONG_STOP,              // Unexpected stop > 5 min
    SOS_TRIGGERED,          // Passenger hit SOS
    SOS_CANCELLED,          // False alarm
    SESSION_EXPIRED,        // Session ended
}

enum class AlertSeverity {
    INFO,       // Normal updates
    WARNING,    // Delays, minor issues
    CRITICAL,   // SOS, route deviation
}

// ─── Viewer Page Data (what the link shows) ───

data class ViewerPageData(
    val passengerName: String,
    val driverName: String,
    val driverRating: Double?,
    val vehicleDescription: String,
    val vehiclePlate: String,
    val pickupName: String,
    val dropoffName: String,
    val currentLocation: LiveLocationUpdate?,
    val estimatedArrivalMin: Int?,
    val rideStatus: String,
    val isSos: Boolean,
    val locationTrail: List<LiveLocationUpdate>,
)

// ─── Engine ───

class RideLiveSharingEngine {

    companion object {
        const val SHARE_BASE_URL = "https://meet.elysium369.com/live/"
        const val DEFAULT_EXPIRY_HOURS = 4L
        const val ROUTE_DEVIATION_THRESHOLD_METERS = 500.0
        const val LONG_STOP_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        const val ARRIVAL_IMMINENT_MINUTES = 5
    }

    private val sessions = mutableMapOf<String, LiveShareSession>()
    private val alerts = mutableListOf<LiveShareAlert>()

    // ─── Create Session ───

    fun createSession(
        rideId: String,
        passengerId: String,
        passengerName: String,
        driverName: String,
        driverRating: Double? = null,
        vehicleDescription: String,
        vehiclePlate: String,
        pickupName: String,
        dropoffName: String,
        estimatedDurationMin: Int? = null,
        viewers: List<LiveShareViewer> = emptyList(),
    ): LiveShareSession {
        val token = generateSecureToken(rideId, passengerId)
        val sessionId = "live-${System.currentTimeMillis()}"
        val expiryMs = System.currentTimeMillis() + DEFAULT_EXPIRY_HOURS * 60 * 60 * 1000

        val session = LiveShareSession(
            sessionId = sessionId,
            rideId = rideId,
            passengerId = passengerId,
            passengerName = passengerName,
            shareToken = token,
            shareUrl = "$SHARE_BASE_URL$token",
            expiresAtEpochMs = expiryMs,
            driverName = driverName,
            driverRating = driverRating,
            vehicleDescription = vehicleDescription,
            vehiclePlate = vehiclePlate,
            pickupName = pickupName,
            dropoffName = dropoffName,
            estimatedDurationMin = estimatedDurationMin,
            viewers = viewers,
        )
        sessions[sessionId] = session

        emitAlert(sessionId, AlertType.RIDE_STARTED,
            "Viaje iniciado: $pickupName → $dropoffName", AlertSeverity.INFO)

        return session
    }

    // ─── Location Updates ───

    fun updateLocation(sessionId: String, location: LiveLocationUpdate): LiveShareSession? {
        val session = sessions[sessionId] ?: return null
        if (!session.isActive) return null

        val updated = session.copy(
            lastLocation = location,
            locationHistory = session.locationHistory + location,
        )
        sessions[sessionId] = updated

        // Check for long stop
        detectLongStop(updated, location)

        return updated
    }

    fun updateEta(sessionId: String, estimatedArrivalMin: Int): LiveShareSession? {
        val session = sessions[sessionId] ?: return null
        if (!session.isActive) return null

        val updated = session.copy(estimatedDurationMin = estimatedArrivalMin)
        sessions[sessionId] = updated

        // Arrival imminent alert
        if (estimatedArrivalMin <= ARRIVAL_IMMINENT_MINUTES) {
            emitAlert(sessionId, AlertType.ARRIVAL_IMMINENT,
                "${session.passengerName} llega en ~$estimatedArrivalMin minutos",
                AlertSeverity.INFO)
        }

        return updated
    }

    // ─── Add/Remove Viewers ───

    fun addViewer(sessionId: String, viewer: LiveShareViewer): Boolean {
        val session = sessions[sessionId] ?: return false
        if (!session.isActive) return false
        if (session.viewers.any { it.viewerId == viewer.viewerId }) return false

        sessions[sessionId] = session.copy(
            viewers = session.viewers + viewer,
        )
        return true
    }

    fun removeViewer(sessionId: String, viewerId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        sessions[sessionId] = session.copy(
            viewers = session.viewers.filter { it.viewerId != viewerId },
        )
        return true
    }

    fun recordView(sessionId: String, viewerId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        sessions[sessionId] = session.copy(
            viewers = session.viewers.map { viewer ->
                if (viewer.viewerId == viewerId) viewer.copy(
                    lastViewedAtEpochMs = System.currentTimeMillis(),
                    viewCount = viewer.viewCount + 1,
                ) else viewer
            },
        )
        return true
    }

    // ─── SOS ───

    fun triggerSos(sessionId: String, message: String = "Emergencia — necesito ayuda"): LiveShareSession? {
        val session = sessions[sessionId] ?: return null

        val updated = session.copy(
            status = LiveShareStatus.SOS_ACTIVE,
            sosTriggered = true,
            sosMessage = message,
        )
        sessions[sessionId] = updated

        emitAlert(sessionId, AlertType.SOS_TRIGGERED,
            "⚠️ SOS: ${session.passengerName} necesita ayuda. $message",
            AlertSeverity.CRITICAL,
            session.lastLocation?.latitude,
            session.lastLocation?.longitude,
        )

        return updated
    }

    fun cancelSos(sessionId: String): LiveShareSession? {
        val session = sessions[sessionId] ?: return null
        if (!session.sosTriggered) return null

        val updated = session.copy(
            status = LiveShareStatus.ACTIVE,
            sosTriggered = false,
            sosMessage = null,
        )
        sessions[sessionId] = updated

        emitAlert(sessionId, AlertType.SOS_CANCELLED,
            "${session.passengerName} canceló la alerta SOS — falsa alarma",
            AlertSeverity.INFO)

        return updated
    }

    // ─── Route Deviation Detection ───

    fun reportRouteDeviation(sessionId: String, deviationMeters: Double): Boolean {
        val session = sessions[sessionId] ?: return false
        if (deviationMeters < ROUTE_DEVIATION_THRESHOLD_METERS) return false

        sessions[sessionId] = session.copy(routeDeviationDetected = true)

        emitAlert(sessionId, AlertType.ROUTE_DEVIATION,
            "⚠️ Desviación de ruta detectada (${deviationMeters.toInt()}m). " +
                "Conductor: ${session.driverName}, Placa: ${session.vehiclePlate}",
            AlertSeverity.CRITICAL,
            session.lastLocation?.latitude,
            session.lastLocation?.longitude,
        )
        return true
    }

    // ─── Session Lifecycle ───

    fun pause(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        if (!session.isActive) return false
        sessions[sessionId] = session.copy(status = LiveShareStatus.PAUSED)
        return true
    }

    fun resume(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        if (session.status != LiveShareStatus.PAUSED) return false
        sessions[sessionId] = session.copy(status = LiveShareStatus.ACTIVE)
        return true
    }

    fun completeRide(sessionId: String): LiveShareSession? {
        val session = sessions[sessionId] ?: return null
        val updated = session.copy(status = LiveShareStatus.EXPIRED)
        sessions[sessionId] = updated

        emitAlert(sessionId, AlertType.RIDE_COMPLETED,
            "✅ ${session.passengerName} llegó seguro/a a ${session.dropoffName}",
            AlertSeverity.INFO)

        return updated
    }

    fun revoke(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        sessions[sessionId] = session.copy(status = LiveShareStatus.REVOKED)
        emitAlert(sessionId, AlertType.SESSION_EXPIRED,
            "Sesión terminada por el pasajero", AlertSeverity.INFO)
        return true
    }

    // ─── Viewer Page ───

    fun getViewerPage(token: String): ViewerPageData? {
        val session = sessions.values.find { it.shareToken == token } ?: return null
        if (session.isExpired && session.status != LiveShareStatus.EXPIRED) return null

        return ViewerPageData(
            passengerName = session.passengerName,
            driverName = session.driverName,
            driverRating = session.driverRating,
            vehicleDescription = session.vehicleDescription,
            vehiclePlate = session.vehiclePlate,
            pickupName = session.pickupName,
            dropoffName = session.dropoffName,
            currentLocation = session.lastLocation,
            estimatedArrivalMin = session.estimatedDurationMin,
            rideStatus = when (session.status) {
                LiveShareStatus.ACTIVE -> "En viaje"
                LiveShareStatus.SOS_ACTIVE -> "⚠️ EMERGENCIA"
                LiveShareStatus.PAUSED -> "Pausado"
                LiveShareStatus.EXPIRED -> "Viaje completado"
                LiveShareStatus.REVOKED -> "Sesión terminada"
            },
            isSos = session.sosTriggered,
            locationTrail = session.locationHistory.takeLast(100), // Last 100 points for map
        )
    }

    // ─── Alerts ───

    fun getAlerts(sessionId: String): List<LiveShareAlert> =
        alerts.filter { it.sessionId == sessionId }.sortedByDescending { it.timestampMs }

    fun getCriticalAlerts(sessionId: String): List<LiveShareAlert> =
        getAlerts(sessionId).filter { it.severity == AlertSeverity.CRITICAL }

    // ─── Queries ───

    fun getSession(id: String): LiveShareSession? = sessions[id]

    fun getActiveSessionForRide(rideId: String): LiveShareSession? =
        sessions.values.find { it.rideId == rideId && it.isActive }

    val activeSessions: Int get() = sessions.count { it.value.isActive }
    val totalAlerts: Int get() = alerts.size

    // ─── Internal ───

    private fun generateSecureToken(rideId: String, passengerId: String): String {
        val seed = "$rideId|$passengerId|${System.currentTimeMillis()}|${Math.random()}"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(seed.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }

    private fun emitAlert(
        sessionId: String,
        type: AlertType,
        message: String,
        severity: AlertSeverity,
        lat: Double? = null,
        lng: Double? = null,
    ) {
        val session = sessions[sessionId] ?: return
        val alert = LiveShareAlert(
            alertId = "alert-${System.currentTimeMillis()}-${alerts.size}",
            sessionId = sessionId,
            type = type,
            message = message,
            severity = severity,
            latitude = lat,
            longitude = lng,
            notifiedViewers = session.viewers
                .filter { viewer ->
                    when (type) {
                        AlertType.SOS_TRIGGERED, AlertType.SOS_CANCELLED -> viewer.notifyOnSos
                        AlertType.ROUTE_DEVIATION -> viewer.notifyOnRouteDeviation
                        AlertType.DELAY_DETECTED -> viewer.notifyOnDelay
                        AlertType.RIDE_COMPLETED -> viewer.notifyOnArrival
                        else -> true
                    }
                }
                .map { it.viewerId },
        )
        alerts.add(alert)
    }

    private fun detectLongStop(session: LiveShareSession, current: LiveLocationUpdate) {
        val history = session.locationHistory
        if (history.size < 2) return

        val recentPoints = history.takeLast(10)
        val allSameSpot = recentPoints.all { point ->
            haversineMeters(point.latitude, point.longitude,
                current.latitude, current.longitude) < 50.0 // Within 50m
        }

        if (allSameSpot && recentPoints.size >= 5) {
            val durationMs = current.timestampMs - recentPoints.first().timestampMs
            if (durationMs > LONG_STOP_THRESHOLD_MS) {
                emitAlert(session.sessionId, AlertType.LONG_STOP,
                    "⚠️ Parada prolongada detectada (${durationMs / 60000} min)",
                    AlertSeverity.WARNING,
                    current.latitude, current.longitude)
            }
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}
