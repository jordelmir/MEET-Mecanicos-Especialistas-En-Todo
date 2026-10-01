package com.elysium.nexus.fabric.discovery

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryOrchestratorTest {

    private class FakeProvider(
        override val protocol: Protocol,
        override val label: String,
        private val records: List<RawDiscoveryRecord>
    ) : DiscoveryProvider {
        override val isAvailable: Boolean = true

        override suspend fun discover(timeoutMs: Long): List<RawDiscoveryRecord> = records
    }

    @Test
    fun `discover merges duplicate devices by stable id`() = runBlocking {
        val mdns = FakeProvider(
            Protocol.WiFi,
            "mDNS",
            listOf(
                RawDiscoveryRecord(
                    providerProtocol = Protocol.WiFi,
                    ipAddress = "192.168.1.10",
                    hostname = "tv-living",
                    serialNumber = "SN-123",
                    manufacturer = "Samsung",
                    model = "QE65QN85B",
                    capabilities = setOf("tv")
                )
            )
        )
        val ssdp = FakeProvider(
            Protocol.WiFi,
            "SSDP",
            listOf(
                RawDiscoveryRecord(
                    providerProtocol = Protocol.WiFi,
                    ipAddress = "192.168.1.10",
                    port = 1900,
                    upnpUdn = "uuid:abc",
                    friendlyName = "Samsung Smart TV",
                    manufacturer = "Samsung Electronics",
                    serialNumber = "SN-123"
                )
            )
        )

        val orchestrator = DiscoveryOrchestrator(listOf(mdns, ssdp))
        val results = orchestrator.discover(timeoutMs = 100L).toList()

        val found = results.filterIsInstance<DiscoveryResult.DeviceFound>()
        val complete = results.filterIsInstance<DiscoveryResult.Complete>()

        // Two different records but same stable serial → ONE device
        assertEquals(1, found.size)
        assertEquals(1, complete.size)
        assertEquals(1, complete.first().deviceCount)
    }

    @Test
    fun `discover emits Complete with device count`() = runBlocking {
        val provider = FakeProvider(
            Protocol.WiFi,
            "SSDP",
            listOf(
                RawDiscoveryRecord(
                    providerProtocol = Protocol.WiFi,
                    ipAddress = "192.168.1.11",
                    serialNumber = "SER-1"
                ),
                RawDiscoveryRecord(
                    providerProtocol = Protocol.WiFi,
                    ipAddress = "192.168.1.12",
                    serialNumber = "SER-2"
                )
            )
        )

        val orchestrator = DiscoveryOrchestrator(listOf(provider))
        val results = orchestrator.discover().toList()

        assertEquals(2, results.count { it is DiscoveryResult.DeviceFound })
        assertEquals(
            DiscoveryResult.Complete(2),
            results.last()
        )
    }

    @Test
    fun `discover continues when a provider throws`() = runBlocking {
        val broken = object : DiscoveryProvider {
            override val protocol: Protocol = Protocol.WiFi
            override val label: String = "broken"
            override val isAvailable: Boolean = true

            override suspend fun discover(timeoutMs: Long): List<RawDiscoveryRecord> {
                error("provider crashed")
            }
        }
        val healthy = FakeProvider(
            Protocol.WiFi,
            "SSDP",
            listOf(
                RawDiscoveryRecord(
                    providerProtocol = Protocol.WiFi,
                    ipAddress = "192.168.1.13",
                    serialNumber = "SER-3"
                )
            )
        )

        val orchestrator = DiscoveryOrchestrator(listOf(broken, healthy))
        val results = orchestrator.discover().toList()

        assertEquals(1, results.count { it is DiscoveryResult.DeviceFound })
    }

    @Test
    fun `default merger infers device type from capabilities`() {
        val merger = DefaultDiscoveryMerger()
        val record = RawDiscoveryRecord(
            providerProtocol = Protocol.WiFi,
            ipAddress = "192.168.1.10",
            serialNumber = "TV-1",
            capabilities = setOf("tv", "input_source")
        )

        val twin = merger.merge(record.stableId!!, listOf(record))

        assertEquals(DeviceType.Television, twin.deviceType)
        assertEquals("TV-1", twin.deviceId.value)
    }

    @Test
    fun `default merger builds protocol binding with port`() {
        val merger = DefaultDiscoveryMerger()
        val record = RawDiscoveryRecord(
            providerProtocol = Protocol.VendorRest,
            ipAddress = "192.168.1.10",
            port = 8060,
            serialNumber = "ROKU-1"
        )

        val twin = merger.merge(record.stableId!!, listOf(record))

        val binding = twin.protocolBindings.first()
        assertEquals(Protocol.VendorRest, binding.protocol)
        assertTrue(binding.endpoint.contains(":8060"))
    }

    @Test
    fun `stableId falls back to ip plus hostname`() {
        val merger = DefaultDiscoveryMerger()
        val record = RawDiscoveryRecord(
            providerProtocol = Protocol.WiFi,
            ipAddress = "192.168.1.99",
            hostname = "desk"
        )

        assertEquals("192.168.1.99_desk", merger.stableKey(record))
    }

    @Test
    fun `raw record display name falls back through candidates`() {
        val withFriendly = RawDiscoveryRecord(
            providerProtocol = Protocol.WiFi,
            friendlyName = "Living Room",
            ipAddress = "1.2.3.4"
        )
        val withHost = RawDiscoveryRecord(
            providerProtocol = Protocol.WiFi,
            hostname = "host-1"
        )
        val bare = RawDiscoveryRecord(providerProtocol = Protocol.WiFi, ipAddress = "9.9.9.9")

        assertEquals("Living Room", withFriendOrDefault(withFriendly))
        assertEquals("host-1", withFriendOrDefault(withHost))
        assertEquals("9.9.9.9", withFriendOrDefault(bare))
    }

    private fun withFriendOrDefault(record: RawDiscoveryRecord): String = record.displayName
}