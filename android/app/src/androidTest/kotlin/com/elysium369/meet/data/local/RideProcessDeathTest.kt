package com.elysium369.meet.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elysium369.meet.data.local.entities.ActiveRideSelectionEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies disk-backed Room reopen persistence and continuous DAO projections.
 * Reopening a database is not an Android process-kill or Activity restoration test.
 */
@RunWith(AndroidJUnit4::class)
class RideProcessDeathTest {

    private lateinit var db: MeetDatabase
    private lateinit var context: Context
    private lateinit var databaseName: String

    private fun openDatabase(): MeetDatabase = Room.databaseBuilder(
        context, MeetDatabase::class.java, databaseName,
    ).allowMainThreadQueries().build()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "ride-reopen-${UUID.randomUUID()}.db"
        db = openDatabase()
    }

    @After
    fun teardown() {
        try {
            if (::db.isInitialized) db.close()
        } finally {
            if (::databaseName.isInitialized) context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun activeRideSelection_survivesDatabaseReopen() = runBlocking {
        val rideDao = db.rideDao()
        val ownerId = "user-process-death-1"

        // 1. Insert a ride request (simulates passenger creating a request)
        val request = RideRequestEntity(
            requestId = "ride-pd-001",
            passengerId = ownerId,
            passengerName = "Test Passenger",
            passengerPhone = "8888-9999",
            pickupLatitude = 9.93,
            pickupLongitude = -84.08,
            pickupAddress = "San José, Costa Rica",
            pickupAccuracy = 10f,
            destLatitude = 10.0,
            destLongitude = -84.21,
            destAddress = "Alajuela, Costa Rica",
            priceOffer = 5000.0,
            currency = "CRC",
            estimatedDistanceKm = 22.5,
            estimatedDurationMin = 35,
            status = "OPEN",
            createdAt = System.currentTimeMillis(),
        )
        rideDao.insertRequest(request)

        // 2. Select as active ride (simulates selectActiveRide())
        val selection = ActiveRideSelectionEntity(
            ownerPrincipalId = ownerId,
            rideRequestId = request.requestId,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        rideDao.upsertActiveRideSelection(selection)

        // 3. Close and reopen the same on-disk database with a fresh Room instance.
        db.close()
        db = openDatabase()
        val freshDao = db.rideDao()

        // 4. Restore active ride selection (simulates init{} in ObdViewModel)
        val restoredSelection = freshDao.getActiveRideSelection(ownerId)
        assertNotNull("Active ride selection must survive database reopen", restoredSelection)
        assertEquals(request.requestId, restoredSelection!!.rideRequestId)

        // 5. Load the ride request from the pointer
        val restoredRequest = freshDao.getRequestById(restoredSelection.rideRequestId)
        assertNotNull("Ride request must be retrievable after database reopen", restoredRequest)
        assertEquals("ride-pd-001", restoredRequest!!.requestId)
        assertEquals("Test Passenger", restoredRequest.passengerName)
        assertEquals("OPEN", restoredRequest.status)
        assertEquals(9.93, restoredRequest.pickupLatitude, 0.001)
        assertEquals(-84.21, restoredRequest.destLongitude, 0.001)
        assertEquals(22.5, restoredRequest.estimatedDistanceKm, 0.1)
    }

    @Test
    fun selectActiveRide_replacesPreviousSelection() = runBlocking {
        val rideDao = db.rideDao()
        val ownerId = "user-replace-1"

        // Create two ride requests
        val req1 = RideRequestEntity(
            requestId = "ride-replace-001", passengerId = ownerId,
            passengerName = "P1", passengerPhone = "1111",
            pickupLatitude = 9.93, pickupLongitude = -84.08,
            pickupAddress = "A", pickupAccuracy = 10f,
            destLatitude = 10.0, destLongitude = -84.21,
            destAddress = "B", priceOffer = 3000.0,
            currency = "CRC", estimatedDistanceKm = 10.0,
            estimatedDurationMin = 15, status = "OPEN",
            createdAt = System.currentTimeMillis(),
        )
        val req2 = RideRequestEntity(
            requestId = "ride-replace-002", passengerId = ownerId,
            passengerName = "P2", passengerPhone = "2222",
            pickupLatitude = 9.95, pickupLongitude = -84.09,
            pickupAddress = "C", pickupAccuracy = 8f,
            destLatitude = 10.02, destLongitude = -84.22,
            destAddress = "D", priceOffer = 7500.0,
            currency = "CRC", estimatedDistanceKm = 28.0,
            estimatedDurationMin = 40, status = "OPEN",
            createdAt = System.currentTimeMillis(),
        )
        rideDao.insertRequest(req1)
        rideDao.insertRequest(req2)

        // Select first ride
        rideDao.upsertActiveRideSelection(
            ActiveRideSelectionEntity(ownerId, req1.requestId, System.currentTimeMillis())
        )
        val first = rideDao.getActiveRideSelection(ownerId)
        assertEquals(req1.requestId, first!!.rideRequestId)

        // Select second ride (should replace)
        rideDao.upsertActiveRideSelection(
            ActiveRideSelectionEntity(ownerId, req2.requestId, System.currentTimeMillis())
        )
        val second = rideDao.getActiveRideSelection(ownerId)
        assertEquals(req2.requestId, second!!.rideRequestId)
    }

    @Test
    fun clearActiveRide_removesSelection() = runBlocking {
        val rideDao = db.rideDao()
        val ownerId = "user-clear-1"

        val req = RideRequestEntity(
            requestId = "ride-clear-001", passengerId = ownerId,
            passengerName = "P", passengerPhone = "3333",
            pickupLatitude = 9.93, pickupLongitude = -84.08,
            pickupAddress = "X", pickupAccuracy = 10f,
            destLatitude = 10.0, destLongitude = -84.21,
            destAddress = "Y", priceOffer = 4000.0,
            currency = "CRC", estimatedDistanceKm = 15.0,
            estimatedDurationMin = 20, status = "OPEN",
            createdAt = System.currentTimeMillis(),
        )
        rideDao.insertRequest(req)
        rideDao.upsertActiveRideSelection(
            ActiveRideSelectionEntity(ownerId, req.requestId, System.currentTimeMillis())
        )

        assertNotNull(rideDao.getActiveRideSelection(ownerId))

        // Clear
        rideDao.clearActiveRideSelection(ownerId)
        assertNull(rideDao.getActiveRideSelection(ownerId))
    }
    @Test
    fun observeRequest_deliversLifecyclePinCompletionAndMissingRowRecovery() = runBlocking {
        val dao = db.rideDao()
        val requestId = "ride-continuous-projection"
        val updates = Channel<RideRequestEntity?>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            dao.observeRequest(requestId).collect { updates.send(it) }
        }
        try {
            assertNull(withTimeout(5_000) { updates.receive() })
            var request = RideRequestEntity(
                requestId = requestId, passengerId = "passenger-observer",
                passengerName = "Passenger", passengerPhone = "",
                pickupLatitude = 9.93, pickupLongitude = -84.08,
                pickupAddress = "Pickup", pickupAccuracy = 10f,
                destLatitude = 10.0, destLongitude = -84.21,
                destAddress = "Destination", priceOffer = 5000.0,
                priceOfferMinor = 5000, currency = "CRC", estimatedDistanceKm = 20.0,
                estimatedDurationMin = 30, status = "OPEN", serverState = "SEARCHING",
                serverVersion = 1, createdAt = 1_000,
            )
            val states = listOf(
                "SEARCHING" to "OPEN", "ASSIGNED" to "ACCEPTED",
                "DRIVER_EN_ROUTE" to "ACCEPTED", "ARRIVED" to "ARRIVED",
                "PASSENGER_ONBOARD" to "PASSENGER_ONBOARD",
                "IN_PROGRESS" to "IN_PROGRESS", "COMPLETED" to "COMPLETED",
            )
            for ((index, state) in states.withIndex()) {
                request = request.copy(
                    serverState = state.first, status = state.second,
                    serverVersion = index + 1L,
                    boardingPin = if (state.first == "ARRIVED") "4826" else null,
                    finalPriceMinor = if (state.first == "COMPLETED") 5300 else null,
                )
                dao.insertRequest(request)
                val observed = withTimeout(5_000) {
                    var next = updates.receive()
                    while (next?.serverVersion != request.serverVersion) next = updates.receive()
                    next
                }
                assertEquals(request, observed)
            }
            // The same collector must recover after temporary local absence.
            dao.deleteRequest(requestId)
            assertNull(withTimeout(5_000) { updates.receive() })
            dao.insertRequest(request)
            assertEquals(request, withTimeout(5_000) { updates.receive() })
        } finally {
            collector.cancelAndJoin()
            updates.close()
        }
    }

}
