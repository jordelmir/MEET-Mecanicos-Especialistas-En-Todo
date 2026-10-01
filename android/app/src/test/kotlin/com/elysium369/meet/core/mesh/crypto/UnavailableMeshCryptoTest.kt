package com.elysium369.meet.core.mesh.crypto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UnavailableMeshCryptoTest {

    @Test
    fun `encrypt fails closed with unverified provider exception`() {
        val crypto = UnavailableMeshCrypto()
        try {
            runBlocking {
                crypto.encrypt("device-001", "plaintext".toByteArray(), "ad".toByteArray())
            }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("MESH_CRYPTO_PROVIDER_NOT_VERIFIED") == true)
        }
    }

    @Test
    fun `decrypt fails closed with unverified provider exception`() {
        val crypto = UnavailableMeshCrypto()
        val ciphertext = MeshCiphertext(
            initializationVector = ByteArray(12),
            ciphertext = ByteArray(32),
            authenticationTag = ByteArray(16),
        )
        try {
            runBlocking {
                crypto.decrypt(ciphertext, "ad".toByteArray())
            }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("MESH_CRYPTO_PROVIDER_NOT_VERIFIED") == true)
        }
    }
}
