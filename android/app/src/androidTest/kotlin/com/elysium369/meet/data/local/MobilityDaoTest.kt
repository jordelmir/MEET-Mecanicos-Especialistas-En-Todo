package com.elysium369.meet.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elysium369.meet.data.local.dao.PttChannelDao
import com.elysium369.meet.data.local.dao.SafeJourneyDao
import com.elysium369.meet.data.local.dao.ScheduledRideDao
import com.elysium369.meet.data.local.entities.FavoriteRouteEntity
import com.elysium369.meet.data.local.entities.PttChannelEntity
import com.elysium369.meet.data.local.entities.PttChannelMemberEntity
import com.elysium369.meet.data.local.entities.SafeJourneyEntity
import com.elysium369.meet.data.local.entities.ScheduledRideEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the mobility DAOs (SafeJourney, PttChannel, ScheduledRide)
 * correctly persist and retrieve data — the foundation for process death survival.
 */
@RunWith(AndroidJUnit4::class)
class MobilityDaoTest {

    private lateinit var database: MeetDatabase
    private lateinit var journeyDao: SafeJourneyDao
    private lateinit var pttDao: PttChannelDao
    private lateinit var scheduleDao: ScheduledRideDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        journeyDao = database.safeJourneyDao()
        pttDao = database.pttChannelDao()
        scheduleDao = database.scheduledRideDao()
    }

    @After
    fun teardown() { database.close() }

    // ── SafeJourney DAO ──

    private fun testJourney(id: String = "j-1", principal: String = "user-1", state: String = "PLANNED") = SafeJourneyEntity(
        journeyId = id, principalId = principal, name = "Trip to airport",
        originName = "Home", destinationName = "SJO Airport",
        destinationLat = 10.0, destinationLon = -84.2,
        destinationRadiusMeters = 100.0, estimatedArrivalEpochMs = System.currentTimeMillis() + 3600_000,
        state = state, journeyState = state, mode = "DRIVING",
        createdAtEpochMs = System.currentTimeMillis(), startedAtEpochMs = null,
        lastCheckInAtEpochMs = null, completedAtEpochMs = null,
        sharedWithPrincipalIdsJson = "", checkInIntervalMs = 1800_000, publisherDeviceId = "dev-1",
    )

    @Test
    fun journey_upsert_and_getById() = runBlocking {
        val j = testJourney()
        journeyDao.upsert(j)
        val loaded = journeyDao.getById(j.journeyId)
        assertNotNull(loaded)
        assertEquals("Trip to airport", loaded!!.name)
        assertEquals("user-1", loaded.principalId)
    }

    @Test
    fun journey_activeFlow_filters_terminal() = runBlocking {
        journeyDao.upsert(testJourney(id = "j-1", state = "ACTIVE"))
        journeyDao.upsert(testJourney(id = "j-2", state = "COMPLETED"))
        journeyDao.upsert(testJourney(id = "j-3", state = "CANCELLED"))
        val active = journeyDao.getActiveJourneysFlow().first()
        assertEquals(1, active.size)
        assertEquals("j-1", active[0].journeyId)
    }

    @Test
    fun journey_purgeTerminal_removes_old() = runBlocking {
        val old = testJourney(id = "old", state = "COMPLETED").copy(completedAtEpochMs = 1000L)
        journeyDao.upsert(old)
        journeyDao.upsert(testJourney(id = "active", state = "ACTIVE"))
        val purged = journeyDao.purgeTerminal(System.currentTimeMillis())
        assertEquals(1, purged)
        assertNull(journeyDao.getById("old"))
        assertNotNull(journeyDao.getById("active"))
    }

    // ── PttChannel DAO ──

    private fun testChannel(id: String = "ch-1", owner: String = "user-1") = PttChannelEntity(
        channelId = id, name = "Fleet Alpha", type = "FLEET",
        state = "ACTIVE", ownerPrincipalId = owner,
        createdAtEpochMs = System.currentTimeMillis(), memberCount = 1,
        maxMembers = 50, isEncrypted = true,
    )

    private fun testMember(channelId: String = "ch-1", principal: String = "user-1") = PttChannelMemberEntity(
        channelId = channelId, principalId = principal,
        role = "OWNER", state = "JOINED", joinedAtEpochMs = System.currentTimeMillis(),
    )

    @Test
    fun ptt_channel_upsert_and_get() = runBlocking {
        pttDao.upsertChannel(testChannel())
        pttDao.upsertMember(testMember())
        val ch = pttDao.getChannelById("ch-1")
        assertNotNull(ch)
        assertEquals("Fleet Alpha", ch!!.name)
    }

    @Test
    fun ptt_activeChannelsFlow_excludes_archived() = runBlocking {
        pttDao.upsertChannel(testChannel(id = "ch-1", owner = "u1"))
        pttDao.upsertChannel(testChannel(id = "ch-2", owner = "u2").copy(state = "ARCHIVED"))
        val active = pttDao.getActiveChannelsFlow().first()
        assertEquals(1, active.size)
        assertEquals("ch-1", active[0].channelId)
    }

    @Test
    fun ptt_removeMember() = runBlocking {
        pttDao.upsertChannel(testChannel())
        pttDao.upsertMember(testMember())
        val removed = pttDao.removeMember("ch-1", "user-1")
        assertEquals(1, removed)
    }

    // ── ScheduledRide DAO ──

    private fun testSchedule(id: String = "s-1", user: String = "user-1", status: String = "PENDING") = ScheduledRideEntity(
        scheduleId = id, userId = user,
        stopsJson = "[{\"stopId\":\"s1\",\"displayName\":\"Home\",\"address\":\"San Jose\",\"latitude\":9.93,\"longitude\":-84.08,\"order\":0,\"isPickup\":true},{\"stopId\":\"s2\",\"displayName\":\"Airport\",\"address\":\"Alajuela\",\"latitude\":10.0,\"longitude\":-84.21,\"order\":1,\"isDropoff\":true}]",
        scheduledAtEpochMs = System.currentTimeMillis() + 86400_000,
        createdAtEpochMs = System.currentTimeMillis(),
        fareMode = "METERED_TIME_DISTANCE", estimatedFare = 5000, currency = "CRC",
        status = status, recurrencePattern = "NONE", recurrenceConfigJson = null,
        notes = "", matchedDriverId = null, rideId = null, dispatchAtEpochMs = null,
    )

    @Test
    fun schedule_upsert_and_getById() = runBlocking {
        val s = testSchedule()
        scheduleDao.upsert(s)
        val loaded = scheduleDao.getById("s-1")
        assertNotNull(loaded)
        assertEquals("user-1", loaded!!.userId)
        assertEquals(5000L, loaded.estimatedFare)
    }

    @Test
    fun schedule_upcomingFlow_filters_completed() = runBlocking {
        scheduleDao.upsert(testSchedule(id = "s-1", status = "PENDING"))
        scheduleDao.upsert(testSchedule(id = "s-2", status = "COMPLETED"))
        val upcoming = scheduleDao.getUpcomingFlow("user-1").first()
        assertEquals(1, upcoming.size)
        assertEquals("s-1", upcoming[0].scheduleId)
    }

    @Test
    fun schedule_purgeTerminal() = runBlocking {
        scheduleDao.upsert(testSchedule(id = "old", status = "COMPLETED").copy(createdAtEpochMs = 1000L))
        scheduleDao.upsert(testSchedule(id = "pending", status = "PENDING"))
        val purged = scheduleDao.purgeTerminal(System.currentTimeMillis())
        assertEquals(1, purged)
        assertNull(scheduleDao.getById("old"))
        assertNotNull(scheduleDao.getById("pending"))
    }

    // ── FavoriteRoute DAO ──

    @Test
    fun favorite_upsert_and_get() = runBlocking {
        val fav = FavoriteRouteEntity(
            routeId = "fav-1", label = "Work", icon = "🏢",
            stopsJson = "[]", fareMode = "METERED_TIME_DISTANCE",
            usageCount = 0, lastUsedEpochMs = 0L,
        )
        scheduleDao.upsertFavorite(fav)
        val loaded = scheduleDao.getFavoriteById("fav-1")
        assertNotNull(loaded)
        assertEquals("Work", loaded!!.label)
    }

    @Test
    fun favorite_incrementUsage() = runBlocking {
        val fav = FavoriteRouteEntity(
            routeId = "fav-1", label = "Work", icon = "🏢",
            stopsJson = "[]", fareMode = "METERED_TIME_DISTANCE",
            usageCount = 0, lastUsedEpochMs = 0L,
        )
        scheduleDao.upsertFavorite(fav)
        scheduleDao.incrementFavoriteUsage("fav-1", System.currentTimeMillis())
        val loaded = scheduleDao.getFavoriteById("fav-1")!!
        assertEquals(1, loaded.usageCount)
        assertTrue(loaded.lastUsedEpochMs > 0)
    }
}
