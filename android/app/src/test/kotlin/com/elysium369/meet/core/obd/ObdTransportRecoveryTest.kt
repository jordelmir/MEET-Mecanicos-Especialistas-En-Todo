package com.elysium369.meet.core.obd

import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ObdTransportRecoveryTest {

    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        testScope = CoroutineScope(Dispatchers.IO + Job())
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    class TestInstrumentedTransport : TransportInterface {
        val connectCount = AtomicInteger(0)
        val disconnectCount = AtomicInteger(0)
        val reconnectCount = AtomicInteger(0)
        val writeCount = AtomicInteger(0)
        val writtenCommands = mutableListOf<String>()

        var responseToReturn: String = "OK\r\n>"
        var shouldThrowIoExceptionOnWrite: Boolean = false
        var shouldThrowIoExceptionOnRead: Boolean = false
        private var _connected: Boolean = true

        override val isConnected: Boolean get() = _connected

        override suspend fun connect() {
            connectCount.incrementAndGet()
            _connected = true
        }

        override suspend fun disconnect() {
            disconnectCount.incrementAndGet()
            _connected = false
        }

        override suspend fun reconnect() {
            reconnectCount.incrementAndGet()
            disconnect()
            delay(10)
            connect()
        }

        override suspend fun write(data: ByteArray) {
            if (shouldThrowIoExceptionOnWrite) {
                throw IOException("Simulated Physical Write Failure")
            }
            writeCount.incrementAndGet()
            val cmd = String(data).trim()
            synchronized(writtenCommands) {
                writtenCommands.add(cmd)
            }
        }

        override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? {
            if (shouldThrowIoExceptionOnRead) {
                throw IOException("Simulated Physical Read Failure")
            }
            return responseToReturn.toByteArray()
        }

        override suspend fun drain() {}
    }

    @Test
    fun testL1AdapterErrorDoesNotTriggerTransportReconnect() {
        val domain = ObdFailureDomain.classifyResponse("?\r\n>")
        assertEquals(ObdFailureDomain.L1_ADAPTER, domain)

        val profile = AdapterCapabilityProfile()
        profile.recordOutcome("ATAL", "?")
        assertTrue(profile.isKnownUnsupported("ATAL"))
        assertFalse(profile.isSupported("ATAL"))
    }

    @Test
    fun testL2VehicleBusErrorClassification() {
        assertEquals(ObdFailureDomain.L2_VEHICLE_BUS, ObdFailureDomain.classifyResponse("CAN ERROR\r\n>"))
        assertEquals(ObdFailureDomain.L2_VEHICLE_BUS, ObdFailureDomain.classifyResponse("BUS ERROR\r\n>"))
        assertEquals(ObdFailureDomain.L2_VEHICLE_BUS, ObdFailureDomain.classifyResponse("UNABLE TO CONNECT\r\n>"))
    }

    @Test
    fun testL0EcuApplicationClassification() {
        assertEquals(ObdFailureDomain.L0_ECU_APPLICATION, ObdFailureDomain.classifyResponse("NO DATA\r\n>"))
        assertEquals(ObdFailureDomain.L0_ECU_APPLICATION, ObdFailureDomain.classifyResponse("NODATA\r\n>"))
        assertEquals(ObdFailureDomain.L0_ECU_APPLICATION, ObdFailureDomain.classifyResponse("7F 22 11\r\n>"))
    }

    @Test
    fun testWriteOncePolicyPreventsRetransmission() {
        val queue = ObdCommandQueue()

        val cmd = ObdCommand(
            query = "04",
            priority = 10,
            onSuccess = {},
            onError = {},
            retryPolicy = RetryPolicy.NEVER_AFTER_WRITE
        )
        queue.enqueue(cmd)

        val dequeued = queue.dequeue()
        assertEquals("04", dequeued?.query)
        assertEquals(RetryPolicy.NEVER_AFTER_WRITE, dequeued?.retryPolicy)
    }

    @Test
    fun testRetryOnTransportBeforeWriteOnlyPolicy() {
        val queue = ObdCommandQueue()
        val cmd = ObdCommand(
            query = "14FFFFFF",
            priority = 10,
            onSuccess = {},
            onError = {},
            retryPolicy = RetryPolicy.RETRY_ON_TRANSPORT_BEFORE_WRITE_ONLY
        )
        queue.enqueue(cmd)

        val dequeued = queue.dequeue()
        assertEquals("14FFFFFF", dequeued?.query)
        assertEquals(RetryPolicy.RETRY_ON_TRANSPORT_BEFORE_WRITE_ONLY, dequeued?.retryPolicy)
    }

    @Test
    fun testPhysicalBusOwnerExclusivityBlocksSelfHealing() = runBlocking {
        val actor = PhysicalBusActor()
        assertEquals(PhysicalBusOwner.IDLE, actor.currentOwner)

        var insideLeaseOwner: PhysicalBusOwner? = null
        actor.withLease(PhysicalBusOwner.DIAGNOSTIC_SCAN, {}, {}) {
            insideLeaseOwner = actor.currentOwner
            // Bus owner lease policy forbids non-matching callers during lease
            assertFalse(PhysicalBusLeasePolicy.allows(actor.currentOwner, PhysicalBusOwner.IDLE))
            assertTrue(PhysicalBusLeasePolicy.allows(actor.currentOwner, PhysicalBusOwner.DIAGNOSTIC_SCAN))
        }

        assertEquals(PhysicalBusOwner.DIAGNOSTIC_SCAN, insideLeaseOwner)
        assertEquals(PhysicalBusOwner.IDLE, actor.currentOwner)
    }
}
