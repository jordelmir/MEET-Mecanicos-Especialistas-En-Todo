package com.elysium369.meet.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.services.kernel.Money
import com.elysium369.meet.core.services.kernel.ServiceRole
import com.elysium369.meet.core.services.tow.TowAction
import com.elysium369.meet.core.services.tow.TowCapabilities
import com.elysium369.meet.core.services.tow.TowCommandRepository
import com.elysium369.meet.core.services.tow.TowCommandResult
import com.elysium369.meet.core.services.tow.TowState
import com.elysium369.meet.data.local.dao.TowJobDao
import com.elysium369.meet.data.local.entities.TowJobEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TowRoomConcurrencyAndCasTest {

    private lateinit var database: MeetDatabase
    private lateinit var towJobDao: TowJobDao
    private lateinit var testJob: kotlinx.coroutines.CompletableJob
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        testJob = SupervisorJob()
        testScope = CoroutineScope(Dispatchers.IO + testJob)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        towJobDao = database.towJobDao()
    }

    @After
    fun teardown() = runBlocking {
        testJob.cancel()
        testJob.join()
        database.close()
    }

    @Test
    fun towJobDaoAtomicCasSingleWinnerTest() = runBlocking {
        val testJobId = "job-cas-concurrent-${UUID.randomUUID()}"
        val initialVersion = 10L

        val initialEntity = TowJobEntity(
            jobId = testJobId,
            customerId = "cust-1",
            customerName = "Test Customer",
            customerPhone = "+506 8888-0000",
            vehicleVin = null,
            vehicleSummary = "Toyota Corolla",
            pickupLatitude = 9.9333,
            pickupLongitude = -84.0833,
            pickupAccuracyMeters = 8.5f,
            pickupCapturedAt = System.currentTimeMillis(),
            pickupAddress = "San José",
            destinationLatitude = null,
            destinationLongitude = null,
            destinationAddress = null,
            state = TowState.REQUESTED.name,
            serverVersion = initialVersion,
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis(),
            assignedProviderId = null,
            assignedOperatorId = null,
            assignedTowUnitId = null,
            assignedOperatorName = null,
            assignedOperatorPhone = null,
            assignedOperatorRating = null,
            assignedOperatorCompletedJobs = null,
            operatorLatitude = null,
            operatorLongitude = null,
            operatorFreshnessEpochMs = null,
            requiredCapabilities = "FLATBED",
            assignedUnitJson = null,
            estimatedPriceMinor = 2500000L,
            quotedPriceMinor = null,
            authorizedPriceMinor = null,
            finalSettlementMinor = null,
            currency = "CRC",
            quoteId = null,
            authorizationId = null,
            correlationId = "corr-1",
            custodyRecordsJson = "[]"
        )

        towJobDao.insertJob(initialEntity)

        // 100 concurrent coroutines attempting CAS with expectedVersion = 10
        val concurrencyCount = 100
        val tasks = (1..concurrencyCount).map { i ->
            testScope.async {
                towJobDao.compareAndSwapState(
                    jobId = testJobId,
                    expectedVersion = initialVersion,
                    newState = TowState.ASSIGNED.name,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    operatorName = "Operator $i",
                    operatorPhone = "+506 8000-$i",
                    custodyRecordsJson = "[]",
                    assignedUnitJson = null
                )
            }
        }

        val results = tasks.awaitAll()

        val successes = results.count { it == 1 }
        val conflicts = results.count { it == 0 }

        // Invariant: Exactly 1 CAS update must succeed
        assertEquals("Exactly 1 concurrent CAS update must succeed", 1, successes)
        assertEquals("Exactly 99 concurrent CAS updates must report a version conflict", 99, conflicts)

        val persisted = towJobDao.getJobById(testJobId)
        assertNotNull(persisted)
        assertEquals("Persisted version must increment to 11", initialVersion + 1, persisted!!.serverVersion)
        assertEquals("Persisted state must be ASSIGNED", TowState.ASSIGNED.name, persisted.state)
    }

    @Test
    fun towVersionConflictReturnsPersistedWinnerTest() = runBlocking {
        val testJobId = UUID.randomUUID()
        val repo = TowCommandRepository(towJobDao, null, testScope)

        val initialJob = repo.requestTow(
            customerId = UUID.randomUUID(),
            customerName = "Conflict Test",
            customerPhone = "+506 7777-8888",
            vehicleSummary = "Hyundai Tucson",
            pickupLocation = GeoPoint(9.93, -84.08),
            pickupAddress = "Curridabat",
            estimatedPrice = Money.ofCrc(20000L)
        ).jobOrNull!!

        assertEquals(1L, initialJob.serverVersion)

        // Winner performs action on version 1 -> advances to version 2
        val winnerResult = repo.executeAction(
            jobId = initialJob.jobId,
            action = TowAction.AssignOperator(UUID.randomUUID(), "TOW-RIG-1"),
            actorRole = ServiceRole.TOW_OPERATOR,
            expectedVersion = 1L
        )
        assertTrue(winnerResult is TowCommandResult.Success)

        // Stale actor attempts action with expectedVersion = 1L
        val loserResult = repo.executeAction(
            jobId = initialJob.jobId,
            action = TowAction.StartEnRoute,
            actorRole = ServiceRole.TOW_OPERATOR,
            expectedVersion = 1L
        )

        assertTrue("Loser must receive ConcurrencyConflict", loserResult is TowCommandResult.ConcurrencyConflict)
        val conflict = loserResult as TowCommandResult.ConcurrencyConflict
        assertEquals(1L, conflict.expectedVersion)
        assertEquals("Actual version must reflect persisted winner (2L)", 2L, conflict.actualVersion)
        assertEquals("Actual state must reflect persisted winner state (ASSIGNED)", TowState.ASSIGNED, conflict.actualState)
    }

    @Test
    fun towRequestSurvivesProcessRestartTest() = runBlocking {
        var repo: TowCommandRepository? = TowCommandRepository(towJobDao, null, testScope)

        val customerId = UUID.randomUUID()
        val correlationId = "corr-restart-${UUID.randomUUID()}"
        val createdResult = repo!!.requestTow(
            customerId = customerId,
            customerName = "Durable Customer",
            customerPhone = "+506 8899-0011",
            vehicleVin = "KMHD14BP95U123456",
            vehicleSummary = "Hyundai Accent 2005",
            pickupLocation = GeoPoint(9.928, -84.090),
            pickupAddress = "San Pedro",
            requiredCapabilities = setOf(TowCapabilities.FLATBED, TowCapabilities.LOW_CLEARANCE),
            estimatedPrice = Money.ofCrc(30000L),
            correlationId = correlationId
        )

        assertTrue(createdResult is com.elysium369.meet.core.services.tow.TowRequestResult.PersistedLocally)
        val originalJob = createdResult.jobOrNull!!

        // Simulate Process Death: discard repository instance from memory
        repo = null

        // Recreate repository after process restart, pointing to same durable Room DAO
        val restartedRepo = TowCommandRepository(towJobDao, null, testScope)
        val rehydratedJob = restartedRepo.fetchJob(originalJob.jobId)

        assertNotNull("Job must survive process restart", rehydratedJob)
        assertEquals(originalJob.jobId, rehydratedJob!!.jobId)
        assertEquals(originalJob.state, rehydratedJob.state)
        assertEquals(originalJob.serverVersion, rehydratedJob.serverVersion)
        assertEquals(correlationId, rehydratedJob.correlationId)
        assertEquals(originalJob.requiredCapabilities, rehydratedJob.requiredCapabilities)
        assertEquals(originalJob.customerName, rehydratedJob.customerName)
        assertEquals(originalJob.customerPhone, rehydratedJob.customerPhone)
        assertEquals(originalJob.pickupAddress, rehydratedJob.pickupAddress)
        assertEquals(originalJob.vehicleVin, rehydratedJob.vehicleVin)
    }

    @Test
    fun everyTowStateRoundTripsExactlyTest() = runBlocking {
        for (state in TowState.values()) {
            val entity = TowJobEntity(
                jobId = "test-roundtrip-${state.name}",
                customerId = "cust-1",
                customerName = "Test Customer",
                customerPhone = "+506 8888-0000",
                vehicleVin = null,
                vehicleSummary = "Sedan",
                pickupLatitude = 9.93,
                pickupLongitude = -84.08,
                pickupAccuracyMeters = null,
                pickupCapturedAt = null,
                pickupAddress = "San Jose",
                destinationLatitude = null,
                destinationLongitude = null,
                destinationAddress = null,
                state = state.name,
                serverVersion = 847L,
                createdAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
                assignedProviderId = null,
                assignedOperatorId = null,
                assignedTowUnitId = null,
                assignedOperatorName = null,
                assignedOperatorPhone = null,
                assignedOperatorRating = null,
                assignedOperatorCompletedJobs = null,
                operatorLatitude = null,
                operatorLongitude = null,
                operatorFreshnessEpochMs = null,
                requiredCapabilities = "FLATBED,LOW_CLEARANCE",
                assignedUnitJson = null,
                estimatedPriceMinor = null,
                quotedPriceMinor = null,
                authorizedPriceMinor = null,
                finalSettlementMinor = null,
                currency = "CRC",
                quoteId = null,
                authorizationId = null,
                correlationId = UUID.randomUUID().toString(),
                custodyRecordsJson = "[]"
            )
            towJobDao.insertJob(entity)
            val read = towJobDao.getJobById(entity.jobId)
            assertNotNull("Entity for state ${state.name} must persist", read)
            assertEquals("Entity state must match", state.name, read!!.state)
            assertEquals("Version must roundtrip", 847L, read.serverVersion)

            val domain = with(TowCommandRepository.Companion) { read.toTowJob() }
            assertEquals("Domain state must match exactly", state, domain.state)
            assertEquals("Capabilities must roundtrip", setOf(TowCapabilities.FLATBED, TowCapabilities.LOW_CLEARANCE), domain.requiredCapabilities)
        }
    }
}
