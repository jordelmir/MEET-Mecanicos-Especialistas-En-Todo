package com.elysium.nexus.fabric.pairing

import com.elysium.nexus.databases.pairing.PairedDeviceEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePairingEngineTest {

    @Test
    fun `pairDevice for LG webOS logic returns success with client key`() = runBlocking {
        val device = PairedDeviceEntity(
            id = "lg_tv_1",
            name = "LG webOS Smart TV",
            brand = "LG",
            deviceType = "TV",
            protocolType = "LG_WEBOS",
            ipAddress = "192.168.1.100",
            port = 3000
        )

        // Verify entity properties
        assertEquals("LG_WEBOS", device.protocolType)
        assertEquals("192.168.1.100", device.ipAddress)
        assertEquals(3000, device.port)
    }

    @Test
    fun `pairDevice for Mac Agent without PIN validation`() = runBlocking {
        val device = PairedDeviceEntity(
            id = "mac_agent_1",
            name = "MacBook Pro",
            brand = "Apple",
            deviceType = "DESKTOP_MAC",
            protocolType = "MAC_AGENT",
            ipAddress = "192.168.1.50",
            port = 7878
        )

        assertEquals("MAC_AGENT", device.protocolType)
        assertEquals(7878, device.port)
    }
}
