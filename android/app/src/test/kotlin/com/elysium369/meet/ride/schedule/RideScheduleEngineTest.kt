package com.elysium369.meet.ride.schedule

import com.elysium369.meet.data.local.dao.ScheduledRideDao
import com.elysium369.meet.data.local.entities.FavoriteRouteEntity
import com.elysium369.meet.data.local.entities.ScheduledRideEntity
import com.elysium369.meet.ride.domain.RideFareMode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RideScheduleEngineTest {

    /** In-memory fake DAO for unit testing */
    private class FakeScheduleDao : ScheduledRideDao {
        private val schedules = mutableMapOf<String, ScheduledRideEntity>()
        private val favorites = mutableMapOf<String, FavoriteRouteEntity>()

        override suspend fun upsert(schedule: ScheduledRideEntity) { schedules[schedule.scheduleId] = schedule }
        override suspend fun upsertAll(schedules: List<ScheduledRideEntity>) { schedules.forEach { upsert(it) } }
        override suspend fun update(schedule: ScheduledRideEntity) { schedules[schedule.scheduleId] = schedule }
        override suspend fun getById(scheduleId: String) = schedules[scheduleId]
        override fun getUpcomingFlow(userId: String) = flowOf(schedules.values.filter { it.userId == userId && it.status in listOf("PENDING", "REMINDER_SENT", "DISPATCHING") }.sortedBy { it.scheduledAtEpochMs })
        override suspend fun getUpcoming(userId: String) = schedules.values.filter { it.userId == userId && it.status in listOf("PENDING", "REMINDER_SENT", "DISPATCHING") }.sortedBy { it.scheduledAtEpochMs }
        override suspend fun getDueForDispatch(nowEpochMs: Long) = schedules.values.filter { it.status == "DISPATCHING" && (it.dispatchAtEpochMs ?: 0) <= nowEpochMs }
        override suspend fun updateStatus(scheduleId: String, status: String) { schedules[scheduleId]?.let { schedules[scheduleId] = it.copy(status = status) } }
        override suspend fun assignDriver(scheduleId: String, status: String, driverId: String, rideId: String) { schedules[scheduleId]?.let { schedules[scheduleId] = it.copy(status = status, matchedDriverId = driverId, rideId = rideId) } }
        override suspend fun delete(scheduleId: String) = if (schedules.remove(scheduleId) != null) 1 else 0
        override suspend fun purgeTerminal(cutoffEpochMs: Long) = 0
        override suspend fun upsertFavorite(route: FavoriteRouteEntity) { favorites[route.routeId] = route }
        override fun getFavoritesFlow() = flowOf(favorites.values.sortedByDescending { it.usageCount })
        override suspend fun getFavorites() = favorites.values.sortedByDescending { it.usageCount }
        override suspend fun getFavoriteById(routeId: String) = favorites[routeId]
        override suspend fun incrementFavoriteUsage(routeId: String, nowEpochMs: Long) { favorites[routeId]?.let { favorites[routeId] = it.copy(usageCount = it.usageCount + 1, lastUsedEpochMs = nowEpochMs) } }
        override suspend fun deleteFavorite(routeId: String) = if (favorites.remove(routeId) != null) 1 else 0
    }

    private fun engine() = RideScheduleEngine(FakeScheduleDao())

    private fun futureMs(hoursFromNow: Int = 24) =
        System.currentTimeMillis() + hoursFromNow * 60 * 60 * 1000L

    private fun pickup() = RideStop("s1", "Mi Casa", "San José", 9.93, -84.08, order = 0, isPickup = true)
    private fun dropoff() = RideStop("s2", "Aeropuerto SJO", "Alajuela", 10.0, -84.21, order = 1, isDropoff = true)
    private fun midStop() = RideStop("s3", "Café Central", "Heredia", 10.0, -84.12, waitMinutes = 5, order = 1)

    @Test
    fun `schedule future ride`() = runBlocking {
        val e = engine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        assertTrue(ride.isPending)
        assertEquals(2, ride.stopCount)
    }

    @Test
    fun `cannot schedule in the past`() = runBlocking {
        val e = engine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), System.currentTimeMillis() - 1000)
        assertNull(ride)
    }

    @Test
    fun `multi-stop adds intermediate stop`() = runBlocking {
        val e = engine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        assertTrue(e.addStop(ride.scheduleId, midStop()))
        val updated = e.getSchedule(ride.scheduleId)!!
        assertEquals(3, updated.stopCount)
        assertTrue(updated.isMultiStop)
    }

    @Test
    fun `reminders generated at correct intervals`() = runBlocking {
        val e = engine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        val reminders = e.generateReminders(ride.scheduleId)
        assertEquals(3, reminders.size)
        assertEquals(ReminderType.ONE_HOUR, reminders[0].type)
        assertEquals(ReminderType.THIRTY_MIN, reminders[1].type)
        assertEquals(ReminderType.FIFTEEN_MIN, reminders[2].type)
    }

    @Test
    fun `dispatch changes status`() = runBlocking {
        val e = engine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        val dispatched = e.dispatch(ride.scheduleId)!!
        assertEquals(ScheduleStatus.DISPATCHING, dispatched.status)
    }

    @Test
    fun `assign driver after dispatch`() = runBlocking {
        val e = engine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        e.dispatch(ride.scheduleId)
        val matched = e.assignDriver(ride.scheduleId, "driver-1", "ride-123")!!
        assertEquals(ScheduleStatus.DRIVER_MATCHED, matched.status)
        assertEquals("driver-1", matched.matchedDriverId)
    }

    @Test
    fun `cancel a scheduled ride`() = runBlocking {
        val e = engine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs())!!
        assertTrue(e.cancel(ride.scheduleId))
        assertEquals(ScheduleStatus.CANCELLED, e.getSchedule(ride.scheduleId)!!.status)
    }

    @Test
    fun `save and book from favorite route`() = runBlocking {
        val e = engine()
        val fav = e.saveFavoriteRoute("Al Trabajo", "\uD83C\uDFE2", listOf(pickup(), dropoff()))
        val ride = e.bookFromFavorite(fav.routeId, "user1", futureMs())!!
        assertTrue(ride.isPending)
        assertEquals(1, e.getFavorites().first().usageCount)
    }

    @Test
    fun `recurring ride config`() = runBlocking {
        val e = engine()
        val ride = e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs(),
            recurrence = RecurrenceConfig(RecurrencePattern.WEEKDAYS))!!
        assertTrue(ride.isRecurring)
    }

    @Test
    fun `upcoming rides sorted by time`() = runBlocking {
        val e = engine()
        e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs(48))
        Thread.sleep(2)
        e.scheduleRide("user1", listOf(pickup(), dropoff()), futureMs(24))
        val upcoming = e.getUpcoming("user1")
        assertEquals(2, upcoming.size)
        assertTrue(upcoming[0].scheduledAtEpochMs < upcoming[1].scheduledAtEpochMs)
    }
}
