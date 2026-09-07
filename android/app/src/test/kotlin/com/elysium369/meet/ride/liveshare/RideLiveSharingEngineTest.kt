package com.elysium369.meet.ride.liveshare

import org.junit.Assert.*
import org.junit.Test

class RideLiveSharingEngineTest {

    private fun engineWithSession(): Pair<RideLiveSharingEngine, LiveShareSession> {
        val e = RideLiveSharingEngine()
        val session = e.createSession(
            rideId = "ride-1", passengerId = "p1", passengerName = "María",
            driverName = "Carlos", driverRating = 4.8,
            vehicleDescription = "Toyota Corolla Gris 2020", vehiclePlate = "ABC-123",
            pickupName = "San José Centro", dropoffName = "Aeropuerto SJO",
            estimatedDurationMin = 25,
            viewers = listOf(
                LiveShareViewer("v1", "Mamá", ViewerRelationship.PARENT),
                LiveShareViewer("v2", "Ana", ViewerRelationship.FRIEND),
            ),
        )
        return e to session
    }

    @Test
    fun `create session with secure token and URL`() {
        val (_, s) = engineWithSession()
        assertTrue(s.isActive)
        assertTrue(s.shareToken.length == 32) // 16 bytes = 32 hex chars
        assertTrue(s.shareUrl.startsWith("https://meet.elysium369.com/live/"))
        assertEquals(2, s.viewerCount)
    }

    @Test
    fun `share message contains ride info but safe`() {
        val (_, s) = engineWithSession()
        val msg = s.shareMessage
        assertTrue(msg.contains("María"))
        assertTrue(msg.contains("Aeropuerto SJO"))
        assertTrue(msg.contains("Carlos"))
        assertTrue(msg.contains("ABC-123"))
    }

    @Test
    fun `update location builds trail`() {
        val (e, s) = engineWithSession()
        e.updateLocation(s.sessionId, LiveLocationUpdate(9.93, -84.08))
        e.updateLocation(s.sessionId, LiveLocationUpdate(9.94, -84.09))
        val updated = e.getSession(s.sessionId)!!
        assertEquals(2, updated.totalLocationPoints)
        assertNotNull(updated.lastLocation)
    }

    @Test
    fun `ETA update triggers arrival imminent alert`() {
        val (e, s) = engineWithSession()
        e.updateEta(s.sessionId, 3) // 3 minutes = imminent
        val alerts = e.getAlerts(s.sessionId)
        assertTrue(alerts.any { it.type == AlertType.ARRIVAL_IMMINENT })
    }

    @Test
    fun `SOS triggers critical alert to all viewers`() {
        val (e, s) = engineWithSession()
        val sos = e.triggerSos(s.sessionId, "Me siento en peligro")!!
        assertEquals(LiveShareStatus.SOS_ACTIVE, sos.status)
        assertTrue(sos.sosTriggered)
        val criticals = e.getCriticalAlerts(s.sessionId)
        assertTrue(criticals.any { it.type == AlertType.SOS_TRIGGERED })
        assertEquals(2, criticals.first().notifiedViewers.size) // Both viewers
    }

    @Test
    fun `cancel SOS restores active status`() {
        val (e, s) = engineWithSession()
        e.triggerSos(s.sessionId)
        val cancelled = e.cancelSos(s.sessionId)!!
        assertEquals(LiveShareStatus.ACTIVE, cancelled.status)
        assertFalse(cancelled.sosTriggered)
    }

    @Test
    fun `route deviation triggers critical alert`() {
        val (e, s) = engineWithSession()
        e.updateLocation(s.sessionId, LiveLocationUpdate(9.93, -84.08))
        assertTrue(e.reportRouteDeviation(s.sessionId, 800.0)) // 800m deviation
        assertTrue(e.getSession(s.sessionId)!!.routeDeviationDetected)
    }

    @Test
    fun `small deviation is ignored`() {
        val (e, s) = engineWithSession()
        assertFalse(e.reportRouteDeviation(s.sessionId, 200.0)) // 200m ok
    }

    @Test
    fun `add and remove viewers`() {
        val (e, s) = engineWithSession()
        assertTrue(e.addViewer(s.sessionId, LiveShareViewer("v3", "Pedro", ViewerRelationship.COWORKER)))
        assertEquals(3, e.getSession(s.sessionId)!!.viewerCount)
        assertTrue(e.removeViewer(s.sessionId, "v3"))
        assertEquals(2, e.getSession(s.sessionId)!!.viewerCount)
    }

    @Test
    fun `cannot add duplicate viewer`() {
        val (e, s) = engineWithSession()
        assertFalse(e.addViewer(s.sessionId, LiveShareViewer("v1", "Mamá", ViewerRelationship.PARENT)))
    }

    @Test
    fun `complete ride expires session and sends alert`() {
        val (e, s) = engineWithSession()
        val completed = e.completeRide(s.sessionId)!!
        assertEquals(LiveShareStatus.EXPIRED, completed.status)
        assertTrue(e.getAlerts(s.sessionId).any { it.type == AlertType.RIDE_COMPLETED })
    }

    @Test
    fun `pause and resume session`() {
        val (e, s) = engineWithSession()
        assertTrue(e.pause(s.sessionId))
        assertEquals(LiveShareStatus.PAUSED, e.getSession(s.sessionId)!!.status)
        assertTrue(e.resume(s.sessionId))
        assertEquals(LiveShareStatus.ACTIVE, e.getSession(s.sessionId)!!.status)
    }

    @Test
    fun `revoke session stops sharing`() {
        val (e, s) = engineWithSession()
        assertTrue(e.revoke(s.sessionId))
        assertEquals(LiveShareStatus.REVOKED, e.getSession(s.sessionId)!!.status)
    }

    @Test
    fun `viewer page shows correct data`() {
        val (e, s) = engineWithSession()
        e.updateLocation(s.sessionId, LiveLocationUpdate(9.93, -84.08))
        val page = e.getViewerPage(s.shareToken)!!
        assertEquals("María", page.passengerName)
        assertEquals("Carlos", page.driverName)
        assertEquals("Aeropuerto SJO", page.dropoffName)
        assertEquals("En viaje", page.rideStatus)
        assertNotNull(page.currentLocation)
    }

    @Test
    fun `record view tracks engagement`() {
        val (e, s) = engineWithSession()
        assertTrue(e.recordView(s.sessionId, "v1"))
        val viewer = e.getSession(s.sessionId)!!.viewers.find { it.viewerId == "v1" }!!
        assertEquals(1, viewer.viewCount)
        assertNotNull(viewer.lastViewedAtEpochMs)
    }
}
