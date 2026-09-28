package com.elysium.nexus.fabric.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * JVM tests for [InMemoryDeviceIdentity] +
 * [Fingerprint]. The Android-backed
 * [AndroidDeviceIdentity] is exercised
 * on-device (Phase 6 has no Keystore on
 * the JVM test path).
 */
class DeviceIdentityTest {

    @Test
    fun `fingerprint is 32 bytes (SHA-256)`() {
        val key = ByteArray(32)
        val fp = Fingerprint.of(key)
        assertEquals(32, fp.size)
    }

    @Test
    fun `fingerprint is deterministic for the same key`() {
        val key = "the same key".toByteArray()
        val a = Fingerprint.ofHex(key)
        val b = Fingerprint.ofHex(key)
        assertEquals(a, b)
    }

    @Test
    fun `different keys produce different fingerprints`() {
        val a = Fingerprint.ofHex("key-a".toByteArray())
        val b = Fingerprint.ofHex("key-b".toByteArray())
        assertNotEquals(a, b)
    }

    @Test
    fun `fingerprint is hex and 64 chars long`() {
        val hex = Fingerprint.ofHex("any key".toByteArray())
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in "0123456789abcdef" })
    }

    @Test
    fun `InMemoryDeviceIdentity rejects blank deviceId`() {
        try {
            InMemoryDeviceIdentity(
                deviceId = "",
                label = "L",
                hardwareClass = HardwareClass.AndroidPhone,
                privateKey = ByteArray(32) { it.toByte() },
                publicKey = ByteArray(32) { it.toByte() }
            )
            throw AssertionError("Expected IllegalArgumentException for blank deviceId.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `InMemoryDeviceIdentity rejects blank label`() {
        try {
            InMemoryDeviceIdentity(
                deviceId = "d1",
                label = "",
                hardwareClass = HardwareClass.AndroidPhone,
                privateKey = ByteArray(32) { it.toByte() },
                publicKey = ByteArray(32) { it.toByte() }
            )
            throw AssertionError("Expected IllegalArgumentException for blank label.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `InMemoryDeviceIdentity rejects empty private key`() {
        try {
            InMemoryDeviceIdentity(
                deviceId = "d1",
                label = "L",
                hardwareClass = HardwareClass.AndroidPhone,
                privateKey = ByteArray(0),
                publicKey = ByteArray(32) { it.toByte() }
            )
            throw AssertionError("Expected IllegalArgumentException for empty private key.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `InMemoryDeviceIdentity sign returns a 32-byte HMAC-SHA256`() {
        val rng = SecureRandom()
        val priv = ByteArray(32).also { rng.nextBytes(it) }
        val pub = ByteArray(32).also { rng.nextBytes(it) }
        val id = InMemoryDeviceIdentity(
            deviceId = "d1",
            label = "Test",
            hardwareClass = HardwareClass.Hub,
            privateKey = priv,
            publicKey = pub
        )
        val sig = id.sign("hello".toByteArray())
        assertEquals(32, sig.size)
    }

    @Test
    fun `InMemoryDeviceIdentity fingerprint is stable across constructions`() {
        val rng = SecureRandom()
        val priv = ByteArray(32).also { rng.nextBytes(it) }
        val pub = ByteArray(32).also { rng.nextBytes(it) }
        val a = InMemoryDeviceIdentity(
            deviceId = "d1",
            label = "Test",
            hardwareClass = HardwareClass.Hub,
            privateKey = priv,
            publicKey = pub
        )
        val b = InMemoryDeviceIdentity(
            deviceId = "d1",
            label = "Test",
            hardwareClass = HardwareClass.Hub,
            privateKey = priv,
            publicKey = pub
        )
        assertTrue(a.fingerprint.contentEquals(b.fingerprint))
    }

    @Test
    fun `InMemoryDeviceIdentity sign is deterministic for the same key + payload`() {
        val rng = SecureRandom()
        val priv = ByteArray(32).also { rng.nextBytes(it) }
        val pub = ByteArray(32).also { rng.nextBytes(it) }
        val a = InMemoryDeviceIdentity(
            deviceId = "d1",
            label = "Test",
            hardwareClass = HardwareClass.Hub,
            privateKey = priv,
            publicKey = pub
        )
        val b = InMemoryDeviceIdentity(
            deviceId = "d1",
            label = "Test",
            hardwareClass = HardwareClass.Hub,
            privateKey = priv,
            publicKey = pub
        )
        val sigA = a.sign("hello".toByteArray())
        val sigB = b.sign("hello".toByteArray())
        assertTrue(sigA.contentEquals(sigB))
    }

    @Test
    fun `HardwareClass includes every variant per §31_1`() {
        // §31.1 enumerates: phone, tablet, TV,
        // foldable, mac, win, linux, web,
        // hub, receiver.
        val expected = setOf(
            "AndroidPhone", "AndroidTablet", "AndroidTv", "Foldable",
            "MacAgent", "WindowsAgent", "LinuxAgent", "WebConsole",
            "Hub", "Receiver"
        )
        val actual = HardwareClass.values().map { it.name }.toSet()
        for (name in expected) {
            assertTrue("Expected HardwareClass '$name' (per §31.1)", name in actual)
        }
    }

    @Test
    fun `InMemoryDeviceIdentity equals is symmetric on deviceId + fingerprint`() {
        val rng = SecureRandom()
        val priv = ByteArray(32).also { rng.nextBytes(it) }
        val pub = ByteArray(32).also { rng.nextBytes(it) }
        val a = InMemoryDeviceIdentity(
            deviceId = "d1",
            label = "A",
            hardwareClass = HardwareClass.Hub,
            privateKey = priv,
            publicKey = pub
        )
        val b = InMemoryDeviceIdentity(
            deviceId = "d1",
            label = "B (same id, same key)",
            hardwareClass = HardwareClass.Hub,
            privateKey = priv,
            publicKey = pub
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
