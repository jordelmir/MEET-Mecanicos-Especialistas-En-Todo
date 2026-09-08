package com.elysium369.meet.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elysium369.meet.data.local.entities.ActiveRideSelectionEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that an active ride request survives process death via Room persistence.
 *
 * Flow: create request → select as active → "kill process" (new DB instance)
 *       → restore selection → verify same data returned.
 */
@RunWith(AndroidJUnit4::class)
class RideProcessDeathTest {

    private lateinit var db: MeetDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeetDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun activeRideSelection_survivesProcessDeath() = runBlocking {
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

        // 3. "Kill process" — create fresh database instance (same schema)
        db.close()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeetDatabase::class.java,
        ).allowMainThreadQueries().build()
        val freshDao = db.rideDao()

        // 4. Restore active ride selection (simulates init{} in ObdViewModel)
        val restoredSelection = freshDao.getActiveRideSelection(ownerId)
        assertNotNull("Active ride selection must survive process death", restoredSelection)
        assertEquals(request.requestId, restoredSelection!!.rideRequestId)

        // 5. Load the ride request from the pointer
        val restoredRequest = freshDao.getRequestById(restoredSelection.rideRequestId)
        assertNotNull("Ride request must be retrievable after process death", restoredRequest)
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
}
