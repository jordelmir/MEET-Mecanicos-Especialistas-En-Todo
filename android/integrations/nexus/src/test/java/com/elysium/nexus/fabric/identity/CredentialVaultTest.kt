package com.elysium.nexus.fabric.identity

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CredentialVaultTest {

    private lateinit var vault: InMemoryCredentialVault

    @Before
    fun setup() {
        vault = InMemoryCredentialVault()
    }

    @Test
    fun `store returns a reference`() {
        val credential = Credential.WiFiCredential(
            deviceId = DeviceId("device-1"),
            ssid = "MyNetwork",
            psk = "password123"
        )

        val ref = vault.store(credential)
        assertNotNull(ref.keyAlias)
        assertEquals(Protocol.WiFi, ref.protocol)
        assertEquals(DeviceId("device-1"), ref.deviceId)
    }

    @Test
    fun `retrieve returns credential by reference`() {
        val credential = Credential.MqttAuth(
            deviceId = DeviceId("device-1"),
            username = "user",
            password = "pass",
            clientId = "client-1"
        )

        val ref = vault.store(credential)
        val retrieved = vault.retrieve(ref)
        assertNotNull(retrieved)
        assertTrue(retrieved is Credential.MqttAuth)
        assertEquals("user", (retrieved as Credential.MqttAuth).username)
    }

    @Test
    fun `retrieve returns null for unknown alias`() {
        val ref = CredentialReference(
            keyAlias = "nonexistent",
            protocol = Protocol.WiFi,
            deviceId = DeviceId("device-1"),
            label = "test",
            createdAtMs = System.currentTimeMillis(),
            expiresAtMs = null
        )

        assertNull(vault.retrieve(ref))
    }

    @Test
    fun `delete removes credential`() {
        val credential = Credential.WiFiCredential(
            deviceId = DeviceId("device-1"),
            ssid = "MyNetwork",
            psk = "password123"
        )

        val ref = vault.store(credential)
        vault.delete(ref)
        assertNull(vault.retrieve(ref))
    }

    @Test
    fun `listForDevice returns all credentials for a device`() {
        val device1 = DeviceId("device-1")
        val device2 = DeviceId("device-2")

        vault.store(Credential.WiFiCredential(device1, "net1", "pass1"))
        vault.store(Credential.MqttAuth(device1, "user1", "pass1", "client1"))
        vault.store(Credential.WiFiCredential(device2, "net2", "pass2"))

        val refs = vault.listForDevice(device1)
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.deviceId == device1 })
    }

    @Test
    fun `listForProtocol returns all credentials for a protocol`() {
        vault.store(Credential.WiFiCredential(DeviceId("d1"), "net1", "pass1"))
        vault.store(Credential.WiFiCredential(DeviceId("d2"), "net2", "pass2"))
        vault.store(Credential.MqttAuth(DeviceId("d1"), "user1", "pass1", "client1"))

        val refs = vault.listForProtocol(Protocol.WiFi)
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.protocol == Protocol.WiFi })
    }

    @Test
    fun `expired credential is not retrieved`() {
        val credential = Credential.WiFiCredential(
            deviceId = DeviceId("device-1"),
            ssid = "MyNetwork",
            psk = "password123",
            expiresAtMs = System.currentTimeMillis() - 1000 // expired
        )

        val ref = vault.store(credential)
        assertNull(vault.retrieve(ref))
    }

    @Test
    fun `non-expired credential is retrieved`() {
        val credential = Credential.WiFiCredential(
            deviceId = DeviceId("device-1"),
            ssid = "MyNetwork",
            psk = "password123",
            expiresAtMs = System.currentTimeMillis() + 3_600_000 // 1 hour from now
        )

        val ref = vault.store(credential)
        assertNotNull(vault.retrieve(ref))
    }

    @Test
    fun `credential without expiry is always valid`() {
        val credential = Credential.WiFiCredential(
            deviceId = DeviceId("device-1"),
            ssid = "MyNetwork",
            psk = "password123",
            expiresAtMs = null
        )

        assertTrue(credential.isValid)
    }

    @Test
    fun `matter pairing credential stores correctly`() {
        val credential = Credential.MatterPairing(
            deviceId = DeviceId("device-1"),
            pairingCode = "12345678",
            salt = ByteArray(16) { it.toByte() }
        )

        val ref = vault.store(credential)
        val retrieved = vault.retrieve(ref) as? Credential.MatterPairing
        assertNotNull(retrieved)
        assertEquals("12345678", retrieved?.pairingCode)
    }

    @Test
    fun `vendor token credential stores correctly`() {
        val credential = Credential.VendorToken(
            deviceId = DeviceId("device-1"),
            vendorProtocol = Protocol.VendorRest,
            token = "abc123",
            refreshToken = "def456",
            expiresAtMs = System.currentTimeMillis() + 3_600_000
        )

        val ref = vault.store(credential)
        val retrieved = vault.retrieve(ref) as? Credential.VendorToken
        assertNotNull(retrieved)
        assertEquals("abc123", retrieved?.token)
        assertEquals("def456", retrieved?.refreshToken)
    }

    @Test
    fun `generic credential stores correctly`() {
        val credential = Credential.Generic(
            deviceId = DeviceId("device-1"),
            protocol = Protocol.Unknown,
            keyAlias = "custom-key",
            metadata = mapOf("version" to "1.0")
        )

        val ref = vault.store(credential)
        val retrieved = vault.retrieve(ref) as? Credential.Generic
        assertNotNull(retrieved)
        assertEquals("custom-key", retrieved?.keyAlias)
        assertEquals("1.0", retrieved?.metadata?.get("version"))
    }

    private fun <T> T?.assertNotNull() {
        org.junit.Assert.assertNotNull(this)
    }

    private fun <T> T?.assertNull() {
        org.junit.Assert.assertNull(this)
    }
}
