package com.elysium.nexus.core.transport.mac

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V0.7 Phase 32 — cross-language parity test.
 *
 * Fixed X25519 scalars (same inputs as the Swift vector
 * `apps/mac-agent/Sources/ElysiumAgent/Crypto.swift` path,
 * produced by /tmp runs of CryptoKit hkdfDerivedSymmetricKey):
 *
 *   swift parity.swift reproducido a mano:
 *   alicePub=07a37cbc142093c8b755dc1b10e86cb426374ad16aa853ed0bdfc0b2b86d1c7c
 *   bobPub=5869aff450549732cbaaed5e5df9b30a6da31cb0e5742bad5ad4a1a768f1a67b
 *   alice_tx == bob_rx == 09b9853c3da2478c501b3026f77bbc6005487f8591fe1ad1a2b5fce57306da6e
 *   bob_tx   == alice_rx == 7bb272e0e0c779239423fab3d05f1102718ba23f714313f22c379738e0c14cd3
 *
 * Kotlin (JCE X25519 + RFC 5869 HKDF-SHA256) must produce
 * THE SAME bytes: this pins the wire-compatible labels
 * `elysium-channel-phone-to-mac` / `elysium-channel-mac-to-phone`.
 */
class MacCryptoCrossLanguageParityTest {

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun `kotlin derives the same directional keys as CryptoKit Swift`() {
        val aliceScalar = hexToBytes(
            "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"
        )
        val bobScalar = hexToBytes(
            "2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40"
        )
        val alicePub = hexToBytes("07a37cbc142093c8b755dc1b10e86cb426374ad16aa853ed0bdfc0b2b86d1c7c")
        val bobPub = hexToBytes("5869aff450549732cbaaed5e5df9b30a6da31cb0e5742bad5ad4a1a768f1a67b")

        val aliceKeys = MacCrypto.deriveChannelKeys(
            MacCrypto.KeyPair(alicePub, aliceScalar),
            bobPub,
            MacCrypto.ChannelSide.PHONE
        )
        val bobKeys = MacCrypto.deriveChannelKeys(
            MacCrypto.KeyPair(bobPub, bobScalar),
            alicePub,
            MacCrypto.ChannelSide.MAC
        )

        assertEquals(
            "Alice TX must equal CryptoKit's phone-to-mac vector",
            "09b9853c3da2478c501b3026f77bbc6005487f8591fe1ad1a2b5fce57306da6e",
            aliceKeys.txKeyBytes.toHex()
        )
        assertEquals(
            "Bob RX must equal Alice TX (same direction key)",
            aliceKeys.txKeyBytes.toHex(),
            bobKeys.rxKeyBytes.toHex()
        )
        assertEquals(
            "Bob TX must equal CryptoKit's mac-to-phone vector",
            "7bb272e0e0c779239423fab3d05f1102718ba23f714313f22c379738e0c14cd3",
            bobKeys.txKeyBytes.toHex()
        )
        assertEquals(
            "Alice RX must equal Bob TX (same direction key)",
            bobKeys.txKeyBytes.toHex(),
            aliceKeys.rxKeyBytes.toHex()
        )
    }
}