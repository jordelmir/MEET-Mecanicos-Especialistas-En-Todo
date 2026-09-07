package com.elysium369.meet.ride.schedule

import com.elysium369.meet.ride.domain.RideFareMode
import org.junit.Assert.*
import org.junit.Test

class RideScheduleEngineTest {

    private fun futureMs(hoursFromNow: Int = 24) =
        System.currentTimeMillis() + hoursFromNow * 60 * 60 * 1000L

    private fun pickup() = RideStop("s1", "Mi Casa", "San José", 9.93, -84.08, order = 0, isPickup = true)
    private fun dropoff() = RideStop("s2", "Aeropuerto SJO", "Alajuela", 10.0, -84.21, order = 1, isDropoff = true)
    private fun midStop() = RideStop("s3", "Café Central", "Heredia", 10.0, -84.12, waitMinutes = 5, order = 1)

    @Test
    fun `schedule future ride`() {
        val e = RideScheduleEngine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        assertTrue(ride.isPending)
        assertEquals(2, ride.stopCount)
    }

    @Test
    fun `cannot schedule in the past`() {
        val e = RideScheduleEngine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), System.currentTimeMillis() - 1000)
        assertNull(ride)
    }

    @Test
    fun `multi-stop adds intermediate stop`() {
        val e = RideScheduleEngine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        assertTrue(e.addStop(ride.scheduleId, midStop()))
        val updated = e.getSchedule(ride.scheduleId)!!
        assertEquals(3, updated.stopCount)
        assertTrue(updated.isMultiStop)
    }

    @Test
    fun `reminders generated at correct intervals`() {
        val e = RideScheduleEngine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        val reminders = e.generateReminders(ride.scheduleId)
        assertEquals(3, reminders.size)
        assertEquals(ReminderType.ONE_HOUR, reminders[0].type)
        assertEquals(ReminderType.THIRTY_MIN, reminders[1].type)
        assertEquals(ReminderType.FIFTEEN_MIN, reminders[2].type)
    }

    @Test
    fun `dispatch changes status`() {
        val e = RideScheduleEngine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        val dispatched = e.dispatch(ride.scheduleId)!!
        assertEquals(ScheduleStatus.DISPATCHING, dispatched.status)
    }

    @Test
    fun `assign driver after dispatch`() {
        val e = RideScheduleEngine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        e.dispatch(ride.scheduleId)
        val matched = e.assignDriver(ride.scheduleId, "driver-1", "ride-123")!!
        assertEquals(ScheduleStatus.DRIVER_MATCHED, matched.status)
        assertEquals("driver-1", matched.matchedDriverId)
    }

    @Test
    fun `cancel a scheduled ride`() {
        val e = RideScheduleEngine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        assertTrue(e.cancel(ride.scheduleId))
        assertEquals(ScheduleStatus.CANCELLED, e.getSchedule(ride.scheduleId)!!.status)
    }

    @Test
    fun `save and book from favorite route`() {
        val e = RideScheduleEngine()
        val fav = e.saveFavoriteRoute("Al Trabajo", "🏢", listOf(pickup(), dropoff()))
        val ride = e.bookFromFavorite(fav.routeId, "user1", futureMs())!!
        assertTrue(ride.isPending)
        assertEquals(1, e.getFavorites().first().usageCount)
    }

    @Test
    fun `recurring ride config`() {
        val e = RideScheduleEngine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs(),
            recurrence = RecurrenceConfig(RecurrencePattern.WEEKDAYS))!!
        assertTrue(ride.isRecurring)
    }

    @Test
    fun `upcoming rides sorted by time`() {
        val e = RideScheduleEngine()
        e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs(48))
        Thread.sleep(2) // ensure different schedule ID (ms-based)
        e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs(24))
        val upcoming = e.getUpcoming("user1")
        assertEquals(2, upcoming.size)
        assertTrue(upcoming[0].scheduledAtEpochMs < upcoming[1].scheduledAtEpochMs)
    }
}
