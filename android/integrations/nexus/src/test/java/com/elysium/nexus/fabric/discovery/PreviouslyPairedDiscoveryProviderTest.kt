package com.elysium.nexus.fabric.discovery

import com.elysium.nexus.databases.pairing.PairedDeviceDao
import com.elysium.nexus.databases.pairing.PairedDeviceEntity
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviouslyPairedDiscoveryProviderTest {

    private class FakeDao(
        val devices: List<PairedDeviceEntity>
    ) : PairedDeviceDao {
        override fun getAllDevicesFlow(): Flow<List<PairedDeviceEntity>> = flowOf(devices)

        override fun getPairedDevicesFlow(): Flow<List<PairedDeviceEntity>> =
            flowOf(devices.filter { it.authStatus == "PAIRED" })

        override suspend fun getDeviceById(id: String): PairedDeviceEntity? =
            devices.firstOrNull { it.id == id }

        override suspend fun getDevicesByProtocol(protocolType: String): List<PairedDeviceEntity> =
            devices.filter { it.protocolType == protocolType }

        override suspend fun insertOrUpdate(device: PairedDeviceEntity) {}

        override suspend fun update(device: PairedDeviceEntity) {}

        override suspend fun updatePairingStatus(
            id: String, token: String?, authStatus: String, timestamp: Long
        ) {}

        override suspend fun delete(device: PairedDeviceEntity) {}

        override suspend fun deleteById(id: String) {}

        override suspend fun deleteAll() {}
    }

    private fun entity(
        id: String = "entity-1",
        name: String = "Salon TV",
        brand: String = "",
        deviceType: String = "TV",
        protocolType: String = "LG_WEBOS",
        ipAddress: String? = "192.168.1.50",
        port: Int? = 3000,
        macAddress: String? = "AA:BB:CC:DD:EE:FF",
        clientKey: String? = "client-key-1",
        authStatus: String = "PAIRED",
        lastSeenTimestamp: Long = System.currentTimeMillis(),
        customName: String? = null
    ) = PairedDeviceEntity(
        id = id,
        name = name,
        brand = brand,
        deviceType = deviceType,
        protocolType = protocolType,
        ipAddress = ipAddress,
        port = port,
        macAddress = macAddress,
        clientKey = clientKey,
        authStatus = authStatus,
        lastSeenTimestamp = lastSeenTimestamp,
        customName = customName
    )

    @Test
    fun `entityToRecord maps LG webOS fields`() {
        val provider = PreviouslyPairedDiscoveryProvider(FakeDao(emptyList()), maxAgeMs = 0L)

        val record = provider.entityToRecord(entity())

        assertEquals(Protocol.VendorWebSocket, record.providerProtocol)
        assertEquals("192.168.1.50", record.ipAddress)
        assertEquals(3000, record.port)
        assertEquals("AA:BB:CC:DD:EE:FF", record.macAddress)
        assertEquals("LG", record.manufacturer)
        assertEquals("TV", record.model)
        assertEquals("true", record.rawProperties["hasClientKey"])
        assertEquals("PreviouslyPaired", record.rawProperties["discoveryProtocol"])
    }

    @Test
    fun `entityToRecord infers brand from protocol type`() {
        val provider = PreviouslyPairedDiscoveryProvider(FakeDao(emptyList()), maxAgeMs = 0L)

        assertEquals("LG", provider.entityToRecord(entity(protocolType = "LG_WEBOS")).manufacturer)
        assertEquals("Samsung", provider.entityToRecord(entity(protocolType = "SAMSUNG_TIZEN")).manufacturer)
        assertEquals("Sony", provider.entityToRecord(entity(protocolType = "SONY_BRAVIA")).manufacturer)
        assertEquals("Roku", provider.entityToRecord(entity(protocolType = "ROKU")).manufacturer)
        assertEquals("Google", provider.entityToRecord(entity(protocolType = "ANDROID_TV")).manufacturer)
        assertEquals("Apple", provider.entityToRecord(entity(protocolType = "MAC_AGENT")).manufacturer)
    }

    @Test
    fun `keeps explicit brand when present`() {
        val provider = PreviouslyPairedDiscoveryProvider(FakeDao(emptyList()), maxAgeMs = 0L)

        val record = provider.entityToRecord(entity(brand = "Philips", protocolType = "LG_WEBOS"))

        assertEquals("Philips", record.manufacturer)
    }

    @Test
    fun `maps protocol types correctly`() {
        val provider = PreviouslyPairedDiscoveryProvider(FakeDao(emptyList()), maxAgeMs = 0L)

        assertEquals(Protocol.VendorWebSocket, provider.entityToRecord(entity(protocolType = "SAMSUNG_TIZEN")).providerProtocol)
        assertEquals(Protocol.VendorRest, provider.entityToRecord(entity(protocolType = "ROKU")).providerProtocol)
        assertEquals(Protocol.VendorRest, provider.entityToRecord(entity(protocolType = "SONY_BRAVIA")).providerProtocol)
        assertEquals(Protocol.ElysiumLink, provider.entityToRecord(entity(protocolType = "MAC_AGENT")).providerProtocol)
        assertEquals(Protocol.DirectIr, provider.entityToRecord(entity(protocolType = "INFRARED")).providerProtocol)
        assertEquals(Protocol.Ble, provider.entityToRecord(entity(protocolType = "BLE_HID")).providerProtocol)
    }

    @Test
    fun `inferCapabilities from device type`() {
        val provider = PreviouslyPairedDiscoveryProvider(FakeDao(emptyList()), maxAgeMs = 0L)

        assertEquals(setOf("tv"), provider.entityToRecord(entity(deviceType = "TV")).capabilities)
        assertEquals(setOf("speaker"), provider.entityToRecord(entity(deviceType = "SPEAKER")).capabilities)
        assertEquals(setOf("computer"), provider.entityToRecord(entity(deviceType = "DESKTOP_MAC")).capabilities)
        assertEquals(setOf("controller"), provider.entityToRecord(entity(deviceType = "GAMEPAD_HOST")).capabilities)
        assertEquals(setOf("device"), provider.entityToRecord(entity(deviceType = "WEIRD")).capabilities)
    }

    @Test
    fun `discover filters stale devices by maxAge`() = runBlocking {
        val recent = entity(id = "recent", lastSeenTimestamp = System.currentTimeMillis())
        val stale = entity(
            id = "stale",
            lastSeenTimestamp = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L)
        )
        val provider = PreviouslyPairedDiscoveryProvider(
            FakeDao(listOf(recent, stale)),
            maxAgeMs = 7 * 24 * 60 * 60 * 1000L
        )

        val records = provider.discover(timeoutMs = 100L)

        assertEquals(1, records.size)
        assertEquals("recent", records.first().rawProperties["entityId"])
    }

    @Test
    fun `discover returns empty when dao fails`() = runBlocking {
        val errorDao = object : PairedDeviceDao {
            override fun getAllDevicesFlow(): Flow<List<PairedDeviceEntity>> =
                kotlinx.coroutines.flow.flow { error("db broken") }

            override fun getPairedDevicesFlow(): Flow<List<PairedDeviceEntity>> = flowOf(emptyList())

            override suspend fun getDeviceById(id: String): PairedDeviceEntity? = null

            override suspend fun getDevicesByProtocol(protocolType: String): List<PairedDeviceEntity> =
                emptyList()

            override suspend fun insertOrUpdate(device: PairedDeviceEntity) {}

            override suspend fun update(device: PairedDeviceEntity) {}

            override suspend fun updatePairingStatus(
                id: String, token: String?, authStatus: String, timestamp: Long
            ) {}

            override suspend fun delete(device: PairedDeviceEntity) {}

            override suspend fun deleteById(id: String) {}

            override suspend fun deleteAll() {}
        }

        val records = PreviouslyPairedDiscoveryProvider(errorDao, maxAgeMs = 0L).discover(100L)

        assertTrue(records.isEmpty())
    }
}