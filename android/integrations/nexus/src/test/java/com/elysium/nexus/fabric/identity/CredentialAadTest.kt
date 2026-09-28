package com.elysium.nexus.fabric.identity

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V0.7 Phase 33 — CredentialAad binding tests.
 *
 * The AAD is a deterministic, identity-binding
 * function: two credentials that differ in ANY
 * canonical field must produce different AADs, so
 * cross-credential ciphertext substitution fails
 * authentication under the shared master key.
 */
class CredentialAadTest {

    private val device = DeviceId("tv-living-room")

    @Test
    fun `build is deterministic for identical inputs`() {
        val a = CredentialAad.build("alias-1", Protocol.Matter, device)
        val b = CredentialAad.build("alias-1", Protocol.Matter, device)
        assertArrayEquals(a, b)
        assertEquals(
            "nexus-credential-v1|alias=alias-1|protocol=Matter|" +
                "device=tv-living-room|purpose=credential-storage|schema=1",
            String(a)
        )
    }

    @Test
    fun `different protocol binds differently`() {
        val matter = CredentialAad.build("alias-1", Protocol.Matter, device)
        val zigbee = CredentialAad.build("alias-1", Protocol.Zigbee, device)
        assertFalse(matter.contentEquals(zigbee))
    }

    @Test
    fun `different device binds differently`() {
        val a = CredentialAad.build("alias-1", Protocol.Matter, DeviceId("a"))
        val b = CredentialAad.build("alias-1", Protocol.Matter, DeviceId("b"))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `different alias binds differently`() {
        val a = CredentialAad.build("alias-1", Protocol.Matter, device)
        val b = CredentialAad.build("alias-2", Protocol.Matter, device)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `different purpose binds differently`() {
        val a = CredentialAad.build("alias-1", Protocol.Matter, device, purpose = "pairing")
        val b = CredentialAad.build("alias-1", Protocol.Matter, device, purpose = "refresh")
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `different schema version binds differently`() {
        val a = CredentialAad.build("alias-1", Protocol.Matter, device, schemaVersion = 1)
        val b = CredentialAad.build("alias-1", Protocol.Matter, device, schemaVersion = 2)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `defaults pin storage purpose and current schema`() {
        assertEquals(CredentialAad.PURPOSE_STORAGE, "credential-storage")
        assertTrue("schema must be pinned to avoid silent drift", CredentialAad.SCHEMA_VERSION >= 1)
    }
}