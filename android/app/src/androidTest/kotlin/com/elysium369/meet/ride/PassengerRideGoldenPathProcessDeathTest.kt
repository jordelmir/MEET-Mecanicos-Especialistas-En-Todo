package com.elysium369.meet.ride

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.entities.ActiveRideSelectionEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ride.data.local.RideCommandOutboxEntity
import com.elysium369.meet.ride.data.local.RideOutboxStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Executable process-death boundary for the passenger golden path.
 *
 * The server-authority suites verify lifecycle and settlement invariants. This
 * device test verifies the Android half: the exact server-confirmed projection,
 * explicit owner-scoped selection, and one idempotent pending command survive a
 * complete Room close/reopen instead of being reconstructed from heap state.
 */
@RunWith(AndroidJUnit4::class)
class PassengerRideGoldenPathProcessDeathTest {
    private lateinit var context: Context
    private val databaseName = "passenger-process-death-test.db"
    private var database: MeetDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun activeRideAndOneCommandSurviveDatabaseRestart() = runBlocking {
        val rideId = "11111111-1111-1111-1111-111111111111"
        val ownerId = "22222222-2222-2222-2222-222222222222"
        val commandKey = "complete:$rideId:version:7"
        val now = 1_788_805_000_000L

        database = openDatabase()
        val rideDao = requireNotNull(database).rideDao()
        val outboxDao = requireNotNull(database).rideCommandOutboxDao()

        rideDao.insertRequest(serverConfirmedActiveRide(rideId, ownerId, now))
        rideDao.upsertActiveRideSelection(
            ActiveRideSelectionEntity(
                ownerPrincipalId = ownerId,
                rideRequestId = rideId,
                updatedAtEpochMs = now,
            ),
        )
        val command = RideCommandOutboxEntity(
            idempotencyKey = commandKey,
            rideId = rideId,
            actorSessionUserId = ownerId,
            commandType = "COMPLETE",
            expectedVersion = 7L,
            payloadVersion = 1,
            payloadJson = "{}",
            status = RideOutboxStatus.PENDING,
            attemptCount = 0,
            nextAttemptAt = now,
            leaseStartedAt = null,
            lastErrorCode = null,
            lastErrorMessage = null,
            correlationId = null,
            createdAt = now,
            updatedAt = now,
        )
        assertNotEquals(-1L, outboxDao.insert(command))
        assertEquals(-1L, outboxDao.insert(command))

        requireNotNull(database).close()
        database = openDatabase()

        val restoredRideDao = requireNotNull(database).rideDao()
        val restoredOutboxDao = requireNotNull(database).rideCommandOutboxDao()
        val selection = restoredRideDao.getActiveRideSelection(ownerId)
        val restoredRide = restoredRideDao.getRequestById(requireNotNull(selection).rideRequestId)
        val restoredCommand = restoredOutboxDao.byIdempotencyKey(commandKey)

        assertEquals(rideId, selection.rideRequestId)
        assertNotNull(restoredRide)
        assertEquals("IN_PROGRESS", restoredRide?.serverState)
        assertEquals(7L, restoredRide?.serverVersion)
        assertEquals("SERVER_CONFIRMED", restoredRide?.syncState)
        assertEquals(commandKey, restoredCommand?.idempotencyKey)
        assertEquals(RideOutboxStatus.PENDING, restoredCommand?.status)
    }

    @Test
    fun selectedRideObservesEveryConfirmedStageAndPendingCommand() = runBlocking {
        database = openDatabase()
        val dao = requireNotNull(database).rideDao()
        val rideId = "11111111-1111-1111-1111-111111111111"
        val ownerId = "22222222-2222-2222-2222-222222222222"
        val base = serverConfirmedActiveRide(rideId, ownerId, 1_788_805_000_000L)
        val stages = listOf(
            "SEARCHING", "ASSIGNED", "DRIVER_EN_ROUTE", "ARRIVED",
            "PASSENGER_ONBOARD", "IN_PROGRESS", "COMPLETED",
        )
        for ((index, state) in stages.withIndex()) {
            dao.insertRequest(base.copy(serverState = state, status = state, serverVersion = index + 1L))
            val observed = withTimeout(5_000) {
                dao.observeRequest(rideId).first { it?.serverState == state }
            }
            assertEquals(index + 1L, observed?.serverVersion)
        }
        dao.markCommandPending(rideId)
        assertEquals("PENDING", withTimeout(5_000) {
            dao.observeRequest(rideId).first { it?.syncState == "PENDING" }
        }?.syncState)
        dao.recordConfirmedTip(rideId, ownerId, 500)
        assertEquals(500L, withTimeout(5_000) {
            dao.observeRequest(rideId).first { it?.tipAmountMinor == 500L }
        }?.tipAmountMinor)
        assertEquals(0, dao.recordConfirmedTip(rideId, "other-account", 900))
        assertEquals(500L, dao.getRequestById(rideId)?.tipAmountMinor)
    }

    private fun openDatabase(): MeetDatabase =
        Room.databaseBuilder(context, MeetDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()

    private fun serverConfirmedActiveRide(
        rideId: String,
        ownerId: String,
        now: Long,
    ) = RideRequestEntity(
        requestId = rideId,
        passengerId = ownerId,
        passengerName = "Pasajero de prueba",
        passengerPhone = "+50600000000",
        pickupLatitude = 9.9325,
        pickupLongitude = -84.0796,
        pickupAddress = "Origen verificado",
        pickupAccuracy = 4.0f,
        destLatitude = 9.9281,
        destLongitude = -84.0907,
        destAddress = "Destino verificado",
        priceOffer = 2_500.0,
        priceOfferMinor = 250_000L,
        currency = "CRC",
        estimatedDistanceKm = 4.2,
        estimatedDurationMin = 14,
        status = "IN_PROGRESS",
        assignedDriverId = "33333333-3333-3333-3333-333333333333",
        assignedDriverName = "Conductor confirmado",
        assignedDriverVehicle = "Vehículo confirmado",
        finalPriceMinor = 250_000L,
        serverState = "IN_PROGRESS",
        serverVersion = 7L,
        syncState = "SERVER_CONFIRMED",
        lastSyncedAt = now,
        lastCorrelationId = "44444444-4444-4444-4444-444444444444",
        createdAt = now - 60_000L,
    )
}
